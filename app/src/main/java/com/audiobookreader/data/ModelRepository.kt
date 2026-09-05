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

    fun directory(spec: TtsModelSpec) = File(root, spec.id)

    fun isInstalled(spec: TtsModelSpec): Boolean {
        val dir = directory(spec)
        return dir.isDirectory && (spec.modelName.isBlank() || File(dir, spec.modelName).exists())
    }

    suspend fun download(spec: TtsModelSpec, progress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val target = directory(spec)
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
        target.mkdirs()
        TarArchiveInputStream(archive.inputStream().buffered()).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                val relative = entry.name.substringAfter('/', entry.name)
                if (relative.isNotBlank()) {
                    val output = File(target, relative)
                    check(output.canonicalPath.startsWith(target.canonicalPath + File.separator)) { "Archivo fuera del modelo" }
                    if (entry.isDirectory) output.mkdirs() else {
                        output.parentFile?.mkdirs()
                        output.outputStream().use { tar.copyTo(it) }
                    }
                }
                entry = tar.nextTarEntry
            }
        }
        archive.delete()
        check(isInstalled(spec)) { "El paquete no contiene el modelo esperado" }
    }
}
