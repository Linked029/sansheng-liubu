package com.lyihub.archiveassistant.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.lyihub.archiveassistant.domain.LocalModelInfo
import com.lyihub.archiveassistant.domain.LocalModelState
import com.lyihub.archiveassistant.domain.LocalModelStatus
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLongArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface ModelDownloadManager {
  val downloadState: Flow<LocalModelState>

  suspend fun startDownload(model: LocalModelInfo): Result<Unit>

  suspend fun cancelDownload()

  suspend fun deleteModel(model: LocalModelInfo): Result<Unit>

  suspend fun importModel(model: LocalModelInfo, uri: Uri): Result<Unit>

  fun isModelPresent(model: LocalModelInfo): Boolean
}

class OkHttpModelDownloadManager(
  private val context: Context,
  private val modelDir: File = File(context.filesDir, "models"),
) : ModelDownloadManager {
  private val okHttpClient = OkHttpClient()
  private val downloadMutex = Mutex()
  private val segmentProgressMutex = Mutex()
  private val state = MutableStateFlow(LocalModelState())

  @Volatile private var isCancelled = false

  override val downloadState: Flow<LocalModelState> = state

  override suspend fun startDownload(model: LocalModelInfo): Result<Unit> = downloadMutex.withLock {
    withContext(Dispatchers.IO) {
      isCancelled = false
      val targetFile = File(modelDir, model.fileName)
      val partialFile = File(modelDir, ".${model.fileName}.part")
      val segmentProgressFile = File(modelDir, ".${model.fileName}.segments")

      runCatching {
          Log.i(TAG, "startDownload url=${model.downloadUrl}")
          modelDir.mkdirs()
          requireNotMeteredNetwork()
          requireStorageSpace(model)

          if (targetFile.exists() && verifySha256(targetFile, model.expectedSha256)) {
            state.value = downloadedState(targetFile, model.sizeBytes)
            return@runCatching
          }

          val segmentedProgressExists = segmentProgressFile.exists()
          val existingBytes =
            if (segmentedProgressExists) {
              0L
            } else {
              partialFile.takeIf { it.exists() }?.length() ?: 0L
            }
          state.value = downloadingState(existingBytes, model.sizeBytes)

          if (canUseSegmentedDownload(model)) {
            Log.i(TAG, "Starting segmented model download with $SEGMENT_COUNT workers")
            downloadSegmented(model, partialFile, segmentProgressFile)
          } else {
            if (segmentedProgressExists) {
              partialFile.delete()
              segmentProgressFile.delete()
            }
            Log.i(TAG, "Starting single-stream model download from byte $existingBytes")
            downloadSingleStream(model, partialFile, existingBytes)
          }

          if (isCancelled) {
            state.value = LocalModelState(status = LocalModelStatus.NOT_DOWNLOADED)
            throw IOException("Download cancelled")
          }

          if (!partialFile.renameTo(targetFile)) {
            throw IOException("Unable to finalize model download")
          }
          segmentProgressFile.delete()

          if (!verifySha256(targetFile, model.expectedSha256)) {
            targetFile.delete()
            partialFile.delete()
            segmentProgressFile.delete()
            state.value =
              LocalModelState(
                status = LocalModelStatus.ERROR,
                errorMessage = "Downloaded model checksum mismatch",
              )
            throw IOException("Downloaded model checksum mismatch")
          }

          state.value = downloadedState(targetFile, targetFile.length())
        }
        .onFailure { error ->
          if (isCancelled) {
            state.value = LocalModelState(status = LocalModelStatus.NOT_DOWNLOADED)
          } else if (state.value.status != LocalModelStatus.ERROR) {
            state.value =
              LocalModelState(
                status = LocalModelStatus.ERROR,
                errorMessage = error.message ?: "Model download failed",
              )
          }
        }
    }
  }

  override suspend fun cancelDownload() {
    isCancelled = true
    okHttpClient.dispatcher.cancelAll()
    state.value = LocalModelState(status = LocalModelStatus.NOT_DOWNLOADED)
  }

  override suspend fun deleteModel(model: LocalModelInfo): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        File(modelDir, model.fileName).delete()
        File(modelDir, ".${model.fileName}.part").delete()
        File(modelDir, ".${model.fileName}.segments").delete()
        state.value = LocalModelState(status = LocalModelStatus.NOT_DOWNLOADED)
      }
    }

  override suspend fun importModel(model: LocalModelInfo, uri: Uri): Result<Unit> =
    downloadMutex.withLock {
      withContext(Dispatchers.IO) {
        val targetFile = File(modelDir, model.fileName)
        val importFile = File(modelDir, ".${model.fileName}.import")
        runCatching {
            modelDir.mkdirs()
            importFile.delete()
            state.value =
              LocalModelState(
                status = LocalModelStatus.DOWNLOADING,
                totalBytes = model.sizeBytes,
              )
            context.contentResolver.openInputStream(uri)?.use { input ->
              importFile.outputStream().buffered().use { output ->
                input.copyTo(output)
              }
            } ?: throw IOException("Unable to open selected model file")

            if (!verifySha256(importFile, model.expectedSha256)) {
              importFile.delete()
              state.value =
                LocalModelState(
                  status = LocalModelStatus.ERROR,
                  errorMessage = "Selected model checksum mismatch",
                )
              throw IOException("Selected model checksum mismatch")
            }

            targetFile.delete()
            if (!importFile.renameTo(targetFile)) {
              throw IOException("Unable to import selected model file")
            }

            state.value = downloadedState(targetFile, targetFile.length())
          }
          .onFailure { error ->
            importFile.delete()
            if (state.value.status != LocalModelStatus.ERROR) {
              state.value =
                LocalModelState(
                  status = LocalModelStatus.ERROR,
                  errorMessage = error.message ?: "Model import failed",
                )
            }
          }
      }
    }

  override fun isModelPresent(model: LocalModelInfo): Boolean {
    val modelFile = File(modelDir, model.fileName)
    return modelFile.exists() && modelFile.length() == model.sizeBytes
  }

  private fun requireNotMeteredNetwork() {
    val connectivityManager =
      context.getSystemService(ConnectivityManager::class.java)
        ?: throw IOException("Connectivity service unavailable")
    val activeNetwork =
      connectivityManager.activeNetwork ?: throw IOException(WIFI_REQUIRED_MESSAGE)
    val capabilities =
      connectivityManager.getNetworkCapabilities(activeNetwork)
        ?: throw IOException(WIFI_REQUIRED_MESSAGE)

    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
      throw IOException(WIFI_REQUIRED_MESSAGE)
    }
  }

  private fun requireStorageSpace(model: LocalModelInfo) {
    if (!hasStorageSpace(model, SINGLE_STREAM_STORAGE_MULTIPLIER)) {
      throw IOException("Insufficient storage for model download")
    }
  }

  private fun hasStorageSpace(model: LocalModelInfo, multiplier: Double): Boolean {
    val requiredBytes = (model.sizeBytes * multiplier).toLong()
    return modelDir.usableSpace > requiredBytes
  }

  private fun canUseSegmentedDownload(model: LocalModelInfo): Boolean {
    val request = Request.Builder().url(model.downloadUrl).header("Range", "bytes=0-0").build()

    return runCatching {
        okHttpClient.newCall(request).execute().use { response ->
          val canUseRange =
            response.code == 206 &&
              response.header("Content-Range")?.let { contentRange ->
                contentRange.substringAfterLast('/', missingDelimiterValue = "").toLongOrNull() ==
                  model.sizeBytes
              } == true
          Log.i(TAG, "Segmented download probe: code=${response.code}, canUseRange=$canUseRange")
          canUseRange
        }
      }
      .onFailure { error ->
        Log.i(TAG, "Segmented download probe failed: ${error.message}")
      }
      .getOrDefault(false)
  }

  private fun downloadSingleStream(model: LocalModelInfo, partialFile: File, existingBytes: Long) {
    val requestBuilder = Request.Builder().url(model.downloadUrl)
    if (existingBytes > 0) {
      requestBuilder.header("Range", "bytes=$existingBytes-")
    }

    okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
      if (!response.isSuccessful) {
        throw IOException("HTTP ${response.code}")
      }

      val shouldAppend = existingBytes > 0 && response.code == 206
      val startBytes = if (shouldAppend) existingBytes else 0L
      if (!shouldAppend && partialFile.exists()) {
        partialFile.delete()
      }

      val responseBody = response.body ?: throw IOException("Empty response body")
      val totalBytes =
        responseBody.contentLength().takeIf { it > 0 }?.plus(startBytes) ?: model.sizeBytes

      if (shouldAppend) {
        appendResponseBody(responseBody, partialFile, startBytes, totalBytes)
      } else {
        partialFile.outputStream().buffered().use { output ->
          copyResponseBody(responseBody, output, partialFile, startBytes, totalBytes)
        }
      }
    }
  }

  private suspend fun downloadSegmented(
    model: LocalModelInfo,
    partialFile: File,
    segmentProgressFile: File,
  ) = coroutineScope {
    val segments = buildSegments(model.sizeBytes)
    val existingProgress = readSegmentProgress(segmentProgressFile, partialFile, segments)
    val segmentBytes =
      AtomicLongArray(segments.size).also { bytes ->
        existingProgress.forEachIndexed { index, downloadedBytes ->
          bytes.set(index, downloadedBytes)
        }
      }
    RandomAccessFile(partialFile, "rw").use { file ->
      file.setLength(model.sizeBytes)
    }
    state.value = downloadingState(segmentBytes.sum(), model.sizeBytes)
    segments
      .mapIndexed { index, segment ->
        async(Dispatchers.IO) {
          downloadSegment(model, partialFile, segmentProgressFile, index, segment, segmentBytes)
        }
      }
      .awaitAll()

    if (isCancelled) {
      return@coroutineScope
    }

    state.value = downloadingState(segmentBytes.sum(), model.sizeBytes)
  }

  private suspend fun downloadSegment(
    model: LocalModelInfo,
    partialFile: File,
    segmentProgressFile: File,
    index: Int,
    segment: LongRange,
    segmentBytes: AtomicLongArray,
  ) {
    val existingBytes = segmentBytes.get(index).coerceAtMost(segment.length())
    if (existingBytes == segment.length()) {
      return
    }
    val startByte = segment.first + existingBytes
    val request =
      Request.Builder()
        .url(model.downloadUrl)
        .header("Range", "bytes=$startByte-${segment.last}")
        .build()

    okHttpClient.newCall(request).execute().use { response ->
      if (response.code != 206) {
        throw IOException("HTTP ${response.code}")
      }

      val responseBody = response.body ?: throw IOException("Empty response body")
      RandomAccessFile(partialFile, "rw").use { output ->
        output.seek(startByte)
        responseBody.byteStream().use { input ->
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          while (true) {
            if (isCancelled) {
              return
            }
            requireNotMeteredNetwork()
            val read = input.read(buffer)
            if (read == -1) {
              break
            }
            output.write(buffer, 0, read)
            segmentBytes.addAndGet(index, read.toLong())
            writeSegmentProgress(segmentProgressFile, segmentBytes)
            state.value = downloadingState(segmentBytes.sum(), model.sizeBytes)
          }
        }
      }
    }
  }

  private fun buildSegments(totalBytes: Long): List<LongRange> {
    val segmentSize = (totalBytes + SEGMENT_COUNT - 1) / SEGMENT_COUNT
    return (0 until SEGMENT_COUNT).mapNotNull { index ->
      val start = index * segmentSize
      if (start >= totalBytes) {
        null
      } else {
        val end = minOf(start + segmentSize - 1, totalBytes - 1)
        start..end
      }
    }
  }

  private fun LongRange.length(): Long = last - first + 1

  private fun readSegmentProgress(
    segmentProgressFile: File,
    partialFile: File,
    segments: List<LongRange>,
  ): List<Long> {
    if (!segmentProgressFile.exists()) {
      return contiguousPrefixProgress(partialFile, segments)
    }
    val values =
      segmentProgressFile.readText().lineSequence().mapNotNull { it.toLongOrNull() }.toList()
    if (values.size != segments.size) {
      return contiguousPrefixProgress(partialFile, segments)
    }
    return values.mapIndexed { index, downloadedBytes ->
      downloadedBytes.coerceIn(0L, segments[index].length())
    }
  }

  private fun contiguousPrefixProgress(partialFile: File, segments: List<LongRange>): List<Long> {
    val existingBytes = partialFile.takeIf { it.exists() }?.length() ?: 0L
    if (existingBytes <= 0L || existingBytes >= segments.sumOf { it.length() }) {
      return List(segments.size) { 0L }
    }
    var remainingBytes = existingBytes
    return segments.map { segment ->
      val downloadedBytes = remainingBytes.coerceIn(0L, segment.length())
      remainingBytes -= downloadedBytes
      downloadedBytes
    }
  }

  private suspend fun writeSegmentProgress(
    segmentProgressFile: File,
    segmentBytes: AtomicLongArray,
  ) {
    segmentProgressMutex.withLock {
      segmentProgressFile.writeText(
        buildString {
          for (index in 0 until segmentBytes.length()) {
            append(segmentBytes.get(index))
            append('\n')
          }
        }
      )
    }
  }

  private fun AtomicLongArray.sum(): Long {
    var total = 0L
    for (index in 0 until length()) {
      total += get(index)
    }
    return total
  }

  private fun appendResponseBody(
    responseBody: okhttp3.ResponseBody,
    partialFile: File,
    downloadedBytes: Long,
    totalBytes: Long,
  ) {
    FileOutputStream(partialFile, true).buffered().use { output ->
      copyResponseBody(responseBody, output, partialFile, downloadedBytes, totalBytes)
    }
  }

  private fun copyResponseBody(
    responseBody: okhttp3.ResponseBody,
    output: java.io.BufferedOutputStream,
    partialFile: File,
    startBytes: Long,
    totalBytes: Long,
  ) {
    var downloadedBytes = startBytes
    responseBody.byteStream().use { input ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        if (isCancelled) {
          return
        }
        requireNotMeteredNetwork()
        val read = input.read(buffer)
        if (read == -1) {
          break
        }
        output.write(buffer, 0, read)
        downloadedBytes += read
        state.value = downloadingState(downloadedBytes, totalBytes)
      }
    }

    if (!isCancelled) {
      state.value = downloadingState(partialFile.length(), totalBytes)
    }
  }

  private fun verifySha256(file: File, expectedSha256: String): Boolean {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val read = input.read(buffer)
        if (read == -1) {
          break
        }
        digest.update(buffer, 0, read)
      }
    }

    val actual = digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    return actual.equals(expectedSha256, ignoreCase = true)
  }

  private fun downloadingState(downloadedBytes: Long, totalBytes: Long) =
    LocalModelState(
      status = LocalModelStatus.DOWNLOADING,
      downloadProgress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f,
      downloadBytes = downloadedBytes,
      totalBytes = totalBytes,
    )

  private fun downloadedState(file: File, totalBytes: Long) =
    LocalModelState(
      status = LocalModelStatus.DOWNLOADED,
      downloadProgress = 1f,
      downloadBytes = totalBytes,
      totalBytes = totalBytes,
      modelPath = file.absolutePath,
    )

  private companion object {
    const val WIFI_REQUIRED_MESSAGE = "网络已断开，请连接 Wi-Fi 后重试"
    const val TAG = "ModelDownloadManager"
    const val SEGMENT_COUNT = 8
    const val SINGLE_STREAM_STORAGE_MULTIPLIER = 1.1
  }
}
