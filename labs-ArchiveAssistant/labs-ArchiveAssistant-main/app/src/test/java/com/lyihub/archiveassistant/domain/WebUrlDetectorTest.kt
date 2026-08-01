package com.lyihub.archiveassistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUrlDetectorTest {

  // ── http / https / www bare URLs ─────────────────────────────────

  @Test
  fun detect_httpsUrl_returnsDetectedUrl() {
    val result = WebUrlDetector.detect("https://example.com/a")
    assertNotNull(result)
    assertEquals("https://example.com/a", result!!.originalUrl)
    assertEquals("https://example.com/a", result.fetchUrl)
    assertTrue(result.isBare)
  }

  @Test
  fun detect_httpUrl_returnsDetectedUrl() {
    val result = WebUrlDetector.detect("http://example.com/a")
    assertNotNull(result)
    assertEquals("http://example.com/a", result!!.originalUrl)
    assertEquals("http://example.com/a", result.fetchUrl)
    assertTrue(result.isBare)
  }

  @Test
  fun detect_wwwUrl_prependsHttps() {
    val result = WebUrlDetector.detect("www.example.com/a")
    assertNotNull(result)
    assertEquals("www.example.com/a", result!!.originalUrl)
    assertEquals("https://www.example.com/a", result.fetchUrl)
    assertTrue(result.isBare)
  }

  // ── Embedded (non-bare) URLs ────────────────────────────────────

  @Test
  fun detect_embeddedHttpsUrl_returnsUrlNotBare() {
    val result = WebUrlDetector.detect("notes https://example.com/a")
    assertNotNull(result)
    assertEquals("https://example.com/a", result!!.originalUrl)
    assertEquals("https://example.com/a", result.fetchUrl)
    assertEquals(false, result.isBare)
  }

  @Test
  fun detect_embeddedUrlWithPrefix_returnsUrlNotBare() {
    // Matches acceptance case: isBare==false
    val result = WebUrlDetector.detect("read https://example.com/a")
    assertNotNull(result)
    assertEquals(false, result!!.isBare)
  }

  @Test
  fun detect_embeddedWwwUrl_returnsWwwUrlNotBare() {
    val result = WebUrlDetector.detect("visit www.example.com/a")
    assertNotNull(result)
    assertEquals("www.example.com/a", result!!.originalUrl)
    assertEquals("https://www.example.com/a", result.fetchUrl)
    assertEquals(false, result.isBare)
  }

  @Test
  fun detect_embeddedHttpUrl_returnsHttpUrlNotBare() {
    val result = WebUrlDetector.detect("check http://example.com/page")
    assertNotNull(result)
    assertEquals("http://example.com/page", result!!.originalUrl)
    assertEquals("http://example.com/page", result.fetchUrl)
    assertEquals(false, result.isBare)
  }

  // ── Blank / non-URL input ───────────────────────────────────────

  @Test
  fun detect_blankInput_returnsNull() {
    assertNull(WebUrlDetector.detect(""))
    assertNull(WebUrlDetector.detect("   "))
  }

  @Test
  fun detect_nonUrlText_returnsNull() {
    assertNull(WebUrlDetector.detect("hello world"))
    assertNull(WebUrlDetector.detect("plain text without url"))
    assertNull(WebUrlDetector.detect("example.com")) // no protocol prefix
  }

  // ── Whitespace tolerance ────────────────────────────────────────

  @Test
  fun detect_httpsUrlWithSurroundingSpaces_isBare() {
    val result = WebUrlDetector.detect("  https://example.com/a  ")
    assertNotNull(result)
    assertTrue(result!!.isBare)
  }

  @Test
  fun detect_wwwUrlWithSurroundingSpaces_prependsHttps() {
    val result = WebUrlDetector.detect("  www.example.com/path  ")
    assertNotNull(result)
    assertEquals("www.example.com/path", result!!.originalUrl)
    assertEquals("https://www.example.com/path", result.fetchUrl)
    assertTrue(result.isBare)
  }

  // ── First-token wins ────────────────────────────────────────────

  @Test
  fun detect_multipleUrls_returnsFirst() {
    val result = WebUrlDetector.detect("https://first.com http://second.com www.third.com")
    assertNotNull(result)
    assertEquals("https://first.com", result!!.originalUrl)
    assertEquals("https://first.com", result.fetchUrl)
    assertEquals(false, result.isBare)
  }

  @Test
  fun detect_httpThenWww_returnsHttpFirst() {
    val result = WebUrlDetector.detect("http://alpha.com and then www.beta.com")
    assertNotNull(result)
    assertEquals("http://alpha.com", result!!.originalUrl)
    assertEquals("http://alpha.com", result.fetchUrl)
  }

  // ── Tab / newline separation ────────────────────────────────────

  @Test
  fun detect_tabSeparated_urlDetected() {
    val result = WebUrlDetector.detect("text\thttps://example.com/a")
    assertNotNull(result)
    assertEquals("https://example.com/a", result!!.originalUrl)
    assertEquals(false, result.isBare)
  }

  @Test
  fun detect_newlineSeparated_urlDetected() {
    val result = WebUrlDetector.detect("line1\nhttps://example.com/a")
    assertNotNull(result)
    assertEquals("https://example.com/a", result!!.originalUrl)
    assertEquals(false, result.isBare)
  }
}
