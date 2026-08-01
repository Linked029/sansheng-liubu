package com.lyihub.archiveassistant.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lyihub.archiveassistant.domain.SampleKnowledgeData
import com.lyihub.archiveassistant.state.TopicNameDialogMode
import com.lyihub.archiveassistant.ui.theme.ArchiveAssistantTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TopicManagementDialogsTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun createDialog_showsAndConfirms() {
    var confirmedName = ""
    var dismissed = false

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        TopicManagementDialogs(
          topics = SampleKnowledgeData.topics,
          topicNameDialogMode = TopicNameDialogMode.CREATE,
          topicNameDialogTopicId = null,
          topicValidationMessage = null,
          deleteConfirmTopicId = null,
          onConfirmCreateTopic = { confirmedName = it },
          onConfirmRenameTopic = {},
          onConfirmDeleteTopic = {},
          onCloseTopicNameDialog = { dismissed = true },
          onCloseDeleteConfirmDialog = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("topic-name-dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("建立新主题").assertIsDisplayed()

    composeTestRule.onNodeWithTag("topic-name-dialog-confirm").performClick()
    assertEquals("", confirmedName)

    composeTestRule.onNodeWithTag("topic-name-dialog-dismiss").performClick()
    assertTrue(dismissed)
  }

  @Test
  fun renameDialog_usesSelectedTopicName() {
    var confirmedName = ""

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        TopicManagementDialogs(
          topics = SampleKnowledgeData.topics,
          topicNameDialogMode = TopicNameDialogMode.RENAME,
          topicNameDialogTopicId = SampleKnowledgeData.topics.first().id,
          topicValidationMessage = null,
          deleteConfirmTopicId = null,
          onConfirmCreateTopic = {},
          onConfirmRenameTopic = { confirmedName = it },
          onConfirmDeleteTopic = {},
          onCloseTopicNameDialog = {},
          onCloseDeleteConfirmDialog = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("topic-name-dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("重命名主题").assertIsDisplayed()

    composeTestRule.onNodeWithTag("topic-name-dialog-confirm").performClick()
    assertEquals(SampleKnowledgeData.topics.first().title, confirmedName)
  }

  @Test
  fun validationMessage_showsErrorText() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        TopicManagementDialogs(
          topics = SampleKnowledgeData.topics,
          topicNameDialogMode = TopicNameDialogMode.CREATE,
          topicNameDialogTopicId = null,
          topicValidationMessage = "请输入主题名称",
          deleteConfirmTopicId = null,
          onConfirmCreateTopic = {},
          onConfirmRenameTopic = {},
          onConfirmDeleteTopic = {},
          onCloseTopicNameDialog = {},
          onCloseDeleteConfirmDialog = {},
        )
      }
    }

    composeTestRule.onNodeWithText("请输入主题名称").assertIsDisplayed()
  }

  @Test
  fun deleteConfirmDialog_showsAndConfirms() {
    var confirmed = false
    var dismissed = false

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        TopicManagementDialogs(
          topics = SampleKnowledgeData.topics,
          topicNameDialogMode = null,
          topicNameDialogTopicId = null,
          topicValidationMessage = null,
          deleteConfirmTopicId = SampleKnowledgeData.topics.first().id,
          onConfirmCreateTopic = {},
          onConfirmRenameTopic = {},
          onConfirmDeleteTopic = { confirmed = true },
          onCloseTopicNameDialog = {},
          onCloseDeleteConfirmDialog = { dismissed = true },
        )
      }
    }

    composeTestRule.onNodeWithTag("delete-confirm-dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("确认删除").assertIsDisplayed()

    composeTestRule.onNodeWithTag("delete-confirm-dialog-confirm").performClick()
    assertTrue(confirmed)

    composeTestRule.onNodeWithTag("delete-confirm-dialog-dismiss").performClick()
    assertTrue(dismissed)
  }
}
