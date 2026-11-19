package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Storage and management utility for official Papel Timbrado (Letterhead template)
 * used in NR-10 technical reports (Relatórios Técnicos) and certificates (Laudos / Certificados).
 *
 * Supports loading both high-resolution images (PNG/JPEG/WEBP) and PDF files (converting page 1 to high-res A4 bitmap),
 * plus an official generated standard JVM template.
 */
object LetterheadStorageHelper {

    private fun getLetterheadDir(context: Context): File {
        val dir = File(context.filesDir, "company_letterhead")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Persistently saves a selected Image or PDF Uri into internal storage.
     * If the source is a PDF, renders the first page at high A4 resolution.
     * If the source is an Image, loads and saves it as high-resolution PNG.
     * Returns the absolute path to the stored letterhead image file, or null on error.
     */
    fun saveUriToLetterheadFile(context: Context, sourceUri: Uri): String? {
        return try {
            val dir = getLetterheadDir(context)
            val outputFile = File(dir, "letterhead_template_${System.currentTimeMillis()}.png")

            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(sourceUri)?.lowercase() ?: ""
            val uriString = sourceUri.toString().lowercase()
            val isPdf = mimeType.contains("pdf") || uriString.endsWith(".pdf")

            if (isPdf) {
                // Render first page of PDF using Android's native PdfRenderer
                val tempPdf = File(context.cacheDir, "temp_lh_${System.currentTimeMillis()}.pdf")
                contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(tempPdf).use { output ->
                        input.copyTo(output)
                    }
                }

                if (tempPdf.exists() && tempPdf.length() > 0) {
                    val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        // Standard A4 ratio at 200 DPI -> 1654 x 2339 px
                        val targetWidth = 1654
                        val targetHeight = 2339
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        page.close()
                        renderer.close()
                        pfd.close()
                        tempPdf.delete()

                        FileOutputStream(outputFile).use { fos ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                            fos.flush()
                        }
                        bitmap.recycle()
                        return outputFile.absolutePath
                    }
                    renderer.close()
                    pfd.close()
                    tempPdf.delete()
                }
            }

            // Process as Image (PNG / JPG / WEBP)
            val inputStream: InputStream? = contentResolver.openInputStream(sourceUri)
            if (inputStream != null) {
                val tempInput = File(context.cacheDir, "temp_img_${System.currentTimeMillis()}")
                FileOutputStream(tempInput).use { fos ->
                    inputStream.copyTo(fos)
                }
                inputStream.close()

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(tempInput.absolutePath, options)

                val maxDim = 2800 // High-res ceiling for A4 printing
                var inSampleSize = 1
                while (options.outWidth / inSampleSize > maxDim || options.outHeight / inSampleSize > maxDim) {
                    inSampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeFile(tempInput.absolutePath, decodeOptions)
                tempInput.delete()

                if (bitmap != null) {
                    FileOutputStream(outputFile).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        fos.flush()
                    }
                    bitmap.recycle()
                    return outputFile.absolutePath
                } else {
                    // Fallback direct copy
                    contentResolver.openInputStream(sourceUri)?.use { rawIn ->
                        FileOutputStream(outputFile).use { fos ->
                            rawIn.copyTo(fos)
                        }
                    }
                    return outputFile.absolutePath
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Draws the JVM Lightbulb icon with technology/circuitry symbols.
     */
    private fun drawJvmLightbulb(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        isWatermark: Boolean = false
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
            isDither = true
        }

        val alphaVal = if (isWatermark) 14 else 255
        val sx = w / 100f
        val sy = h / 140f

        val domeRadius = 42f * sx
        val domeCenterX = x + 50f * sx
        val domeCenterY = y + 46f * sy

        // Bulb glass contour
        val bulbPath = Path().apply {
            arcTo(
                RectF(
                    domeCenterX - domeRadius,
                    domeCenterY - domeRadius,
                    domeCenterX + domeRadius,
                    domeCenterY + domeRadius
                ),
                145f,
                250f,
                false
            )
            cubicTo(
                domeCenterX + 38f * sx, domeCenterY + 36f * sy,
                domeCenterX + 22f * sx, y + 98f * sy,
                domeCenterX + 18f * sx, y + 108f * sy
            )
            lineTo(domeCenterX - 18f * sx, y + 108f * sy)
            cubicTo(
                domeCenterX - 22f * sx, y + 98f * sy,
                domeCenterX - 38f * sx, domeCenterY + 36f * sy,
                domeCenterX - domeRadius * 0.82f, domeCenterY - domeRadius * 0.57f
            )
            close()
        }

        if (isWatermark) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * sx
            paint.color = Color.parseColor("#0284C7")
            paint.alpha = alphaVal
            canvas.drawPath(bulbPath, paint)

            paint.style = Paint.Style.FILL
            paint.alpha = (alphaVal * 0.45f).toInt()
            canvas.drawPath(bulbPath, paint)
        } else {
            // Gradient fill for bulb interior
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(
                x, y, x + w, y + h,
                intArrayOf(Color.parseColor("#E0F2FE"), Color.parseColor("#BAE6FD"), Color.parseColor("#F0F9FF")),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawPath(bulbPath, paint)
            paint.shader = null

            // Glass stroke
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3.2f * sx
            paint.color = Color.parseColor("#0284C7")
            paint.alpha = alphaVal
            canvas.drawPath(bulbPath, paint)
        }

        // Screw thread ridges at neck
        val threadColor = if (isWatermark) Color.parseColor("#0284C7") else Color.parseColor("#00B4D8")
        paint.style = Paint.Style.FILL
        paint.color = threadColor
        paint.alpha = alphaVal
        for (i in 0..2) {
            val ty = y + (110f + i * 7f) * sy
            val tw = (34f - i * 3f) * sx
            canvas.drawRoundRect(
                RectF(domeCenterX - tw / 2f, ty, domeCenterX + tw / 2f, ty + 5f * sy),
                2.5f * sx, 2.5f * sx, paint
            )
        }
        // Bottom electrical contact
        val contactY = y + 131f * sy
        val cw = 16f * sx
        canvas.drawRoundRect(
            RectF(domeCenterX - cw / 2f, contactY, domeCenterX + cw / 2f, contactY + 5f * sy),
            2.5f * sx, 2.5f * sx, paint
        )

        // Internal technology & circuit network
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * sx
        paint.color = if (isWatermark) Color.parseColor("#0284C7") else Color.parseColor("#0096C7")
        paint.alpha = alphaVal

        // Circuit paths
        canvas.drawLine(domeCenterX - 18f * sx, domeCenterY - 15f * sy, domeCenterX, domeCenterY - 8f * sy, paint)
        canvas.drawLine(domeCenterX, domeCenterY - 8f * sy, domeCenterX + 16f * sx, domeCenterY - 18f * sy, paint)
        canvas.drawLine(domeCenterX, domeCenterY - 8f * sy, domeCenterX, domeCenterY + 16f * sy, paint)
        canvas.drawLine(domeCenterX - 14f * sx, domeCenterY + 14f * sy, domeCenterX + 14f * sx, domeCenterY + 14f * sy, paint)
        canvas.drawLine(domeCenterX, domeCenterY + 16f * sy, domeCenterX, y + 108f * sy, paint)

        // Lightning spark in center
        val sparkPath = Path().apply {
            moveTo(domeCenterX + 2f * sx, domeCenterY - 24f * sy)
            lineTo(domeCenterX - 6f * sx, domeCenterY - 6f * sy)
            lineTo(domeCenterX - 1f * sx, domeCenterY - 6f * sy)
            lineTo(domeCenterX - 4f * sx, domeCenterY + 10f * sy)
            lineTo(domeCenterX + 6f * sx, domeCenterY - 4f * sy)
            lineTo(domeCenterX + 1f * sx, domeCenterY - 4f * sy)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = if (isWatermark) Color.parseColor("#0284C7") else Color.parseColor("#F59E0B")
        paint.alpha = alphaVal
        canvas.drawPath(sparkPath, paint)

        // Circular node pads & small tech symbols
        paint.style = Paint.Style.FILL
        val nodeColors = if (isWatermark) {
            intArrayOf(Color.parseColor("#0284C7"), Color.parseColor("#0284C7"), Color.parseColor("#0284C7"))
        } else {
            intArrayOf(Color.parseColor("#0077B6"), Color.parseColor("#00B4D8"), Color.parseColor("#7C3AED"))
        }

        // Nodes
        paint.color = nodeColors[0]
        paint.alpha = alphaVal
        canvas.drawCircle(domeCenterX - 18f * sx, domeCenterY - 15f * sy, 4.5f * sx, paint)

        paint.color = nodeColors[1]
        canvas.drawCircle(domeCenterX + 16f * sx, domeCenterY - 18f * sy, 4.5f * sx, paint)

        paint.color = nodeColors[2]
        canvas.drawCircle(domeCenterX - 14f * sx, domeCenterY + 14f * sy, 4f * sx, paint)

        paint.color = nodeColors[0]
        canvas.drawCircle(domeCenterX + 14f * sx, domeCenterY + 14f * sy, 4f * sx, paint)

        // Gear icon (upper left)
        val gearX = domeCenterX - 22f * sx
        val gearY = domeCenterY + 2f * sy
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * sx
        paint.color = if (isWatermark) Color.parseColor("#0284C7") else Color.parseColor("#0284C7")
        paint.alpha = alphaVal
        canvas.drawCircle(gearX, gearY, 5f * sx, paint)

        // Screen icon (upper right)
        val scrX = domeCenterX + 12f * sx
        val scrY = domeCenterY - 3f * sy
        canvas.drawRoundRect(RectF(scrX, scrY, scrX + 11f * sx, scrY + 9f * sy), 1.5f * sx, 1.5f * sx, paint)

        // Wifi arcs
        val wifiY = domeCenterY - 26f * sy
        canvas.drawArc(RectF(domeCenterX - 10f * sx, wifiY - 6f * sy, domeCenterX + 10f * sx, wifiY + 6f * sy), 200f, 140f, false, paint)
    }

    /**
     * Draws the horizontal PCB circuit trace divider line in cyan.
     */
    private fun drawCircuitDivider(canvas: Canvas, y: Float, startX: Float, endX: Float, strokeWidth: Float = 3.5f) {
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            color = Color.parseColor("#00A3C4")
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val path = Path().apply {
            moveTo(startX, y)
            lineTo(startX + 160f, y)
            lineTo(startX + 180f, y - 18f)
            lineTo(startX + 270f, y - 18f)
            lineTo(startX + 290f, y)
            lineTo(startX + 480f, y)
            lineTo(startX + 500f, y - 18f)
            lineTo(startX + 590f, y - 18f)
            lineTo(startX + 610f, y)
            lineTo(startX + 820f, y)
            lineTo(startX + 840f, y - 18f)
            lineTo(startX + 930f, y - 18f)
            lineTo(startX + 950f, y)
            lineTo(startX + 1160f, y)
            lineTo(startX + 1180f, y - 18f)
            lineTo(startX + 1270f, y - 18f)
            lineTo(startX + 1290f, y)
            lineTo(endX, y)
        }
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.FILL
        val dotRadius = strokeWidth * 1.8f
        val nodes = floatArrayOf(
            startX + 180f, y - 18f,
            startX + 270f, y - 18f,
            startX + 410f, y,
            startX + 500f, y - 18f,
            startX + 590f, y - 18f,
            startX + 740f, y,
            startX + 840f, y - 18f,
            startX + 930f, y - 18f,
            startX + 1070f, y,
            startX + 1180f, y - 18f,
            startX + 1270f, y - 18f
        )
        for (i in nodes.indices step 2) {
            canvas.drawCircle(nodes[i], nodes[i + 1], dotRadius, paint)
        }
    }

    /**
     * Draws the footer circuit trace line in cyan.
     */
    private fun drawFooterCircuitDivider(canvas: Canvas, y: Float, startX: Float, endX: Float, strokeWidth: Float = 3.5f) {
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            color = Color.parseColor("#00A3C4")
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val path = Path().apply {
            moveTo(startX, y)
            lineTo(startX + 240f, y)
            lineTo(startX + 260f, y - 18f)
            lineTo(startX + 350f, y - 18f)
            lineTo(startX + 370f, y)
            lineTo(endX, y)
        }
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.FILL
        val dotRadius = strokeWidth * 1.8f
        canvas.drawCircle(startX + 260f, y - 18f, dotRadius, paint)
        canvas.drawCircle(startX + 350f, y - 18f, dotRadius, paint)
    }

    /**
     * Generates the official standard A4 Papel Timbrado for JVM Engenharia
     * strictly adhering to the institutional layout:
     * - Top-left: Technological lightbulb logo + "JVM" + "ENGENHARIA & TREINAMENTO"
     * - Top-right: "JVM ENGENHARIA LTDA", CNPJ 29.894.500/0001-04, (61) 98530-0679, joao.nunes@jvmengenharia.com.br
     * - Cyan PCB circuit trace divider line below header
     * - Central subtle security watermark with lightbulb & JVM branding
     * - Footer with cyan circuit trace, navy rule, João Carlos's credentials and website
     */
    fun generateOfficialSampleLetterhead(context: Context): String? {
        return try {
            val dir = getLetterheadDir(context)
            val outputFile = File(dir, "letterhead_template_jvm_oficial.png")
            val legacyFile = File(dir, "letterhead_template_jvm_padrao.png")

            val width = 1654 // 200 DPI A4
            val height = 2339
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Pure White clean background
            canvas.drawColor(Color.WHITE)

            val paint = Paint().apply {
                isAntiAlias = true
                isDither = true
            }

            val arialBold = try { Typeface.create("Arial", Typeface.BOLD) } catch (e: Exception) { Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            val arialNormal = try { Typeface.create("Arial", Typeface.NORMAL) } catch (e: Exception) { Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }

            val margin = 75f
            val rightMargin = width - margin

            // 1. Top Left Header: Logo and JVM Branding
            val logoX = margin
            val logoY = 56f
            val bulbW = 106f
            val bulbH = 148f
            drawJvmLightbulb(canvas, logoX, logoY, bulbW, bulbH, isWatermark = false)

            // Brand Text beside Bulb
            val brandX = logoX + bulbW + 28f
            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.parseColor("#0077B6")
            paint.textSize = 68f
            paint.typeface = arialBold
            canvas.drawText("JVM", brandX, logoY + 76f, paint)

            paint.color = Color.parseColor("#0096C7")
            paint.textSize = 18.5f
            paint.typeface = arialBold
            canvas.drawText("ENGENHARIA & TREINAMENTO", brandX + 2f, logoY + 115f, paint)

            // 2. Top Right Header: Company Legal Info
            paint.textAlign = Paint.Align.RIGHT
            paint.color = Color.parseColor("#0077B6")
            paint.textSize = 29f
            paint.typeface = arialBold
            canvas.drawText("JVM ENGENHARIA LTDA", rightMargin, logoY + 38f, paint)

            paint.color = Color.parseColor("#334155")
            paint.textSize = 22f
            paint.typeface = arialNormal
            canvas.drawText("CNPJ 29.894.500/0001-04", rightMargin, logoY + 70f, paint)
            canvas.drawText("(61) 98530-0679", rightMargin, logoY + 98f, paint)
            canvas.drawText("joao.nunes@jvmengenharia.com.br", rightMargin, logoY + 126f, paint)

            // 3. Header Divider: Cyan PCB Circuit Trace Line
            drawCircuitDivider(canvas, 230f, margin, rightMargin, 3.5f)

            // 4. Subtle Central Security Watermark
            val wmWidth = 380f
            val wmHeight = 500f
            val wmX = (width - wmWidth) / 2f
            val wmY = (height - wmHeight) / 2f - 40f
            drawJvmLightbulb(canvas, wmX, wmY, wmWidth, wmHeight, isWatermark = true)

            // Watermark Text below Bulb
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.parseColor("#0284C7")
            paint.alpha = 14
            paint.textSize = 34f
            paint.typeface = arialBold
            canvas.drawText("JVM ENGENHARIA & TREINAMENTO", width / 2f, wmY + wmHeight + 36f, paint)

            // 5. Bottom Footer Section
            val footerCircuitY = 2170f
            drawFooterCircuitDivider(canvas, footerCircuitY, margin, rightMargin, 3.5f)

            // Solid Navy Accent Line
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4.5f
            paint.color = Color.parseColor("#0A2540")
            paint.alpha = 255
            canvas.drawLine(margin, 2192f, rightMargin, 2192f, paint)

            // Footer Left Text
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.parseColor("#1E293B")
            paint.textSize = 20.5f
            paint.typeface = arialBold
            canvas.drawText(
                "João Carlos E. G. Nunes — Eng. Eletricista e de Segurança do Trabalho · CREA 24481/D-DF",
                margin, 2235f, paint
            )

            paint.color = Color.parseColor("#0077B6")
            paint.textSize = 20.5f
            paint.typeface = arialBold
            canvas.drawText(
                "www.jvmengenharia.com.br · @jvm_engenhariaetreinamento",
                margin, 2268f, paint
            )

            // Footer Right Text: Page number default
            paint.textAlign = Paint.Align.RIGHT
            paint.color = Color.parseColor("#64748B")
            paint.textSize = 19f
            paint.typeface = arialNormal
            canvas.drawText("Página 1 de 1", rightMargin, 2245f, paint)

            // Save official file
            FileOutputStream(outputFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }
            // Also overwrite legacy padrao file so all references get the new design
            try {
                FileOutputStream(legacyFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.flush()
                }
            } catch (_: Exception) {}

            bitmap.recycle()
            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Ensures the official letterhead template exists in storage and returns its path.
     */
    fun ensureOfficialLetterheadExists(context: Context): String? {
        val dir = getLetterheadDir(context)
        val file = File(dir, "letterhead_template_jvm_oficial.png")
        if (file.exists() && file.length() > 5000) {
            return file.absolutePath
        }
        return generateOfficialSampleLetterhead(context)
    }

    /**
     * Retrieves the letterhead bitmap from path/URI, or falls back to generating/loading
     * the official JVM letterhead template.
     */
    fun getOrLoadOfficialLetterhead(
        context: Context,
        pathOrUri: String?,
        reqWidth: Int = 1200,
        reqHeight: Int = 1600
    ): Bitmap? {
        if (!pathOrUri.isNullOrBlank()) {
            val bm = loadLetterheadBitmap(context, pathOrUri, reqWidth, reqHeight)
            if (bm != null) return bm
        }
        val defaultPath = ensureOfficialLetterheadExists(context)
        return if (defaultPath != null) {
            loadLetterheadBitmap(context, defaultPath, reqWidth, reqHeight)
        } else null
    }

    /**
     * Deletes the old letterhead file if it exists in internal storage.
     */
    fun deleteLetterheadFile(filePath: String) {
        try {
            if (filePath.isNotBlank()) {
                val file = File(filePath)
                if (file.exists() && (file.parentFile?.name == "company_letterhead" || file.name.startsWith("letterhead_template_"))) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteLetterhead(context: Context, filePath: String) {
        deleteLetterheadFile(filePath)
    }

    fun generateStandardLetterheadFile(
        context: Context,
        companyName: String = "JVM ENGENHARIA LTDA",
        cnpj: String = "29.894.500/0001-04",
        crea: String = "CREA-DF 1620-PJ",
        phone: String = "(61) 98530-0679",
        email: String = "joao.nunes@jvmengenharia.com.br",
        address: String = "Brasília - DF"
    ): String? {
        return generateOfficialSampleLetterhead(context)
    }

    /**
     * Efficiently loads a sampled bitmap of the letterhead for rendering.
     */
    fun loadLetterheadBitmap(
        context: Context,
        pathOrUri: String,
        reqWidth: Int = 1200,
        reqHeight: Int = 1600
    ): Bitmap? {
        if (pathOrUri.isBlank()) return null
        return try {
            val file = File(pathOrUri)
            if (file.exists()) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                var inSampleSize = 1
                val halfH = options.outHeight / 2
                val halfW = options.outWidth / 2
                while ((halfH / inSampleSize) >= reqHeight && (halfW / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
                options.inSampleSize = inSampleSize.coerceAtLeast(1)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.ARGB_8888
                BitmapFactory.decodeFile(file.absolutePath, options)
            } else {
                val uri = Uri.parse(pathOrUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
