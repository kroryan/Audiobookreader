package com.audiobookreader.desktop

import com.audiobookreader.data.TtsModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.prefs.Preferences
import com.audiobookreader.data.LanguageCodes
import com.audiobookreader.data.ModelFamily

/** Downloads and installs model archives outside the application package. */
class DesktopModelRepository {
    private val root = modelStorageDirectory()
    private val metadata = Preferences.userRoot().node("com.audiobookreader.models")

    fun directory(spec: TtsModelSpec): File = File(root, spec.storageId)

    // Use the same writable location as models, including installed /opt builds.
    fun audioCache(): DesktopAudioCache = DesktopAudioCache(File(root.parentFile, "audio-cache"))

    fun importedModels(): List<TtsModelSpec> = runCatching {
        metadata.keys().mapNotNull { key ->
            val directory = File(root, key)
            val modelName = metadata.get("$key.model", "")
            if (!directory.isDirectory || modelName.isBlank()) return@mapNotNull null
            TtsModelSpec(
                id = key,
                name = metadata.get("$key.name", "Imported ONNX · $modelName"),
                family = ModelFamily.PIPER,
                language = metadata.get("$key.language", "all"),
                archiveName = "",
                modelName = modelName,
                requiredFiles = listOf("tokens.txt"),
                storageId = key,
            ).takeIf(::isInstalled)
        }
    }.getOrDefault(emptyList())

    fun importModel(files: List<File>, languageCode: String): TtsModelSpec {
        val model = files.firstOrNull { it.extension.equals("onnx", ignoreCase = true) }
            ?: error("Select at least one .onnx file")
        check(files.any { it.name.equals("tokens.txt", ignoreCase = true) }) {
            "Select tokens.txt as well"
        }
        val language = LanguageCodes.normalizeImportCode(languageCode)
        val id = "local-${UUID.randomUUID()}"
        val staging = File(root, "$id.installing")
        val target = File(root, id)
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            files.distinctBy { it.name.lowercase() }.forEach { source ->
                check(source.isFile) { "Not a file: ${source.name}" }
                val safeName = source.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                source.copyTo(File(staging, safeName), overwrite = true)
            }
            val modelName = model.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            check(File(staging, modelName).isFile) { "Could not import the ONNX model" }
            check(File(staging, "tokens.txt").isFile) { "Could not import tokens.txt" }
            File(staging, INSTALL_MARKER).writeText(id)
            check(staging.renameTo(target)) { "Could not activate the imported model" }
            val spec = TtsModelSpec(
                id = id,
                name = "Imported ONNX · ${model.name.substringBeforeLast('.')}",
                family = ModelFamily.PIPER,
                language = language,
                archiveName = "",
                modelName = modelName,
                requiredFiles = listOf("tokens.txt"),
                storageId = id,
            )
            metadata.put("$id.name", spec.name)
            metadata.put("$id.language", spec.language)
            metadata.put("$id.model", spec.modelName)
            metadata.flush()
            check(isInstalled(spec)) { "The ONNX model was not installed correctly" }
            return spec
        } finally {
            staging.deleteRecursively()
        }
    }

    fun isInstalled(spec: TtsModelSpec): Boolean = File(root, spec.storageId).let { directory ->
        File(directory, INSTALL_MARKER).isFile && modelFile(directory, spec) != null &&
            spec.requiredFiles.all { required -> directory.walkTopDown().any { it.isFile && it.name == required } } &&
            (spec.auxiliaryName.isBlank() || directory.walkTopDown().any { it.isFile && it.name == spec.auxiliaryName })
    }

    suspend fun download(spec: TtsModelSpec, progress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        if (spec.remoteFiles.isNotEmpty()) {
            downloadRemoteFiles(spec, progress)
            return@withContext
        }
        check(spec.archiveName.isNotBlank()) { "This voice is online and does not have a downloadable package" }
        val target = File(root, spec.storageId)
        val installing = File(root, "${spec.storageId}.installing")
        val archive = File(root, "${spec.storageId}.part")
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
            if (spec.auxiliaryUrl.isNotBlank() && spec.auxiliaryName.isNotBlank()) {
                val auxiliary = File(installing, spec.auxiliaryName)
                val temporary = File(installing, ".${spec.auxiliaryName}.part")
                downloadAuxiliary(spec.auxiliaryUrl, temporary) { auxiliaryProgress ->
                    progress((90 + auxiliaryProgress * 9 / 100).coerceIn(90, 99))
                }
                check(temporary.renameTo(auxiliary)) { "Could not install the auxiliary model file" }
            }
            check(spec.requiredFiles.all { required -> installing.walkTopDown().any { it.isFile && it.name == required } }) {
                "The package does not contain all required model files"
            }
            File(installing, INSTALL_MARKER).writeText(spec.storageId)
            check(installing.renameTo(target)) { "Could not install the model" }
            progress(100)
        } finally {
            connection.disconnect()
            archive.delete()
            installing.deleteRecursively()
        }
    }

    private fun downloadRemoteFiles(spec: TtsModelSpec, progress: (Int) -> Unit) {
        val target = File(root, spec.storageId)
        val installing = File(root, "${spec.storageId}.installing")
        installing.deleteRecursively()
        installing.mkdirs()
        try {
            spec.remoteFiles.forEachIndexed { index, remote ->
                check(remote.fileName == File(remote.fileName).name && remote.fileName.isNotBlank()) {
                    "Invalid remote model file name"
                }
                val temporary = File(installing, ".${remote.fileName}.part")
                val destination = File(installing, remote.fileName)
                downloadAuxiliary(remote.url, temporary) { fileProgress ->
                    val start = index * 90 / spec.remoteFiles.size
                    val span = 90 / spec.remoteFiles.size
                    val withinFile = fileProgress * span / 100
                    progress((start + withinFile).coerceIn(0, 95))
                }
                check(temporary.renameTo(destination)) { "Could not install ${remote.fileName}" }
            }
            check(spec.requiredFiles.all { required ->
                File(installing, required).isFile && File(installing, required).length() > 0
            }) { "The downloaded model is incomplete" }
            File(installing, INSTALL_MARKER).writeText(spec.storageId)
            val backup = File(root, "${spec.storageId}.backup")
            backup.deleteRecursively()
            if (target.exists()) check(target.renameTo(backup)) { "Could not reserve the previous model" }
            if (!installing.renameTo(target)) {
                backup.renameTo(target)
                error("Could not activate the downloaded model")
            }
            backup.deleteRecursively()
            progress(100)
        } finally {
            installing.deleteRecursively()
        }
    }

    private fun modelFile(directory: File, spec: TtsModelSpec): File? {
        if (!directory.isDirectory) return null
        if (spec.modelName.isBlank()) {
            val expected = spec.requiredFiles.firstOrNull() ?: "tts.json"
            return directory.walkTopDown().firstOrNull { it.isFile && it.name == expected }
        }
        return directory.walkTopDown().firstOrNull { it.isFile && it.name == spec.modelName }
    }

    private fun downloadAuxiliary(url: String, target: File, progress: (Int) -> Unit) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "BookReader/0.1")
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Auxiliary download failed: HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            var copied = 0L
            connection.inputStream.use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read: Int
                while (input.read(buffer).also { read = it } >= 0) {
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    copied += read
                    if (total > 0) progress((copied * 100 / total).toInt().coerceIn(0, 100))
                }
            } }
            check(target.length() > 0L) { "The auxiliary download was empty" }
        } finally {
            connection.disconnect()
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
