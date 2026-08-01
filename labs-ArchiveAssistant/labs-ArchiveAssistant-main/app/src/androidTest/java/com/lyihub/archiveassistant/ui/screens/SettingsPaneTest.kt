package com.lyihub.archiveassistant.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.lyihub.archiveassistant.data.AiEndpointLatencyResult
import com.lyihub.archiveassistant.domain.AiEnginePreset
import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import com.lyihub.archiveassistant.domain.BenchResult
import com.lyihub.archiveassistant.domain.InferenceBackend
import com.lyihub.archiveassistant.domain.LocalModelState
import com.lyihub.archiveassistant.domain.LocalModelStatus
import com.lyihub.archiveassistant.ui.theme.ArchiveAssistantTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsPaneTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun settingsPane_displaysGroupLabelAndEngineSelector() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        SettingsPane(
          aiSettings = AiEngineSettings(engineType = AiEngineType.OPENAI_COMPATIBLE),
          onAiSettingsChanged = {},
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("settings-pane").assertIsDisplayed()
    composeTestRule.onNodeWithText("AI 推理引擎配置").assertIsDisplayed()
    composeTestRule.onNodeWithTag("engine-type-selector").assertIsDisplayed()
    composeTestRule.onNodeWithTag("preset-selector").assertIsDisplayed()
  }

  @Test
  fun settingsPane_cloudMode_showsCloudFieldsAndHidesLocalFields() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        SettingsPane(
          aiSettings = AiEngineSettings(engineType = AiEngineType.OPENAI_COMPATIBLE),
          onAiSettingsChanged = {},
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("cloud-base-url-input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("api-key-input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("cloud-model-input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("local-endpoint-input").assertDoesNotExist()
    composeTestRule.onNodeWithTag("local-model-panel").assertDoesNotExist()
  }

  @Test
  fun settingsPane_localMode_showsLocalFieldsAndHidesCloudFields() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        SettingsPane(
          aiSettings = AiEngineSettings(engineType = AiEngineType.LOCAL_MODEL),
          onAiSettingsChanged = {},
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("local-endpoint-input").assertDoesNotExist()
    composeTestRule.onNodeWithTag("local-model-panel").assertIsDisplayed()
    composeTestRule.onNodeWithTag("model-status-text").assertTextContains("未下载")
    composeTestRule.onNodeWithTag("cloud-base-url-input").assertDoesNotExist()
    composeTestRule.onNodeWithTag("api-key-input").assertDoesNotExist()
    composeTestRule.onNodeWithTag("cloud-model-input").assertDoesNotExist()
  }

  @Test
  fun settingsPane_switchEngineType_updatesVisibleFields() {
    var currentSettings = AiEngineSettings(engineType = AiEngineType.OPENAI_COMPATIBLE)

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        SettingsPane(
          aiSettings = currentSettings,
          onAiSettingsChanged = { currentSettings = it },
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("cloud-base-url-input").assertIsDisplayed()

    composeTestRule.onNodeWithTag("engine-type-selector").performClick()
    composeTestRule.onNodeWithText("本地模型").performClick()

    composeTestRule.waitForIdle()
    assertEquals(AiEngineType.LOCAL_MODEL, currentSettings.engineType)
    composeTestRule.onNodeWithTag("local-endpoint-input").assertDoesNotExist()
    composeTestRule.onNodeWithTag("cloud-base-url-input").assertDoesNotExist()
  }

  @Test
  fun settingsPane_apiKeyInput_isMasked() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        SettingsPane(
          aiSettings = AiEngineSettings(engineType = AiEngineType.OPENAI_COMPATIBLE),
          onAiSettingsChanged = {},
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("api-key-input").performTextInput("sk-test-secret")
    composeTestRule.onNodeWithTag("api-key-input").assertTextEquals("•••••••••••••")
  }

  @Test
  fun settingsPane_baseUrlChange_triggersCallback() {
    var updatedSettings: AiEngineSettings? = null

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        SettingsPane(
          aiSettings =
            AiEngineSettings(
              engineType = AiEngineType.OPENAI_COMPATIBLE,
              baseUrl = "https://api.example.com/v1",
            ),
          onAiSettingsChanged = { updatedSettings = it },
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("cloud-base-url-input").performTextInput("/chat")
    composeTestRule.waitForIdle()

    assertEquals("https://api.example.com/v1/chat", updatedSettings?.baseUrl)
  }

  @Test
  fun settingsPane_localMode_hidesLocalEndpoint() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        SettingsPane(
          aiSettings =
            AiEngineSettings(
              engineType = AiEngineType.LOCAL_MODEL,
              localEndpoint = "http://127.0.0.1:11434",
            ),
          onAiSettingsChanged = {},
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("local-endpoint-input").assertDoesNotExist()
  }

  @Test
  fun settingsPane_displaysTestLatencyButton() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        SettingsPane(
          aiSettings = AiEngineSettings(engineType = AiEngineType.OPENAI_COMPATIBLE),
          onAiSettingsChanged = {},
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("test-api-latency-button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("test-api-latency-button").assertTextEquals("测试 API 延迟")
  }

  @Test
  fun settingsPane_testLatencySuccess_showsLatencyResult() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        SettingsPane(
          aiSettings = AiEngineSettings(engineType = AiEngineType.OPENAI_COMPATIBLE),
          onAiSettingsChanged = {},
          onBack = {},
          testLatency = { _, _ -> AiEndpointLatencyResult.Success(123) },
        )
      }
    }

    composeTestRule.onNodeWithTag("test-api-latency-button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("api-latency-result").assertIsDisplayed()
    composeTestRule.onNodeWithTag("api-latency-result").assertTextEquals("延迟：123 ms")
  }

  @Test
  fun settingsPane_testLatencyFailure_showsFailureResult() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        SettingsPane(
          aiSettings = AiEngineSettings(engineType = AiEngineType.OPENAI_COMPATIBLE),
          onAiSettingsChanged = {},
          onBack = {},
          testLatency = { _, _ -> AiEndpointLatencyResult.Failure("connection refused") },
        )
      }
    }

    composeTestRule.onNodeWithTag("test-api-latency-button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("api-latency-result").assertIsDisplayed()
    composeTestRule.onNodeWithTag("api-latency-result").assertTextEquals("测试失败：connection refused")
  }

  @Test
  fun settingsPane_localModel_hidesTestLatencyButton() {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        SettingsPane(
          aiSettings = AiEngineSettings(engineType = AiEngineType.LOCAL_MODEL),
          onAiSettingsChanged = {},
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("test-api-latency-button").assertDoesNotExist()
  }

  @Test
  fun settingsPane_selectPreset_appliesPresetSettingsAndApiKey() {
    var currentSettings by
      mutableStateOf(AiEngineSettings(engineType = AiEngineType.OPENAI_COMPATIBLE))
    val preset =
      AiEnginePreset(
        name = "Work OpenAI",
        engineType = AiEngineType.OPENAI_RESPONSES,
        baseUrl = "https://work.openai.com",
        modelName = "gpt-4o",
        apiKey = "sk-work-secret",
        localEndpoint = "http://127.0.0.1:11434",
      )

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        SettingsPane(
          aiSettings = currentSettings,
          onAiSettingsChanged = { currentSettings = it },
          onBack = {},
          presets = listOf(preset),
        )
      }
    }

    composeTestRule.onNodeWithTag("preset-selector").performClick()
    composeTestRule.onNodeWithText("Work OpenAI").performClick()
    composeTestRule.waitForIdle()

    assertEquals(AiEngineType.OPENAI_RESPONSES, currentSettings.engineType)
    assertEquals("https://work.openai.com", currentSettings.baseUrl)
    assertEquals("gpt-4o", currentSettings.modelName)
    assertEquals("sk-work-secret", currentSettings.apiKey)
    composeTestRule.onNodeWithTag("api-key-input").assertTextEquals("•••••••••••••")
  }

  @Test
  fun notDownloadedShowsDownloadButton() {
    var downloadClicked = false
    var chooseClicked = false

    setLocalModelContent(
      localModelState = LocalModelState(LocalModelStatus.NOT_DOWNLOADED),
      onDownloadModel = { downloadClicked = true },
      onChooseModelFile = { chooseClicked = true },
    )

    composeTestRule.onNodeWithTag("download-model-button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("choose-model-file-button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("download-model-button").performClick()
    composeTestRule.onNodeWithTag("choose-model-file-button").performClick()

    assertTrue(downloadClicked)
    assertTrue(chooseClicked)
  }

  @Test
  fun downloadingShowsProgressAndCancel() {
    var cancelClicked = false

    setLocalModelContent(
      localModelState =
        LocalModelState(
          status = LocalModelStatus.DOWNLOADING,
          downloadProgress = 0.5f,
        ),
      onCancelDownload = { cancelClicked = true },
    )

    composeTestRule.onNodeWithTag("download-progress-bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("model-busy-text").assertTextContains("正在下载模型，请稍候")
    composeTestRule.onNodeWithTag("cancel-download-button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("cancel-download-button").performClick()

    assertTrue(cancelClicked)
  }

  @Test
  fun importingModelShowsBusyPromptWithoutRepeatActions() {
    setLocalModelContent(
      localModelState =
        LocalModelState(
          status = LocalModelStatus.DOWNLOADING,
          totalBytes = 100,
        )
    )

    composeTestRule.onNodeWithTag("download-progress-bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("model-busy-text").assertTextContains("正在导入并校验模型文件，请稍候")
    composeTestRule.onNodeWithTag("download-model-button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("choose-model-file-button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("cancel-download-button").assertDoesNotExist()
  }

  @Test
  fun downloadedShowsStartButton() {
    var startClicked = false

    setLocalModelContent(
      localModelState = LocalModelState(LocalModelStatus.DOWNLOADED),
      onStartModel = { startClicked = true },
    )

    composeTestRule.onNodeWithTag("start-model-button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("start-model-button").performClick()

    assertTrue(startClicked)
  }

  @Test
  fun readyShowsStopButton() {
    var stopClicked = false

    setLocalModelContent(
      localModelState = LocalModelState(LocalModelStatus.READY),
      onStopModel = { stopClicked = true },
    )

    composeTestRule.onNodeWithTag("stop-model-button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("stop-model-button").performClick()

    assertTrue(stopClicked)
  }

  @Test
  fun readyShowsBenchmarkButton() {
    var benchmarkClicked = false

    setLocalModelContent(
      localModelState = LocalModelState(LocalModelStatus.READY),
      onRunBenchmark = { benchmarkClicked = true },
    )

    composeTestRule.onNodeWithTag("benchmark-button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("benchmark-button").performClick()

    assertTrue(benchmarkClicked)
  }

  @Test
  fun errorShowsRetryButton() {
    var downloadClicked = false
    var chooseClicked = false

    setLocalModelContent(
      localModelState =
        LocalModelState(
          status = LocalModelStatus.ERROR,
          errorMessage = "test error",
        ),
      onDownloadModel = { downloadClicked = true },
      onChooseModelFile = { chooseClicked = true },
    )

    composeTestRule.onNodeWithTag("model-error-text").assertTextContains("test error")
    composeTestRule.onNodeWithTag("download-model-button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("choose-model-file-button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("download-model-button").performClick()
    composeTestRule.onNodeWithTag("choose-model-file-button").performClick()

    assertTrue(downloadClicked)
    assertTrue(chooseClicked)
  }

  @Test
  fun benchmarkResultDisplayed() {
    setLocalModelContent(
      localModelState = LocalModelState(LocalModelStatus.READY),
      benchmarkResult =
        BenchResult(
          promptTokens = 128,
          generateTokens = 64,
          timeToFirstTokenMs = 250L,
          prefillTokensPerSecond = 1000f,
          decodeTokensPerSecond = 50f,
          backend = InferenceBackend.GPU,
        ),
    )

    composeTestRule
      .onNodeWithTag("benchmark-result-text")
      .assertTextContains(
        "预填充: 1000.0 tk/s (128 tokens) | 解码: 50.0 tk/s (64 tokens) | TTFT: 250ms | 后端: GPU"
      )
  }

  @Test
  fun backendPreferenceChange() {
    var selectedBackend: InferenceBackend? = null

    setLocalModelContent(
      localModelState = LocalModelState(LocalModelStatus.NOT_DOWNLOADED),
      onBackendPreferenceChange = { selectedBackend = it },
    )

    composeTestRule.onNodeWithTag("backend-preference-selector").performClick()
    composeTestRule.onNodeWithText("GPU").performClick()
    composeTestRule.waitForIdle()

    assertEquals(InferenceBackend.GPU, selectedBackend)
  }

  @Test
  fun settingsPane_savePreset_addsPresetAndCallsCallback() {
    var currentSettings by
      mutableStateOf(
        AiEngineSettings(
          engineType = AiEngineType.ANTHROPIC,
          baseUrl = "https://api.anthropic.com",
          modelName = "claude-3-opus",
        )
      )
    var savedPresets: List<AiEnginePreset>? = null

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        SettingsPane(
          aiSettings = currentSettings,
          onAiSettingsChanged = { currentSettings = it },
          onBack = {},
          presets = emptyList(),
          onPresetsChanged = { savedPresets = it },
        )
      }
    }

    composeTestRule.onNodeWithTag("api-key-input").performTextInput("sk-anthropic")
    composeTestRule.onNodeWithTag("preset-selector").performClick()
    composeTestRule.onNodeWithText("保存当前为预设").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("预设名称").performTextInput("Anthropic")
    composeTestRule.onNodeWithText("保存").performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, savedPresets?.size)
    val preset = savedPresets?.first()
    assertEquals("Anthropic", preset?.name)
    assertEquals(AiEngineType.ANTHROPIC, preset?.engineType)
    assertEquals("https://api.anthropic.com", preset?.baseUrl)
    assertEquals("claude-3-opus", preset?.modelName)
    assertEquals("sk-anthropic", preset?.apiKey)
  }

  @Test
  fun settingsPane_deletePreset_removesPresetAndCallsCallback() {
    var currentSettings by
      mutableStateOf(AiEngineSettings(engineType = AiEngineType.OPENAI_COMPATIBLE))
    var savedPresets =
      listOf(
        AiEnginePreset(
          name = "Preset One",
          engineType = AiEngineType.OPENAI_COMPATIBLE,
          baseUrl = "https://api.example.com",
          modelName = "model",
          apiKey = "key",
          localEndpoint = "http://127.0.0.1:11434",
        )
      )

    composeTestRule.setContent {
      ArchiveAssistantTheme {
        SettingsPane(
          aiSettings = currentSettings,
          onAiSettingsChanged = { currentSettings = it },
          onBack = {},
          presets = savedPresets,
          onPresetsChanged = { savedPresets = it },
        )
      }
    }

    composeTestRule.onNodeWithTag("preset-selector").performClick()
    composeTestRule.onNodeWithText("Preset One").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("preset-selector").performClick()
    composeTestRule.onNodeWithText("删除当前预设").performClick()
    composeTestRule.waitForIdle()

    assertEquals(emptyList<AiEnginePreset>(), savedPresets)
  }

  private fun setLocalModelContent(
    localModelState: LocalModelState,
    benchmarkResult: BenchResult? = null,
    onDownloadModel: () -> Unit = {},
    onChooseModelFile: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onStartModel: () -> Unit = {},
    onStopModel: () -> Unit = {},
    onRunBenchmark: () -> Unit = {},
    onBackendPreferenceChange: (InferenceBackend) -> Unit = {},
  ) {
    composeTestRule.setContent {
      ArchiveAssistantTheme {
        SettingsPane(
          aiSettings = AiEngineSettings(engineType = AiEngineType.LOCAL_MODEL),
          onAiSettingsChanged = {},
          onBack = {},
          localModelState = localModelState,
          benchmarkResult = benchmarkResult,
          onDownloadModel = onDownloadModel,
          onChooseModelFile = onChooseModelFile,
          onCancelDownload = onCancelDownload,
          onStartModel = onStartModel,
          onStopModel = onStopModel,
          onRunBenchmark = onRunBenchmark,
          onBackendPreferenceChange = onBackendPreferenceChange,
        )
      }
    }
  }
}
