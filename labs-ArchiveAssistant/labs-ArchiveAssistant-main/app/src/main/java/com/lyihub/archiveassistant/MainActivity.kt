package com.lyihub.archiveassistant

import android.content.Context
import android.os.Bundle
import android.view.DragAndDropPermissions
import android.view.DragEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.preferencesDataStore
import com.lyihub.archiveassistant.app.ArchiveAssistantApp
import com.lyihub.archiveassistant.app.extractDragPayload
import com.lyihub.archiveassistant.app.isMimeAllowed
import com.lyihub.archiveassistant.data.AiEnginePresetRepository
import com.lyihub.archiveassistant.data.AiEngineSettingsRepository
import com.lyihub.archiveassistant.data.AppDataRepository
import com.lyihub.archiveassistant.data.OkHttpModelDownloadManager
import com.lyihub.archiveassistant.service.LocalInferenceConnection
import com.lyihub.archiveassistant.state.ArchiveAssistantStateStore
import com.lyihub.archiveassistant.ui.components.ArchiveNoticeBanner
import com.lyihub.archiveassistant.ui.theme.ArchiveAssistantTheme
import com.lyihub.archiveassistant.util.toChineseCount
import kotlinx.coroutines.delay

private val Context.aiEngineSettingsDataStore by preferencesDataStore(name = "ai_engine_settings")
private val Context.appDataStore by preferencesDataStore(name = "app_data")

class MainActivity : ComponentActivity() {
  private lateinit var stateStore: ArchiveAssistantStateStore
  private var dragDropPermissions: DragAndDropPermissions? = null
  private val noticeMessage = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val aiSettingsRepository = AiEngineSettingsRepository(aiEngineSettingsDataStore)
    val inferenceConnection = LocalInferenceConnection(this)
    stateStore =
      ArchiveAssistantStateStore(
        appDataRepository = AppDataRepository(appDataStore),
        aiSettingsRepository = aiSettingsRepository,
        modelDownloadManager = OkHttpModelDownloadManager(this),
        inferenceConnection = inferenceConnection,
        androidContext = this,
      )
    window.decorView.setOnDragListener { _, event ->
      handleDragEvent(event)
    }
    setContent {
      val aiSettingsRepository = remember {
        aiSettingsRepository
      }
      val aiPresetRepository = remember {
        AiEnginePresetRepository(aiEngineSettingsDataStore)
      }
      val appDataRepository = remember {
        AppDataRepository(appDataStore)
      }
      ArchiveAssistantTheme {
        var notice by remember { noticeMessage }
        LaunchedEffect(notice) {
          if (!notice.isNullOrBlank()) {
            delay(2_400L)
            notice = null
          }
        }
        Box(modifier = Modifier.fillMaxSize()) {
          ArchiveAssistantApp(
            stateStore = stateStore,
            aiSettingsRepository = aiSettingsRepository,
            aiPresetRepository = aiPresetRepository,
            appDataRepository = appDataRepository,
          )
          ArchiveNoticeBanner(
            message = notice,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 34.dp),
          )
        }
      }
    }
  }

  private fun handleDragEvent(event: DragEvent): Boolean {
    return when (event.action) {
      DragEvent.ACTION_DRAG_STARTED -> {
        val clipDescription = event.clipDescription ?: return false
        val mimeTypes =
          Array(clipDescription.mimeTypeCount) { index ->
            clipDescription.getMimeType(index)
          }
        isMimeAllowed(mimeTypes)
      }
      DragEvent.ACTION_DROP -> handleDrop(event)
      DragEvent.ACTION_DRAG_ENDED -> true
      else -> true
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    dragDropPermissions?.release()
    dragDropPermissions = null
    stateStore.releaseDragPermission = null
  }

  private fun handleDrop(event: DragEvent): Boolean {
    val state = stateStore.state
    if (state.addItemDialogVisible || state.editingItem != null || state.showClipboardDialog) {
      showNotice("请先关闭当前弹窗后再拖拽")
      return false
    }

    dragDropPermissions = requestDragAndDropPermissions(event)

    val payload = extractDragPayload(this, event.clipData)
    if (payload == null) {
      showNotice("不支持的文件类型")
      dragDropPermissions?.release()
      dragDropPermissions = null
      return false
    }

    stateStore.releaseDragPermission = {
      dragDropPermissions?.release()
      dragDropPermissions = null
      stateStore.releaseDragPermission = null
    }

    stateStore.showClipboard(
      content = payload.content ?: "",
      imageUri = payload.imageUri,
      sourceUri = payload.sourceUri,
      sourceContentType = payload.sourceContentType,
      sourceDocumentFormat = payload.sourceDocumentFormat,
      sourceFileName = payload.sourceFileName,
      sourceLabel = payload.sourceLabel,
    )

    if (payload.ignoredItemCount > 0) {
      showNotice("已处理第一个文件，忽略了${payload.ignoredItemCount.toChineseCount()}个其他文件")
    }

    return true
  }

  private fun showNotice(message: String) {
    noticeMessage.value = message
  }
}
