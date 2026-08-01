package com.lyihub.archiveassistant.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import com.lyihub.archiveassistant.domain.InferenceBackend

object AiEngineSettingsPreferences {
  val EngineTypeKey = stringPreferencesKey("ai_engine_type")
  val BaseUrlKey = stringPreferencesKey("ai_base_url")
  val ModelNameKey = stringPreferencesKey("ai_model_name")
  val ApiKeyAliasKey = stringPreferencesKey("ai_api_key_alias")
  val ApiKeyKey = stringPreferencesKey("ai_api_key")
  val LocalEndpointKey = stringPreferencesKey("ai_local_endpoint")
  val LocalModelIdKey = stringPreferencesKey("ai_local_model_id")
  val LocalBackendPrefKey = stringPreferencesKey("ai_local_backend_pref")

  @Suppress("DEPRECATION")
  fun decode(preferences: Preferences): AiEngineSettings {
    val defaults = AiEngineSettings()
    return AiEngineSettings(
      engineType = preferences[EngineTypeKey]?.let(::decodeEngineType) ?: defaults.engineType,
      baseUrl = preferences[BaseUrlKey] ?: defaults.baseUrl,
      modelName = preferences[ModelNameKey] ?: defaults.modelName,
      apiKeyAlias = preferences[ApiKeyAliasKey] ?: defaults.apiKeyAlias,
      apiKey = preferences[ApiKeyKey] ?: defaults.apiKey,
      localEndpoint = preferences[LocalEndpointKey] ?: defaults.localEndpoint,
      localModelId = preferences[LocalModelIdKey] ?: defaults.localModelId,
      localBackendPreference =
        preferences[LocalBackendPrefKey]?.let { decodeBackend(it) }
          ?: defaults.localBackendPreference,
    )
  }

  @Suppress("DEPRECATION")
  fun encode(settings: AiEngineSettings, preferences: MutablePreferences) {
    preferences[EngineTypeKey] = settings.engineType.name
    preferences[BaseUrlKey] = settings.baseUrl
    preferences[ModelNameKey] = settings.modelName
    preferences[ApiKeyAliasKey] = settings.apiKeyAlias
    preferences[ApiKeyKey] = settings.apiKey
    preferences[LocalEndpointKey] = settings.localEndpoint
    settings.localModelId?.let { preferences[LocalModelIdKey] = it }
    preferences[LocalBackendPrefKey] = settings.localBackendPreference.name
  }

  private fun decodeEngineType(value: String): AiEngineType =
    AiEngineType.entries.firstOrNull { it.name == value } ?: AiEngineSettings().engineType

  private fun decodeBackend(value: String): InferenceBackend =
    InferenceBackend.entries.firstOrNull { it.name == value } ?: InferenceBackend.NPU
}
