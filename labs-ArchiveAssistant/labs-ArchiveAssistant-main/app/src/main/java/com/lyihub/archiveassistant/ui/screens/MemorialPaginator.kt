package com.lyihub.archiveassistant.ui.screens

import android.text.TextPaint
import kotlin.math.min
import kotlin.math.roundToInt

internal class MemorialPaginator(
  private val textPaint: TextPaint,
  private val lineHeightMultiplier: Float = 1.82f,
  private val fallbackText: String = "此奏章暂无正文。",
) {
  fun paginate(
    body: String,
    contentWidth: Int,
    firstPageHeight: Float,
    pageHeight: Float,
  ): List<String> {
    val normalized = normalizeBody(body)
    return paginateTextByHeight(
      text = normalized,
      width = contentWidth.coerceAtLeast(1),
      firstPageHeight = firstPageHeight,
      pageHeight = pageHeight,
    )
  }

  private fun normalizeBody(body: String): String {
    return body
      .lineSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .joinToString("\n\n")
      .ifBlank { fallbackText }
  }

  private fun paginateTextByHeight(
    text: String,
    width: Int,
    firstPageHeight: Float,
    pageHeight: Float,
  ): List<String> {
    val segments = mutableListOf<String>()
    var remaining = text.trim()
    var firstPage = true
    var guard = 0
    while (remaining.isNotBlank() && guard < 80) {
      val availableHeight = if (firstPage) firstPageHeight else pageHeight
      val splitEnd = fittingTextEnd(remaining, width, availableHeight)
      val chunk = remaining.take(splitEnd).trim()
      if (chunk.isBlank()) break
      segments += chunk
      remaining = remaining.drop(splitEnd).trimStart()
      firstPage = false
      guard += 1
    }
    return segments.ifEmpty { listOf(fallbackText) }
  }

  private fun fittingTextEnd(
    text: String,
    width: Int,
    maxHeight: Float,
  ): Int {
    val layout = buildTextLayout(text, textPaint, width, lineHeightMultiplier, false)
    if (layout.height <= maxHeight) return text.length
    var lastFittingLine = -1
    for (line in 0 until layout.lineCount) {
      if (layout.getLineBottom(line) <= maxHeight) {
        lastFittingLine = line
      } else {
        break
      }
    }
    if (lastFittingLine < 0) return min(text.length, 24).coerceAtLeast(1)
    val rawEnd = layout.getLineEnd(lastFittingLine).coerceIn(1, text.length)
    if (rawEnd >= text.length) return text.length
    val earliestPreferred = (rawEnd * 0.52f).roundToInt().coerceAtLeast(1)
    val preferredEnd =
      listOf('\n', '。', '！', '？', '；')
        .map { mark -> text.lastIndexOf(mark, startIndex = rawEnd - 1) }
        .filter { it >= earliestPreferred }
        .maxOrNull()
    return (preferredEnd?.plus(1) ?: rawEnd).coerceIn(1, text.length)
  }
}
