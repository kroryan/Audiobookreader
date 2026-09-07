package com.audiobookreader.desktop

import com.audiobookreader.data.SpeechText
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

/** Edge Read Aloud client used for the online voices shown in Models. */
class DesktopEdgeTtsClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    fun synthesize(text: String, voice: String, language: String, speed: Float): ByteArray {
        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val url = "$WS_ENDPOINT?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
            "&Sec-MS-GEC=${secMsGec()}&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION" +
            "&ConnectionId=$connectionId"
        val request = Request.Builder()
            .url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", ORIGIN)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", "muid=${randomMuid()}")
            .build()
        val audio = ByteArrayOutputStream()
        val error = AtomicReference<Throwable?>(null)
        val completed = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        var socket: WebSocket? = null

        fun finish(failure: Throwable? = null) {
            if (!finished.compareAndSet(false, true)) return
            if (failure != null) error.compareAndSet(null, failure)
            completed.countDown()
            socket?.close(1000, null)
        }

        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val timestamp = edgeTimestamp()
                webSocket.send(speechConfig(timestamp))
                webSocket.send(ssmlMessage(requestId, timestamp, text, voice, language, speed))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end", ignoreCase = true)) finish()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val frame = bytes.toByteArray()
                if (frame.size < 2) return
                val headerLength = ((frame[0].toInt() and 0xff) shl 8) or (frame[1].toInt() and 0xff)
                val audioStart = 2 + headerLength
                if (audioStart > frame.size) return
                val header = frame.copyOfRange(2, audioStart).toString(Charsets.UTF_8)
                if (header.contains("Content-Type:audio", ignoreCase = true)) {
                    audio.write(frame, audioStart, frame.size - audioStart)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = finish(t)

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!finished.get()) finish(IllegalStateException("Edge TTS closed before completing"))
            }
        })
        check(completed.await(2, TimeUnit.MINUTES)) { "Edge TTS timed out" }
        error.get()?.let { throw IllegalStateException("Edge TTS failed", it) }
        check(audio.size() > 44) { "Edge TTS returned no audio" }
        return audio.toByteArray()
    }

    private fun speechConfig(timestamp: String): String =
        "X-Timestamp:$timestamp\r\n" +
            "Content-Type:application/json; charset=utf-8\r\n" +
            "Path:speech.config\r\n\r\n" +
            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"riff-24khz-16bit-mono-pcm\"}}}}\r\n"

    private fun ssmlMessage(requestId: String, timestamp: String, text: String, voice: String, language: String, speed: Float): String {
        val locale = voice.substringBeforeLast('-').ifBlank { language }
        val rate = ((speed.coerceIn(0.5f, 2.5f) - 1f) * 100f).toInt()
        val rateText = if (rate >= 0) "+${rate}%" else "${rate}%"
        return "X-RequestId:$requestId\r\n" +
            "Content-Type:application/ssml+xml\r\n" +
            "X-Timestamp:${timestamp}Z\r\n" +
            "Path:ssml\r\n\r\n" +
            "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$locale'>" +
            "<voice name='$voice'><prosody rate='$rateText' pitch='+0Hz' volume='+0%'>${ssmlText(text)}</prosody></voice></speak>"
    }

    private fun ssmlText(text: String): String = escapeXml(SpeechText.forEdgeTts(text))
        .replace("…", "<break time='550ms'/>")
        .replace(Regex("\\s*[—–]\\s*"), "<break time='350ms'/>")

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun edgeTimestamp(): String = SimpleDateFormat(
        "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US,
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    private fun secMsGec(): String {
        val seconds = System.currentTimeMillis() / 1000L + WINDOWS_EPOCH_OFFSET
        val rounded = seconds - seconds % 300L
        val ticks = rounded * 10_000_000L
        return MessageDigest.getInstance("SHA-256")
            .digest("$ticks$TRUSTED_CLIENT_TOKEN".toByteArray())
            .joinToString("") { "%02X".format(it) }
    }

    private fun randomMuid(): String = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        .joinToString("") { "%02X".format(it) }

    companion object {
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val SEC_MS_GEC_VERSION = "1-143.0.3650.75"
        private const val WINDOWS_EPOCH_OFFSET = 11_644_473_600L
        private const val WS_ENDPOINT = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        private const val ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"
    }
}
