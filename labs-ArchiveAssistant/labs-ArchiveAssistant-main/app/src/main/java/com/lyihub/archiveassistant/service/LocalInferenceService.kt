package com.lyihub.archiveassistant.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lyihub.archiveassistant.R
import com.lyihub.archiveassistant.data.LiteRtLmEngineAdapter
import com.lyihub.archiveassistant.domain.InferenceBackend
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.LocalLlmEngine
import com.lyihub.archiveassistant.domain.LocalLlmSmartSummarizer
import com.lyihub.archiveassistant.domain.LocalModelInfo
import com.lyihub.archiveassistant.domain.LocalModelState
import com.lyihub.archiveassistant.domain.LocalModelStatus
import com.lyihub.archiveassistant.domain.SmartSummarizeRequest
import com.lyihub.archiveassistant.domain.SmartSummarizeResult
import com.lyihub.archiveassistant.domain.Topic
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalInferenceService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val binder by lazy {
    LocalInferenceBinder(
      controller =
        LocalInferenceServiceController(
          scope = serviceScope,
          engineFactory = { LiteRtLmEngineAdapter(applicationContext) },
          modelPathProvider = { model -> File(filesDir, "models/${model.fileName}").absolutePath },
          foregroundController = AndroidForegroundController(),
        )
    )
  }

  override fun onBind(intent: Intent?): IBinder = binder

  override fun onDestroy() {
    binder.stopModel()
    super.onDestroy()
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    binder.stopModel()
    super.onTaskRemoved(rootIntent)
  }

  inner class LocalInferenceBinder
  internal constructor(private val controller: LocalInferenceServiceController) : Binder() {
    fun getEngine(): LocalLlmEngine? = controller.getEngine()

    suspend fun summarize(
      request: SmartSummarizeRequest,
      topics: List<Topic>,
      existingItems: List<KnowledgeItem>,
    ): SmartSummarizeResult = controller.summarize(request, topics, existingItems)

    fun startModel(model: LocalModelInfo, backend: InferenceBackend) {
      controller.startModel(model, backend)
    }

    fun stopModel() {
      controller.stopModel()
    }

    val serviceState: SharedFlow<LocalModelState> = controller.serviceState
  }

  inner class AndroidForegroundController : InferenceForegroundController {
    override fun startLoading() {
      createNotificationChannel()
      warnIfNotificationPermissionMissing()
      startForeground(
        NOTIFICATION_ID,
        buildNotification("正在加载本地模型…"),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
      )
    }

    override fun showReady() {
      notificationManager.notify(NOTIFICATION_ID, buildNotification("本地模型就绪"))
    }

    override fun stop() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        stopForeground(STOP_FOREGROUND_REMOVE)
      } else {
        @Suppress("DEPRECATION") stopForeground(true)
      }
    }

    private val notificationManager: NotificationManager
      get() = getSystemService(NotificationManager::class.java)

    private fun createNotificationChannel() {
      val channel =
        NotificationChannel(
          NOTIFICATION_CHANNEL_ID,
          "LocalInferenceChannel",
          NotificationManager.IMPORTANCE_LOW,
        )
      notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification =
      NotificationCompat.Builder(
          this@LocalInferenceService,
          NOTIFICATION_CHANNEL_ID,
        )
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(contentText)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun warnIfNotificationPermissionMissing() {
      if (!NotificationManagerCompat.from(this@LocalInferenceService).areNotificationsEnabled()) {
        Log.w(TAG, "通知权限未授予，前台服务通知可能不显示")
        return
      }
      if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
          ContextCompat.checkSelfPermission(
            this@LocalInferenceService,
            Manifest.permission.POST_NOTIFICATIONS,
          ) != PackageManager.PERMISSION_GRANTED
      ) {
        Log.w(TAG, "通知权限未授予，前台服务通知可能不显示")
      }
    }
  }

  companion object {
    private const val TAG = "LocalInferenceService"
    private const val NOTIFICATION_ID = 7_007
    private const val NOTIFICATION_CHANNEL_ID = "LocalInferenceChannel"
  }
}

internal class LocalInferenceServiceController(
  private val scope: CoroutineScope,
  private val engineFactory: () -> LocalLlmEngine,
  private val modelPathProvider: (LocalModelInfo) -> String,
  private val foregroundController: InferenceForegroundController,
) {
  private val stateFlow = MutableSharedFlow<LocalModelState>(replay = STATE_REPLAY_COUNT)
  private val engineMutex = Mutex()

  @Volatile private var engine: LocalLlmEngine? = null

  val serviceState: SharedFlow<LocalModelState> = stateFlow.asSharedFlow()

  init {
    stateFlow.tryEmit(LocalModelState(status = LocalModelStatus.DOWNLOADED))
  }

  fun getEngine(): LocalLlmEngine? = engine

  fun startModel(model: LocalModelInfo, backend: InferenceBackend) {
    scope.launch {
      engineMutex.withLock {
        releaseCurrentEngineLocked()
        val modelPath = modelPathProvider(model)
        val initializingState =
          LocalModelState(
            status = LocalModelStatus.INITIALIZING,
            activeBackend = backend,
            modelPath = modelPath,
          )
        stateFlow.emit(initializingState)
        foregroundController.startLoading()

        val nextEngine = engineFactory()
        val result = nextEngine.initialize(modelPath, backend)
        result
          .onSuccess { activeBackend ->
            engine = nextEngine
            stateFlow.emit(
              LocalModelState(
                status = LocalModelStatus.READY,
                activeBackend = activeBackend,
                modelPath = modelPath,
              )
            )
            foregroundController.showReady()
          }
          .onFailure { error ->
            nextEngine.release()
            engine = null
            stateFlow.emit(
              LocalModelState(
                status = LocalModelStatus.ERROR,
                activeBackend = InferenceBackend.UNKNOWN,
                errorMessage = error.message,
                modelPath = modelPath,
              )
            )
            foregroundController.stop()
          }
      }
    }
  }

  fun stopModel() {
    scope.launch {
      engineMutex.withLock {
        stateFlow.emit(LocalModelState(status = LocalModelStatus.STOPPING))
        releaseCurrentEngineLocked()
        foregroundController.stop()
        stateFlow.emit(LocalModelState(status = LocalModelStatus.DOWNLOADED))
      }
    }
  }

  suspend fun summarize(
    request: SmartSummarizeRequest,
    topics: List<Topic>,
    existingItems: List<KnowledgeItem>,
  ): SmartSummarizeResult = engineMutex.withLock {
    val currentEngine = engine ?: return@withLock SmartSummarizeResult.Failure("本地 AI 不可用，请先开启模型")
    LocalLlmSmartSummarizer(currentEngine).summarize(request, topics, existingItems)
  }

  private suspend fun releaseCurrentEngineLocked() {
    val currentEngine = engine
    engine = null
    currentEngine?.release()
  }

  private companion object {
    const val STATE_REPLAY_COUNT = 8
  }
}

internal interface InferenceForegroundController {
  fun startLoading()

  fun showReady()

  fun stop()
}
