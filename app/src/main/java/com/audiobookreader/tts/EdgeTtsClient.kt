package com.audiobookreader.tts

import com.audiobookreader.data.ModelCatalog
import com.audiobookreader.data.SpeechText
import com.audiobookreader.data.TtsModelSpec
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Small client for the public Edge Read Aloud protocol used by edge-tts.
 * Edge voices are online voices, so no ONNX model is installed on the device.
 */
class EdgeTtsClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun synthesizeToFile(
        text: String,
        voice: String,
        language: String,
        speed: Float,
        output: File,
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val muid = randomMuid()
        val url = "$WS_ENDPOINT?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
            "&Sec-MS-GEC=${secMsGec()}&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION" +
            "&ConnectionId=$connectionId"
        val request = Request.Builder()
            .url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", ORIGIN)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", "muid=$muid")
            .build()
        val audio = ByteArrayOutputStream()
        var completed = false
        lateinit var socket: WebSocket

        fun finish(error: Throwable?) {
            if (completed) return
            completed = true
            if (error == null) {
                runCatching {
                    check(audio.size() > 0) { "Edge TTS no devolvió audio" }
                    output.parentFile?.mkdirs()
                    output.outputStream().use { audio.writeTo(it) }
                }.onSuccess {
                    if (continuation.isActive) continuation.resume(Unit)
                }.onFailure {
                    if (continuation.isActive) continuation.resumeWithException(it)
                }
            } else if (continuation.isActive) {
                continuation.resumeWithException(error)
            }
            socket.close(1000, null)
        }

        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val timestamp = edgeTimestamp()
                webSocket.send(speechConfig(timestamp))
                webSocket.send(ssmlMessage(requestId, timestamp, text, voice, language, speed))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end", ignoreCase = true)) finish(null)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val frame = bytes.toByteArray()
                if (frame.size < 2) return
                val headerLength = ((frame[0].toInt() and 0xff) shl 8) or (frame[1].toInt() and 0xff)
                val audioStart = 2 + headerLength
                val header = frame.copyOfRange(2, audioStart.coerceAtMost(frame.size)).toString(Charsets.UTF_8)
                if (audioStart <= frame.size && header.contains("Content-Type:audio", ignoreCase = true)) {
                    audio.write(frame, audioStart, frame.size - audioStart)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!completed) finish(IllegalStateException("Edge TTS cerró la conexión antes de terminar"))
            }
        })
        continuation.invokeOnCancellation { socket.cancel() }
    }

    fun fetchVoices(): List<TtsModelSpec> {
        val url = "$VOICE_LIST_ENDPOINT?trustedclienttoken=$TRUSTED_CLIENT_TOKEN" +
            "&Sec-MS-GEC=${secMsGec()}&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Origin", ORIGIN)
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "application/json")
        return try {
            check(connection.responseCode in 200..299) { "Edge TTS devolvió HTTP ${connection.responseCode}" }
            val array = connection.inputStream.bufferedReader().use { JSONArray(it.readText()) }
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val shortName = item.optString("ShortName").trim()
                val locale = item.optString("Locale").trim()
                if (shortName.isBlank() || locale.isBlank()) return@mapNotNull null
                val localeName = item.optString("LocaleName").ifBlank { locale }
                val gender = item.optString("Gender").takeIf { it.isNotBlank() }
                val friendly = item.optString("FriendlyName").takeIf { it.isNotBlank() }
                val description = listOfNotNull(localeName, friendly, gender).distinct().joinToString(" · ")
                ModelCatalog.edgeVoice(shortName, description, locale.substringBefore('-').lowercase())
            }.distinctBy { it.id }
        } finally {
            connection.disconnect()
        }
    }

    private fun speechConfig(timestamp: String): String =
        "X-Timestamp:$timestamp\r\n" +
            "Content-Type:application/json; charset=utf-8\r\n" +
            "Path:speech.config\r\n\r\n" +
            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"

    private fun ssmlMessage(
        requestId: String,
        timestamp: String,
        text: String,
        voice: String,
        language: String,
        speed: Float,
    ): String {
        val locale = voice.substringBeforeLast('-').ifBlank { language }
        val rate = ((speed.coerceIn(0.5f, 2.5f) - 1f) * 100f).toInt()
        val rateText = if (rate >= 0) "+${rate}%" else "${rate}%"
        val safeText = edgeSsmlText(text)
        return "X-RequestId:$requestId\r\n" +
            "Content-Type:application/ssml+xml\r\n" +
            "X-Timestamp:${timestamp}Z\r\n" +
            "Path:ssml\r\n\r\n" +
            "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$locale'>" +
            "<voice name='$voice'><prosody rate='$rateText' pitch='+0Hz' volume='+0%'>$safeText</prosody></voice></speak>"
    }

    private fun edgeSsmlText(text: String): String {
        val normalized = SpeechText.forEdgeTts(text)
        val escaped = escapeXml(normalized)
            .filter { it == '\n' || it == '\r' || it == '\t' || it.code >= 0x20 }
        return escaped
            .replace("…", "<break time='550ms'/>")
            .replace(Regex("\\s*[—–]\\s*"), "<break time='350ms'/>")
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun edgeTimestamp(): String = SimpleDateFormat(
        "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
        Locale.US,
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    private fun secMsGec(): String {
        val windowsEpochSeconds = System.currentTimeMillis() / 1000L + WINDOWS_EPOCH_OFFSET
        val rounded = windowsEpochSeconds - windowsEpochSeconds % 300L
        val ticks = rounded * 10_000_000L
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$ticks$TRUSTED_CLIENT_TOKEN".toByteArray())
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun randomMuid(): String = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        .joinToString("") { "%02X".format(it) }

    companion object {
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val SEC_MS_GEC_VERSION = "1-143.0.3650.75"
        private const val WINDOWS_EPOCH_OFFSET = 11_644_473_600L
        private const val WS_ENDPOINT = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        private const val VOICE_LIST_ENDPOINT = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list"
        private const val ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"
    }
}
