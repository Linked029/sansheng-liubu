package com.lyihub.archiveassistant.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

fun importFileName(displayName: String?, fallbackExtension: String): String {
  val sanitizedName =
    displayName
      ?.substringAfterLast('/')
      ?.substringAfterLast('\\')
      ?.replace('\u0000', '_')
      ?.trim()
      ?.takeIf { it.isNotBlank() }

  return sanitizedName ?: "imported-file$fallbackExtension"
}

fun uniqueImportFile(itemsDir: File, fileName: String): File {
  val initialFile = File(itemsDir, fileName)
  if (!initialFile.exists()) return initialFile

  val dotIndex = fileName.lastIndexOf('.').takeIf { it > 0 }
  val baseName = dotIndex?.let { fileName.substring(0, it) } ?: fileName
  val extension = dotIndex?.let { fileName.substring(it) } ?: ""
  var suffix = 1

  while (true) {
    val candidate = File(itemsDir, "$baseName ($suffix)$extension")
    if (!candidate.exists()) return candidate
    suffix += 1
  }
}

fun markdownFileName(title: String): String {
  val baseName =
    title
      .lineSequence()
      .firstOrNull()
      .orEmpty()
      .trim()
      .take(48)
      .replace(Regex("[\\\\/:*?\"<>|]+"), "-")
      .ifBlank { "clipboard-note" }
  return if (baseName.endsWith(".md", ignoreCase = true)) baseName else "$baseName.md"
}

fun writeMarkdownFile(itemsDir: File, title: String, content: String): File? {
  return try {
    itemsDir.mkdirs()
    val dest = uniqueImportFile(itemsDir, markdownFileName(title))
    dest.writeText(content)
    dest
  } catch (_: Exception) {
    null
  }
}

fun copyUriToFile(context: Context, uri: Uri, destFile: File): Boolean {
  return try {
    context.contentResolver.openInputStream(uri)?.use { input ->
      destFile.outputStream().use { output -> input.copyTo(output) }
    } != null
  } catch (_: Exception) {
    false
  }
}

fun resolveDisplayName(context: Context, uri: Uri): String {
  if (uri.scheme == "content") {
    try {
      context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
              val name = cursor.getString(nameIndex)
              if (!name.isNullOrBlank()) return name
            }
          }
        }
    } catch (_: Exception) {}
  }
  return uri.lastPathSegment ?: uri.toString()
}
