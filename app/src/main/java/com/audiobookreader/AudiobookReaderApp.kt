package com.audiobookreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiobookreader.data.Book
import com.audiobookreader.data.AppLanguage
import com.audiobookreader.data.ModelFamily
import com.audiobookreader.data.TextChunker
import com.audiobookreader.data.TtsModelSpec

@Composable
fun AudiobookReaderApp(viewModel: ReaderViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = UiStrings.forLanguage(state.appLanguage)
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("📚") }, label = { Text(strings.library) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("🔊") }, label = { Text(strings.models) })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("⚙") }, label = { Text(strings.settings) })
            }
        }
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> LibraryScreen(state, viewModel, strings)
                1 -> ModelScreen(state, viewModel, strings)
                else -> SettingsScreen(state, viewModel, strings)
            }
        }
    }
}

@Composable
private fun LibraryScreen(state: ReaderState, viewModel: ReaderViewModel, strings: UiStrings) {
    var openedBookId by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBook)
    }
    val openedBook = state.books.firstOrNull { it.id == openedBookId }
    if (openedBook != null) {
        BookDetailScreen(openedBook, state, viewModel, strings) { openedBookId = null }
        return
    }
    Box(Modifier.fillMaxSize()) {
        ShelfBackground()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(strings.library, style = MaterialTheme.typography.headlineMedium)
                Text(strings.librarySubtitle)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { picker.launch(arrayOf("application/pdf", "application/epub+zip", "text/plain", "text/html")) }) {
                    Text(strings.addBook)
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            if (state.books.isEmpty()) item { Text(strings.emptyLibrary) }
            items(state.books.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { book -> BookCard(book, state, viewModel, strings, Modifier.weight(1f)) { openedBookId = book.id } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ShelfBackground() {
    val shelfColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Canvas(Modifier.fillMaxSize()) {
            var y = size.height * 0.30f
            while (y < size.height) {
                drawRect(shelfColor.copy(alpha = 0.16f), topLeft = androidx.compose.ui.geometry.Offset(0f, y), size = androidx.compose.ui.geometry.Size(size.width, 8f))
                drawLine(shelfColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 3f)
                y += 300f
            }
        }
    }
}

@Composable
private fun BookCard(book: Book, state: ReaderState, viewModel: ReaderViewModel, strings: UiStrings, modifier: Modifier, onOpen: () -> Unit) {
    Card(modifier.padding(6.dp).clickable {
        viewModel.selectBook(book)
        onOpen()
    }) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BookCover(book, Modifier.fillMaxWidth().height(190.dp))
            Spacer(Modifier.height(8.dp))
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            if (state.selectedBook?.id == book.id) Text(state.progress?.let { "${it.percentage}%" } ?: "0%") else Text(strings.openBook)
        }
    }
}

@Composable
private fun BookCover(book: Book, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, book.coverPath) { value = book.coverPath?.let(BitmapFactory::decodeFile) }
    if (bitmap != null) {
        androidx.compose.foundation.Image(bitmap!!.asImageBitmap(), contentDescription = book.title, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Text("📖", fontSize = 42.sp) }
    }
}

@Composable
private fun BookDetailScreen(book: Book, state: ReaderState, viewModel: ReaderViewModel, strings: UiStrings, onBack: () -> Unit) {
    val chunks = remember(book.id) { book.chapters.flatMap { chapter -> TextChunker.split(chapter.text).map { Triple(chapter.id, chapter.title, it) } } }
    val listState = rememberLazyListState()
    val activeIndex = state.progress?.itemIndex ?: -1
    LaunchedEffect(activeIndex, chunks.size) {
        if (activeIndex >= 0 && chunks.isNotEmpty()) listState.animateScrollToItem((activeIndex + 2).coerceAtMost(chunks.lastIndex + 2))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("‹ ${strings.library}") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BookCover(book, Modifier.size(width = 120.dp, height = 170.dp).clip(MaterialTheme.shapes.medium))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.headlineSmall)
                    Text("${book.chapters.size} ${strings.chapters}")
                    Text("${strings.voice}: ${state.selectedModel.name}")
                }
            }
        }
        item {
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.progress?.let {
                val displayedFragment = if (it.itemCount <= 0) 0 else it.itemIndex.coerceIn(0, it.itemCount - 1) + 1
                Text("${strings.savedProgress}: ${it.percentage}% · ${strings.fragment} $displayedFragment/${it.itemCount}")
            }
            state.cacheStatus?.let { Text("${strings.audioReady}: ${it.percentage}% · ${it.sizeLabel}") }
            Text(strings.autoSave, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.generating) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(strings.preparing)
            } else {
                Button(onClick = { viewModel.playSelected() }) { Text("▶ ${strings.play}") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = viewModel::addBookmark) { Text(strings.addBookmark) }
                TextButton(onClick = viewModel::resetSelectedBookProgress) { Text(strings.resetProgress) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = viewModel::stopSelectedPlayback) { Text(strings.stop) }
                TextButton(onClick = viewModel::clearSelectedBookCache) { Text(strings.clearAudio) }
                TextButton(onClick = viewModel::clearAllAudioCache) { Text(strings.clearCache) }
            }
        }
        if (state.bookmarks.isNotEmpty()) {
            item {
                Text(strings.bookmarks, style = MaterialTheme.typography.titleMedium)
                state.bookmarks.forEach { Text("• ${it.label}") }
            }
        }
        itemsIndexed(chunks) { index, chunk ->
            val active = activeIndex == index
            Column(
                Modifier.fillMaxWidth().then(if (active) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium).padding(16.dp) else Modifier.padding(vertical = 8.dp))
            ) {
                Text(if (active) "${chunk.second} · ${strings.readingNow}" else chunk.second, style = MaterialTheme.typography.titleMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                Spacer(Modifier.height(7.dp))
                Text(chunk.third, style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (active) 22.sp else 18.sp, lineHeight = if (active) 34.sp else 29.sp, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)), color = MaterialTheme.colorScheme.onSurface)
                if (!active && index < chunks.lastIndex) Divider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }
        }
    }
}

@Composable
private fun ModelScreen(state: ReaderState, viewModel: ReaderViewModel, strings: UiStrings) {
    var expanded by remember { mutableStateOf(false) }
    var language by rememberSaveable { mutableStateOf("all") }
    val languages = state.availableModels.map { it.language }.filter { it != "all" }.distinct().sorted()
    val visibleModels = state.availableModels.filter { language == "all" || it.language == language || it.language == "all" }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(strings.models, style = MaterialTheme.typography.headlineMedium)
            Text(strings.modelsSubtitle)
            Box {
                Button(onClick = { expanded = true }) { Text(strings.languageLabel(language)) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(strings.allLanguages) }, onClick = { language = "all"; expanded = false })
                    languages.forEach { code -> DropdownMenuItem(text = { Text(strings.languageLabel(code)) }, onClick = { language = code; expanded = false }) }
                }
            }
        }
        items(visibleModels, key = { it.id }) { spec -> ModelCard(spec, state, viewModel, strings) }
    }
}

@Composable
private fun ModelCard(spec: TtsModelSpec, state: ReaderState, viewModel: ReaderViewModel, strings: UiStrings) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(spec.name, style = MaterialTheme.typography.titleMedium)
            Text("${if (spec.archiveName.isBlank()) strings.imported else spec.family.label()} · ${strings.languageLabel(spec.language)}")
            if (spec.experimental) Text(strings.experimental)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (state.selectedModel.id == spec.id) Text("${strings.selected}  ")
                TextButton(onClick = { viewModel.selectModel(spec) }) { Text(strings.use) }
                if (state.installed.contains(spec.id)) Text(strings.downloaded)
                else if (state.downloading == spec.id) Text("${state.downloadProgress}%")
                else TextButton(onClick = { viewModel.downloadModel(spec) }) { Text(strings.download) }
            }
            if (state.downloading == spec.id) LinearProgressIndicator((state.downloadProgress / 100f).coerceIn(0f, 1f), Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingsScreen(state: ReaderState, viewModel: ReaderViewModel, strings: UiStrings) {
    var modelLanguage by rememberSaveable { mutableStateOf("") }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importCustomModel(uris, modelLanguage)
    }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(strings.settings, style = MaterialTheme.typography.headlineMedium)
            Text(strings.settingsSubtitle)
        }
        item {
            Text(strings.interfaceLanguage, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { language ->
                    Button(onClick = { viewModel.setAppLanguage(language) }, enabled = state.appLanguage != language) { Text(language.label) }
                }
            }
        }
        item { Text(strings.systemTheme, style = MaterialTheme.typography.titleMedium); Text(strings.systemThemeDescription) }
        item { Text(strings.progressSettings, style = MaterialTheme.typography.titleMedium); Text(strings.progressDescription) }
        item {
            Text(strings.importLocalModel, style = MaterialTheme.typography.titleMedium)
            Text(strings.importModelHelp)
            OutlinedTextField(
                value = modelLanguage,
                onValueChange = { modelLanguage = it.take(12) },
                label = { Text(strings.languageCode) },
                placeholder = { Text("es") },
                singleLine = true,
            )
            Spacer(Modifier.height(6.dp))
            Button(onClick = { modelPicker.launch(arrayOf("*/*")) }, enabled = modelLanguage.trim().isNotEmpty()) {
                Text(strings.importModel)
            }
        }
    }
}

private data class UiStrings(
    val library: String, val librarySubtitle: String, val addBook: String, val emptyLibrary: String, val openBook: String,
    val models: String, val modelsSubtitle: String, val settings: String, val settingsSubtitle: String,
    val chapters: String, val voice: String, val savedProgress: String, val fragment: String, val audioReady: String,
    val autoSave: String, val preparing: String, val play: String, val addBookmark: String, val clearAudio: String,
    val clearCache: String, val bookmarks: String, val readingNow: String, val experimental: String, val selected: String,
    val use: String, val downloaded: String, val download: String, val resetProgress: String, val stop: String,
    val allLanguages: String, val interfaceLanguage: String, val imported: String,
    val importLocalModel: String, val importModelHelp: String, val languageCode: String, val importModel: String,
    val systemTheme: String, val systemThemeDescription: String, val progressSettings: String, val progressDescription: String,
) {
    fun languageLabel(code: String) = if (code == "all") allLanguages else LANGUAGE_NAMES[code] ?: code.uppercase()
    companion object {
        private val LANGUAGE_NAMES = mapOf("af" to "Afrikaans", "ar" to "Arabic", "ca" to "Catalan", "cs" to "Czech", "cy" to "Welsh", "da" to "Danish", "de" to "German", "el" to "Greek", "en" to "English", "es" to "Spanish", "eu" to "Basque", "fa" to "Persian", "fi" to "Finnish", "fr" to "French", "hi" to "Hindi", "hr" to "Croatian", "hu" to "Hungarian", "id" to "Indonesian", "is" to "Icelandic", "it" to "Italian", "ka" to "Georgian", "kk" to "Kazakh", "ku" to "Kurdish", "lb" to "Luxembourgish", "lv" to "Latvian", "ne" to "Nepali", "nl" to "Dutch", "no" to "Norwegian", "pl" to "Polish", "pt" to "Portuguese", "ro" to "Romanian", "ru" to "Russian", "sk" to "Slovak", "sl" to "Slovenian", "sq" to "Albanian", "sr" to "Serbian", "sv" to "Swedish", "sw" to "Swahili", "tr" to "Turkish", "uk" to "Ukrainian", "ur" to "Urdu", "vi" to "Vietnamese", "zh" to "Chinese")
        fun forLanguage(language: AppLanguage) = if (language == AppLanguage.SPANISH) UiStrings("Biblioteca", "Tus libros y su progreso de escucha", "Añadir PDF, EPUB o texto", "Todavía no has añadido ningún libro.", "Abrir libro", "Modelos", "Se descargan bajo demanda y se ejecutan dentro de BookReader.", "Ajustes", "Preferencias de la aplicación", "capítulos/páginas", "Voz", "Progreso guardado", "fragmento", "Audio preparado", "La posición se guarda automáticamente cada 20 segundos y al pausar.", "Preparando los primeros minutos…", "Reproducir / continuar", "Guardar marcador", "Limpiar audio", "Limpiar caché", "Marcadores", "leyendo ahora", "Experimental: requiere validación adicional", "Seleccionado", "Usar", "Descargado", "Descargar", "Reiniciar progreso", "Detener", "Todos los idiomas", "Idioma de la interfaz", "Modelo importado", "Importar modelo ONNX", "Selecciona el .onnx y tokens.txt; puedes añadir también el .onnx.json y archivos auxiliares. Se guardan dentro de BookReader.", "Código de idioma", "Importar archivos", "Tema del sistema", "El modo oscuro sigue automáticamente la configuración del sistema.", "Guardado de progreso", "El progreso y los marcadores se guardan automáticamente mientras escuchas.") else UiStrings("Library", "Your books and listening progress", "Add PDF, EPUB or text", "You have not added any books yet.", "Open book", "Models", "Downloaded on demand and executed inside BookReader.", "Settings", "Application preferences", "chapters/pages", "Voice", "Saved progress", "fragment", "Audio prepared", "Position is saved automatically every 20 seconds and when paused.", "Preparing the first minutes…", "Play / continue", "Save bookmark", "Clear audio", "Clear cache", "Bookmarks", "reading now", "Experimental: requires additional validation", "Selected", "Use", "Downloaded", "Download", "Reset progress", "Stop", "All languages", "Interface language", "Imported model", "Import ONNX model", "Select the .onnx and tokens.txt; you may also add the .onnx.json and auxiliary files. They are stored inside BookReader.", "Language code", "Import files", "System theme", "Dark mode follows the system setting automatically.", "Progress saving", "Progress and bookmarks are saved automatically while you listen.")
    }
}

private fun ModelFamily.label() = when (this) {
    ModelFamily.PIPER -> "Piper/VITS"
    ModelFamily.COQUI -> "Coqui/VITS"
    ModelFamily.MIMIC3 -> "Mimic3/VITS"
    ModelFamily.KOKORO -> "Kokoro"
    ModelFamily.KITTEN -> "Kitten"
    ModelFamily.SUPERTONIC -> "Supertonic"
}
