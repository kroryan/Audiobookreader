package com.audiobookreader.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
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
            ).takeIf { it.id.isNotBlank() && it.modelName.isNotBlank() && isInstalled(it) && File(rootDir(it), "tokens.txt").isFile }
        }
    }.getOrDefault(emptyList())

    fun importOnnx(uris: List<Uri>, language: String): TtsModelSpec {
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
        )
        File(directory, INSTALL_MARKER).writeText(spec.id)
        val saved = JSONArray(metadata.getString(KEY_IMPORTED_MODELS, "[]"))
        saved.put(JSONObject().apply {
            put("id", spec.id)
            put("name", spec.name)
            put("language", spec.language)
            put("modelName", spec.modelName)
        })
        metadata.edit().putString(KEY_IMPORTED_MODELS, saved.toString()).apply()
        check(isInstalled(spec)) { "El modelo ONNX no quedó instalado" }
        return spec
    }

    private fun displayName(uri: Uri): String = appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "modelo.onnx"

    suspend fun download(spec: TtsModelSpec, progress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val target = rootDir(spec)
        val installing = File(root, "${spec.id}.installing")
        val archive = File(root, "${spec.id}.part")
        val connection = URL(spec.archiveName).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.connect()
        check(connection.responseCode in 200..299) { "Descarga fallida: HTTP ${connection.responseCode}" }
        val total = connection.contentLengthLong
        connection.inputStream.use { input -> archive.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var copied = 0L
            var read: Int
            while (input.read(buffer).also { read = it } >= 0) {
                if (read == 0) continue
                output.write(buffer, 0, read)
                copied += read
                if (total > 0) progress((copied * 100 / total).toInt())
            }
        } }
        target.deleteRecursively()
        installing.deleteRecursively()
        installing.mkdirs()
        archive.inputStream().buffered().use { compressed ->
            BZip2CompressorInputStream(compressed).use { uncompressed ->
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
                                output.outputStream().use { tar.copyTo(it) }
                            }
                        }
                        entry = tar.nextTarEntry
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
    }

    private fun modelFileIn(directory: File, spec: TtsModelSpec): File? {
        if (!directory.isDirectory) return null
        if (spec.modelName.isBlank()) return directory.walkTopDown().firstOrNull { it.isFile && it.name == "tts.json" }
        return directory.walkTopDown().firstOrNull { it.isFile && it.name == spec.modelName }
    }

    companion object {
        private const val INSTALL_MARKER = ".bookreader-installed"
        private const val KEY_IMPORTED_MODELS = "imported_models"
    }
}
