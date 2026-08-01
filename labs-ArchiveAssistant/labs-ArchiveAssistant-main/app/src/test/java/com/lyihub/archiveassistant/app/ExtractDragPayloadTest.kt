package com.lyihub.archiveassistant.app

import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractDragPayloadTest {
  @Test
  fun classifyDragItemByExtension_photoPng_returnsImageScreenshot() {
    val result = classifyDragItemByExtension("photo.png")

    assertEquals(ContentType.IMAGE_SCREENSHOT, result?.contentType)
    assertNull(result?.documentFormat)
  }

  @Test
  fun classifyDragItemByExtension_docPdf_returnsPdfDocument() {
    val result = classifyDragItemByExtension("doc.pdf")

    assertEquals(ContentType.DOCUMENT, result?.contentType)
    assertEquals(DocumentFormat.PDF, result?.documentFormat)
  }

  @Test
  fun classifyDragItemByExtension_notesMd_returnsMarkdownDocument() {
    val result = classifyDragItemByExtension("notes.md")

    assertEquals(ContentType.DOCUMENT, result?.contentType)
    assertEquals(DocumentFormat.MARKDOWN, result?.documentFormat)
  }

  @Test
  fun classifyDragItemByExtension_readmeTxt_returnsTxtDocument() {
    val result = classifyDragItemByExtension("readme.txt")

    assertEquals(ContentType.DOCUMENT, result?.contentType)
    assertEquals(DocumentFormat.TXT, result?.documentFormat)
  }

  @Test
  fun classifyDragItemByExtension_reportDocx_returnsDocxDocument() {
    val result = classifyDragItemByExtension("report.docx")

    assertEquals(ContentType.DOCUMENT, result?.contentType)
    assertEquals(DocumentFormat.DOCX, result?.documentFormat)
  }

  @Test
  fun classifyDragItemByExtension_songMp3_returnsNull() {
    assertNull(classifyDragItemByExtension("song.mp3"))
  }

  @Test
  fun classifyDragItemByExtension_videoMp4_returnsNull() {
    assertNull(classifyDragItemByExtension("video.mp4"))
  }

  @Test
  fun classifyDragItemByExtension_null_returnsNull() {
    assertNull(classifyDragItemByExtension(null))
  }

  @Test
  fun classifyDragItemByExtension_noExtension_returnsNull() {
    assertNull(classifyDragItemByExtension("noext"))
  }

  @Test
  fun classifyDragItemByExtension_uppercasePdf_returnsPdfDocument() {
    val result = classifyDragItemByExtension("file.PDF")

    assertEquals(ContentType.DOCUMENT, result?.contentType)
    assertEquals(DocumentFormat.PDF, result?.documentFormat)
  }

  @Test
  fun classifyDragItemByExtension_dataXlsx_returnsUnknownDocument() {
    val result = classifyDragItemByExtension("data.xlsx")

    assertEquals(ContentType.DOCUMENT, result?.contentType)
    assertEquals(DocumentFormat.UNKNOWN, result?.documentFormat)
  }
}
