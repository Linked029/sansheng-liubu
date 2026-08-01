package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.BenchResult
import com.lyihub.archiveassistant.domain.FakeLocalLlmEngine
import com.lyihub.archiveassistant.domain.InferenceBackend
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmEngineAdapterTest {

  @Test
  fun fallbackToGpu() = runTest {
    val engine =
      FakeLocalLlmEngine().apply {
        backendFailures = setOf(InferenceBackend.NPU)
      }

    val result = engine.initialize("/tmp/model.litertlm", InferenceBackend.NPU)

    assertTrue(result.isSuccess)
    assertEquals(InferenceBackend.GPU, result.getOrThrow())
  }

  @Test
  fun allBackendsFail() = runTest {
    val engine =
      FakeLocalLlmEngine().apply {
        backendFailures = setOf(InferenceBackend.NPU, InferenceBackend.GPU, InferenceBackend.CPU)
      }

    val result = engine.initialize("/tmp/model.litertlm", InferenceBackend.NPU)

    assertTrue(result.isFailure)
  }

  @Test
  fun initializeTimeout() = runTest {
    val engine = FakeLocalLlmEngine(delayMillis = 60_001L)

    val result = runCatching {
      withTimeout(60_000L) {
        engine.initialize("/tmp/model.litertlm", InferenceBackend.NPU).getOrThrow()
      }
    }

    assertTrue(result.isFailure)
  }

  @Test
  fun benchmarkReturnsTps() = runTest {
    val engine =
      FakeLocalLlmEngine(
        benchmarkResult =
          BenchResult(
            promptTokens = 128,
            generateTokens = 128,
            timeToFirstTokenMs = 250L,
            prefillTokensPerSecond = 1200f,
            decodeTokensPerSecond = 60f,
            backend = InferenceBackend.GPU,
          )
      )
    engine.initialize("/tmp/model.litertlm", InferenceBackend.GPU).getOrThrow()

    val result = engine.benchmark()

    assertTrue(result.isSuccess)
    val bench = result.getOrThrow()
    assertTrue(bench.prefillTokensPerSecond > 0f)
    assertTrue(bench.decodeTokensPerSecond > 0f)
  }

  @Test
  fun benchmarkNotInitialized() = runTest {
    val engine = FakeLocalLlmEngine()

    val result = engine.benchmark()

    assertTrue(result.isFailure)
  }
}
