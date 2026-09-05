package com.audiobookreader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModelRepository(context: Context) {
    private val root = File(context.filesDir, "tts-models").also { it.mkdirs() }

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
        TarArchiveInputStream(archive.inputStream().buffered()).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                val relative = entry.name.trimStart('/').substringAfter('/', entry.name.trimStart('/'))
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
    }
}
