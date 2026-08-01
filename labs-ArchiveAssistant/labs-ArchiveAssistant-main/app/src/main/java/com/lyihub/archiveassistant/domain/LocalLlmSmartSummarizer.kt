package com.lyihub.archiveassistant.domain

import org.json.JSONObject

class LocalLlmSmartSummarizer(private val engine: LocalLlmEngine) : SmartSummarizer {
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

    val output =
      engine
        .generate(promptFor(request, normalizedInput, sixMinistryTopics), maxTokens = 768)
        .getOrElse {
          return SmartSummarizeResult.Failure("本地模型生成失败，请稍后重试")
        }
        .trim()
    if (output.isEmpty()) {
      return SmartSummarizeResult.Failure("本地模型没有返回有效结果")
    }

    return runCatching {
        parseResult(output, topics)
      }
      .getOrElse {
        SmartSummarizeResult.Failure("本地模型返回格式无效，请重试")
      }
  }

  private fun parseResult(output: String, topics: List<Topic>): SmartSummarizeResult.Success {
    val json = JSONObject(extractJsonObject(output))
    val topicId = resolveTopicId(json.requiredString("topicId"))
    val contentType = enumValue<ContentType>(json.requiredString("contentType"))
    val title = json.requiredString("title")
    val summary = json.requiredString("summary")
    val documentFormat = enumValue<DocumentFormat>(json.requiredString("documentFormat"))
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

    return """
        你是一个本地归档助手。请只基于用户原始输入进行智能总结。
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
        {"topicId":"六部ID","contentType":"WEB_ARTICLE","title":"简洁标题","summary":"一句话摘要","sourceUrl":"来源URL或空字符串","documentFormat":"PDF"}
    """
      .trimIndent()
  }

  private fun JSONObject.requiredString(name: String): String {
    require(has(name) && !isNull(name))
    return getString(name).trim().also {
      require(it.isNotEmpty())
    }
  }

  private inline fun <reified T : Enum<T>> enumValue(value: String): T = enumValueOf(value)

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

  private companion object {
    val ALLOWED_CONTENT_TYPES =
      setOf(
        ContentType.WEB_ARTICLE,
        ContentType.IMAGE_SCREENSHOT,
        ContentType.DOCUMENT,
      )
  }
}
