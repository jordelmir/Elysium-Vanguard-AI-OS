package com.elysium.vanguard.core.util

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * MINIMAL DOCX WRITER
 *
 * Produces a real Office Open XML document (`.docx`) without pulling a heavyweight
 * office library into the APK. A `.docx` is a ZIP container with a fixed set of
 * parts, so writing it directly keeps the output fully under our control and
 * guarantees it opens in Word, Pages, Google Docs and LibreOffice.
 */
object DocxWriter {

    /** A styled run of text inside a paragraph. */
    data class Run(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val sizeHalfPoints: Int = 22
    )

    enum class BlockType { TITLE, HEADING, PARAGRAPH, BULLET }

    data class Paragraph(
        val runs: List<Run>,
        val type: BlockType = BlockType.PARAGRAPH
    )

    /**
     * Writes [paragraphs] as a `.docx` document at [outputFile].
     */
    fun write(paragraphs: List<Paragraph>, outputFile: File): Result<File> = try {
        outputFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            zip.putEntry("[Content_Types].xml", CONTENT_TYPES)
            zip.putEntry("_rels/.rels", RELS)
            zip.putEntry("word/_rels/document.xml.rels", DOCUMENT_RELS)
            zip.putEntry("word/styles.xml", STYLES)
            zip.putEntry("docProps/core.xml", CORE_PROPERTIES)
            zip.putEntry("word/document.xml", buildDocument(paragraphs))
        }
        Result.success(outputFile)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun ZipOutputStream.putEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun buildDocument(paragraphs: List<Paragraph>): String {
        val sb = StringBuilder(16_384)
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")

        paragraphs.forEach { paragraph ->
            sb.append("<w:p>")
            when (paragraph.type) {
                BlockType.TITLE -> sb.append("""<w:pPr><w:pStyle w:val="Title"/></w:pPr>""")
                BlockType.HEADING -> sb.append("""<w:pPr><w:pStyle w:val="Heading1"/></w:pPr>""")
                BlockType.BULLET -> sb.append("""<w:pPr><w:pStyle w:val="ListParagraph"/><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>""")
                BlockType.PARAGRAPH -> Unit
            }
            paragraph.runs.forEach { run ->
                sb.append("<w:r>")
                sb.append("<w:rPr>")
                if (run.bold) sb.append("<w:b/>")
                if (run.italic) sb.append("<w:i/>")
                if (run.underline) sb.append("""<w:u w:val="single"/>""")
                sb.append("""<w:sz w:val="${run.sizeHalfPoints}"/>""")
                sb.append("""<w:szCs w:val="${run.sizeHalfPoints}"/>""")
                sb.append("</w:rPr>")
                sb.append("""<w:t xml:space="preserve">${escape(run.text)}</w:t>""")
                sb.append("</w:r>")
            }
            sb.append("</w:p>")
        }

        sb.append(
            """<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>""" +
                """<w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" """ +
                """w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>"""
        )
        sb.append("</w:body></w:document>")
        return sb.toString()
    }

    private fun escape(raw: String): String {
        val sb = StringBuilder(raw.length + 16)
        raw.forEach { ch ->
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    // Strip control characters that are illegal in XML 1.0.
                    if (ch.code >= 0x20 || ch == '\n' || ch == '\t' || ch == '\r') sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>"""

    private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>"""

    private const val DOCUMENT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val CORE_PROPERTIES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:title>MEET Conversion</dc:title>
<dc:creator>Elysium Vanguard</dc:creator>
</cp:coreProperties>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:style w:type="paragraph" w:styleId="Normal" w:default="1"><w:name w:val="Normal"/><w:rPr><w:sz w:val="22"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:rPr><w:b/><w:sz w:val="48"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:rPr><w:b/><w:sz w:val="32"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/></w:style>
</w:styles>"""
}
