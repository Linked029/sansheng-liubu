package com.lyihub.archiveassistant.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import com.lyihub.archiveassistant.domain.InferenceBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AiEngineSettingsPreferencesTest {
  @Test
  fun decode_whenPreferencesAreEmpty_returnsDefaults() {
    val settings = AiEngineSettingsPreferences.decode(preferencesOf())

    assertEquals(AiEngineSettings(), settings)
  }

  @Test
  fun encodeAndDecode_roundTripsSettingsWithoutRawApiKey() {
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.LOCAL_MODEL,
        baseUrl = "https://api.example.test/v2",
        modelName = "knowledge-router",
        apiKeyAlias = "work-profile",
        localEndpoint = "http://127.0.0.1:1234",
      )
    val preferences = mutablePreferencesOf()

    AiEngineSettingsPreferences.encode(settings, preferences)
    val decoded = AiEngineSettingsPreferences.decode(preferences.toPreferences())

    assertEquals(settings, decoded)
    assertEquals(
      null,
      preferences.asMap().keys.firstOrNull { it.name.contains("raw", ignoreCase = true) },
    )
    assertEquals(
      null,
      preferences.asMap().keys.firstOrNull { it.name.contains("secret", ignoreCase = true) },
    )
  }

  @Test
  fun decode_whenEngineTypeIsUnknown_usesDefaultEngineType() {
    val preferences = preferencesOf(AiEngineSettingsPreferences.EngineTypeKey to "UNKNOWN_ENGINE")

    val settings = AiEngineSettingsPreferences.decode(preferences)

    assertEquals(AiEngineType.OPENAI_COMPATIBLE, settings.engineType)
  }

  @Test
  fun repository_saveAndRead_loadsPersistedSettings() = runBlocking {
    val repository = AiEngineSettingsRepository(FakePreferencesDataStore())
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.LOCAL_MODEL,
        baseUrl = "https://api.example.test/v3",
        modelName = "offline-curator",
        apiKeyAlias = "team-alias",
        localEndpoint = "http://127.0.0.1:4321",
      )

    repository.save(settings)

    assertEquals(settings, repository.settings.first())
  }

  @Test
  fun newFieldsRoundTrip() {
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.LOCAL_MODEL,
        baseUrl = "http://localhost:8080",
        modelName = "gemma-4-E4B",
        apiKeyAlias = "test",
        localEndpoint = "http://127.0.0.1:11434",
        localModelId = "gemma-4-E4B-it-litert-lm",
        localBackendPreference = InferenceBackend.NPU,
      )
    val preferences = mutablePreferencesOf()

    AiEngineSettingsPreferences.encode(settings, preferences)
    val decoded = AiEngineSettingsPreferences.decode(preferences.toPreferences())

    assertEquals("gemma-4-E4B-it-litert-lm", decoded.localModelId)
    assertEquals(InferenceBackend.NPU, decoded.localBackendPreference)
    assertEquals(settings, decoded)
  }

  @Test
  @Suppress("DEPRECATION")
  fun legacyConfigMigration() {
    // Write only old keys — no ai_local_model_id or ai_local_backend_pref
    val preferences =
      preferencesOf(
        AiEngineSettingsPreferences.EngineTypeKey to AiEngineType.LOCAL_MODEL.name,
        AiEngineSettingsPreferences.LocalEndpointKey to "http://old-server:11434",
      )

    val decoded = AiEngineSettingsPreferences.decode(preferences)

    assertEquals(AiEngineType.LOCAL_MODEL, decoded.engineType)
    assertEquals("http://old-server:11434", decoded.localEndpoint)
    assertEquals(null, decoded.localModelId)
    assertEquals(InferenceBackend.NPU, decoded.localBackendPreference)
  }

  private class FakePreferencesDataStore : DataStore<Preferences> {
    private val flow = MutableStateFlow(preferencesOf())

    override val data: Flow<Preferences> = flow

    override suspend fun updateData(
      transform: suspend (t: Preferences) -> Preferences
    ): Preferences {
      val updated = transform(flow.value)
      flow.value = updated
      return updated
    }
  }
}
