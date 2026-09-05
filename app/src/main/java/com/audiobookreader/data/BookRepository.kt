package com.audiobookreader.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

class BookRepository(private val context: Context) {
    init { PDFBoxResourceLoader.init(context) }

    fun import(uri: Uri): Book {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use {
            val index = it.getColumnIndex("_display_name")
            if (it.moveToFirst() && index >= 0) it.getString(index) else null
        } ?: "Libro"
        val lower = name.lowercase()
        val stableId = UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString()
        val chapters = when {
            lower.endsWith(".pdf") -> parsePdf(uri)
            lower.endsWith(".epub") -> parseEpub(uri)
            else -> listOf(Chapter("chapter-1", name.substringBeforeLast('.'), readText(uri)))
        }
        val cover = when {
            lower.endsWith(".pdf") -> extractPdfCover(uri, stableId)
            lower.endsWith(".epub") -> extractEpubCover(uri, stableId)
            else -> null
        }
        return Book(stableId, name.substringBeforeLast('.'), uri, chapters, coverPath = cover)
    }

    private fun coverFile(id: String, extension: String): File =
        File(context.filesDir, "book-covers/$id.$extension").also { it.parentFile?.mkdirs() }

    private fun extractPdfCover(uri: Uri, id: String): String? = runCatching {
        val document = context.contentResolver.openInputStream(uri).use { PDDocument.load(it) }
        document.use { pdf ->
            if (pdf.numberOfPages == 0) return@runCatching null
            val bitmap = PDFRenderer(pdf).renderImageWithDPI(0, 90f)
            val output = coverFile(id, "jpg")
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
            bitmap.recycle()
            output.absolutePath
        }
    }.getOrNull()

    private fun extractEpubCover(uri: Uri, id: String): String? = runCatching {
        val temp = File.createTempFile("cover-", ".epub", context.cacheDir)
        context.contentResolver.openInputStream(uri).use { input -> temp.outputStream().use { output -> input!!.copyTo(output) } }
        try {
            ZipFile(temp).use { zip ->
                val container = Jsoup.parse(zip.getInputStream(zip.getEntry("META-INF/container.xml")), "UTF-8", "", Parser.xmlParser())
                val opfPath = container.select("rootfile").attr("full-path")
                val opf = Jsoup.parse(zip.getInputStream(zip.getEntry(opfPath)), "UTF-8", "", Parser.xmlParser())
                val base = opfPath.substringBeforeLast('/', "")
                val items = opf.select("manifest item")
                val coverId = opf.select("metadata meta[name=cover]").attr("content")
                val item = items.firstOrNull { it.attr("id") == coverId }
                    ?: items.firstOrNull { it.attr("properties").split(' ').contains("cover-image") }
                    ?: items.firstOrNull { it.attr("media-type").startsWith("image/") }
                    ?: return@use null
                val href = item.attr("href").replace("\\", "/")
                val path = if (base.isBlank()) href else "$base/$href".replace("//", "/")
                val entry = zip.getEntry(path) ?: return@use null
                val extension = item.attr("media-type").substringAfter('/', "jpg").substringBefore(';')
                val output = coverFile(id, extension)
                zip.getInputStream(entry).use { input -> output.outputStream().use { out -> input.copyTo(out) } }
                output.absolutePath
            }
        } finally {
            temp.delete()
        }
    }.getOrNull()

    private fun readText(uri: Uri): String = context.contentResolver.openInputStream(uri)!!
        .bufferedReader().use { it.readText() }

    private fun parsePdf(uri: Uri): List<Chapter> {
        val document = context.contentResolver.openInputStream(uri).use { PDDocument.load(it) }
        document.use { pdf ->
            val stripper = PDFTextStripper()
            return pdf.pages.mapIndexed { index, _ ->
                stripper.startPage = index + 1
                stripper.endPage = index + 1
                Chapter("page-${index + 1}", "Página ${index + 1}", stripper.getText(pdf).trim())
            }.filter { it.text.isNotBlank() }
        }
    }

    private fun parseEpub(uri: Uri): List<Chapter> {
        val temp = File.createTempFile("book-", ".epub", context.cacheDir)
        context.contentResolver.openInputStream(uri).use { input -> temp.outputStream().use { output -> input!!.copyTo(output) } }
        return try {
            ZipFile(temp).use { zip ->
                val container = Jsoup.parse(zip.getInputStream(zip.getEntry("META-INF/container.xml")), "UTF-8", "", Parser.xmlParser())
                val opfPath = container.select("rootfile").attr("full-path")
                val opf = Jsoup.parse(zip.getInputStream(zip.getEntry(opfPath)), "UTF-8", "", Parser.xmlParser())
                val base = opfPath.substringBeforeLast('/', "")
                val manifest = opf.select("manifest item").associateBy { it.attr("id") }
                opf.select("spine itemref").mapIndexedNotNull { index, itemref ->
                    val item = manifest[itemref.attr("idref")] ?: return@mapIndexedNotNull null
                    val path = if (base.isBlank()) item.attr("href") else "$base/${item.attr("href")}".replace("//", "/")
                    val entry = zip.getEntry(path) ?: return@mapIndexedNotNull null
                    val html = Jsoup.parse(zip.getInputStream(entry), "UTF-8", "")
                    val text = html.body()?.text()?.trim().orEmpty()
                    if (text.isBlank()) null else Chapter("chapter-${index + 1}", "Capítulo ${index + 1}", text)
                }
            }
        } finally {
            temp.delete()
        }
    }
}
