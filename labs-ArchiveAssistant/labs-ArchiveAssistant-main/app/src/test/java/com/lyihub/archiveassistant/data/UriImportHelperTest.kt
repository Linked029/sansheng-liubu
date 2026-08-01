package com.lyihub.archiveassistant.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UriImportHelperTest {

  @get:Rule val tempFolder = TemporaryFolder()

  // -- uniqueImportFile --

  @Test
  fun uniqueImportFile_noCollision_returnsSameName() {
    val dir = tempFolder.root
    val result = uniqueImportFile(dir, "document.pdf")
    assertEquals(File(dir, "document.pdf"), result)
  }

  @Test
  fun uniqueImportFile_oneCollision_appendsSuffix() {
    val dir = tempFolder.root
    File(dir, "photo.jpg").createNewFile()
    val result = uniqueImportFile(dir, "photo.jpg")
    assertEquals(File(dir, "photo (1).jpg"), result)
  }

  @Test
  fun uniqueImportFile_multipleCollisions_incrementsSuffix() {
    val dir = tempFolder.root
    File(dir, "notes.txt").createNewFile()
    File(dir, "notes (1).txt").createNewFile()
    File(dir, "notes (2).txt").createNewFile()
    val result = uniqueImportFile(dir, "notes.txt")
    assertEquals(File(dir, "notes (3).txt"), result)
  }

  @Test
  fun uniqueImportFile_fileWithoutExtension_appendsSuffix() {
    val dir = tempFolder.root
    File(dir, "readme").createNewFile()
    val result = uniqueImportFile(dir, "readme")
    assertEquals(File(dir, "readme (1)"), result)
  }

  @Test
  fun uniqueImportFile_dotfileNoExtension_doesNotConfuseWithExtension() {
    val dir = tempFolder.root
    File(dir, ".hidden").createNewFile()
    val result = uniqueImportFile(dir, ".hidden")
    assertEquals(File(dir, ".hidden (1)"), result)
  }

  // -- importFileName --

  @Test
  fun importFileName_normalName_returnsAsIs() {
    val result = importFileName("report.pdf", ".pdf")
    assertEquals("report.pdf", result)
  }

  @Test
  fun importFileName_nullName_returnsFallback() {
    val result = importFileName(null, ".md")
    assertEquals("imported-file.md", result)
  }

  @Test
  fun importFileName_emptyExtension_usesEmptyFallback() {
    val result = importFileName("data", "")
    assertEquals("data", result)
  }

  @Test
  fun importFileName_blankExtension_usesEmptyFallback() {
    val result = importFileName("data", "   ")
    assertEquals("data", result)
  }

  @Test
  fun importFileName_blankName_returnsFallback() {
    val result = importFileName("  ", ".txt")
    assertEquals("imported-file.txt", result)
  }

  @Test
  fun importFileName_sanitizesNullByte() {
    val result = importFileName("bad\u0000file.txt", ".txt")
    assertEquals("bad_file.txt", result)
  }
}
