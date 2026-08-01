package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEndpointLatencyTesterTest {

  @Test
  fun testLatency_blankBaseUrl_returnsFailure() = runBlocking {
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.OPENAI_COMPATIBLE,
        baseUrl = "",
      )
    val transport = FakeTransport()

    val result = testAiEndpointLatency(settings, apiKey = "sk-test", transport = transport)

    assertEquals(
      AiEndpointLatencyResult.Failure("Endpoint is empty"),
      result,
    )
    assertEquals(0, transport.calls.size)
  }

  @Test
  fun testLatency_openAiCompatible_usesCorrectEndpointAndAuth() = runBlocking {
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.OPENAI_COMPATIBLE,
        baseUrl = "https://api.example.com/v1",
        modelName = "gpt-4",
      )
    val transport = FakeTransport(responseCode = 200)

    val result = testAiEndpointLatency(settings, apiKey = "sk-test", transport = transport)

    assertTrue(result is AiEndpointLatencyResult.Success)
    val request = transport.calls.single()
    assertEquals("https://api.example.com/v1/chat/completions", request.endpoint)
    assertEquals("POST", request.method)
    assertEquals("Bearer sk-test", request.headers["Authorization"])
    assertTrue(request.body!!.contains("\"model\":\"gpt-4\""))
    assertTrue(request.body.contains("\"max_tokens\":1"))
  }

  @Test
  fun testLatency_openAiResponses_usesResponsesEndpoint() = runBlocking {
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.OPENAI_RESPONSES,
        baseUrl = "https://api.openai.com/v1",
        modelName = "gpt-4o",
      )
    val transport = FakeTransport(responseCode = 200)

    val result = testAiEndpointLatency(settings, apiKey = "sk-test", transport = transport)

    assertTrue(result is AiEndpointLatencyResult.Success)
    val request = transport.calls.single()
    assertEquals("https://api.openai.com/v1/responses", request.endpoint)
    assertTrue(request.body!!.contains("\"input\":\"hi\""))
  }

  @Test
  fun testLatency_anthropic_usesMessagesEndpointAndVersionHeader() = runBlocking {
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.ANTHROPIC,
        baseUrl = "https://api.anthropic.com/v1",
        modelName = "claude-3",
      )
    val transport = FakeTransport(responseCode = 200)

    val result = testAiEndpointLatency(settings, apiKey = "sk-ant", transport = transport)

    assertTrue(result is AiEndpointLatencyResult.Success)
    val request = transport.calls.single()
    assertEquals("https://api.anthropic.com/v1/messages", request.endpoint)
    assertEquals("Bearer sk-ant", request.headers["Authorization"])
    assertEquals("2023-06-01", request.headers["anthropic-version"])
  }

  @Test
  fun testLatency_gemini_usesModelEndpointWithKeyQueryParam() = runBlocking {
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.GEMINI,
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        modelName = "gemini-pro",
      )
    val transport = FakeTransport(responseCode = 200)

    val result = testAiEndpointLatency(settings, apiKey = "AIza-test", transport = transport)

    assertTrue(result is AiEndpointLatencyResult.Success)
    val request = transport.calls.single()
    assertEquals(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=AIza-test",
      request.endpoint,
    )
    assertTrue(request.headers["Authorization"].isNullOrBlank())
  }

  @Test
  fun testLatency_gemini_modelAlreadyPrefixed_doesNotDoublePrefix() = runBlocking {
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.GEMINI,
        baseUrl = "https://generativelanguage.googleapis.com/v1",
        modelName = "models/gemini-pro",
      )
    val transport = FakeTransport(responseCode = 200)

    testAiEndpointLatency(settings, apiKey = "key", transport = transport)

    val request = transport.calls.single()
    assertEquals(
      "https://generativelanguage.googleapis.com/v1/models/gemini-pro:generateContent?key=key",
      request.endpoint,
    )
  }

  @Test
  fun testLatency_localModel_usesLocalEndpointAndOpenAiFormat() = runBlocking {
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.LOCAL_MODEL,
        baseUrl = "https://api.example.com/v1",
        localEndpoint = "http://127.0.0.1:11434",
        modelName = "qwen3-2b",
      )
    val transport = FakeTransport(responseCode = 200)

    val result = testAiEndpointLatency(settings, apiKey = "", transport = transport)

    assertTrue(result is AiEndpointLatencyResult.Success)
    val request = transport.calls.single()
    assertEquals("http://127.0.0.1:11434/v1/chat/completions", request.endpoint)
    assertTrue(request.body!!.contains("\"model\":\"qwen3-2b\""))
  }

  @Test
  fun testLatency_successfulResponse_returnsSuccessWithElapsedTime() = runBlocking {
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.OPENAI_COMPATIBLE,
        baseUrl = "https://api.example.com/v1",
        modelName = "gpt-4",
      )
    val transport = FakeTransport(responseCode = 200)

    val result = testAiEndpointLatency(settings, apiKey = "sk", transport = transport)

    assertTrue(result is AiEndpointLatencyResult.Success)
    assertTrue((result as AiEndpointLatencyResult.Success).elapsedMillis >= 0)
  }

  @Test
  fun testLatency_non2xxResponse_returnsFailure() = runBlocking {
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.OPENAI_COMPATIBLE,
        baseUrl = "https://api.example.com/v1",
        modelName = "gpt-4",
      )
    val transport = FakeTransport(responseCode = 401)

    val result = testAiEndpointLatency(settings, apiKey = "sk", transport = transport)

    assertEquals(AiEndpointLatencyResult.Failure("HTTP 401"), result)
  }

  @Test
  fun testLatency_transportThrowsIOException_returnsFailure() = runBlocking {
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.OPENAI_COMPATIBLE,
        baseUrl = "https://api.example.com/v1",
        modelName = "gpt-4",
      )
    val transport = FakeTransport(error = IOException("connection refused"))

    val result = testAiEndpointLatency(settings, apiKey = "sk", transport = transport)

    assertEquals(
      AiEndpointLatencyResult.Failure("connection refused"),
      result,
    )
  }

  @Test
  fun testLatency_noApiKey_doesNotAddAuthorizationHeader() = runBlocking {
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.OPENAI_COMPATIBLE,
        baseUrl = "https://api.example.com/v1",
        modelName = "gpt-4",
      )
    val transport = FakeTransport(responseCode = 200)

    testAiEndpointLatency(settings, apiKey = "", transport = transport)

    val request = transport.calls.single()
    assertTrue(request.headers["Authorization"].isNullOrBlank())
  }

  private class FakeTransport(
    private val responseCode: Int? = null,
    private val error: Throwable? = null,
  ) : AiLatencyTransport {
    val calls = mutableListOf<AiLatencyRequest>()

    override suspend fun send(request: AiLatencyRequest): Int {
      calls.add(request)
      error?.let { throw it }
      return responseCode ?: 200
    }
  }
}
