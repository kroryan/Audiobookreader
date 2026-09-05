package com.audiobookreader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.audiobookreader.data.Book
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
)

class ReaderViewModel(private val appContext: Context) : ViewModel() {
    private val books = BookRepository(appContext)
    private val models = ModelRepository(appContext)
    private val progressRepository = ProgressRepository(appContext)
    private val audioCache = AudioCacheRepository(appContext)
    private val settings = appContext.getSharedPreferences("bookreader-settings", Context.MODE_PRIVATE)
    private val initialModel = ModelCatalog.models.firstOrNull {
        it.id == settings.getString(KEY_SELECTED_MODEL, null)
    } ?: ModelCatalog.models.first()
    private val _state = MutableStateFlow(ReaderState(selectedModel = initialModel))
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    init { refreshModels() }

    fun importBook(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                runCatching {
                    appContext.contentResolver.takePersistableUriPermission(uri, IntentFlags)
                }
                books.import(uri)
            }.onSuccess { book ->
                _state.value = _state.value.copy(
                    books = _state.value.books + book,
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

    fun selectModel(spec: TtsModelSpec) {
        val book = _state.value.selectedBook
        settings.edit().putString(KEY_SELECTED_MODEL, spec.id).apply()
        _state.value = _state.value.copy(
            selectedModel = spec,
            cacheStatus = book?.let { audioCache.status(it, spec) },
            message = null,
        )
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

    fun playSelected(context: Context) {
        val current = _state.value
        val book = current.selectedBook ?: return
        val spec = current.selectedModel
        if (!models.isInstalled(spec)) {
            _state.value = current.copy(message = "Descarga primero el modelo seleccionado")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(generating = true, message = "Generando audio…")
            runCatching {
                val cache = File(appContext.cacheDir, "audio/${book.id}/${spec.id}").also { it.mkdirs() }
                SherpaTtsEngine(models.directory(spec), spec).use { engine ->
                    val generated = book.chapters.flatMap { chapter ->
                        TextChunker.split(chapter.text).mapIndexed { index, chunk ->
                            val output = File(cache, "${chapter.id}-$index.wav")
                            if (!output.exists()) {
                                val samples = engine.generate(chunk)
                                val estimatedBytes = samples.size.toLong() * 2L + 44L
                                check(audioCache.canWriteMore(estimatedBytes)) { "La caché de audio ha alcanzado 512 MB. Límpiala para continuar." }
                                WavFile.write(output, samples, engine.sampleRate())
                            }
                            output.absolutePath
                        }
                    }
                    generated
                }
            }.onSuccess { files ->
                val saved = progressRepository.load(book.id)
                val start = saved.itemIndex.coerceIn(0, (files.size - 1).coerceAtLeast(0))
                val progress = ReadingProgress(book.id, start, saved.positionMs, files.size)
                progressRepository.save(progress)
                PlaybackService.play(context, files, book.id, start, saved.positionMs)
                _state.value = _state.value.copy(generating = false, progress = progress, cacheStatus = audioCache.status(book, spec), message = null)
            }.onFailure { error ->
                _state.value = _state.value.copy(generating = false, message = "No se pudo generar audio: ${error.message}")
            }
        }
    }

    private fun refreshModels() {
        _state.value = _state.value.copy(installed = ModelCatalog.models.filter(models::isInstalled).map { it.id }.toSet())
    }

    companion object {
        private const val IntentFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        private const val KEY_SELECTED_MODEL = "selected_model"
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ReaderViewModel(context.applicationContext) as T
        }
    }
}
