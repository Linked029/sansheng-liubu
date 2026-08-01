package com.lyihub.archiveassistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSummarizerContractTest {

  // -----------------------------------------------------------------------
  // Success → ClassificationPayload mapping
  // -----------------------------------------------------------------------

  @Test
  fun `success maps to ClassificationPayload with rawText as rawInput`() {
    val result =
      SmartSummarizeResult.Success(
        topicId = "topic-1",
        contentType = ContentType.WEB_ARTICLE,
        title = "Scalable AI Systems",
        summary = "A concise summary of scalable AI architectures.",
        documentFormat = DocumentFormat.UNKNOWN,
        sourceUrl = "https://example.com/ai",
      )

    val payload = result.toClassificationPayload("original user input")

    assertEquals("topic-1", payload.topicId)
    assertEquals(ContentType.WEB_ARTICLE, payload.contentType)
    assertEquals("Scalable AI Systems", payload.title)
    assertEquals("A concise summary of scalable AI architectures.", payload.summary)
    assertEquals("original user input", payload.rawInput)
    assertEquals(DocumentFormat.UNKNOWN, payload.documentFormat)
  }

  @Test
  fun `success with documentFormat maps to ClassificationPayload`() {
    val result =
      SmartSummarizeResult.Success(
        topicId = "topic-1",
        contentType = ContentType.DOCUMENT,
        title = "Research Paper",
        summary = "Abstract of the paper.",
        documentFormat = DocumentFormat.PDF,
      )

    val payload = result.toClassificationPayload("paste from clipboard")

    assertEquals(ContentType.DOCUMENT, payload.contentType)
    assertEquals(DocumentFormat.PDF, payload.documentFormat)
    assertEquals("paste from clipboard", payload.rawInput)
  }

  @Test
  fun `success rawInput must not equal summary when different from rawText`() {
    val result =
      SmartSummarizeResult.Success(
        topicId = "topic-1",
        contentType = ContentType.WEB_ARTICLE,
        title = "Article",
        summary = "AI-generated summary of the article.",
        documentFormat = DocumentFormat.UNKNOWN,
      )

    val payload = result.toClassificationPayload("user typed this long text")

    // rawInput must come from request rawText, not from model summary
    assertEquals("user typed this long text", payload.rawInput)
    // If summary is different, they must differ (confirming separation)
    assertTrue(payload.rawInput != payload.summary)
  }

  // -----------------------------------------------------------------------
  // Failure carries message without payload
  // -----------------------------------------------------------------------

  @Test
  fun `failure carries message and cannot be cast to Success`() {
    val result = SmartSummarizeResult.Failure("无法总结：输入为空")

    assertEquals("无法总结：输入为空", result.message)
    val asSuccess = result as? SmartSummarizeResult.Success
    assertNull(asSuccess)
  }

  @Test
  fun `failure has no partial payload accessor`() {
    val result = SmartSummarizeResult.Failure("LLM returned invalid JSON")

    val asSuccess = result as? SmartSummarizeResult.Success
    assertNull(asSuccess?.toClassificationPayload("ignored"))
  }

  // -----------------------------------------------------------------------
  // sourceUrl handling
  // -----------------------------------------------------------------------

  @Test
  fun `sourceUrl is nullable`() {
    val result =
      SmartSummarizeResult.Success(
        topicId = "topic-1",
        contentType = ContentType.WEB_ARTICLE,
        title = "Article",
        summary = "Summary.",
        documentFormat = DocumentFormat.UNKNOWN,
      )

    assertNull(result.sourceUrl)
  }

  @Test
  fun `fromAiJson normalizes blank sourceUrl to null`() {
    val result =
      SmartSummarizeResult.Success.fromAiJson(
        topicId = "topic-1",
        contentType = ContentType.WEB_ARTICLE,
        title = "Article",
        summary = "Summary.",
        documentFormat = DocumentFormat.UNKNOWN,
        sourceUrl = "",
      )

    assertNull(result.sourceUrl)
  }

  @Test
  fun `fromAiJson preserves valid sourceUrl`() {
    val result =
      SmartSummarizeResult.Success.fromAiJson(
        topicId = "topic-1",
        contentType = ContentType.WEB_ARTICLE,
        title = "Article",
        summary = "Summary.",
        documentFormat = DocumentFormat.MARKDOWN,
        sourceUrl = "https://example.com/page",
      )

    assertEquals("https://example.com/page", result.sourceUrl)
  }

  // -----------------------------------------------------------------------
  // SmartSummarizeRequest preserves original input
  // -----------------------------------------------------------------------

  @Test
  fun `request preserves rawText and optional metadata`() {
    val request =
      SmartSummarizeRequest(
        rawText = "original clipboard content",
        sourceUrl = "https://example.com",
        sourceTitle = "Example Page",
      )

    assertEquals("original clipboard content", request.rawText)
    assertEquals("https://example.com", request.sourceUrl)
    assertEquals("Example Page", request.sourceTitle)
  }

  @Test
  fun `request defaults sourceUrl and sourceTitle to null`() {
    val request = SmartSummarizeRequest(rawText = "just text")

    assertNull(request.sourceUrl)
    assertNull(request.sourceTitle)
  }

  // -----------------------------------------------------------------------
  // FetchedWebContext — domain-owned web fetch context
  // -----------------------------------------------------------------------

  @Test
  fun `fetchedWebContext defaults to null when not provided`() {
    val request = SmartSummarizeRequest(rawText = "just text")

    assertNull(request.fetchedWebContext)
  }

  @Test
  fun `fetchedWebContext carries original URL, title, description, and body`() {
    val context =
      FetchedWebContext(
        originalUrl = "https://example.com/article",
        title = "Example Article Title",
        description = "A short meta description.",
        bodyText = "This is the visible body text of the article.",
      )
    val request =
      SmartSummarizeRequest(
        rawText = "https://example.com/article",
        fetchedWebContext = context,
      )

    assertNotNull(request.fetchedWebContext)
    assertEquals("https://example.com/article", request.fetchedWebContext!!.originalUrl)
    assertEquals("Example Article Title", request.fetchedWebContext!!.title)
    assertEquals("A short meta description.", request.fetchedWebContext!!.description)
    assertEquals(
      "This is the visible body text of the article.",
      request.fetchedWebContext!!.bodyText,
    )
  }

  // -----------------------------------------------------------------------
  // Raw-input invariant: fetched body must never replace rawText
  // -----------------------------------------------------------------------

  @Test
  fun `toClassificationPayload uses rawText not fetched body`() {
    val context =
      FetchedWebContext(
        originalUrl = "https://example.com/article",
        title = "Article Title",
        description = "Meta desc.",
        bodyText = "Fetched body content that should NOT leak into rawInput.",
      )
    val request =
      SmartSummarizeRequest(
        rawText = "user typed this input",
        fetchedWebContext = context,
      )

    val result =
      SmartSummarizeResult.Success(
        topicId = "topic-1",
        contentType = ContentType.WEB_ARTICLE,
        title = "Article Title",
        summary = "AI-generated summary.",
        documentFormat = DocumentFormat.UNKNOWN,
      )
    val payload = result.toClassificationPayload(request.rawText)

    assertEquals("user typed this input", payload.rawInput)
    assertTrue(payload.rawInput != context.bodyText)
  }

  @Test
  fun `request with fetchedWebContext preserves rawText sourceUrl sourceTitle`() {
    val context =
      FetchedWebContext(
        originalUrl = "https://example.com/article",
        title = "Article Title",
        description = "Meta desc.",
        bodyText = "Full visible body text.",
      )
    val request =
      SmartSummarizeRequest(
        rawText = "original clipboard content",
        sourceUrl = "https://example.com",
        sourceTitle = "Example Page",
        fetchedWebContext = context,
      )

    assertEquals("original clipboard content", request.rawText)
    assertEquals("https://example.com", request.sourceUrl)
    assertEquals("Example Page", request.sourceTitle)
    assertEquals(context, request.fetchedWebContext)
  }

  // -----------------------------------------------------------------------
  // Interface contract compiles (satisfied by any future impl)
  // -----------------------------------------------------------------------

  @Test
  fun `interface method signature is accessible`() {
    // This also ensures that SmartSummarizer is a valid type in tests.
    val summarizer =
      object : SmartSummarizer {
        override suspend fun summarize(
          request: SmartSummarizeRequest,
          topics: List<Topic>,
          existingItems: List<KnowledgeItem>,
        ): SmartSummarizeResult {
          return SmartSummarizeResult.Failure("not implemented")
        }
      }

    assertNotNull(summarizer)
  }
}
