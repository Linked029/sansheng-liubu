package com.lyihub.archiveassistant.data

import android.content.Context
import android.net.Uri
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Node

interface DocumentContentExtractor {
  suspend fun extract(
    uri: Uri,
    format: DocumentFormat,
    fileName: String?,
  ): DocumentContentExtractionResult
}

sealed class DocumentContentExtractionResult {
  data class Success(val content: ExtractedDocumentContent) : DocumentContentExtractionResult()

  data class Failure(val message: String) : DocumentContentExtractionResult()
}

data class ExtractedDocumentContent(
  val fileName: String,
  val format: DocumentFormat,
  val extractedText: String,
  val originalCharCount: Int,
  val isTruncated: Boolean,
)

class DefaultDocumentContentExtractor(private val context: Context) : DocumentContentExtractor {
  init {
    PDFBoxResourceLoader.init(context)
  }

  override suspend fun extract(
    uri: Uri,
    format: DocumentFormat,
    fileName: String?,
  ): DocumentContentExtractionResult =
    withContext(Dispatchers.IO) {
      val resolvedName =
        fileName?.trim()?.takeIf { it.isNotBlank() } ?: resolveDisplayName(context, uri)
      val detectedFormat =
        if (format != DocumentFormat.UNKNOWN) format else detectFormatFromName(resolvedName)
      val content =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
              when (detectedFormat) {
                DocumentFormat.TXT,
                DocumentFormat.MARKDOWN -> readTextDocument(input, resolvedName, detectedFormat)
                DocumentFormat.DOCX -> readDocxDocument(input, resolvedName)
                DocumentFormat.PDF -> readPdfDocument(input, resolvedName)
                DocumentFormat.UNKNOWN -> throw UnsupportedOperationException("暂不支持该文档格式")
              }
            } ?: throw IOException("无法读取文档")
          }
          .getOrElse { error ->
            return@withContext DocumentContentExtractionResult.Failure(
              mapDocumentExtractionError(error)
            )
          }

      if (content.extractedText.isBlank()) {
        DocumentContentExtractionResult.Failure("文档内容为空或无法解析")
      } else {
        DocumentContentExtractionResult.Success(content)
      }
    }
}

internal fun interface PdfTextExtractor {
  fun extractText(input: InputStream): String
}

internal object PdfBoxPdfTextExtractor : PdfTextExtractor {
  override fun extractText(input: InputStream): String =
    PDDocument.load(input).use { document ->
      PDFTextStripper().getText(document)
    }
}

internal fun readTextDocument(
  input: InputStream,
  fileName: String,
  format: DocumentFormat,
): ExtractedDocumentContent {
  val bytes = input.readBytesUpTo(MAX_TEXT_BYTES + 1)
  val rawText = decodeText(bytes.copyOfRange(0, minOf(bytes.size, MAX_TEXT_BYTES)))
  return boundedContent(fileName, format, rawText, bytes.size > MAX_TEXT_BYTES)
}

internal fun readDocxDocument(input: InputStream, fileName: String): ExtractedDocumentContent {
  ZipInputStream(input).use { zipInput ->
    while (true) {
      val entry = zipInput.nextEntry ?: break
      if (entry.name == "word/document.xml") {
        val xmlBytes = zipInput.readBytesUpTo(MAX_DOCX_XML_BYTES + 1)
        val text =
          extractDocxXmlText(xmlBytes.copyOfRange(0, minOf(xmlBytes.size, MAX_DOCX_XML_BYTES)))
        return boundedContent(
          fileName,
          DocumentFormat.DOCX,
          text,
          xmlBytes.size > MAX_DOCX_XML_BYTES,
        )
      }
    }
  }
  throw IOException("不是有效的 DOCX 文档")
}

internal fun readPdfDocument(
  input: InputStream,
  fileName: String,
  pdfTextExtractor: PdfTextExtractor = PdfBoxPdfTextExtractor,
): ExtractedDocumentContent =
  boundedContent(
    fileName = fileName,
    format = DocumentFormat.PDF,
    rawText = pdfTextExtractor.extractText(input),
    upstreamTruncated = false,
  )

private fun boundedContent(
  fileName: String,
  format: DocumentFormat,
  rawText: String,
  upstreamTruncated: Boolean,
): ExtractedDocumentContent {
  val normalized = normalizeWhitespace(rawText)
  val bounded = normalized.take(MAX_EXTRACTED_CHARS)
  return ExtractedDocumentContent(
    fileName = fileName,
    format = format,
    extractedText = bounded.take(MAX_REQUEST_CONTEXT_CHARS),
    originalCharCount = normalized.length,
    isTruncated =
      upstreamTruncated ||
        normalized.length > bounded.length ||
        bounded.length > MAX_REQUEST_CONTEXT_CHARS,
  )
}

private fun extractDocxXmlText(xmlBytes: ByteArray): String {
  val document =
    DocumentBuilderFactory.newInstance()
      .apply {
        isNamespaceAware = true
      }
      .newDocumentBuilder()
      .parse(ByteArrayInputStream(xmlBytes))
  val builder = StringBuilder()
  appendDocxNodeText(document.documentElement, builder)
  return builder.toString()
}

private fun appendDocxNodeText(node: Node, builder: StringBuilder) {
  if (builder.length >= MAX_EXTRACTED_CHARS) return
  when (node.localName ?: node.nodeName.substringAfter(':')) {
    "t" -> builder.append(node.textContent)
    "tab" -> builder.append('\t')
    "br" -> builder.append('\n')
  }
  val children = node.childNodes
  for (index in 0 until children.length) {
    appendDocxNodeText(children.item(index), builder)
    if (builder.length >= MAX_EXTRACTED_CHARS) return
  }
  if ((node.localName ?: node.nodeName.substringAfter(':')) == "p") {
    builder.append('\n')
  }
}

private fun InputStream.readBytesUpTo(limit: Int): ByteArray {
  val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
  val output = java.io.ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE))
  var remaining = limit
  while (remaining > 0) {
    val read = read(buffer, 0, minOf(buffer.size, remaining))
    if (read == -1) break
    output.write(buffer, 0, read)
    remaining -= read
  }
  return output.toByteArray()
}

private fun decodeText(bytes: ByteArray): String {
  if (bytes.startsWith(0xEF, 0xBB, 0xBF))
    return bytes.drop(3).toByteArray().toString(Charsets.UTF_8)
  if (bytes.startsWith(0xFF, 0xFE)) return bytes.drop(2).toByteArray().toString(Charsets.UTF_16LE)
  if (bytes.startsWith(0xFE, 0xFF)) return bytes.drop(2).toByteArray().toString(Charsets.UTF_16BE)
  return bytes.toString(Charsets.UTF_8)
}

private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
  size >= prefix.size && prefix.indices.all { this[it].toInt() and 0xFF == prefix[it] }

private fun normalizeWhitespace(value: String): String =
  value
    .replace("\u0000", " ")
    .lineSequence()
    .joinToString("\n") { line ->
      line.trim().replace(INLINE_WHITESPACE_REGEX, " ")
    }
    .trim()

private fun detectFormatFromName(fileName: String): DocumentFormat =
  when (fileName.substringAfterLast('.', "").lowercase()) {
    "txt" -> DocumentFormat.TXT
    "md",
    "markdown" -> DocumentFormat.MARKDOWN
    "docx" -> DocumentFormat.DOCX
    "pdf" -> DocumentFormat.PDF
    else -> DocumentFormat.UNKNOWN
  }

private fun mapDocumentExtractionError(error: Throwable): String =
  when (error) {
    is UnsupportedOperationException -> error.message ?: "暂不支持该文档格式"
    is IOException -> "文档读取失败，请检查文件是否可访问"
    else -> "文档解析失败，请稍后重试"
  }

private const val MAX_TEXT_BYTES = 512 * 1024
private const val MAX_DOCX_XML_BYTES = 2 * 1024 * 1024
private const val MAX_EXTRACTED_CHARS = 60_000
private const val MAX_REQUEST_CONTEXT_CHARS = 16_000
private val INLINE_WHITESPACE_REGEX = Regex("[ \\t\\x0B\\f\\r]+")
