package com.audiobookreader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.audiobookreader.data.Book
import com.audiobookreader.data.AppLanguage
import com.audiobookreader.data.BookRepository
import com.audiobookreader.data.ModelCatalog
import com.audiobookreader.data.ModelRepository
import com.audiobookreader.data.Bookmark
import com.audiobookreader.data.AudioCacheRepository
import com.audiobookreader.data.AudioCacheStatus
import com.audiobookreader.data.ProgressRepository
import com.audiobookreader.data.ReadingProgress
import com.audiobookreader.data.TtsModelSpec
import com.audiobookreader.data.TextChunker
import com.audiobookreader.data.LanguageCodes
import com.audiobookreader.data.BookTtsSettings
import com.audiobookreader.playback.PlaybackService
import com.audiobookreader.playback.WavFile
import com.audiobookreader.tts.SherpaTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ReaderState(
    val books: List<Book> = emptyList(),
    val selectedBook: Book? = null,
    val availableModels: List<TtsModelSpec> = ModelCatalog.models,
    val selectedModel: TtsModelSpec = ModelCatalog.models.first(),
    val bookTtsSettings: BookTtsSettings = BookTtsSettings(ModelCatalog.models.first().id),
    val pendingStartIndex: Int? = null,
    val progress: ReadingProgress? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val cacheStatus: AudioCacheStatus? = null,
    val readyChunks: Set<Int> = emptySet(),
    val currentDurationMs: Long = 0L,
    val installed: Set<String> = emptySet(),
    val downloading: String? = null,
    val downloadProgress: Int = 0,
    val generating: Boolean = false,
    val message: String? = null,
    val appLanguage: AppLanguage = AppLanguage.ENGLISH,
)

class ReaderViewModel(private val appContext: Context) : ViewModel() {
    private val books = BookRepository(appContext)
    private val models = ModelRepository(appContext)
    private val progressRepository = ProgressRepository(appContext)
    private val audioCache = AudioCacheRepository(appContext)
    private val settings = appContext.getSharedPreferences("bookreader-settings", Context.MODE_PRIVATE)
    private val initialLanguage = AppLanguage.fromCode(settings.getString(KEY_APP_LANGUAGE, null))
    private val allModels = ModelCatalog.models + models.importedModels()
    private val initialModel = allModels.firstOrNull {
        it.id == settings.getString(KEY_SELECTED_MODEL, null)
    } ?: allModels.first()
    private val _state = MutableStateFlow(ReaderState(availableModels = allModels, selectedModel = initialModel, appLanguage = initialLanguage))
    val state: StateFlow<ReaderState> = _state.asStateFlow()
    private var generationJob: Job? = null

    init {
        refreshModels()
        restoreLibrary()
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                runCatching {
                    appContext.contentResolver.takePersistableUriPermission(uri, IntentFlags)
                }
                books.import(uri)
            }.onSuccess { book ->
                val savedUris = settings.getStringSet(KEY_LIBRARY_URIS, emptySet()).orEmpty().toMutableSet()
                savedUris += uri.toString()
                settings.edit().putStringSet(KEY_LIBRARY_URIS, savedUris).apply()
                _state.value = _state.value.copy(
                    books = (_state.value.books.filterNot { it.id == book.id } + book),
                    selectedBook = book,
                    pendingStartIndex = null,
                    progress = progressRepository.load(book.id),
                    bookmarks = progressRepository.bookmarks(book.id),
                    bookTtsSettings = loadBookTtsSettings(book.id),
                    cacheStatus = audioCache.status(book, _state.value.selectedModel),
                    readyChunks = audioCache.readyChunks(book.id, _state.value.selectedModel.id),
                    message = null,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(message = "No se pudo abrir el libro: ${error.message}")
            }
        }
    }

    fun selectBook(book: Book) {
        val saved = progressRepository.load(book.id)
        val bookSettings = loadBookTtsSettings(book.id)
        val model = allModels.firstOrNull { it.id == bookSettings.modelId } ?: _state.value.selectedModel
        _state.value = _state.value.copy(
            selectedBook = book,
            selectedModel = model,
            bookTtsSettings = bookSettings.copy(modelId = model.id),
            pendingStartIndex = null,
            progress = saved,
            bookmarks = progressRepository.bookmarks(book.id),
            cacheStatus = audioCache.status(book, model),
            readyChunks = audioCache.readyChunks(book.id, model.id),
            currentDurationMs = audioCache.durationMs(book.id, model.id, saved.itemIndex),
            message = null,
        )
    }

    fun selectModel(spec: TtsModelSpec) {
        val current = _state.value
        val book = current.selectedBook
        if (book == null) {
            settings.edit().putString(KEY_SELECTED_MODEL, spec.id).apply()
            _state.value = current.copy(selectedModel = spec, message = null)
        } else {
            if (current.selectedModel.id != spec.id) {
                val job = generationJob
                generationJob = null
                job?.cancel()
                PlaybackService.stop(appContext)
            }
            val updated = current.bookTtsSettings.copy(modelId = spec.id)
            saveBookTtsSettings(book.id, updated)
            val progress = progressRepository.load(book.id)
            _state.value = current.copy(
                selectedModel = spec,
                bookTtsSettings = updated,
                cacheStatus = audioCache.status(book, spec),
                readyChunks = audioCache.readyChunks(book.id, spec.id),
                currentDurationMs = audioCache.durationMs(book.id, spec.id, progress.itemIndex),
                message = null,
            )
        }
    }

    fun setBookSpeed(speed: Float) {
        val current = _state.value
        val book = current.selectedBook ?: return
        val updated = current.bookTtsSettings.copy(speed = speed.coerceIn(0.5f, 2.5f))
        if (updated.speed == current.bookTtsSettings.speed) return
        saveBookTtsSettings(book.id, updated)
        invalidateBookAudio(book, "Velocidad cambiada; el audio se regenerará con la nueva configuración")
        _state.value = _state.value.copy(bookTtsSettings = updated)
    }

    fun setBookSpeakerId(speakerId: Int) {
        val current = _state.value
        val book = current.selectedBook ?: return
        val updated = current.bookTtsSettings.copy(speakerId = speakerId.coerceIn(0, 31))
        if (updated.speakerId == current.bookTtsSettings.speakerId) return
        saveBookTtsSettings(book.id, updated)
        invalidateBookAudio(book, "Voz cambiada; el audio se regenerará con la nueva voz")
        _state.value = _state.value.copy(bookTtsSettings = updated)
    }

    fun setAppLanguage(language: AppLanguage) {
        settings.edit().putString(KEY_APP_LANGUAGE, language.code).apply()
        _state.value = _state.value.copy(appLanguage = language)
    }

    fun clearSelectedBookCache() {
        val current = _state.value
        val book = current.selectedBook ?: return
        val job = generationJob
        generationJob = null
        job?.cancel()
        PlaybackService.stop(appContext)
        viewModelScope.launch(Dispatchers.IO) {
            job?.cancelAndJoin()
            audioCache.clearBook(book.id)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(generating = false, cacheStatus = audioCache.status(book, _state.value.selectedModel), readyChunks = emptySet(), message = "Audio preparado eliminado")
            }
        }
    }

    fun clearAllAudioCache() {
        val job = generationJob
        generationJob = null
        job?.cancel()
        PlaybackService.stop(appContext)
        viewModelScope.launch(Dispatchers.IO) {
            job?.cancelAndJoin()
            audioCache.clearAll()
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(generating = false, cacheStatus = _state.value.selectedBook?.let { audioCache.status(it, _state.value.selectedModel) }, readyChunks = emptySet(), message = "Caché de audio limpiada")
            }
        }
    }

    fun updatePlaybackProgress(bookId: String, itemIndex: Int, positionMs: Long, itemCount: Int) {
        val current = _state.value.selectedBook ?: return
        if (current.id != bookId) return
        _state.value = _state.value.copy(
            progress = ReadingProgress(bookId, itemIndex, positionMs, itemCount),
            currentDurationMs = audioCache.durationMs(bookId, _state.value.selectedModel.id, itemIndex),
        )
    }

    fun jumpToChunk(index: Int) {
        val current = _state.value
        val book = current.selectedBook ?: return
        val total = book.chapters.sumOf { TextChunker.split(it.text).size }
        if (index !in 0 until total) return
        val paths = audioCache.filesThrough(book.id, current.selectedModel.id, index)
        if (paths == null) {
            _state.value = current.copy(message = "Ese fragmento todavía no está preparado")
            return
        }
        val saved = ReadingProgress(book.id, index, 0L, total)
        progressRepository.save(saved)
        if (generationJob?.isActive == true) {
            PlaybackService.seekTo(appContext, index, 0L)
        } else {
            PlaybackService.play(appContext, paths, book.id, index, 0L, total)
        }
        _state.value = current.copy(
            progress = saved,
            currentDurationMs = audioCache.durationMs(book.id, current.selectedModel.id, index),
            message = "Reproduciendo desde el fragmento ${index + 1}",
        )
    }

    /** Select a paragraph without starting playback; Play will generate from it. */
    fun selectChunkForPlayback(index: Int) {
        val current = _state.value
        val book = current.selectedBook ?: return
        val total = book.chapters.sumOf { TextChunker.split(it.text).size }
        if (index !in 0 until total) return
        _state.value = current.copy(
            pendingStartIndex = index,
            message = "Seleccionado el fragmento ${index + 1}; pulsa reproducir para empezar desde ahí",
        )
    }

    fun seekCurrentPosition(positionMs: Long) {
        val current = _state.value
        val progress = current.progress ?: return
        val index = progress.itemIndex
        if (index !in current.readyChunks) return
        val position = positionMs.coerceIn(0L, current.currentDurationMs)
        PlaybackService.seekTo(appContext, index, position)
        val updated = progress.copy(positionMs = position)
        progressRepository.save(updated)
        _state.value = current.copy(progress = updated)
    }

    private fun invalidateBookAudio(book: Book, message: String) {
        val modelId = _state.value.selectedModel.id
        val job = generationJob
        generationJob = null
        job?.cancel()
        PlaybackService.stop(appContext)
        viewModelScope.launch(Dispatchers.IO) {
            job?.cancelAndJoin()
            audioCache.clearModel(book.id, modelId)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    generating = false,
                    readyChunks = emptySet(),
                    cacheStatus = audioCache.status(book, _state.value.selectedModel),
                    message = message,
                )
            }
        }
    }

    fun addBookmark() {
        val current = _state.value
        val book = current.selectedBook ?: return
        val progress = current.progress ?: progressRepository.load(book.id)
        val bookmark = Bookmark(
            bookId = book.id,
            label = "${progress.percentage}% · fragmento ${progress.itemIndex + 1}",
            itemIndex = progress.itemIndex,
            positionMs = progress.positionMs,
            percentage = progress.percentage,
        )
        progressRepository.addBookmark(bookmark)
        _state.value = current.copy(bookmarks = progressRepository.bookmarks(book.id), message = "Marcapáginas guardado")
    }

    fun resetSelectedBookProgress() {
        val current = _state.value
        val book = current.selectedBook ?: return
        val itemCount = book.chapters.sumOf { TextChunker.split(it.text).size }.coerceAtLeast(1)
        val reset = ReadingProgress(book.id, itemIndex = 0, positionMs = 0L, itemCount = itemCount)
        val job = generationJob
        generationJob = null
        job?.cancel()
        PlaybackService.reset(appContext, book.id, itemCount)
        progressRepository.save(reset)
        viewModelScope.launch(Dispatchers.IO) {
            job?.cancelAndJoin()
            audioCache.clearTemporary(book.id, current.selectedModel.id)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    progress = reset,
                    pendingStartIndex = null,
                    generating = false,
                    cacheStatus = audioCache.status(book, _state.value.selectedModel),
                    message = "Posición del libro reiniciada",
                )
            }
        }
    }

    fun stopSelectedPlayback() {
        val current = _state.value
        val book = current.selectedBook ?: return
        val spec = current.selectedModel
        val saved = current.progress ?: progressRepository.load(book.id)
        val job = generationJob
        generationJob = null
        job?.cancel()
        progressRepository.save(saved)
        PlaybackService.stop(appContext)
        viewModelScope.launch(Dispatchers.IO) {
            job?.cancelAndJoin()
            // Keep audio before the current playback point. Any generated
            // look-ahead is temporary and will be regenerated on resume.
            audioCache.clearFrom(book.id, spec.id, saved.itemIndex)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    generating = false,
                    cacheStatus = audioCache.status(book, _state.value.selectedModel),
                    message = "Reproducción detenida; se regenerará desde el fragmento guardado",
                )
            }
        }
    }

    fun downloadModel(spec: TtsModelSpec) {
        if (_state.value.downloading != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(downloading = spec.id, downloadProgress = 0, message = null)
            runCatching { models.download(spec) { progress -> _state.value = _state.value.copy(downloadProgress = progress) } }
                .onSuccess {
                    refreshModels()
                    val book = _state.value.selectedBook
                    settings.edit().putString(KEY_SELECTED_MODEL, spec.id).apply()
                    _state.value = _state.value.copy(
                        selectedModel = spec,
                        installed = _state.value.installed + spec.id,
                        downloading = null,
                        cacheStatus = book?.let { audioCache.status(it, spec) },
                        readyChunks = book?.let { audioCache.readyChunks(it.id, spec.id) } ?: emptySet(),
                        message = "Modelo descargado y seleccionado: ${spec.name}",
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(downloading = null, message = "Error descargando el modelo: ${error.message}")
                }
        }
    }

    fun importCustomModel(uris: List<Uri>, language: String) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { models.importOnnx(uris, LanguageCodes.normalize(language)) }
                .onSuccess { spec ->
                    withContext(Dispatchers.Main) {
                        val available = (_state.value.availableModels + spec).distinctBy { it.id }
                        settings.edit().putString(KEY_SELECTED_MODEL, spec.id).apply()
                        _state.value = _state.value.copy(
                            availableModels = available,
                            selectedModel = spec,
                            installed = _state.value.installed + spec.id,
                            message = "Modelo ONNX importado y seleccionado: ${spec.name}",
                        )
                    }
                }
                .onFailure { error ->
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(message = "No se pudo importar el modelo: ${error.message}")
                    }
                }
        }
    }

    fun playSelected() {
        val current = _state.value
        val book = current.selectedBook ?: return
        val spec = current.selectedModel
        val ttsSettings = current.bookTtsSettings
        val requestedStart = current.pendingStartIndex
        if (generationJob?.isActive == true) {
            _state.value = current.copy(message = "El audio ya se está preparando en segundo plano")
            return
        }
        if (!models.isInstalled(spec)) {
            _state.value = current.copy(message = "Descarga primero el modelo seleccionado")
            return
        }
        generationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(generating = true, message = "Preparando los primeros minutos…")
                }
                val chunks = book.chapters.flatMap { chapter ->
                    TextChunker.split(chapter.text).map { chunk -> chapter.id to chunk }
                }
                check(chunks.isNotEmpty()) { "El libro no contiene texto reproducible" }
                val saved = progressRepository.load(book.id)
                val startFrom = requestedStart?.coerceIn(0, chunks.lastIndex)
                val generationStart = startFrom ?: 0
                var start = startFrom ?: if (saved.itemIndex >= chunks.size) 0 else saved.itemIndex.coerceIn(0, chunks.lastIndex)
                var startPositionMs = if (saved.itemIndex >= chunks.size) 0L else saved.positionMs.coerceAtLeast(0L)
                if (startFrom != null) startPositionMs = 0L
                val cache = File(appContext.cacheDir, "audio/${book.id}/${spec.id}").also { it.mkdirs() }
                val initialFiles = mutableListOf<String>()
                val initialEnd: Int
                SherpaTtsEngine(models.directory(spec), spec).use { engine ->
                    var preparedDurationMs = 0L
                    var end = chunks.size
                    for (index in generationStart until chunks.size) {
                        val file = renderChunk(cache, chunks[index], index, engine, ttsSettings)
                        initialFiles += file.absolutePath
                        preparedDurationMs += WavFile.durationMs(file, engine.sampleRate())
                        val preparedCount = index - generationStart + 1
                        val enoughChunks = preparedCount >= START_CHUNKS
                        val enoughDuration = preparedDurationMs >= MIN_READY_DURATION_MS
                        val enoughForStart = preparedCount >= maxOf(START_CHUNKS, start - generationStart + 1)
                        if (enoughForStart && (enoughChunks || enoughDuration)) {
                            end = index + 1
                            break
                        }
                    }
                    initialEnd = end
                    val localStart = start - generationStart
                    check(initialFiles.size > localStart) { "No se pudo preparar el punto seleccionado del libro" }
                    val savedFileDuration = WavFile.durationMs(File(initialFiles[localStart]), engine.sampleRate())
                    if (startFrom == null && startPositionMs >= savedFileDuration - END_TOLERANCE_MS) {
                        if (start < chunks.lastIndex) {
                            start += 1
                            startPositionMs = 0L
                        } else {
                            // A play action at the end of a completed book
                            // starts it again instead of appearing frozen.
                            start = 0
                            startPositionMs = 0L
                        }
                    }
                    val progress = ReadingProgress(book.id, start, startPositionMs, chunks.size)
                    progressRepository.save(progress)
                    PlaybackService.play(appContext, initialFiles, book.id, localStart, startPositionMs, chunks.size, generationStart)
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(
                            generating = false,
                            pendingStartIndex = null,
                            progress = progress,
                            cacheStatus = audioCache.status(book, spec),
                            readyChunks = audioCache.readyChunks(book.id, spec.id),
                            currentDurationMs = audioCache.durationMs(book.id, spec.id, start),
                            message = "Reproduciendo; preparando el resto en segundo plano…",
                        )
                    }
                    for (index in initialEnd until chunks.size) {
                        val file = renderChunk(cache, chunks[index], index, engine, ttsSettings)
                        PlaybackService.append(appContext, listOf(file.absolutePath), book.id)
                        withContext(Dispatchers.Main) {
                            _state.value = _state.value.copy(cacheStatus = audioCache.status(book, spec), readyChunks = audioCache.readyChunks(book.id, spec.id))
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(message = null)
                }
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        generating = false,
                        message = "No se pudo preparar el audio: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    private fun renderChunk(
        cache: File,
        chunk: Pair<String, String>,
        index: Int,
        engine: SherpaTtsEngine,
        ttsSettings: BookTtsSettings,
    ): File {
        val output = File(cache, "${chunk.first}-$index.wav")
        if (!output.exists()) {
            val temporary = File(cache, ".${chunk.first}-$index.wav.part")
            temporary.delete()
            val samples = engine.generate(chunk.second, ttsSettings.speakerId, ttsSettings.speed)
            val estimatedBytes = samples.size.toLong() * 2L + 44L
            check(audioCache.canWriteMore(estimatedBytes)) {
                "La caché de audio ha alcanzado 512 MB. Límpiala para continuar."
            }
            // Never expose a partially written WAV as a playable chunk.
            WavFile.write(temporary, samples, engine.sampleRate())
            check(temporary.renameTo(output)) { "No se pudo guardar el fragmento generado" }
        }
        return output
    }

    private fun refreshModels() {
        val available = ModelCatalog.models + models.importedModels()
        _state.value = _state.value.copy(
            availableModels = available,
            installed = available.filter(models::isInstalled).map { it.id }.toSet(),
        )
    }

    private fun restoreLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val restored = settings.getStringSet(KEY_LIBRARY_URIS, emptySet()).orEmpty()
                .mapNotNull { uri -> runCatching { books.import(Uri.parse(uri)) }.getOrNull() }
            if (restored.isNotEmpty()) {
                val selected = restored.first()
                val saved = progressRepository.load(selected.id)
                val bookSettings = loadBookTtsSettings(selected.id)
                val model = allModels.firstOrNull { it.id == bookSettings.modelId } ?: _state.value.selectedModel
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        books = restored,
                        selectedBook = selected,
                        selectedModel = model,
                        bookTtsSettings = bookSettings.copy(modelId = model.id),
                        progress = saved,
                        bookmarks = progressRepository.bookmarks(selected.id),
                        cacheStatus = audioCache.status(selected, model),
                        readyChunks = audioCache.readyChunks(selected.id, model.id),
                        currentDurationMs = audioCache.durationMs(selected.id, model.id, saved.itemIndex),
                    )
                }
            }
        }
    }

    private fun loadBookTtsSettings(bookId: String): BookTtsSettings {
        val modelId = settings.getString("book.$bookId.model", null)
            ?: settings.getString(KEY_SELECTED_MODEL, null)
            ?: allModels.first().id
        return BookTtsSettings(
            modelId = modelId,
            speed = settings.getFloat("book.$bookId.speed", 1f).coerceIn(0.5f, 2.5f),
            speakerId = settings.getInt("book.$bookId.speaker", 0).coerceIn(0, 31),
        )
    }

    private fun saveBookTtsSettings(bookId: String, value: BookTtsSettings) {
        settings.edit()
            .putString("book.$bookId.model", value.modelId)
            .putFloat("book.$bookId.speed", value.speed)
            .putInt("book.$bookId.speaker", value.speakerId)
            .apply()
    }

    companion object {
        private const val START_CHUNKS = 3
        private const val MIN_READY_DURATION_MS = 5 * 60 * 1000L
        private const val END_TOLERANCE_MS = 500L
        private const val IntentFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_LIBRARY_URIS = "library_uris"
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ReaderViewModel(context.applicationContext) as T
        }
    }
}
