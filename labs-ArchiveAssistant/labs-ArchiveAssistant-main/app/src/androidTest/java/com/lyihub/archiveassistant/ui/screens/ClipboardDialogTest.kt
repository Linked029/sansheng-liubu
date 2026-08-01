package com.lyihub.archiveassistant.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lyihub.archiveassistant.domain.DocumentFormat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ClipboardDialogTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun clipboardDialog_dragSourceLabel_showsDragTitle() {
    composeRule.setContent {
      ClipboardDialog(
        content = "test content",
        sourceLabel = "拖拽",
        onSummarize = {},
        onManualCreate = {},
        onDismiss = {},
      )
    }
    composeRule.onNodeWithText("检测到拖拽内容").assertIsDisplayed()
  }

  @Test
  fun clipboardDialog_nullSourceLabel_showsClipboardTitle() {
    composeRule.setContent {
      ClipboardDialog(
        content = "test content",
        sourceLabel = null,
        onSummarize = {},
        onManualCreate = {},
        onDismiss = {},
      )
    }
    composeRule.onNodeWithText("检测到剪切板内容").assertIsDisplayed()
  }

  @Test
  fun clipboardDialog_smartSummarizing_smartSummarizeButtonDisabledAndShowsLoadingText() {
    composeRule.setContent {
      ClipboardDialog(
        content = "test content",
        onSummarize = {},
        onManualCreate = {},
        onDismiss = {},
        isSmartSummarizing = true,
      )
    }
    composeRule.onNodeWithText("归纳中…").assertIsDisplayed()
    composeRule.onNodeWithText("归纳中…").assertIsNotEnabled()
    composeRule.onNodeWithTag("clipboard-smart-summarize-loading").assertIsDisplayed()
  }

  @Test
  fun clipboardDialog_smartSummarizing_manualCreateButtonRemainsEnabled() {
    var manualCreateCalled = false
    composeRule.setContent {
      ClipboardDialog(
        content = "test content",
        onSummarize = {},
        onManualCreate = { manualCreateCalled = true },
        onDismiss = {},
        isSmartSummarizing = true,
      )
    }
    composeRule.onNodeWithText("手动归纳").assertIsEnabled()
    composeRule.onNodeWithText("手动归纳").performClick()
    assertTrue(manualCreateCalled)
  }

  @Test
  fun clipboardDialog_smartSummarizationMessage_showsErrorText() {
    composeRule.setContent {
      ClipboardDialog(
        content = "test content",
        onSummarize = {},
        onManualCreate = {},
        onDismiss = {},
        smartSummarizationMessage = "归纳出错",
      )
    }
    composeRule.onNodeWithText("归纳出错").assertIsDisplayed()
  }

  @Test
  fun clipboardDialog_pdfOnly_showsFileNameAndTypeLabel() {
    composeRule.setContent {
      ClipboardDialog(
        content = "",
        sourceLabel = "拖拽",
        sourceFileName = "report.pdf",
        sourceDocumentFormat = DocumentFormat.PDF,
        onSummarize = {},
        onManualCreate = {},
        onDismiss = {},
      )
    }
    composeRule.onNodeWithText("report.pdf").assertIsDisplayed()
    composeRule.onNodeWithText("PDF 文档").assertIsDisplayed()
  }
}
