package com.lyihub.archiveassistant.ui.screens

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.TextPaint
import android.text.TextUtils
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import com.lyihub.archiveassistant.util.toChineseCount
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class MemorialToolbarButton {
  CoverSummary,
  CoverOpen,
  CoverReject,
  CoverApprove,
  CoverKeep,
  Like,
  Dislike,
  Collapse,
}

private const val MinReadableSpreadPageWidthDp = 258f
private const val StablePaperAgingSeed = 1

private data class SizeF(
  val width: Float,
  val height: Float,
)

internal class MemorialFoldView(context: Context) : View(context) {
  private val displayDensity = resources.displayMetrics.density
  private val scaledDensity = displayDensity * resources.configuration.fontScale
  private val camera = Camera()
  private val matrix = Matrix()
  private val articleRect = RectF()
  private val config = ViewConfiguration.get(context)
  private val touchSlop = config.scaledTouchSlop

  private val assets = MemorialAssets(context)
  private val paints = MemorialPaints(displayDensity, scaledDensity, assets)
  private val paginator = MemorialPaginator(paints.author)
  private val buttonLayouter = MemorialButtonLayouter(assets.buttonAspectRatio)
  private val toolbarButtonRect1 = RectF()
  private val toolbarButtonRect2 = RectF()
  private val collapseButtonRect = RectF()
  private val coverActionLeftRect = RectF()
  private val coverActionRightRect = RectF()
  private val coverActionKeepRect = RectF()
  private val coverActionSummaryRect = RectF()
  private val coverActionOpenRect = RectF()
  private val alphaLayerRect = RectF()
  private val coverTextureSrcRect = Rect()
  private val articleImageSrcRect = Rect()
  private val paperClipPath = Path()
  private val tagChipPath = Path()

  private var lastTouchX = 0f
  private var downTouchX = 0f
  private var downTouchY = 0f
  private var coverGestureStartAvgX = 0f
  private var coverGestureStartAvgY = 0f
  private var coverGestureLastAvgX = 0f
  private var coverGestureLastAvgY = 0f
  private var activePointerCount = 0
  private var coverDragX = 0f
  private var coverDragY = 0f
  private var coverSwipeAnimator: ValueAnimator? = null
  private var coverPreviewStamp: MemorialStamp? = null
  private var coverPreviewStampStrength = 0f
  private var coverPreviewStampTargetStrength = 0f
  private var coverFinalStamp: MemorialStamp? = null
  private var coverFinalStampStrength = 0f
  private var coverFinalStampFromButton = false
  private var attachedCoverStamp: MemorialStamp? = null
  private var attachedCoverStampStrength = 0f
  private var summaryAlpha = 0f
  private var summaryAnimator: ValueAnimator? = null
  private var completedAlpha = 0f
  private var completedAnimator: ValueAnimator? = null
  private var coverStackIndex = 0
  private var coverStackLiftProgress = 1f
  private var summaryVisible = false
  private var summaryWasShown = false
  private var summaryPinnedByTap = false
  private var summaryPinnedAtDown = false
  private var summaryShownByHold = false
  private var coverSummaryPressed = false
  private var coverTouchStartedOnCover = false
  private var summaryTouchX = 0f
  private var summaryTouchY = 0f
  private var isDragging = false
  private var foldScrollX = 0f
  private var maxScrollX = 0f
  private var articleWidth = 0f
  private var foldLeft = 0f
  private var foldRight = 0f
  private var foldTop = 0f
  private var foldBottom = 0f
  private var pages: List<MemorialPage> = emptyList()
  private var dossiers: List<PendingMemorialDossier> = fallbackPendingMemorialDossiers
  private var articles: List<ArticleLayout> = emptyList()
  private var bodySegmentsByDossierIndex: Map<Int, List<String>> = emptyMap()
  private var openProgress = 1f
  private var transitionAnimator: ValueAnimator? = null
  private var scrollReturnAnimator: ValueAnimator? = null
  private var stampAnimator: ValueAnimator? = null
  private var stage = MemorialStage.CoverOnly
  private var stampProgress = 0f
  private var currentStamp: MemorialStamp? = null
  private var stampCompletion = StampCompletion.AutoDismiss
  private var toolbarPressedStamp: MemorialStamp? = null
  private var pressedToolbarButton: MemorialToolbarButton? = null
  private var ignoreLockedTouchSequence = false
  private var hideReadingControlsDuringClose = false
  private var onAutoDismiss: (() -> Unit)? = null
  private var onCloseAnimationFinished: (() -> Unit)? = null
  private var readerMode = MemorialReaderMode.ReviewStack
  private var hasPlayedOpenAnimation = false
  private var coverSequenceOffset = 0
  private val showSummaryRunnable = Runnable {
    if (
      stage == MemorialStage.CoverOnly &&
        coverSwipeAnimator == null &&
        currentStamp == null &&
        coverSummaryPressed
    ) {
      coverPreviewStampTargetStrength = 0f
      coverPreviewStampStrength = 0f
      coverPreviewStamp = null
      showSummary(animated = true)
      summaryWasShown = true
      summaryShownByHold = true
      summaryPinnedByTap = false
      invalidate()
    }
  }

  init {
    isClickable = true
    isFocusable = true
    overScrollMode = OVER_SCROLL_NEVER
  }

  fun setPages(nextPages: List<MemorialPage>) {
    if (pages == nextPages) {
      invalidate()
      return
    }
    pages = nextPages
    foldScrollX = 0f
    openProgress = 0f
    completedAlpha = 0f
    clearAttachedCoverStamp()
    summaryAnimator?.cancel()
    summaryAnimator = null
    summaryAlpha = 0f
    bodySegmentsByDossierIndex = emptyMap()
    completedAnimator?.cancel()
    completedAnimator = null
    stage = MemorialStage.CoverOnly
    hasPlayedOpenAnimation = false
    rebuildLayout(width, height)
    invalidate()
  }

  fun setDossiers(nextDossiers: List<PendingMemorialDossier>) {
    val targetCount = expectedDossierCount()
    val normalized =
      (nextDossiers.ifEmpty { fallbackPendingMemorialDossiers } + fallbackPendingMemorialDossiers)
        .take(targetCount)
    if (dossiers == normalized && pages.isNotEmpty()) {
      invalidate()
      return
    }
    dossiers = normalized
    bodySegmentsByDossierIndex = emptyMap()
    coverStackIndex = 0
    resetToCoverStack()
    rebuildPagesForCurrentDossier()
  }

  fun setCoverSequenceOffset(offset: Int) {
    if (coverSequenceOffset == offset) return
    coverSequenceOffset = offset
    invalidate()
  }

  private fun resetToCoverStack() {
    foldScrollX = 0f
    openProgress = 0f
    completedAlpha = 0f
    coverDragX = 0f
    coverDragY = 0f
    coverPreviewStamp = null
    coverPreviewStampStrength = 0f
    coverPreviewStampTargetStrength = 0f
    coverFinalStamp = null
    coverFinalStampFromButton = false
    coverFinalStampStrength = 0f
    coverStackLiftProgress = 1f
    clearAttachedCoverStamp()
    summaryAnimator?.cancel()
    summaryAnimator = null
    summaryAlpha = 0f
    completedAnimator?.cancel()
    completedAnimator = null
    stage = MemorialStage.CoverOnly
    hasPlayedOpenAnimation = false
    currentStamp = null
    stampProgress = 0f
  }

  private fun rebuildPagesForCurrentDossier() {
    val dossierIndex = coverStackIndex.coerceIn(0, (dossiers.size - 1).coerceAtLeast(0))
    val dossier = dossiers[dossierIndex]
    val bodySegments =
      if (width > 0 && height > 0) {
        paginateDossierBody(dossier)
      } else {
        splitMemorialBody(dossier.body)
      }
    bodySegmentsByDossierIndex = bodySegmentsByDossierIndex + (dossierIndex to bodySegments)
    pages =
      buildMemorialPagesForBodySegments(
        dossierIndex = dossierIndex,
        bodySegments = bodySegments,
      )
    rebuildLayout(width, height)
    invalidate()
  }

  private fun dossierFor(index: Int): PendingMemorialDossier {
    return dossiers[index.coerceIn(0, (dossiers.size - 1).coerceAtLeast(0))]
  }

  private fun currentDossier(): PendingMemorialDossier = dossierFor(coverStackIndex)

  fun setAutoDismissHandler(handler: () -> Unit) {
    onAutoDismiss = handler
  }

  fun setCloseAnimationFinishedHandler(handler: () -> Unit) {
    onCloseAnimationFinished = handler
  }

  fun setReaderMode(mode: MemorialReaderMode) {
    if (readerMode == mode) return
    readerMode = mode
    if (readerMode == MemorialReaderMode.ArticleReader) {
      toolbarPressedStamp = null
      coverPreviewStamp = null
      coverPreviewStampStrength = 0f
      coverPreviewStampTargetStrength = 0f
      hideSummary(animated = false)
    }
    invalidate()
  }

  private fun expectedDossierCount(): Int =
    if (readerMode == MemorialReaderMode.ArticleReader) 1 else TOTAL_PENDING_MEMORIALS

  override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
    super.onSizeChanged(width, height, oldWidth, oldHeight)
    if (pages.isNotEmpty() && dossiers.isNotEmpty()) {
      rebuildPagesForCurrentDossier()
    } else {
      rebuildLayout(width, height)
    }
    foldScrollX = foldScrollX.coerceIn(0f, maxScrollX)
    startOpenAnimationIfReady()
  }

  override fun onDetachedFromWindow() {
    transitionAnimator?.cancel()
    transitionAnimator = null
    scrollReturnAnimator?.cancel()
    scrollReturnAnimator = null
    coverSwipeAnimator?.cancel()
    coverSwipeAnimator = null
    summaryAnimator?.cancel()
    summaryAnimator = null
    stampAnimator?.cancel()
    stampAnimator = null
    completedAnimator?.cancel()
    completedAnimator = null
    removeCallbacks(showSummaryRunnable)
    super.onDetachedFromWindow()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    drawStageBackground(canvas)

    val viewportWidth = foldRight - foldLeft
    if (viewportWidth <= 0f) return

    canvas.save()
    canvas.clipRect(
      foldLeft - dp(34f),
      foldTop - dp(34f),
      foldRight + dp(34f),
      foldBottom + dp(34f),
    )
    when (stage) {
      MemorialStage.CoverOnly -> drawCoverOnly(canvas)
      MemorialStage.Opening,
      MemorialStage.Closing -> {
        drawCoverStackLayer(canvas, coverStackAlpha())
        drawOpeningAnimation(canvas, viewportWidth)
        drawCoverControlsLayer(canvas, coverControlsAlpha())
        drawReadingControls(canvas, readingControlsAlpha())
      }
      MemorialStage.Expanded -> {
        drawReadingState(canvas, viewportWidth)
        drawReadingControls(canvas, readingControlsAlpha())
      }
      MemorialStage.Completed -> drawCompletedState(canvas, completedAlpha)
    }
    drawStampOverlay(canvas)
    canvas.restore()
  }

  private fun drawStageBackground(canvas: Canvas) {
    val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
    paints.article.color = APP_BACKGROUND_BASE
    canvas.drawRect(rect, paints.article)
    paints.article.color = AndroidColor.WHITE
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (articles.isEmpty()) return false

    if (stage == MemorialStage.Completed) {
      if (event.actionMasked == MotionEvent.ACTION_UP) {
        performClick()
        dismissCompletedState()
      }
      return true
    }

    if (stage == MemorialStage.CoverOnly) {
      return handleCoverTouch(event)
    }

    if (stage != MemorialStage.Expanded) return true

    if (ignoreLockedTouchSequence) {
      if (
        event.actionMasked == MotionEvent.ACTION_UP ||
          event.actionMasked == MotionEvent.ACTION_CANCEL
      ) {
        ignoreLockedTouchSequence = false
      }
      resetReaderTouchState()
      return true
    }

    if (isReaderInteractionLocked()) {
      if (
        event.actionMasked == MotionEvent.ACTION_DOWN ||
          event.actionMasked == MotionEvent.ACTION_POINTER_DOWN
      ) {
        ignoreLockedTouchSequence = true
      }
      resetReaderTouchState()
      return true
    }

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        parent?.requestDisallowInterceptTouchEvent(true)
        lastTouchX = event.x
        downTouchX = event.x
        downTouchY = event.y
        toolbarPressedStamp =
          if (readerMode == MemorialReaderMode.ReviewStack) {
            toolbarHitTest(event.x, event.y)
              ?: MemorialStamp.Collapse.takeIf { collapseButtonRect.contains(event.x, event.y) }
          } else {
            null
          }
        pressedToolbarButton =
          when (toolbarPressedStamp) {
            MemorialStamp.Like -> MemorialToolbarButton.Like
            MemorialStamp.Dislike -> MemorialToolbarButton.Dislike
            MemorialStamp.Collapse -> MemorialToolbarButton.Collapse
            else -> null
          }
        isDragging = false
        invalidate()
        return true
      }

      MotionEvent.ACTION_MOVE -> {
        val dx = lastTouchX - event.x
        if (!isDragging && abs(dx) > touchSlop) {
          isDragging = true
          clearPressedToolbarButton()
        }
        if (isDragging) {
          scrollByAmount(dx)
          lastTouchX = event.x
        }
        return true
      }

      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
          val pressedStamp = toolbarPressedStamp
          val releasedStamp =
            if (readerMode == MemorialReaderMode.ReviewStack) toolbarHitTest(event.x, event.y)
            else null
          val releasedCollapse = collapseButtonRect.contains(event.x, event.y)
          val tappedBlankReader =
            pressedStamp == null &&
              !isDragging &&
              !touchHitsVisibleArticle(downTouchX, downTouchY) &&
              !touchHitsVisibleArticle(event.x, event.y)
          if (tappedBlankReader) {
            performClick()
            closeWithAnimation {
              onCloseAnimationFinished?.invoke() ?: onAutoDismiss?.invoke()
            }
          } else if (!isDragging && pressedStamp == MemorialStamp.Collapse && releasedCollapse) {
            closeFromExpandedReader()
          } else if (!isDragging && pressedStamp != null && pressedStamp == releasedStamp) {
            startStampAndDismiss(pressedStamp)
          } else {
            if (isDragging) finishReadingDragWithSnap()
            performClick()
          }
        } else if (isDragging) {
          finishReadingDragWithSnap()
        }
        isDragging = false
        toolbarPressedStamp = null
        clearPressedToolbarButton()
        parent?.requestDisallowInterceptTouchEvent(false)
        return true
      }
    }

    return super.onTouchEvent(event)
  }

  override fun onGenericMotionEvent(event: MotionEvent): Boolean {
    if (stage != MemorialStage.Expanded) return true
    if (
      event.action == MotionEvent.ACTION_SCROLL &&
        event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)
    ) {
      val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
      val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
      val primary =
        if (abs(horizontal) >= abs(vertical)) {
          -horizontal
        } else {
          -vertical
        }
      if (primary != 0f) {
        scrollByAmount(primary * dp(96f))
        return true
      }
    }
    return super.onGenericMotionEvent(event)
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }

  private fun isReaderInteractionLocked(): Boolean {
    return transitionAnimator != null ||
      scrollReturnAnimator != null ||
      stampAnimator != null ||
      coverSwipeAnimator != null ||
      currentStamp != null ||
      coverFinalStamp != null
  }

  private fun resetReaderTouchState() {
    isDragging = false
    toolbarPressedStamp = null
    clearPressedToolbarButton()
    parent?.requestDisallowInterceptTouchEvent(false)
  }

  private fun handleCoverTouch(event: MotionEvent): Boolean {
    if (currentStamp != null || coverFinalStamp != null || coverSwipeAnimator != null) {
      clearPressedToolbarButton()
      return true
    }
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN,
      MotionEvent.ACTION_POINTER_DOWN -> {
        parent?.requestDisallowInterceptTouchEvent(true)
        coverSwipeAnimator?.cancel()
        removeCallbacks(showSummaryRunnable)
        val cover = articles.firstOrNull()
        val coverLeft = cover?.let { coverStackLeft(it) } ?: 0f
        coverTouchStartedOnCover =
          cover?.let {
            RectF(coverLeft, it.top, coverLeft + it.width, it.top + it.height)
              .contains(event.x, event.y)
          } ?: false
        activePointerCount = event.pointerCount
        coverGestureStartAvgX = averagePointerX(event)
        coverGestureStartAvgY = averagePointerY(event)
        coverGestureLastAvgX = coverGestureStartAvgX
        coverGestureLastAvgY = coverGestureStartAvgY
        downTouchX = event.x
        downTouchY = event.y
        toolbarPressedStamp = coverActionHitTest(event.x, event.y)
        summaryTouchX = event.x
        summaryTouchY = event.y
        summaryPinnedAtDown = summaryPinnedByTap
        summaryShownByHold = false
        if (!summaryPinnedByTap) {
          hideSummary(animated = false)
        }
        summaryWasShown = false
        coverSummaryPressed = coverActionSummaryRect.contains(event.x, event.y)
        pressedToolbarButton = coverPressedToolbarButton(event.x, event.y)
        if (coverSummaryPressed) {
          postDelayed(showSummaryRunnable, 120L)
        }
        coverDragX = 0f
        coverDragY = 0f
        coverPreviewStamp = null
        coverPreviewStampStrength = 0f
        coverPreviewStampTargetStrength = 0f
        isDragging = false
        return true
      }

      MotionEvent.ACTION_MOVE -> {
        activePointerCount = max(activePointerCount, event.pointerCount)
        coverGestureLastAvgX = averagePointerX(event)
        coverGestureLastAvgY = averagePointerY(event)
        coverDragX = coverGestureLastAvgX - coverGestureStartAvgX
        coverDragY = (coverGestureLastAvgY - coverGestureStartAvgY) * 0.28f
        coverFinalStamp = null
        coverFinalStampFromButton = false
        coverFinalStampStrength = 0f
        summaryTouchX = coverGestureLastAvgX
        summaryTouchY = coverGestureLastAvgY
        if (
          abs(coverGestureLastAvgX - coverGestureStartAvgX) > touchSlop ||
            abs(coverGestureLastAvgY - coverGestureStartAvgY) > touchSlop
        ) {
          isDragging = true
          clearPressedToolbarButton()
          if (!summaryPinnedByTap) {
            hideSummary(animated = true)
          }
          removeCallbacks(showSummaryRunnable)
          if (coverSummaryPressed) {
            postDelayed(showSummaryRunnable, 360L)
          }
        }
        updateCoverPreviewStamp()
        invalidate()
        return true
      }

      MotionEvent.ACTION_UP -> {
        removeCallbacks(showSummaryRunnable)
        val consumedLongPress = summaryWasShown
        if (!summaryPinnedByTap) {
          hideSummary(animated = true)
        }
        val dx = coverGestureLastAvgX - coverGestureStartAvgX
        val dy = coverGestureLastAvgY - coverGestureStartAvgY
        val twoFingerKeep = activePointerCount >= 2 && -dy > dp(54f) && abs(dy) > abs(dx) * 1.2f
        val horizontalVerdict = abs(dx) > dp(64f) && abs(dx) > abs(dy) * 1.25f
        val pressedAction = toolbarPressedStamp
        val releasedAction = coverActionHitTest(event.x, event.y)
        val releasedSummary = coverActionSummaryRect.contains(event.x, event.y)
        when {
          !isDragging && coverSummaryPressed && releasedSummary -> {
            val shouldPin = !summaryPinnedAtDown && !summaryShownByHold
            summaryPinnedByTap = shouldPin
            setSummaryVisible(shouldPin, animated = true)
            summaryWasShown = false
            summaryShownByHold = false
            coverDragX = 0f
            coverDragY = 0f
            coverPreviewStamp = null
            invalidate()
          }
          !isDragging && pressedAction == MemorialStamp.Reject && releasedAction == pressedAction ->
            triggerCoverAction(MemorialStamp.Reject)
          !isDragging &&
            pressedAction == MemorialStamp.Approve &&
            releasedAction == pressedAction -> triggerCoverAction(MemorialStamp.Approve)
          !isDragging && pressedAction == MemorialStamp.Keep && releasedAction == pressedAction ->
            triggerCoverAction(MemorialStamp.Keep)
          !isDragging &&
            pressedAction == MemorialStamp.Collapse &&
            releasedAction == pressedAction -> {
            coverDragX = 0f
            coverDragY = 0f
            coverPreviewStamp = null
            summaryPinnedByTap = false
            performClick()
            expandFromCover()
          }
          twoFingerKeep -> startCoverVerdict(MemorialStamp.Keep, dx, -1f)
          horizontalVerdict && dx > 0f -> startCoverVerdict(MemorialStamp.Approve, dx, 0f)
          horizontalVerdict && dx < 0f -> startCoverVerdict(MemorialStamp.Reject, dx, 0f)
          consumedLongPress -> animateCoverBack()
          !isDragging &&
            coverTouchStartedOnCover &&
            abs(event.x - downTouchX) < touchSlop &&
            abs(event.y - downTouchY) < touchSlop -> {
            coverDragX = 0f
            coverDragY = 0f
            coverPreviewStamp = null
            coverPreviewStampStrength = 0f
            coverPreviewStampTargetStrength = 0f
            summaryPinnedByTap = false
            performClick()
            expandFromCover()
          }
          !isDragging && toolbarPressedStamp == null && !coverTouchStartedOnCover -> {
            hideSummary(animated = true)
            summaryPinnedByTap = false
            summaryPinnedAtDown = false
            summaryShownByHold = false
            performClick()
            onAutoDismiss?.invoke()
          }
          else -> animateCoverBack()
        }
        activePointerCount = 0
        isDragging = false
        summaryWasShown = false
        summaryShownByHold = false
        coverSummaryPressed = false
        coverTouchStartedOnCover = false
        toolbarPressedStamp = null
        clearPressedToolbarButton()
        parent?.requestDisallowInterceptTouchEvent(false)
        return true
      }

      MotionEvent.ACTION_CANCEL -> {
        removeCallbacks(showSummaryRunnable)
        hideSummary(animated = true)
        summaryWasShown = false
        summaryPinnedByTap = false
        summaryPinnedAtDown = false
        summaryShownByHold = false
        coverSummaryPressed = false
        activePointerCount = 0
        isDragging = false
        toolbarPressedStamp = null
        clearPressedToolbarButton()
        coverFinalStamp = null
        coverFinalStampFromButton = false
        coverFinalStampStrength = 0f
        coverPreviewStampStrength = 0f
        coverPreviewStampTargetStrength = 0f
        coverTouchStartedOnCover = false
        animateCoverBack()
        parent?.requestDisallowInterceptTouchEvent(false)
        return true
      }
    }
    return true
  }

  private fun drawCoverOnly(canvas: Canvas) {
    val cover = articles.firstOrNull() ?: return
    val coverLeft = coverStackLeft(cover)
    drawCoverStackLayer(canvas, coverStackAlpha())
    val rotation = (coverDragX / max(1f, foldRight - foldLeft)).coerceIn(-1f, 1f) * 10f
    canvas.save()
    if (coverDragX != 0f || coverDragY != 0f) {
      canvas.rotate(rotation, coverLeft + cover.width / 2f, cover.top + cover.height * 0.62f)
      canvas.translate(coverDragX, coverDragY)
    }
    drawArticle(
      canvas = canvas,
      article = cover,
      left = coverLeft,
      coverSequenceIndex = coverStackIndex,
      transform =
        FoldTransform(
          rotationY = 0f,
          pivotX = coverLeft + cover.width,
          shadingAlpha = 0f,
          edgeShadowProgress = 0f,
          visible = true,
        ),
    )
    drawCoverSwipePreview(canvas, coverLeft, cover)
    canvas.restore()
    drawSummaryBubble(canvas, coverLeft, cover)
    drawCoverControlsLayer(canvas, coverControlsAlpha())
  }

  private fun coverStackLeft(cover: ArticleLayout): Float {
    return foldLeft + ((foldRight - foldLeft) - cover.width) / 2f
  }

  private fun openingAlignmentOffset(): Float {
    val cover = articles.firstOrNull() ?: return 0f
    val centeredLeft = coverStackLeft(cover)
    return centeredLeft - foldLeft
  }

  private fun openingBaseLeft(): Float {
    val progress = openProgress.coerceIn(0f, 1f)
    val coverStart = foldLeft + openingAlignmentOffset()
    val readingStart = foldLeft
    return lerp(coverStart, readingStart, progress)
  }

  private fun readingContentInset(viewportWidth: Float = foldRight - foldLeft): Float {
    if (articleWidth <= 0f || viewportWidth <= 0f) return 0f
    val pagesPerSpread = pagesPerSpreadForViewport(viewportWidth)
    val spreadWidth = articleWidth * pagesPerSpread
    return ((viewportWidth - spreadWidth) / 2f).coerceAtLeast(0f)
  }

  private fun pagesPerSpreadForViewport(
    viewportWidth: Float,
    viewportHeight: Float = foldBottom - foldTop,
  ): Float {
    if (viewportWidth <= 0f || viewportHeight <= 0f) return 1f
    return if (viewportWidth / 2f >= dp(MinReadableSpreadPageWidthDp)) 2f else 1f
  }

  private fun drawCoverStackLayer(canvas: Canvas, alpha: Float) {
    val cover = articles.firstOrNull() ?: return
    if (alpha <= 0.01f) return
    drawWithTransitionAlpha(canvas, alpha) {
      drawCoverStack(canvas, cover, coverStackLeft(cover))
    }
  }

  private fun drawCoverControlsLayer(canvas: Canvas, alpha: Float) {
    if (readerMode == MemorialReaderMode.ArticleReader) return
    if (alpha <= 0.01f) return
    drawWithTransitionAlpha(canvas, alpha) {
      drawCoverGestureHint(canvas)
    }
  }

  private fun drawReadingControls(canvas: Canvas, alpha: Float) {
    if (readerMode == MemorialReaderMode.ArticleReader) return
    if (alpha <= 0.01f) return
    drawWithTransitionAlpha(canvas, alpha) {
      drawCollapseButton(canvas)
      drawVerdictToolbar(canvas)
    }
  }

  private fun drawWithTransitionAlpha(canvas: Canvas, alpha: Float, block: () -> Unit) {
    val clamped = alpha.coerceIn(0f, 1f)
    if (clamped <= 0.01f) return
    if (clamped >= 0.995f) {
      block()
      return
    }

    alphaLayerRect.set(
      foldLeft - dp(72f),
      foldTop - dp(72f),
      foldRight + dp(72f),
      foldBottom + dp(72f),
    )
    val previousAlpha = paints.layerAlpha.alpha
    paints.layerAlpha.alpha = (255f * clamped).roundToInt().coerceIn(0, 255)
    val layer = canvas.saveLayer(alphaLayerRect, paints.layerAlpha)
    block()
    canvas.restoreToCount(layer)
    paints.layerAlpha.alpha = previousAlpha
  }

  private fun coverStackAlpha(): Float {
    return when (stage) {
      MemorialStage.CoverOnly -> 1f
      MemorialStage.Opening,
      MemorialStage.Closing -> 1f - smoothStep(0.06f, 0.32f, openProgress)
      else -> 0f
    }
  }

  private fun coverControlsAlpha(): Float {
    return when (stage) {
      MemorialStage.CoverOnly -> 1f
      MemorialStage.Opening,
      MemorialStage.Closing -> 1f - smoothStep(0.04f, 0.24f, openProgress)
      else -> 0f
    }
  }

  private fun readingControlsAlpha(): Float {
    return when (stage) {
      MemorialStage.Opening,
      MemorialStage.Closing ->
        if (hideReadingControlsDuringClose) {
          0f
        } else {
          smoothStep(0.62f, 0.88f, openProgress)
        }
      MemorialStage.Expanded ->
        if (currentStamp != null) {
          1f - smoothStep(0.08f, 0.42f, stampProgress)
        } else {
          1f
        }
      else -> 0f
    }
  }

  private fun drawCoverStack(canvas: Canvas, cover: ArticleLayout, coverLeft: Float) {
    val remaining = (expectedDossierCount() - coverStackIndex - 1).coerceAtLeast(0)
    val stackCount = min(remaining, 5)
    val isVerdictLeaving = coverFinalStamp != null || currentStamp != null
    val stackShiftProgress =
      if (isVerdictLeaving && coverStackIndex < expectedDossierCount() - 1) {
        smoothStep(0f, 0.72f, coverStackLiftProgress.coerceIn(0f, 1f))
      } else {
        0f
      }
    val extraBottomLevel = if (isVerdictLeaving && remaining > stackCount) 1 else 0
    val renderedStackCount = stackCount + extraBottomLevel
    for (level in renderedStackCount downTo 1) {
      val pose = MemorialStackGeometry.pose(level, stackShiftProgress, ::dp)
      val stackRect =
        RectF(
          coverLeft + pose.offsetX,
          cover.top + pose.offsetY,
          coverLeft + pose.offsetX + cover.width,
          cover.top + pose.offsetY + cover.height,
        )
      val alpha =
        if (extraBottomLevel == 1 && level == renderedStackCount) {
          smoothStep(0.12f, 0.82f, stackShiftProgress)
        } else {
          1f
        }
      canvas.save()
      canvas.rotate(pose.rotation, stackRect.centerX(), stackRect.centerY())
      canvas.scale(pose.scale, pose.scale, stackRect.centerX(), stackRect.centerY())
      drawStackCoverArticle(
        canvas = canvas,
        cover = cover,
        left = stackRect.left,
        coverSequenceIndex = coverStackIndex + level,
        alpha = alpha,
      )
      canvas.restore()
    }
  }

  private fun drawStackCoverArticle(
    canvas: Canvas,
    cover: ArticleLayout,
    left: Float,
    coverSequenceIndex: Int,
    alpha: Float,
  ) {
    val clampedAlpha = alpha.coerceIn(0f, 1f)
    if (clampedAlpha <= 0.01f) return
    val drawLayer = {
      drawArticle(
        canvas = canvas,
        article = cover,
        left = left,
        coverSequenceIndex = coverSequenceIndex,
        transform =
          FoldTransform(
            rotationY = 0f,
            pivotX = left + cover.width,
            shadingAlpha = 0f,
            edgeShadowProgress = 0f,
            visible = true,
          ),
      )
    }
    if (clampedAlpha >= 0.995f) {
      drawLayer()
      return
    }

    alphaLayerRect.set(
      left - dp(16f),
      cover.top - dp(16f),
      left + cover.width + dp(16f),
      cover.top + cover.height + dp(16f),
    )
    val previousAlpha = paints.layerAlpha.alpha
    paints.layerAlpha.alpha = (255f * clampedAlpha).roundToInt().coerceIn(0, 255)
    val layer = canvas.saveLayer(alphaLayerRect, paints.layerAlpha)
    drawLayer()
    canvas.restoreToCount(layer)
    paints.layerAlpha.alpha = previousAlpha
  }

  private fun drawCoverGestureHint(canvas: Canvas) {
    val controlsWidth = controlsViewportWidth()
    val controlScale = controlsResponsiveScale()
    val bottomGap = dp(12f) * controlScale
    val buttonHeight = dp(48f) * controlScale
    val bottomTop = foldBottom - buttonHeight - dp(18f) * controlScale
    val bottomGroupWidth = min(dp(430f) * controlScale, controlsWidth)
    val bottomLeft = foldLeft + ((foldRight - foldLeft) - bottomGroupWidth) / 2f
    val bottomButtonWidth = min(buttonHeight * assets.buttonAspectRatio, bottomGroupWidth * 0.31f)
    val bottomButtonCenterInset = bottomButtonWidth / 2f
    buttonLayouter.layoutAspectButton(
      rect = coverActionLeftRect,
      centerX = bottomLeft + bottomButtonCenterInset,
      top = bottomTop,
      height = buttonHeight,
      maxWidth = bottomButtonWidth,
    )
    buttonLayouter.layoutAspectButton(
      rect = coverActionKeepRect,
      centerX = bottomLeft + bottomGroupWidth / 2f,
      top = bottomTop,
      height = buttonHeight,
      maxWidth = bottomButtonWidth,
    )
    buttonLayouter.layoutAspectButton(
      rect = coverActionRightRect,
      centerX = bottomLeft + bottomGroupWidth - bottomButtonCenterInset,
      top = bottomTop,
      height = buttonHeight,
      maxWidth = bottomButtonWidth,
    )

    val topTotalWidth = min(dp(430f), controlsWidth)
    val topLeft = foldLeft + ((foldRight - foldLeft) - topTotalWidth) / 2f
    val top = foldTop + dp(18f) * controlScale
    val topButtonWidth = min(buttonHeight * assets.buttonAspectRatio, topTotalWidth * 0.36f)
    val topButtonCenterInset = topButtonWidth / 2f
    buttonLayouter.layoutAspectButton(
      rect = coverActionSummaryRect,
      centerX = topLeft + topButtonCenterInset,
      top = top,
      height = buttonHeight,
      maxWidth = topButtonWidth,
    )
    buttonLayouter.layoutAspectButton(
      rect = coverActionOpenRect,
      centerX = topLeft + topTotalWidth - topButtonCenterInset,
      top = top,
      height = buttonHeight,
      maxWidth = topButtonWidth,
    )

    drawToolbarButton(
      canvas,
      coverActionSummaryRect,
      "长按简览",
      pressedToolbarButton == MemorialToolbarButton.CoverSummary,
    )
    drawToolbarButton(
      canvas,
      coverActionOpenRect,
      "点击展开",
      pressedToolbarButton == MemorialToolbarButton.CoverOpen,
    )
    drawToolbarButton(
      canvas,
      coverActionLeftRect,
      "左滑驳回",
      pressedToolbarButton == MemorialToolbarButton.CoverReject,
    )
    drawToolbarButton(
      canvas,
      coverActionRightRect,
      "右滑准奏",
      pressedToolbarButton == MemorialToolbarButton.CoverApprove,
    )
    drawToolbarButton(
      canvas,
      coverActionKeepRect,
      "上滑留中",
      pressedToolbarButton == MemorialToolbarButton.CoverKeep,
    )

    val counter = coverCounterText()
    drawCenteredText(
      canvas,
      counter,
      topLeft + topTotalWidth / 2f,
      coverActionSummaryRect.centerY() + textCenterOffset(paints.toolbarText),
      paints.toolbarText,
    )
  }

  private fun coverActionHitTest(x: Float, y: Float): MemorialStamp? {
    return when {
      coverActionLeftRect.contains(x, y) -> MemorialStamp.Reject
      coverActionRightRect.contains(x, y) -> MemorialStamp.Approve
      coverActionKeepRect.contains(x, y) -> MemorialStamp.Keep
      coverActionOpenRect.contains(x, y) -> MemorialStamp.Collapse
      else -> null
    }
  }

  private fun coverCounterText(): String {
    val current = coverStackIndex + 1
    val total = expectedDossierCount()
    return if (foldRight - foldLeft < dp(420f)) {
      "$current/$total"
    } else {
      "第${current.toChineseCount()}封，共${total.toChineseCount()}封"
    }
  }

  private fun coverPressedToolbarButton(x: Float, y: Float): MemorialToolbarButton? {
    return when {
      coverActionSummaryRect.contains(x, y) -> MemorialToolbarButton.CoverSummary
      coverActionOpenRect.contains(x, y) -> MemorialToolbarButton.CoverOpen
      coverActionLeftRect.contains(x, y) -> MemorialToolbarButton.CoverReject
      coverActionRightRect.contains(x, y) -> MemorialToolbarButton.CoverApprove
      coverActionKeepRect.contains(x, y) -> MemorialToolbarButton.CoverKeep
      else -> null
    }
  }

  private fun clearPressedToolbarButton() {
    if (pressedToolbarButton != null) {
      pressedToolbarButton = null
      invalidate()
    }
  }

  private fun showSummary(animated: Boolean) {
    setSummaryVisible(visible = true, animated = animated)
  }

  private fun hideSummary(animated: Boolean) {
    setSummaryVisible(visible = false, animated = animated)
  }

  private fun setSummaryVisible(visible: Boolean, animated: Boolean) {
    if (
      summaryVisible == visible &&
        ((visible && summaryAlpha >= 1f) || (!visible && summaryAlpha <= 0f))
    ) {
      return
    }
    summaryAnimator?.cancel()
    summaryVisible = visible
    val target = if (visible) 1f else 0f
    if (!animated) {
      summaryAlpha = target
      invalidate()
      return
    }

    summaryAnimator =
      ValueAnimator.ofFloat(summaryAlpha.coerceIn(0f, 1f), target).apply {
        duration = SUMMARY_FADE_DURATION_MS
        interpolator = PathInterpolator(0.22f, 0f, 0.2f, 1f)
        addUpdateListener { animator ->
          summaryAlpha = animator.animatedValue as Float
          invalidate()
        }
        addListener(
          object : AnimatorListenerAdapter() {
            private var canceled = false

            override fun onAnimationCancel(animation: Animator) {
              canceled = true
            }

            override fun onAnimationEnd(animation: Animator) {
              if (summaryAnimator == animation) {
                summaryAnimator = null
              }
              if (!canceled) {
                summaryAlpha = target
                summaryVisible = visible
                invalidate()
              }
            }
          }
        )
        start()
      }
  }

  private fun updateCoverPreviewStamp() {
    if (!coverTouchStartedOnCover || summaryAlpha > 0.05f) {
      coverPreviewStamp = null
      coverPreviewStampTargetStrength = 0f
      coverPreviewStampStrength = lerp(coverPreviewStampStrength, 0f, 0.32f)
      if (coverPreviewStampStrength < 0.02f) {
        coverPreviewStampStrength = 0f
      }
      return
    }

    val horizontalThreshold = dp(46f)
    val horizontalFull = dp(86f)
    val keepThreshold = dp(34f)
    val keepFull = dp(72f)
    val targetStamp =
      when {
        activePointerCount >= 2 && -coverDragY > keepThreshold -> MemorialStamp.Keep
        coverDragX > horizontalThreshold -> MemorialStamp.Approve
        coverDragX < -horizontalThreshold -> MemorialStamp.Reject
        else -> null
      }
    val targetStrength =
      when (targetStamp) {
        MemorialStamp.Keep -> smoothStep(keepThreshold, keepFull, -coverDragY)
        MemorialStamp.Approve,
        MemorialStamp.Reject -> smoothStep(horizontalThreshold, horizontalFull, abs(coverDragX))
        else -> 0f
      }

    if (targetStamp != null) {
      coverPreviewStamp = targetStamp
    }
    coverPreviewStampTargetStrength = targetStrength
    coverPreviewStampStrength =
      lerp(coverPreviewStampStrength, coverPreviewStampTargetStrength, 0.36f)
    if (targetStamp == null && coverPreviewStampStrength < 0.02f) {
      coverPreviewStamp = null
      coverPreviewStampStrength = 0f
    }
  }

  private fun drawSummaryBubble(canvas: Canvas, coverLeft: Float, cover: ArticleLayout) {
    if (
      (!summaryVisible && summaryAlpha <= 0.01f) ||
        coverPreviewStamp != null ||
        coverFinalStamp != null
    )
      return
    val summary = currentDossier()
    val rectWidth = min(dp(520f), foldRight - foldLeft - dp(20f))
    val rectHeight = min(dp(330f), foldBottom - foldTop - dp(76f))
    if (rectWidth <= 0f || rectHeight <= 0f) return
    val centerX = foldLeft + (foldRight - foldLeft) / 2f
    val centerY = cover.top + cover.height * 0.5f
    val left =
      (centerX - rectWidth / 2f).coerceIn(foldLeft + dp(10f), foldRight - dp(10f) - rectWidth)
    val top =
      (centerY - rectHeight / 2f).coerceIn(foldTop + dp(18f), foldBottom - dp(18f) - rectHeight)
    val rect = RectF(left, top, left + rectWidth, top + rectHeight)

    val alpha = summaryAlpha.coerceIn(0f, 1f)
    val layerAlpha = (255f * alpha).roundToInt().coerceIn(0, 255)
    if (layerAlpha <= 0) return
    val previousAlpha = paints.layerAlpha.alpha
    paints.layerAlpha.alpha = layerAlpha
    val layer = canvas.saveLayer(rect, paints.layerAlpha)
    val scale = lerp(0.985f, 1f, smoothStep(0f, 1f, alpha))
    canvas.scale(scale, scale, rect.centerX(), rect.centerY())
    drawPaperPanel(
      canvas = canvas,
      rect = rect,
      clippedCircle = false,
    )
    drawDoubleGoldFrame(canvas, rect, outerInset = dp(10f), innerInset = dp(17f))
    val ornamentRect = RectF(rect).apply { inset(dp(17f), dp(17f)) }
    drawCoverCornerOrnaments(canvas, ornamentRect, sizeScale = 0.1f)

    val summaryTitlePaint =
      TextPaint(paints.itemTitle).apply {
        textSize = sp(22f)
        textAlign = Paint.Align.LEFT
        color = MEMORIAL_INK_BROWN
      }
    val bodyPaint =
      TextPaint(paints.author).apply {
        textSize = sp(17f)
        textAlign = Paint.Align.LEFT
        color = MEMORIAL_INK_BROWN
      }

    val contentWidth = (rect.width() - dp(64f)).roundToInt().coerceAtLeast(1)
    val contentLeft = rect.centerX() - contentWidth / 2f
    val titleLayout =
      buildEllipsizedTextLayout(summary.title, summaryTitlePaint, contentWidth, 1.18f, 1)
    val bodyLayout = buildEllipsizedTextLayout(summary.summary, bodyPaint, contentWidth, 1.36f, 6)
    val gap = dp(12f)
    val totalHeight = titleLayout.height + gap + bodyLayout.height
    var y = rect.centerY() - totalHeight / 2f
    drawStaticLayout(canvas, titleLayout, contentLeft, y)
    y += titleLayout.height + gap
    drawStaticLayout(canvas, bodyLayout, contentLeft, y)
    canvas.restoreToCount(layer)
    paints.layerAlpha.alpha = previousAlpha
  }

  private fun drawCompletedState(canvas: Canvas, alpha: Float) {
    if (alpha <= 0.01f) return
    val viewportWidth = foldRight - foldLeft
    val viewportHeight = foldBottom - foldTop
    val compactLayout = pagesPerSpreadForViewport(viewportWidth, viewportHeight) < 2f
    val side =
      min(
        min(dp(480f), viewportWidth - dp(52f)),
        viewportHeight - dp(56f),
      )
    val left = foldLeft + ((foldRight - foldLeft) - side) / 2f
    val top = foldTop + ((foldBottom - foldTop) - side) / 2f
    val rect = RectF(left, top, left + side, top + side)
    paints.layerAlpha.alpha = (255f * alpha.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
    val layer = canvas.saveLayer(rect, paints.layerAlpha)
    val contentScale = if (compactLayout) (side / dp(420f)).coerceIn(0.72f, 1f) else 1f

    assets.completionTexture?.let { texture ->
      canvas.drawBitmap(texture, null, rect, paints.completionImage)
    }
      ?: drawPaperPanel(
        canvas = canvas,
        rect = rect,
        clippedCircle = true,
      )

    val completionTitlePaint =
      TextPaint(paints.title).apply {
        textSize = sp(44f) * contentScale
        color = AndroidColor.rgb(72, 43, 31)
        textAlign = Paint.Align.CENTER
        typeface = assets.stampTypeface
        letterSpacing = 0.08f
      }
    val completionBodyPaint =
      TextPaint(paints.quote).apply {
        textSize = sp(22f) * contentScale
        color = AndroidColor.rgb(72, 43, 31)
        textAlign = Paint.Align.CENTER
        typeface = assets.songTypeface
      }

    val title = "诸折皆毕"
    val body = "伏愿皇上少憩片刻，以养宸躬。"
    val maxTextWidth = rect.width() * 0.72f
    val fittedTitle =
      TextUtils.ellipsize(title, completionTitlePaint, maxTextWidth, TextUtils.TruncateAt.END)
    val titleCenterY =
      if (compactLayout) {
        rect.centerY() - rect.height() * 0.28f * contentScale
      } else {
        rect.centerY() - rect.height() * 0.18f
      }
    val bodyCenterY = rect.centerY()
    val stampCenterY =
      if (compactLayout) {
        rect.centerY() + rect.height() * 0.28f * contentScale
      } else {
        rect.centerY() + rect.height() * 0.18f
      }
    drawCenteredText(
      canvas = canvas,
      text = fittedTitle.toString(),
      x = rect.centerX(),
      baseline = titleCenterY + textCenterOffset(completionTitlePaint),
      paint = completionTitlePaint,
    )
    if (compactLayout) {
      val bodyLayoutWidth = maxTextWidth.roundToInt().coerceAtLeast(1)
      val bodyLayout =
        buildEllipsizedTextLayout(
          body,
          TextPaint(completionBodyPaint).apply { textAlign = Paint.Align.LEFT },
          bodyLayoutWidth,
          1.22f,
          2,
          Layout.Alignment.ALIGN_CENTER,
        )
      drawStaticLayout(
        canvas = canvas,
        layout = bodyLayout,
        left = rect.centerX() - bodyLayoutWidth / 2f,
        top = bodyCenterY - bodyLayout.height / 2f,
      )
    } else {
      val fittedBody =
        TextUtils.ellipsize(
          body,
          completionBodyPaint,
          rect.width() * 0.74f,
          TextUtils.TruncateAt.END,
        )
      drawCenteredText(
        canvas = canvas,
        text = fittedBody.toString(),
        x = rect.centerX(),
        baseline = bodyCenterY + textCenterOffset(completionBodyPaint),
        paint = completionBodyPaint,
      )
    }
    drawHorizontalRetreatStamp(canvas, rect.centerX(), stampCenterY, contentScale)
    canvas.restoreToCount(layer)
    paints.layerAlpha.alpha = 255
  }

  private fun drawHorizontalRetreatStamp(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    contentScale: Float,
  ) {
    val label = "轻点退朝"
    val textPaint =
      TextPaint(paints.stampText).apply {
        textSize = sp(27f) * contentScale
        color = AndroidColor.WHITE
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
        strokeWidth = 0f
      }
    val rectWidth =
      max(dp(132f) * contentScale, textPaint.measureText(label) + dp(22f) * contentScale)
    val rectHeight = dp(50f) * contentScale
    val rect =
      RectF(cx - rectWidth / 2f, cy - rectHeight / 2f, cx + rectWidth / 2f, cy + rectHeight / 2f)
    val texture = assets.pendingNoteStampTexture
    texture?.let {
      canvas.save()
      canvas.rotate(90f, rect.centerX(), rect.centerY())
      val rotatedRect =
        RectF(
          rect.centerX() - rect.height() / 2f,
          rect.centerY() - rect.width() / 2f,
          rect.centerX() + rect.height() / 2f,
          rect.centerY() + rect.width() / 2f,
        )
      canvas.drawBitmap(it, null, rotatedRect, paints.stampImage)
      canvas.restore()
    }
      ?: run {
        canvas.drawRoundRect(rect, dp(3f), dp(3f), paints.stamp)
      }
    drawCenteredText(canvas, label, cx, cy + textCenterOffset(textPaint), textPaint)
  }

  private fun drawCoverSwipePreview(canvas: Canvas, coverLeft: Float, cover: ArticleLayout) {
    val stamp = coverFinalStamp ?: coverPreviewStamp ?: return
    val strength =
      when (stamp) {
        coverFinalStamp -> coverFinalStampStrength.coerceIn(0f, 1f)
        MemorialStamp.Keep,
        MemorialStamp.Approve,
        MemorialStamp.Reject -> coverPreviewStampStrength.coerceIn(0f, 1f)
        else -> 0f
      }
    if (strength <= 0f) return

    val cx =
      when (stamp) {
        coverFinalStamp ->
          if (coverFinalStampFromButton) {
            coverLeft + cover.width * 0.5f
          } else {
            when (stamp) {
              MemorialStamp.Approve,
              MemorialStamp.Like -> coverLeft + cover.width * (1f / 3f)
              MemorialStamp.Reject,
              MemorialStamp.Dislike -> coverLeft + cover.width * (2f / 3f)
              else -> coverLeft + cover.width * 0.5f
            }
          }
        MemorialStamp.Approve,
        MemorialStamp.Like -> coverLeft + cover.width * (1f / 3f)
        MemorialStamp.Reject,
        MemorialStamp.Dislike -> coverLeft + cover.width * (2f / 3f)
        else -> coverLeft + cover.width * 0.5f
      }
    val cy =
      when (stamp) {
        coverFinalStamp ->
          if (coverFinalStampFromButton) {
            cover.top + cover.height * 0.64f
          } else {
            when (stamp) {
              MemorialStamp.Approve,
              MemorialStamp.Reject,
              MemorialStamp.Like,
              MemorialStamp.Dislike -> cover.top + cover.height * 0.76f
              MemorialStamp.Keep -> cover.top + cover.height * 0.72f
              else -> cover.top + cover.height * 0.55f
            }
          }
        MemorialStamp.Approve,
        MemorialStamp.Reject,
        MemorialStamp.Like,
        MemorialStamp.Dislike -> cover.top + cover.height * 0.76f
        MemorialStamp.Keep -> cover.top + cover.height * 0.72f
        else -> cover.top + cover.height * 0.55f
      }
    drawRubberStamp(canvas, stamp, cx, cy, strength, preview = true)
  }

  private fun drawVerdictToolbar(canvas: Canvas) {
    val controlsWidth = controlsViewportWidth()
    val controlScale = controlsResponsiveScale()
    val toolbarHeight = dp(52f) * controlScale
    val toolbarTop = foldBottom - toolbarHeight - dp(18f) * controlScale
    val gap = dp(18f) * controlScale
    buttonLayouter.layoutButtonRow(
      rects = listOf(toolbarButtonRect1, toolbarButtonRect2),
      centerX = foldLeft + (foldRight - foldLeft) / 2f,
      top = toolbarTop,
      height = toolbarHeight,
      desiredGap = gap,
      maxWidth = controlsWidth,
    )
    drawToolbarButton(
      canvas,
      toolbarButtonRect1,
      "朕喜欢",
      pressedToolbarButton == MemorialToolbarButton.Like,
    )
    drawToolbarButton(
      canvas,
      toolbarButtonRect2,
      "朕不喜欢",
      pressedToolbarButton == MemorialToolbarButton.Dislike,
    )
  }

  private fun drawCollapseButton(canvas: Canvas) {
    val controlsWidth = controlsViewportWidth()
    val controlScale = controlsResponsiveScale()
    val buttonHeight = dp(42f) * controlScale
    val top = foldTop + dp(18f) * controlScale
    buttonLayouter.layoutAspectButton(
      rect = collapseButtonRect,
      centerX = foldLeft + (foldRight - foldLeft) / 2f,
      top = top,
      height = buttonHeight,
      maxWidth = controlsWidth,
    )
    drawToolbarButton(
      canvas,
      collapseButtonRect,
      "收起",
      pressedToolbarButton == MemorialToolbarButton.Collapse,
    )
  }

  private fun drawToolbarButton(canvas: Canvas, rect: RectF, label: String, pressed: Boolean) {
    canvas.save()
    if (pressed) {
      canvas.scale(0.96f, 0.96f, rect.centerX(), rect.centerY())
    }
    assets.buttonTexture?.let { texture ->
      canvas.drawBitmap(texture, null, rect, paints.buttonImage)
    }
      ?: run {
        canvas.drawRoundRect(rect, dp(10f), dp(10f), paints.toolbar)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), paints.toolbarStroke)
      }
    if (pressed) {
      paints.surface.shader = null
      paints.surface.style = Paint.Style.FILL
      paints.surface.color = AndroidColor.argb(42, 42, 20, 8)
      canvas.drawRoundRect(rect, dp(10f), dp(10f), paints.surface)
      paints.surface.color = AndroidColor.TRANSPARENT
    }
    drawCenteredText(
      canvas,
      label,
      rect.centerX(),
      rect.centerY() + textCenterOffset(paints.toolbarText),
      paints.toolbarText,
    )
    canvas.restore()
  }

  private fun toolbarHitTest(x: Float, y: Float): MemorialStamp? {
    return when {
      toolbarButtonRect1.contains(x, y) -> MemorialStamp.Like
      toolbarButtonRect2.contains(x, y) -> MemorialStamp.Dislike
      else -> null
    }
  }

  private fun controlsViewportWidth(): Float {
    return (foldRight - foldLeft - controlsHorizontalInset() * 2f).coerceAtLeast(1f)
  }

  private fun controlsHorizontalInset(): Float {
    return if (foldRight - foldLeft < dp(420f)) dp(10f) else 0f
  }

  private fun controlsResponsiveScale(): Float {
    return (controlsViewportWidth() / dp(360f)).coerceIn(0.86f, 1f)
  }

  private fun drawStampOverlay(canvas: Canvas) {
    val stamp = currentStamp ?: return
    if (stampProgress <= 0f) return

    val appear = smoothStep(0f, 0.32f, stampProgress)
    val settle = smoothStep(0.18f, 1f, stampProgress)
    val scale = 1.36f - 0.36f * settle
    val cx = foldLeft + (foldRight - foldLeft) / 2f
    val cy = foldTop + (foldBottom - foldTop) * 0.55f

    canvas.save()
    canvas.scale(scale, scale, cx, cy)
    drawRubberStamp(canvas, stamp, cx, cy, appear, preview = false)
    canvas.restore()
  }

  private fun drawRubberStamp(
    canvas: Canvas,
    stamp: MemorialStamp,
    cx: Float,
    cy: Float,
    strength: Float,
    preview: Boolean,
  ) {
    if (stamp == MemorialStamp.Collapse) return
    val alphaBase = if (preview) 255 else 255
    val alpha = (alphaBase * strength.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
    if (alpha <= 0) return

    val label = stamp.label
    paints.stampText.color = STAMP_PAPER
    paints.stampText.textSize =
      when (stamp) {
        MemorialStamp.Like -> sp(38f)
        MemorialStamp.Dislike -> sp(34f)
        MemorialStamp.Keep -> sp(37f)
        else -> sp(50f)
      }
    paints.stampText.style = Paint.Style.FILL_AND_STROKE
    paints.stampText.strokeWidth = dp(0.45f)
    val textMetrics = paints.stampText.fontMetrics
    val charHeight = textMetrics.descent - textMetrics.ascent
    val charGap =
      when (stamp) {
        MemorialStamp.Like,
        MemorialStamp.Dislike -> dp(1f)
        else -> dp(2f)
      }
    val maxCharWidth = label.maxOf { paints.stampText.measureText(it.toString()) }
    val textHeight = label.length * charHeight + (label.length - 1).coerceAtLeast(0) * charGap
    val rectWidth = max(dp(78f), maxCharWidth + dp(38f))
    val rectHeight =
      max(
        when (stamp) {
          MemorialStamp.Like -> dp(202f)
          MemorialStamp.Dislike -> dp(228f)
          MemorialStamp.Keep -> dp(132f)
          else -> dp(108f)
        },
        textHeight +
          if (stamp == MemorialStamp.Like || stamp == MemorialStamp.Dislike) dp(62f) else dp(42f),
      )
    val rect =
      RectF(cx - rectWidth / 2f, cy - rectHeight / 2f, cx + rectWidth / 2f, cy + rectHeight / 2f)
    val rotation =
      when (stamp) {
        MemorialStamp.Approve -> -13f
        MemorialStamp.Reject -> 8f
        MemorialStamp.Keep -> -6f
        MemorialStamp.Like -> -9f
        MemorialStamp.Dislike -> 7f
        MemorialStamp.Collapse -> 0f
      }

    canvas.save()
    canvas.rotate(rotation, cx, cy)
    val layerBounds =
      RectF(rect).apply {
        inset(-dp(6f), -dp(6f))
      }
    val layer = canvas.saveLayer(layerBounds, null)
    assets.stampTextureFor(stamp)?.let { texture ->
      paints.stampImage.alpha = alpha.coerceIn(0, 255)
      canvas.drawBitmap(texture, null, rect, paints.stampImage)
    }
      ?: run {
        paints.stamp.alpha = alpha.coerceIn(0, 255)
        canvas.drawRoundRect(rect, dp(7f), dp(7f), paints.stamp)
      }
    paints.stampText.alpha = alpha.coerceIn(0, 255)
    drawVerticalStampText(canvas, label, cx, cy, charHeight, charGap)
    drawStampDistress(canvas, rect, alpha, stamp)
    canvas.restoreToCount(layer)
    paints.stamp.alpha = 255
    paints.stampImage.alpha = 255
    paints.stampText.alpha = 255
    paints.stampText.strokeWidth = 0f
    paints.stampText.style = Paint.Style.FILL
    canvas.restore()
  }

  private fun drawVerticalStampText(
    canvas: Canvas,
    label: String,
    cx: Float,
    cy: Float,
    charHeight: Float,
    charGap: Float,
  ) {
    val textHeight = label.length * charHeight + (label.length - 1).coerceAtLeast(0) * charGap
    val startCenterY = cy - textHeight / 2f + charHeight / 2f
    val baselineOffset =
      -(paints.stampText.fontMetrics.ascent + paints.stampText.fontMetrics.descent) / 2f
    label.forEachIndexed { index, char ->
      val charCenterY = startCenterY + index * (charHeight + charGap)
      canvas.drawText(char.toString(), cx, charCenterY + baselineOffset, paints.stampText)
    }
  }

  private fun drawStampDistress(canvas: Canvas, rect: RectF, alpha: Int, stamp: MemorialStamp) {
    val columns = 7
    val rows = 12
    for (row in 0 until rows) {
      for (column in 0 until columns) {
        val noise = stableNoise(stamp.ordinal, column, row)
        if (noise > 0.68f) {
          val localX =
            (column + 0.25f + stableNoise(stamp.ordinal + 11, column, row) * 0.5f) / columns
          val localY = (row + 0.18f + stableNoise(stamp.ordinal + 23, column, row) * 0.64f) / rows
          val radius = dp(0.45f + stableNoise(stamp.ordinal + 31, column, row) * 1.25f)
          paints.stampErase.alpha = (alpha * (0.12f + noise * 0.18f)).roundToInt().coerceIn(0, 92)
          canvas.drawCircle(
            rect.left + rect.width() * localX,
            rect.top + rect.height() * localY,
            radius,
            paints.stampErase,
          )
        }
      }
    }
    repeat(4) { index ->
      val noise = stableNoise(stamp.ordinal + 41, index, 3)
      paints.stampErase.alpha = (alpha * (0.07f + noise * 0.11f)).roundToInt().coerceIn(0, 58)
      val y = rect.top + rect.height() * (0.18f + index * 0.18f + noise * 0.04f)
      val x = rect.left + rect.width() * (0.18f + stableNoise(stamp.ordinal + 53, index, 5) * 0.32f)
      val scratchWidth = rect.width() * (0.18f + stableNoise(stamp.ordinal + 59, index, 7) * 0.18f)
      val scratchHeight = dp(0.55f + noise * 0.55f)
      canvas.drawRoundRect(
        RectF(x, y, x + scratchWidth, y + scratchHeight),
        scratchHeight,
        scratchHeight,
        paints.stampErase,
      )
    }
    paints.stampErase.alpha = 255
  }

  private fun stableNoise(seed: Int, x: Int, y: Int): Float {
    var value = seed * 7349 + x * 9157 + y * 6151
    value = value xor (value shl 13)
    val mixed = value * (value * value * 15731 + 789221) + 1376312589
    return ((mixed and 0x7fffffff) / 2147483647f).coerceIn(0f, 1f)
  }

  private fun rebuildLayout(viewWidth: Int, viewHeight: Int) {
    if (viewWidth <= 0 || viewHeight <= 0) return

    val previousStartPageIndex = readingStartPageIndexForScroll()
    foldLeft = 0f
    foldRight = viewWidth.toFloat()
    foldTop = dp(20f)
    foldBottom = viewHeight - dp(20f)

    val viewportWidth = (foldRight - foldLeft).coerceAtLeast(1f)
    val viewportHeight = (foldBottom - foldTop).coerceAtLeast(1f)
    val articleSize = resolvedArticleSizeForViewport(viewportWidth, viewportHeight)
    articleWidth = articleSize.width
    val articleHeight = articleSize.height
    val articleTop = foldTop + (viewportHeight - articleHeight) / 2f
    var nextLeft = 0f
    articles = pages.mapIndexed { index, page ->
      ArticleLayout(
          page = page,
          pageIndex = index,
          left = nextLeft,
          top = articleTop,
          width = articleWidth,
          height = articleHeight,
        )
        .also {
          nextLeft += articleWidth
        }
    }

    maxScrollX =
      max(0f, nextLeft - articleWidth * pagesPerSpreadForViewport(viewportWidth, viewportHeight))
    foldScrollX =
      if (stage == MemorialStage.Expanded) {
        readingSnapTargetForPage(previousStartPageIndex)
      } else {
        foldScrollX.coerceIn(0f, maxScrollX)
      }
  }

  private fun paginateDossierBody(dossier: PendingMemorialDossier): List<String> {
    val pageWidth = resolvedArticleWidth(width)
    val pageHeight = resolvedArticleHeight(width, height)
    val scale = articleResponsiveScale(pageWidth)
    val horizontalInset = responsiveDp(42f, pageWidth)
    val contentWidth = (pageWidth - horizontalInset * 2f).roundToInt().coerceAtLeast(1)
    val firstBodyHeight =
      (pageHeight - responsiveDp(126f, pageWidth) - responsiveDp(54f, pageWidth)).coerceAtLeast(
        dp(96f) * scale
      )
    val bodyHeight =
      (pageHeight - responsiveDp(64f, pageWidth) - responsiveDp(54f, pageWidth)).coerceAtLeast(
        dp(128f) * scale
      )
    return paginator.paginate(
      body = dossier.body,
      contentWidth = contentWidth,
      firstPageHeight = firstBodyHeight,
      pageHeight = bodyHeight,
    )
  }

  private fun resolvedArticleWidth(viewWidth: Int): Float {
    return resolvedArticleSize(viewWidth, height).width
  }

  private fun resolvedArticleHeight(viewWidth: Int, viewHeight: Int): Float {
    return resolvedArticleSize(viewWidth, viewHeight).height
  }

  private fun resolvedArticleSize(viewWidth: Int, viewHeight: Int): SizeF {
    val viewportWidth = viewWidth.toFloat().coerceAtLeast(1f)
    val viewportHeight = (viewHeight - dp(40f)).coerceAtLeast(1f)
    return resolvedArticleSizeForViewport(viewportWidth, viewportHeight)
  }

  private fun resolvedArticleSizeForViewport(viewportWidth: Float, viewportHeight: Float): SizeF {
    val pagesPerSpread = pagesPerSpreadForViewport(viewportWidth, viewportHeight)
    val resolvedWidth = (viewportWidth / pagesPerSpread).coerceAtLeast(1f)
    val resolvedHeight = (viewportHeight * 0.78f).coerceAtLeast(1f)
    return SizeF(width = resolvedWidth, height = resolvedHeight)
  }

  private fun calculateTransform(
    screenLeft: Float,
    actualWidth: Float,
    viewportWidth: Float,
  ): FoldTransform {
    val screenRight = screenLeft + actualWidth
    val overlapStart = max(foldLeft, screenLeft)
    val overlapEnd = min(foldRight, screenRight)

    if (overlapStart >= overlapEnd) {
      return FoldTransform(
        rotationY = 0f,
        pivotX = screenLeft,
        shadingAlpha = 0f,
        edgeShadowProgress = 0f,
        visible = false,
      )
    }

    val overlap = overlapEnd - overlapStart
    val visibleFraction = (overlap / actualWidth).coerceIn(0f, 1f)
    var rotation = 0f
    var shade = 0f
    val itemIsBeforeViewport = screenLeft < foldLeft
    val itemIsAfterViewport = screenRight > foldRight
    val pivotX =
      if (itemIsBeforeViewport) {
        screenLeft + actualWidth
      } else {
        screenLeft
      }

    if (visibleFraction < 1f && (itemIsBeforeViewport || itemIsAfterViewport)) {
      val direction = if (itemIsBeforeViewport) -1f else 1f
      val hiddenFraction = (1f - visibleFraction).coerceIn(0f, 1f)
      val easedHiddenFraction = foldProgress(hiddenFraction)
      val radians = easedHiddenFraction * (PI.toFloat() / 2f) * direction

      if (abs(radians) > PI.toFloat() / 2f) {
        return FoldTransform(
          rotationY = 0f,
          pivotX = pivotX,
          shadingAlpha = 0f,
          edgeShadowProgress = 0f,
          visible = false,
        )
      }

      rotation = radians * 180f / PI.toFloat()
      shade = easedHiddenFraction
    }
    val centerX = foldLeft + viewportWidth / 2f
    val normalizedDistance = (abs(pivotX - centerX) / max(1f, viewportWidth / 2f)).coerceIn(0f, 1f)
    val edgeAcceleration = smoothStep(0.34f, 0.88f, normalizedDistance)
    val edgeShadowProgress =
      (shade * (0.18f + 0.82f * edgeAcceleration * edgeAcceleration)).coerceIn(0f, 1f)

    return FoldTransform(
      rotationY = rotation,
      pivotX = pivotX,
      shadingAlpha = shade,
      edgeShadowProgress = edgeShadowProgress,
      visible = true,
    )
  }

  private fun drawReadingState(canvas: Canvas, viewportWidth: Float) {
    val contentInset = readingContentInset(viewportWidth)
    for (article in articles) {
      val screenLeft = foldLeft + contentInset + article.left - foldScrollX
      val transform =
        calculateTransform(
          screenLeft = screenLeft,
          actualWidth = article.width,
          viewportWidth = viewportWidth,
        )
      if (transform.visible) {
        canvas.save()
        if (transform.rotationY != 0f) {
          applyFoldMatrix(
            canvas = canvas,
            rotationY = transform.rotationY,
            pivotX = transform.pivotX,
          )
        }
        drawArticle(
          canvas = canvas,
          article = article,
          left = screenLeft,
          transform = transform,
        )
        canvas.restore()
      }
    }
  }

  private fun touchHitsVisibleArticle(x: Float, y: Float): Boolean {
    if (articles.isEmpty()) return false
    val viewportWidth = foldRight - foldLeft
    if (viewportWidth <= 0f) return false
    val contentInset = readingContentInset(viewportWidth)
    return articles.any { article ->
      val screenLeft = foldLeft + contentInset + article.left - foldScrollX
      val transform =
        calculateTransform(
          screenLeft = screenLeft,
          actualWidth = article.width,
          viewportWidth = viewportWidth,
        )
      transform.visible &&
        x >= screenLeft &&
        x <= screenLeft + article.width &&
        y >= article.top &&
        y <= article.top + article.height
    }
  }

  private fun drawOpeningAnimation(canvas: Canvas, viewportWidth: Float) {
    if (articles.isEmpty()) return

    val placements = buildOpeningPlacements()

    for (placement in placements.asReversed()) {
      val article = placement.article
      val panelRect =
        RectF(
          placement.left,
          article.top,
          placement.left + article.width,
          article.top + article.height,
        )
      val pivotsOnLeft = article.pageIndex % 2 == 1

      canvas.save()
      if (placement.rotationY != 0f) {
        applyFoldMatrix(
          canvas = canvas,
          rotationY = placement.rotationY,
          pivotX = placement.pivotX,
        )
      }
      canvas.clipRect(panelRect)
      drawArticle(
        canvas = canvas,
        article = article,
        left = placement.left,
        transform =
          FoldTransform(
            rotationY = placement.rotationY,
            pivotX = placement.pivotX,
            shadingAlpha = 0.1f * placement.foldAmount,
            edgeShadowProgress = 0.48f * placement.foldAmount,
            visible = true,
          ),
      )
      drawOpeningSegmentTone(canvas, panelRect, placement.foldAmount, !pivotsOnLeft)
      canvas.restore()
    }
  }

  private fun buildOpeningPlacements(): List<OpeningPlacement> {
    if (articles.isEmpty()) return emptyList()
    val placements = ArrayList<OpeningPlacement>(articles.size)
    var chainEdgeX = openingBaseLeft()

    articles.forEachIndexed { index, article ->
      val rotation = openingRotationFor(index)
      val pivotX = chainEdgeX
      val left = chainEdgeX
      placements +=
        OpeningPlacement(
          article = article,
          left = left,
          pivotX = pivotX,
          rotationY = rotation,
          foldAmount = if (index == 0) 0f else abs(rotation) / 180f,
        )
      chainEdgeX =
        if (rotation == 0f) {
          left + article.width
        } else {
          pivotX + article.width * cos(rotation * PI.toFloat() / 180f)
        }
    }
    return placements
  }

  private fun openingRotationFor(pageIndex: Int): Float {
    if (pageIndex == 0) return 0f
    if (pageIndex % 2 == 0) return 0f
    val waveDelay = 0.035f * (pageIndex - 1)
    val localProgress = openingSegmentProgress(openProgress, waveDelay)
    val rotationMagnitude = 180f * (1f - localProgress)
    return -rotationMagnitude
  }

  private fun applyFoldMatrix(
    canvas: Canvas,
    rotationY: Float,
    pivotX: Float,
  ) {
    val pivotY = foldTop + (foldBottom - foldTop) / 2f
    matrix.reset()
    camera.save()
    camera.setLocation(0f, 0f, -dp(900f) / displayDensity)
    camera.rotateY(rotationY)
    camera.getMatrix(matrix)
    camera.restore()
    matrix.preTranslate(-pivotX, -pivotY)
    matrix.postTranslate(pivotX, pivotY)
    canvas.concat(matrix)
  }

  private fun drawArticle(
    canvas: Canvas,
    article: ArticleLayout,
    left: Float,
    coverSequenceIndex: Int = coverStackIndex,
    transform: FoldTransform,
  ) {
    articleRect.set(left, article.top, left + article.width, article.top + article.height)
    canvas.drawRect(articleRect, paints.article)
    if (article.page.type == MemorialPageType.Cover) {
      drawCoverBackground(canvas, articleRect, coverSequenceIndex)
    } else {
      drawPaperBackground(canvas, articleRect, rotated = false)
    }
    drawPaperAging(canvas, articleRect)
    drawArticleSurface(canvas, articleRect)
    drawPageContent(canvas, article, articleRect, coverSequenceIndex)
    if (article.page.type == MemorialPageType.Cover && coverSequenceIndex == coverStackIndex) {
      drawAttachedCoverStamp(canvas, articleRect)
    }
    drawRotationalVolumeShadow(
      canvas = canvas,
      rect = articleRect,
      pivotX = transform.pivotX,
      rotationY = transform.rotationY,
      progress = transform.edgeShadowProgress,
    )
    drawDynamicFoldShadow(canvas, articleRect, transform.pivotX, transform.edgeShadowProgress)
    drawFoldEdgeThickness(
      canvas = canvas,
      rect = articleRect,
      pivotX = transform.pivotX,
      progress = max(abs(transform.rotationY) / 90f, transform.edgeShadowProgress * 0.55f),
    )

    if (transform.shadingAlpha > 0f) {
      paints.shade.alpha = (255f * 0.08f * transform.shadingAlpha).roundToInt().coerceIn(0, 255)
      canvas.drawRect(articleRect, paints.shade)
      paints.shade.alpha = 255
    }
  }

  private fun drawAttachedCoverStamp(canvas: Canvas, rect: RectF) {
    val stamp = attachedCoverStamp ?: return
    val strength = attachedCoverStampStrength.coerceIn(0f, 1f)
    if (strength <= 0f) return
    drawRubberStamp(
      canvas = canvas,
      stamp = stamp,
      cx = rect.centerX(),
      cy = rect.top + rect.height() * 0.64f,
      strength = strength,
      preview = true,
    )
  }

  private fun clearAttachedCoverStamp() {
    attachedCoverStamp = null
    attachedCoverStampStrength = 0f
  }

  private fun drawCoverBackground(canvas: Canvas, rect: RectF, coverSequenceIndex: Int) {
    assets.coverTextureFor(coverSequenceIndex + coverSequenceOffset)?.let { texture ->
      drawBitmapCenterCrop(canvas, texture, rect, paints.cover)
    }
      ?: run {
        paints.article.color = AndroidColor.rgb(122, 62, 42)
        canvas.drawRect(rect, paints.article)
        paints.article.color = AndroidColor.WHITE
      }
    paints.surface.shader =
      LinearGradient(
        rect.left,
        rect.top,
        rect.right,
        rect.bottom,
        intArrayOf(
          AndroidColor.argb(40, 255, 255, 255),
          AndroidColor.TRANSPARENT,
          AndroidColor.argb(70, 0, 0, 0),
        ),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP,
      )
    canvas.drawRect(rect, paints.surface)
    paints.surface.shader = null
  }

  private fun drawBitmapCenterCrop(
    canvas: Canvas,
    bitmap: android.graphics.Bitmap,
    dst: RectF,
    paint: Paint,
  ) {
    val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
    val dstRatio = dst.width() / dst.height()
    if (bitmapRatio > dstRatio) {
      val srcWidth = bitmap.height * dstRatio
      val left = (bitmap.width - srcWidth) / 2f
      coverTextureSrcRect.set(left.roundToInt(), 0, (left + srcWidth).roundToInt(), bitmap.height)
    } else {
      val srcHeight = bitmap.width / dstRatio
      val top = (bitmap.height - srcHeight) / 2f
      coverTextureSrcRect.set(0, top.roundToInt(), bitmap.width, (top + srcHeight).roundToInt())
    }
    canvas.drawBitmap(bitmap, coverTextureSrcRect, dst, paint)
  }

  private fun drawPaperBackground(canvas: Canvas, rect: RectF, rotated: Boolean) {
    assets.paperTexture?.let { texture ->
      canvas.save()
      if (rotated) {
        canvas.rotate(180f, rect.centerX(), rect.centerY())
      }
      canvas.drawBitmap(texture, null, rect, paints.paper)
      canvas.restore()
    }
  }

  private fun drawPaperPanel(
    canvas: Canvas,
    rect: RectF,
    clippedCircle: Boolean,
  ) {
    canvas.save()
    if (clippedCircle) {
      paperClipPath.reset()
      paperClipPath.addOval(rect, Path.Direction.CW)
      canvas.clipPath(paperClipPath)
    }
    paints.article.color = STAMP_PAPER
    canvas.drawRect(rect, paints.article)
    drawPaperBackground(canvas, rect, rotated = false)
    drawPaperAging(canvas, rect)
    drawArticleSurface(canvas, rect)
    paints.article.color = AndroidColor.WHITE
    canvas.restore()
  }

  private fun drawPaperAging(canvas: Canvas, rect: RectF) {
    paints.paperAging.shader =
      LinearGradient(
        rect.left,
        rect.top,
        rect.right,
        rect.bottom,
        intArrayOf(
          AndroidColor.argb(18, 124, 88, 34),
          AndroidColor.TRANSPARENT,
          AndroidColor.argb(15, 88, 57, 26),
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
      )
    canvas.drawRect(rect, paints.paperAging)
    paints.paperAging.shader = null

    paints.paperAging.shader =
      LinearGradient(
        rect.left,
        0f,
        rect.right,
        0f,
        intArrayOf(
          AndroidColor.argb(20, 76, 48, 20),
          AndroidColor.TRANSPARENT,
          AndroidColor.TRANSPARENT,
          AndroidColor.argb(18, 76, 48, 20),
        ),
        floatArrayOf(0f, 0.09f, 0.9f, 1f),
        Shader.TileMode.CLAMP,
      )
    canvas.drawRect(rect, paints.paperAging)
    paints.paperAging.shader = null

    repeat(26) {
      val radius = dp(1f + stableNoise(StablePaperAgingSeed, it, 3) * 2f) * 0.45f
      val x = rect.left + stableNoise(StablePaperAgingSeed, it, 7) * rect.width()
      val y = rect.top + stableNoise(StablePaperAgingSeed, it, 11) * rect.height()
      val alpha = (5f + stableNoise(StablePaperAgingSeed, it, 13) * 8f).roundToInt()
      paints.paperAging.color = AndroidColor.argb(alpha, 70, 46, 22)
      canvas.drawCircle(x, y, radius, paints.paperAging)
    }
    paints.paperAging.color = AndroidColor.TRANSPARENT
  }

  private fun drawDynamicFoldShadow(
    canvas: Canvas,
    rect: RectF,
    pivotX: Float,
    progress: Float,
  ) {
    if (progress <= 0f) return

    val clampedPivotX = pivotX.coerceIn(rect.left, rect.right)
    val overflow =
      when {
        pivotX < rect.left -> rect.left - pivotX
        pivotX > rect.right -> pivotX - rect.right
        else -> 0f
      }
    val edgeFade = (1f - overflow / dp(42f)).coerceIn(0f, 1f)
    if (edgeFade <= 0f) return
    val easedProgress = progress * progress * (3f - 2f * progress)
    val effectiveProgress = easedProgress * edgeFade
    val shadowWidth = dp(24f) + dp(42f) * easedProgress
    val peakAlpha = (5f + 22f * effectiveProgress).roundToInt().coerceIn(0, 40)
    val shadowLeft: Float
    val shadowRight: Float
    val colors: IntArray

    if (clampedPivotX <= rect.centerX()) {
      shadowLeft = clampedPivotX
      shadowRight = min(rect.right, clampedPivotX + shadowWidth)
      colors =
        intArrayOf(
          AndroidColor.argb(peakAlpha, 0, 0, 0),
          AndroidColor.argb((peakAlpha * 0.26f).roundToInt(), 0, 0, 0),
          AndroidColor.TRANSPARENT,
        )
    } else {
      shadowLeft = max(rect.left, clampedPivotX - shadowWidth)
      shadowRight = clampedPivotX
      colors =
        intArrayOf(
          AndroidColor.TRANSPARENT,
          AndroidColor.argb((peakAlpha * 0.26f).roundToInt(), 0, 0, 0),
          AndroidColor.argb(peakAlpha, 0, 0, 0),
        )
    }

    if (shadowRight <= shadowLeft) return

    paints.foldShadow.shader =
      LinearGradient(
        shadowLeft,
        0f,
        shadowRight,
        0f,
        colors,
        floatArrayOf(0f, 0.38f, 1f),
        Shader.TileMode.CLAMP,
      )
    canvas.drawRect(shadowLeft, rect.top, shadowRight, rect.bottom, paints.foldShadow)
    paints.foldShadow.shader = null
  }

  private fun drawFoldEdgeThickness(
    canvas: Canvas,
    rect: RectF,
    pivotX: Float,
    progress: Float,
  ) {
    val t = progress.coerceIn(0f, 1f)
    if (t <= 0.02f || pivotX < rect.left - dp(2f) || pivotX > rect.right + dp(2f)) return

    val edgeWidth = dp(1.2f) + dp(3.2f) * t
    val edgeLeft: Float
    val edgeRight: Float
    val colors: IntArray
    if (pivotX <= rect.centerX()) {
      edgeLeft = pivotX
      edgeRight = min(rect.right, pivotX + edgeWidth)
      colors =
        intArrayOf(
          AndroidColor.argb((24f * t).roundToInt(), 0, 0, 0),
          AndroidColor.argb((12f * t).roundToInt(), 255, 255, 255),
          AndroidColor.TRANSPARENT,
        )
    } else {
      edgeLeft = max(rect.left, pivotX - edgeWidth)
      edgeRight = pivotX
      colors =
        intArrayOf(
          AndroidColor.TRANSPARENT,
          AndroidColor.argb((12f * t).roundToInt(), 255, 255, 255),
          AndroidColor.argb((24f * t).roundToInt(), 0, 0, 0),
        )
    }
    if (edgeRight <= edgeLeft) return

    paints.foldShadow.shader =
      LinearGradient(
        edgeLeft,
        0f,
        edgeRight,
        0f,
        colors,
        floatArrayOf(0f, 0.42f, 1f),
        Shader.TileMode.CLAMP,
      )
    canvas.drawRect(edgeLeft, rect.top, edgeRight, rect.bottom, paints.foldShadow)
    paints.foldShadow.shader = null

    // Keep the fold hint extremely subtle; the paper texture and shading should carry the volume.
  }

  private fun drawRotationalVolumeShadow(
    canvas: Canvas,
    rect: RectF,
    pivotX: Float,
    rotationY: Float,
    progress: Float,
  ) {
    val raw = max(abs(rotationY) / 90f, progress).coerceIn(0f, 1f)
    val t = raw * raw * (3f - 2f * raw)
    if (t <= 0.02f) return

    val hingeOnLeft = pivotX <= rect.centerX()
    val darkAlpha = (6f + 28f * t).roundToInt().coerceIn(0, 52)
    val midAlpha = (4f + 14f * t).roundToInt().coerceIn(0, 28)
    val lightAlpha = (4f + 12f * t).roundToInt().coerceIn(0, 24)
    val colors =
      if (hingeOnLeft) {
        intArrayOf(
          AndroidColor.argb(darkAlpha, 0, 0, 0),
          AndroidColor.argb(midAlpha, 0, 0, 0),
          AndroidColor.TRANSPARENT,
          AndroidColor.argb(lightAlpha, 255, 255, 255),
        )
      } else {
        intArrayOf(
          AndroidColor.argb(lightAlpha, 255, 255, 255),
          AndroidColor.TRANSPARENT,
          AndroidColor.argb(midAlpha, 0, 0, 0),
          AndroidColor.argb(darkAlpha, 0, 0, 0),
        )
      }

    paints.surface.shader =
      LinearGradient(
        rect.left,
        0f,
        rect.right,
        0f,
        colors,
        floatArrayOf(0f, 0.22f, 0.7f, 1f),
        Shader.TileMode.CLAMP,
      )
    canvas.drawRect(rect, paints.surface)
    paints.surface.shader = null
  }

  private fun drawPageContent(
    canvas: Canvas,
    article: ArticleLayout,
    rect: RectF,
    coverSequenceIndex: Int,
  ) {
    when (article.page.type) {
      MemorialPageType.Cover -> drawCoverContent(canvas, rect, article.page, coverSequenceIndex)
      MemorialPageType.Directory -> drawDirectoryContent(canvas, rect, article.page)
      MemorialPageType.BodyLeft,
      MemorialPageType.BodyRight -> drawBodyContent(canvas, rect, article.page)
      MemorialPageType.End -> drawEndContent(canvas, rect, article.page)
    }
  }

  private fun drawCoverContent(
    canvas: Canvas,
    rect: RectF,
    page: MemorialPage,
    coverSequenceIndex: Int,
  ) {
    val scale = articleResponsiveScale(rect.width())
    val dossier =
      dossierFor(if (page.type == MemorialPageType.Cover) coverSequenceIndex else page.dossierIndex)
    val labelWidth = rect.width() * 0.42f
    val labelHeight = rect.height() * 0.58f
    val label =
      RectF(
        rect.centerX() - labelWidth / 2f,
        rect.centerY() - labelHeight / 2f,
        rect.centerX() + labelWidth / 2f,
        rect.centerY() + labelHeight / 2f,
      )
    drawPaperPanel(
      canvas = canvas,
      rect = label,
      clippedCircle = false,
    )
    drawInsideStrokeRect(canvas, label, paints.gold)
    val innerInset = dp(6f)
    val innerLabel =
      RectF(
        label.left + innerInset,
        label.top + innerInset,
        label.right - innerInset,
        label.bottom - innerInset,
      )
    drawInsideStrokeRect(canvas, innerLabel, paints.gold)
    drawCoverCornerOrnaments(canvas, innerLabel, sizeScale = 0.17f)

    val titlePaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = responsiveSp(22f, rect.width())
        color = paints.coverTitle.color
        typeface = assets.stampTypeface
        textAlign = Paint.Align.CENTER
      }
    val coverTitlePaint =
      TextPaint(paints.coverTitle).apply {
        textSize = paints.coverTitle.textSize * scale
      }
    val titleLineGap = responsiveDp(5f, rect.width())
    val coverTitleLineGap = responsiveDp(24f, rect.width())
    val titleText =
      ellipsizeVerticalText(
        text = dossier.title,
        paint = titlePaint,
        maxHeight = innerLabel.height() - responsiveDp(76f, rect.width()),
        lineGap = titleLineGap,
      )
    val titleColumnWidth = verticalTextWidth(titleText, titlePaint)
    val coverColumnWidth = verticalTextWidth("奏章", coverTitlePaint)
    val columnGap = responsiveDp(5f, rect.width())
    val groupWidth = titleColumnWidth + columnGap + coverColumnWidth
    val groupLeft = label.centerX() - groupWidth / 2f
    val coverTitleHeight = verticalTextHeight("奏章", coverTitlePaint, coverTitleLineGap)
    val titleHeight = verticalTextHeight(titleText, titlePaint, titleLineGap)
    val titleTop = innerLabel.centerY() - titleHeight / 2f
    val coverTitleTop =
      innerLabel.centerY() - coverTitleHeight / 2f - responsiveDp(10f, rect.width())
    drawVerticalText(
      canvas = canvas,
      text = titleText,
      paint = titlePaint,
      centerX = groupLeft + titleColumnWidth / 2f,
      top = titleTop,
      lineGap = titleLineGap,
    )
    val coverColumnX = groupLeft + titleColumnWidth + columnGap + coverColumnWidth / 2f
    drawVerticalText(
      canvas = canvas,
      text = "奏章",
      centerX = coverColumnX,
      top = coverTitleTop,
      paint = coverTitlePaint,
      lineGap = coverTitleLineGap,
    )
  }

  private fun drawInsideStrokeRect(canvas: Canvas, rect: RectF, paint: Paint) {
    val strokeInset = paint.strokeWidth / 2f
    val strokeRect =
      RectF(rect).apply {
        inset(strokeInset, strokeInset)
      }
    canvas.drawRect(strokeRect, paint)
  }

  private fun drawDoubleGoldFrame(
    canvas: Canvas,
    rect: RectF,
    outerInset: Float,
    innerInset: Float,
  ) {
    val outer =
      RectF(rect).apply {
        inset(outerInset, outerInset)
      }
    val inner =
      RectF(rect).apply {
        inset(innerInset, innerInset)
      }
    drawInsideStrokeRect(canvas, outer, paints.gold)
    drawInsideStrokeRect(canvas, inner, paints.gold)
  }

  private fun drawCoverCornerOrnaments(
    canvas: Canvas,
    innerLabel: RectF,
    sizeScale: Float = 0.22f,
  ) {
    val texture = assets.coverCornerTexture ?: return
    val cornerSize = min(innerLabel.width(), innerLabel.height()) * sizeScale
    drawCoverCornerOrnament(
      canvas = canvas,
      texture = texture,
      dst =
        RectF(
          innerLabel.left,
          innerLabel.top,
          innerLabel.left + cornerSize,
          innerLabel.top + cornerSize,
        ),
      rotation = 0f,
    )
    drawCoverCornerOrnament(
      canvas = canvas,
      texture = texture,
      dst =
        RectF(
          innerLabel.right - cornerSize,
          innerLabel.top,
          innerLabel.right,
          innerLabel.top + cornerSize,
        ),
      rotation = 90f,
    )
    drawCoverCornerOrnament(
      canvas = canvas,
      texture = texture,
      dst =
        RectF(
          innerLabel.right - cornerSize,
          innerLabel.bottom - cornerSize,
          innerLabel.right,
          innerLabel.bottom,
        ),
      rotation = 180f,
    )
    drawCoverCornerOrnament(
      canvas = canvas,
      texture = texture,
      dst =
        RectF(
          innerLabel.left,
          innerLabel.bottom - cornerSize,
          innerLabel.left + cornerSize,
          innerLabel.bottom,
        ),
      rotation = 270f,
    )
  }

  private fun drawCoverCornerOrnament(
    canvas: Canvas,
    texture: android.graphics.Bitmap,
    dst: RectF,
    rotation: Float,
  ) {
    canvas.save()
    canvas.rotate(rotation, dst.centerX(), dst.centerY())
    canvas.drawBitmap(texture, null, dst, paints.coverCorner)
    canvas.restore()
  }

  private fun drawDirectoryTags(
    canvas: Canvas,
    tags: List<String>,
    left: Float,
    top: Float,
    maxWidth: Float,
  ): Float {
    val tagPaint =
      TextPaint(paints.itemMeta).apply {
        typeface = assets.songTypeface
        textSize = sp(11f)
        color = AndroidColor.argb(210, 24, 22, 20)
        textAlign = Paint.Align.LEFT
      }
    val rowHeight = dp(22f)
    val horizontalGap = dp(6f)
    val verticalGap = dp(6f)
    var x = left
    var y = top
    tags.forEachIndexed { index, tag ->
      val textWidth = tagPaint.measureText(tag)
      val chipWidth = (textWidth + dp(15f)).coerceAtMost(maxWidth)
      if (x > left && x + chipWidth > left + maxWidth) {
        x = left
        y += rowHeight + verticalGap
      }
      val chip = RectF(x, y, x + chipWidth, y + rowHeight)
      drawDirectoryTagChip(canvas, chip, tagAccentArgb(tag))
      canvas.drawText(
        tag,
        chip.left + dp(7.5f),
        chip.centerY() + textCenterOffset(tagPaint),
        tagPaint,
      )
      x += chipWidth + horizontalGap
    }
    return y + rowHeight
  }

  private fun drawDirectoryTagChip(canvas: Canvas, rect: RectF, color: Int) {
    val notch = min(rect.width(), rect.height()) * 0.26f
    tagChipPath.reset()
    tagChipPath.moveTo(rect.left + notch, rect.top)
    tagChipPath.lineTo(rect.right - notch, rect.top)
    tagChipPath.lineTo(rect.right, rect.top + notch)
    tagChipPath.lineTo(rect.right, rect.bottom - notch)
    tagChipPath.lineTo(rect.right - notch, rect.bottom)
    tagChipPath.lineTo(rect.left + notch, rect.bottom)
    tagChipPath.lineTo(rect.left, rect.bottom - notch)
    tagChipPath.lineTo(rect.left, rect.top + notch)
    tagChipPath.close()
    paints.article.color = withAlpha(color, 34)
    canvas.drawPath(tagChipPath, paints.article)
    paints.article.style = Paint.Style.STROKE
    paints.article.strokeWidth = dp(0.85f)
    paints.article.color = withAlpha(color, 184)
    canvas.drawPath(tagChipPath, paints.article)
    paints.article.style = Paint.Style.FILL
  }

  private fun withAlpha(color: Int, alpha: Int): Int {
    return AndroidColor.argb(
      alpha.coerceIn(0, 255),
      AndroidColor.red(color),
      AndroidColor.green(color),
      AndroidColor.blue(color),
    )
  }

  private fun drawArticlePreviewImage(
    canvas: Canvas,
    bitmap: android.graphics.Bitmap,
    dst: RectF,
  ) {
    if (dst.width() <= 0f || dst.height() <= 0f) return
    canvas.save()
    paperClipPath.reset()
    paperClipPath.addRoundRect(dst, dp(8f), dp(8f), Path.Direction.CW)
    canvas.clipPath(paperClipPath)
    paints.article.color = AndroidColor.WHITE
    canvas.drawRect(dst, paints.article)
    paints.article.color = AndroidColor.WHITE
    val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
    val dstRatio = dst.width() / dst.height()
    if (bitmapRatio > dstRatio) {
      val srcWidth = bitmap.height * dstRatio
      val left = (bitmap.width - srcWidth) / 2f
      articleImageSrcRect.set(left.roundToInt(), 0, (left + srcWidth).roundToInt(), bitmap.height)
    } else {
      val srcHeight = bitmap.width / dstRatio
      val top = (bitmap.height - srcHeight) / 2f
      articleImageSrcRect.set(0, top.roundToInt(), bitmap.width, (top + srcHeight).roundToInt())
    }
    canvas.drawBitmap(bitmap, articleImageSrcRect, dst, paints.cover)
    canvas.restore()
  }

  private fun drawDirectoryContent(canvas: Canvas, rect: RectF, page: MemorialPage) {
    val scale = articleResponsiveScale(rect.width())
    val dossier = dossierFor(page.dossierIndex)
    val directoryTitlePaint =
      TextPaint(paints.title).apply {
        typeface = assets.stampTypeface
        textSize = paints.title.textSize * scale
      }
    var y = rect.top + responsiveDp(48f, rect.width())
    drawCenteredText(canvas, "本折提要", rect.centerX(), y, directoryTitlePaint)
    y += responsiveDp(28f, rect.width())
    drawCenteredText(
      canvas,
      if (readerMode == MemorialReaderMode.ArticleReader) {
        "恭呈御览"
      } else {
        "第${(page.dossierIndex + 1).toChineseCount()}封，共${expectedDossierCount().toChineseCount()}封 · 恭呈御览"
      },
      rect.centerX(),
      y,
      paints.meta,
    )
    y += responsiveDp(36f, rect.width())

    val horizontalInset = responsiveDp(44f, rect.width())
    val itemLeft = rect.left + horizontalInset
    val itemWidth = rect.width() - horizontalInset * 2f
    drawTextBlock(canvas, dossier.title, paints.itemTitle, itemLeft, y, itemWidth, 1.28f, false)
    y += responsiveDp(38f, rect.width())
    y = drawDirectoryTags(canvas, dossier.tags.ifEmpty { listOf("待批") }, itemLeft, y, itemWidth)
    y += responsiveDp(18f, rect.width())

    val image = assets.articleImageFor(dossier.imageResName)
    if (image != null) {
      val availableForImage = rect.bottom - y - responsiveDp(160f, rect.width())
      if (availableForImage >= responsiveDp(92f, rect.width())) {
        val maxImageHeight = min(itemWidth, availableForImage)
        val imageHeight =
          min(itemWidth / (image.width.toFloat() / image.height.toFloat()), maxImageHeight)
        val imageRect = RectF(itemLeft, y, itemLeft + itemWidth, y + imageHeight)
        drawArticlePreviewImage(canvas, image, imageRect)
        y = imageRect.bottom + responsiveDp(22f, rect.width())
      }
    }

    canvas.drawLine(itemLeft, y, itemLeft + itemWidth, y, paints.dashed)
    y += responsiveDp(22f, rect.width())
    val summaryLayout =
      buildEllipsizedTextLayout(
        dossier.summary,
        paints.author,
        itemWidth.roundToInt().coerceAtLeast(1),
        1.62f,
        max(
          2,
          ((rect.bottom - y - responsiveDp(62f, rect.width())) / (paints.author.textSize * 1.62f))
            .roundToInt(),
        ),
      )
    drawStaticLayout(
      canvas = canvas,
      layout = summaryLayout,
      left = itemLeft,
      top = y,
    )
    drawPageNumber(canvas, rect, page.pageNumber)
  }

  private fun drawBodyContent(canvas: Canvas, rect: RectF, page: MemorialPage) {
    val scale = articleResponsiveScale(rect.width())
    val dossier = dossierFor(page.dossierIndex)
    val segments =
      bodySegmentsByDossierIndex[page.dossierIndex]
        ?: pages.mapNotNull { if (it.dossierIndex == page.dossierIndex) it.bodyText else null }
    val segment =
      page.bodyText ?: segments.getOrElse(page.bodySegmentIndex) { segments.lastOrNull().orEmpty() }
    var y = rect.top + responsiveDp(54f, rect.width())
    if (page.bodySegmentIndex == 0) {
      val bodyTitlePaint =
        TextPaint(paints.title).apply {
          typeface = assets.songTypeface
          textSize = paints.title.textSize * scale
          letterSpacing = 0.02f
          textAlign = Paint.Align.LEFT
        }
      val titleInset = responsiveDp(54f, rect.width())
      val titleWidth = (rect.width() - titleInset * 2f).roundToInt().coerceAtLeast(1)
      val titleLayout =
        buildEllipsizedTextLayout(
          dossier.title,
          bodyTitlePaint,
          titleWidth,
          1.24f,
          2,
          Layout.Alignment.ALIGN_CENTER,
        )
      drawStaticLayout(
        canvas = canvas,
        layout = titleLayout,
        left = rect.left + (rect.width() - titleLayout.width) / 2f,
        top = y,
      )
      y += titleLayout.height + responsiveDp(18f, rect.width())
      y += responsiveDp(18f, rect.width())
    } else {
      y = rect.top + responsiveDp(64f, rect.width())
    }
    val horizontalInset = responsiveDp(42f, rect.width())
    val left = rect.left + horizontalInset
    val width = rect.width() - horizontalInset * 2f
    canvas.save()
    canvas.clipRect(left, y, left + width, rect.bottom - responsiveDp(54f, rect.width()))
    drawTextBlock(
      canvas,
      segment,
      paints.author,
      left,
      y,
      width,
      1.82f,
      false,
    )
    canvas.restore()
    drawPageNumber(canvas, rect, page.pageNumber)
  }

  private fun drawEndContent(canvas: Canvas, rect: RectF, page: MemorialPage) {
    val endPaint =
      TextPaint(paints.title).apply {
        textSize = sp(42f)
        letterSpacing = 0.16f
      }
    val endText = "皇上圣安"
    val endGap = dp(16f)
    val endHeight = verticalTextHeight(endText, endPaint, endGap)
    val endTop = rect.centerY() - endHeight / 2f
    drawVerticalText(
      canvas = canvas,
      text = endText,
      centerX = rect.centerX(),
      top = endTop,
      paint = endPaint,
      lineGap = endGap,
    )
    drawPageNumber(canvas, rect, page.pageNumber)
  }

  private fun drawParagraph(
    canvas: Canvas,
    text: String,
    left: Float,
    top: Float,
    width: Float,
    paint: TextPaint = paints.quote,
  ): Float {
    val layout = buildTextLayout("　　$text", paint, width.roundToInt(), 2.0f, true)
    canvas.save()
    canvas.translate(left, top)
    layout.draw(canvas)
    canvas.restore()
    return top + layout.height + dp(20f)
  }

  private fun drawTextBlock(
    canvas: Canvas,
    text: String,
    paint: TextPaint,
    left: Float,
    top: Float,
    width: Float,
    lineHeightMultiplier: Float,
    justify: Boolean,
    alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
  ): Float {
    val layout =
      buildTextLayout(text, paint, width.roundToInt(), lineHeightMultiplier, justify, alignment)
    canvas.save()
    canvas.translate(left, top)
    layout.draw(canvas)
    canvas.restore()
    return top + layout.height
  }

  private fun verticalTextHeight(text: String, paint: TextPaint, lineGap: Float): Float {
    if (text.isEmpty()) return 0f
    return text.length * paint.textSize + (text.length - 1).coerceAtLeast(0) * lineGap
  }

  private fun verticalTextWidth(text: String, paint: TextPaint): Float {
    return text.maxOfOrNull { paint.measureText(it.toString()) } ?: 0f
  }

  private fun ellipsizeVerticalText(
    text: String,
    paint: TextPaint,
    maxHeight: Float,
    lineGap: Float,
  ): String {
    val normalized = text.trim()
    if (normalized.isEmpty()) return ""
    if (verticalTextHeight(normalized, paint, lineGap) <= maxHeight) return normalized
    val ellipsis = "…"
    val ellipsisHeight = verticalTextHeight(ellipsis, paint, lineGap)
    val charStep = paint.textSize + lineGap
    val availableChars = ((maxHeight - ellipsisHeight) / charStep).toInt().coerceAtLeast(1)
    return normalized.take(availableChars) + ellipsis
  }

  private fun drawPageNumber(canvas: Canvas, rect: RectF, pageNumber: String) {
    drawCenteredText(canvas, pageNumber, rect.right - dp(26f), rect.bottom - dp(22f), paints.meta)
  }

  private fun drawOpeningSegmentTone(
    canvas: Canvas,
    rect: RectF,
    foldAmount: Float,
    pivotsOnRight: Boolean,
  ) {
    if (foldAmount <= 0f) return

    val darkAlpha = (46f * foldAmount).roundToInt().coerceIn(0, 72)
    val lightAlpha = (24f * foldAmount).roundToInt().coerceIn(0, 48)
    val colors =
      if (pivotsOnRight) {
        intArrayOf(
          AndroidColor.argb(lightAlpha, 255, 255, 255),
          AndroidColor.TRANSPARENT,
          AndroidColor.argb(darkAlpha, 0, 0, 0),
        )
      } else {
        intArrayOf(
          AndroidColor.argb(darkAlpha, 0, 0, 0),
          AndroidColor.TRANSPARENT,
          AndroidColor.argb(lightAlpha, 255, 255, 255),
        )
      }
    paints.surface.shader =
      LinearGradient(
        rect.left,
        0f,
        rect.right,
        0f,
        colors,
        floatArrayOf(0f, 0.52f, 1f),
        Shader.TileMode.CLAMP,
      )
    canvas.drawRect(rect, paints.surface)
    paints.surface.shader = null
  }

  private fun drawArticleSurface(canvas: Canvas, rect: RectF) {
    paints.surface.shader =
      LinearGradient(
        rect.left,
        0f,
        rect.right,
        0f,
        intArrayOf(
          AndroidColor.argb(6, 0, 0, 0),
          AndroidColor.TRANSPARENT,
          AndroidColor.TRANSPARENT,
          AndroidColor.argb(9, 0, 0, 0),
        ),
        floatArrayOf(0f, 0.08f, 0.92f, 1f),
        Shader.TileMode.CLAMP,
      )
    canvas.drawRect(rect, paints.surface)
    paints.surface.shader = null

    paints.surface.shader =
      LinearGradient(
        rect.right - dp(18f),
        0f,
        rect.right,
        0f,
        intArrayOf(
          AndroidColor.TRANSPARENT,
          AndroidColor.argb(12, 0, 0, 0),
        ),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP,
      )
    canvas.drawRect(rect.right - dp(18f), rect.top, rect.right, rect.bottom, paints.surface)
    paints.surface.shader = null

    paints.surface.shader =
      LinearGradient(
        rect.left,
        0f,
        rect.left + dp(12f),
        0f,
        intArrayOf(
          AndroidColor.argb(8, 255, 255, 255),
          AndroidColor.TRANSPARENT,
        ),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP,
      )
    canvas.drawRect(rect.left, rect.top, rect.left + dp(12f), rect.bottom, paints.surface)
    paints.surface.shader = null

    paints.crease.alpha = 18
    canvas.drawLine(rect.left, rect.top + dp(10f), rect.left, rect.bottom - dp(10f), paints.crease)
    canvas.drawLine(
      rect.right - 1f,
      rect.top + dp(10f),
      rect.right - 1f,
      rect.bottom - dp(10f),
      paints.crease,
    )
    paints.crease.alpha = 255
    canvas.drawLine(rect.left, rect.bottom - 1f, rect.right, rect.bottom - 1f, paints.dashed)
  }

  private fun scrollByAmount(delta: Float) {
    if (openProgress < 1f) return
    val next = (foldScrollX + delta).coerceIn(0f, maxScrollX)
    if (next != foldScrollX) {
      foldScrollX = next
      invalidate()
    }
  }

  private fun finishReadingDragWithSnap() {
    snapToNearestSpread()
  }

  private fun snapToNearestSpread() {
    if (articleWidth <= 0f || maxScrollX <= 0f) return
    val target = nearestReadingSnapTarget(foldScrollX)
    if (abs(target - foldScrollX) < 1f) {
      foldScrollX = target
      invalidate()
      return
    }
    scrollReturnAnimator?.cancel()
    val start = foldScrollX
    scrollReturnAnimator =
      ValueAnimator.ofFloat(start, target).apply {
        duration =
          ((180L + abs(target - start) / max(1f, readingSnapStep()) * 150L).roundToInt())
            .coerceIn(180, 360)
            .toLong()
        interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        addUpdateListener { animator ->
          foldScrollX = (animator.animatedValue as Float).coerceIn(0f, maxScrollX)
          invalidate()
        }
        addListener(
          object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
              if (scrollReturnAnimator == animation) {
                scrollReturnAnimator = null
              }
              foldScrollX = target
              invalidate()
            }
          }
        )
        start()
      }
  }

  private fun nearestReadingSnapTarget(scrollX: Float): Float {
    if (articleWidth <= 0f || maxScrollX <= 0f) return 0f
    val clampedScroll = scrollX.coerceIn(0f, maxScrollX)
    return readingSnapTargets().minByOrNull { abs(it - clampedScroll) } ?: clampedScroll
  }

  private fun readingSnapStep(): Float {
    return articleWidth
  }

  private fun readingSnapTargets(): List<Float> {
    if (articleWidth <= 0f || articles.isEmpty()) return listOf(0f)
    val maxStartPageIndex = maxReadingStartPageIndex()
    return (0..maxStartPageIndex).map { index -> readingSnapTargetForPage(index) }
  }

  private fun readingSnapTargetForPage(pageIndex: Int): Float {
    if (articleWidth <= 0f || maxScrollX <= 0f) return 0f
    return (pageIndex.coerceIn(0, maxReadingStartPageIndex()) * articleWidth).coerceIn(
      0f,
      maxScrollX,
    )
  }

  private fun maxReadingStartPageIndex(): Int {
    val pagesPerSpread =
      pagesPerSpreadForViewport(foldRight - foldLeft, foldBottom - foldTop)
        .roundToInt()
        .coerceIn(1, 2)
    return (articles.size - pagesPerSpread).coerceAtLeast(0)
  }

  private fun readingStartPageIndexForScroll(
    scrollX: Float = foldScrollX,
    pageWidth: Float = articleWidth,
  ): Int {
    if (pageWidth <= 0f) return 0
    return (scrollX / pageWidth).roundToInt().coerceAtLeast(0)
  }

  private fun dp(value: Float): Float = value * displayDensity

  private fun sp(value: Float): Float = value * scaledDensity

  private fun articleResponsiveScale(articleWidth: Float = this.articleWidth): Float {
    return (articleWidth / dp(360f)).coerceIn(0.82f, 1f)
  }

  private fun responsiveDp(value: Float, articleWidth: Float = this.articleWidth): Float {
    return dp(value) * articleResponsiveScale(articleWidth)
  }

  private fun responsiveSp(value: Float, articleWidth: Float = this.articleWidth): Float {
    return sp(value) * articleResponsiveScale(articleWidth)
  }

  private fun startOpenAnimationIfReady() {
    if (hasPlayedOpenAnimation || width <= 0 || height <= 0 || articles.isEmpty()) return

    hasPlayedOpenAnimation = true
    stage = MemorialStage.CoverOnly
    foldScrollX = 0f
    openProgress = 0f
    invalidate()
  }

  private fun expandFromCover() {
    if (stage != MemorialStage.CoverOnly) return
    hasPlayedOpenAnimation = true
    hideReadingControlsDuringClose = false
    stage = MemorialStage.Opening
    foldScrollX = 0f
    animateOpenProgress(
      from = 0f,
      to = 1f,
      durationMs = MEMORIAL_OPEN_CLOSE_DURATION_MS,
      onFinished = {
        stage = MemorialStage.Expanded
        openProgress = 1f
        invalidate()
      },
    )
  }

  private fun startCoverVerdict(
    stamp: MemorialStamp,
    dx: Float,
    verticalDirection: Float,
    fromButton: Boolean = false,
    motion: CoverVerdictMotion =
      if (fromButton) CoverVerdictMotion.Button else CoverVerdictMotion.Gesture,
  ) {
    val initialStampStrength = coverPreviewStampStrength.coerceIn(0f, 1f)
    coverPreviewStamp = stamp
    coverPreviewStampStrength = initialStampStrength
    coverPreviewStampTargetStrength = initialStampStrength
    coverFinalStamp = stamp
    coverFinalStampFromButton = fromButton
    coverFinalStampStrength = initialStampStrength
    coverStackLiftProgress = 0f
    val startX = coverDragX
    val startY = coverDragY
    val targetX =
      when {
        verticalDirection < 0f -> startX
        dx >= 0f -> width.toFloat() + articleWidth
        else -> -width.toFloat() - articleWidth
      }
    val targetY =
      if (verticalDirection < 0f) {
        -height.toFloat() - dp(80f)
      } else {
        startY
      }
    coverSwipeAnimator?.cancel()
    coverSwipeAnimator =
      ValueAnimator.ofFloat(0f, 1f).apply {
        duration = COVER_VERDICT_DURATION_MS
        interpolator = LinearInterpolator()
        addUpdateListener { animator ->
          val t = animator.animatedValue as Float
          val stampT =
            when (motion) {
              CoverVerdictMotion.Button -> lerp(initialStampStrength, 1f, smoothStep(0f, 0.333f, t))
              CoverVerdictMotion.Gesture -> lerp(initialStampStrength, 1f, smoothStep(0f, 0.12f, t))
            }
          val moveT =
            when (motion) {
              CoverVerdictMotion.Button -> smoothStep(0.333f, 1f, t)
              CoverVerdictMotion.Gesture -> smoothStep(0f, 1f, t)
            }
          coverFinalStampStrength = stampT
          coverPreviewStampStrength = stampT
          coverPreviewStampTargetStrength = stampT
          coverStackLiftProgress = smoothStep(0.18f, 0.78f, t)
          coverDragX = lerp(startX, targetX, moveT)
          coverDragY = lerp(startY, targetY, moveT)
          invalidate()
        }
        addListener(
          object : AnimatorListenerAdapter() {
            private var canceled = false

            override fun onAnimationCancel(animation: Animator) {
              canceled = true
            }

            override fun onAnimationEnd(animation: Animator) {
              if (coverSwipeAnimator == animation) {
                coverSwipeAnimator = null
              }
              if (!canceled) {
                coverFinalStamp = null
                coverFinalStampFromButton = false
                coverFinalStampStrength = 0f
                coverPreviewStampStrength = 0f
                coverPreviewStampTargetStrength = 0f
                advanceAfterVerdict()
              }
            }
          }
        )
        start()
      }
  }

  private fun triggerCoverAction(stamp: MemorialStamp) {
    when (stamp) {
      MemorialStamp.Reject -> {
        coverDragX = 0f
        coverDragY = 0f
        coverPreviewStamp = stamp
        coverPreviewStampStrength = 0f
        coverPreviewStampTargetStrength = 0f
        startCoverVerdict(stamp, dx = -dp(96f), verticalDirection = 0f, fromButton = true)
      }
      MemorialStamp.Approve -> {
        coverDragX = 0f
        coverDragY = 0f
        coverPreviewStamp = stamp
        coverPreviewStampStrength = 0f
        coverPreviewStampTargetStrength = 0f
        startCoverVerdict(stamp, dx = dp(96f), verticalDirection = 0f, fromButton = true)
      }
      MemorialStamp.Keep -> {
        coverDragX = 0f
        coverDragY = 0f
        coverPreviewStamp = stamp
        coverPreviewStampStrength = 0f
        coverPreviewStampTargetStrength = 0f
        startCoverVerdict(stamp, dx = 0f, verticalDirection = -1f, fromButton = true)
      }
      else -> Unit
    }
  }

  private fun animateCoverBack() {
    val startX = coverDragX
    val startY = coverDragY
    coverPreviewStamp = null
    coverPreviewStampStrength = 0f
    coverPreviewStampTargetStrength = 0f
    coverSwipeAnimator?.cancel()
    coverSwipeAnimator =
      ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 260L
        interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        addUpdateListener { animator ->
          val t = animator.animatedValue as Float
          coverDragX = lerp(startX, 0f, t)
          coverDragY = lerp(startY, 0f, t)
          invalidate()
        }
        addListener(
          object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
              if (coverSwipeAnimator == animation) {
                coverSwipeAnimator = null
              }
              coverDragX = 0f
              coverDragY = 0f
              coverFinalStamp = null
              coverFinalStampFromButton = false
              coverFinalStampStrength = 0f
              clearAttachedCoverStamp()
              invalidate()
            }
          }
        )
        start()
      }
  }

  private fun showNextCoverFromStack() {
    stage = MemorialStage.CoverOnly
    openProgress = 0f
    foldScrollX = 0f
    coverStackIndex += 1
    coverDragX = 0f
    coverDragY = 0f
    coverPreviewStamp = null
    coverPreviewStampStrength = 0f
    coverPreviewStampTargetStrength = 0f
    coverFinalStamp = null
    coverFinalStampFromButton = false
    coverFinalStampStrength = 0f
    clearAttachedCoverStamp()
    hideSummary(animated = false)
    summaryPinnedByTap = false
    summaryPinnedAtDown = false
    summaryShownByHold = false
    removeCallbacks(showSummaryRunnable)
    coverSwipeAnimator?.cancel()
    coverSwipeAnimator = null
    coverStackLiftProgress = 1f
    rebuildPagesForCurrentDossier()
    invalidate()
  }

  private fun advanceAfterVerdict() {
    removeCallbacks(showSummaryRunnable)
    hideSummary(animated = false)
    summaryPinnedByTap = false
    summaryPinnedAtDown = false
    summaryShownByHold = false
    if (coverStackIndex >= expectedDossierCount() - 1) {
      coverStackIndex = expectedDossierCount()
      coverDragX = 0f
      coverDragY = 0f
      coverPreviewStamp = null
      coverFinalStamp = null
      coverFinalStampFromButton = false
      coverFinalStampStrength = 0f
      clearAttachedCoverStamp()
      currentStamp = null
      stampProgress = 0f
      openProgress = 0f
      foldScrollX = 0f
      showCompletedState()
    } else {
      showNextCoverFromStack()
    }
  }

  private fun showCompletedState() {
    completedAnimator?.cancel()
    completedAlpha = 0f
    stage = MemorialStage.Completed
    completedAnimator =
      ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 680L
        interpolator = PathInterpolator(0.18f, 0f, 0f, 1f)
        addUpdateListener { animator ->
          completedAlpha = animator.animatedValue as Float
          invalidate()
        }
        addListener(
          object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
              if (completedAnimator == animation) {
                completedAnimator = null
              }
              completedAlpha = 1f
              invalidate()
            }
          }
        )
        start()
      }
  }

  private fun dismissCompletedState() {
    if (stage != MemorialStage.Completed) {
      onAutoDismiss?.invoke()
      return
    }
    completedAnimator?.cancel()
    val startAlpha = completedAlpha.coerceIn(0f, 1f)
    completedAnimator =
      ValueAnimator.ofFloat(startAlpha, 0f).apply {
        duration = 360L
        interpolator = PathInterpolator(0.3f, 0f, 0.8f, 1f)
        addUpdateListener { animator ->
          completedAlpha = animator.animatedValue as Float
          invalidate()
        }
        addListener(
          object : AnimatorListenerAdapter() {
            private var canceled = false

            override fun onAnimationCancel(animation: Animator) {
              canceled = true
            }

            override fun onAnimationEnd(animation: Animator) {
              if (completedAnimator == animation) {
                completedAnimator = null
              }
              if (!canceled) {
                completedAlpha = 0f
                onAutoDismiss?.invoke()
              }
            }
          }
        )
        start()
      }
  }

  private fun startStampAndDismiss(stamp: MemorialStamp) {
    if (currentStamp != null || coverSwipeAnimator != null) return
    when (stamp) {
      MemorialStamp.Like,
      MemorialStamp.Dislike ->
        returnToCoverForVerdict {
          startAttachedCoverStamp(stamp) {
            collapseStampedCoverForVerdict(stamp)
          }
        }
      else ->
        returnToFirstSpread {
          startStamp(stamp, StampCompletion.ResetCover)
        }
    }
  }

  private fun returnToCoverForVerdict(onFinished: () -> Unit) {
    hideReadingControlsDuringClose = true
    isDragging = false
    returnToFirstSpread {
      foldScrollX = 0f
      invalidate()
      onFinished()
    }
  }

  private fun startAttachedCoverStamp(stamp: MemorialStamp, onFinished: () -> Unit) {
    stampAnimator?.cancel()
    attachedCoverStamp = stamp
    attachedCoverStampStrength = 0f
    stampAnimator =
      ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 420L
        interpolator = PathInterpolator(0.16f, 0f, 0f, 1f)
        addUpdateListener { animator ->
          attachedCoverStampStrength = animator.animatedValue as Float
          invalidate()
        }
        addListener(
          object : AnimatorListenerAdapter() {
            private var canceled = false

            override fun onAnimationCancel(animation: Animator) {
              canceled = true
            }

            override fun onAnimationEnd(animation: Animator) {
              if (stampAnimator == animation) {
                stampAnimator = null
              }
              if (!canceled) {
                attachedCoverStampStrength = 1f
                invalidate()
                onFinished()
              }
            }
          }
        )
        start()
      }
  }

  private fun collapseStampedCoverForVerdict(stamp: MemorialStamp) {
    stage = MemorialStage.Closing
    animateOpenProgress(
      from = openProgress.coerceIn(0f, 1f),
      to = 0f,
      durationMs = MEMORIAL_OPEN_CLOSE_DURATION_MS,
      onFinished = {
        stage = MemorialStage.CoverOnly
        openProgress = 0f
        foldScrollX = 0f
        currentStamp = null
        stampProgress = 0f
        hideReadingControlsDuringClose = false
        clearAttachedCoverStamp()
        coverDragY = 0f
        coverDragX = 0f
        coverPreviewStamp = stamp
        coverPreviewStampStrength = 1f
        coverPreviewStampTargetStrength = 1f
        startCoverVerdict(
          stamp = stamp,
          dx = if (stamp == MemorialStamp.Like) dp(96f) else -dp(96f),
          verticalDirection = 0f,
          fromButton = true,
          motion = CoverVerdictMotion.Gesture,
        )
      },
    )
  }

  private fun startStamp(stamp: MemorialStamp, completion: StampCompletion) {
    if (currentStamp != null) return
    stampAnimator?.cancel()
    currentStamp = stamp
    stampCompletion = completion
    stampProgress = 0f
    stampAnimator =
      ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 620L
        interpolator = PathInterpolator(0.16f, 0f, 0f, 1f)
        addUpdateListener { animator ->
          stampProgress = animator.animatedValue as Float
          invalidate()
        }
        addListener(
          object : AnimatorListenerAdapter() {
            private var canceled = false

            override fun onAnimationCancel(animation: Animator) {
              canceled = true
            }

            override fun onAnimationEnd(animation: Animator) {
              if (stampAnimator == animation) {
                stampAnimator = null
              }
              if (!canceled) {
                when (stampCompletion) {
                  StampCompletion.AutoDismiss ->
                    postDelayed(
                      {
                        onAutoDismiss?.invoke()
                      },
                      170L,
                    )

                  StampCompletion.ResetCover ->
                    postDelayed(
                      {
                        currentStamp = null
                        stampProgress = 0f
                        coverPreviewStamp = null
                        returnToCoverStack(advanceStack = true)
                      },
                      130L,
                    )
                }
              }
            }
          }
        )
        start()
      }
  }

  private fun averagePointerX(event: MotionEvent): Float {
    var sum = 0f
    for (index in 0 until event.pointerCount) {
      sum += event.getX(index)
    }
    return sum / event.pointerCount.coerceAtLeast(1)
  }

  private fun averagePointerY(event: MotionEvent): Float {
    var sum = 0f
    for (index in 0 until event.pointerCount) {
      sum += event.getY(index)
    }
    return sum / event.pointerCount.coerceAtLeast(1)
  }

  fun collapseToStack() {
    returnToCoverStack(advanceStack = false)
  }

  fun expandCurrentCover() {
    expandFromCover()
  }

  private fun closeFromExpandedReader() {
    if (readerMode == MemorialReaderMode.ReviewStack) {
      returnToCoverStack(advanceStack = false)
    } else {
      closeWithAnimation {
        onCloseAnimationFinished?.invoke() ?: onAutoDismiss?.invoke()
      }
    }
  }

  private fun returnToCoverStack(advanceStack: Boolean) {
    if (articles.isEmpty()) return
    hideReadingControlsDuringClose = advanceStack
    val finishReturn = {
      if (advanceStack) {
        advanceAfterVerdict()
      } else {
        hideReadingControlsDuringClose = false
        stage = MemorialStage.CoverOnly
        openProgress = 0f
        foldScrollX = 0f
        coverDragX = 0f
        coverDragY = 0f
        coverPreviewStamp = null
        coverStackLiftProgress = 1f
        coverFinalStamp = null
        coverFinalStampFromButton = false
        coverFinalStampStrength = 0f
        clearAttachedCoverStamp()
        invalidate()
      }
    }
    if (stage == MemorialStage.CoverOnly || openProgress <= 0f) {
      finishReturn()
      hideReadingControlsDuringClose = false
      return
    }
    closeWithAnimation {
      finishReturn()
      hideReadingControlsDuringClose = false
    }
  }

  fun closeWithAnimation(onFinished: () -> Unit) {
    if (articles.isEmpty()) {
      onFinished()
      return
    }

    isDragging = false
    if (stage == MemorialStage.CoverOnly) {
      onFinished()
      return
    }
    val startScrollX = foldScrollX.coerceIn(0f, maxScrollX)
    if (startScrollX > 1f) {
      returnToFirstSpread {
        stage = MemorialStage.Closing
        animateOpenProgress(
          from = openProgress.coerceIn(0f, 1f),
          to = 0f,
          durationMs = MEMORIAL_OPEN_CLOSE_DURATION_MS,
          onFinished = onFinished,
        )
      }
      return
    }
    foldScrollX = 0f
    stage = MemorialStage.Closing
    animateOpenProgress(
      from = openProgress.coerceIn(0f, 1f),
      to = 0f,
      durationMs = MEMORIAL_OPEN_CLOSE_DURATION_MS,
      onFinished = onFinished,
    )
  }

  private fun returnToFirstSpread(onFinished: () -> Unit) {
    val startScrollX = foldScrollX.coerceIn(0f, maxScrollX)
    if (startScrollX <= 1f) {
      foldScrollX = 0f
      invalidate()
      onFinished()
      return
    }

    scrollReturnAnimator?.cancel()
    scrollReturnAnimator =
      ValueAnimator.ofFloat(startScrollX, 0f).apply {
        duration =
          ((360L + startScrollX / max(1f, articleWidth) * 170L).roundToInt())
            .coerceIn(420, 760)
            .toLong()
        interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        addUpdateListener { animator ->
          foldScrollX = (animator.animatedValue as Float).coerceIn(0f, maxScrollX)
          invalidate()
        }
        addListener(
          object : AnimatorListenerAdapter() {
            private var canceled = false

            override fun onAnimationCancel(animation: Animator) {
              canceled = true
            }

            override fun onAnimationEnd(animation: Animator) {
              if (scrollReturnAnimator == animation) {
                scrollReturnAnimator = null
              }
              if (!canceled) {
                foldScrollX = 0f
                invalidate()
                onFinished()
              }
            }
          }
        )
        start()
      }
  }

  private fun animateOpenProgress(
    from: Float,
    to: Float,
    durationMs: Long,
    onFinished: (() -> Unit)?,
  ) {
    transitionAnimator?.cancel()
    openProgress = from.coerceIn(0f, 1f)
    transitionAnimator =
      ValueAnimator.ofFloat(openProgress, to.coerceIn(0f, 1f)).apply {
        duration = durationMs
        interpolator = PathInterpolator(0.42f, 0f, 0.58f, 1f)
        addUpdateListener { animator ->
          openProgress = animator.animatedValue as Float
          invalidate()
        }
        addListener(
          object : AnimatorListenerAdapter() {
            private var canceled = false

            override fun onAnimationCancel(animation: Animator) {
              canceled = true
            }

            override fun onAnimationEnd(animation: Animator) {
              if (transitionAnimator == animation) {
                transitionAnimator = null
              }
              if (!canceled) {
                openProgress = to.coerceIn(0f, 1f)
                invalidate()
                onFinished?.invoke()
              }
            }
          }
        )
        start()
      }
  }
}
