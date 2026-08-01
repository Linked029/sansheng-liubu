package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.SmartSummarizeRequest
import com.lyihub.archiveassistant.domain.SmartSummarizeResult
import com.lyihub.archiveassistant.domain.SmartSummarizer
import com.lyihub.archiveassistant.domain.Topic
import com.lyihub.archiveassistant.domain.resolveTopicId
import com.lyihub.archiveassistant.domain.sixMinistryTopics
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject

class RemoteApiSmartSummarizer(
  private val settings: AiEngineSettings,
  private val transport: RemoteAiTransport = RealRemoteAiTransport(),
) : SmartSummarizer {
  override suspend fun summarize(
    request: SmartSummarizeRequest,
    topics: List<Topic>,
    existingItems: List<KnowledgeItem>,
  ): SmartSummarizeResult {
    val normalizedInput = request.rawText.trim()
    if (normalizedInput.isEmpty()) {
      return SmartSummarizeResult.Failure("请输入要智能总结的内容")
    }
    if (topics.isEmpty()) {
      return SmartSummarizeResult.Failure("没有可用主题，无法智能总结")
    }

    val prompt = promptFor(request, normalizedInput, sixMinistryTopics)
    val apiRequest = buildRemoteRequest(settings, prompt)
    if (apiRequest.endpoint.isBlank()) {
      return SmartSummarizeResult.Failure("远程 AI Endpoint 为空，请检查配置")
    }

    val response =
      runCatching { transport.send(apiRequest) }
        .getOrElse { error ->
          return SmartSummarizeResult.Failure(mapRemoteError(error))
        }
    if (response.code !in 200..299) {
      return SmartSummarizeResult.Failure("远程 AI 请求失败：HTTP ${response.code}")
    }

    return runCatching {
        parseResult(extractModelText(settings.engineType, response.body), topics)
      }
      .getOrElse {
        SmartSummarizeResult.Failure("远程 AI 返回格式无效，请重试")
      }
  }

  private fun parseResult(output: String, topics: List<Topic>): SmartSummarizeResult.Success {
    val json = JSONObject(extractJsonObject(output))
    val topicId = resolveTopicId(json.requiredString("topicId"))
    val contentType = contentTypeValue(json.requiredString("contentType"))
    val title = json.requiredString("title")
    val summary = json.requiredString("summary")
    val documentFormat = documentFormatValue(json.requiredString("documentFormat"))
    val sourceUrl = json.optString("sourceUrl").trim()

    require(contentType in ALLOWED_CONTENT_TYPES)

    return SmartSummarizeResult.Success.fromAiJson(
      topicId = topicId,
      contentType = contentType,
      title = title,
      summary = summary,
      documentFormat = documentFormat,
      sourceUrl = sourceUrl,
    )
  }

  private fun promptFor(
    request: SmartSummarizeRequest,
    normalizedInput: String,
    topics: List<Topic>,
  ): String {
    val fetchedBlock =
      request.fetchedWebContext
        ?.let { ctx ->
          """
已获取的网页内容：
原始 URL：${ctx.originalUrl}
网页标题：${ctx.title}
网页描述：${ctx.description}
网页正文：${ctx.bodyText}

注意：禁止只根据 URL 猜测标题或摘要。
当 contentType 为 WEB_ARTICLE 时，title 必须使用上述网页标题（来自已获取的网页元数据/内容），不要发明摘要式的标题。
summary 必须基于上述网页正文/描述生成。
sourceUrl 必须等于原始 URL。

"""
            .trimIndent()
        }
        .orEmpty()
    val documentBlock =
      request.fetchedDocumentContext
        ?.let { ctx ->
          """
已解析的文档内容：
文件名：${ctx.fileName}
文档格式：${ctx.format.name}
原始字符数：${ctx.originalCharCount}
是否已截断：${ctx.isTruncated}
文档正文节选：${ctx.extractedText}

注意：当 contentType 为 DOCUMENT 时，title 和 summary 必须基于上述文档正文节选生成。
documentFormat 必须等于上述文档格式。
如果“是否已截断”为 true，只能基于可见节选总结，禁止猜测未提供内容。

"""
            .trimIndent()
        }
        .orEmpty()

    val exampleContentType =
      if (request.fetchedDocumentContext != null) "DOCUMENT" else "WEB_ARTICLE"
    val exampleDocumentFormat = request.fetchedDocumentContext?.format?.name ?: "PDF"

    return """
        你是一个归档助手。请只基于用户原始输入进行智能总结。
        你必须根据主题名称从六部主题中选择最接近的一个 topicId，topicId 必须是下列六部 ID 之一，禁止创建新主题或返回主题名称。

        用户原始输入：$normalizedInput
        来源 URL（如有）：${request.sourceUrl.orEmpty()}
        来源标题（如有）：${request.sourceTitle.orEmpty()}

        ${fetchedBlock}${documentBlock}六部主题：
        ${topics.joinToString("\n") { "- id=${it.id}; title=${it.title}" }}

        请推断 contentType、documentFormat 和 sourceUrl（能确定时填写；不能确定时 sourceUrl 返回空字符串）。
        contentType 只能是：${ALLOWED_CONTENT_TYPES.joinToString(", ") { it.name }}。
        documentFormat 只能是：${DocumentFormat.values().joinToString(", ") { it.name }}；只有模型明确判断未知文档格式时才返回 UNKNOWN。
        title、summary 必须简洁，title 不超过 28 个中文字符，summary 不超过 96 个中文字符。

        只返回严格 JSON 对象，不要 Markdown，不要解释，不要额外字段：
        {"topicId":"六部ID","contentType":"${exampleContentType}","title":"简洁标题","summary":"一句话摘要","sourceUrl":"来源URL或空字符串","documentFormat":"${exampleDocumentFormat}"}
    """
      .trimIndent()
  }

  private fun JSONObject.requiredString(name: String): String {
    require(has(name) && !isNull(name))
    return getString(name).trim().also { require(it.isNotEmpty()) }
  }

  private fun contentTypeValue(value: String): ContentType =
    ContentType.entries.firstOrNull { type ->
      type.name == value || type.name.equals(value, ignoreCase = true) || type.label == value
    } ?: throw IllegalArgumentException("No content type: $value")

  private fun documentFormatValue(value: String): DocumentFormat =
    DocumentFormat.entries.firstOrNull { format ->
      format.name == value || format.name.equals(value, ignoreCase = true) || format.label == value
    } ?: throw IllegalArgumentException("No document format: $value")

  private companion object {
    val ALLOWED_CONTENT_TYPES =
      setOf(
        ContentType.WEB_ARTICLE,
        ContentType.IMAGE_SCREENSHOT,
        ContentType.DOCUMENT,
      )
  }
}

interface RemoteAiTransport {
  suspend fun send(request: RemoteAiRequest): RemoteAiResponse
}

class RealRemoteAiTransport : RemoteAiTransport {
  override suspend fun send(request: RemoteAiRequest): RemoteAiResponse =
    withContext(Dispatchers.IO) {
      yield()
      var connection: HttpURLConnection? = null
      try {
        connection = URL(request.endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = request.method
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.doInput = true
        connection.doOutput = request.body != null
        connection.setRequestProperty("Content-Type", "application/json")
        request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        request.body?.let { body ->
          connection.outputStream.use { output -> output.write(body.toByteArray(Charsets.UTF_8)) }
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        RemoteAiResponse(
          code,
          stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(),
        )
      } finally {
        connection?.disconnect()
      }
    }
}

data class RemoteAiRequest(
  val endpoint: String,
  val method: String,
  val headers: Map<String, String>,
  val body: String?,
)

data class RemoteAiResponse(
  val code: Int,
  val body: String,
)

internal fun buildRemoteRequest(settings: AiEngineSettings, prompt: String): RemoteAiRequest =
  when (settings.engineType) {
    AiEngineType.OPENAI_COMPATIBLE -> openAiCompatibleSummaryRequest(settings, prompt)
    AiEngineType.OPENAI_RESPONSES -> openAiResponsesSummaryRequest(settings, prompt)
    AiEngineType.ANTHROPIC -> anthropicSummaryRequest(settings, prompt)
    AiEngineType.GEMINI -> geminiSummaryRequest(settings, prompt)
    AiEngineType.LOCAL_MODEL ->
      error("Local model summarization does not use RemoteApiSmartSummarizer")
  }

private fun openAiCompatibleSummaryRequest(
  settings: AiEngineSettings,
  prompt: String,
): RemoteAiRequest {
  val body =
    JSONObject()
      .put("model", settings.modelName)
      .put(
        "messages",
        JSONArray().put(JSONObject().put("role", "user").put("content", prompt)),
      )
      .put("max_tokens", 768)
      .toString()
  return RemoteAiRequest(
    apiEndpoint(settings.baseUrl, "chat/completions"),
    "POST",
    authHeaders(settings.apiKey),
    body,
  )
}

private fun openAiResponsesSummaryRequest(
  settings: AiEngineSettings,
  prompt: String,
): RemoteAiRequest {
  val body =
    JSONObject()
      .put("model", settings.modelName)
      .put("input", prompt)
      .put("max_output_tokens", 768)
      .toString()
  return RemoteAiRequest(
    apiEndpoint(settings.baseUrl, "responses"),
    "POST",
    authHeaders(settings.apiKey),
    body,
  )
}

private fun anthropicSummaryRequest(settings: AiEngineSettings, prompt: String): RemoteAiRequest {
  val body =
    JSONObject()
      .put("model", settings.modelName)
      .put("max_tokens", 768)
      .put(
        "messages",
        JSONArray().put(JSONObject().put("role", "user").put("content", prompt)),
      )
      .toString()
  val headers = anthropicHeaders(settings.apiKey)
  return RemoteAiRequest(apiEndpoint(settings.baseUrl, "messages"), "POST", headers, body)
}

private fun geminiSummaryRequest(settings: AiEngineSettings, prompt: String): RemoteAiRequest {
  val modelPath =
    if (settings.modelName.startsWith("models/")) settings.modelName
    else "models/${settings.modelName}"
  val body =
    JSONObject()
      .put(
        "contents",
        JSONArray()
          .put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))),
      )
      .put("generationConfig", JSONObject().put("maxOutputTokens", 768))
      .toString()
  return RemoteAiRequest(
    apiEndpoint(settings.baseUrl, "$modelPath:generateContent") + "?key=${settings.apiKey.trim()}",
    "POST",
    emptyMap(),
    body,
  )
}

private fun extractModelText(engineType: AiEngineType, responseBody: String): String {
  val json = JSONObject(responseBody)
  return when (engineType) {
    AiEngineType.OPENAI_COMPATIBLE ->
      json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    AiEngineType.OPENAI_RESPONSES ->
      json.optString("output_text").ifBlank {
        json
          .getJSONArray("output")
          .getJSONObject(0)
          .getJSONArray("content")
          .getJSONObject(0)
          .getString("text")
      }
    AiEngineType.ANTHROPIC -> json.getJSONArray("content").getJSONObject(0).getString("text")
    AiEngineType.GEMINI ->
      json
        .getJSONArray("candidates")
        .getJSONObject(0)
        .getJSONObject("content")
        .getJSONArray("parts")
        .getJSONObject(0)
        .getString("text")
    AiEngineType.LOCAL_MODEL -> error("Local model responses are not remote API responses")
  }
}

private fun extractJsonObject(output: String): String {
  val trimmed = output.trim()
  val unfenced =
    if (trimmed.startsWith("```")) {
      trimmed
        .removePrefix("```")
        .removePrefix("json")
        .removePrefix("JSON")
        .trim()
        .removeSuffix("```")
        .trim()
    } else {
      trimmed
    }

  val start = unfenced.indexOf('{')
  require(start >= 0)

  var depth = 0
  var inString = false
  var isEscaped = false
  for (index in start until unfenced.length) {
    val char = unfenced[index]
    if (isEscaped) {
      isEscaped = false
      continue
    }
    when {
      char == '\\' && inString -> isEscaped = true
      char == '"' -> inString = !inString
      !inString && char == '{' -> depth += 1
      !inString && char == '}' -> {
        depth -= 1
        if (depth == 0) return unfenced.substring(start, index + 1)
      }
    }
  }

  error("No complete JSON object found")
}

private fun apiEndpoint(baseUrl: String, path: String): String {
  val base = baseUrl.trim().trimEnd('/')
  return if (base.isBlank()) "" else "$base/$path"
}

private fun authHeaders(apiKey: String): Map<String, String> =
  if (apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${apiKey.trim()}")

private fun anthropicHeaders(apiKey: String): Map<String, String> =
  if (apiKey.isBlank()) {
    mapOf("anthropic-version" to "2023-06-01")
  } else {
    mapOf("x-api-key" to apiKey.trim(), "anthropic-version" to "2023-06-01")
  }

private fun mapRemoteError(error: Throwable): String =
  when (error) {
    is MalformedURLException -> "远程 AI Endpoint 无效，请检查配置"
    is IOException -> error.message ?: "远程 AI 网络请求失败"
    else -> error.message ?: "远程 AI 请求失败"
  }
