package com.lyihub.archiveassistant.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.lyihub.archiveassistant.R
import kotlin.math.roundToInt

internal class MemorialAssets(context: Context) {
  private val resources = context.resources
  private val packageName = context.packageName
  private val articleImageCache = mutableMapOf<String, Bitmap?>()

  val paperTexture: Bitmap? =
    BitmapFactory.decodeResource(
      resources,
      R.drawable.memorial_xuan_paper,
    )

  private val fallbackCoverTexture: Bitmap? =
    BitmapFactory.decodeResource(
      resources,
      R.drawable.memorial_cover_pattern,
    )

  private val fixedFirstCoverTexture: Bitmap? = fallbackCoverTexture

  private val generatedCoverTextures: List<Bitmap?> =
    listOf(
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_02),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_03),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_04),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_05),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_06),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_07),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_08),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_09),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_10),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_11),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_12),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_13),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_14),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_15),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_16),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_17),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_18),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_19),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_20),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_21),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_22),
      BitmapFactory.decodeResource(resources, R.drawable.memorial_cover_23),
    )

  val buttonTexture: Bitmap? =
    BitmapFactory.decodeResource(
      resources,
      R.drawable.memorial_button_bg,
    )

  val buttonAspectRatio: Float =
    buttonTexture?.let { texture ->
      texture.width.toFloat() / texture.height.toFloat()
    } ?: (413f / 141f)

  val completionTexture: Bitmap? =
    BitmapFactory.decodeResource(
      resources,
      R.drawable.memorial_completion_bg,
    )

  val pendingNoteStampTexture: Bitmap? =
    BitmapFactory.decodeResource(
      resources,
      R.drawable.pending_note_stamp,
    )

  val coverCornerTexture: Bitmap? =
    BitmapFactory.decodeResource(
      resources,
      R.drawable.memorial_cover_corner,
    )

  val heritageTypeface: Typeface =
    ResourcesCompat.getFont(context, R.font.ma_shan_zheng_regular)
      ?: Typeface.create(Typeface.SERIF, Typeface.NORMAL)

  val stampTypeface: Typeface =
    ResourcesCompat.getFont(context, R.font.san_ji_xing_kai_jian_ti_cu) ?: heritageTypeface

  val songTypeface: Typeface =
    ResourcesCompat.getFont(context, R.font.dinglie_song_typeface)
      ?: Typeface.create(Typeface.SERIF, Typeface.NORMAL)

  private val stampLikeTexture: Bitmap? =
    BitmapFactory.decodeResource(
        resources,
        R.drawable.memorial_stamp_like,
      )
      ?.let(::buildCinnabarStampTexture)

  private val stampDislikeTexture: Bitmap? =
    BitmapFactory.decodeResource(
        resources,
        R.drawable.memorial_stamp_dislike,
      )
      ?.let(::buildCinnabarStampTexture)

  fun coverTextureFor(coverSequenceIndex: Int): Bitmap? {
    // The first demo memorial intentionally keeps the original approved cover.
    // Later generated memorials walk the pool before cycling, so covers stay fixed
    // per sequence and avoid repeats until the available pool is exhausted.
    if (coverSequenceIndex <= 0) return fixedFirstCoverTexture
    if (generatedCoverTextures.isEmpty()) return fallbackCoverTexture
    val textureIndex = coverSequenceIndex - 1
    return generatedCoverTextures[
      MemorialCoverSequence.texturePoolIndex(textureIndex, generatedCoverTextures.size)]
      ?: fallbackCoverTexture
  }

  fun stampTextureFor(stamp: MemorialStamp): Bitmap? {
    return when (stamp) {
      MemorialStamp.Approve,
      MemorialStamp.Keep,
      MemorialStamp.Like -> stampLikeTexture
      MemorialStamp.Reject,
      MemorialStamp.Dislike -> stampDislikeTexture
      MemorialStamp.Collapse -> null
    }
  }

  fun articleImageFor(imageResName: String?): Bitmap? {
    val normalized = imageResName?.trim().orEmpty()
    if (normalized.isEmpty()) return null
    return articleImageCache.getOrPut(normalized) {
      val resourceId = resources.getIdentifier(normalized, "drawable", packageName)
      if (resourceId == 0) null else BitmapFactory.decodeResource(resources, resourceId)
    }
  }

  private fun buildCinnabarStampTexture(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    val input = IntArray(width * height)
    val output = IntArray(width * height)
    source.getPixels(input, 0, width, 0, 0, width, height)
    for (index in input.indices) {
      val color = input[index]
      val red = AndroidColor.red(color)
      val green = AndroidColor.green(color)
      val blue = AndroidColor.blue(color)
      val luminance = red * 0.299f + green * 0.587f + blue * 0.114f
      val maskAlpha = ((luminance - 8f) / 247f * 255f).roundToInt().coerceIn(0, 255)
      output[index] =
        if (maskAlpha <= 2) {
          AndroidColor.TRANSPARENT
        } else {
          AndroidColor.argb(
            maskAlpha,
            AndroidColor.red(STAMP_RED),
            AndroidColor.green(STAMP_RED),
            AndroidColor.blue(STAMP_RED),
          )
        }
    }
    return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
  }
}
