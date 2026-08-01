package com.lyihub.archiveassistant.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.lyihub.archiveassistant.domain.InferenceBackend
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.LocalLlmEngine
import com.lyihub.archiveassistant.domain.LocalModelInfo
import com.lyihub.archiveassistant.domain.LocalModelState
import com.lyihub.archiveassistant.domain.LocalModelStatus
import com.lyihub.archiveassistant.domain.SmartSummarizeRequest
import com.lyihub.archiveassistant.domain.SmartSummarizeResult
import com.lyihub.archiveassistant.domain.Topic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

interface LocalInferenceGateway {
  val serviceState: Flow<LocalModelState>

  fun bind()

  fun unbind()

  fun getEngine(): LocalLlmEngine?

  suspend fun summarize(
    request: SmartSummarizeRequest,
    topics: List<Topic>,
    existingItems: List<KnowledgeItem>,
  ): SmartSummarizeResult

  fun startModel(model: LocalModelInfo, backend: InferenceBackend)

  fun stopModel()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalInferenceConnection(private val context: Context) : LocalInferenceGateway {
  private val applicationContext = context.applicationContext
  private val binderFlow = MutableStateFlow<LocalInferenceService.LocalInferenceBinder?>(null)
  private var isBound = false
  private var pendingStartRequest: StartModelRequest? = null

  override val serviceState: Flow<LocalModelState> = binderFlow.flatMapLatest { binder ->
    binder?.serviceState ?: flowOf(LocalModelState(status = LocalModelStatus.DOWNLOADED))
  }

  private val connection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val binder = service as? LocalInferenceService.LocalInferenceBinder
        binderFlow.value = binder
        val request = pendingStartRequest
        if (binder != null && request != null) {
          pendingStartRequest = null
          binder.startModel(request.model, request.backend)
        }
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        Log.w(TAG, "Local inference service disconnected: $name")
        binderFlow.value = null
        isBound = false
      }
    }

  override fun bind() {
    if (isBound) return
    val intent = Intent(applicationContext, LocalInferenceService::class.java)
    isBound = applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
  }

  override fun unbind() {
    if (!isBound) return
    applicationContext.unbindService(connection)
    isBound = false
    binderFlow.value = null
    pendingStartRequest = null
  }

  override fun getEngine(): LocalLlmEngine? = binderFlow.value?.getEngine()

  override suspend fun summarize(
    request: SmartSummarizeRequest,
    topics: List<Topic>,
    existingItems: List<KnowledgeItem>,
  ): SmartSummarizeResult {
    val binder = binderFlow.value ?: return SmartSummarizeResult.Failure("本地 AI 不可用，请先开启模型")
    return binder.summarize(request, topics, existingItems)
  }

  override fun startModel(model: LocalModelInfo, backend: InferenceBackend) {
    val binder = binderFlow.value
    if (binder != null) {
      binder.startModel(model, backend)
      return
    }
    pendingStartRequest = StartModelRequest(model, backend)
    bind()
  }

  private data class StartModelRequest(
    val model: LocalModelInfo,
    val backend: InferenceBackend,
  )

  override fun stopModel() {
    binderFlow.value?.stopModel()
  }

  private companion object {
    const val TAG = "LocalInferenceConnection"
  }
}
