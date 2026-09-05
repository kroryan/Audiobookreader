package com.audiobookreader.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.AlertDialog
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.audiobookreader.data.ModelCatalog
import com.audiobookreader.data.AppLanguage
import com.audiobookreader.data.TextChunker
import com.audiobookreader.data.TtsModelSpec
import kotlinx.coroutines.launch
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
    val modelRepository = remember { DesktopModelRepository() }
    var downloadedModels by remember { mutableStateOf(ModelCatalog.models.filter(modelRepository::isInstalled).map { it.id }.toSet()) }
    var downloadingModel by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0) }
    var pendingLicenseModel by remember { mutableStateOf<TtsModelSpec?>(null) }
    var availableModels by remember { mutableStateOf(ModelCatalog.models) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        availableModels = ModelCatalog.models + runCatching { DesktopEdgeVoiceRepository().load() }.getOrDefault(emptyList())
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
