package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class AiEndpointLatencyResult {
  data class Success(val elapsedMillis: Long) : AiEndpointLatencyResult()

  data class Failure(val message: String) : AiEndpointLatencyResult()
}

suspend fun testAiEndpointLatency(
  settings: AiEngineSettings,
  apiKey: String = "",
  transport: AiLatencyTransport = RealAiLatencyTransport(),
): AiEndpointLatencyResult =
  withContext(Dispatchers.IO) {
    val request = buildLatencyRequest(settings, apiKey)
    val endpoint = request.endpoint

    if (endpoint.isBlank()) {
      return@withContext AiEndpointLatencyResult.Failure("Endpoint is empty")
    }

    val start = System.nanoTime()
    val result = runCatching { transport.send(request) }
    val elapsedMillis = (System.nanoTime() - start) / 1_000_000

    result.fold(
      onSuccess = { code ->
        if (code in 200..299) {
          AiEndpointLatencyResult.Success(elapsedMillis)
        } else {
          AiEndpointLatencyResult.Failure("HTTP $code")
        }
      },
      onFailure = { error ->
        AiEndpointLatencyResult.Failure(mapLatencyError(error))
      },
    )
  }

interface AiLatencyTransport {
  suspend fun send(request: AiLatencyRequest): Int
}

class RealAiLatencyTransport : AiLatencyTransport {
  override suspend fun send(request: AiLatencyRequest): Int =
    withContext(Dispatchers.IO) {
      var connection: HttpURLConnection? = null
      try {
        connection = URL(request.endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = request.method
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.doInput = true
        connection.doOutput = request.body != null
        connection.setRequestProperty("Content-Type", "application/json")
        request.headers.forEach { (key, value) ->
          connection.setRequestProperty(key, value)
        }

        request.body?.let { body ->
          connection.outputStream.use { output ->
            output.write(body.toByteArray(Charsets.UTF_8))
          }
        }

        connection.responseCode
      } finally {
        connection?.disconnect()
      }
    }
}

data class AiLatencyRequest(
  val endpoint: String,
  val method: String,
  val headers: Map<String, String>,
  val body: String?,
)

internal fun buildLatencyRequest(settings: AiEngineSettings, apiKey: String): AiLatencyRequest =
  when (settings.engineType) {
    AiEngineType.OPENAI_COMPATIBLE -> openAiCompatibleRequest(settings, apiKey)
    AiEngineType.OPENAI_RESPONSES -> openAiResponsesRequest(settings, apiKey)
    AiEngineType.ANTHROPIC -> anthropicRequest(settings, apiKey)
    AiEngineType.GEMINI -> geminiRequest(settings, apiKey)
    AiEngineType.LOCAL_MODEL -> localModelRequest(settings)
  }

private fun openAiCompatibleRequest(settings: AiEngineSettings, apiKey: String): AiLatencyRequest {
  val endpoint = apiEndpoint(settings.baseUrl, "chat/completions")
  val body =
    jsonObject(
      "model" to jsonString(settings.modelName),
      "messages" to
        jsonArray(
          jsonObject(
            "role" to jsonString("user"),
            "content" to jsonString("hi"),
          )
        ),
      "max_tokens" to "1",
    )
  return AiLatencyRequest(
    endpoint = endpoint,
    method = "POST",
    headers = authHeaders(apiKey),
    body = body,
  )
}

private fun openAiResponsesRequest(settings: AiEngineSettings, apiKey: String): AiLatencyRequest {
  val endpoint = apiEndpoint(settings.baseUrl, "responses")
  val body =
    jsonObject(
      "model" to jsonString(settings.modelName),
      "input" to jsonString("hi"),
      "max_tokens" to "1",
    )
  return AiLatencyRequest(
    endpoint = endpoint,
    method = "POST",
    headers = authHeaders(apiKey),
    body = body,
  )
}

private fun anthropicRequest(settings: AiEngineSettings, apiKey: String): AiLatencyRequest {
  val endpoint = apiEndpoint(settings.baseUrl, "messages")
  val body =
    jsonObject(
      "model" to jsonString(settings.modelName),
      "max_tokens" to "1",
      "messages" to
        jsonArray(
          jsonObject(
            "role" to jsonString("user"),
            "content" to jsonString("hi"),
          )
        ),
    )
  val headers = authHeaders(apiKey).plus("anthropic-version" to "2023-06-01")
  return AiLatencyRequest(
    endpoint = endpoint,
    method = "POST",
    headers = headers,
    body = body,
  )
}

private fun geminiRequest(settings: AiEngineSettings, apiKey: String): AiLatencyRequest {
  val modelPath =
    if (settings.modelName.startsWith("models/")) {
      settings.modelName
    } else {
      "models/${settings.modelName}"
    }
  val endpoint =
    apiEndpoint(settings.baseUrl, "$modelPath:generateContent") + "?key=${apiKey.trim()}"
  val body =
    jsonObject(
      "contents" to
        jsonArray(jsonObject("parts" to jsonArray(jsonObject("text" to jsonString("hi"))))),
      "generationConfig" to jsonObject("maxOutputTokens" to "1"),
    )
  return AiLatencyRequest(
    endpoint = endpoint,
    method = "POST",
    headers = emptyMap(),
    body = body,
  )
}

@Suppress("DEPRECATION")
private fun localModelRequest(settings: AiEngineSettings): AiLatencyRequest {
  // Note: in-process LiteRT-LM mode does not use this HTTP latency test path
  val endpoint = apiEndpoint(settings.localEndpoint, "v1/chat/completions")
  val body =
    jsonObject(
      "model" to jsonString(settings.modelName),
      "messages" to
        jsonArray(
          jsonObject(
            "role" to jsonString("user"),
            "content" to jsonString("hi"),
          )
        ),
      "max_tokens" to "1",
    )
  return AiLatencyRequest(
    endpoint = endpoint,
    method = "POST",
    headers = emptyMap(),
    body = body,
  )
}

private fun apiEndpoint(baseUrl: String, path: String): String {
  val base = baseUrl.trim().trimEnd('/')
  return if (base.isBlank()) "" else "$base/$path"
}

private fun authHeaders(apiKey: String): Map<String, String> =
  if (apiKey.isBlank()) {
    emptyMap()
  } else {
    mapOf("Authorization" to "Bearer $apiKey")
  }

private fun jsonString(value: String): String =
  "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

private fun jsonObject(vararg pairs: Pair<String, String>): String =
  pairs.joinToString(",", "{", "}") { "${jsonString(it.first)}:${it.second}" }

private fun jsonArray(vararg items: String): String = items.joinToString(",", "[", "]")

private fun mapLatencyError(error: Throwable): String =
  when (error) {
    is MalformedURLException -> "Invalid endpoint URL"
    is IOException -> error.message ?: "Network request failed"
    else -> error.message ?: "Request failed"
  }
