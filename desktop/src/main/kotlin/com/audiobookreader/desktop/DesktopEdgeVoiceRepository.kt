package com.audiobookreader.desktop

import com.audiobookreader.data.ModelCatalog
import com.audiobookreader.data.TtsModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class DesktopEdgeVoiceRepository {
    suspend fun load(): List<TtsModelSpec> = withContext(Dispatchers.IO) {
        val connection = URL(VOICE_LIST_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "BookReader/0.1")
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Edge voice list failed: HTTP ${connection.responseCode}" }
            val voices = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            (0 until voices.length()).mapNotNull { index ->
                val voice = voices.optJSONObject(index) ?: return@mapNotNull null
                val shortName = voice.optString("ShortName")
                val displayName = voice.optString("FriendlyName").ifBlank { shortName }
                val locale = voice.optString("Locale").lowercase()
                if (shortName.isBlank() || locale.isBlank()) null
                else ModelCatalog.edgeVoice(shortName, displayName, locale.substringBefore('-'))
            }.distinctBy { it.id }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val VOICE_LIST_URL = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list"
    }
}
