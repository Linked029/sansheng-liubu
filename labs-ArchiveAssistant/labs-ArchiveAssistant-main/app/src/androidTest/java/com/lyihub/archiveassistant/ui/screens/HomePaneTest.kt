package com.lyihub.archiveassistant.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lyihub.archiveassistant.domain.SampleKnowledgeData
import com.lyihub.archiveassistant.ui.theme.ArchiveAssistantTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomePaneTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun homePane_displaysParserInputAndClassifyButton() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        HomePane(
          title = "聚合拾遗",
          parserValidationMessage = null,
          recentTopics = SampleKnowledgeData.topics.take(5),
          itemsByTopic = SampleKnowledgeData.items.groupBy { it.topicId },
          onTopicSelected = {},
          onOpenSettings = {},
          onCreateTopic = {},
          searchQuery = "",
          onSearchQueryChanged = {},
          onOpenClipboard = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("clipboard-button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("ministry-stamp-stack").assertIsDisplayed()
  }

  @Test
  fun homePane_memorialDemoButton_click_triggersCallback() {
    var memorialDemoCalled = false

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        HomePane(
          title = "聚合拾遗",
          parserValidationMessage = null,
          recentTopics = emptyList(),
          itemsByTopic = emptyMap(),
          onTopicSelected = {},
          onOpenSettings = {},
          onCreateTopic = {},
          searchQuery = "",
          onSearchQueryChanged = {},
          onOpenClipboard = {},
          onOpenMemorialDemo = { memorialDemoCalled = true },
        )
      }
    }

    composeTestRule.onNodeWithTag("memorial-entry-card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("memorial-entry-card").performClick()
    assertEquals(true, memorialDemoCalled)
  }

  @Test
  fun homePane_clipboardButton_click_triggersCallback() {
    var clipboardCalled = false

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        HomePane(
          title = "聚合拾遗",
          parserValidationMessage = null,
          recentTopics = emptyList(),
          itemsByTopic = emptyMap(),
          onTopicSelected = {},
          onOpenSettings = {},
          onCreateTopic = {},
          searchQuery = "",
          onSearchQueryChanged = {},
          onOpenClipboard = { clipboardCalled = true },
        )
      }
    }

    composeTestRule.onNodeWithTag("clipboard-button").performClick()
    assertEquals(true, clipboardCalled)
  }

  @Test
  fun homePane_validationMessage_showsErrorText() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        HomePane(
          title = "聚合拾遗",
          parserValidationMessage = "请输入要归档的内容",
          recentTopics = emptyList(),
          itemsByTopic = emptyMap(),
          onTopicSelected = {},
          onOpenSettings = {},
          onCreateTopic = {},
          searchQuery = "",
          onSearchQueryChanged = {},
          onOpenClipboard = {},
        )
      }
    }

    composeTestRule.onNodeWithText("请输入要归档的内容").assertIsDisplayed()
  }

  @Test
  fun homePane_recentTopics_showsTopicCards() {
    val recent = SampleKnowledgeData.topics.take(2)

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        HomePane(
          title = "聚合拾遗",
          parserValidationMessage = null,
          recentTopics = recent,
          itemsByTopic = SampleKnowledgeData.items.groupBy { it.topicId },
          onTopicSelected = {},
          onOpenSettings = {},
          onCreateTopic = {},
          searchQuery = "",
          onSearchQueryChanged = {},
          onOpenClipboard = {},
        )
      }
    }

    recent.forEach { topic ->
      composeTestRule.onNodeWithTag("topic-card-${topic.id}").assertIsDisplayed()
    }
  }

  @Test
  fun homePane_smartSummarizationMessage_showsErrorText() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        HomePane(
          title = "聚合拾遗",
          parserValidationMessage = null,
          recentTopics = emptyList(),
          itemsByTopic = emptyMap(),
          onTopicSelected = {},
          onOpenSettings = {},
          onCreateTopic = {},
          searchQuery = "",
          onSearchQueryChanged = {},
          onOpenClipboard = {},
          smartSummarizationMessage = "智能归纳失败",
        )
      }
    }

    composeTestRule.onNodeWithText("智能归纳失败").assertIsDisplayed()
  }
}
