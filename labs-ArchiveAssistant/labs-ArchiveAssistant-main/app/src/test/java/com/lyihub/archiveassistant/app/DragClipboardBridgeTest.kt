package com.lyihub.archiveassistant.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DragClipboardBridgeTest {
  @Test
  fun isMimeAllowed_imagePng_returnsTrue() {
    assertTrue(isMimeAllowed(arrayOf("image/png")))
  }

  @Test
  fun isMimeAllowed_applicationPdf_returnsTrue() {
    assertTrue(isMimeAllowed(arrayOf("application/pdf")))
  }

  @Test
  fun isMimeAllowed_textPlain_returnsTrue() {
    assertTrue(isMimeAllowed(arrayOf("text/plain")))
  }

  @Test
  fun isMimeAllowed_audioMpeg_returnsFalse() {
    assertFalse(isMimeAllowed(arrayOf("audio/mpeg")))
  }

  @Test
  fun isMimeAllowed_videoMp4_returnsFalse() {
    assertFalse(isMimeAllowed(arrayOf("video/mp4")))
  }

  @Test
  fun isMimeAllowed_applicationOctetStream_returnsFalse() {
    assertFalse(isMimeAllowed(arrayOf("application/octet-stream")))
  }

  @Test
  fun isMimeAllowed_textHtml_returnsFalse() {
    assertFalse(isMimeAllowed(arrayOf("text/html")))
  }

  @Test
  fun isMimeAllowed_emptyArray_returnsFalse() {
    assertFalse(isMimeAllowed(emptyArray()))
  }
}
