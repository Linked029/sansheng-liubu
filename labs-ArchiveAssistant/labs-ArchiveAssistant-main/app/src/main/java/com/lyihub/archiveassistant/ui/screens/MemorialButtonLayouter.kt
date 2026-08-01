package com.lyihub.archiveassistant.ui.screens

import android.graphics.RectF

internal class MemorialButtonLayouter(private val aspectRatio: Float) {
  fun layoutButtonRow(
    rects: List<RectF>,
    centerX: Float,
    top: Float,
    height: Float,
    desiredGap: Float,
    maxWidth: Float,
  ) {
    if (rects.isEmpty()) return
    val desiredButtonWidth = height * aspectRatio
    val desiredTotalWidth = desiredButtonWidth * rects.size + desiredGap * (rects.size - 1)
    val scale =
      if (desiredTotalWidth > maxWidth && desiredTotalWidth > 0f) {
        (maxWidth / desiredTotalWidth).coerceIn(0f, 1f)
      } else {
        1f
      }
    val buttonHeight = height * scale
    val buttonWidth = buttonHeight * aspectRatio
    val gap = desiredGap * scale
    val totalWidth = buttonWidth * rects.size + gap * (rects.size - 1)
    val rectTop = top + (height - buttonHeight) / 2f
    var left = centerX - totalWidth / 2f
    rects.forEach { rect ->
      rect.set(left, rectTop, left + buttonWidth, rectTop + buttonHeight)
      left += buttonWidth + gap
    }
  }

  fun layoutAspectButton(
    rect: RectF,
    centerX: Float,
    top: Float,
    height: Float,
    maxWidth: Float,
  ) {
    val desiredWidth = height * aspectRatio
    val scale =
      if (desiredWidth > maxWidth && desiredWidth > 0f) {
        (maxWidth / desiredWidth).coerceIn(0f, 1f)
      } else {
        1f
      }
    val buttonHeight = height * scale
    val width = buttonHeight * aspectRatio
    val left = centerX - width / 2f
    val rectTop = top + (height - buttonHeight) / 2f
    rect.set(left, rectTop, left + width, rectTop + buttonHeight)
  }
}
