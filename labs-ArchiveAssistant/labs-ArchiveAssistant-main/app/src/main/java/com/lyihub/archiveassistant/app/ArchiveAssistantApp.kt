package com.lyihub.archiveassistant.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lyihub.archiveassistant.data.AiEnginePresetRepository
import com.lyihub.archiveassistant.data.AiEngineSettingsRepository
import com.lyihub.archiveassistant.data.AppDataRepository
import com.lyihub.archiveassistant.data.OkHttpModelDownloadManager
import com.lyihub.archiveassistant.domain.AiEnginePreset
import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AppPane
import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.service.LocalInferenceConnection
import com.lyihub.archiveassistant.state.ArchiveAssistantState
import com.lyihub.archiveassistant.state.ArchiveAssistantStateStore
import com.lyihub.archiveassistant.ui.layout.LayoutMode
import com.lyihub.archiveassistant.ui.layout.rememberWindowLayoutInfo
import com.lyihub.archiveassistant.ui.screens.AddItemDialog
import com.lyihub.archiveassistant.ui.screens.ArticleMemorialReaderOverlay
import com.lyihub.archiveassistant.ui.screens.ClipboardDialog
import com.lyihub.archiveassistant.ui.screens.DeleteItemConfirmDialog
import com.lyihub.archiveassistant.ui.screens.DetailPane
import com.lyihub.archiveassistant.ui.screens.HomePane
import com.lyihub.archiveassistant.ui.screens.MemorialBriefingPane
import com.lyihub.archiveassistant.ui.screens.MemorialDemoOverlay
import com.lyihub.archiveassistant.ui.screens.SettingsPane
import com.lyihub.archiveassistant.ui.screens.TOTAL_PENDING_MEMORIALS
import com.lyihub.archiveassistant.ui.screens.TopicManagementDialogs
import com.lyihub.archiveassistant.ui.theme.ImperialIvory
import kotlinx.coroutines.launch

@Composable
fun ArchiveAssistantApp(
  stateStore: ArchiveAssistantStateStore? = null,
  aiSettingsRepository: AiEngineSettingsRepository? = null,
  aiPresetRepository: AiEnginePresetRepository? = null,
  appDataRepository: AppDataRepository? = null,
) {
  val context = LocalContext.current
  val effectiveStateStore =
    stateStore
      ?: androidx.compose.runtime.remember(appDataRepository) {
        ArchiveAssistantStateStore(
          appDataRepository = appDataRepository,
          aiSettingsRepository = aiSettingsRepository,
          modelDownloadManager = OkHttpModelDownloadManager(context),
          inferenceConnection = LocalInferenceConnection(context),
          androidContext = context,
        )
      }
  val coroutineScope = rememberCoroutineScope()
  val onAiSettingsChanged: (AiEngineSettings) -> Unit = { settings ->
    effectiveStateStore.updateAiSettings(settings)
  }
  val modelFilePickerLauncher =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let(effectiveStateStore::importLocalModel)
    }
  val onChooseModelFile = {
    modelFilePickerLauncher.launch(arrayOf("*/*"))
  }

  val presets =
    aiPresetRepository?.presets?.collectAsState(initial = emptyList())?.value ?: emptyList()
  val onPresetsChanged: (List<AiEnginePreset>) -> Unit = { updatedPresets ->
    aiPresetRepository?.let { repository ->
      coroutineScope.launch {
        repository.save(updatedPresets)
      }
    }
  }

  LaunchedEffect(aiSettingsRepository) {
    aiSettingsRepository?.settings?.collect(effectiveStateStore::updateAiSettings)
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  val view = LocalView.current
  DisposableEffect(lifecycleOwner, context, view, effectiveStateStore) {
    var pendingRead: Runnable? = null
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        pendingRead?.let(view::removeCallbacks)
        pendingRead =
          Runnable {
              if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                readClipboardPayload(context)?.let { payload ->
                  effectiveStateStore.showClipboard(
                    content = payload.text.orEmpty(),
                    imageUri = payload.imageUri,
                    sourceUri = payload.sourceUri,
                    sourceContentType = payload.sourceContentType,
                    sourceDocumentFormat = payload.sourceDocumentFormat,
                    sourceFileName = payload.sourceFileName,
                  )
                }
              }
            }
            .also { view.postDelayed(it, 200) }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      pendingRead?.let(view::removeCallbacks)
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  val state = effectiveStateStore.state
  val layoutInfo = rememberWindowLayoutInfo()
  val showMemorialDemo = remember { mutableStateOf(false) }
  val openMemorialDemo = { showMemorialDemo.value = true }
  val openMemorialBriefing = { effectiveStateStore.openMemorialBriefing() }

  val layoutModeTag =
    when (layoutInfo.mode) {
      LayoutMode.COMPACT -> "layout-mode-compact"
      LayoutMode.EXPANDED -> "layout-mode-expanded"
      LayoutMode.FOLDABLE -> "layout-mode-foldable"
    }

  Box(modifier = Modifier.fillMaxSize().background(ImperialIvory).testTag(layoutModeTag)) {
    if (layoutInfo.mode == LayoutMode.COMPACT) {
      SinglePaneLayout(
        stateStore = effectiveStateStore,
        onAiSettingsChanged = onAiSettingsChanged,
        presets = presets,
        onPresetsChanged = onPresetsChanged,
        onChooseModelFile = onChooseModelFile,
        onOpenMemorialBriefing = openMemorialBriefing,
        onOpenMemorialDemo = openMemorialDemo,
      )
    } else {
      WideWorkspaceLayout(
        stateStore = effectiveStateStore,
        onAiSettingsChanged = onAiSettingsChanged,
        presets = presets,
        onPresetsChanged = onPresetsChanged,
        onChooseModelFile = onChooseModelFile,
        onOpenMemorialBriefing = openMemorialBriefing,
        onOpenMemorialDemo = openMemorialDemo,
      )
    }

    state.modalItem?.let { item ->
      ArticleMemorialReaderOverlay(
        item = item,
        onDismiss = effectiveStateStore::closeCardModal,
      )
    }

    state.editingItem?.let { item ->
      AddItemDialog(
        onDismiss = effectiveStateStore::closeEditItemDialog,
        onConfirm = {
          _,
          title,
          contentType,
          sourceUrl,
          summary,
          useAiSummary,
          documentFormat,
          fileName ->
          effectiveStateStore.confirmEditItem(
            title,
            contentType,
            sourceUrl,
            summary,
            useAiSummary,
            documentFormat,
            fileName,
          )
        },
        validationMessage = state.editItemDialogValidationMessage,
        initialItem = item,
        topics = state.topics,
        initialTopicId = item.topicId,
      )
    }

    if (state.addItemDialogVisible) {
      AddItemDialog(
        onDismiss = effectiveStateStore::closeAddItemDialog,
        onConfirm = {
          topicId,
          title,
          contentType,
          sourceUrl,
          summary,
          useAiSummary,
          documentFormat,
          fileName ->
          effectiveStateStore.confirmAddItem(
            topicId,
            title,
            contentType,
            sourceUrl,
            summary,
            useAiSummary,
            documentFormat,
            fileName,
          )
        },
        validationMessage = state.addItemDialogValidationMessage,
        prefill = state.addItemDialogPrefill,
        topics = state.topics,
        initialTopicId = state.selectedTopicId,
      )
    }

    state.deleteConfirmItemId?.let { itemId ->
      val deletingItem = state.items.firstOrNull { it.id == itemId }
      DeleteItemConfirmDialog(
        itemTitle = deletingItem?.title ?: "",
        onConfirm = effectiveStateStore::confirmDeleteItem,
        onDismiss = effectiveStateStore::closeDeleteItemConfirmDialog,
      )
    }

    if (state.showClipboardDialog) {
      ClipboardDialog(
        content = state.clipboardContent ?: "",
        imageUri = state.clipboardImageUri,
        sourceLabel = state.clipboardSourceLabel,
        sourceContentType = state.clipboardSourceContentType,
        sourceDocumentFormat = state.clipboardSourceDocumentFormat,
        sourceFileName = state.clipboardSourceFileName,
        isSmartSummarizing = state.isSmartSummarizing,
        smartSummarizationMessage = state.smartSummarizationMessage,
        onSummarize = effectiveStateStore::acceptClipboardAndSummarize,
        onManualCreate = effectiveStateStore::acceptClipboardAndManualCreate,
        onDismiss = effectiveStateStore::dismissClipboardDialog,
      )
    }

    if (showMemorialDemo.value) {
      MemorialDemoOverlay(
        items = fixedPendingMemorialItems(state),
        onDismiss = { showMemorialDemo.value = false },
      )
    }
  }
}

private data class ClipboardPayload(
  val text: String?,
  val imageUri: String?,
  val sourceUri: String?,
  val sourceContentType: ContentType?,
  val sourceDocumentFormat: DocumentFormat?,
  val sourceFileName: String?,
)

data class DragClipboardPayload(
  val content: String?,
  val imageUri: String?,
  val sourceUri: String?,
  val sourceContentType: ContentType?,
  val sourceDocumentFormat: DocumentFormat?,
  val sourceFileName: String?,
  val sourceLabel: String,
  val ignoredItemCount: Int,
)

data class DragItemClassification(
  val contentType: ContentType,
  val documentFormat: DocumentFormat?,
)

private data class ClipboardSource(
  val uri: String,
  val contentType: ContentType,
  val documentFormat: DocumentFormat?,
  val fileName: String?,
)

private fun readClipboardPayload(context: Context): ClipboardPayload? {
  val clip =
    try {
      context.getSystemService(ClipboardManager::class.java)?.primaryClip
    } catch (_: Exception) {
      null
    } ?: return null

  val source = readableClipboardSource(context, clip)
  val text =
    (0 until clip.itemCount).firstNotNullOfOrNull { index ->
      val item = clip.getItemAt(index)
      if (item.uri != null && source?.uri == item.uri.toString()) {
        null
      } else {
        try {
          item.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
          null
        }
      }
    }

  return if (text != null || source != null) {
    ClipboardPayload(
      text = text ?: source?.fileName,
      imageUri = source?.uri?.takeIf { source.contentType == ContentType.IMAGE_SCREENSHOT },
      sourceUri = source?.uri,
      sourceContentType = source?.contentType,
      sourceDocumentFormat = source?.documentFormat,
      sourceFileName = source?.fileName,
    )
  } else {
    null
  }
}

fun isMimeAllowed(mimeTypes: Array<String>): Boolean {
  val allowedMimes =
    setOf(
      "image/*",
      "application/pdf",
      "text/plain",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "application/vnd.openxmlformats-officedocument.presentationml.presentation",
      "application/vnd.oasis.opendocument.text",
      "application/vnd.oasis.opendocument.spreadsheet",
      "application/vnd.oasis.opendocument.presentation",
      "text/markdown",
      "application/rtf",
    )
  return mimeTypes.any { mime ->
    allowedMimes.any { allowed ->
      if (allowed.endsWith("/*")) {
        mime.startsWith(allowed.removeSuffix("/*"))
      } else {
        mime == allowed
      }
    }
  }
}

fun classifyDragItemByExtension(fileName: String?): DragItemClassification? {
  val ext =
    fileName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
  val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif")
  val extensionFormats =
    mapOf(
      "pdf" to DocumentFormat.PDF,
      "md" to DocumentFormat.MARKDOWN,
      "markdown" to DocumentFormat.MARKDOWN,
      "txt" to DocumentFormat.TXT,
      "docx" to DocumentFormat.DOCX,
      "xlsx" to DocumentFormat.UNKNOWN,
      "pptx" to DocumentFormat.UNKNOWN,
      "odt" to DocumentFormat.UNKNOWN,
      "ods" to DocumentFormat.UNKNOWN,
      "odp" to DocumentFormat.UNKNOWN,
      "rtf" to DocumentFormat.UNKNOWN,
    )
  return if (ext in imageExtensions) {
    DragItemClassification(ContentType.IMAGE_SCREENSHOT, null)
  } else if (ext in extensionFormats) {
    DragItemClassification(ContentType.DOCUMENT, extensionFormats[ext])
  } else {
    null
  }
}

internal fun extractDragPayload(context: Context, clipData: ClipData?): DragClipboardPayload? {
  if (clipData == null) return null
  val clipDescription = clipData.description

  val allowedMimes =
    setOf(
      "image/*",
      "application/pdf",
      "text/plain",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "application/vnd.openxmlformats-officedocument.presentationml.presentation",
      "application/vnd.oasis.opendocument.text",
      "application/vnd.oasis.opendocument.spreadsheet",
      "application/vnd.oasis.opendocument.presentation",
      "text/markdown",
      "application/rtf",
    )
  val hasAllowedMime = allowedMimes.any { clipDescription.hasMimeType(it) }
  if (!hasAllowedMime) return null

  var skippedCount = 0
  for (i in 0 until clipData.itemCount) {
    val item = clipData.getItemAt(i)
    val uri = item.uri ?: continue
    val fileName = displayNameFor(context, uri)

    val ext = fileName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() }
    val classification = classifyDragItemByExtension(fileName)

    val contentType: ContentType
    val documentFormat: DocumentFormat?

    if (classification != null) {
      contentType = classification.contentType
      documentFormat = classification.documentFormat
    } else if (ext == null && clipDescription.hasMimeType("image/*")) {
      contentType = ContentType.IMAGE_SCREENSHOT
      documentFormat = null
    } else if (ext == null) {
      documentFormat =
        when {
          clipDescription.hasMimeType("application/pdf") -> DocumentFormat.PDF
          clipDescription.hasMimeType("text/markdown") -> DocumentFormat.MARKDOWN
          clipDescription.hasMimeType("text/plain") -> DocumentFormat.TXT
          clipDescription.hasMimeType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          ) -> DocumentFormat.DOCX
          else -> null
        }
      if (documentFormat != null) {
        contentType = ContentType.DOCUMENT
      } else {
        skippedCount++
        continue
      }
    } else {
      skippedCount++
      continue
    }

    val ignoredItemCount = skippedCount + (clipData.itemCount - 1 - i)
    return DragClipboardPayload(
      content = null,
      imageUri = if (contentType == ContentType.IMAGE_SCREENSHOT) uri.toString() else null,
      sourceUri = uri.toString(),
      sourceContentType = contentType,
      sourceDocumentFormat = documentFormat,
      sourceFileName = fileName,
      sourceLabel = "拖拽",
      ignoredItemCount = ignoredItemCount,
    )
  }

  if (clipDescription.hasMimeType("text/plain")) {
    for (i in 0 until clipData.itemCount) {
      val item = clipData.getItemAt(i)
      val text =
        try {
          item.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
          null
        }
      if (text != null) {
        val ignoredCount = clipData.itemCount - 1
        return DragClipboardPayload(
          content = text,
          imageUri = null,
          sourceUri = null,
          sourceContentType = ContentType.DOCUMENT,
          sourceDocumentFormat = DocumentFormat.TXT,
          sourceFileName = null,
          sourceLabel = "拖拽",
          ignoredItemCount = ignoredCount,
        )
      }
    }
  }

  return null
}

private fun readableClipboardSource(context: Context, clip: ClipData): ClipboardSource? {
  return (0 until clip.itemCount).firstNotNullOfOrNull { index ->
    val uri = clip.getItemAt(index).uri ?: return@firstNotNullOfOrNull null
    val fileName = displayNameFor(context, uri)
    val contentType = clipboardSourceContentType(clip, fileName) ?: return@firstNotNullOfOrNull null
    try {
      context.contentResolver.openInputStream(uri)?.use {}
      ClipboardSource(
        uri = uri.toString(),
        contentType = contentType,
        documentFormat =
          if (contentType == ContentType.DOCUMENT) documentFormatFor(clip, fileName) else null,
        fileName = fileName,
      )
    } catch (_: Exception) {
      null
    }
  }
}

private fun displayNameFor(context: Context, uri: android.net.Uri): String? {
  if (uri.scheme == "content") {
    try {
      context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
              val name = cursor.getString(nameIndex)
              if (!name.isNullOrBlank()) return name
            }
          }
        }
    } catch (_: Exception) {
      null
    }
  }
  return uri.lastPathSegment?.takeIf { it.isNotBlank() }
}

private fun clipboardSourceContentType(clip: ClipData, fileName: String?): ContentType? =
  when {
    clip.description.hasMimeType("image/*") ||
      fileName.hasExtension("png", "jpg", "jpeg", "webp", "gif") -> ContentType.IMAGE_SCREENSHOT
    clip.description.hasMimeType("application/*") ||
      clip.description.hasMimeType("text/*") ||
      fileName.hasExtension("pdf", "md", "markdown", "txt", "docx") -> ContentType.DOCUMENT
    else -> null
  }

private fun documentFormatFor(clip: ClipData, fileName: String?): DocumentFormat =
  when {
    fileName.hasExtension("pdf") || clip.description.hasMimeType("application/pdf") ->
      DocumentFormat.PDF
    fileName.hasExtension("md", "markdown") || clip.description.hasMimeType("text/markdown") ->
      DocumentFormat.MARKDOWN
    fileName.hasExtension("txt") || clip.description.hasMimeType("text/plain") -> DocumentFormat.TXT
    fileName.hasExtension("docx") ||
      clip.description.hasMimeType(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      ) -> DocumentFormat.DOCX
    else -> DocumentFormat.UNKNOWN
  }

private fun String?.hasExtension(vararg extensions: String): Boolean {
  val extension =
    this?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() } ?: return false
  return extensions.any { it == extension }
}

private fun pendingMemorialCount(state: ArchiveAssistantState): Int = TOTAL_PENDING_MEMORIALS

private fun fixedPendingMemorialItems(state: ArchiveAssistantState) =
  state.topics
    .mapNotNull { topic -> state.itemsByTopic[topic.id]?.bestPendingMemorialItem() }
    .take(TOTAL_PENDING_MEMORIALS)

private fun List<KnowledgeItem>.bestPendingMemorialItem(): KnowledgeItem? {
  return minWithOrNull(
    compareByDescending<KnowledgeItem> { item -> item.imageResName != null }
      .thenBy { item -> if (item.title.contains(AsciiLetterRegex)) 1 else 0 }
      .thenBy { item -> item.title.length }
      .thenByDescending { item -> item.createdAtEpochMillis }
  )
}

private val AsciiLetterRegex = Regex("[A-Za-z]")

@Composable
private fun ArchiveHomeContent(
  stateStore: ArchiveAssistantStateStore,
  state: ArchiveAssistantState,
  onOpenMemorialBriefing: () -> Unit,
  modifier: Modifier = Modifier,
) {
  HomePane(
    title = "聚合拾遗",
    parserValidationMessage = state.parserValidationMessage,
    recentTopics = if (state.homeSearchQuery.isBlank()) state.topics else state.searchedTopics,
    itemsByTopic = state.itemsByTopic,
    searchQuery = state.homeSearchQuery,
    smartSummarizationMessage = state.smartSummarizationMessage,
    onTopicSelected = stateStore::openTopic,
    onOpenSettings = stateStore::openSettings,
    onCreateTopic = stateStore::openAddItemDialog,
    onRenameTopic = stateStore::openRenameTopicDialog,
    onDeleteTopic = stateStore::openDeleteConfirmDialog,
    onSearchQueryChanged = stateStore::updateHomeSearchQuery,
    onOpenClipboard = stateStore::openLatestClipboardDialog,
    onOpenMemorialDemo = onOpenMemorialBriefing,
    modifier = modifier,
  )
}

@Composable
private fun ArchiveMemorialContent(
  state: ArchiveAssistantState,
  onOpenMemorialDemo: () -> Unit,
  modifier: Modifier = Modifier,
  pendingCount: Int = pendingMemorialCount(state),
  showBackButton: Boolean = false,
  onBack: (() -> Unit)? = null,
) {
  MemorialBriefingPane(
    pendingCount = pendingCount,
    briefingItems = state.items,
    onOpenMemorialDemo = onOpenMemorialDemo,
    modifier = modifier,
    onBack = onBack,
    showBackButton = showBackButton,
  )
}

@Composable
private fun SelectedTopicDetailContent(
  stateStore: ArchiveAssistantStateStore,
  state: ArchiveAssistantState,
  onBack: () -> Unit,
  showBackButton: Boolean,
  fallback: @Composable () -> Unit,
) {
  val topic = state.selectedTopic
  if (topic != null) {
    DetailPane(
      topic = topic,
      items = state.filteredSelectedTopicItems,
      searchQuery = state.homeSearchQuery,
      onBack = onBack,
      onItemClick = stateStore::openCardModal,
      showBackButton = showBackButton,
    )
  } else {
    fallback()
  }
}

@Composable
private fun SinglePaneLayout(
  stateStore: ArchiveAssistantStateStore,
  onAiSettingsChanged: (AiEngineSettings) -> Unit,
  presets: List<AiEnginePreset>,
  onPresetsChanged: (List<AiEnginePreset>) -> Unit,
  onChooseModelFile: () -> Unit,
  onOpenMemorialBriefing: () -> Unit,
  onOpenMemorialDemo: () -> Unit,
) {
  val state = stateStore.state

  when (state.selectedPane) {
    AppPane.TOPICS ->
      ArchiveHomeContent(
        stateStore = stateStore,
        state = state,
        onOpenMemorialBriefing = onOpenMemorialBriefing,
      )

    AppPane.MANAGE,
    AppPane.CLASSIFICATION_REVIEW ->
      ArchiveHomeContent(
        stateStore = stateStore,
        state = state,
        onOpenMemorialBriefing = onOpenMemorialBriefing,
      )

    AppPane.MEMORIAL ->
      ArchiveMemorialContent(
        state = state,
        onOpenMemorialDemo = onOpenMemorialDemo,
        onBack = stateStore::closePanes,
        showBackButton = true,
      )

    AppPane.DETAIL ->
      SelectedTopicDetailContent(
        stateStore = stateStore,
        state = state,
        onBack = stateStore::closePanes,
        showBackButton = true,
      ) {
        ArchiveHomeContent(
          stateStore = stateStore,
          state = state,
          onOpenMemorialBriefing = onOpenMemorialBriefing,
        )
      }

    AppPane.SETTINGS ->
      SettingsPane(
        aiSettings = state.aiSettings,
        onAiSettingsChanged = onAiSettingsChanged,
        onBack = stateStore::closePanes,
        presets = presets,
        onPresetsChanged = onPresetsChanged,
        onDownloadModel = stateStore::downloadModel,
        onChooseModelFile = onChooseModelFile,
        onCancelDownload = stateStore::cancelDownload,
        onStartModel = stateStore::startModel,
        onStopModel = stateStore::stopModel,
        onBackendPreferenceChange = stateStore::updateBackendPreference,
        onRunBenchmark = stateStore::runBenchmark,
        localModelState = state.localModelState,
        benchmarkResult = state.benchmarkResult,
        isBenchmarkRunning = state.isBenchmarkRunning,
      )

    AppPane.ARTICLE_READER ->
      SelectedTopicDetailContent(
        stateStore = stateStore,
        state = state,
        onBack = stateStore::closeCardModal,
        showBackButton = true,
      ) {
        ArchiveHomeContent(
          stateStore = stateStore,
          state = state,
          onOpenMemorialBriefing = onOpenMemorialBriefing,
        )
      }

    AppPane.CARD_DETAIL ->
      SelectedTopicDetailContent(
        stateStore = stateStore,
        state = state,
        onBack = stateStore::closeCardModal,
        showBackButton = true,
      ) {
        ArchiveHomeContent(
          stateStore = stateStore,
          state = state,
          onOpenMemorialBriefing = onOpenMemorialBriefing,
        )
      }
  }
}

@Composable
private fun WideWorkspaceLayout(
  stateStore: ArchiveAssistantStateStore,
  onAiSettingsChanged: (AiEngineSettings) -> Unit,
  presets: List<AiEnginePreset>,
  onPresetsChanged: (List<AiEnginePreset>) -> Unit,
  onChooseModelFile: () -> Unit,
  onOpenMemorialBriefing: () -> Unit,
  onOpenMemorialDemo: () -> Unit,
) {
  val state = stateStore.state
  Box(modifier = Modifier.fillMaxSize()) {
    Row(modifier = Modifier.fillMaxSize()) {
      ArchiveHomeContent(
        stateStore = stateStore,
        state = state,
        onOpenMemorialBriefing = onOpenMemorialBriefing,
        modifier = Modifier.weight(1f),
      )

      Box(modifier = Modifier.weight(1f)) {
        when (state.selectedPane) {
          AppPane.TOPICS ->
            ArchiveMemorialContent(
              state = state,
              onOpenMemorialDemo = onOpenMemorialDemo,
            )

          AppPane.MANAGE,
          AppPane.CLASSIFICATION_REVIEW ->
            ArchiveMemorialContent(
              state = state,
              onOpenMemorialDemo = onOpenMemorialDemo,
            )

          AppPane.MEMORIAL ->
            ArchiveMemorialContent(
              state = state,
              onOpenMemorialDemo = onOpenMemorialDemo,
              onBack = stateStore::closePanes,
              showBackButton = false,
            )

          AppPane.DETAIL,
          AppPane.ARTICLE_READER,
          AppPane.CARD_DETAIL ->
            SelectedTopicDetailContent(
              stateStore = stateStore,
              state = state,
              onBack = stateStore::closePanes,
              showBackButton = false,
            ) {
              ArchiveMemorialContent(
                state = state,
                pendingCount = 0,
                onOpenMemorialDemo = onOpenMemorialDemo,
              )
            }

          AppPane.SETTINGS ->
            SettingsPane(
              aiSettings = state.aiSettings,
              onAiSettingsChanged = onAiSettingsChanged,
              onBack = stateStore::closePanes,
              presets = presets,
              onPresetsChanged = onPresetsChanged,
              onDownloadModel = stateStore::downloadModel,
              onChooseModelFile = onChooseModelFile,
              onCancelDownload = stateStore::cancelDownload,
              onStartModel = stateStore::startModel,
              onStopModel = stateStore::stopModel,
              onBackendPreferenceChange = stateStore::updateBackendPreference,
              onRunBenchmark = stateStore::runBenchmark,
              localModelState = state.localModelState,
              benchmarkResult = state.benchmarkResult,
              isBenchmarkRunning = state.isBenchmarkRunning,
            )
        }
      }
    }

    TopicManagementDialogs(
      topics = state.topics,
      topicNameDialogMode = state.topicNameDialogMode,
      topicNameDialogTopicId = state.topicNameDialogTopicId,
      topicValidationMessage = state.topicValidationMessage,
      deleteConfirmTopicId = state.deleteConfirmTopicId,
      onConfirmCreateTopic = stateStore::confirmCreateTopic,
      onConfirmRenameTopic = stateStore::confirmRenameTopic,
      onConfirmDeleteTopic = stateStore::confirmDeleteTopic,
      onCloseTopicNameDialog = stateStore::closeTopicNameDialog,
      onCloseDeleteConfirmDialog = stateStore::closeDeleteConfirmDialog,
    )
  }
}
