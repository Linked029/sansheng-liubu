package com.lyihub.archiveassistant.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import com.lyihub.archiveassistant.data.AiEnginePresetPreferences.presetFromCurrentSettings
import com.lyihub.archiveassistant.data.AiEnginePresetPreferences.toSettings
import com.lyihub.archiveassistant.domain.AiEnginePreset
import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AiEnginePresetPreferencesTest {
  @Test
  fun decode_whenPreferencesAreEmpty_returnsEmptyList() {
    val presets = AiEnginePresetPreferences.decode(preferencesOf())

    assertEquals(emptyList<AiEnginePreset>(), presets)
  }

  @Test
  fun encodeAndDecode_roundTripsPresets() {
    val presets =
      listOf(
        AiEnginePreset(
          name = "OpenAI",
          engineType = AiEngineType.OPENAI_COMPATIBLE,
          baseUrl = "https://api.openai.com/v1",
          modelName = "gpt-4o-mini",
          apiKey = "sk-test",
          localEndpoint = "http://127.0.0.1:11434",
        ),
        AiEnginePreset(
          name = "Local Ollama",
          engineType = AiEngineType.LOCAL_MODEL,
          baseUrl = "",
          modelName = "llama3",
          apiKey = "",
          localEndpoint = "http://127.0.0.1:11434",
        ),
      )
    val preferences = mutablePreferencesOf()

    AiEnginePresetPreferences.encode(presets, preferences)
    val decoded = AiEnginePresetPreferences.decode(preferences.toPreferences())

    assertEquals(presets, decoded)
  }

  @Test
  fun decode_withUnknownEngineType_fallsBackToOpenAiCompatible() {
    val json =
      """
      [{
          "name": "Bad Engine",
          "engineType": "UNKNOWN_TYPE",
          "baseUrl": "https://example.com",
          "modelName": "model",
          "apiKey": "key",
          "localEndpoint": "http://localhost"
      }]
      """
        .trimIndent()
    val preferences = preferencesOf(AiEnginePresetPreferences.PresetsKey to json)

    val decoded = AiEnginePresetPreferences.decode(preferences)

    assertEquals(AiEngineType.OPENAI_COMPATIBLE, decoded.first().engineType)
  }

  @Test
  fun presetFromCurrentSettings_capturesAllFields() {
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.ANTHROPIC,
        baseUrl = "https://api.anthropic.com",
        modelName = "claude-3",
        apiKeyAlias = "work",
        localEndpoint = "http://127.0.0.1:1234",
      )

    val preset =
      AiEnginePresetPreferences.presetFromCurrentSettings(
        name = "Anthropic Work",
        settings = settings,
        rawApiKey = "sk-secret",
      )

    assertEquals(
      AiEnginePreset(
        name = "Anthropic Work",
        engineType = AiEngineType.ANTHROPIC,
        baseUrl = "https://api.anthropic.com",
        modelName = "claude-3",
        apiKey = "sk-secret",
        localEndpoint = "http://127.0.0.1:1234",
      ),
      preset,
    )
  }

  @Test
  fun presetToSettings_mapsFieldsCorrectly() {
    val preset =
      AiEnginePreset(
        name = "Gemini",
        engineType = AiEngineType.GEMINI,
        baseUrl = "https://generativelanguage.googleapis.com",
        modelName = "gemini-1.5-flash",
        apiKey = "api-key",
        localEndpoint = "http://127.0.0.1:11434",
      )

    val settings = preset.toSettings()

    assertEquals(
      AiEngineSettings(
        engineType = AiEngineType.GEMINI,
        baseUrl = "https://generativelanguage.googleapis.com",
        modelName = "gemini-1.5-flash",
        apiKeyAlias = "Gemini",
        apiKey = "api-key",
        localEndpoint = "http://127.0.0.1:11434",
      ),
      settings,
    )
  }

  @Test
  fun repository_saveAndRead_loadsPersistedPresets() =
    kotlinx.coroutines.runBlocking {
      val repository = AiEnginePresetRepository(FakePreferencesDataStore())
      val presets =
        listOf(
          AiEnginePreset(
            name = "Preset A",
            engineType = AiEngineType.OPENAI_RESPONSES,
            baseUrl = "https://api.openai.com",
            modelName = "gpt-4o",
            apiKey = "sk-abc",
            localEndpoint = "http://127.0.0.1:11434",
          )
        )

      repository.save(presets)

      assertEquals(presets, repository.presets.first())
    }

  private class FakePreferencesDataStore :
    androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
    private val flow = kotlinx.coroutines.flow.MutableStateFlow(preferencesOf())

    override val data:
      kotlinx.coroutines.flow.Flow<androidx.datastore.preferences.core.Preferences> =
      flow

    override suspend fun updateData(
      transform:
        suspend (
          t: androidx.datastore.preferences.core.Preferences
        ) -> androidx.datastore.preferences.core.Preferences
    ): androidx.datastore.preferences.core.Preferences {
      val updated = transform(flow.value)
      flow.value = updated
      return updated
    }
  }
}
