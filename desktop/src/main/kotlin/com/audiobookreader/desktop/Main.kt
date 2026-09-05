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
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
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
import com.audiobookreader.data.TextChunker
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "BookReader") {
        MaterialTheme {
            DesktopApp()
        }
    }
}

@Composable
private fun DesktopApp() {
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var text by remember { mutableStateOf("") }
    val chunks = remember(text) { if (text.isBlank()) emptyList() else TextChunker.split(text) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("BookReader", style = MaterialTheme.typography.h4)
        Text("Shared Kotlin core for Android, Linux, and Windows")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                val dialog = FileDialog(null as Frame?, "Open book", FileDialog.LOAD)
                dialog.isVisible = true
                dialog.file?.let { name ->
                    selectedFile = File(dialog.directory, name)
                    text = selectedFile?.readText().orEmpty()
                }
            }) {
                Text("Open text book")
            }
            Text(selectedFile?.name ?: "No book selected")
        }
        Text("Available voice models: ${ModelCatalog.models.size}")
        Divider()
        if (chunks.isEmpty()) {
            Text("Open a text file to preview its shared paragraph segmentation.")
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
}
