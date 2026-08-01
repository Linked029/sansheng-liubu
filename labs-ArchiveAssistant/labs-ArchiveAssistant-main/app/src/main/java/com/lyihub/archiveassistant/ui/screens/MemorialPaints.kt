package com.lyihub.archiveassistant.ui.screens

import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.text.TextPaint
import kotlin.math.max

internal class MemorialPaints(
  private val density: Float,
  private val scaledDensity: Float,
  assets: MemorialAssets,
) {
  val background =
    Paint().apply {
      style = Paint.Style.FILL
      color = APP_BACKGROUND_BASE
    }

  val article =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = AndroidColor.WHITE
    }

  val shade =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = AndroidColor.BLACK
    }

  val foldShadow =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
    }

  val paperAging =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
    }

  val paper =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
      alpha = 138
    }

  val cover = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

  val buttonImage =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
      alpha = 232
    }

  val completionImage = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

  val coverCorner = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

  val dashed =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 1f
      color = AndroidColor.rgb(221, 221, 221)
      pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

  val surface = Paint(Paint.ANTI_ALIAS_FLAG)

  val crease =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = max(0.45f, density * 0.32f)
      color = AndroidColor.argb(12, 126, 111, 92)
    }

  val quote =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.rgb(51, 51, 51)
      textSize = sp(17f)
      typeface = assets.heritageTypeface
    }

  val author =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.rgb(38, 38, 38)
      textSize = sp(12f)
      typeface = assets.songTypeface
    }

  val title =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.rgb(26, 26, 26)
      textSize = sp(24f)
      typeface = assets.heritageTypeface
      textAlign = Paint.Align.CENTER
      letterSpacing = 0.08f
    }

  val itemTitle =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.rgb(26, 26, 26)
      textSize = sp(18f)
      typeface = assets.songTypeface
      textAlign = Paint.Align.LEFT
    }

  val itemMeta =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.rgb(60, 48, 34)
      textSize = sp(12f)
      typeface = assets.songTypeface
      textAlign = Paint.Align.LEFT
    }

  val meta =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.rgb(60, 48, 34)
      textSize = sp(13f)
      typeface = assets.songTypeface
      textAlign = Paint.Align.CENTER
    }

  val cinnabar =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.rgb(230, 0, 18)
      textSize = sp(17f)
      typeface = assets.heritageTypeface
    }

  val coverTitle =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.rgb(26, 26, 26)
      textSize = sp(58f)
      typeface = assets.heritageTypeface
      textAlign = Paint.Align.CENTER
    }

  val gold =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 1.5f * density
      color = IMPERIAL_GOLD
    }

  val seal =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2f * density
      color = AndroidColor.rgb(184, 62, 47)
    }

  val sealText =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.rgb(184, 62, 47)
      textSize = sp(22f)
      typeface = assets.heritageTypeface
      textAlign = Paint.Align.CENTER
    }

  val stamp =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 4f * density
      color = STAMP_RED
    }

  val stampImage = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

  val stampErase =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

  val stampText =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = STAMP_PAPER
      textSize = sp(38f)
      typeface = assets.stampTypeface
      textAlign = Paint.Align.CENTER
    }

  val toolbar =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = AndroidColor.argb(214, 255, 246, 221)
    }

  val toolbarStroke =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 1.2f * density
      color =
        AndroidColor.argb(
          178,
          AndroidColor.red(IMPERIAL_GOLD),
          AndroidColor.green(IMPERIAL_GOLD),
          AndroidColor.blue(IMPERIAL_GOLD),
        )
    }

  val layerAlpha = Paint(Paint.ANTI_ALIAS_FLAG)

  val toolbarText =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = MEMORIAL_INK_BROWN
      textSize = sp(18f)
      typeface = assets.heritageTypeface
      textAlign = Paint.Align.CENTER
    }

  private fun sp(value: Float): Float = value * scaledDensity
}
