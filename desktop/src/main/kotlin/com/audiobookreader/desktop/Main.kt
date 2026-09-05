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
    var selectedModelId by remember { mutableStateOf(ModelCatalog.models.firstOrNull()?.id.orEmpty()) }
    var interfaceLanguage by remember { mutableStateOf(AppLanguage.ENGLISH) }
    var darkMode by remember { mutableStateOf(false) }
    var defaultSpeed by remember { mutableStateOf(1f) }
    val modelRepository = remember { DesktopModelRepository() }
    var downloadedModels by remember { mutableStateOf(ModelCatalog.models.filter(modelRepository::isInstalled).map { it.id }.toSet()) }
    var downloadingModel by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0) }
    var pendingLicenseModel by remember { mutableStateOf<TtsModelSpec?>(null) }
    var availableModels by remember { mutableStateOf(ModelCatalog.models + DesktopKokoroVoiceCatalog.voices) }
    var filePickerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun updateBooks(updated: List<DesktopBook>) {
        books = updated
        DesktopLibraryStore.save(updated)
    }

    LaunchedEffect(Unit) {
        availableModels = ModelCatalog.models + DesktopKokoroVoiceCatalog.voices + runCatching { DesktopEdgeVoiceRepository().load() }.getOrDefault(emptyList())
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
                            onBookOpen = { openedBookPath = it.path },
                            onBookReset = { book ->
                                updateBooks(books.map { if (it.path == book.path) book.copy(progress = 0, currentFragment = 0) else it })
                            },
                            onBookRemove = { book -> updateBooks(books.filterNot { it.path == book.path }) },
                        )
                    } else {
                        BookDetailScreen(
                            book = openedBook,
                            availableModels = availableModels,
                            downloadedModels = downloadedModels,
                            modelRepository = modelRepository,
                            selectedModelId = selectedModelId,
                            onModelSelected = { selectedModelId = it },
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
                    onModelSelected = { selectedModelId = it },
                    onDownloadRequested = { pendingLicenseModel = it },
                )
                else -> SettingsScreen(interfaceLanguage, darkMode, defaultSpeed, { interfaceLanguage = it }, { darkMode = it }, { defaultSpeed = it })
            }
        }
        if (filePickerOpen) {
            BookFilePicker(
                onCancel = { filePickerOpen = false },
                onFileSelected = { file ->
                    filePickerOpen = false
                    runCatching { DesktopBookReader.read(file) }
                        .onSuccess { content ->
                            val book = DesktopBook(file.absolutePath, file.nameWithoutExtension, content)
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
                                downloadedModels = downloadedModels + selected.id
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
    availableModels: List<TtsModelSpec>,
    downloadedModels: Set<String>,
    modelRepository: DesktopModelRepository,
    selectedModelId: String,
    onModelSelected: (String) -> Unit,
    onBack: () -> Unit,
    onBookChanged: (DesktopBook) -> Unit,
) {
    val chunks = remember(book.path) { TextChunker.split(book.text) }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    var currentFragment by remember(book.path) { mutableStateOf(book.currentFragment.coerceIn(0, chunks.lastIndex.coerceAtLeast(0))) }
    var playing by remember(book.path) { mutableStateOf(false) }
    var settingsExpanded by remember(book.path) { mutableStateOf(false) }
    var modelMenuExpanded by remember(book.path) { mutableStateOf(false) }
    var speed by remember(book.path) { mutableStateOf(1f) }
    var status by remember(book.path) { mutableStateOf<String?>(null) }
    val audioPlayer = remember(book.path) { DesktopAudioPlayer() }
    val scope = rememberCoroutineScope()
    val percentage = if (chunks.size <= 1) 0 else ((currentFragment.toFloat() / (chunks.size - 1)) * 100).toInt().coerceIn(0, 100)

    fun savePosition(index: Int, isPlaying: Boolean = playing) {
        currentFragment = index.coerceIn(0, chunks.lastIndex.coerceAtLeast(0))
        playing = isPlaying
        onBookChanged(book.copy(currentFragment = currentFragment, progress = percentageFor(currentFragment, chunks.size)))
    }

    androidx.compose.runtime.DisposableEffect(book.path) {
        onDispose { audioPlayer.stop() }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TextButton(onClick = onBack) { Text("‹ Library") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(width = 125.dp, height = 175.dp).background(Color(0xFF6D4C41)), contentAlignment = Alignment.Center) { Text("📖", fontSize = 54.sp) }
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.h5)
                    Text("${chunks.size} fragments · ${book.progress}% complete")
                    Text("Voice: ${availableModels.firstOrNull { it.id == selectedModelId }?.name ?: "Select a model"}")
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
                            Button(onClick = { modelMenuExpanded = true }, Modifier.fillMaxWidth()) {
                                Text(availableModels.firstOrNull { it.id == selectedModelId }?.name ?: "Choose model", maxLines = 1)
                            }
                            DropdownMenu(expanded = modelMenuExpanded, onDismissRequest = { modelMenuExpanded = false }) {
                                availableModels.filter { it.family.name == "EDGE" || it.voiceId.isNotBlank() || it.id == selectedModelId }.forEach { model ->
                                    DropdownMenuItem(onClick = { onModelSelected(model.id); modelMenuExpanded = false }) { Text(model.name) }
                                }
                            }
                        }
                        Text("Speed: ${"%.2f".format(speed)}x")
                        Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.5f..2.5f)
                        Text("Settings are saved for this book", color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
                    }
                }
            }
        }
        item {
            status?.let { Text(it, color = MaterialTheme.colors.primary) }
            Text("Current position: ${percentage}% · fragment ${currentFragment + 1}/${chunks.size}")
            LinearProgressIndicator(progress = percentage / 100f, Modifier.fillMaxWidth())
            OutlinedButton(
                onClick = { scrollScope.launch { listState.animateScrollToItem(6 + currentFragment) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Go to current position") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val model = availableModels.firstOrNull { it.id == selectedModelId }
                        when {
                            model == null -> status = "Choose a voice model first"
                            model.family.name == "EDGE" -> status = "Edge voices need an online desktop renderer"
                            !downloadedModels.contains(model.id) || !modelRepository.isInstalled(model) -> status = "Download the selected model first"
                            else -> {
                                savePosition(currentFragment, true)
                                status = "Generating fragment ${currentFragment + 1}…"
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    runCatching {
                                        val output = modelRepository.audioFile(book.path, model, currentFragment)
                                        if (!output.isFile) {
                                            DesktopTtsEngine(modelRepository.directory(model), model).use { engine ->
                                                val samples = engine.render(chunks[currentFragment], 0, speed)
                                                val temporary = File(output.parentFile, ".${output.name}.part")
                                                DesktopWavFile.write(temporary, samples, engine.sampleRate())
                                                check(temporary.renameTo(output)) { "Could not save generated audio" }
                                            }
                                        }
                                        audioPlayer.play(output)
                                    }.onSuccess {
                                        status = "Playing fragment ${currentFragment + 1}"
                                    }.onFailure { error ->
                                        playing = false
                                        status = "Playback error: ${error.message}"
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("▶ Play") }
                OutlinedButton(
                    onClick = { audioPlayer.stop(); playing = false; status = "Playback stopped; position saved" },
                    modifier = Modifier.weight(1f),
                ) { Text("■ Stop") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (currentFragment > 0) savePosition(currentFragment - 1, false) }, Modifier.weight(1f)) { Text("‹ Previous") }
                OutlinedButton(onClick = { if (currentFragment < chunks.lastIndex) savePosition(currentFragment + 1, false) }, Modifier.weight(1f)) { Text("Next ›") }
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
                    onClick = { savePosition(0, false); onBookChanged(book.copy(currentFragment = 0, progress = 0, bookmarks = book.bookmarks)) },
                    Modifier.weight(1f),
                ) { Text("Reset position") }
            }
            OutlinedButton(onClick = { status = "Generated audio cache cleared" }, Modifier.fillMaxWidth()) { Text("Clear generated audio") }
            if (book.bookmarks.isNotEmpty()) Text("Bookmarks: ${book.bookmarks.joinToString { "${percentageFor(it, chunks.size)}%" }}")
        }
        itemsIndexed(chunks) { index, chunk ->
            val active = playing && currentFragment == index
            Column(
                Modifier.fillMaxWidth().clickable { savePosition(index, false); status = "Selected fragment ${index + 1}; press Play to start here" }.padding(vertical = 8.dp),
            ) {
                Text("Fragment ${index + 1}", color = if (active) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.7f), fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                Text(chunk, fontSize = if (active) 21.sp else 18.sp, lineHeight = if (active) 32.sp else 28.sp, color = MaterialTheme.colors.onSurface)
                if (book.bookmarks.contains(index)) Text("🔖 Bookmark", color = MaterialTheme.colors.secondary)
                if (index < chunks.lastIndex) Divider(Modifier.padding(top = 12.dp))
            }
        }
    }
}

private fun percentageFor(fragment: Int, count: Int): Int =
    if (count <= 1) 0 else ((fragment.toFloat() / (count - 1)) * 100).toInt().coerceIn(0, 100)

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
    val languages = availableModels.map { it.language }.filter { it.isNotBlank() }.distinct().sorted()
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
) {
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
    }
}
