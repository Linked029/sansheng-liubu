package com.lyihub.archiveassistant.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.lyihub.archiveassistant.domain.AiEngineSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AiEngineSettingsRepository(private val dataStore: DataStore<Preferences>) {
  val settings: Flow<AiEngineSettings> = dataStore.data.map(AiEngineSettingsPreferences::decode)

  suspend fun save(settings: AiEngineSettings) {
    dataStore.edit { preferences ->
      AiEngineSettingsPreferences.encode(settings, preferences)
    }
  }
}
