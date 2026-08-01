package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.DocumentFormat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentContentExtractorTest {
  @Test
  fun readTextDocument_normalizesWhitespaceAndKeepsMarkdown() {
    val input = ByteArrayInputStream("# Title\n\nFirst    paragraph".toByteArray(Charsets.UTF_8))

    val content = readTextDocument(input, "note.md", DocumentFormat.MARKDOWN)

    assertEquals("note.md", content.fileName)
    assertEquals(DocumentFormat.MARKDOWN, content.format)
    assertEquals("# Title\n\nFirst paragraph", content.extractedText)
    assertFalse(content.isTruncated)
  }

  @Test
  fun readTextDocument_truncatesRequestContext() {
    val input = ByteArrayInputStream("a".repeat(20_000).toByteArray(Charsets.UTF_8))

    val content = readTextDocument(input, "long.txt", DocumentFormat.TXT)

    assertEquals(16_000, content.extractedText.length)
    assertEquals(20_000, content.originalCharCount)
    assertTrue(content.isTruncated)
  }

  @Test
  fun readDocxDocument_extractsParagraphText() {
    val docx =
      docxBytes(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:body>
            <w:p><w:r><w:t>Hello</w:t></w:r><w:r><w:t> DOCX</w:t></w:r></w:p>
            <w:p><w:r><w:t>Second paragraph</w:t></w:r></w:p>
          </w:body>
        </w:document>
        """
          .trimIndent()
      )

    val content = readDocxDocument(ByteArrayInputStream(docx), "paper.docx")

    assertEquals(DocumentFormat.DOCX, content.format)
    assertTrue(content.extractedText.contains("Hello DOCX"))
    assertTrue(content.extractedText.contains("Second paragraph"))
  }

  @Test
  fun readPdfDocument_extractsPageText() {
    val input = ByteArrayInputStream(byteArrayOf(1, 2, 3))

    val content = readPdfDocument(input, "paper.pdf") { "Hello    PDF archive" }

    assertEquals("paper.pdf", content.fileName)
    assertEquals(DocumentFormat.PDF, content.format)
    assertEquals("Hello PDF archive", content.extractedText)
    assertFalse(content.isTruncated)
  }

  private fun docxBytes(documentXml: String): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
      zip.putNextEntry(ZipEntry("word/document.xml"))
      zip.write(documentXml.toByteArray(Charsets.UTF_8))
      zip.closeEntry()
    }
    return output.toByteArray()
  }
}
