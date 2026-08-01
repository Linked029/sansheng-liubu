package com.lyihub.archiveassistant.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.Topic
import org.json.JSONArray
import org.json.JSONObject

object AppDataPreferences {
  val TopicsKey = stringPreferencesKey("app_topics_json")
  val ItemsKey = stringPreferencesKey("app_items_json")

  sealed interface DecodeResult<out T> {
    data class Valid<T>(val value: T) : DecodeResult<T>

    data object Missing : DecodeResult<Nothing>

    data class Corrupt(val rawJson: String) : DecodeResult<Nothing>
  }

  fun decodeTopics(preferences: Preferences): List<Topic> {
    return try {
      when (val result = tryDecodeTopics(preferences)) {
        is DecodeResult.Valid -> result.value
        DecodeResult.Missing,
        is DecodeResult.Corrupt -> emptyList()
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  fun tryDecodeTopics(preferences: Preferences): DecodeResult<List<Topic>> {
    val json = preferences[TopicsKey] ?: return DecodeResult.Missing
    return try {
      DecodeResult.Valid(parseTopics(json))
    } catch (_: Exception) {
      DecodeResult.Corrupt(json)
    }
  }

  fun encodeTopics(topics: List<Topic>, preferences: MutablePreferences) {
    val array = JSONArray()
    topics.forEach { topic ->
      array.put(
        JSONObject().apply {
          put("id", topic.id)
          put("title", topic.title)
          put("iconName", topic.iconName)
          put("iconColor", topic.iconColor)
          put("updatedAtEpochMillis", topic.updatedAtEpochMillis)
        }
      )
    }
    preferences[TopicsKey] = array.toString()
  }

  fun decodeItems(preferences: Preferences): List<KnowledgeItem> {
    return try {
      when (val result = tryDecodeItems(preferences)) {
        is DecodeResult.Valid -> result.value
        DecodeResult.Missing,
        is DecodeResult.Corrupt -> emptyList()
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  fun tryDecodeItems(preferences: Preferences): DecodeResult<List<KnowledgeItem>> {
    val json = preferences[ItemsKey] ?: return DecodeResult.Missing
    return try {
      DecodeResult.Valid(parseItems(json))
    } catch (_: Exception) {
      DecodeResult.Corrupt(json)
    }
  }

  fun encodeItems(items: List<KnowledgeItem>, preferences: MutablePreferences) {
    val array = JSONArray()
    items.forEach { item ->
      array.put(
        JSONObject().apply {
          put("id", item.id)
          put("topicId", item.topicId)
          put("contentType", item.contentType.name)
          put("title", item.title)
          put("summary", item.summary)
          put("fullText", item.fullText)
          item.sourceUrl?.let { put("sourceUrl", it) }
          item.imageResName?.let { put("imageResName", it) }
          item.documentFormat?.let { put("documentFormat", it.name) }
          item.fileName?.let { put("fileName", it) }
          item.fileSize?.let { put("fileSize", it) }
          put("createdAtEpochMillis", item.createdAtEpochMillis)
        }
      )
    }
    preferences[ItemsKey] = array.toString()
  }

  private fun parseTopics(json: String): List<Topic> {
    val array = JSONArray(json)
    return (0 until array.length()).map { i ->
      val obj = array.getJSONObject(i)
      Topic(
        id = obj.getString("id"),
        title = obj.getString("title"),
        iconName = obj.getString("iconName"),
        iconColor = obj.getString("iconColor"),
        updatedAtEpochMillis = obj.getLong("updatedAtEpochMillis"),
      )
    }
  }

  private fun parseItems(json: String): List<KnowledgeItem> {
    val array = JSONArray(json)
    return (0 until array.length()).map { i ->
      val obj = array.getJSONObject(i)
      KnowledgeItem(
        id = obj.getString("id"),
        topicId = obj.getString("topicId"),
        contentType = ContentType.valueOf(obj.getString("contentType")),
        title = obj.getString("title"),
        summary = obj.optString("summary", ""),
        fullText = obj.optString("fullText", ""),
        sourceUrl = obj.optString("sourceUrl", "").takeIf { it.isNotEmpty() },
        imageResName = obj.optString("imageResName", "").takeIf { it.isNotEmpty() },
        documentFormat =
          obj
            .optString("documentFormat", "")
            .takeIf { it.isNotEmpty() }
            ?.let { runCatching { DocumentFormat.valueOf(it) }.getOrNull() },
        fileName = obj.optString("fileName", "").takeIf { it.isNotEmpty() },
        fileSize =
          if (obj.has("fileSize") && !obj.isNull("fileSize")) obj.getLong("fileSize") else null,
        createdAtEpochMillis = obj.getLong("createdAtEpochMillis"),
      )
    }
  }
}
