package com.lyihub.archiveassistant.service

import com.lyihub.archiveassistant.domain.FakeLocalLlmEngine
import com.lyihub.archiveassistant.domain.InferenceBackend
import com.lyihub.archiveassistant.domain.LocalLlmEngine
import com.lyihub.archiveassistant.domain.LocalModelInfo
import com.lyihub.archiveassistant.domain.LocalModelState
import com.lyihub.archiveassistant.domain.LocalModelStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalInferenceServiceLogicTest {
  @Test
  fun startModelSuccess() = runTest {
    val fakeEngine = FakeLocalLlmEngine(actualBackend = InferenceBackend.GPU)
    val controller = controller(this, fakeEngine)

    controller.startModel(testModel, InferenceBackend.GPU)
    advanceUntilIdle()

    assertEquals(
      listOf(LocalModelStatus.INITIALIZING, LocalModelStatus.READY),
      controller.statusHistory().takeLast(2),
    )
    assertNotNull(controller.getEngine())
  }

  @Test
  fun startModelFailure() = runTest {
    val fakeEngine =
      FakeLocalLlmEngine().apply {
        backendFailures = setOf(InferenceBackend.NPU, InferenceBackend.GPU, InferenceBackend.CPU)
      }
    val controller = controller(this, fakeEngine)

    controller.startModel(testModel, InferenceBackend.NPU)
    advanceUntilIdle()

    assertEquals(
      listOf(LocalModelStatus.INITIALIZING, LocalModelStatus.ERROR),
      controller.statusHistory().takeLast(2),
    )
    assertNull(controller.getEngine())
    assertEquals(
      "All fake backends failed",
      controller.serviceState.replayCache.last().errorMessage,
    )
  }

  @Test
  fun stopModelReleases() = runTest {
    val fakeEngine = TrackingLocalLlmEngine(FakeLocalLlmEngine())
    val controller = controller(this, fakeEngine)
    controller.startModel(testModel, InferenceBackend.CPU)
    advanceUntilIdle()

    controller.stopModel()
    advanceUntilIdle()

    assertEquals(
      listOf(LocalModelStatus.STOPPING, LocalModelStatus.DOWNLOADED),
      controller.statusHistory().takeLast(2),
    )
    assertTrue(fakeEngine.releaseCalled)
    assertNull(controller.getEngine())
  }

  @Test
  fun initializeTimeout() = runTest {
    val fakeEngine = FakeLocalLlmEngine(delayMillis = 61_000L)
    val controller = controller(this, fakeEngine)

    controller.startModel(testModel, InferenceBackend.CPU)
    val readyWithinTimeout =
      withTimeoutOrNull(60_000L) {
        while (controller.serviceState.replayCache.last().status != LocalModelStatus.READY) {
          delay(1_000L)
        }
        true
      }

    assertNull(readyWithinTimeout)
    assertEquals(LocalModelStatus.INITIALIZING, controller.serviceState.replayCache.last().status)
    advanceTimeBy(1_000L)
    advanceUntilIdle()
    assertEquals(LocalModelStatus.READY, controller.serviceState.replayCache.last().status)
  }

  @Test
  fun generateAfterRelease() = runTest {
    val fakeEngine = FakeLocalLlmEngine(returnText = "generated")
    val controller = controller(this, fakeEngine)
    controller.startModel(testModel, InferenceBackend.CPU)
    advanceUntilIdle()
    val releasedEngine = controller.getEngine()

    controller.stopModel()
    advanceUntilIdle()

    assertNull(controller.getEngine())
    assertTrue(releasedEngine?.generate("prompt", 32)?.isFailure ?: false)
  }

  @Test
  fun benchmarkViaBinder() = runTest {
    val fakeEngine = FakeLocalLlmEngine()
    val controller = controller(this, fakeEngine)
    controller.startModel(testModel, InferenceBackend.CPU)
    advanceUntilIdle()
    val binder = LocalInferenceService().LocalInferenceBinder(controller)

    val benchmark = binder.getEngine()?.benchmark(32, 16)?.getOrNull()

    assertNotNull(benchmark)
    assertEquals(32, benchmark?.promptTokens)
    assertEquals(16, benchmark?.generateTokens)
  }

  private fun controller(
    scope: CoroutineScope,
    engine: LocalLlmEngine,
  ) =
    LocalInferenceServiceController(
      scope = scope,
      engineFactory = { engine },
      modelPathProvider = { model -> "/tmp/${model.fileName}" },
      foregroundController = NoOpForegroundController,
    )

  private fun LocalInferenceServiceController.statusHistory(): List<LocalModelStatus> =
    serviceState.replayCache.map { it.status }

  private class TrackingLocalLlmEngine(private val delegate: FakeLocalLlmEngine) : LocalLlmEngine {
    var releaseCalled = false

    override val state: Flow<LocalModelState> = delegate.state

    override suspend fun initialize(
      modelPath: String,
      backend: InferenceBackend,
    ): Result<InferenceBackend> = delegate.initialize(modelPath, backend)

    override suspend fun generate(prompt: String, maxTokens: Int): Result<String> =
      delegate.generate(prompt, maxTokens)

    override suspend fun benchmark(
      promptTokens: Int,
      generateTokens: Int,
    ): Result<com.lyihub.archiveassistant.domain.BenchResult> =
      delegate.benchmark(promptTokens, generateTokens)

    override suspend fun release(): Result<Unit> {
      releaseCalled = true
      return delegate.release()
    }
  }

  private object NoOpForegroundController : InferenceForegroundController {
    override fun startLoading() = Unit

    override fun showReady() = Unit

    override fun stop() = Unit
  }

  private companion object {
    val testModel =
      LocalModelInfo(
        id = "test-model",
        displayName = "Test Model",
        fileName = "test-model.litertlm",
        downloadUrl = "https://example.com/test-model.litertlm",
        expectedSha256 = "sha256",
        sizeBytes = 1024L,
      )
  }
}
