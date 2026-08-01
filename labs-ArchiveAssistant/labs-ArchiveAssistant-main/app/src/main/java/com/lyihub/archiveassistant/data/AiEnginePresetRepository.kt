package com.lyihub.archiveassistant.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.lyihub.archiveassistant.domain.AiEnginePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AiEnginePresetRepository(private val dataStore: DataStore<Preferences>) {
  val presets: Flow<List<AiEnginePreset>> = dataStore.data.map(AiEnginePresetPreferences::decode)

  suspend fun save(presets: List<AiEnginePreset>) {
    dataStore.edit { preferences ->
      AiEnginePresetPreferences.encode(presets, preferences)
    }
  }
}
