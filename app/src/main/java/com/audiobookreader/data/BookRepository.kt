package com.audiobookreader.data

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
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
        val chapters = when {
            lower.endsWith(".pdf") -> parsePdf(uri)
            lower.endsWith(".epub") -> parseEpub(uri)
            else -> listOf(Chapter("chapter-1", name.substringBeforeLast('.'), readText(uri)))
        }
        val stableId = UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString()
        return Book(stableId, name.substringBeforeLast('.'), uri, chapters)
    }

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
