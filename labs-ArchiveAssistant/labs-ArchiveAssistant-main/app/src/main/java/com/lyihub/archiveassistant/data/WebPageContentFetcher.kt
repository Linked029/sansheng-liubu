package com.lyihub.archiveassistant.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

interface WebPageContentFetcher {
  suspend fun fetch(originalUrl: String, fetchUrl: String): WebPageContentFetchResult
}

class DefaultWebPageContentFetcher(
  private val transport: WebPageTransport = OkHttpWebPageTransport()
) : WebPageContentFetcher {
  override suspend fun fetch(originalUrl: String, fetchUrl: String): WebPageContentFetchResult =
    withContext(Dispatchers.IO) {
      val response =
        runCatching { transport.fetch(fetchUrl) }
          .getOrElse { error ->
            return@withContext WebPageContentFetchResult.Failure(mapWebPageFetchError(error))
          }

      if (response.code !in 200..299) {
        return@withContext WebPageContentFetchResult.Failure("网页抓取失败：HTTP ${response.code}")
      }

      val contentType = response.contentType?.trim().orEmpty()
      if (contentType.isBlank() || !contentType.contains("text/html", ignoreCase = true)) {
        return@withContext WebPageContentFetchResult.Failure("不支持的网页内容类型")
      }

      val html = response.body.trim()
      if (html.isEmpty()) {
        return@withContext WebPageContentFetchResult.Failure("网页内容为空")
      }

      val resolvedUrl = response.resolvedUrl.ifBlank { fetchUrl }
      WebPageContentFetchResult.Success(
        extractWebPageContent(originalUrl, fetchUrl, resolvedUrl, html, contentType)
      )
    }
}

sealed class WebPageContentFetchResult {
  data class Success(val content: FetchedWebPageContent) : WebPageContentFetchResult()

  data class Failure(val message: String) : WebPageContentFetchResult()
}

data class FetchedWebPageContent(
  val originalUrl: String,
  val fetchUrl: String,
  val resolvedUrl: String,
  val title: String,
  val description: String,
  val bodyText: String,
  val contentType: String,
)

interface WebPageTransport {
  suspend fun fetch(url: String): WebPageResponse
}

class OkHttpWebPageTransport(private val client: OkHttpClient = defaultWebPageOkHttpClient()) :
  WebPageTransport {
  override suspend fun fetch(url: String): WebPageResponse =
    withContext(Dispatchers.IO) {
      val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()

      client.newCall(request).execute().use { response ->
        WebPageResponse(
          code = response.code,
          contentType = response.header("Content-Type"),
          body = response.body?.string().orEmpty(),
          resolvedUrl = response.request.url.toString(),
        )
      }
    }

  private companion object {
    const val USER_AGENT = "Mozilla/5.0 (Android) JuHeShiYi/1.0"
  }
}

data class WebPageResponse(
  val code: Int,
  val contentType: String?,
  val body: String,
  val resolvedUrl: String,
)

private fun defaultWebPageOkHttpClient(): OkHttpClient =
  OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .callTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

private fun extractWebPageContent(
  originalUrl: String,
  fetchUrl: String,
  resolvedUrl: String,
  html: String,
  contentType: String,
): FetchedWebPageContent {
  val document = Jsoup.parse(html, resolvedUrl)
  document.select("script,style,noscript,nav,header,footer,aside,form").remove()
  val bodyText = normalizeWhitespace(document.body()?.text().orEmpty()).take(MAX_BODY_TEXT_LENGTH)

  return FetchedWebPageContent(
    originalUrl = originalUrl,
    fetchUrl = fetchUrl,
    resolvedUrl = resolvedUrl,
    title =
      firstNonBlank(
        document.selectFirst("meta[property=og:title]")?.attr("content"),
        document.selectFirst("meta[name=twitter:title]")?.attr("content"),
        document.title(),
        document.selectFirst("h1")?.text(),
      ),
    description =
      firstNonBlank(
        document.selectFirst("meta[name=description]")?.attr("content"),
        document.selectFirst("meta[property=og:description]")?.attr("content"),
        document.selectFirst("meta[name=twitter:description]")?.attr("content"),
      ),
    bodyText = bodyText,
    contentType = contentType,
  )
}

private fun firstNonBlank(vararg values: String?): String =
  values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

private fun normalizeWhitespace(value: String): String = value.trim().replace(WHITESPACE_REGEX, " ")

private fun mapWebPageFetchError(error: Throwable): String =
  when (error) {
    is SocketTimeoutException -> "网页抓取超时，请稍后重试"
    is IOException -> "网页抓取失败，请检查链接或网络"
    else -> "网页抓取失败，请稍后重试"
  }

private const val MAX_BODY_TEXT_LENGTH = 12_000
private val WHITESPACE_REGEX = Regex("\\s+")
