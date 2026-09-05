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
import com.audiobookreader.playback.PlaybackService
import com.audiobookreader.playback.WavFile
import com.audiobookreader.tts.SherpaTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ReaderState(
    val books: List<Book> = emptyList(),
    val selectedBook: Book? = null,
    val selectedModel: TtsModelSpec = ModelCatalog.models.first(),
    val progress: ReadingProgress? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val cacheStatus: AudioCacheStatus? = null,
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
    private val initialModel = ModelCatalog.models.firstOrNull {
        it.id == settings.getString(KEY_SELECTED_MODEL, null)
    } ?: ModelCatalog.models.first()
    private val _state = MutableStateFlow(ReaderState(selectedModel = initialModel, appLanguage = initialLanguage))
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
                    progress = progressRepository.load(book.id),
                    bookmarks = progressRepository.bookmarks(book.id),
                    cacheStatus = audioCache.status(book, _state.value.selectedModel),
                    message = null,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(message = "No se pudo abrir el libro: ${error.message}")
            }
        }
    }

    fun selectBook(book: Book) {
        _state.value = _state.value.copy(
            selectedBook = book,
            progress = progressRepository.load(book.id),
            bookmarks = progressRepository.bookmarks(book.id),
            cacheStatus = audioCache.status(book, _state.value.selectedModel),
            message = null,
        )
    }

    fun selectModel(spec: TtsModelSpec) {
        val book = _state.value.selectedBook
        settings.edit().putString(KEY_SELECTED_MODEL, spec.id).apply()
        _state.value = _state.value.copy(
            selectedModel = spec,
            cacheStatus = book?.let { audioCache.status(it, spec) },
            message = null,
        )
    }

    fun setAppLanguage(language: AppLanguage) {
        settings.edit().putString(KEY_APP_LANGUAGE, language.code).apply()
        _state.value = _state.value.copy(appLanguage = language)
    }

    fun clearSelectedBookCache() {
        val book = _state.value.selectedBook ?: return
        audioCache.clearBook(book.id)
        _state.value = _state.value.copy(cacheStatus = audioCache.status(book, _state.value.selectedModel), message = "Audio preparado eliminado")
    }

    fun clearAllAudioCache() {
        audioCache.clearAll()
        _state.value = _state.value.copy(cacheStatus = _state.value.selectedBook?.let { audioCache.status(it, _state.value.selectedModel) }, message = "Caché de audio limpiada")
    }

    fun updatePlaybackProgress(bookId: String, itemIndex: Int, positionMs: Long, itemCount: Int) {
        val current = _state.value.selectedBook ?: return
        if (current.id != bookId) return
        _state.value = _state.value.copy(progress = ReadingProgress(bookId, itemIndex, positionMs, itemCount))
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
                        message = "Modelo descargado y seleccionado: ${spec.name}",
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(downloading = null, message = "Error descargando el modelo: ${error.message}")
                }
        }
    }

    fun playSelected() {
        val current = _state.value
        val book = current.selectedBook ?: return
        val spec = current.selectedModel
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
                var start = saved.itemIndex.coerceIn(0, chunks.lastIndex)
                var startPositionMs = saved.positionMs.coerceAtLeast(0L)
                val cache = File(appContext.cacheDir, "audio/${book.id}/${spec.id}").also { it.mkdirs() }
                val initialFiles = mutableListOf<String>()
                val initialEnd: Int
                SherpaTtsEngine(models.directory(spec), spec).use { engine ->
                    var preparedDurationMs = 0L
                    var end = chunks.size
                    for (index in chunks.indices) {
                        val file = renderChunk(cache, chunks[index], index, engine)
                        initialFiles += file.absolutePath
                        preparedDurationMs += WavFile.durationMs(file, engine.sampleRate())
                        val enoughChunks = index + 1 >= START_CHUNKS
                        val enoughDuration = preparedDurationMs >= MIN_READY_DURATION_MS
                        if (index + 1 >= maxOf(START_CHUNKS, start + 1) && (enoughChunks || enoughDuration)) {
                            end = index + 1
                            break
                        }
                    }
                    initialEnd = end
                    check(initialFiles.size > start) { "No se pudo preparar el punto guardado del libro" }
                    val savedFileDuration = WavFile.durationMs(File(initialFiles[start]), engine.sampleRate())
                    if (startPositionMs >= savedFileDuration - END_TOLERANCE_MS) {
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
                    PlaybackService.play(appContext, initialFiles, book.id, start, startPositionMs, chunks.size)
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(
                            generating = false,
                            progress = progress,
                            cacheStatus = audioCache.status(book, spec),
                            message = "Reproduciendo; preparando el resto en segundo plano…",
                        )
                    }
                    for (index in initialEnd until chunks.size) {
                        val file = renderChunk(cache, chunks[index], index, engine)
                        PlaybackService.append(appContext, listOf(file.absolutePath), book.id)
                        withContext(Dispatchers.Main) {
                            _state.value = _state.value.copy(cacheStatus = audioCache.status(book, spec))
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
    ): File {
        val output = File(cache, "${chunk.first}-$index.wav")
        if (!output.exists()) {
            val samples = engine.generate(chunk.second)
            val estimatedBytes = samples.size.toLong() * 2L + 44L
            check(audioCache.canWriteMore(estimatedBytes)) {
                "La caché de audio ha alcanzado 512 MB. Límpiala para continuar."
            }
            WavFile.write(output, samples, engine.sampleRate())
        }
        return output
    }

    private fun refreshModels() {
        _state.value = _state.value.copy(installed = ModelCatalog.models.filter(models::isInstalled).map { it.id }.toSet())
    }

    private fun restoreLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val restored = settings.getStringSet(KEY_LIBRARY_URIS, emptySet()).orEmpty()
                .mapNotNull { uri -> runCatching { books.import(Uri.parse(uri)) }.getOrNull() }
            if (restored.isNotEmpty()) {
                val selected = restored.first()
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        books = restored,
                        selectedBook = selected,
                        progress = progressRepository.load(selected.id),
                        bookmarks = progressRepository.bookmarks(selected.id),
                        cacheStatus = audioCache.status(selected, _state.value.selectedModel),
                    )
                }
            }
        }
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
