package com.audiobookreader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiobookreader.data.ModelFamily
import com.audiobookreader.data.ModelCatalog
import com.audiobookreader.data.TtsModelSpec

@Composable
fun AudiobookReaderApp(viewModel: ReaderViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = {}, label = { Text("Libros") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = {}, label = { Text("Modelos") })
            }
        }
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> LibraryScreen(state, viewModel)
                else -> ModelScreen(state, viewModel)
            }
        }
    }
}

@Composable
private fun LibraryScreen(state: ReaderState, viewModel: ReaderViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBook)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("BookReader", style = MaterialTheme.typography.headlineMedium)
            Text("Lee tus libros con voces locales, incluso con la pantalla bloqueada.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = { picker.launch(arrayOf("application/pdf", "application/epub+zip", "text/plain", "text/html")) }) {
                Text("Abrir PDF, EPUB o texto")
            }
        }
        state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
        state.selectedBook?.let { book ->
            item {
                Text(book.title, style = MaterialTheme.typography.titleLarge)
                Text("${book.chapters.size} capítulos/páginas")
                Spacer(Modifier.height(8.dp))
                Text("Voz: ${state.selectedModel.name}")
                state.progress?.let { progress ->
                    Text("Progreso: ${progress.percentage}% · fragmento ${progress.itemIndex + 1}/${progress.itemCount}")
                }
                state.cacheStatus?.let { cache ->
                    Text("Audio preparado: ${cache.percentage}% · ${cache.sizeLabel}")
                }
                Spacer(Modifier.height(4.dp))
                if (state.generating) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Preparando audio local…")
                } else {
                    Button(onClick = { viewModel.playSelected(context) }) {
                        Text("▶ Reproducir")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::addBookmark) { Text("Marcapáginas") }
                    TextButton(onClick = viewModel::clearSelectedBookCache) { Text("Limpiar audio") }
                    TextButton(onClick = viewModel::clearAllAudioCache) { Text("Limpiar caché") }
                }
                if (state.bookmarks.isNotEmpty()) {
                    Text("Marcapáginas", style = MaterialTheme.typography.titleMedium)
                    state.bookmarks.forEach { bookmark -> Text("• ${bookmark.label}") }
                }
            }
            items(book.chapters) { chapter ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(chapter.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(chapter.text.take(700) + if (chapter.text.length > 700) "…" else "")
                    }
                }
            }
        }
        if (state.books.isEmpty()) {
            item { Text("Todavía no has añadido ningún libro.") }
        }
    }
}

@Composable
private fun ModelScreen(state: ReaderState, viewModel: ReaderViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Modelos y voces", style = MaterialTheme.typography.headlineMedium)
            Text("Se descargan bajo demanda y se ejecutan en el teléfono.")
        }
        items(ModelCatalog.models) { spec ->
            ModelCard(spec, state, viewModel)
        }
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
                if (state.installed.contains(spec.id)) {
                    Text("Descargado")
                } else if (state.downloading == spec.id) {
                    Text("${state.downloadProgress}%")
                } else {
                    TextButton(onClick = { viewModel.downloadModel(spec) }) { Text("Descargar") }
                }
            }
            if (state.downloading == spec.id) LinearProgressIndicator(
                (state.downloadProgress / 100f).coerceIn(0f, 1f),
                Modifier.fillMaxWidth()
            )
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
