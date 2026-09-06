package com.audiobookreader.desktop

import com.audiobookreader.data.TtsModelSpec
import java.io.File
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest
import javax.sound.sampled.AudioSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DesktopPlaybackRequest(
    val bookPath: String,
    val chunks: List<String>,
    val model: TtsModelSpec,
    val startFragment: Int,
    val positionMs: Long = 0,
    val speed: Float = 1f,
    val speakerId: Int = 0,
)

enum class PlaybackPhase { IDLE, PREPARING, PLAYING, STOPPING, CLEARING, FINISHED, ERROR }

data class DesktopPlaybackState(
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val fragment: Int = 0,
    val positionMs: Long = 0,
    val cachedFragments: Int = 0,
    val message: String = "",
) {
    val busy: Boolean get() = phase in setOf(PlaybackPhase.PREPARING, PlaybackPhase.PLAYING, PlaybackPhase.STOPPING, PlaybackPhase.CLEARING)
}

/** Cache identity includes synthesis settings and text, never just the fragment number. */
class DesktopAudioCache(private val root: File) {
    private fun bookDirectory(bookPath: String): File = File(root, bookPath.hashCode().toUInt().toString(16))

    fun file(request: DesktopPlaybackRequest, index: Int): File {
        val identity = listOf("wav-v2", request.model.id, request.model.modelName, request.model.voiceId,
            request.speakerId.toString(), request.speed.toString(), request.chunks[index]).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(bookDirectory(request.bookPath), "$index-$digest.wav")
    }

    fun isReady(file: File): Boolean = file.isFile && file.length() > 44 && runCatching {
        AudioSystem.getAudioInputStream(file).use {
            it.frameLength > 0 && it.format.frameSize > 0 && file.length() >= 44 + it.frameLength * it.format.frameSize
        }
    }.getOrDefault(false)

    fun count(request: DesktopPlaybackRequest): Int = request.chunks.indices.count { isReady(file(request, it)) }

    fun publish(temporary: File, output: File) {
        check(isReady(temporary)) { "The generated WAV is empty or invalid" }
        try {
            Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Called only after the generation job has joined. Also removes legacy cache layouts. */
    fun clear(bookPath: String): Long {
        val directory = bookDirectory(bookPath).toPath()
        if (!Files.exists(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return 0
        var bytes = 0L
        // Do not follow symlinks out of the book's cache directory.
        Files.walkFileTree(directory, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                bytes += attrs.size()
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
        return bytes
    }
}

/** One generation session and one audio output. UI commands run on the UI dispatcher. */
class DesktopPlaybackSession(
    private val scope: CoroutineScope,
    private val cache: DesktopAudioCache,
    private val engineFactory: (TtsModelSpec) -> DesktopSpeechEngine,
    private val player: DesktopAudioOutput = DesktopAudioPlayer(),
) {
    private val mutableState = MutableStateFlow(DesktopPlaybackState())
    val state = mutableState.asStateFlow()
    private var session: Job? = null

    suspend fun refreshCache(request: DesktopPlaybackRequest) {
        if (session?.isActive == true) return
        val count = withContext(Dispatchers.IO) { cache.count(request) }
        if (session?.isActive != true) mutableState.update { it.copy(cachedFragments = count) }
    }

    fun play(request: DesktopPlaybackRequest) {
        if (session?.isActive == true) return
        require(request.startFragment in request.chunks.indices)
        mutableState.value = DesktopPlaybackState(PlaybackPhase.PREPARING, request.startFragment, request.positionMs,
            message = "Preparing fragment ${request.startFragment + 1}; loading the voice may take a moment…")
        session = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val count = cache.count(request)
                    mutableState.update { it.copy(cachedFragments = count) }
                }
                coroutineScope {
                    // A rendezvous channel limits work to the playing fragment + one ahead.
                    // Playback starts as soon as the first requested fragment is ready.
                    val ready = Channel<Pair<Int, File>>(Channel.RENDEZVOUS)
                    val producer = launch(Dispatchers.IO) {
                        var engine: DesktopSpeechEngine? = null
                        try {
                            for (index in request.startFragment..request.chunks.lastIndex) {
                                ensureActive()
                                val output = cache.file(request, index)
                                if (!cache.isReady(output)) {
                                    if (engine == null) engine = engineFactory(request.model)
                                    ensureActive()
                                    check(output.parentFile.isDirectory || output.parentFile.mkdirs()) { "Cannot create audio cache directory" }
                                    val temporary = File.createTempFile("fragment-", ".part", output.parentFile)
                                    try {
                                        // Native inference cannot safely be interrupted halfway through.
                                        // Cancellation is checked before writing/publishing its result.
                                        val samples = engine.render(request.chunks[index], request.speakerId, request.speed)
                                        ensureActive()
                                        DesktopWavFile.write(temporary, samples, engine.sampleRate())
                                        ensureActive()
                                        cache.publish(temporary, output)
                                        mutableState.update { it.copy(cachedFragments = it.cachedFragments + 1) }
                                    } finally {
                                        Files.deleteIfExists(temporary.toPath())
                                    }
                                }
                                ready.send(index to output)
                            }
                        } finally {
                            try { engine?.close() } finally { ready.close() }
                        }
                    }
                    for ((index, file) in ready) {
                        val offset = if (index == request.startFragment) request.positionMs else 0L
                        mutableState.update { it.copy(fragment = index, positionMs = offset) }
                        player.playToEnd(file, offset,
                            onStarted = { mutableState.update {
                                if (it.phase == PlaybackPhase.PREPARING) it.copy(phase = PlaybackPhase.PLAYING, message = "Playing fragment ${index + 1}") else it
                            } },
                            onPosition = { position -> mutableState.update {
                                if (it.phase == PlaybackPhase.PLAYING) it.copy(positionMs = position) else it
                            } },
                        )
                        if (index < request.chunks.lastIndex) mutableState.update {
                            it.copy(phase = PlaybackPhase.PREPARING, fragment = index + 1, positionMs = 0,
                                message = "Preparing fragment ${index + 2}…")
                        }
                    }
                    producer.join()
                }
                mutableState.update { it.copy(phase = PlaybackPhase.FINISHED, positionMs = 0, message = "Book finished") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(phase = PlaybackPhase.ERROR, message = "Playback error: ${error.message ?: error.javaClass.simpleName}") }
            } finally {
                player.stop()
            }
        }
    }

    fun stop() {
        if (mutableState.value.phase == PlaybackPhase.CLEARING) return
        val previous = session
        previous?.cancel()
        player.stop()
        mutableState.update { it.copy(phase = PlaybackPhase.STOPPING, message = "Stopping generation…") }
        session = scope.launch {
            previous?.join()
            mutableState.update { it.copy(phase = PlaybackPhase.IDLE, message = "Stopped; position saved") }
        }
    }

    fun clear(bookPath: String) {
        if (mutableState.value.phase == PlaybackPhase.CLEARING) return
        val previous = session
        previous?.cancel()
        player.stop()
        mutableState.update { it.copy(phase = PlaybackPhase.CLEARING, positionMs = 0, message = "Stopping generation and clearing audio…") }
        session = scope.launch {
            previous?.join()
            try {
                val bytes = withContext(Dispatchers.IO) { cache.clear(bookPath) }
                mutableState.update { it.copy(phase = PlaybackPhase.IDLE, cachedFragments = 0,
                    message = "Generated audio cleared (${bytes / 1024} KiB removed)") }
            } catch (error: Exception) {
                mutableState.update { it.copy(phase = PlaybackPhase.ERROR, message = "Could not clear audio: ${error.message}") }
            }
        }
    }

    fun dispose() {
        // Clearing belongs to the app scope, so changing tabs cannot silently cancel it.
        if (mutableState.value.phase != PlaybackPhase.CLEARING) stop()
    }
}
