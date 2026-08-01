package com.lyihub.archiveassistant.data

import android.net.Uri
import com.lyihub.archiveassistant.domain.LocalModelInfo
import com.lyihub.archiveassistant.domain.LocalModelState
import com.lyihub.archiveassistant.domain.LocalModelStatus
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelDownloadManagerTest {
  private lateinit var modelDir: File
  private lateinit var model: LocalModelInfo

  @Before
  fun setUp() {
    modelDir = Files.createTempDirectory("model-download-test").toFile()
    model =
      LocalModelInfo(
        id = "test-model",
        displayName = "Test Model",
        fileName = "test-model.bin",
        downloadUrl = "https://example.com/test-model.bin",
        expectedSha256 = sha256(TEST_BYTES),
        sizeBytes = TEST_BYTES.size.toLong(),
      )
  }

  @After
  fun tearDown() {
    modelDir.deleteRecursively()
  }

  @Test
  fun startDownload_success_progressesToDownloadedAndWritesModel() = runTest {
    val downloader = FakeModelDownloader(modelDir)

    val result = downloader.startDownload(model)

    assertTrue(result.isSuccess)
    assertEquals(LocalModelStatus.NOT_DOWNLOADED, downloader.states.first().status)
    assertTrue(
      downloader.states.any {
        it.status == LocalModelStatus.DOWNLOADING && it.downloadProgress > 0f
      }
    )
    assertEquals(LocalModelStatus.DOWNLOADED, downloader.states.last().status)
    assertEquals(1f, downloader.states.last().downloadProgress)
    assertTrue(File(modelDir, model.fileName).exists())
    assertFalse(partialFile().exists())
  }

  @Test
  fun startDownload_networkFailureAtHalf_retainsPartialAndReportsError() = runTest {
    val downloader = FakeModelDownloader(modelDir, failureMode = FailureMode.NETWORK_AT_HALF)

    val result = downloader.startDownload(model)

    assertTrue(result.isFailure)
    assertEquals(LocalModelStatus.ERROR, downloader.states.last().status)
    assertEquals(TEST_BYTES.size / 2L, partialFile().length())
    assertFalse(File(modelDir, model.fileName).exists())
  }

  @Test
  fun startDownload_wifiDisconnectDuringDownload_retainsPartialAndReportsError() = runTest {
    val downloader =
      FakeModelDownloader(modelDir, failureMode = FailureMode.WIFI_DISCONNECT_AT_HALF)

    val result = downloader.startDownload(model)

    assertTrue(result.isFailure)
    assertEquals(LocalModelStatus.ERROR, downloader.states.last().status)
    assertEquals(TEST_BYTES.size / 2L, partialFile().length())
    assertFalse(File(modelDir, model.fileName).exists())
  }

  @Test
  fun startDownload_resumeFromPartial_continuesFromHalfAndCompletes() = runTest {
    partialFile().writeBytes(TEST_BYTES.copyOfRange(0, TEST_BYTES.size / 2))
    val downloader = FakeModelDownloader(modelDir)

    val result = downloader.startDownload(model)

    assertTrue(result.isSuccess)
    assertEquals(TEST_BYTES.size / 2L, downloader.states[1].downloadBytes)
    assertEquals(LocalModelStatus.DOWNLOADED, downloader.states.last().status)
    assertEquals(TEST_BYTES.toList(), File(modelDir, model.fileName).readBytes().toList())
    assertFalse(partialFile().exists())
  }

  @Test
  fun startDownload_checksumMismatch_deletesFilesAndReportsError() = runTest {
    val badModel =
      model.copy(
        expectedSha256 = "f335f2bfd1b758dc6476db16c0f41854bd6237e2658d604cbe566bcefd00a7bc"
      )
    val downloader = FakeModelDownloader(modelDir)

    val result = downloader.startDownload(badModel)

    assertTrue(result.isFailure)
    assertEquals(LocalModelStatus.ERROR, downloader.states.last().status)
    assertFalse(File(modelDir, model.fileName).exists())
    assertFalse(partialFile().exists())
  }

  @Test
  fun startDownload_insufficientStorage_rejectsAndReportsError() = runTest {
    val downloader = FakeModelDownloader(modelDir, hasStorage = false)

    val result = downloader.startDownload(model)

    assertTrue(result.isFailure)
    assertEquals(LocalModelStatus.ERROR, downloader.states.last().status)
    assertFalse(File(modelDir, model.fileName).exists())
    assertFalse(partialFile().exists())
  }

  @Test
  fun startDownload_wifiNotAvailable_rejectsAndReportsError() = runTest {
    val downloader = FakeModelDownloader(modelDir, hasWifi = false)

    val result = downloader.startDownload(model)

    assertTrue(result.isFailure)
    assertEquals(LocalModelStatus.ERROR, downloader.states.last().status)
    assertFalse(File(modelDir, model.fileName).exists())
    assertFalse(partialFile().exists())
  }

  @Test
  fun cancelDownload_retainsPartialAndReturnsToNotDownloaded() = runTest {
    val downloader = FakeModelDownloader(modelDir, failureMode = FailureMode.CANCEL_AT_HALF)

    val result = downloader.startDownload(model)

    assertTrue(result.isFailure)
    assertEquals(LocalModelStatus.NOT_DOWNLOADED, downloader.states.last().status)
    assertEquals(TEST_BYTES.size / 2L, partialFile().length())
    assertFalse(File(modelDir, model.fileName).exists())
  }

  @Test
  fun deleteModel_removesFileAndResetsStateToNotDownloaded() = runTest {
    val downloader = FakeModelDownloader(modelDir)
    downloader.startDownload(model).getOrThrow()
    assertTrue(File(modelDir, model.fileName).exists())

    val result = downloader.deleteModel(model)

    assertTrue(result.isSuccess)
    assertFalse(File(modelDir, model.fileName).exists())
    assertFalse(partialFile().exists())
    assertEquals(LocalModelStatus.NOT_DOWNLOADED, downloader.states.last().status)
  }

  @Test
  fun startDownload_concurrentCalls_secondIgnoredWhileFirstDownloading() = runTest {
    val downloader = FakeModelDownloader(modelDir, suspendAtHalf = true)

    val first = async { downloader.startDownload(model) }
    yield()
    val second = downloader.startDownload(model)

    assertTrue(second.isFailure)
    assertEquals(1, downloader.startedDownloads)
    downloader.resumeDownload()
    assertTrue(first.await().isSuccess)
    assertEquals(LocalModelStatus.DOWNLOADED, downloader.states.last().status)
  }

  private fun partialFile() = File(modelDir, ".${model.fileName}.part")

  private class FakeModelDownloader(
    private val modelDir: File,
    private val hasWifi: Boolean = true,
    private val hasStorage: Boolean = true,
    private val failureMode: FailureMode? = null,
    private val suspendAtHalf: Boolean = false,
  ) : ModelDownloadManager {
    private val state = MutableStateFlow(LocalModelState())
    private var isDownloading = false
    private var resumeDownload: (() -> Unit)? = null
    val states = mutableListOf(state.value)
    var startedDownloads = 0
      private set

    override val downloadState: Flow<LocalModelState> = state

    override suspend fun startDownload(model: LocalModelInfo): Result<Unit> =
      runCatching {
          if (isDownloading) {
            throw IllegalStateException("Download already in progress")
          }
          isDownloading = true
          startedDownloads++
          modelDir.mkdirs()
          if (!hasWifi) {
            fail("Wi-Fi or unmetered network is required")
          }
          if (!hasStorage) {
            fail("Insufficient storage for model download")
          }

          val targetFile = File(modelDir, model.fileName)
          val partialFile = File(modelDir, ".${model.fileName}.part")
          val existingBytes = partialFile.takeIf { it.exists() }?.length() ?: 0L
          emit(downloadingState(existingBytes, model.sizeBytes))

          val halfBytes = TEST_BYTES.size / 2
          if (existingBytes < halfBytes) {
            partialFile.appendBytes(TEST_BYTES.copyOfRange(existingBytes.toInt(), halfBytes))
            emit(downloadingState(halfBytes.toLong(), model.sizeBytes))
          }

          if (suspendAtHalf) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
              resumeDownload = { continuation.resume(Unit) { _, _, _ -> } }
            }
          }

          when (failureMode) {
            FailureMode.NETWORK_AT_HALF -> fail("Network failed")
            FailureMode.WIFI_DISCONNECT_AT_HALF -> fail("Wi-Fi disconnected during download")
            FailureMode.CANCEL_AT_HALF -> {
              cancelDownload()
              throw IllegalStateException("Download cancelled")
            }
            null -> Unit
          }

          partialFile.appendBytes(
            TEST_BYTES.copyOfRange(partialFile.length().toInt(), TEST_BYTES.size)
          )
          emit(downloadingState(TEST_BYTES.size.toLong(), model.sizeBytes))
          partialFile.renameTo(targetFile)

          if (sha256(targetFile.readBytes()) != model.expectedSha256) {
            targetFile.delete()
            partialFile.delete()
            fail("Downloaded model checksum mismatch")
          }

          emit(
            LocalModelState(
              status = LocalModelStatus.DOWNLOADED,
              downloadProgress = 1f,
              downloadBytes = targetFile.length(),
              totalBytes = model.sizeBytes,
              modelPath = targetFile.absolutePath,
            )
          )
        }
        .also {
          isDownloading = false
        }

    override suspend fun cancelDownload() {
      emit(LocalModelState(status = LocalModelStatus.NOT_DOWNLOADED))
    }

    override suspend fun deleteModel(model: LocalModelInfo): Result<Unit> = runCatching {
      File(modelDir, model.fileName).delete()
      File(modelDir, ".${model.fileName}.part").delete()
      emit(LocalModelState(status = LocalModelStatus.NOT_DOWNLOADED))
    }

    override suspend fun importModel(model: LocalModelInfo, uri: Uri): Result<Unit> =
      Result.success(Unit)

    override fun isModelPresent(model: LocalModelInfo): Boolean {
      return File(modelDir, model.fileName).exists()
    }

    private fun fail(message: String): Nothing {
      emit(LocalModelState(status = LocalModelStatus.ERROR, errorMessage = message))
      throw IllegalStateException(message)
    }

    private fun emit(newState: LocalModelState) {
      state.value = newState
      states.add(newState)
    }

    fun resumeDownload() {
      resumeDownload?.invoke()
      resumeDownload = null
    }

    private fun downloadingState(downloadedBytes: Long, totalBytes: Long) =
      LocalModelState(
        status = LocalModelStatus.DOWNLOADING,
        downloadProgress = downloadedBytes.toFloat() / totalBytes,
        downloadBytes = downloadedBytes,
        totalBytes = totalBytes,
      )
  }

  private enum class FailureMode {
    NETWORK_AT_HALF,
    WIFI_DISCONNECT_AT_HALF,
    CANCEL_AT_HALF,
  }

  private companion object {
    val TEST_BYTES = "ArchiveAssistant local model bytes".encodeToByteArray()

    fun sha256(bytes: ByteArray): String {
      val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
      return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
  }
}
