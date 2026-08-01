package com.lyihub.archiveassistant.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lyihub.archiveassistant.domain.AiEnginePreset
import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType

object AiEnginePresetPreferences {
  val PresetsKey = stringPreferencesKey("ai_engine_presets")

  private val presetFields =
    listOf(
      "name" to AiEnginePreset::name,
      "engineType" to AiEnginePreset::engineType,
      "baseUrl" to AiEnginePreset::baseUrl,
      "modelName" to AiEnginePreset::modelName,
      "apiKey" to AiEnginePreset::apiKey,
      "localEndpoint" to AiEnginePreset::localEndpoint,
    )

  fun decode(preferences: Preferences): List<AiEnginePreset> {
    val json = preferences[PresetsKey] ?: return emptyList()
    return parsePresetArray(json)
  }

  fun encode(presets: List<AiEnginePreset>, preferences: MutablePreferences) {
    preferences[PresetsKey] = encodePresetArray(presets)
  }

  @Suppress("DEPRECATION")
  fun presetFromCurrentSettings(
    name: String,
    settings: AiEngineSettings,
    rawApiKey: String,
  ): AiEnginePreset =
    AiEnginePreset(
      name = name,
      engineType = settings.engineType,
      baseUrl = settings.baseUrl,
      modelName = settings.modelName,
      apiKey = rawApiKey,
      localEndpoint = settings.localEndpoint,
    )

  @Suppress("DEPRECATION")
  fun AiEnginePreset.toSettings(): AiEngineSettings =
    AiEngineSettings(
      engineType = engineType,
      baseUrl = baseUrl,
      modelName = modelName,
      apiKeyAlias = name,
      apiKey = apiKey,
      localEndpoint = localEndpoint,
    )

  private fun encodePresetArray(presets: List<AiEnginePreset>): String {
    if (presets.isEmpty()) return "[]"
    val entries =
      presets.joinToString(",\n") { preset ->
        presetFields.joinToString(", ", "{", "}") { (key, property) ->
          "\"$key\": ${jsonString(property.get(preset).toString())}"
        }
      }
    return "[\n$entries\n]"
  }

  private fun parsePresetArray(json: String): List<AiEnginePreset> {
    val presets = mutableListOf<AiEnginePreset>()
    var index = 0
    skipWhitespace(json, index).also { index = it.second }
    if (index >= json.length || json[index] != '[') return emptyList()
    index++
    while (index < json.length) {
      skipWhitespace(json, index).also { index = it.second }
      if (index < json.length && json[index] == ']') {
        index++
        break
      }
      val (obj, nextIndex) = parseObject(json, index)
      presets.add(parsePreset(obj))
      index = nextIndex
      skipWhitespace(json, index).also { index = it.second }
      if (index < json.length && json[index] == ',') {
        index++
      }
    }
    return presets
  }

  private fun parsePreset(obj: Map<String, String>): AiEnginePreset {
    val engineType =
      obj["engineType"]?.let { name -> AiEngineType.entries.firstOrNull { it.name == name } }
        ?: AiEngineType.OPENAI_COMPATIBLE
    return AiEnginePreset(
      name = obj["name"] ?: "",
      engineType = engineType,
      baseUrl = obj["baseUrl"] ?: "",
      modelName = obj["modelName"] ?: "",
      apiKey = obj["apiKey"] ?: "",
      localEndpoint = obj["localEndpoint"] ?: "",
    )
  }

  private fun parseObject(json: String, start: Int): Pair<Map<String, String>, Int> {
    var index = start
    skipWhitespace(json, index).also { index = it.second }
    require(index < json.length && json[index] == '{') { "Expected '{' at $index" }
    index++
    val map = mutableMapOf<String, String>()
    while (index < json.length) {
      skipWhitespace(json, index).also { index = it.second }
      if (index < json.length && json[index] == '}') {
        index++
        break
      }
      val (key, keyEnd) = parseString(json, index)
      index = keyEnd
      skipWhitespace(json, index).also { index = it.second }
      require(index < json.length && json[index] == ':') { "Expected ':' after key at $index" }
      index++
      skipWhitespace(json, index).also { index = it.second }
      val (value, valueEnd) = parseString(json, index)
      index = valueEnd
      map[key] = value
      skipWhitespace(json, index).also { index = it.second }
      if (index < json.length && json[index] == ',') {
        index++
      }
    }
    return map to index
  }

  private fun parseString(json: String, start: Int): Pair<String, Int> {
    var index = start
    skipWhitespace(json, index).also { index = it.second }
    require(index < json.length && json[index] == '"') { "Expected '\"' at $index" }
    index++
    val builder = StringBuilder()
    while (index < json.length) {
      when (val char = json[index]) {
        '"' -> {
          index++
          return builder.toString() to index
        }
        '\\' -> {
          index++
          if (index < json.length) {
            when (json[index]) {
              '"',
              '\\',
              '/' -> builder.append(json[index])
              'b' -> builder.append('\b')
              'n' -> builder.append('\n')
              'r' -> builder.append('\r')
              't' -> builder.append('\t')
              else -> builder.append(json[index])
            }
            index++
          }
        }
        else -> {
          builder.append(char)
          index++
        }
      }
    }
    return builder.toString() to index
  }

  private fun skipWhitespace(json: String, start: Int): Pair<Unit, Int> {
    var index = start
    while (index < json.length && json[index].isWhitespace()) {
      index++
    }
    return Unit to index
  }

  private fun jsonString(value: String): String {
    val escaped =
      value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\b", "\\b")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "\"$escaped\""
  }
}
