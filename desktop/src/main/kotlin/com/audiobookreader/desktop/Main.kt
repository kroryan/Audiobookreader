package com.audiobookreader.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TextField
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.audiobookreader.data.ModelCatalog
import com.audiobookreader.data.AppLanguage
import com.audiobookreader.data.TextChunker
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "BookReader") {
        DesktopApp()
    }
}

@Composable
private fun DesktopApp() {
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var text by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedModelId by remember { mutableStateOf(ModelCatalog.models.firstOrNull()?.id.orEmpty()) }
    var interfaceLanguage by remember { mutableStateOf(AppLanguage.ENGLISH) }
    var darkMode by remember { mutableStateOf(false) }
    var defaultSpeed by remember { mutableStateOf(1f) }

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
                0 -> LibraryScreen(
                    selectedFile = selectedFile,
                    text = text,
                    onOpen = {
                        val dialog = FileDialog(null as Frame?, "Open book", FileDialog.LOAD)
                        dialog.isVisible = true
                        dialog.file?.let { name ->
                            selectedFile = File(dialog.directory, name)
                            text = selectedFile?.readText().orEmpty()
                        }
                    },
                )
                1 -> ModelsScreen(selectedModelId) { selectedModelId = it }
                else -> SettingsScreen(interfaceLanguage, darkMode, defaultSpeed, { interfaceLanguage = it }, { darkMode = it }, { defaultSpeed = it })
            }
        }
    }
}

@Composable
private fun LibraryScreen(selectedFile: File?, text: String, onOpen: () -> Unit) {
    val chunks = remember(text) { if (text.isBlank()) emptyList() else TextChunker.split(text) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onOpen) { Text("Open book") }
        Text(selectedFile?.name ?: "No book selected")
    }
    Text("Shared paragraph segmentation: ${chunks.size} fragments")
    Divider()
    if (chunks.isEmpty()) {
        Text("Open a text file to preview its paragraph-aware segmentation.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chunks.take(20)) { chunk ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(chunk, modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
}

@Composable
private fun ModelsScreen(selectedModelId: String, onModelSelected: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val models = remember(query) {
        ModelCatalog.models.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) || it.language.contains(query, ignoreCase = true)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Models", style = MaterialTheme.typography.h5)
        Text("Choose the voice model used for audiobook playback.")
        TextField(value = query, onValueChange = { query = it }, label = { Text("Filter by model or language") }, modifier = Modifier.fillMaxWidth())
        Text("${ModelCatalog.models.size} model packages available on demand")
        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(models) { model ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(model.name, style = MaterialTheme.typography.subtitle1)
                            Text("${model.family} · ${model.language}")
                            Text("Downloaded on demand; model files are kept outside the application package.")
                        }
                        Button(onClick = { onModelSelected(model.id) }) {
                            Text(if (model.id == selectedModelId) "Selected" else "Select")
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
