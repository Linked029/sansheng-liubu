package com.lyihub.archiveassistant.ui.screens

import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.KnowledgeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorialCoverSequenceTest {
  @Test
  fun wheelResources_avoidsNearDuplicatesAcrossCircularBoundary() {
    val resources = (1..23).toList()
    val sequence =
      MemorialCoverSequence.wheelResources(
        coverResources = resources,
        itemCount = 20,
        duplicateGuard = 3,
        seed = 20260627,
      )

    assertEquals(20, sequence.size)
    sequence.indices.forEach { index ->
      (1..3).forEach { distance ->
        assertNotEquals(sequence[index], sequence[(index + distance) % sequence.size])
        assertNotEquals(
          sequence[index],
          sequence[(index - distance + sequence.size) % sequence.size],
        )
      }
    }
  }

  @Test
  fun articleCoverOffset_keepsArticleReadersAwayFromFixedFirstCover() {
    val item =
      KnowledgeItem(
        id = "demo-article",
        topicId = "topic",
        contentType = ContentType.WEB_ARTICLE,
        title = "测试文章",
        summary = "摘要",
        fullText = "正文",
        sourceUrl = null,
        createdAtEpochMillis = 0L,
      )

    val offset = MemorialCoverSequence.articleCoverOffset(item, coverPoolSize = 23)

    assertTrue(offset in 1 until 23)
  }
}
