package com.lyihub.archiveassistant.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.Topic
import kotlinx.coroutines.flow.firstOrNull

class AppDataRepository(private val dataStore: DataStore<Preferences>) {
  data class Snapshot(
    val topics: AppDataPreferences.DecodeResult<List<Topic>>,
    val items: AppDataPreferences.DecodeResult<List<KnowledgeItem>>,
  )

  suspend fun loadTopics(): List<Topic> {
    val preferences = dataStore.data.firstOrNull() ?: return emptyList()
    return AppDataPreferences.decodeTopics(preferences)
  }

  suspend fun loadItems(): List<KnowledgeItem> {
    val preferences = dataStore.data.firstOrNull() ?: return emptyList()
    return AppDataPreferences.decodeItems(preferences)
  }

  suspend fun loadSnapshot(): Snapshot? {
    val preferences = dataStore.data.firstOrNull() ?: return null
    return Snapshot(
      topics = AppDataPreferences.tryDecodeTopics(preferences),
      items = AppDataPreferences.tryDecodeItems(preferences),
    )
  }

  suspend fun saveAll(topics: List<Topic>, items: List<KnowledgeItem>) {
    dataStore.edit { preferences ->
      AppDataPreferences.encodeTopics(topics, preferences)
      AppDataPreferences.encodeItems(items, preferences)
    }
  }
}
