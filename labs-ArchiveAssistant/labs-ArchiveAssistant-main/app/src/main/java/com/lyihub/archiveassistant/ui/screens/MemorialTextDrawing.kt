package com.lyihub.archiveassistant.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

internal fun drawStaticLayout(canvas: Canvas, layout: StaticLayout, left: Float, top: Float) {
  canvas.save()
  canvas.translate(left, top)
  layout.draw(canvas)
  canvas.restore()
}

internal fun drawCenteredText(
  canvas: Canvas,
  text: String,
  x: Float,
  baseline: Float,
  paint: TextPaint,
) {
  canvas.drawText(text, x, baseline, paint)
}

internal fun drawVerticalText(
  canvas: Canvas,
  text: String,
  centerX: Float,
  top: Float,
  paint: TextPaint,
  lineGap: Float,
) {
  var y = top - paint.fontMetrics.ascent
  text.forEach { char ->
    canvas.drawText(char.toString(), centerX, y, paint)
    y += paint.textSize + lineGap
  }
}

internal fun textCenterOffset(paint: Paint): Float {
  val metrics = paint.fontMetrics
  return -(metrics.ascent + metrics.descent) / 2f
}

internal fun buildTextLayout(
  text: String,
  paint: TextPaint,
  width: Int,
  lineHeightMultiplier: Float,
  justify: Boolean,
  alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
): StaticLayout {
  return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
    .setAlignment(alignment)
    .setLineSpacing(0f, lineHeightMultiplier)
    .setIncludePad(false)
    .apply {
      if (justify) {
        setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
      }
    }
    .build()
}

internal fun buildEllipsizedTextLayout(
  text: String,
  paint: TextPaint,
  width: Int,
  lineHeightMultiplier: Float,
  maxLines: Int,
  alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
): StaticLayout {
  return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
    .setAlignment(alignment)
    .setLineSpacing(0f, lineHeightMultiplier)
    .setIncludePad(false)
    .setMaxLines(maxLines)
    .setEllipsize(TextUtils.TruncateAt.END)
    .build()
}

internal fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
  val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

internal fun foldProgress(value: Float): Float {
  val t = value.coerceIn(0f, 1f)
  val smooth = t * t * (3f - 2f * t)
  val moderateEarlyResponse = 1f - (1f - t) * (1f - t)
  return (0.48f * moderateEarlyResponse + 0.52f * smooth).coerceIn(0f, 1f)
}

internal fun openingSegmentProgress(globalProgress: Float, delay: Float): Float {
  val available = (1f - delay).coerceAtLeast(0.01f)
  val t = ((globalProgress - delay) / available).coerceIn(0f, 1f)
  return 1f - (1f - t) * (1f - t) * (1f - t)
}

internal fun lerp(start: Float, end: Float, fraction: Float): Float {
  return start + (end - start) * fraction.coerceIn(0f, 1f)
}
