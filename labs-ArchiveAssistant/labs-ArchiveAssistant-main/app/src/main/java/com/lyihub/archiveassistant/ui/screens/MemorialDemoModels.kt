package com.lyihub.archiveassistant.ui.screens

import android.graphics.Color as AndroidColor
import com.lyihub.archiveassistant.util.toChineseCount

internal enum class MemorialPageType {
  Cover,
  Directory,
  BodyLeft,
  BodyRight,
  End,
}

internal data class MemorialPage(
  val type: MemorialPageType,
  val pageNumber: String,
  val dossierIndex: Int = 0,
  val bodySegmentIndex: Int = 0,
  val bodyText: String? = null,
)

internal data class ArticleLayout(
  val page: MemorialPage,
  val pageIndex: Int,
  val left: Float,
  val top: Float,
  val width: Float,
  val height: Float,
)

internal data class FoldTransform(
  val rotationY: Float,
  val pivotX: Float,
  val shadingAlpha: Float,
  val edgeShadowProgress: Float,
  val visible: Boolean,
)

internal data class OpeningPlacement(
  val article: ArticleLayout,
  val left: Float,
  val pivotX: Float,
  val rotationY: Float,
  val foldAmount: Float,
)

internal enum class MemorialStage {
  CoverOnly,
  Opening,
  Expanded,
  Closing,
  Completed,
}

internal enum class MemorialStamp(val label: String) {
  Approve("准"),
  Reject("驳"),
  Keep("留中"),
  Like("朕喜欢"),
  Dislike("朕不喜欢"),
  Collapse("收起"),
}

internal enum class StampCompletion {
  ResetCover,
  AutoDismiss,
}

internal enum class MemorialReaderMode {
  ReviewStack,
  ArticleReader,
}

internal enum class CoverVerdictMotion {
  Gesture,
  Button,
}

internal val STAMP_RED: Int = AndroidColor.rgb(178, 37, 31)
internal val APP_BACKGROUND_BASE: Int = AndroidColor.rgb(252, 251, 246)
internal val APP_XUAN_PAPER_BASE: Int = AndroidColor.rgb(252, 251, 246)
internal val STAMP_PAPER: Int = AndroidColor.rgb(247, 240, 219)
internal val IMPERIAL_GOLD: Int = AndroidColor.rgb(166, 126, 45)
internal val IMPERIAL_GOLD_DARK: Int = AndroidColor.rgb(104, 75, 26)
internal val MEMORIAL_INK_BROWN: Int = AndroidColor.rgb(78, 52, 31)

internal const val TOTAL_PENDING_MEMORIALS = 6
internal const val MEMORIAL_OPEN_CLOSE_DURATION_MS = 1_600L
internal const val COVER_VERDICT_DURATION_MS = 1_500L
internal const val SUMMARY_FADE_DURATION_MS = 150L

internal data class PendingMemorialSummary(
  val title: String,
  val source: String,
  val summary: String,
)

internal data class PendingMemorialDossier(
  val title: String,
  val source: String,
  val summary: String,
  val body: String,
  val tags: List<String>,
  val imageResName: String? = null,
  val createdAtEpochMillis: Long,
)

internal val pendingMemorialSummaries: List<PendingMemorialSummary> =
  listOf(
    PendingMemorialSummary(
      title = "江南水患赈灾折",
      source = "工部侍郎 张廷玉",
      summary = "江南诸府雨水连绵，田庐多毁，乞速拨银粮，以济流民。",
    ),
    PendingMemorialSummary(
      title = "西北边务军情折",
      source = "兵部尚书",
      summary = "边外游骑频现，关防需整，候旨调兵备饷。",
    ),
    PendingMemorialSummary(
      title = "漕运改海折",
      source = "漕运总督",
      summary = "运河淤阻，漕船迟滞，奏请酌改海运，以通南北。",
    ),
    PendingMemorialSummary(
      title = "盐课亏空清查折",
      source = "户部侍郎",
      summary = "两淮盐课亏空渐巨，请遣员清核账册，严惩侵蚀。",
    ),
    PendingMemorialSummary(
      title = "书院修缮请款折",
      source = "礼部尚书",
      summary = "各省书院屋宇倾圮，士子肄业受阻，请拨帑修葺。",
    ),
    PendingMemorialSummary(
      title = "河工岁修用银折",
      source = "河道总督",
      summary = "黄河岁修将届，料物工价俱涨，乞准预拨工银。",
    ),
  )

internal fun buildMemorialPagesForDossier(
  dossierIndex: Int,
  dossier: PendingMemorialDossier,
): List<MemorialPage> {
  return buildMemorialPagesForBodySegments(
    dossierIndex = dossierIndex,
    bodySegments = splitMemorialBody(dossier.body),
  )
}

internal fun buildMemorialPagesForBodySegments(
  dossierIndex: Int,
  bodySegments: List<String>,
): List<MemorialPage> {
  val normalizedSegments = bodySegments.ifEmpty { listOf("此奏章暂无正文。") }
  return buildList {
    add(MemorialPage(MemorialPageType.Cover, pageNumeral(size + 1), dossierIndex))
    add(MemorialPage(MemorialPageType.Directory, pageNumeral(size + 1), dossierIndex))
    normalizedSegments.forEachIndexed { segmentIndex, segment ->
      add(
        MemorialPage(
          type =
            if (segmentIndex % 2 == 0) {
              MemorialPageType.BodyLeft
            } else {
              MemorialPageType.BodyRight
            },
          pageNumber = pageNumeral(size + 1),
          dossierIndex = dossierIndex,
          bodySegmentIndex = segmentIndex,
          bodyText = segment,
        )
      )
    }
    add(MemorialPage(MemorialPageType.End, "终", dossierIndex))
  }
}

internal val fallbackPendingMemorialDossiers: List<PendingMemorialDossier> =
  pendingMemorialSummaries.mapIndexed { index, summary ->
    PendingMemorialDossier(
      title = summary.title,
      source = summary.source,
      summary = summary.summary,
      body =
        "标题：${summary.title}\n\n${summary.summary}\n\n此为待批奏章演示正文，用于展示封面堆叠、横向翻阅、批复与退朝流程。第${(index + 1).toChineseCount()}封奏章在演示中保持固定顺序，便于核对批阅数量与阅读分页。",
      tags = listOf("待批", "演示"),
      createdAtEpochMillis = 1_782_700_177_000L - index * 86_400_000L,
    )
  }

internal fun splitMemorialBody(body: String): List<String> {
  val normalized =
    body
      .lineSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .joinToString("\n\n")
      .ifBlank { "此奏章暂无正文。" }
  val maxSegmentLength = 420
  val segments = mutableListOf<String>()
  var remaining = normalized
  while (remaining.length > maxSegmentLength) {
    val splitAt =
      remaining.lastIndexOf('\n', startIndex = maxSegmentLength).takeIf {
        it >= maxSegmentLength * 0.45f
      }
        ?: remaining.lastIndexOf('。', startIndex = maxSegmentLength).takeIf {
          it >= maxSegmentLength * 0.45f
        }
        ?: maxSegmentLength
    segments += remaining.substring(0, splitAt + 1).trim()
    remaining = remaining.substring(splitAt + 1).trim()
  }
  if (remaining.isNotBlank()) segments += remaining
  return segments.ifEmpty { listOf("此奏章暂无正文。") }
}

internal fun pageNumeral(number: Int): String {
  val numerals = listOf("零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖", "拾")
  return when {
    number in numerals.indices -> numerals[number]
    number < 20 -> "拾${numerals[number % 10]}"
    else -> number.toString()
  }
}
