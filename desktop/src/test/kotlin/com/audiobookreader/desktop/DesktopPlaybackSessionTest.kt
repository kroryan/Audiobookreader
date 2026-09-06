package com.audiobookreader.desktop

import com.audiobookreader.data.ModelFamily
import com.audiobookreader.data.TtsModelSpec
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioSystem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopPlaybackSessionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val model = TtsModelSpec("test", "Test", ModelFamily.PIPER, "en", "", "model.onnx")
    private fun request(chunks: List<String> = listOf("First.", "Second.", "Third."), start: Int = 0) =
        DesktopPlaybackRequest("/books/story.epub", chunks, model, start)

    private class Output : DesktopAudioOutput {
        val played = mutableListOf<Pair<File, Long>>()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun playToEnd(file: File, positionMs: Long, onStarted: () -> Unit, onPosition: (Long) -> Unit) {
            played += file to positionMs
            onStarted()
            onPosition(positionMs + 10)
            gate?.await() ?: delay(20)
        }
        override fun stop() = Unit
    }

    private open class Engine : DesktopSpeechEngine {
        val renders = AtomicInteger()
        val closed = AtomicInteger()
        override fun render(text: String, speakerId: Int, speed: Float): FloatArray {
            renders.incrementAndGet()
            return FloatArray(160) { 0.25f }
        }
        override fun sampleRate() = 16_000
        override fun close() { closed.incrementAndGet() }
    }

    private suspend fun waitUntil(predicate: () -> Boolean) = withTimeout(5_000) {
        while (!predicate()) delay(5)
    }

    @Test fun startsBeforeNextFragmentFinishesAndContinuesUsingOneEngine() = runBlocking {
        val releaseSecond = CountDownLatch(1)
        val engine = object : Engine() {
            override fun render(text: String, speakerId: Int, speed: Float): FloatArray {
                if (text == "Second.") check(releaseSecond.await(5, TimeUnit.SECONDS))
                return super.render(text, speakerId, speed)
            }
        }
        val factories = AtomicInteger()
        val output = Output()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val session = DesktopPlaybackSession(scope, DesktopAudioCache(temporary.newFolder()), { factories.incrementAndGet(); engine }, output)
        try {
            session.play(request())
            session.play(request()) // repeated clicks must not start another generator
            waitUntil { output.played.size == 1 }
            assertEquals(1, engine.renders.get())
            releaseSecond.countDown()
            waitUntil { session.state.value.phase == PlaybackPhase.FINISHED }
            assertEquals(listOf(0, 1, 2), output.played.map { it.first.name.substringBefore('-').toInt() })
            assertEquals(1, factories.get())
            assertEquals(1, engine.closed.get())
        } finally { releaseSecond.countDown(); scope.cancel() }
    }

    @Test fun startsAtSelectedFragmentWithoutGeneratingEarlierTextAndReusesCache() = runBlocking {
        val cache = DesktopAudioCache(temporary.newFolder())
        val engine = Engine()
        val output = Output()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val session = DesktopPlaybackSession(scope, cache, { engine }, output)
        try {
            val request = request(start = 2).copy(positionMs = 5)
            session.play(request)
            waitUntil { session.state.value.phase == PlaybackPhase.FINISHED }
            assertEquals(1, engine.renders.get())
            assertEquals(5L, output.played.first().second)
            assertFalse(cache.file(request, 0).exists())
            session.play(request)
            waitUntil { output.played.size == 2 && session.state.value.phase == PlaybackPhase.FINISHED }
            assertEquals(1, engine.renders.get())
            session.play(request.copy(speed = 1.5f))
            waitUntil { output.played.size == 3 && session.state.value.phase == PlaybackPhase.FINISHED }
            assertEquals(2, engine.renders.get())
        } finally { scope.cancel() }
    }

    @Test fun clearWaitsForNativeInferenceThenRemovesAudioAndPartialsWithoutRecreation() = runBlocking {
        val root = temporary.newFolder()
        val cache = DesktopAudioCache(root)
        val request = request()
        val other = request.copy(bookPath = "/books/other.epub")
        DesktopWavFile.write(cache.file(other, 0), FloatArray(160), 16_000)
        val legacy = File(root, "${request.bookPath.hashCode().toUInt().toString(16)}/test/99.wav")
        legacy.parentFile.mkdirs(); legacy.writeText("old cache")
        val entered = AtomicInteger()
        val release = CountDownLatch(1)
        val engine = object : Engine() {
            override fun render(text: String, speakerId: Int, speed: Float): FloatArray {
                entered.incrementAndGet()
                check(release.await(5, TimeUnit.SECONDS))
                return super.render(text, speakerId, speed)
            }
        }
        val output = Output()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val session = DesktopPlaybackSession(scope, cache, { engine }, output)
        try {
            session.play(request)
            waitUntil { entered.get() == 1 }
            session.clear(request.bookPath)
            session.play(request) // cannot race a clearing operation
            assertEquals(PlaybackPhase.CLEARING, session.state.value.phase)
            session.dispose() // leaving the reader must not silently cancel cache deletion
            release.countDown()
            waitUntil { session.state.value.phase == PlaybackPhase.IDLE }
            assertFalse(cache.file(request, 0).parentFile.exists())
            assertTrue(cache.isReady(cache.file(other, 0)))
            assertEquals(0, output.played.size)
            assertEquals(1, engine.closed.get())
            delay(50)
            assertFalse(legacy.exists())
            session.play(request(start = 2))
            waitUntil { session.state.value.phase == PlaybackPhase.FINISHED }
            assertEquals(1, output.played.size)
        } finally { release.countDown(); scope.cancel() }
    }

    @Test fun stopDuringPlaybackDoesNotAdvanceAndCanResume() = runBlocking {
        val cache = DesktopAudioCache(temporary.newFolder())
        val output = Output().apply { gate = CompletableDeferred() }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val session = DesktopPlaybackSession(scope, cache, { Engine() }, output)
        try {
            session.play(request())
            waitUntil { session.state.value.phase == PlaybackPhase.PLAYING }
            session.stop()
            waitUntil { session.state.value.phase == PlaybackPhase.IDLE }
            assertEquals(0, session.state.value.fragment)
            assertEquals(1, output.played.size)
            output.gate = null
            session.play(request().copy(positionMs = session.state.value.positionMs))
            waitUntil { session.state.value.phase == PlaybackPhase.FINISHED }
            assertEquals(4, output.played.size)
            assertEquals(10L, output.played[1].second)
        } finally { scope.cancel() }
    }

    @Test fun generationErrorsAreVisibleAndLeaveNoTemporaryFiles() = runBlocking {
        val root = temporary.newFolder()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val session = DesktopPlaybackSession(scope, DesktopAudioCache(root), {
            object : Engine() {
                override fun render(text: String, speakerId: Int, speed: Float): FloatArray = error("Inference failed")
            }
        }, Output())
        try {
            session.play(request())
            waitUntil { session.state.value.phase == PlaybackPhase.ERROR }
            assertTrue(session.state.value.message.contains("Inference failed"))
            assertTrue(root.walkTopDown().none { it.isFile })
        } finally { scope.cancel() }
    }

    @Test fun cacheIdentityIncludesTextSpeedAndSpeakerAndRejectsBrokenWav() {
        val cache = DesktopAudioCache(temporary.newFolder())
        val request = request()
        val file = cache.file(request, 0)
        assertNotEquals(file, cache.file(request.copy(speed = 2f), 0))
        assertNotEquals(file, cache.file(request.copy(speakerId = 2), 0))
        assertNotEquals(file, cache.file(request.copy(chunks = listOf("Changed text")), 0))
        file.parentFile.mkdirs(); file.writeText("broken")
        assertFalse(cache.isReady(file))
        DesktopWavFile.write(file, floatArrayOf(-1f, 0f, 1f), 16_000)
        assertTrue(cache.isReady(file))
        AudioSystem.getAudioInputStream(file).use { assertEquals(3L, it.frameLength) }
        assertEquals(50L, file.length())
        java.io.RandomAccessFile(file, "rw").use { it.setLength(48) }
        assertFalse(cache.isReady(file))
    }
}
