package com.audiobookreader.desktop

import com.audiobookreader.data.TtsModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads and installs model archives outside the application package. */
class DesktopModelRepository {
    private val root = modelStorageDirectory()

    fun directory(spec: TtsModelSpec): File = File(root, spec.id)

    fun audioFile(bookPath: String, spec: TtsModelSpec, fragment: Int): File {
        val bookId = bookPath.hashCode().toUInt().toString(16)
        return File(applicationDataDirectory(), "audio-cache/$bookId/${spec.id}/$fragment.wav")
    }

    fun isInstalled(spec: TtsModelSpec): Boolean = File(root, spec.id).let { directory ->
        File(directory, INSTALL_MARKER).isFile && modelFile(directory, spec) != null
    }

    suspend fun download(spec: TtsModelSpec, progress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        check(spec.archiveName.isNotBlank()) { "This voice is online and does not have a downloadable package" }
        val target = File(root, spec.id)
        val installing = File(root, "${spec.id}.installing")
        val archive = File(root, "${spec.id}.part")
        val connection = URL(spec.archiveName).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "BookReader/0.1")
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Download failed: HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            connection.inputStream.use { input -> archive.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                var read: Int
                while (input.read(buffer).also { read = it } >= 0) {
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    copied += read
                    if (total > 0) progress((copied * 100 / total).toInt().coerceIn(0, 100))
                }
            } }
            check(archive.length() > 0L) { "The download was empty" }
            target.deleteRecursively()
            installing.deleteRecursively()
            installing.mkdirs()
            archive.inputStream().buffered().use { compressed ->
                BZip2CompressorInputStream(compressed).use { uncompressed ->
                    TarArchiveInputStream(uncompressed).use { tar ->
                        var entry = tar.nextTarEntry
                        while (entry != null) {
                            val relative = entry.name.trimStart('/').substringAfter('/', entry.name.trimStart('/'))
                            if (relative.isNotBlank()) {
                                val output = File(installing, relative)
                                check(output.canonicalPath.startsWith(installing.canonicalPath + File.separator)) { "Archive entry escaped model directory" }
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
            check(modelFile(installing, spec) != null) { "The package does not contain the expected model files" }
            File(installing, INSTALL_MARKER).writeText(spec.id)
            check(installing.renameTo(target)) { "Could not install the model" }
            progress(100)
        } finally {
            connection.disconnect()
            archive.delete()
            installing.deleteRecursively()
        }
    }

    private fun modelFile(directory: File, spec: TtsModelSpec): File? {
        if (!directory.isDirectory) return null
        return if (spec.modelName.isBlank()) {
            directory.walkTopDown().firstOrNull { it.isFile && it.name == "tts.json" }
        } else {
            directory.walkTopDown().firstOrNull { it.isFile && it.name == spec.modelName }
        }
    }

    private fun applicationDataDirectory(): File {
        // AppImage executes from a temporary mount. APPIMAGE points to the
        // real portable file, so keep data beside the .AppImage itself.
        System.getenv("BOOKREADER_APP_DIR")?.takeIf { it.isNotBlank() }?.let { return File(it) }

        // jpackage launchers run Java from <install>/runtime/bin. Walk up to
        // the installation directory for both Windows and Linux packages.
        val command = ProcessHandle.current().info().command().orElse("")
        var directory = command.takeIf { it.isNotBlank() }?.let(::File)?.parentFile
        repeat(8) {
            if (directory != null && (File(directory, "runtime").isDirectory || File(directory, "lib").isDirectory)) {
                return directory!!
            }
            directory = directory?.parentFile
        }

        // Development runs have no packaged executable; keep their data in
        // the project working directory rather than pretending it is installed.
        return File(System.getProperty("user.dir"))
    }

    private fun modelStorageDirectory(): File {
        val preferred = File(applicationDataDirectory(), "tts-models")
        if ((preferred.isDirectory || preferred.mkdirs()) && preferred.canWrite()) return preferred

        // Program Files and /opt can be read-only for normal users. Keep the
        // app usable in that case while retaining the executable directory as
        // the first choice for portable and per-user installations.
        val fallback = File(
            System.getProperty("user.home"),
            if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
                "AppData/Local/BookReader/tts-models"
            } else {
                ".local/share/BookReader/tts-models"
            },
        )
        fallback.mkdirs()
        return fallback
    }

    companion object { private const val INSTALL_MARKER = ".bookreader-installed" }
}
