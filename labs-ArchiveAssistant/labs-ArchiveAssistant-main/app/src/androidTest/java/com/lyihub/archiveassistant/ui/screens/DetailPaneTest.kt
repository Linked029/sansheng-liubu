package com.lyihub.archiveassistant.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.SampleKnowledgeData
import com.lyihub.archiveassistant.state.AddItemDialogPrefill
import com.lyihub.archiveassistant.ui.theme.ArchiveAssistantTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DetailPaneTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val defaultTopic =
    SampleKnowledgeData.topics.first { it.id == SampleKnowledgeData.DefaultTopicId }
  private val defaultItems =
    SampleKnowledgeData.items.filter { it.topicId == SampleKnowledgeData.DefaultTopicId }

  @Test
  fun detailPane_displaysHeaderAndCards() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        DetailPane(
          topic = defaultTopic,
          items = defaultItems,
          searchQuery = "",
          onBack = {},
          onItemClick = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("detail-pane").assertIsDisplayed()
    composeTestRule.onNodeWithText(defaultTopic.title).assertIsDisplayed()
    composeTestRule.onNodeWithTag("detail-summary").assertIsDisplayed()
    defaultItems.forEach { item ->
      composeTestRule.onNodeWithTag("knowledge-card-${item.id}").assertIsDisplayed()
    }
  }

  @Test
  fun detailPane_emptyFilter_showsEmptyState() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        DetailPane(
          topic = defaultTopic,
          items = emptyList(),
          searchQuery = "",
          onBack = {},
          onItemClick = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("detail-summary").assertIsDisplayed()
    composeTestRule.onNodeWithText("该分类下暂无资料").assertIsDisplayed()
  }

  @Test
  fun detailPane_cardClick_triggersCallback() {
    var clickedItemId: String? = null

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        DetailPane(
          topic = defaultTopic,
          items = defaultItems,
          searchQuery = "",
          onBack = {},
          onItemClick = { clickedItemId = it },
        )
      }
    }

    val targetItem = defaultItems.first()
    composeTestRule.onNodeWithTag("knowledge-card-${targetItem.id}").performClick()
    assertEquals(targetItem.id, clickedItemId)
  }

  @Test
  fun addItemDialog_whenSourcePrefillIsLocked_doesNotSwitchType() {
    var confirmedTopicId: String? = null
    var confirmedType: ContentType? = null

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        AddItemDialog(
          onDismiss = {},
          onConfirm = { topicId, _, contentType, _, _, _, _, _ ->
            confirmedTopicId = topicId
            confirmedType = contentType
          },
          validationMessage = null,
          prefill =
            AddItemDialogPrefill(
              title = "paper.pdf",
              contentType = ContentType.DOCUMENT,
              sourceUrl = "content://clipboard/document",
              documentFormat = DocumentFormat.PDF,
              fileName = "paper.pdf",
              lockContentType = true,
            ),
          topics = SampleKnowledgeData.topics,
          initialTopicId = SampleKnowledgeData.DefaultTopicId,
        )
      }
    }

    composeTestRule.onNodeWithText("归属主题").assertIsDisplayed()
    composeTestRule.onNodeWithTag("add-item-topic-selector").assertIsDisplayed()
    composeTestRule.onNodeWithTag("add-item-summary").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("AI总结").assertCountEquals(0)
    composeTestRule.onNodeWithText("网页").assertHasNoClickAction()
    composeTestRule.onNodeWithText("文档").assertHasNoClickAction()
    composeTestRule.onAllNodesWithTag("add-item-pick-document").assertCountEquals(0)
    composeTestRule.onNodeWithTag("add-item-confirm").performClick()

    assertEquals(SampleKnowledgeData.DefaultTopicId, confirmedTopicId)
    assertEquals(ContentType.DOCUMENT, confirmedType)
  }

  @Test
  fun addItemDialog_whenTextPrefill_showsOnlyDocumentAndTextContent() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        AddItemDialog(
          onDismiss = {},
          onConfirm = { _, _, _, _, _, _, _, _ -> },
          validationMessage = null,
          prefill =
            AddItemDialogPrefill(
              contentType = ContentType.DOCUMENT,
              documentFormat = DocumentFormat.MARKDOWN,
              lockContentType = true,
              availableContentTypes = listOf(ContentType.DOCUMENT),
              textContent = "Clipboard note",
            ),
          topics = SampleKnowledgeData.topics,
          initialTopicId = SampleKnowledgeData.DefaultTopicId,
        )
      }
    }

    composeTestRule.onAllNodesWithText("网页").assertCountEquals(0)
    composeTestRule.onAllNodesWithText("图片").assertCountEquals(0)
    composeTestRule.onNodeWithText("文档").assertHasNoClickAction()
    composeTestRule.onNodeWithTag("add-item-text-content").assertTextEquals("Clipboard note")
  }

  @Test
  fun addItemDialog_whenUrlPrefillSwitchesType_preservesLinkAndShowsTextContent() {
    val copiedUrl = "https://example.com/article"

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        AddItemDialog(
          onDismiss = {},
          onConfirm = { _, _, _, _, _, _, _, _ -> },
          validationMessage = null,
          prefill =
            AddItemDialogPrefill(
              contentType = ContentType.WEB_ARTICLE,
              sourceUrl = copiedUrl,
              availableContentTypes = listOf(ContentType.WEB_ARTICLE, ContentType.DOCUMENT),
              textContent = copiedUrl,
            ),
          topics = SampleKnowledgeData.topics,
          initialTopicId = SampleKnowledgeData.DefaultTopicId,
        )
      }
    }

    composeTestRule.onNodeWithTag("add-item-url").assertTextEquals(copiedUrl)
    composeTestRule.onNodeWithText("文档").performClick()
    composeTestRule.onNodeWithTag("add-item-text-content").assertTextEquals(copiedUrl)
    composeTestRule.onNodeWithText("网页").performClick()
    composeTestRule.onNodeWithTag("add-item-url").assertTextEquals(copiedUrl)
  }

  @Test
  fun addItemDialog_whenMixedImageTextPrefill_showsOnlyLockedDocumentType() {
    var confirmedType: ContentType? = null

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        AddItemDialog(
          onDismiss = {},
          onConfirm = { _, _, contentType, _, _, _, _, _ -> confirmedType = contentType },
          validationMessage = null,
          prefill =
            AddItemDialogPrefill(
              title = "Caption and notes",
              contentType = ContentType.DOCUMENT,
              documentFormat = DocumentFormat.MARKDOWN,
              lockContentType = true,
              availableContentTypes = listOf(ContentType.DOCUMENT),
            ),
          topics = SampleKnowledgeData.topics,
          initialTopicId = SampleKnowledgeData.DefaultTopicId,
        )
      }
    }

    composeTestRule.onAllNodesWithText("网页").assertCountEquals(0)
    composeTestRule.onAllNodesWithText("图片").assertCountEquals(0)
    composeTestRule.onNodeWithText("文档").assertHasNoClickAction()
    composeTestRule.onNodeWithTag("add-item-confirm").performClick()

    assertEquals(ContentType.DOCUMENT, confirmedType)
  }

  @Test
  fun addItemDialog_whenTextPrefillSavedAsDocument_writesMarkdownFile() {
    var confirmedType: ContentType? = null
    var confirmedSourceUrl: String? = null
    var confirmedDocumentFormat: DocumentFormat? = null
    var confirmedFileName: String? = null

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        AddItemDialog(
          onDismiss = {},
          onConfirm = { _, _, contentType, sourceUrl, _, _, documentFormat, fileName ->
            confirmedType = contentType
            confirmedSourceUrl = sourceUrl
            confirmedDocumentFormat = documentFormat
            confirmedFileName = fileName
          },
          validationMessage = null,
          prefill =
            AddItemDialogPrefill(
              contentType = ContentType.DOCUMENT,
              documentFormat = DocumentFormat.MARKDOWN,
              lockContentType = true,
              availableContentTypes = listOf(ContentType.DOCUMENT),
              textContent = "# Clipboard Note\nBody",
            ),
          topics = SampleKnowledgeData.topics,
          initialTopicId = SampleKnowledgeData.DefaultTopicId,
        )
      }
    }

    composeTestRule.onNodeWithTag("add-item-title").performTextInput("Clipboard Note")
    composeTestRule.onNodeWithTag("add-item-confirm").performClick()

    assertEquals(ContentType.DOCUMENT, confirmedType)
    assertEquals(DocumentFormat.MARKDOWN, confirmedDocumentFormat)
    assertTrue(confirmedFileName?.endsWith(".md") == true)
    val sourceFile = File(requireNotNull(confirmedSourceUrl))
    assertTrue(sourceFile.exists())
    assertEquals("# Clipboard Note\nBody", sourceFile.readText())
  }
}
