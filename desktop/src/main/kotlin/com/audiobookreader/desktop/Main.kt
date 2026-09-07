package com.audiobookreader.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.AlertDialog
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TextField
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import com.audiobookreader.data.ModelCatalog
import com.audiobookreader.data.AppLanguage
import com.audiobookreader.data.TextChunker
import com.audiobookreader.data.TtsModelSpec
import kotlinx.coroutines.launch
import java.io.File
import java.awt.FileDialog
import java.io.FilenameFilter
import java.util.prefs.Preferences

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "BookReader") {
        DesktopApp()
    }
}

@Composable
private fun DesktopApp() {
    var books by remember { mutableStateOf(DesktopLibraryStore.load()) }
    var openedBookPath by remember { mutableStateOf<String?>(null) }
    var libraryMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    val settings = remember { Preferences.userRoot().node("com.audiobookreader.settings") }
    var selectedModelId by remember {
        mutableStateOf(settings.get("selected-model", ModelCatalog.models.firstOrNull()?.id.orEmpty()))
    }
    var applySelectedModelOnNextBook by remember { mutableStateOf(false) }
    var interfaceLanguage by remember {
        mutableStateOf(if (settings.get("language", "en") == "es") AppLanguage.SPANISH else AppLanguage.ENGLISH)
    }
    var darkMode by remember { mutableStateOf(settings.getBoolean("dark-mode", false)) }
    var defaultSpeed by remember { mutableStateOf(settings.getFloat("default-speed", 1f).coerceIn(0.5f, 2.5f)) }
    val modelRepository = remember { DesktopModelRepository() }
    var downloadedModels by remember { mutableStateOf(ModelCatalog.models.filter(modelRepository::isInstalled).map { it.id }.toSet()) }
    var downloadingModel by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0) }
    var pendingLicenseModel by remember { mutableStateOf<TtsModelSpec?>(null) }
    var availableModels by remember { mutableStateOf(ModelCatalog.models + modelRepository.importedModels()) }
    var filePickerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val playbackSessions = remember { mutableMapOf<String, DesktopPlaybackSession>() }

    fun updateBooks(updated: List<DesktopBook>) {
        books = updated
        DesktopLibraryStore.save(updated)
    }

    fun selectModelFromManager(modelId: String) {
        selectedModelId = modelId
        settings.put("selected-model", modelId)
        settings.flush()
        val selected = availableModels.firstOrNull { it.id == modelId } ?: return
        val path = openedBookPath
        if (path == null) {
            applySelectedModelOnNextBook = true
        } else {
            updateBooks(books.map { book ->
                if (book.path != path) book else book.copy(
                    modelId = selected.id,
                    voiceId = if (selected.family == com.audiobookreader.data.ModelFamily.KOKORO) {
                        book.voiceId.ifBlank { ModelCatalog.kokoroVoices.firstOrNull { it.available && it.language == "es" }?.id.orEmpty() }
                    } else "",
                    positionMs = 0,
                )
            })
            applySelectedModelOnNextBook = false
        }
    }

    LaunchedEffect(Unit) {
        val models = (ModelCatalog.models + modelRepository.importedModels() + runCatching { DesktopEdgeVoiceRepository().load() }.getOrDefault(emptyList()))
            .distinctBy { it.id }
        availableModels = models
        downloadedModels = models.filter(modelRepository::isInstalled).map { it.id }.toSet()
    }

    LaunchedEffect(interfaceLanguage, darkMode, defaultSpeed) {
        settings.put("language", if (interfaceLanguage == AppLanguage.SPANISH) "es" else "en")
        settings.putBoolean("dark-mode", darkMode)
        settings.putFloat("default-speed", defaultSpeed)
        settings.flush()
    }

    MaterialTheme(colors = if (darkMode) darkColors() else lightColors()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("BookReader", style = MaterialTheme.typography.h4)
            Text("Audiobook reader for Linux and Windows")
            TabRow(selectedTabIndex = selectedTab) {
                listOf("Library", "Models", "Settings").forEachIndexed { index, label ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
                }
            }
            when (selectedTab) {
                0 -> {
                    val openedBook = books.firstOrNull { it.path == openedBookPath }
                    if (openedBook == null) {
                        LibraryScreen(
                            books = books,
                            message = libraryMessage,
                            onOpen = { filePickerOpen = true },
                            onBookOpen = { selectedBook ->
                                if (applySelectedModelOnNextBook) {
                                    val selected = availableModels.firstOrNull { it.id == selectedModelId }
                                    if (selected != null) {
                                        updateBooks(books.map { book ->
                                            if (book.path != selectedBook.path) book else book.copy(
                                                modelId = selected.id,
                                                voiceId = if (selected.family == com.audiobookreader.data.ModelFamily.KOKORO) {
                                                    book.voiceId.ifBlank { ModelCatalog.kokoroVoices.firstOrNull { it.available && it.language == "es" }?.id.orEmpty() }
                                                } else "",
                                                positionMs = 0,
                                            )
                                        })
                                    }
                                    applySelectedModelOnNextBook = false
                                }
                                openedBookPath = selectedBook.path
                            },
                            onBookReset = { book ->
                                updateBooks(books.map { if (it.path == book.path) book.copy(progress = 0, currentFragment = 0, positionMs = 0) else it })
                            },
                            onBookRemove = { book -> updateBooks(books.filterNot { it.path == book.path }) },
                        )
                    } else {
                        BookDetailScreen(
                            book = openedBook,
                            playback = playbackSessions.getOrPut(openedBook.path) {
                                DesktopPlaybackSession(scope, modelRepository.audioCache(), { request ->
                                    val runtimeModel = if (request.model.family == com.audiobookreader.data.ModelFamily.KOKORO) {
                                        request.model.copy(
                                            voiceId = request.voiceId,
                                            language = ModelCatalog.kokoroVoices.firstOrNull { it.id == request.voiceId }
                                                ?.let { ModelCatalog.kokoroLanguage(it.speakerId) }
                                                ?: request.model.language,
                                        )
                                    } else request.model.copy(voiceId = request.voiceId)
                                    DesktopTtsEngine(
                                        modelRepository.directory(request.model),
                                        runtimeModel,
                                        request.referenceAudioPath,
                                        request.referenceText,
                                    )
                                })
                            },
                            availableModels = availableModels,
                            downloadedModels = downloadedModels,
                            modelRepository = modelRepository,
                            selectedModelId = selectedModelId,
                            onModelSelected = ::selectModelFromManager,
                            onReferenceAudioSelected = { path ->
                                updateBooks(books.map { if (it.path == openedBook.path) it.copy(referenceAudioPath = path) else it })
                            },
                            onBack = { openedBookPath = null },
                            onBookChanged = { updated -> updateBooks(books.map { if (it.path == updated.path) updated else it }) },
                        )
                    }
                }
                1 -> ModelsScreen(
                    selectedModelId = selectedModelId,
                    availableModels = availableModels,
                    downloadedModels = downloadedModels,
                    downloadingModel = downloadingModel,
                    downloadProgress = downloadProgress,
                    onModelSelected = ::selectModelFromManager,
                    onDownloadRequested = { pendingLicenseModel = it },
                )
                else -> SettingsScreen(
                    interfaceLanguage,
                    darkMode,
                    defaultSpeed,
                    onLanguageChanged = { interfaceLanguage = it },
                    onDarkModeChanged = { darkMode = it },
                    onSpeedChanged = { defaultSpeed = it },
                    onImportModel = { files, language ->
                        runCatching { modelRepository.importModel(files, language) }
                            .onSuccess { imported ->
                                availableModels = (availableModels + imported).distinctBy { it.id }
                                downloadedModels = downloadedModels + imported.id
                            }
                            .exceptionOrNull()?.message
                    },
                )
            }
        }
        if (filePickerOpen) {
            BookFilePicker(
                onCancel = { filePickerOpen = false },
                onFileSelected = { file ->
                    filePickerOpen = false
                    runCatching { DesktopBookReader.read(file) }
                        .onSuccess { content ->
                            val book = DesktopBook(file.absolutePath, file.nameWithoutExtension, content, speed = defaultSpeed)
                            updateBooks((books.filterNot { it.path == book.path } + book).sortedBy { it.title.lowercase() })
                            libraryMessage = null
                            openedBookPath = book.path
                        }
                        .onFailure { error -> libraryMessage = "Could not open ${file.name}: ${error.message}" }
                },
            )
        }
    }
    pendingLicenseModel?.let { spec ->
        val restricted = spec.requiresAcceptance
        AlertDialog(
            onDismissRequest = { pendingLicenseModel = null },
            title = { Text(if (restricted) "Model usage terms" else "License information") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(spec.name, style = MaterialTheme.typography.subtitle1)
                    Text(if (restricted) "Review these terms before downloading this model." else "Review this model's license and attribution before downloading it.")
                    Text("License: ${spec.licenseSpdx}")
                    if (spec.attribution.isNotBlank()) Text("Attribution: ${spec.attribution}")
                    if (restricted) Text("By accepting, you confirm that you will respect the model's restrictions, attribution, and ShareAlike requirements where applicable.")
                    if (spec.licenseUrl.isNotBlank()) Text(spec.licenseUrl, style = MaterialTheme.typography.caption)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val selected = spec
                    pendingLicenseModel = null
                    if (downloadingModel == null) {
                        downloadingModel = selected.id
                        downloadProgress = 0
                        scope.launch {
                            runCatching {
                                modelRepository.download(selected) { downloadProgress = it }
                            }.onSuccess {
                                downloadedModels = downloadedModels + availableModels
                                    .filter { it.storageId == selected.storageId && modelRepository.isInstalled(it) }
                                    .map { it.id }
                            }
                            downloadingModel = null
                        }
                    }
                }) { Text(if (restricted) "Accept and download" else "Continue download") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLicenseModel = null }) { Text(if (restricted) "Reject" else "Cancel") }
            },
        )
    }
}

@Composable
private fun BookFilePicker(onCancel: () -> Unit, onFileSelected: (File) -> Unit) {
    var directory by remember { mutableStateOf(defaultBookDirectory()) }
    var selected by remember { mutableStateOf<File?>(null) }
    val entries = remember(directory) {
        directory.listFiles()
            ?.filter { it.isDirectory || it.extension.lowercase() in SUPPORTED_BOOK_EXTENSIONS }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .orEmpty()
    }
    DialogWindow(
        onCloseRequest = onCancel,
        title = "Open book",
        state = rememberDialogState(width = 860.dp, height = 620.dp),
    ) {
        MaterialTheme {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { directory.parentFile?.let { directory = it } },
                        enabled = directory.parentFile != null,
                    ) { Text("Up") }
                    Text(directory.absolutePath, modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface)
                }
                Text("Choose a PDF, EPUB, TXT, HTML or HTM file", color = MaterialTheme.colors.onSurface)
                Card(Modifier.fillMaxWidth().weight(1f)) {
                    if (entries.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text(
                                if (directory.canRead()) "No supported books in this folder" else "This folder cannot be read",
                                color = MaterialTheme.colors.onSurface,
                            )
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize().padding(6.dp)) {
                            items(entries, key = { it.absolutePath }) { entry ->
                                val isSelected = selected?.absolutePath == entry.absolutePath
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable {
                                            if (entry.isDirectory) directory = entry else selected = entry
                                        }
                                        .padding(horizontal = 10.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(if (entry.isDirectory) "📁" else "📄", color = MaterialTheme.colors.onSurface)
                                    Text(
                                        entry.name,
                                        color = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(selected?.name ?: "No file selected", modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface)
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(onClick = { selected?.let(onFileSelected) }, enabled = selected != null) { Text("Open") }
                }
            }
        }
    }
}

private val SUPPORTED_BOOK_EXTENSIONS = setOf("pdf", "epub", "txt", "html", "htm")

private fun defaultBookDirectory(): File {
    val home = System.getProperty("user.home")?.let(::File)
    return home?.takeIf { it.isDirectory } ?: File(System.getProperty("user.dir", "."))
}

private fun chooseModelFiles(): List<File> {
    val dialog = FileDialog(null as java.awt.Frame?, "Choose ONNX model files", FileDialog.LOAD)
    dialog.isMultipleMode = true
    dialog.setFilenameFilter(FilenameFilter { _, name ->
        name.lowercase().endsWith(".onnx") || name.lowercase() == "tokens.txt" ||
            name.lowercase().endsWith(".json") || name.lowercase().endsWith(".txt")
    })
    dialog.setVisible(true)
    val directory = dialog.directory ?: return emptyList()
    return dialog.files.map { File(directory, it.name) }
}

@Composable
private fun ReferenceAudioPicker(onCancel: () -> Unit, onFileSelected: (String) -> Unit) {
    var directory by remember { mutableStateOf(defaultBookDirectory()) }
    var selected by remember { mutableStateOf<File?>(null) }
    val entries = remember(directory) {
        directory.listFiles()
            ?.filter { it.isDirectory || it.extension.equals("wav", ignoreCase = true) }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .orEmpty()
    }
    DialogWindow(
        onCloseRequest = onCancel,
        title = "Choose reference WAV",
        state = rememberDialogState(width = 860.dp, height = 620.dp),
    ) {
        MaterialTheme {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { directory.parentFile?.let { directory = it; selected = null } },
                        enabled = directory.parentFile != null,
                    ) { Text("Up") }
                    Text(directory.absolutePath, modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface)
                }
                Text("Choose a WAV file containing the reference voice.", color = MaterialTheme.colors.onSurface)
                Card(Modifier.fillMaxWidth().weight(1f)) {
                    if (entries.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (directory.canRead()) "No WAV files in this folder" else "This folder cannot be read",
                                color = MaterialTheme.colors.onSurface,
                            )
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize().padding(6.dp)) {
                            items(entries, key = { it.absolutePath }) { entry ->
                                val isSelected = selected?.absolutePath == entry.absolutePath
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable {
                                            if (entry.isDirectory) {
                                                directory = entry
                                                selected = null
                                            } else {
                                                selected = entry
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(if (entry.isDirectory) "📁" else "🔊", color = MaterialTheme.colors.onSurface)
                                    Text(
                                        entry.name,
                                        color = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(selected?.name ?: "No WAV file selected", modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface)
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(onClick = { selected?.let { onFileSelected(it.absolutePath) } }, enabled = selected != null) { Text("Use audio") }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    books: List<DesktopBook>,
    message: String?,
    onOpen: () -> Unit,
    onBookOpen: (DesktopBook) -> Unit,
    onBookReset: (DesktopBook) -> Unit,
    onBookRemove: (DesktopBook) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        ShelfBackground()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Library", style = MaterialTheme.typography.h4)
                        Text("Your books and audiobooks", color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f))
                    }
                    Button(onClick = onOpen) { Text("＋ Add book") }
                }
                Text("${books.size} ${if (books.size == 1) "book" else "books"}", color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f))
                message?.let { Text(it, color = MaterialTheme.colors.error) }
            }
            if (books.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(vertical = 30.dp)) {
                        Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📚", fontSize = 48.sp)
                            Text("Your shelf is empty", style = MaterialTheme.typography.h6)
                            Text("Add a PDF, EPUB, TXT or HTML book to start reading aloud.")
                            Spacer(Modifier.padding(4.dp))
                            Button(onClick = onOpen) { Text("＋ Add your first book") }
                        }
                    }
                }
            }
            items(books.chunked(4)) { shelf ->
                Row(Modifier.fillMaxWidth()) {
                    shelf.forEach { book ->
                        BookCard(
                            book = book,
                            modifier = Modifier.weight(1f),
                            onOpen = { onBookOpen(book) },
                            onReset = { onBookReset(book) },
                            onRemove = { onBookRemove(book) },
                        )
                    }
                    repeat(4 - shelf.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ShelfBackground() {
    val wood = if (MaterialTheme.colors.isLight) Color(0xFFE7D2B5) else Color(0xFF30261E)
    val shelf = if (MaterialTheme.colors.isLight) Color(0xFF9B6B42) else Color(0xFF8B5E3C)
    Box(Modifier.fillMaxSize().background(wood.copy(alpha = 0.38f))) {
        Canvas(Modifier.fillMaxSize()) {
            var y = size.height * 0.29f
            while (y < size.height) {
                drawRect(shelf.copy(alpha = 0.25f), topLeft = androidx.compose.ui.geometry.Offset(0f, y), size = androidx.compose.ui.geometry.Size(size.width, 15f))
                drawLine(shelf.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 3f)
                y += 290f
            }
        }
    }
}

@Composable
private fun BookCard(
    book: DesktopBook,
    modifier: Modifier,
    onOpen: () -> Unit,
    onReset: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember(book.path) { mutableStateOf(false) }
    Card(modifier.padding(8.dp).clickable(onClick = onOpen)) {
        Column(Modifier.padding(10.dp)) {
            Box(
                Modifier.fillMaxWidth().height(190.dp).background(Color(0xFF6D4C41)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📖", fontSize = 48.sp)
                    Text(book.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 3)
                }
            }
            Spacer(Modifier.padding(3.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.subtitle1, maxLines = 2)
                    Text("${book.progress}% complete", color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
                }
                Box {
                    TextButton(onClick = { menuExpanded = true }) { Text("⋮") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(onClick = { menuExpanded = false; onOpen() }) { Text("Open book") }
                        DropdownMenuItem(onClick = { menuExpanded = false; onReset() }) { Text("Reset position") }
                        DropdownMenuItem(onClick = { menuExpanded = false; onRemove() }) { Text("Remove from library") }
                    }
                }
            }
            LinearProgressIndicator(progress = book.progress / 100f, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BookDetailScreen(
    book: DesktopBook,
    playback: DesktopPlaybackSession,
    availableModels: List<TtsModelSpec>,
    downloadedModels: Set<String>,
    modelRepository: DesktopModelRepository,
    selectedModelId: String,
    onModelSelected: (String) -> Unit,
    onReferenceAudioSelected: (String) -> Unit,
    onBack: () -> Unit,
    onBookChanged: (DesktopBook) -> Unit,
) {
    val chunks = remember(book.path) { TextChunker.split(book.text) }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    var currentFragment by remember(book.path) { mutableStateOf(book.currentFragment.coerceIn(0, chunks.lastIndex.coerceAtLeast(0))) }
    var settingsExpanded by remember(book.path) { mutableStateOf(false) }
    var modelMenuExpanded by remember(book.path) { mutableStateOf(false) }
    var showAllModelFamilies by remember(book.path) { mutableStateOf(false) }
    var kokoroVoiceMenuExpanded by remember(book.path) { mutableStateOf(false) }
    var supertonicVoiceMenuExpanded by remember(book.path) { mutableStateOf(false) }
    var referencePickerOpen by remember(book.path) { mutableStateOf(false) }
    var speed by remember(book.path) { mutableStateOf(book.speed) }
    var referenceText by remember(book.path) { mutableStateOf(book.referenceText) }
    var status by remember(book.path) { mutableStateOf<String?>(null) }
    val playbackState by playback.state.collectAsState()
    val playing = playbackState.phase == PlaybackPhase.PLAYING
    val latestBook by rememberUpdatedState(book)
    val notifyBookChanged by rememberUpdatedState(onBookChanged)
    val selectedVoiceId = book.modelId.ifBlank { selectedModelId }
    val selectedModel = availableModels.firstOrNull { it.id == selectedVoiceId }
    val percentage = if (chunks.size <= 1) 0 else ((currentFragment.toFloat() / (chunks.size - 1)) * 100).toInt().coerceIn(0, 100)

    fun savePosition(index: Int, positionMs: Long = 0) {
        currentFragment = index.coerceIn(0, chunks.lastIndex.coerceAtLeast(0))
        notifyBookChanged(latestBook.copy(currentFragment = currentFragment, positionMs = positionMs, progress = percentageFor(currentFragment, chunks.size)))
    }

    LaunchedEffect(selectedVoiceId, book.voiceId, book.speakerId, speed, book.referenceAudioPath, book.referenceText) {
        availableModels.firstOrNull { it.id == selectedVoiceId }?.let { model ->
            playback.refreshCache(DesktopPlaybackRequest(book.path, chunks, model, currentFragment, speed = speed,
                speakerId = book.speakerId, voiceId = book.voiceId,
                referenceAudioPath = book.referenceAudioPath, referenceText = book.referenceText))
        }
    }
    LaunchedEffect(playbackState.fragment, playbackState.phase) {
        if (playbackState.phase in setOf(PlaybackPhase.PREPARING, PlaybackPhase.PLAYING, PlaybackPhase.FINISHED)) {
            savePosition(playbackState.fragment, playbackState.positionMs)
        }
    }
    LaunchedEffect(playback) {
        while (true) {
            kotlinx.coroutines.delay(20_000)
            val state = playback.state.value
            if (state.phase == PlaybackPhase.PLAYING) savePosition(state.fragment, state.positionMs)
        }
    }
    androidx.compose.runtime.DisposableEffect(book.path) {
        onDispose {
            val state = playback.state.value
            if (state.phase == PlaybackPhase.PLAYING || state.phase == PlaybackPhase.PREPARING) {
                notifyBookChanged(latestBook.copy(currentFragment = state.fragment, positionMs = state.positionMs,
                    progress = percentageFor(state.fragment, chunks.size)))
            }
            playback.dispose()
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TextButton(onClick = onBack) { Text("‹ Library") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(width = 125.dp, height = 175.dp).background(Color(0xFF6D4C41)), contentAlignment = Alignment.Center) { Text("📖", fontSize = 54.sp) }
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.h5)
                    Text("${chunks.size} fragments · ${book.progress}% complete")
                    Text("Voice: ${selectedVoiceLabel(selectedModel, book)}")
                }
            }
            Text("Voice settings", style = MaterialTheme.typography.subtitle1)
            OutlinedButton(onClick = { settingsExpanded = !settingsExpanded }, Modifier.fillMaxWidth()) {
                Text(if (settingsExpanded) "⚙ Voice settings ▲" else "⚙ Voice settings ▼")
            }
            if (settingsExpanded) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Voice model", color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f))
                        Box {
                            Button(onClick = { modelMenuExpanded = true }, Modifier.fillMaxWidth(), enabled = !playbackState.busy) {
                                Text(selectedVoiceLabel(selectedModel, book), maxLines = 1)
                            }
                            DropdownMenu(expanded = modelMenuExpanded, onDismissRequest = { modelMenuExpanded = false }) {
                                val activeFamily = selectedModel?.family
                                availableModels.filter { model ->
                                    val usable = model.family == com.audiobookreader.data.ModelFamily.EDGE ||
                                        model.id in downloadedModels || model.id == selectedVoiceId
                                    usable && (showAllModelFamilies || activeFamily == null || model.family == activeFamily)
                                }.forEach { model ->
                                    DropdownMenuItem(onClick = {
                                        onModelSelected(model.id)
                                        val voiceId = if (model.family == com.audiobookreader.data.ModelFamily.KOKORO) {
                                            latestBook.voiceId.ifBlank {
                                                ModelCatalog.kokoroVoices.firstOrNull { it.available && it.language == "es" }?.id
                                                    ?: ModelCatalog.kokoroVoices.firstOrNull { it.available }?.id.orEmpty()
                                            }
                                        } else ""
                                        onBookChanged(latestBook.copy(modelId = model.id, voiceId = voiceId, positionMs = 0))
                                        modelMenuExpanded = false
                                    }) { Text(model.name) }
                                }
                            }
                        }
                        TextButton(onClick = { showAllModelFamilies = !showAllModelFamilies }) {
                            Text(if (showAllModelFamilies) "Show only ${selectedModel?.family?.name ?: "current"} voices" else "Change model family")
                        }
                        if (selectedModel?.family == com.audiobookreader.data.ModelFamily.KOKORO) {
                            Text("Kokoro voice", color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f))
                            Box {
                                Button(onClick = { kokoroVoiceMenuExpanded = true }, Modifier.fillMaxWidth(), enabled = !playbackState.busy) {
                                    Text(kokoroVoiceLabel(book.voiceId), maxLines = 1)
                                }
                                DropdownMenu(expanded = kokoroVoiceMenuExpanded, onDismissRequest = { kokoroVoiceMenuExpanded = false }) {
                                    ModelCatalog.kokoroVoices.filter { it.available }.groupBy { it.language }.toSortedMap().forEach { (language, voices) ->
                                        DropdownMenuItem(onClick = {}, enabled = false) {
                                            Text(language.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
                                        }
                                        voices.forEach { voice ->
                                            DropdownMenuItem(onClick = {
                                                onBookChanged(latestBook.copy(modelId = "kokoro-multi-v1-0", voiceId = voice.id, positionMs = 0))
                                                kokoroVoiceMenuExpanded = false
                                            }) { Text(kokoroVoiceLabel(voice.id)) }
                                        }
                                    }
                                }
                            }
                        }
                        Text("Speed: ${"%.2f".format(speed)}x")
                        Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.5f..2.5f,
                            enabled = !playbackState.busy,
                            onValueChangeFinished = { onBookChanged(latestBook.copy(speed = speed, positionMs = 0)) })
                        if (selectedModel?.family == com.audiobookreader.data.ModelFamily.SUPERTONIC) {
                            Text("Supertonic voice", color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f))
                            Box {
                                val selectedSpeaker = ModelCatalog.supertonicVoices.getOrNull(book.speakerId)
                                    ?: ModelCatalog.supertonicVoices.first()
                                Button(onClick = { supertonicVoiceMenuExpanded = true }, Modifier.fillMaxWidth(), enabled = !playbackState.busy) {
                                    Text(supertonicVoiceLabel(selectedSpeaker), maxLines = 1)
                                }
                                DropdownMenu(expanded = supertonicVoiceMenuExpanded, onDismissRequest = { supertonicVoiceMenuExpanded = false }) {
                                    ModelCatalog.supertonicVoices.forEachIndexed { index, voice ->
                                        DropdownMenuItem(onClick = {
                                            onBookChanged(latestBook.copy(speakerId = index, positionMs = 0))
                                            supertonicVoiceMenuExpanded = false
                                        }) { Text(supertonicVoiceLabel(voice)) }
                                    }
                                }
                            }
                        }
                        Text("Settings are saved for this book", color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
                        if (selectedModel?.family == com.audiobookreader.data.ModelFamily.POCKET || selectedModel?.family == com.audiobookreader.data.ModelFamily.ZIPVOICE) {
                            Text("Voice cloning", color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { referencePickerOpen = true },
                                    Modifier.weight(1f),
                                    enabled = !playbackState.busy,
                                ) {
                                    Text(if (book.referenceAudioPath.isBlank()) "Choose reference WAV" else "Reference audio selected", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        playback.clear(book.path)
                                        onBookChanged(latestBook.copy(referenceAudioPath = ""))
                                        status = "Reference audio cleared; generated audio will be rebuilt"
                                    },
                                    Modifier.weight(0.65f),
                                    enabled = book.referenceAudioPath.isNotBlank() && !playbackState.busy,
                                ) {
                                    Text("Clear")
                                }
                            }
                            if (selectedModel.family == com.audiobookreader.data.ModelFamily.ZIPVOICE) {
                                TextField(
                                    value = referenceText,
                                    onValueChange = { referenceText = it; onBookChanged(latestBook.copy(referenceText = it)) },
                                    label = { Text("Exact reference transcript") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            status?.let { Text(it, color = MaterialTheme.colors.primary) }
            if (playbackState.message.isNotBlank()) Text(playbackState.message, color = if (playbackState.phase == PlaybackPhase.ERROR) MaterialTheme.colors.error else MaterialTheme.colors.primary)
            if (playbackState.phase in setOf(PlaybackPhase.PREPARING, PlaybackPhase.STOPPING, PlaybackPhase.CLEARING)) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Audio ready with these voice settings: ${playbackState.cachedFragments * 100 / chunks.size.coerceAtLeast(1)}% (${playbackState.cachedFragments}/${chunks.size} fragments)")
            Text("Current position: ${percentage}% · fragment ${currentFragment + 1}/${chunks.size}")
            LinearProgressIndicator(progress = percentage / 100f, Modifier.fillMaxWidth())
            OutlinedButton(
                onClick = { scrollScope.launch { listState.animateScrollToItem(2 + currentFragment) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Go to current position") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val model = availableModels.firstOrNull { it.id == selectedVoiceId }
                        when {
                            model == null -> status = "Choose a voice model first"
                            model.family.name != "EDGE" && (!downloadedModels.contains(model.id) || !modelRepository.isInstalled(model)) -> status = "Download the selected model first"
                            else -> {
                                status = null
                                onBookChanged(latestBook.copy(modelId = model.id, speed = speed))
                                playback.play(DesktopPlaybackRequest(book.path, chunks, model, currentFragment,
                                    positionMs = book.positionMs, speed = speed, speakerId = book.speakerId, voiceId = book.voiceId,
                                    referenceAudioPath = book.referenceAudioPath, referenceText = book.referenceText))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !playbackState.busy && book.text.isNotBlank(),
                ) { Text("▶ Play") }
                OutlinedButton(
                    onClick = { savePosition(currentFragment, playbackState.positionMs); status = null; playback.stop() },
                    modifier = Modifier.weight(1f),
                    enabled = playbackState.phase == PlaybackPhase.PREPARING || playing,
                ) { Text("■ Stop") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { playback.stop(); if (currentFragment > 0) savePosition(currentFragment - 1) }, Modifier.weight(1f), enabled = playbackState.phase != PlaybackPhase.CLEARING) { Text("‹ Previous") }
                OutlinedButton(onClick = { playback.stop(); if (currentFragment < chunks.lastIndex) savePosition(currentFragment + 1) }, Modifier.weight(1f), enabled = playbackState.phase != PlaybackPhase.CLEARING) { Text("Next ›") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val bookmarks = (book.bookmarks + currentFragment).distinct().sorted()
                        onBookChanged(book.copy(bookmarks = bookmarks))
                        status = "Bookmark saved at ${percentage}%"
                    },
                    Modifier.weight(1f),
                ) { Text("＋ Bookmark") }
                OutlinedButton(
                    onClick = { playback.stop(); savePosition(0) },
                    Modifier.weight(1f),
                    enabled = playbackState.phase != PlaybackPhase.CLEARING,
                ) { Text("Reset position") }
            }
            OutlinedButton(onClick = { status = null; savePosition(currentFragment); playback.clear(book.path) }, Modifier.fillMaxWidth(), enabled = playbackState.phase != PlaybackPhase.CLEARING) { Text("Clear generated audio") }
            if (book.bookmarks.isNotEmpty()) Text("Bookmarks: ${book.bookmarks.joinToString { "${percentageFor(it, chunks.size)}%" }}")
        }
        itemsIndexed(chunks) { index, chunk ->
            val active = playing && currentFragment == index
            Column(
                Modifier.fillMaxWidth().clickable(enabled = playbackState.phase != PlaybackPhase.CLEARING) { playback.stop(); savePosition(index); status = "Selected fragment ${index + 1}; press Play to start here" }.padding(vertical = 8.dp),
            ) {
                Text("Fragment ${index + 1}", color = if (active) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.7f), fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                Text(chunk, fontSize = if (active) 21.sp else 18.sp, lineHeight = if (active) 32.sp else 28.sp, color = MaterialTheme.colors.onSurface)
                if (book.bookmarks.contains(index)) Text("🔖 Bookmark", color = MaterialTheme.colors.secondary)
                if (index < chunks.lastIndex) Divider(Modifier.padding(top = 12.dp))
            }
        }
    }
    if (referencePickerOpen) {
        ReferenceAudioPicker(
            onCancel = { referencePickerOpen = false },
            onFileSelected = { path ->
                referencePickerOpen = false
                onReferenceAudioSelected(path)
            },
        )
    }
}

private fun percentageFor(fragment: Int, count: Int): Int =
    if (count <= 1) 0 else ((fragment.toFloat() / (count - 1)) * 100).toInt().coerceIn(0, 100)

private fun kokoroVoiceLabel(id: String): String {
    val voice = ModelCatalog.kokoroVoices.firstOrNull { it.id == id }
    if (voice == null) return "Choose a Kokoro voice"
    val name = when (id) {
        "ef_dora" -> "Dora"
        "em_alex" -> "Alex"
        "em_santa" -> "Santa"
        else -> id.substringAfter('_').replaceFirstChar { it.uppercase() }
    }
    return "$name ($id) · ${voice.language.uppercase()}"
}

private fun selectedVoiceLabel(model: TtsModelSpec?, book: DesktopBook): String = when {
    model?.family == com.audiobookreader.data.ModelFamily.KOKORO -> kokoroVoiceLabel(book.voiceId)
    model == null -> "Choose model"
    else -> model.name
}

private fun supertonicVoiceLabel(voice: String): String =
    "$voice · ${if (voice.startsWith("F")) "female" else "male"}"

@Composable
private fun ModelsScreen(
    availableModels: List<TtsModelSpec>,
    selectedModelId: String,
    downloadedModels: Set<String>,
    downloadingModel: String?,
    downloadProgress: Int,
    onModelSelected: (String) -> Unit,
    onDownloadRequested: (TtsModelSpec) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("all") }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val languages = availableModels.map { it.language }
        .filter { it.isNotBlank() && it != "all" }
        .distinct()
        .sorted()
    val models = remember(query, language, availableModels) {
        availableModels.filter {
            (language == "all" || it.language == language || it.language == "all") &&
                (query.isBlank() || it.name.contains(query, ignoreCase = true) || it.language.contains(query, ignoreCase = true))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Models", style = MaterialTheme.typography.h5)
        Text("Choose the voice model used for audiobook playback.")
        TextField(value = query, onValueChange = { query = it }, label = { Text("Filter by model or language") }, modifier = Modifier.fillMaxWidth())
        Box {
            Button(onClick = { languageMenuExpanded = true }) { Text("Language: ${if (language == "all") "All languages" else language.uppercase()}") }
            DropdownMenu(expanded = languageMenuExpanded, onDismissRequest = { languageMenuExpanded = false }) {
                DropdownMenuItem(onClick = { language = "all"; languageMenuExpanded = false }) { Text("All languages") }
                languages.forEach { code ->
                    DropdownMenuItem(onClick = { language = code; languageMenuExpanded = false }) { Text(code.uppercase()) }
                }
            }
        }
        Text("${availableModels.size} models and online voices available")
        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(models) { model ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(model.name, style = MaterialTheme.typography.subtitle1)
                            Text("${model.family} · ${model.language}")
                            Text("Downloaded on demand; model files are kept outside the application package.")
                        }
                        Column {
                            Button(onClick = { onModelSelected(model.id) }) {
                                Text(if (model.id == selectedModelId) "Selected" else "Select")
                            }
                            when {
                                model.family.name == "EDGE" -> Text("ONLINE", color = Color(0xFF2E7D32))
                                model.voiceId.isNotBlank() -> Text("VOICE", color = Color(0xFF1565C0))
                                model.id == downloadingModel -> {
                                    Text("$downloadProgress%")
                                    LinearProgressIndicator(progress = downloadProgress / 100f)
                                }
                                model.id in downloadedModels -> Text("Downloaded")
                                else -> Button(onClick = { onDownloadRequested(model) }) { Text("Download") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    language: AppLanguage,
    darkMode: Boolean,
    speed: Float,
    onLanguageChanged: (AppLanguage) -> Unit,
    onDarkModeChanged: (Boolean) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onImportModel: (List<File>, String) -> String?,
) {
    var modelLanguage by remember { mutableStateOf("") }
    var importMessage by remember { mutableStateOf<String?>(null) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Settings", style = MaterialTheme.typography.h5)
            Text("Application and playback preferences")
        }
        item {
            Text("Interface language", style = MaterialTheme.typography.subtitle1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { option ->
                    Button(onClick = { onLanguageChanged(option) }, enabled = option != language) { Text(option.label) }
                }
            }
        }
        item {
            Text("Appearance", style = MaterialTheme.typography.subtitle1)
            Row {
                Checkbox(checked = darkMode, onCheckedChange = onDarkModeChanged)
                Text("Dark mode")
            }
        }
        item {
            Text("Default playback speed: ${"%.2f".format(speed)}x", style = MaterialTheme.typography.subtitle1)
            Slider(value = speed, onValueChange = onSpeedChanged, valueRange = 0.5f..2.5f)
        }
        item {
            Text("Model storage", style = MaterialTheme.typography.subtitle1)
            Text("Models are selected per book and downloaded on demand. Generated audio can be cleaned from the book view.")
        }
        item {
            Text("Import local ONNX model", style = MaterialTheme.typography.subtitle1)
            Text("Select an ONNX file and tokens.txt. Use a three-letter ISO 639-2 code such as spa or eng; espeak-ng-data is optional when the model does not require it.")
            TextField(
                value = modelLanguage,
                onValueChange = { modelLanguage = it.lowercase().filter(Char::isLetter).take(3) },
                label = { Text("Language code") },
                placeholder = { Text("spa") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { importMessage = chooseModelFiles().takeIf { it.isNotEmpty() }?.let { onImportModel(it, modelLanguage) } },
                enabled = modelLanguage.length == 3,
            ) { Text("Import model files") }
            importMessage?.let { Text(it, color = MaterialTheme.colors.error) }
        }
    }
}
