package com.elysium.vanguard.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * CONVERSION ENGINE
 *
 * Desktop-class conversions, executed entirely on device:
 *
 *  - image(s)  -> PDF      (one page per image, aspect preserved, EXIF honoured)
 *  - PDF       -> image(s) (one file per page, rendered at print quality)
 *  - PDF       -> DOCX     (text recovered with PDFBox, written as real OOXML)
 *  - DOCX/DOC  -> PDF      (paragraphs, headings and emphasis preserved)
 *  - text/CSV  -> PDF      (wrapped, paginated)
 */
object ConversionEngine {

    // A4 dimensions in points (72 DPI)
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val MARGIN = 50f
    private const val LINE_HEIGHT = 16f
    private const val FONT_SIZE = 11f

    /** Maximum pixel count accepted for a single source image. */
    private const val MAX_IMAGE_PIXELS = 12_000_000L

    /** Render scale used when rasterising a PDF page. */
    private const val PDF_RENDER_SCALE = 2f

    // ── IMAGES -> PDF ────────────────────────────────────────────────────────

    /**
     * Converts one or more images into a single PDF, each image on its own page.
     * Images are scaled to fit A4 while maintaining aspect ratio.
     */
    fun imagesToPdf(imageFiles: List<File>, outputFile: File): Result<File> {
        return try {
            if (imageFiles.isEmpty()) throw IllegalArgumentException("No images selected")
            val pdfDocument = PdfDocument()

            imageFiles.forEachIndexed { index, file ->
                val bitmap = decodeOriented(file) ?: return@forEachIndexed
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas: Canvas = page.canvas

                    val scale = minOf(
                        (A4_WIDTH - (MARGIN * 2)) / bitmap.width,
                        (A4_HEIGHT - (MARGIN * 2)) / bitmap.height
                    )
                    val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
                    val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)

                    val left = (A4_WIDTH - scaledWidth) / 2
                    val top = (A4_HEIGHT - scaledHeight) / 2
                    val destRect = Rect(left, top, left + scaledWidth, top + scaledHeight)
                    canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

                    pdfDocument.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Decodes an image honouring its EXIF orientation and bounding its pixel count.
     */
    private fun decodeOriented(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while ((bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > MAX_IMAGE_PIXELS) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        val rotation = readExifRotation(file)
        if (rotation == 0f) return decoded

        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } catch (_: Throwable) {
            return decoded
        }
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun readExifRotation(file: File): Float = try {
        when (ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (_: Throwable) {
        0f
    }

    // ── PDF -> IMAGES ────────────────────────────────────────────────────────

    /**
     * Renders every page of a PDF into a separate image inside [outputDirectory].
     *
     * @param format "PNG" or "JPG".
     */
    fun pdfToImages(
        pdfFile: File,
        outputDirectory: File,
        format: String = "PNG",
        quality: Int = 92
    ): Result<List<File>> {
        return try {
            if (!pdfFile.isFile) throw IllegalArgumentException("PDF not found: ${pdfFile.name}")
            outputDirectory.mkdirs()

            val outputs = mutableListOf<File>()
            val useJpeg = format.equals("JPG", ignoreCase = true) || format.equals("JPEG", ignoreCase = true)
            val extension = if (useJpeg) "jpg" else "png"

            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { page ->
                            val width = (page.width * PDF_RENDER_SCALE).toInt().coerceAtLeast(1)
                            val height = (page.height * PDF_RENDER_SCALE).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                val matrix = Matrix().apply {
                                    postScale(PDF_RENDER_SCALE, PDF_RENDER_SCALE)
                                }
                                page.render(
                                    bitmap,
                                    null,
                                    matrix,
                                    PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                                )
                                val target = File(
                                    outputDirectory,
                                    "${pdfFile.nameWithoutExtension}_page_%03d.$extension".format(index + 1)
                                )
                                target.outputStream().use { out ->
                                    bitmap.compress(
                                        if (useJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,
                                        quality,
                                        out
                                    )
                                }
                                outputs += target
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
            Result.success(outputs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── PDF -> WORD ──────────────────────────────────────────────────────────

    /**
     * Converts a PDF into a real `.docx` document. Text is recovered page by page
     * with PDFBox and written through [DocxWriter] so the result opens in Word,
     * Pages, Google Docs and LibreOffice.
     */
    fun pdfToWord(context: Context, pdfFile: File, outputFile: File): Result<File> {
        return try {
            if (!pdfFile.isFile) throw IllegalArgumentException("PDF not found: ${pdfFile.name}")
            PDFBoxResourceLoader.init(context.applicationContext)

            val paragraphs = mutableListOf<DocxWriter.Paragraph>()
            PDDocument.load(pdfFile).use { document ->
                val pageCount = document.numberOfPages
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true

                for (page in 1..pageCount) {
                    stripper.startPage = page
                    stripper.endPage = page
                    val text = stripper.getText(document)
                    if (text.isBlank()) continue

                    if (page > 1) {
                        paragraphs += DocxWriter.Paragraph(
                            runs = listOf(DocxWriter.Run("")),
                            type = DocxWriter.BlockType.PARAGRAPH
                        )
                    }

                    text.lines().forEach { rawLine ->
                        val line = rawLine.trimEnd()
                        if (line.isBlank()) {
                            paragraphs += DocxWriter.Paragraph(listOf(DocxWriter.Run("")))
                        } else {
                            paragraphs += DocxWriter.Paragraph(
                                runs = listOf(DocxWriter.Run(line)),
                                type = DocxWriter.BlockType.PARAGRAPH
                            )
                        }
                    }
                }
            }

            if (paragraphs.isEmpty()) {
                throw IllegalStateException("No selectable text found in this PDF")
            }

            outputFile.parentFile?.mkdirs()
            DocxWriter.write(paragraphs, outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── WORD -> PDF ──────────────────────────────────────────────────────────

    /**
     * Converts a DOCX into a PDF keeping paragraph structure, headings and emphasis.
     */
    fun docxToPdf(docxFile: File, outputFile: File): Result<File> {
        return try {
            val paragraphs = parseDocx(docxFile)
            if (paragraphs.isEmpty()) {
                throw IllegalStateException("The document contains no readable text")
            }
            renderParagraphsToPdf(paragraphs, outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Converts a legacy binary `.doc` (or an RTF / HTML file renamed to .doc)
     * into a PDF using best effort text recovery.
     */
    fun docToPdf(docFile: File, outputFile: File): Result<File> {
        return try {
            val paragraphs = recoverLegacyText(docFile)
                .map { DocxWriter.Paragraph(listOf(DocxWriter.Run(it))) }
            if (paragraphs.isEmpty()) {
                throw IllegalStateException("No readable text could be recovered from this document")
            }
            renderParagraphsToPdf(paragraphs, outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Converts a text / code / CSV file into a PDF with proper wrapping.
     */
    fun fileToPdf(inputFile: File, outputFile: File): Result<File> {
        return try {
            val text = inputFile.readText()
            textToPdf(text, outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Converts plain text to a multi-page PDF with proper line wrapping.
     */
    fun textToPdf(text: String, outputFile: File): Result<File> {
        return try {
            val paragraphs = text.lines().map { line ->
                DocxWriter.Paragraph(listOf(DocxWriter.Run(line)))
            }
            renderParagraphsToPdf(paragraphs, outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Merges a mixed selection (images, documents, text) into a single PDF.
     */
    fun filesToPdf(files: List<File>, outputFile: File): Result<File> {
        return try {
            if (files.isEmpty()) throw IllegalArgumentException("No files selected")

            val images = files.filter { isImage(it) }
            val documents = files.filterNot { isImage(it) }

            // BUG FIX: the images and the documents must land in the SAME document.
            // Writing the images first and the documents afterwards replaced the
            // file and silently dropped the images.
            val merged = PdfDocument()
            var pageIndex = 0

            images.forEach { image ->
                val bitmap = decodeOriented(image) ?: return@forEach
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, ++pageIndex).create()
                    val page = merged.startPage(pageInfo)
                    val scale = minOf(
                        (A4_WIDTH - (MARGIN * 2)) / bitmap.width,
                        (A4_HEIGHT - (MARGIN * 2)) / bitmap.height
                    )
                    val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
                    val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
                    val left = (A4_WIDTH - w) / 2
                    val top = (A4_HEIGHT - h) / 2
                    page.canvas.drawBitmap(
                        bitmap, null,
                        Rect(left, top, left + w, top + h),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    )
                    merged.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }

            documents.forEach { document ->
                val paragraphs = when (document.extension.lowercase()) {
                    "docx" -> parseDocx(document)
                    "doc" -> recoverLegacyText(document).map {
                        DocxWriter.Paragraph(listOf(DocxWriter.Run(it)))
                    }
                    else -> runCatching { document.readText() }.getOrDefault("").lines()
                        .map { DocxWriter.Paragraph(listOf(DocxWriter.Run(it))) }
                }
                pageIndex = appendParagraphs(merged, paragraphs, pageIndex)
            }

            if (pageIndex == 0) throw IllegalStateException("Nothing could be converted")

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { merged.writeTo(it) }
            merged.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isImage(file: File): Boolean =
        file.extension.lowercase() in IMAGE_EXTENSIONS

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "bmp", "gif", "heif", "heic"
    )

    // ── Rendering helpers ────────────────────────────────────────────────────

    private fun paintFor(paragraph: DocxWriter.Paragraph): Paint {
        val size = when (paragraph.type) {
            DocxWriter.BlockType.TITLE -> 24f
            DocxWriter.BlockType.HEADING -> 15f
            else -> FONT_SIZE
        }
        val bold = paragraph.type != DocxWriter.BlockType.PARAGRAPH ||
            paragraph.runs.any { it.bold }
        return Paint().apply {
            textSize = size
            color = android.graphics.Color.BLACK
            isAntiAlias = true
            isFakeBoldText = bold
            typeface = android.graphics.Typeface.DEFAULT
        }
    }

    /**
     * Renders a list of paragraphs into a paginated PDF.
     */
    private fun renderParagraphsToPdf(
        paragraphs: List<DocxWriter.Paragraph>,
        outputFile: File
    ): Result<File> {
        val pdfDocument = PdfDocument()
        appendParagraphs(pdfDocument, paragraphs, 0)
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return Result.success(outputFile)
    }

    /**
     * Appends paragraphs to an existing document, wrapping the text and paginating
     * it. Returns the index of the last page written.
     */
    private fun appendParagraphs(
        document: PdfDocument,
        paragraphs: List<DocxWriter.Paragraph>,
        firstPageNumber: Int
    ): Int {
        val usableWidth = A4_WIDTH - (MARGIN * 2)
        val linesPerPage = ((A4_HEIGHT - (MARGIN * 2)) / LINE_HEIGHT).toInt().coerceAtLeast(1)

        val rendered = mutableListOf<RenderedLine>()
        paragraphs.forEach { paragraph ->
            val paint = paintFor(paragraph)
            val spacing = when (paragraph.type) {
                DocxWriter.BlockType.TITLE -> LINE_HEIGHT * 1.8f
                DocxWriter.BlockType.HEADING -> LINE_HEIGHT * 1.4f
                else -> LINE_HEIGHT
            }
            val fullText = paragraph.runs.joinToString("") { it.text }
            if (fullText.isEmpty()) {
                rendered += RenderedLine("", paint, spacing)
                return@forEach
            }
            var remaining = fullText
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, usableWidth, null)
                if (count <= 0) break
                rendered += RenderedLine(remaining.substring(0, count), paint, spacing)
                remaining = remaining.substring(count)
            }
        }

        if (rendered.isEmpty()) return firstPageNumber

        var pageIndex = firstPageNumber
        var index = 0
        while (index < rendered.size) {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, ++pageIndex).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            var y = MARGIN + LINE_HEIGHT
            var drawn = 0
            while (index < rendered.size && drawn < linesPerPage) {
                val line = rendered[index]
                if (line.text.isNotEmpty()) canvas.drawText(line.text, MARGIN, y, line.paint)
                y += line.spacing
                index++
                drawn++
            }
            document.finishPage(page)
        }
        return pageIndex
    }

    private data class RenderedLine(val text: String, val paint: Paint, val spacing: Float)

    // ── DOCX parsing ─────────────────────────────────────────────────────────

    /**
     * Extracts paragraphs, headings and emphasis from a DOCX.
     */
    fun parseDocx(docxFile: File): List<DocxWriter.Paragraph> {
        val result = mutableListOf<DocxWriter.Paragraph>()
        java.util.zip.ZipInputStream(docxFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zis.readBytes().decodeToString()
                    result += parseDocumentXml(xml)
                    break
                }
                entry = zis.nextEntry
            }
        }
        return result
    }

    private fun parseDocumentXml(xml: String): List<DocxWriter.Paragraph> {
        val paragraphs = mutableListOf<DocxWriter.Paragraph>()
        try {
            val document = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
            }.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))

            val body = document.documentElement?.getElementsByTagName("w:body")?.item(0) as? Element
                ?: return paragraphs

            val paragraphElements = body.getElementsByTagName("w:p")
            for (i in 0 until paragraphElements.length) {
                val element = paragraphElements.item(i) as? Element ?: continue
                val runs = mutableListOf<DocxWriter.Run>()
                val runElements = element.getElementsByTagName("w:r")
                for (r in 0 until runElements.length) {
                    val runElement = runElements.item(r) as? Element ?: continue
                    val text = runElement.getElementsByTagName("w:t")
                    if (text.length == 0) continue
                    val sb = StringBuilder()
                    for (t in 0 until text.length) {
                        sb.append(text.item(t).textContent ?: "")
                    }
                    val value = sb.toString()
                    if (value.isEmpty()) continue

                    val properties = runElement.getElementsByTagName("w:rPr")
                    val propsElement = if (properties.length > 0) properties.item(0) as? Element else null
                    val bold = propsElement?.getElementsByTagName("w:b")?.length?.let { it > 0 } ?: false
                    val italic = propsElement?.getElementsByTagName("w:i")?.length?.let { it > 0 } ?: false
                    val underline = propsElement?.getElementsByTagName("w:u")?.length?.let { it > 0 } ?: false
                    var size = 22
                    if (propsElement != null) {
                        val sizeNodes = propsElement.getElementsByTagName("w:sz")
                        if (sizeNodes.length > 0) {
                            size = sizeNodes.item(0).textContent?.toIntOrNull() ?: 22
                        }
                    }

                    runs += DocxWriter.Run(
                        text = value,
                        bold = bold,
                        italic = italic,
                        underline = underline,
                        sizeHalfPoints = size
                    )
                }

                val style = element.getElementsByTagName("w:pStyle")
                val styleValue = if (style.length > 0) {
                    (style.item(0) as? Element)?.getAttribute("w:val") ?: ""
                } else {
                    ""
                }
                val type = when {
                    styleValue.equals("Title", ignoreCase = true) -> DocxWriter.BlockType.TITLE
                    styleValue.startsWith("Heading", ignoreCase = true) -> DocxWriter.BlockType.HEADING
                    styleValue.equals("ListParagraph", ignoreCase = true) -> DocxWriter.BlockType.BULLET
                    else -> DocxWriter.BlockType.PARAGRAPH
                }

                paragraphs += DocxWriter.Paragraph(runs, type)
            }
        } catch (_: Exception) {
            // Fall back to a plain text recovery so the user still gets a document.
            val text = xml
                .replace(Regex("</w:p>"), "\n")
                .replace(Regex("<[^>]*>"), "")
            return text.lines().map { DocxWriter.Paragraph(listOf(DocxWriter.Run(it))) }
        }
        return paragraphs
    }

    // ── Legacy .doc recovery ─────────────────────────────────────────────────

    /**
     * Best effort text recovery from legacy binary Word documents, RTF and HTML.
     */
    fun recoverLegacyText(file: File): List<String> {
        val bytes = file.readBytes()
        val head = String(bytes, 0, minOf(8, bytes.size), Charsets.ISO_8859_1)

        return when {
            head.startsWith("{\\rtf") -> stripRtf(String(bytes, Charsets.ISO_8859_1))
            head.trimStart().startsWith("<") -> stripHtml(String(bytes, Charsets.ISO_8859_1))
            else -> extractPrintableRuns(String(bytes, Charsets.ISO_8859_1))
        }.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun stripRtf(raw: String): List<String> =
        raw.replace(Regex("\\\\[a-zA-Z]+-?[0-9]* ?"), "")
            .replace(Regex("[{}]"), "")
            .split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun stripHtml(raw: String): List<String> =
        raw.replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("<[^>]*>"), "")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun extractPrintableRuns(raw: String): List<String> {
        val builder = StringBuilder()
        val out = mutableListOf<String>()
        raw.forEach { ch ->
            if (ch == '\n' || ch == '\r') {
                if (builder.isNotBlank()) {
                    out += builder.toString()
                    builder.clear()
                }
            } else if (!ch.isISOControl()) {
                builder.append(ch)
            }
        }
        if (builder.isNotBlank()) out += builder.toString()
        return out
    }
}
