package com.lyihub.archiveassistant.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.SixMinistry
import com.lyihub.archiveassistant.domain.Topic
import com.lyihub.archiveassistant.domain.resolveTopicId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDataPreferencesTest {
  @Test
  fun legacyTopicsAndItemsDecode() {
    val topics = listOf(legacyTopic())
    val items = listOf(legacyItem("topic-ai-architecture"))
    val preferences = mutablePreferencesOf()

    AppDataPreferences.encodeTopics(topics, preferences)
    AppDataPreferences.encodeItems(items, preferences)

    assertEquals(topics, AppDataPreferences.decodeTopics(preferences.toPreferences()))
    assertEquals(items, AppDataPreferences.decodeItems(preferences.toPreferences()))
  }

  @Test
  fun tryDecodeTopics_whenCorrupt_distinguishesFromMissingAndValidEmpty() {
    val missing = AppDataPreferences.tryDecodeTopics(preferencesOf())
    val validEmpty =
      AppDataPreferences.tryDecodeTopics(preferencesOf(AppDataPreferences.TopicsKey to "[]"))
    val corrupt =
      AppDataPreferences.tryDecodeTopics(preferencesOf(AppDataPreferences.TopicsKey to "{not-json"))

    assertEquals(AppDataPreferences.DecodeResult.Missing, missing)
    assertEquals(emptyList<Topic>(), (validEmpty as AppDataPreferences.DecodeResult.Valid).value)
    assertTrue(corrupt is AppDataPreferences.DecodeResult.Corrupt)
  }

  @Test
  fun resolveTopicId_knownIdsMapToSelfAndEverythingElseFallsBackToTreasury() {
    assertEquals(
      listOf("officials", "treasury", "rites", "military", "justice", "works"),
      SixMinistry.entries.map { it.id },
    )
    SixMinistry.entries.forEach { ministry ->
      assertEquals(ministry.id, resolveTopicId(ministry.id))
    }

    assertEquals(SixMinistry.TREASURY.id, resolveTopicId("topic-ai-architecture"))
    assertEquals(SixMinistry.TREASURY.id, resolveTopicId("topic-ui-inspiration"))
    assertEquals(SixMinistry.TREASURY.id, resolveTopicId("topic-anthropology-clips"))
    assertEquals(SixMinistry.TREASURY.id, resolveTopicId("topic-hidden-travel"))
    assertEquals(SixMinistry.TREASURY.id, resolveTopicId("topic-user"))
    assertEquals(SixMinistry.TREASURY.id, resolveTopicId("unknown"))
    assertEquals(SixMinistry.TREASURY.id, resolveTopicId(""))
    assertEquals(SixMinistry.TREASURY.id, resolveTopicId(null))
  }

  private fun legacyTopic() =
    Topic(
      id = "topic-ai-architecture",
      title = "AI 架构",
      iconName = "brain",
      iconColor = "#111111",
      updatedAtEpochMillis = 1_715_000_000_100,
    )

  private fun legacyItem(topicId: String) =
    KnowledgeItem(
      id = "item-legacy-1",
      topicId = topicId,
      contentType = ContentType.DOCUMENT,
      title = "Legacy Item",
      summary = "Readable legacy item",
      fullText = "full text",
      sourceUrl = null,
      documentFormat = DocumentFormat.PDF,
      fileName = "legacy.pdf",
      fileSize = 42L,
      createdAtEpochMillis = 1_715_000_000_200,
    )
}
