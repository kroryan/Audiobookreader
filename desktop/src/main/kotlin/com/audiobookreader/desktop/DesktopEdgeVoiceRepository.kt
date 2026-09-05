package com.audiobookreader.desktop

import com.audiobookreader.data.ModelCatalog
import com.audiobookreader.data.TtsModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class DesktopEdgeVoiceRepository {
    private val cacheFile = File(applicationDirectory(), "edge-voices.json")

    suspend fun load(): List<TtsModelSpec> = withContext(Dispatchers.IO) {
        runCatching { fetch().also(::save) }.getOrElse { cached() }
    }

    private fun fetch(): List<TtsModelSpec> {
        val url = "$VOICE_LIST_URL?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
            "&Sec-MS-GEC=${secMsGec()}&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        connection.setRequestProperty("Origin", ORIGIN)
        connection.setRequestProperty("User-Agent", "BookReader/0.1")
        connection.setRequestProperty("Accept", "application/json")
        return try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Edge voice list failed: HTTP ${connection.responseCode}" }
            val voices = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            (0 until voices.length()).mapNotNull { index ->
                val voice = voices.optJSONObject(index) ?: return@mapNotNull null
                val shortName = voice.optString("ShortName")
                val displayName = voice.optString("FriendlyName").ifBlank { shortName }
                val locale = voice.optString("Locale").trim().lowercase()
                if (shortName.isBlank() || locale.isBlank()) null
                else ModelCatalog.edgeVoice(shortName, displayName, locale.substringBefore('-'))
            }.distinctBy { it.id }
        } finally {
            connection.disconnect()
        }
    }

    private fun save(models: List<TtsModelSpec>) {
        cacheFile.parentFile?.mkdirs()
        val array = JSONArray()
        models.forEach { model ->
            array.put(JSONObject().apply {
                put("voice", model.edgeVoice)
                put("name", model.name.removePrefix("Edge · "))
                put("language", model.language)
            })
        }
        cacheFile.writeText(array.toString())
    }

    private fun cached(): List<TtsModelSpec> = runCatching {
        val array = JSONArray(cacheFile.takeIf(File::isFile)?.readText() ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val voice = item.optString("voice")
            val name = item.optString("name")
            val language = item.optString("language")
            if (voice.isBlank() || name.isBlank() || language.isBlank()) null
            else ModelCatalog.edgeVoice(voice, name, language)
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    private fun secMsGec(): String {
        val windowsEpochSeconds = System.currentTimeMillis() / 1000L + WINDOWS_EPOCH_OFFSET
        val rounded = windowsEpochSeconds - windowsEpochSeconds % 300L
        val ticks = rounded * 10_000_000L
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$ticks$TRUSTED_CLIENT_TOKEN".toByteArray())
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun applicationDirectory(): File {
        System.getenv("BOOKREADER_APP_DIR")?.takeIf { it.isNotBlank() }?.let { return File(it) }
        val command = ProcessHandle.current().info().command().orElse("")
        var directory = command.takeIf { it.isNotBlank() }?.let(::File)?.parentFile
        repeat(8) {
            if (directory != null && (File(directory, "runtime").isDirectory || File(directory, "lib").isDirectory)) return directory!!
            directory = directory?.parentFile
        }
        return File(System.getProperty("user.dir"))
    }

    companion object {
        private const val VOICE_LIST_URL = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list"
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val SEC_MS_GEC_VERSION = "1-143.0.3650.75"
        private const val WINDOWS_EPOCH_OFFSET = 11_644_473_600L
        private const val ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"
    }
}
