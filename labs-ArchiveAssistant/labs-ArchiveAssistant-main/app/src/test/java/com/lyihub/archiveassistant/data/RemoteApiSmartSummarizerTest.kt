package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.FetchedDocumentContext
import com.lyihub.archiveassistant.domain.FetchedWebContext
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.SampleKnowledgeData
import com.lyihub.archiveassistant.domain.SixMinistry
import com.lyihub.archiveassistant.domain.SmartSummarizeRequest
import com.lyihub.archiveassistant.domain.SmartSummarizeResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteApiSmartSummarizerTest {
  @Test
  fun summarize_openAiCompatible_sendsConfiguredRequestAndParsesJson() = runBlocking {
    val transport = FakeRemoteTransport(openAiCompatibleBody(summaryJson()))
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(
          engineType = AiEngineType.OPENAI_COMPATIBLE,
          baseUrl = "https://api.example.com/v1",
          modelName = "gpt-4",
          apiKey = "sk-test",
        ),
        transport,
      )

    val result =
      summarizer.summarize(SmartSummarizeRequest("AI Agent paper"), SampleKnowledgeData.topics)

    assertTrue(result is SmartSummarizeResult.Success)
    val success = result as SmartSummarizeResult.Success
    assertEquals(SampleKnowledgeData.DefaultTopicId, success.topicId)
    assertEquals(ContentType.DOCUMENT, success.contentType)
    assertEquals(DocumentFormat.MARKDOWN, success.documentFormat)
    val request = transport.calls.single()
    assertEquals("https://api.example.com/v1/chat/completions", request.endpoint)
    assertEquals("Bearer sk-test", request.headers["Authorization"])
    assertTrue(request.body!!.contains("AI Agent paper"))
  }

  @Test
  fun summarize_promptIncludesTopicOptionsOnlyAndNoExistingItemSnippets() = runBlocking {
    val transport = FakeRemoteTransport(openAiCompatibleBody(summaryJson()))
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(
          engineType = AiEngineType.OPENAI_COMPATIBLE,
          baseUrl = "https://api.example.com/v1",
          modelName = "gpt-4",
          apiKey = "sk-test",
        ),
        transport,
      )
    val existingItems =
      listOf(
        KnowledgeItem(
          id = "item-existing",
          topicId = SampleKnowledgeData.DefaultTopicId,
          contentType = ContentType.DOCUMENT,
          title = "Existing private note",
          summary = "Existing summary should not enter prompt",
          fullText = "Existing full text should not enter prompt",
          sourceUrl = null,
          documentFormat = DocumentFormat.PDF,
          createdAtEpochMillis = 1L,
        )
      )

    val result =
      summarizer.summarize(
        SmartSummarizeRequest("AI Agent paper"),
        SampleKnowledgeData.topics,
        existingItems,
      )

    assertTrue(result is SmartSummarizeResult.Success)
    val body = transport.calls.single().body.orEmpty()
    assertTrue(body.contains("六部主题"))
    assertTrue(body.contains(SampleKnowledgeData.DefaultTopicId))
    assertTrue(body.contains("户 · 府库"))
    assertTrue(!body.contains("相关已归档素材"))
    assertTrue(!body.contains("selectedSnippets"))
    assertTrue(!body.contains("item-existing"))
    assertTrue(!body.contains("Existing private note"))
    assertTrue(!body.contains("Existing summary should not enter prompt"))
    assertTrue(!body.contains("Existing full text should not enter prompt"))
    assertTrue(!body.contains("\"tag\""))
  }

  @Test
  fun summarize_openAiResponses_parsesOutputText() = runBlocking {
    val transport = FakeRemoteTransport("""{"output_text":${jsonString(summaryJson())}}""")
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(
          engineType = AiEngineType.OPENAI_RESPONSES,
          baseUrl = "https://api.openai.com/v1",
        ),
        transport,
      )

    val result = summarizer.summarize(SmartSummarizeRequest("content"), SampleKnowledgeData.topics)

    assertTrue(result is SmartSummarizeResult.Success)
    val request = transport.calls.single()
    assertEquals("https://api.openai.com/v1/responses", request.endpoint)
    assertTrue(request.body!!.contains("\"max_output_tokens\":768"))
    assertTrue(!request.body.contains("\"max_tokens\""))
  }

  @Test
  fun summarize_anthropic_addsVersionHeaderAndParsesContentText() = runBlocking {
    val transport = FakeRemoteTransport("""{"content":[{"text":${jsonString(summaryJson())}}]}""")
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(
          engineType = AiEngineType.ANTHROPIC,
          baseUrl = "https://api.anthropic.com/v1",
          apiKey = "sk-ant",
        ),
        transport,
      )

    val result = summarizer.summarize(SmartSummarizeRequest("content"), SampleKnowledgeData.topics)

    assertTrue(result is SmartSummarizeResult.Success)
    val request = transport.calls.single()
    assertEquals("https://api.anthropic.com/v1/messages", request.endpoint)
    assertEquals("sk-ant", request.headers["x-api-key"])
    assertTrue(request.headers["Authorization"].isNullOrBlank())
    assertEquals("2023-06-01", request.headers["anthropic-version"])
  }

  @Test
  fun summarize_gemini_usesGenerateContentEndpointAndParsesCandidateText() = runBlocking {
    val transport =
      FakeRemoteTransport(
        """{"candidates":[{"content":{"parts":[{"text":${jsonString(summaryJson())}}]}}]}"""
      )
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(
          engineType = AiEngineType.GEMINI,
          baseUrl = "https://generativelanguage.googleapis.com/v1beta",
          modelName = "gemini-pro",
          apiKey = "AIza-test",
        ),
        transport,
      )

    val result = summarizer.summarize(SmartSummarizeRequest("content"), SampleKnowledgeData.topics)

    assertTrue(result is SmartSummarizeResult.Success)
    assertEquals(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=AIza-test",
      transport.calls.single().endpoint,
    )
  }

  @Test
  fun summarize_httpFailure_returnsUserVisibleFailure() = runBlocking {
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(baseUrl = "https://api.example.com/v1"),
        FakeRemoteTransport("{}", code = 401),
      )

    val result = summarizer.summarize(SmartSummarizeRequest("content"), SampleKnowledgeData.topics)

    assertEquals(SmartSummarizeResult.Failure("远程 AI 请求失败：HTTP 401"), result)
  }

  @Test
  fun summarize_withFetchedWebContext_includesContextInRequestBody() = runBlocking {
    val transport = FakeRemoteTransport(openAiCompatibleBody(summaryJson()))
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(
          engineType = AiEngineType.OPENAI_COMPATIBLE,
          baseUrl = "https://api.example.com/v1",
          modelName = "gpt-4",
          apiKey = "sk-test",
        ),
        transport,
      )
    val context =
      FetchedWebContext(
        originalUrl = "https://example.com/article",
        title = "Fetched Article Title",
        description = "Fetched article description",
        bodyText = "Fetched article body with useful content for summarization.",
      )

    val result =
      summarizer.summarize(
        SmartSummarizeRequest(
          rawText = "https://example.com/article",
          sourceUrl = "https://example.com/article",
          sourceTitle = "Article",
          fetchedWebContext = context,
        ),
        SampleKnowledgeData.topics,
      )

    assertTrue(result is SmartSummarizeResult.Success)
    val request = transport.calls.single()
    assertTrue(request.body!!.contains("https://example.com/article"))
    assertTrue(request.body.contains("Fetched Article Title"))
    assertTrue(request.body.contains("Fetched article description"))
    assertTrue(request.body.contains("Fetched article body with useful content for summarization."))
    assertTrue(request.body.contains("禁止只根据 URL 猜测标题或摘要"))
    assertTrue(request.body.contains("sourceUrl 必须等于原始 URL"))
  }

  @Test
  fun summarize_withFetchedDocumentContext_includesContextInRequestBody() = runBlocking {
    val transport = FakeRemoteTransport(openAiCompatibleBody(summaryJson()))
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(
          engineType = AiEngineType.OPENAI_COMPATIBLE,
          baseUrl = "https://api.example.com/v1",
          modelName = "gpt-4",
          apiKey = "sk-test",
        ),
        transport,
      )
    val context =
      FetchedDocumentContext(
        fileName = "note.md",
        format = DocumentFormat.MARKDOWN,
        extractedText = "# Note\nImportant document text.",
        originalCharCount = 128,
        isTruncated = false,
      )

    val result =
      summarizer.summarize(
        SmartSummarizeRequest(
          rawText = "note.md",
          fetchedDocumentContext = context,
        ),
        SampleKnowledgeData.topics,
      )

    assertTrue(result is SmartSummarizeResult.Success)
    val request = transport.calls.single()
    assertTrue(request.body!!.contains("已解析的文档内容"))
    assertTrue(request.body.contains("note.md"))
    assertTrue(request.body.contains("文档格式：MARKDOWN"))
    assertTrue(request.body.contains("Important document text."))
  }

  @Test
  fun summarize_openAiCompatible_extractsJsonFromWrappedModelText() = runBlocking {
    val wrappedOutput =
      """
            下面是归纳结果：
            ${summaryJson()}
            如需继续处理，请告诉我。
        """
        .trimIndent()
    val transport = FakeRemoteTransport(openAiCompatibleBody(wrappedOutput))
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(
          engineType = AiEngineType.OPENAI_COMPATIBLE,
          baseUrl = "https://api.example.com/v1",
          modelName = "gpt-4",
        ),
        transport,
      )

    val result =
      summarizer.summarize(SmartSummarizeRequest("note.docx"), SampleKnowledgeData.topics)

    assertTrue(result is SmartSummarizeResult.Success)
    assertEquals("Agent 论文", (result as SmartSummarizeResult.Success).title)
  }

  @Test
  fun summarize_documentContext_showsDocumentExampleInPrompt() = runBlocking {
    val transport = FakeRemoteTransport(openAiCompatibleBody(summaryJson()))
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(
          engineType = AiEngineType.OPENAI_COMPATIBLE,
          baseUrl = "https://api.example.com/v1",
          modelName = "gpt-4",
          apiKey = "sk-test",
        ),
        transport,
      )
    val context =
      FetchedDocumentContext(
        fileName = "report.pdf",
        format = DocumentFormat.PDF,
        extractedText = "PDF content here.",
        originalCharCount = 256,
        isTruncated = false,
      )

    summarizer.summarize(
      SmartSummarizeRequest(rawText = "report.pdf", fetchedDocumentContext = context),
      SampleKnowledgeData.topics,
    )

    val body = transport.calls.single().body.orEmpty()
    assertTrue(
      "Example should use DOCUMENT not WEB_ARTICLE",
      body.contains("contentType\\\":\\\"DOCUMENT"),
    )
    assertTrue("Example should use PDF format name", body.contains("documentFormat\\\":\\\"PDF"))
    assertTrue(
      "Should keep document-specific instructions",
      body.contains("contentType 为 DOCUMENT"),
    )
  }

  @Test
  fun summarize_parsesLowercaseContentType() = runBlocking {
    val json =
      """{"topicId":"${SampleKnowledgeData.DefaultTopicId}","contentType":"document","title":"Agent 论文","summary":"介绍。","sourceUrl":"","documentFormat":"MARKDOWN"}"""
    val transport = FakeRemoteTransport(openAiCompatibleBody(json))
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(
          engineType = AiEngineType.OPENAI_COMPATIBLE,
          baseUrl = "https://api.example.com/v1",
        ),
        transport,
      )
    val result = summarizer.summarize(SmartSummarizeRequest("test"), SampleKnowledgeData.topics)
    assertTrue(result is SmartSummarizeResult.Success)
    assertEquals(ContentType.DOCUMENT, (result as SmartSummarizeResult.Success).contentType)
  }

  @Test
  fun summarize_parsesLowercaseDocumentFormat() = runBlocking {
    val json =
      """{"topicId":"${SampleKnowledgeData.DefaultTopicId}","contentType":"DOCUMENT","title":"Agent 论文","summary":"介绍。","sourceUrl":"","documentFormat":"markdown"}"""
    val transport = FakeRemoteTransport(openAiCompatibleBody(json))
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(
          engineType = AiEngineType.OPENAI_COMPATIBLE,
          baseUrl = "https://api.example.com/v1",
        ),
        transport,
      )
    val result = summarizer.summarize(SmartSummarizeRequest("test"), SampleKnowledgeData.topics)
    assertTrue(result is SmartSummarizeResult.Success)
    assertEquals(DocumentFormat.MARKDOWN, (result as SmartSummarizeResult.Success).documentFormat)
  }

  @Test
  fun summarize_parsesContentTypeByChineseLabel() = runBlocking {
    val json =
      """{"topicId":"${SampleKnowledgeData.DefaultTopicId}","contentType":"文档","title":"Agent 论文","summary":"介绍。","sourceUrl":"","documentFormat":"MARKDOWN"}"""
    val transport = FakeRemoteTransport(openAiCompatibleBody(json))
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(
          engineType = AiEngineType.OPENAI_COMPATIBLE,
          baseUrl = "https://api.example.com/v1",
        ),
        transport,
      )
    val result = summarizer.summarize(SmartSummarizeRequest("test"), SampleKnowledgeData.topics)
    assertTrue(result is SmartSummarizeResult.Success)
    assertEquals(ContentType.DOCUMENT, (result as SmartSummarizeResult.Success).contentType)
  }

  @Test
  fun summarize_parsesDocumentFormatByLabel() = runBlocking {
    val json =
      """{"topicId":"${SampleKnowledgeData.DefaultTopicId}","contentType":"DOCUMENT","title":"Agent 论文","summary":"介绍。","sourceUrl":"","documentFormat":"PDF"}"""
    val transport = FakeRemoteTransport(openAiCompatibleBody(json))
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(
          engineType = AiEngineType.OPENAI_COMPATIBLE,
          baseUrl = "https://api.example.com/v1",
        ),
        transport,
      )
    val result = summarizer.summarize(SmartSummarizeRequest("test"), SampleKnowledgeData.topics)
    assertTrue(result is SmartSummarizeResult.Success)
    assertEquals(DocumentFormat.PDF, (result as SmartSummarizeResult.Success).documentFormat)
  }

  @Test
  fun summarize_unknownTopicFallsBackToTreasury() = runBlocking {
    val json =
      """{"topicId":"topic-missing","contentType":"DOCUMENT","title":"Agent 论文","summary":"介绍。","sourceUrl":"","documentFormat":"MARKDOWN"}"""
    val transport = FakeRemoteTransport(openAiCompatibleBody(json))
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(
          engineType = AiEngineType.OPENAI_COMPATIBLE,
          baseUrl = "https://api.example.com/v1",
        ),
        transport,
      )

    val result = summarizer.summarize(SmartSummarizeRequest("test"), SampleKnowledgeData.topics)

    assertTrue(result is SmartSummarizeResult.Success)
    assertEquals(SixMinistry.TREASURY.id, (result as SmartSummarizeResult.Success).topicId)
  }

  @Test
  fun summarize_legacyTopicFallsBackToTreasury() = runBlocking {
    val json =
      """{"topicId":"topic-ai-architecture","contentType":"DOCUMENT","title":"Agent 论文","summary":"介绍。","sourceUrl":"","documentFormat":"MARKDOWN"}"""
    val transport = FakeRemoteTransport(openAiCompatibleBody(json))
    val summarizer =
      RemoteApiSmartSummarizer(
        AiEngineSettings(
          engineType = AiEngineType.OPENAI_COMPATIBLE,
          baseUrl = "https://api.example.com/v1",
        ),
        transport,
      )

    val result = summarizer.summarize(SmartSummarizeRequest("test"), SampleKnowledgeData.topics)

    assertTrue(result is SmartSummarizeResult.Success)
    assertEquals(SixMinistry.TREASURY.id, (result as SmartSummarizeResult.Success).topicId)
  }

  private class FakeRemoteTransport(
    private val body: String,
    private val code: Int = 200,
  ) : RemoteAiTransport {
    val calls = mutableListOf<RemoteAiRequest>()

    override suspend fun send(request: RemoteAiRequest): RemoteAiResponse {
      calls.add(request)
      return RemoteAiResponse(code, body)
    }
  }

  private fun openAiCompatibleBody(content: String): String =
    """{"choices":[{"message":{"content":${jsonString(content)}}}]}"""

  private fun summaryJson(): String =
    """{"topicId":"${SampleKnowledgeData.DefaultTopicId}","contentType":"DOCUMENT","title":"Agent 论文","summary":"介绍 Agent 记忆与工具使用。","sourceUrl":"","documentFormat":"MARKDOWN"}"""

  private fun jsonString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
}
