package com.audiobookreader.tts

import android.content.Context
import com.audiobookreader.data.ModelCatalog
import com.audiobookreader.data.TtsModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class EdgeVoiceRepository(context: Context) {
    private val client = EdgeTtsClient()
    private val preferences = context.applicationContext.getSharedPreferences("bookreader-edge", Context.MODE_PRIVATE)

    suspend fun load(): List<TtsModelSpec> = withContext(Dispatchers.IO) {
        runCatching { client.fetchVoices() }
            .onSuccess { save(it) }
            .getOrElse { cached() }
    }

    fun client(): EdgeTtsClient = client

    fun cachedVoices(): List<TtsModelSpec> = cached()

    private fun save(models: List<TtsModelSpec>) {
        val array = JSONArray()
        models.forEach { model ->
            array.put(JSONObject().apply {
                put("id", model.id)
                put("name", model.name)
                put("language", model.language)
                put("edgeVoice", model.edgeVoice)
            })
        }
        preferences.edit().putString(KEY_VOICES, array.toString()).apply()
    }

    private fun cached(): List<TtsModelSpec> = runCatching {
        val array = JSONArray(preferences.getString(KEY_VOICES, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            val name = item.optString("name")
            val language = item.optString("language")
            val voice = item.optString("edgeVoice")
            if (id.isBlank() || name.isBlank() || language.isBlank() || voice.isBlank()) null
            else ModelCatalog.edgeVoice(voice, name.removePrefix("Edge · "), language)
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    companion object { private const val KEY_VOICES = "voices" }
}
