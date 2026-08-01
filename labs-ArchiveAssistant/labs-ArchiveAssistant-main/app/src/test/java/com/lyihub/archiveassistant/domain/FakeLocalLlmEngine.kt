package com.lyihub.archiveassistant.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLocalLlmEngine(
  var delayMillis: Long = 0L,
  var returnText: String = "fake local response",
  var actualBackend: InferenceBackend = InferenceBackend.CPU,
  var benchmarkResult: BenchResult =
    BenchResult(
      promptTokens = 128,
      generateTokens = 128,
      timeToFirstTokenMs = 240L,
      prefillTokensPerSecond = 1000f,
      decodeTokensPerSecond = 50f,
      backend = actualBackend,
    ),
) : LocalLlmEngine {
  private val stateFlow = MutableStateFlow(LocalModelState())
  private var initialized = false

  var backendFailures: Set<InferenceBackend> = emptySet()
  var generateFailure: Throwable? = null
  var benchmarkFailure: Throwable? = null
  var lastPrompt: String? = null
    private set

  var lastMaxTokens: Int? = null
    private set

  var generateCallCount: Int = 0
    private set

  override val state: Flow<LocalModelState> = stateFlow

  override suspend fun initialize(
    modelPath: String,
    backend: InferenceBackend,
  ): Result<InferenceBackend> {
    delayIfNeeded()
    stateFlow.value =
      LocalModelState(
        status = LocalModelStatus.INITIALIZING,
        activeBackend = InferenceBackend.UNKNOWN,
        modelPath = modelPath,
      )

    val attempts = backendAttempts(backend)
    for (attempt in attempts) {
      if (attempt !in backendFailures) {
        initialized = true
        actualBackend = attempt
        stateFlow.value =
          LocalModelState(
            status = LocalModelStatus.READY,
            activeBackend = attempt,
            modelPath = modelPath,
          )
        return Result.success(attempt)
      }
    }

    val error = IllegalStateException("All fake backends failed")
    stateFlow.value =
      LocalModelState(
        status = LocalModelStatus.ERROR,
        errorMessage = error.message,
        modelPath = modelPath,
      )
    return Result.failure(error)
  }

  override suspend fun generate(prompt: String, maxTokens: Int): Result<String> {
    delayIfNeeded()
    lastPrompt = prompt
    lastMaxTokens = maxTokens
    generateCallCount += 1
    if (!initialized) {
      return Result.failure(IllegalStateException("Local LLM engine is not initialized"))
    }
    generateFailure?.let {
      return Result.failure(it)
    }

    stateFlow.value = stateFlow.value.copy(status = LocalModelStatus.INFERENCING)
    stateFlow.value = stateFlow.value.copy(status = LocalModelStatus.READY)
    return Result.success(returnText)
  }

  override suspend fun benchmark(promptTokens: Int, generateTokens: Int): Result<BenchResult> {
    delayIfNeeded()
    if (!initialized) {
      return Result.failure(IllegalStateException("Local LLM engine is not initialized"))
    }
    benchmarkFailure?.let {
      return Result.failure(it)
    }
    return Result.success(
      benchmarkResult.copy(
        promptTokens = promptTokens,
        generateTokens = generateTokens,
        backend = actualBackend,
      )
    )
  }

  override suspend fun release(): Result<Unit> {
    delayIfNeeded()
    initialized = false
    stateFlow.value = LocalModelState(status = LocalModelStatus.NOT_DOWNLOADED)
    return Result.success(Unit)
  }

  private suspend fun delayIfNeeded() {
    if (delayMillis > 0L) {
      delay(delayMillis)
    }
  }

  private fun backendAttempts(preferredBackend: InferenceBackend): List<InferenceBackend> {
    val fallbackOrder = listOf(InferenceBackend.NPU, InferenceBackend.GPU, InferenceBackend.CPU)
    return if (preferredBackend in fallbackOrder) {
      listOf(preferredBackend) + fallbackOrder.filterNot { it == preferredBackend }
    } else {
      fallbackOrder
    }
  }
}
