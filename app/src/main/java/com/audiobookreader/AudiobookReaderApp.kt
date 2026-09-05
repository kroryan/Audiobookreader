package com.audiobookreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiobookreader.data.Book
import com.audiobookreader.data.ModelCatalog
import com.audiobookreader.data.ModelFamily
import com.audiobookreader.data.TextChunker
import com.audiobookreader.data.TtsModelSpec
import com.audiobookreader.playback.WavFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AudiobookReaderApp(viewModel: ReaderViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("📚") }, label = { Text("Libros") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("🔊") }, label = { Text("Modelos") })
            }
        }
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding)) {
            if (tab == 0) LibraryScreen(state, viewModel) else ModelScreen(state, viewModel)
        }
    }
}

@Composable
private fun LibraryScreen(state: ReaderState, viewModel: ReaderViewModel) {
    val context = LocalContext.current
    var openedBookId by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBook)
    }
    val openedBook = state.books.firstOrNull { it.id == openedBookId }
    if (openedBook != null) {
        BookDetailScreen(openedBook, state, viewModel, context) { openedBookId = null }
        return
    }
    Box(Modifier.fillMaxSize()) {
        ShelfBackground()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Biblioteca", style = MaterialTheme.typography.headlineMedium)
                Text("Tus libros y su progreso de escucha")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { picker.launch(arrayOf("application/pdf", "application/epub+zip", "text/plain", "text/html")) }) {
                    Text("Añadir PDF, EPUB o texto")
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            if (state.books.isEmpty()) item { Text("Todavía no has añadido ningún libro.") }
            items(state.books.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { book -> BookCard(book, state, viewModel, Modifier.weight(1f)) { openedBookId = book.id } }
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
private fun BookCard(book: Book, state: ReaderState, viewModel: ReaderViewModel, modifier: Modifier, onOpen: () -> Unit) {
    Card(modifier.padding(6.dp).clickable {
        viewModel.selectBook(book)
        onOpen()
    }) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BookCover(book, Modifier.fillMaxWidth().height(190.dp))
            Spacer(Modifier.height(8.dp))
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            if (state.selectedBook?.id == book.id) Text(state.progress?.let { "${it.percentage}%" } ?: "0%") else Text("Abrir libro")
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
private fun BookDetailScreen(book: Book, state: ReaderState, viewModel: ReaderViewModel, context: android.content.Context, onBack: () -> Unit) {
    val chunks = remember(book.id) { book.chapters.flatMap { chapter -> TextChunker.split(chapter.text).map { Triple(chapter.id, chapter.title, it) } } }
    val chunkDurations by produceState<Map<Int, Long>>(emptyMap(), book.id, state.selectedModel.id, chunks.size) {
        value = withContext(Dispatchers.IO) {
            chunks.indices.associateWith { index ->
                val file = File(context.cacheDir, "audio/${book.id}/${state.selectedModel.id}/${chunks[index].first}-$index.wav")
                if (file.isFile) WavFile.durationMs(file) else 0L
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("‹ Biblioteca") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BookCover(book, Modifier.size(width = 120.dp, height = 170.dp).clip(MaterialTheme.shapes.medium))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.headlineSmall)
                    Text("${book.chapters.size} capítulos/páginas")
                    Text("Voz: ${state.selectedModel.name}")
                }
            }
        }
        item {
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.progress?.let { Text("Progreso guardado: ${it.percentage}% · fragmento ${it.itemIndex + 1}/${it.itemCount}") }
            state.cacheStatus?.let { Text("Audio preparado: ${it.percentage}% · ${it.sizeLabel}") }
            Text("La posición se guarda automáticamente cada 20 segundos y al pausar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.generating) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Preparando los primeros minutos…")
            } else {
                Button(onClick = { viewModel.playSelected(context) }) { Text("▶ Reproducir / continuar") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = viewModel::addBookmark) { Text("Guardar marcador manual") }
                TextButton(onClick = viewModel::clearSelectedBookCache) { Text("Limpiar audio") }
                TextButton(onClick = viewModel::clearAllAudioCache) { Text("Limpiar caché") }
            }
        }
        if (state.bookmarks.isNotEmpty()) {
            item {
                Text("Marcadores", style = MaterialTheme.typography.titleMedium)
                state.bookmarks.forEach { Text("• ${it.label}") }
            }
        }
        itemsIndexed(chunks) { index, chunk ->
            val active = state.progress?.itemIndex == index
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    if (active) "${chunk.second} · leyendo ahora" else chunk.second,
                    style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    if (active) wordHighlight(chunk.third, state.progress?.positionMs ?: 0L, chunkDurations[index] ?: 0L)
                    else buildAnnotatedString { append(chunk.third) },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = if (active) 22.sp else 18.sp,
                        lineHeight = if (active) 34.sp else 29.sp,
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (index < chunks.lastIndex) Divider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }
        }
    }
}

@Composable
private fun wordHighlight(text: String, positionMs: Long, durationMs: Long) = buildAnnotatedString {
    val words = Regex("\\S+").findAll(text).toList()
    if (words.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    // TTS does not expose phoneme timestamps. A real WAV duration is much
    // closer than a fixed milliseconds-per-word estimate.
    val usableDuration = durationMs.takeIf { it > 0L } ?: (words.size * 400L)
    val activeIndex = ((positionMs.coerceIn(0L, usableDuration) * words.size) / usableDuration.coerceAtLeast(1L))
        .toInt().coerceIn(0, words.lastIndex)
    var cursor = 0
    words.forEachIndexed { index, match ->
        append(text.substring(cursor, match.range.first))
        withStyle(if (index == activeIndex) SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) else SpanStyle()) { append(match.value) }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

@Composable
private fun ModelScreen(state: ReaderState, viewModel: ReaderViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Modelos y voces", style = MaterialTheme.typography.headlineMedium)
            Text("Se descargan bajo demanda y se ejecutan en el teléfono.")
        }
        items(ModelCatalog.models) { spec -> ModelCard(spec, state, viewModel) }
    }
}

@Composable
private fun ModelCard(spec: TtsModelSpec, state: ReaderState, viewModel: ReaderViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(spec.name, style = MaterialTheme.typography.titleMedium)
            Text("${spec.family.label()} · ${spec.language}")
            if (spec.experimental) Text("Experimental: requiere validación adicional")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (state.selectedModel.id == spec.id) Text("Seleccionado  ")
                TextButton(onClick = { viewModel.selectModel(spec) }) { Text("Usar") }
                if (state.installed.contains(spec.id)) Text("Descargado")
                else if (state.downloading == spec.id) Text("${state.downloadProgress}%")
                else TextButton(onClick = { viewModel.downloadModel(spec) }) { Text("Descargar") }
            }
            if (state.downloading == spec.id) LinearProgressIndicator((state.downloadProgress / 100f).coerceIn(0f, 1f), Modifier.fillMaxWidth())
        }
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
