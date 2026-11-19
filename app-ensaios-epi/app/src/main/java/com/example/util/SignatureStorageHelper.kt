package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object SignatureStorageHelper {

    private fun getSignaturesDir(context: Context): File {
        val dir = File(context.filesDir, "signatures")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Generates and returns the official signature image for Eng. João Carlos Evaristo Guedes Nunes (CREA 24481/D-DF).
     */
    fun getOrCreateDefaultJoaoSignature(context: Context): String {
        return try {
            val dir = getSignaturesDir(context)
            val file = File(dir, "sig_joao_carlos_crea24481.png")
            if (!file.exists() || file.length() < 100) {
                val widthPx = 800
                val heightPx = 300
                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.TRANSPARENT)

                // Blue ink pen
                val bluePaint = Paint().apply {
                    color = android.graphics.Color.rgb(11, 56, 185) // Vivid Blue ink #0B38B9
                    style = Paint.Style.STROKE
                    strokeWidth = 4.5f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    isAntiAlias = true
                }

                // 1. Left loop "João"
                val pathLeft = Path().apply {
                    moveTo(35f, 155f)
                    cubicTo(25f, 195f, 30f, 245f, 45f, 255f)
                    cubicTo(70f, 260f, 85f, 140f, 92f, 50f)
                    cubicTo(96f, 35f, 82f, 40f, 75f, 70f)
                    cubicTo(68f, 130f, 70f, 220f, 78f, 250f)
                    // "o-ã-o" flourish
                    moveTo(115f, 150f)
                    cubicTo(125f, 135f, 145f, 140f, 140f, 160f)
                    cubicTo(135f, 175f, 118f, 170f, 120f, 150f)
                    moveTo(150f, 165f)
                    cubicTo(160f, 140f, 175f, 140f, 180f, 165f)
                    moveTo(160f, 132f)
                    quadTo(170f, 126f, 180f, 132f) // til
                    moveTo(195f, 160f)
                    cubicTo(205f, 140f, 220f, 145f, 215f, 168f)
                    cubicTo(210f, 180f, 195f, 175f, 195f, 160f)
                }
                canvas.drawPath(pathLeft, bluePaint)

                // 2. Middle loop "Carlos"
                val pathMid = Path().apply {
                    moveTo(245f, 240f)
                    cubicTo(270f, 180f, 295f, 90f, 312f, 55f)
                    cubicTo(325f, 35f, 340f, 50f, 335f, 95f)
                    cubicTo(325f, 170f, 290f, 230f, 300f, 245f)
                    cubicTo(310f, 260f, 370f, 180f, 395f, 120f)
                    cubicTo(415f, 70f, 435f, 85f, 430f, 140f)
                    cubicTo(425f, 190f, 400f, 235f, 435f, 230f)
                    cubicTo(450f, 225f, 470f, 170f, 490f, 155f)
                }
                canvas.drawPath(pathMid, bluePaint)

                // 3. Right flourish "E. G. Nunes"
                val pathRight = Path().apply {
                    moveTo(505f, 235f)
                    cubicTo(535f, 160f, 555f, 90f, 570f, 75f)
                    cubicTo(585f, 60f, 575f, 110f, 560f, 185f)
                    cubicTo(550f, 235f, 570f, 240f, 595f, 200f)
                    // "nus" loop
                    moveTo(615f, 170f)
                    cubicTo(630f, 100f, 655f, 80f, 665f, 85f)
                    cubicTo(680f, 95f, 660f, 175f, 665f, 215f)
                    cubicTo(670f, 240f, 715f, 210f, 740f, 215f)
                    cubicTo(755f, 218f, 770f, 210f, 780f, 220f)
                }
                canvas.drawPath(pathRight, bluePaint)

                // 4. Center Typed Text (slightly tilted like official stamp)
                canvas.save()
                canvas.rotate(-8f, 360f, 175f)

                val arialBold = try { android.graphics.Typeface.create("Arial", android.graphics.Typeface.BOLD) } catch (e: Exception) { android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD) }
                val arialNormal = try { android.graphics.Typeface.create("Arial", android.graphics.Typeface.NORMAL) } catch (e: Exception) { android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL) }

                val textPaintName = Paint().apply {
                    color = android.graphics.Color.rgb(15, 23, 42)
                    textSize = 23f
                    typeface = arialBold
                    isAntiAlias = true
                }
                val textPaintRole = Paint().apply {
                    color = android.graphics.Color.rgb(30, 41, 59)
                    textSize = 19f
                    typeface = arialNormal
                    isAntiAlias = true
                }
                val textPaintCrea = Paint().apply {
                    color = android.graphics.Color.rgb(15, 23, 42)
                    textSize = 21f
                    typeface = arialBold
                    isAntiAlias = true
                }

                canvas.drawText("João Carlos E. G. Nunes", 215f, 145f, textPaintName)
                canvas.drawText("Engenheiro Eletricista", 238f, 172f, textPaintRole)
                canvas.drawText("CREA - 24481/D-DF", 262f, 199f, textPaintCrea)

                canvas.restore()

                val fos = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
                fos.close()
                bitmap.recycle()
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Copies a selected Image Uri into the internal app storage for persistent signature usage.
     */
    fun saveUriToSignatureFile(context: Context, sourceUri: Uri, prefix: String = "sig"): String? {
        return try {
            val dir = getSignaturesDir(context)
            val file = File(dir, "${prefix}_${System.currentTimeMillis()}.png")
            val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            if (inputStream != null) {
                val outputStream = FileOutputStream(file)
                inputStream.copyTo(outputStream)
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                file.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Renders a list of touch / stylus offsets to a PNG Bitmap and saves it to internal storage.
     */
    fun savePointsToSignatureFile(
        context: Context,
        points: List<Offset?>,
        widthPx: Int = 800,
        heightPx: Int = 300,
        prefix: String = "draw_sig"
    ): String? {
        if (points.isEmpty()) return null
        return try {
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT)

            val paint = Paint().apply {
                color = 0xFF0A192F.toInt() // Navy Primary
                style = Paint.Style.STROKE
                strokeWidth = 6f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }

            val path = Path()
            var isFirst = true

            // Calculate scale if drawn on smaller composable
            points.forEach { pt ->
                if (pt == null) {
                    isFirst = true
                } else if (isFirst) {
                    path.moveTo(pt.x * (widthPx / 400f), pt.y * (heightPx / 150f))
                    isFirst = false
                } else {
                    path.lineTo(pt.x * (widthPx / 400f), pt.y * (heightPx / 150f))
                }
            }

            canvas.drawPath(path, paint)

            val dir = getSignaturesDir(context)
            val file = File(dir, "${prefix}_${System.currentTimeMillis()}.png")
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
            bitmap.recycle()

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes old signature file if it exists in app storage.
     */
    fun deleteSignatureFile(filePath: String) {
        try {
            if (filePath.isNotBlank()) {
                val file = File(filePath)
                if (file.exists() && file.parentFile?.name == "signatures") {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
