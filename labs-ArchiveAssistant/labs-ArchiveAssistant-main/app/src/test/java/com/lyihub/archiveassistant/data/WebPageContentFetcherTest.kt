package com.lyihub.archiveassistant.data

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPageContentFetcherTest {
  @Test
  fun fetch_staticHtml_extractsPriorityMetadataAndNormalizedBody() = runBlocking {
    val fetcher =
      DefaultWebPageContentFetcher(
        FakeWebPageTransport(
          body =
            """
            <html>
              <head>
                <meta property="og:title" content="OG Title">
                <meta name="twitter:title" content="Twitter Title">
                <meta name="description" content="Meta Description">
                <meta property="og:description" content="OG Description">
                <title>Document Title</title>
              </head>
              <body>
                <header>Header text</header>
                <nav>Navigation text</nav>
                <h1>Heading Title</h1>
                <main> First    paragraph.
                  <script>hiddenScript()</script>
                  <style>.hidden { color: red; }</style>
                  <noscript>Noscript text</noscript>
                  <footer>Footer text</footer>
                  Second paragraph.
                </main>
              </body>
            </html>
            """
              .trimIndent(),
          resolvedUrl = "https://example.com/final",
        )
      )

    val result = fetcher.fetch("https://example.com/source", "https://example.com/fetch")

    assertTrue(result is WebPageContentFetchResult.Success)
    val content = (result as WebPageContentFetchResult.Success).content
    assertEquals("https://example.com/source", content.originalUrl)
    assertEquals("https://example.com/fetch", content.fetchUrl)
    assertEquals("https://example.com/final", content.resolvedUrl)
    assertEquals("OG Title", content.title)
    assertEquals("Meta Description", content.description)
    assertEquals("Heading Title First paragraph. Second paragraph.", content.bodyText)
    assertFalse(content.bodyText.contains("Header text"))
    assertFalse(content.bodyText.contains("Navigation text"))
    assertFalse(content.bodyText.contains("Footer text"))
    assertEquals("text/html; charset=utf-8", content.contentType)
  }

  @Test
  fun fetch_staticHtml_usesTwitterTitleThenTitleThenH1Fallbacks() = runBlocking {
    val twitterTitle =
      successfulContent(
        """
        <html><head><meta name="twitter:title" content="Twitter Title"><title>Document Title</title></head><body>Body</body></html>
        """
          .trimIndent()
      )
    val title =
      successfulContent("<html><head><title>Document Title</title></head><body>Body</body></html>")
    val h1 = successfulContent("<html><body><h1>Heading Title</h1><p>Body</p></body></html>")

    assertEquals("Twitter Title", twitterTitle.title)
    assertEquals("Document Title", title.title)
    assertEquals("Heading Title", h1.title)
  }

  @Test
  fun fetch_staticHtml_usesOgThenTwitterDescriptionFallbacks() = runBlocking {
    val ogDescription =
      successfulContent(
        """
        <html><head><meta property="og:description" content="OG Description"><meta name="twitter:description" content="Twitter Description"></head><body>Body</body></html>
        """
          .trimIndent()
      )
    val twitterDescription =
      successfulContent(
        """
        <html><head><meta name="twitter:description" content="Twitter Description"></head><body>Body</body></html>
        """
          .trimIndent()
      )

    assertEquals("OG Description", ogDescription.description)
    assertEquals("Twitter Description", twitterDescription.description)
  }

  @Test
  fun fetch_staticHtml_truncatesNormalizedBodyToTwelveThousandCharacters() = runBlocking {
    val repeatedText = List(12_050) { "x" }.joinToString("\n")

    val content = successfulContent("<html><body>$repeatedText</body></html>")

    assertEquals(12_000, content.bodyText.length)
    assertTrue(content.bodyText.all { it == 'x' || it == ' ' })
  }

  @Test
  fun fetch_http404_returnsExplicitFailure() = runBlocking {
    val result = fetchResult(FakeWebPageTransport(code = 404, body = "not found"))

    assertEquals(WebPageContentFetchResult.Failure("网页抓取失败：HTTP 404"), result)
  }

  @Test
  fun fetch_timeoutOrIoException_returnsSafeFailure() = runBlocking {
    val timeout = fetchResult(FakeWebPageTransport(error = SocketTimeoutException("raw timeout")))
    val io = fetchResult(FakeWebPageTransport(error = IOException("raw io")))

    assertEquals(WebPageContentFetchResult.Failure("网页抓取超时，请稍后重试"), timeout)
    assertEquals(WebPageContentFetchResult.Failure("网页抓取失败，请检查链接或网络"), io)
  }

  @Test
  fun fetch_blankMissingOrNonHtmlContentType_returnsUnsupportedFailure() = runBlocking {
    val blank =
      fetchResult(FakeWebPageTransport(contentType = " ", body = "<html><body>Body</body></html>"))
    val missing =
      fetchResult(FakeWebPageTransport(contentType = null, body = "<html><body>Body</body></html>"))
    val nonHtml = fetchResult(FakeWebPageTransport(contentType = "application/json", body = "{}"))

    val expected = WebPageContentFetchResult.Failure("不支持的网页内容类型")
    assertEquals(expected, blank)
    assertEquals(expected, missing)
    assertEquals(expected, nonHtml)
  }

  @Test
  fun fetch_emptyBody_returnsExplicitFailure() = runBlocking {
    val result = fetchResult(FakeWebPageTransport(body = "   "))

    assertEquals(WebPageContentFetchResult.Failure("网页内容为空"), result)
  }

  private suspend fun successfulContent(html: String): FetchedWebPageContent {
    val result = fetchResult(FakeWebPageTransport(body = html))
    assertTrue(result is WebPageContentFetchResult.Success)
    return (result as WebPageContentFetchResult.Success).content
  }

  private suspend fun fetchResult(transport: FakeWebPageTransport): WebPageContentFetchResult =
    DefaultWebPageContentFetcher(transport)
      .fetch("https://example.com/source", "https://example.com/fetch")

  private class FakeWebPageTransport(
    private val code: Int = 200,
    private val contentType: String? = "text/html; charset=utf-8",
    private val body: String = "<html><body>Body</body></html>",
    private val resolvedUrl: String = "https://example.com/fetch",
    private val error: Throwable? = null,
  ) : WebPageTransport {
    override suspend fun fetch(url: String): WebPageResponse {
      error?.let { throw it }
      return WebPageResponse(
        code = code,
        contentType = contentType,
        body = body,
        resolvedUrl = resolvedUrl,
      )
    }
  }
}
