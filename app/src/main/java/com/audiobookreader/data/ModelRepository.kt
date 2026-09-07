package com.audiobookreader.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class ModelRepository(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "tts-models").also { it.mkdirs() }
    private val metadata = appContext.getSharedPreferences("bookreader-models", Context.MODE_PRIVATE)

    private fun rootDir(spec: TtsModelSpec) = File(root, spec.id)

    private fun modelFile(spec: TtsModelSpec): File? {
        val rootDir = rootDir(spec)
        if (!rootDir.isDirectory) return null
        if (spec.modelName.isBlank()) return rootDir.walkTopDown().firstOrNull { it.isFile && it.name == "tts.json" }
        return rootDir.walkTopDown().firstOrNull { it.isFile && it.name == spec.modelName }
    }

    fun directory(spec: TtsModelSpec): File {
        val rootDir = rootDir(spec)
        return modelFile(spec)?.parentFile ?: rootDir
    }

    fun isInstalled(spec: TtsModelSpec): Boolean {
        if (spec.family == ModelFamily.EDGE) return true
        val rootDir = rootDir(spec)
        val marker = File(rootDir, INSTALL_MARKER)
        // The marker is written only after the archive has been fully extracted
        // and the expected model file has been found. Keep the model-file
        // fallback so installations made by older app versions remain usable.
        return (marker.isFile && marker.readText() == spec.id) || modelFile(spec) != null
    }

    fun importedModels(): List<TtsModelSpec> = runCatching {
        val array = JSONArray(metadata.getString(KEY_IMPORTED_MODELS, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            TtsModelSpec(
                id = item.optString("id"),
                name = item.optString("name"),
                family = ModelFamily.PIPER,
                language = LanguageCodes.normalize(item.optString("language", "all")),
                archiveName = "",
                modelName = item.optString("modelName"),
                dataDir = item.optString("dataDir", ""),
            ).takeIf { it.id.isNotBlank() && it.modelName.isNotBlank() && isInstalled(it) && File(rootDir(it), "tokens.txt").isFile }
        }
    }.getOrDefault(emptyList())

    fun importOnnx(uris: List<Uri>, language: String, espeakDataTree: Uri?): TtsModelSpec {
        val names = uris.map { displayName(it) to it }
        val model = names.firstOrNull { it.first.lowercase().endsWith(".onnx") }
            ?: error("Selecciona al menos un archivo .onnx")
        val tokens = names.firstOrNull { it.first.lowercase().let { name -> name == "tokens.txt" || name.contains("token") && name.endsWith(".txt") } }
            ?: error("Selecciona también el archivo tokens.txt")
        val id = "local-" + UUID.randomUUID().toString()
        val directory = File(root, id).also { it.mkdirs() }
        try {
            names.distinctBy { it.first }.forEach { (originalName, uri) ->
                val safeName = if (uri == tokens.second) "tokens.txt" else originalName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                check(safeName.isNotBlank()) { "Nombre de archivo no válido" }
                appContext.contentResolver.openInputStream(uri).use { input ->
                    checkNotNull(input) { "No se pudo leer $originalName" }
                    File(directory, safeName).outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (espeakDataTree != null) {
                val espeakDirectory = File(directory, "espeak-ng-data").also { it.mkdirs() }
                copyDocumentTree(espeakDataTree, espeakDirectory)
                check(File(espeakDirectory, "phontab").isFile) {
                    "La carpeta seleccionada no parece ser espeak-ng-data (falta phontab)"
                }
            }
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
        val modelName = model.first.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val spec = TtsModelSpec(
            id = id,
            name = "ONNX local · ${model.first.substringBeforeLast('.')}",
            family = ModelFamily.PIPER,
            language = LanguageCodes.normalize(language.ifBlank { "all" }),
            archiveName = "",
            modelName = modelName,
            dataDir = if (espeakDataTree != null) "espeak-ng-data" else "",
        )
        File(directory, INSTALL_MARKER).writeText(spec.id)
        val saved = JSONArray(metadata.getString(KEY_IMPORTED_MODELS, "[]"))
        saved.put(JSONObject().apply {
            put("id", spec.id)
            put("name", spec.name)
            put("language", spec.language)
            put("modelName", spec.modelName)
            put("dataDir", spec.dataDir)
        })
        metadata.edit().putString(KEY_IMPORTED_MODELS, saved.toString()).apply()
        check(isInstalled(spec)) { "El modelo ONNX no quedó instalado" }
        return spec
    }

    private fun displayName(uri: Uri): String = appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "modelo.onnx"

    private fun copyDocumentTree(treeUri: Uri, target: File) {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        copyDocumentDirectory(
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocumentId),
            treeUri,
            target,
        )
    }

    private fun copyDocumentDirectory(childrenUri: Uri, treeUri: Uri, target: File) {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        appContext.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(idColumn)
                val name = cursor.getString(nameColumn).replace(Regex("[^A-Za-z0-9._-]"), "_")
                if (name.isBlank()) continue
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                val output = File(target, name)
                if (cursor.getString(mimeColumn) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    output.mkdirs()
                    copyDocumentDirectory(
                        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId),
                        treeUri,
                        output,
                    )
                } else {
                    appContext.contentResolver.openInputStream(documentUri).use { input ->
                        checkNotNull(input) { "No se pudo leer el archivo de espeak-ng-data: $name" }
                        output.outputStream().use { outputStream -> input.copyTo(outputStream) }
                    }
                }
            }
        } ?: error("No se pudo leer la carpeta espeak-ng-data")
    }

    suspend fun download(spec: TtsModelSpec, progress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        progress(0)
        val target = rootDir(spec)
        val installing = File(root, "${spec.id}.installing")
        val archive = File(root, "${spec.id}.part")
        val connection = URL(spec.archiveName).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "BookReader/0.1")
        connection.setRequestProperty("Accept", "application/octet-stream")
        connection.connect()
        try {
            check(connection.responseCode in 200..299) { "Descarga fallida: HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            connection.inputStream.use { rawInput -> BufferedInputStream(rawInput, IO_BUFFER_SIZE).use { input ->
                BufferedOutputStream(archive.outputStream(), IO_BUFFER_SIZE).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                var read: Int
                while (input.read(buffer).also { read = it } >= 0) {
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    copied += read
                    if (total > 0) progress((copied * 60 / total).toInt().coerceIn(0, 60))
                }
                }
            } }
            check(archive.length() > 0L) { "La descarga terminó sin datos" }
        } finally {
            connection.disconnect()
        }
        target.deleteRecursively()
        installing.deleteRecursively()
        installing.mkdirs()
        val compressedSize = archive.length()
        CountingInputStream(
            BufferedInputStream(archive.inputStream(), IO_BUFFER_SIZE),
            compressedSize,
        ) { consumed ->
            progress((60 + (consumed * 39 / compressedSize.coerceAtLeast(1L)).toInt()).coerceIn(60, 99))
        }.use { counted ->
            BZip2CompressorInputStream(counted).use { uncompressed ->
                TarArchiveInputStream(uncompressed).use { tar ->
                    var entry = tar.nextTarEntry
                    while (entry != null) {
                        val entryName = entry.name.trimStart('/')
                        val relative = entryName.substringAfter('/', entryName)
                        if (relative.isNotBlank()) {
                            val output = File(installing, relative)
                            check(output.canonicalPath.startsWith(installing.canonicalPath + File.separator)) { "Archivo fuera del modelo" }
                            if (entry.isDirectory) output.mkdirs() else {
                                output.parentFile?.mkdirs()
                                BufferedOutputStream(output.outputStream(), IO_BUFFER_SIZE).use { tar.copyTo(it, IO_BUFFER_SIZE) }
                            }
                        }
                        entry = tar.nextTarEntry
                    }
                }
            }
            }
        }
        archive.delete()
        check(modelFileIn(installing, spec) != null) {
            "El paquete no contiene ${spec.modelName.ifBlank { "los archivos del modelo" }}"
        }
        File(installing, INSTALL_MARKER).writeText(spec.id)
        check(installing.renameTo(target)) { "No se pudo guardar el modelo descargado" }
        progress(100)
    }

    private class CountingInputStream(
        input: InputStream,
        private val totalBytes: Long,
        private val onProgress: (Long) -> Unit,
    ) : FilterInputStream(input) {
        private var consumed = 0L
        private var lastReported = -1

        private fun report() {
            val percentage = (consumed * 100 / totalBytes.coerceAtLeast(1L)).toInt()
            if (percentage != lastReported) {
                lastReported = percentage
                onProgress(consumed)
            }
        }

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                consumed++
                report()
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) {
                consumed += count
                report()
            }
            return count
        }
    }

    private fun modelFileIn(directory: File, spec: TtsModelSpec): File? {
        if (!directory.isDirectory) return null
        if (spec.modelName.isBlank()) return directory.walkTopDown().firstOrNull { it.isFile && it.name == "tts.json" }
        return directory.walkTopDown().firstOrNull { it.isFile && it.name == spec.modelName }
    }

    companion object {
        private const val INSTALL_MARKER = ".bookreader-installed"
        private const val KEY_IMPORTED_MODELS = "imported_models"
        private const val IO_BUFFER_SIZE = 1024 * 1024
    }
}
