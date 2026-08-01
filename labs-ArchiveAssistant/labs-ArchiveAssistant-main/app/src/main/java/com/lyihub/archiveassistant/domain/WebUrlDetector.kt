package com.lyihub.archiveassistant.domain

/**
 * Result of detecting a web URL in user input.
 *
 * @property originalUrl The URL token as it appeared in the input (preserves `www.` prefix).
 * @property fetchUrl The URL to actually fetch — `https://` is prepended for `www.` matches.
 * @property isBare `true` when the trimmed whole input is exactly one supported URL with no
 *   whitespace.
 */
data class DetectedWebUrl(
  val originalUrl: String,
  val fetchUrl: String,
  val isBare: Boolean,
)

/**
 * Detects web URLs (`http://`, `https://`, `www.` prefixes) inside free-text input.
 *
 * Designed as a reusable, testable extraction of the URL-detection logic currently duplicated in
 * [ArchiveAssistantStateStore] private helpers.
 */
object WebUrlDetector {

  /**
   * Scans [input] for the first whitespace-delimited token that starts with `http://`, `https://`,
   * or `www.`.
   *
   * @return [DetectedWebUrl] if a supported URL is found, or `null` for blank / non-URL input.
   */
  fun detect(input: String): DetectedWebUrl? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null

    // bare-URL flag: the entire trimmed input is a single token with no whitespace
    // and starts with one of the supported prefixes.
    val isBare =
      trimmed.isNotBlank() &&
        !trimmed.any(Char::isWhitespace) &&
        (trimmed.startsWith("http://") ||
          trimmed.startsWith("https://") ||
          trimmed.startsWith("www."))

    // Find the first URL token in the input (split by whitespace).
    val matchedUrl =
      trimmed.splitToSequence(' ', '\t', '\n').firstOrNull {
        it.startsWith("http://") || it.startsWith("https://") || it.startsWith("www.")
      } ?: return null

    val fetchUrl =
      if (matchedUrl.startsWith("www.")) {
        "https://$matchedUrl"
      } else {
        matchedUrl
      }

    return DetectedWebUrl(
      originalUrl = matchedUrl,
      fetchUrl = fetchUrl,
      isBare = isBare,
    )
  }
}
