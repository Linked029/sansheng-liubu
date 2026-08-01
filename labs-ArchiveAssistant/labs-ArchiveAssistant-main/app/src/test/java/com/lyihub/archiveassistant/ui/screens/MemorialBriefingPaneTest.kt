package com.lyihub.archiveassistant.ui.screens

import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.KnowledgeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorialBriefingPaneTest {
  @Test
  fun departmentLabelAlpha_hidesNearActiveNeighbors() {
    assertEquals(0f, departmentLabelAlpha(0f), 0.001f)
    assertEquals(0f, departmentLabelAlpha(0.8f), 0.001f)
    assertTrue(departmentLabelAlpha(0.9f) in 0.55f..0.75f)
  }

  @Test
  fun departmentLabelAlpha_showsOnlyAtActivePeak() {
    assertTrue(departmentLabelAlpha(0.95f) > 0.8f)
    assertEquals(1f, departmentLabelAlpha(1f), 0.001f)
  }

  @Test
  fun orderedBriefingItems_cyclesDepartmentsBeforeNextArticleRound() {
    val items =
      listOf(
        sampleItem("ai-1", SampleTopicIds[0]),
        sampleItem("ai-2", SampleTopicIds[0]),
        sampleItem("ui-1", SampleTopicIds[1]),
        sampleItem("clip-1", SampleTopicIds[2]),
        sampleItem("travel-1", SampleTopicIds[3]),
        sampleItem("tool-1", SampleTopicIds[4]),
        sampleItem("workflow-1", SampleTopicIds[5]),
        sampleItem("workflow-2", SampleTopicIds[5]),
      )

    val orderedIds = orderedBriefingItems(items).map { it.id }

    assertEquals(
      listOf("ai-1", "ui-1", "clip-1", "travel-1", "tool-1", "workflow-1", "ai-2", "workflow-2"),
      orderedIds,
    )
  }

  private fun sampleItem(id: String, topicId: String): KnowledgeItem {
    return KnowledgeItem(
      id = id,
      topicId = topicId,
      contentType = ContentType.WEB_ARTICLE,
      title = "标题 $id",
      summary = "摘要 $id",
      fullText = "正文 $id",
      sourceUrl = null,
      createdAtEpochMillis = 0L,
    )
  }
}
