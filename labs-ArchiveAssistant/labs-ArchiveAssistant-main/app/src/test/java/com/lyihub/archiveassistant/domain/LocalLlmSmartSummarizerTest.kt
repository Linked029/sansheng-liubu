package com.lyihub.archiveassistant.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLlmSmartSummarizerTest {
  private val topics = sixMinistryTopics

  private val existingItems =
    listOf(
      KnowledgeItem(
        id = "item-1",
        topicId = SixMinistry.OFFICIALS.id,
        contentType = ContentType.DOCUMENT,
        title = "AI Agent 论文",
        summary = "AI Agent 记忆与工具使用",
        fullText = "agent memory and tool use",
        sourceUrl = null,
        documentFormat = DocumentFormat.PDF,
        createdAtEpochMillis = 20L,
      )
    )

  @Test
  fun summarizeSuccessParsesStrictJson() = runTest {
    val summarizer =
      summarizerReturning(
        """{"topicId":"officials","contentType":"DOCUMENT","title":"Agent 论文","summary":"介绍 Agent 记忆与工具使用。","sourceUrl":"","documentFormat":"PDF"}"""
      )

    val result = summarizer.summarize(SmartSummarizeRequest("AI Agent 论文内容"), topics, existingItems)

    assertTrue(result is SmartSummarizeResult.Success)
    result as SmartSummarizeResult.Success
    assertEquals(SixMinistry.OFFICIALS.id, result.topicId)
    assertEquals(ContentType.DOCUMENT, result.contentType)
    assertEquals("Agent 论文", result.title)
    assertEquals("介绍 Agent 记忆与工具使用。", result.summary)
    assertEquals(DocumentFormat.PDF, result.documentFormat)
    assertNull(result.sourceUrl)
  }

  @Test
  fun summarizeSuccessParsesJsonFence() = runTest {
    val summarizer =
      summarizerReturning(
        """
        ```json
        {"topicId":"officials","contentType":"DOCUMENT","title":"Agent 论文","summary":"介绍 Agent 记忆与工具使用。","sourceUrl":"","documentFormat":"PDF"}
        ```
        """
          .trimIndent()
      )

    val result = summarizer.summarize(SmartSummarizeRequest("AI Agent 论文内容"), topics, existingItems)

    assertTrue(result is SmartSummarizeResult.Success)
    assertEquals("Agent 论文", (result as SmartSummarizeResult.Success).title)
  }

  @Test
  fun summarizeSuccessParsesTurnTokenWrappedJson() = runTest {
    val summarizer =
      summarizerReturning(
        """
        <|turn>model
        {"topicId":"officials","contentType":"DOCUMENT","title":"Agent 论文","summary":"介绍 Agent 记忆与工具使用。","sourceUrl":"","documentFormat":"PDF"}<turn|>
        <|turn>model
        """
          .trimIndent()
      )

    val result = summarizer.summarize(SmartSummarizeRequest("AI Agent 论文内容"), topics, existingItems)

    assertTrue(result is SmartSummarizeResult.Success)
    assertEquals("Agent 论文", (result as SmartSummarizeResult.Success).title)
  }

  @Test
  fun promptIncludesTopicOptionsOnly() = runTest {
    val engine =
      initializedEngine(
        """{"topicId":"officials","contentType":"DOCUMENT","title":"Agent 论文","summary":"介绍 Agent。","sourceUrl":"https://example.com/a","documentFormat":"PDF"}"""
      )
    val summarizer = LocalLlmSmartSummarizer(engine)

    summarizer.summarize(
      SmartSummarizeRequest(
        rawText = "AI Agent 论文内容",
        sourceUrl = "https://example.com/a",
        sourceTitle = "Agent Paper",
      ),
      topics,
      existingItems,
    )

    val prompt = engine.lastPrompt.orEmpty()
    SixMinistry.entries.forEach { ministry -> assertTrue(prompt.contains(ministry.id)) }
    assertTrue(prompt.contains("禁止创建新主题"))
    assertTrue(!prompt.contains("topic-ai"))
    assertTrue(!prompt.contains("topic-reading"))
    assertTrue(!prompt.contains("item-1"))
    assertTrue(!prompt.contains("AI Agent 记忆与工具使用"))
    assertTrue(prompt.contains("https://example.com/a"))
    assertEquals(768, engine.lastMaxTokens)
  }

  @Test
  fun promptIncludesFetchedWebContextWhenAvailable() = runTest {
    val engine =
      initializedEngine(
        """{"topicId":"officials","contentType":"WEB_ARTICLE","title":"From Fetched Title","summary":"Based on fetched body.","sourceUrl":"https://example.com/article","documentFormat":"UNKNOWN"}"""
      )
    val summarizer = LocalLlmSmartSummarizer(engine)
    val context =
      FetchedWebContext(
        originalUrl = "https://example.com/article",
        title = "Original Article Title",
        description = "A description of the article.",
        bodyText =
          "This is the full fetched body text of the article. It contains useful information for summarization.",
      )

    summarizer.summarize(
      SmartSummarizeRequest(
        rawText = "https://example.com/article",
        sourceUrl = "https://example.com/article",
        sourceTitle = "Some title",
        fetchedWebContext = context,
      ),
      topics,
      existingItems,
    )

    val prompt = engine.lastPrompt.orEmpty()
    assertTrue(prompt.contains("https://example.com/article"))
    assertTrue(prompt.contains("Original Article Title"))
    assertTrue(prompt.contains("A description of the article."))
    assertTrue(prompt.contains("This is the full fetched body text of the article."))
    assertTrue(prompt.contains("禁止只根据 URL 猜测标题或摘要"))
    assertTrue(prompt.contains("sourceUrl 必须等于原始 URL"))
  }

  @Test
  fun promptIncludesFetchedDocumentContextWhenAvailable() = runTest {
    val engine =
      initializedEngine(
        """{"topicId":"officials","contentType":"DOCUMENT","title":"Doc Title","summary":"Based on document text.","sourceUrl":"","documentFormat":"DOCX"}"""
      )
    val summarizer = LocalLlmSmartSummarizer(engine)
    val context =
      FetchedDocumentContext(
        fileName = "research.docx",
        format = DocumentFormat.DOCX,
        extractedText = "Document body excerpt for safe summarization.",
        originalCharCount = 80_000,
        isTruncated = true,
      )

    summarizer.summarize(
      SmartSummarizeRequest(
        rawText = "research.docx",
        sourceUrl = "content://doc/research",
        sourceTitle = "research.docx",
        fetchedDocumentContext = context,
      ),
      topics,
      existingItems,
    )

    val prompt = engine.lastPrompt.orEmpty()
    assertTrue(prompt.contains("已解析的文档内容"))
    assertTrue(prompt.contains("research.docx"))
    assertTrue(prompt.contains("文档格式：DOCX"))
    assertTrue(prompt.contains("Document body excerpt for safe summarization."))
    assertTrue(prompt.contains("禁止猜测未提供内容"))
  }

  @Test
  fun malformedJsonReturnsFailure() = runTest {
    val result = summarizerReturning("not json").summarize(SmartSummarizeRequest("content"), topics)

    assertFailure(result)
  }

  @Test
  fun unknownTopicFallsBackToTreasury() = runTest {
    val result =
      summarizerReturning(
          """{"topicId":"topic-missing","contentType":"DOCUMENT","title":"Title","summary":"Summary","sourceUrl":"","documentFormat":"PDF"}"""
        )
        .summarize(SmartSummarizeRequest("content"), topics)

    assertTrue(result is SmartSummarizeResult.Success)
    assertEquals(SixMinistry.TREASURY.id, (result as SmartSummarizeResult.Success).topicId)
  }

  @Test
  fun legacyTopicFallsBackToTreasury() = runTest {
    val result =
      summarizerReturning(
          """{"topicId":"topic-ai","contentType":"DOCUMENT","title":"Title","summary":"Summary","sourceUrl":"","documentFormat":"PDF"}"""
        )
        .summarize(SmartSummarizeRequest("content"), topics)

    assertTrue(result is SmartSummarizeResult.Success)
    assertEquals(SixMinistry.TREASURY.id, (result as SmartSummarizeResult.Success).topicId)
  }

  @Test
  fun engineFailureReturnsFailure() = runTest {
    val engine =
      initializedEngine("unused").apply {
        generateFailure = IllegalStateException("generation failed")
      }

    val result = LocalLlmSmartSummarizer(engine).summarize(SmartSummarizeRequest("content"), topics)

    assertFailure(result)
  }

  @Test
  fun invalidEnumReturnsFailure() = runTest {
    val result =
      summarizerReturning(
          """{"topicId":"officials","contentType":"ARTICLE","title":"Title","summary":"Summary","sourceUrl":"","documentFormat":"PDF"}"""
        )
        .summarize(SmartSummarizeRequest("content"), topics)

    assertFailure(result)
  }

  @Test
  fun allContentTypeReturnsFailure() = runTest {
    val result =
      summarizerReturning(
          """{"topicId":"officials","contentType":"ALL","title":"Title","summary":"Summary","sourceUrl":"","documentFormat":"PDF"}"""
        )
        .summarize(SmartSummarizeRequest("content"), topics)

    assertFailure(result)
  }

  @Test
  fun missingDocumentFormatReturnsFailure() = runTest {
    val result =
      summarizerReturning(
          """{"topicId":"officials","contentType":"DOCUMENT","title":"Title","summary":"Summary","sourceUrl":""}"""
        )
        .summarize(SmartSummarizeRequest("content"), topics)

    assertFailure(result)
  }

  @Test
  fun invalidDocumentFormatReturnsFailure() = runTest {
    val result =
      summarizerReturning(
          """{"topicId":"officials","contentType":"DOCUMENT","title":"Title","summary":"Summary","sourceUrl":"","documentFormat":"HTML"}"""
        )
        .summarize(SmartSummarizeRequest("content"), topics)

    assertFailure(result)
  }

  @Test
  fun missingRequiredFieldReturnsFailure() = runTest {
    val result =
      summarizerReturning(
          """{"topicId":"officials","contentType":"DOCUMENT","title":"Title","sourceUrl":"","documentFormat":"PDF"}"""
        )
        .summarize(SmartSummarizeRequest("content"), topics)

    assertFailure(result)
  }

  @Test
  fun blankRequiredFieldReturnsFailure() = runTest {
    val result =
      summarizerReturning(
          """{"topicId":"officials","contentType":"DOCUMENT","title":" ","summary":"Summary","sourceUrl":"","documentFormat":"PDF"}"""
        )
        .summarize(SmartSummarizeRequest("content"), topics)

    assertFailure(result)
  }

  @Test
  fun unknownDocumentFormatSucceedsWhenExplicitlyReturned() = runTest {
    val result =
      summarizerReturning(
          """{"topicId":"officials","contentType":"WEB_ARTICLE","title":"Title","summary":"Summary","sourceUrl":"","documentFormat":"UNKNOWN"}"""
        )
        .summarize(SmartSummarizeRequest("content"), topics)

    assertTrue(result is SmartSummarizeResult.Success)
    assertEquals(DocumentFormat.UNKNOWN, (result as SmartSummarizeResult.Success).documentFormat)
  }

  @Test
  fun blankInputReturnsFailureWithoutCallingEngine() = runTest {
    val engine = initializedEngine("unused")

    val result = LocalLlmSmartSummarizer(engine).summarize(SmartSummarizeRequest("   "), topics)

    assertFailure(result)
    assertEquals(0, engine.generateCallCount)
  }

  private suspend fun summarizerReturning(output: String): LocalLlmSmartSummarizer =
    LocalLlmSmartSummarizer(initializedEngine(output))

  private suspend fun initializedEngine(output: String): FakeLocalLlmEngine =
    FakeLocalLlmEngine(returnText = output).also { engine ->
      engine.initialize("/tmp/model.litertlm", InferenceBackend.CPU).getOrThrow()
    }

  private fun assertFailure(result: SmartSummarizeResult) {
    assertTrue(result is SmartSummarizeResult.Failure)
    assertTrue((result as SmartSummarizeResult.Failure).message.isNotBlank())
    assertNull(result as? SmartSummarizeResult.Success)
  }
}
