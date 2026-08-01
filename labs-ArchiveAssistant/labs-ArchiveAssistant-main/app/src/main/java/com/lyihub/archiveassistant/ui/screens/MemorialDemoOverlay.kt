package com.lyihub.archiveassistant.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lyihub.archiveassistant.R
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.ui.theme.ImperialTitleFont
import kotlinx.coroutines.delay

private const val ReaderOverlayFadeMillis = 240
private const val ReaderOverlayScaleMillis = 320
private const val ReaderOverlayExitDelayMillis = 240L

@Composable
fun MemorialDemoOverlay(
  items: List<KnowledgeItem>,
  onDismiss: () -> Unit,
) {
  val foldView = remember { mutableStateOf<MemorialFoldView?>(null) }
  val dossiers = remember(items) { buildPendingDossiers(items) }
  var visible by remember { mutableStateOf(false) }
  var closing by remember { mutableStateOf(false) }
  var pendingDismiss by remember { mutableStateOf(false) }
  val alpha by
    animateFloatAsState(
      targetValue = if (visible && !closing) 1f else 0f,
      animationSpec = tween(durationMillis = ReaderOverlayFadeMillis),
      label = "memorial-demo-alpha",
    )
  val scale by
    animateFloatAsState(
      targetValue = if (visible && !closing) 1f else 0.9f,
      animationSpec = tween(durationMillis = ReaderOverlayScaleMillis),
      label = "memorial-demo-scale",
    )
  LaunchedEffect(pendingDismiss) {
    if (pendingDismiss) {
      delay(ReaderOverlayExitDelayMillis)
      onDismiss()
    }
  }
  val finishDismiss = {
    pendingDismiss = true
  }
  val autoDismiss = {
    if (!closing) {
      closing = true
      finishDismiss()
    }
  }
  val requestDismiss = {
    if (!closing) {
      closing = true
      foldView.value?.closeWithAnimation(finishDismiss) ?: finishDismiss()
    }
  }

  LaunchedEffect(Unit) {
    visible = true
  }
  BackHandler(onBack = requestDismiss)
  MemorialImmersiveSystemUi(onDispose = { foldView.value = null })

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Color(APP_BACKGROUND_BASE).copy(alpha = (alpha * 0.96f).coerceIn(0f, 1f)))
        .testTag("memorial-demo-overlay")
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize().graphicsLayer {
          this.alpha = alpha
          scaleX = scale
          scaleY = scale
        }
    ) {
      AndroidView(
        factory = { context ->
          MemorialFoldView(context).apply {
            foldView.value = this
            setAutoDismissHandler(autoDismiss)
            setCloseAnimationFinishedHandler(autoDismiss)
            setReaderMode(MemorialReaderMode.ReviewStack)
            setDossiers(dossiers)
          }
        },
        update = { view ->
          foldView.value = view
          view.setAutoDismissHandler(autoDismiss)
          view.setCloseAnimationFinishedHandler(autoDismiss)
          view.setReaderMode(MemorialReaderMode.ReviewStack)
          view.setDossiers(dossiers)
        },
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

@Composable
fun ArticleMemorialReaderOverlay(
  item: KnowledgeItem,
  onDismiss: () -> Unit,
) {
  val foldView = remember { mutableStateOf<MemorialFoldView?>(null) }
  val dossier = remember(item) { buildPendingDossier(item) }
  val coverSequenceOffset = remember(item) { stableArticleCoverOffset(item) }
  var visible by remember { mutableStateOf(false) }
  var closing by remember { mutableStateOf(false) }
  var pendingDismiss by remember { mutableStateOf(false) }
  val alpha by
    animateFloatAsState(
      targetValue = if (visible && !closing) 1f else 0f,
      animationSpec = tween(durationMillis = 280),
      label = "article-reader-alpha",
    )
  val scale by
    animateFloatAsState(
      targetValue = if (visible && !closing) 1f else 0.88f,
      animationSpec = tween(durationMillis = 360),
      label = "article-reader-scale",
    )
  LaunchedEffect(pendingDismiss) {
    if (pendingDismiss) {
      delay(ReaderOverlayExitDelayMillis)
      onDismiss()
    }
  }
  val completeDismiss = {
    closing = true
    pendingDismiss = true
  }
  val requestDismiss = {
    if (!closing) {
      closing = true
      foldView.value?.closeWithAnimation(completeDismiss) ?: completeDismiss()
    }
  }
  val finishAfterViewClose = {
    if (!closing) {
      closing = true
      completeDismiss()
    }
  }

  LaunchedEffect(Unit) {
    visible = true
  }
  LaunchedEffect(foldView.value, visible) {
    if (visible) {
      kotlinx.coroutines.delay(380L)
      foldView.value?.expandCurrentCover()
    }
  }
  BackHandler(onBack = requestDismiss)
  MemorialImmersiveSystemUi(onDispose = { foldView.value = null })

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Color(APP_BACKGROUND_BASE).copy(alpha = (0.94f * alpha).coerceIn(0f, 1f)))
        .testTag("article-memorial-reader-overlay")
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize().graphicsLayer {
          this.alpha = alpha
          scaleX = scale
          scaleY = scale
        }
    ) {
      AndroidView(
        factory = { context ->
          MemorialFoldView(context).apply {
            foldView.value = this
            setReaderMode(MemorialReaderMode.ArticleReader)
            setCoverSequenceOffset(coverSequenceOffset)
            setAutoDismissHandler(requestDismiss)
            setCloseAnimationFinishedHandler(finishAfterViewClose)
            setDossiers(listOf(dossier))
          }
        },
        update = { view ->
          foldView.value = view
          view.setReaderMode(MemorialReaderMode.ArticleReader)
          view.setCoverSequenceOffset(coverSequenceOffset)
          view.setAutoDismissHandler(requestDismiss)
          view.setCloseAnimationFinishedHandler(finishAfterViewClose)
          view.setDossiers(listOf(dossier))
        },
        modifier = Modifier.fillMaxSize(),
      )
      ReaderDismissButton(
        onClick = requestDismiss,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
      )
    }
  }
}

@Composable
private fun ReaderDismissButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.clickable(onClick = onClick).width(116.dp).height(42.dp),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      painter = painterResource(id = R.drawable.memorial_button_bg),
      contentDescription = null,
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.FillBounds,
    )
    Text(
      text = "收起",
      style = MaterialTheme.typography.titleSmall.copy(fontFamily = ImperialTitleFont),
      color = Color(0xFF3C3022),
      fontWeight = FontWeight.Normal,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
    )
  }
}

internal fun buildPendingDossiers(items: List<KnowledgeItem>): List<PendingMemorialDossier> {
  val fromItems = items.take(TOTAL_PENDING_MEMORIALS).map(::buildPendingDossier)
  if (fromItems.size >= TOTAL_PENDING_MEMORIALS) return fromItems
  return (fromItems + fallbackPendingMemorialDossiers).take(TOTAL_PENDING_MEMORIALS)
}

internal fun buildPendingDossier(item: KnowledgeItem): PendingMemorialDossier {
  return PendingMemorialDossier(
    title = item.title,
    source = sourceLine(item),
    summary = item.summary.ifBlank { item.fullText.lineSequence().firstOrNull().orEmpty() },
    body = readingBody(item),
    tags = articleTagsFromFullText(item.fullText),
    imageResName = item.imageResName,
    createdAtEpochMillis = item.createdAtEpochMillis,
  )
}

private fun stableArticleCoverOffset(item: KnowledgeItem): Int {
  return MemorialCoverSequence.articleCoverOffset(item, MemorialCoverResources.size)
}

private fun sourceLine(item: KnowledgeItem): String {
  val source =
    item.fullText
      .lineSequence()
      .firstOrNull { it.startsWith("来源：") }
      ?.removePrefix("来源：")
      ?.takeIf { it.isNotBlank() }
  return source ?: item.sourceUrl ?: "聚合拾遗"
}

private fun readingBody(item: KnowledgeItem): String {
  return item.fullText.substringAfter("整理正文", item.fullText).trim().ifBlank {
    item.summary.ifBlank { item.title }
  }
}

private fun articleTagsFromFullText(fullText: String): List<String> {
  return fullText
    .lineSequence()
    .firstOrNull { it.startsWith("标签：") }
    ?.removePrefix("标签：")
    ?.split("·", "、", ",", "，")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.distinct()
    ?.take(4)
    .orEmpty()
}
