package com.audiobookreader.desktop

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import com.audiobookreader.data.TextChunker
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.util.zip.ZipFile

object DesktopBookReader {
    fun read(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> PDDocument.load(file).use { pdf ->
            PDFTextStripper().apply {
                lineSeparator = "\n"
                paragraphStart = "\n\n"
                paragraphEnd = "\n\n"
            }.getText(pdf)
        }.let(TextChunker::normalizeDocumentText)
        "epub" -> readEpub(file)
        "html", "htm" -> TextChunker.normalizeDocumentText(readHtml(file))
        else -> TextChunker.normalizeDocumentText(file.readText())
    }

    private fun readEpub(file: File): String = ZipFile(file).use { zip ->
        val container = Jsoup.parse(zip.getInputStream(zip.getEntry("META-INF/container.xml")), "UTF-8", "")
        val opfPath = container.select("rootfile").attr("full-path")
        val opf = Jsoup.parse(zip.getInputStream(zip.getEntry(opfPath)), "UTF-8", "")
        val base = opfPath.substringBeforeLast('/', "")
        val manifest = opf.select("manifest item").associateBy { it.attr("id") }
        opf.select("spine itemref").mapNotNull { itemref ->
            val item = manifest[itemref.attr("idref")] ?: return@mapNotNull null
            val path = if (base.isBlank()) item.attr("href") else "$base/${item.attr("href")}".replace("//", "/")
            val entry = zip.getEntry(path) ?: return@mapNotNull null
            val document = Jsoup.parse(zip.getInputStream(entry), "UTF-8", "")
            epubBlocks(document).takeIf { it.isNotBlank() }
        }.joinToString("\n\n").let(TextChunker::normalizeDocumentText)
    }

    private fun epubBlocks(document: Document): String {
        val body = document.body() ?: return ""
        val blocks = body.select("h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,dt,dd,figcaption,caption,tr")
            .map { it.text().trim() }
            .filter(String::isNotBlank)
        return if (blocks.isNotEmpty()) blocks.joinToString("\n\n") else body.text()
    }

    private fun readHtml(file: File): String {
        val document = Jsoup.parse(file, "UTF-8")
        return epubBlocks(document)
    }
}
