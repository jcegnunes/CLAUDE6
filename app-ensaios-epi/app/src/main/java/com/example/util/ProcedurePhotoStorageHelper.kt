package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.Base64
import com.example.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Storage and processing helper for custom laboratory procedure photos
 * (e.g. Dielectric Glove Testing Setup, Water Bath, Transformer & Variac Console).
 * Supports saving persistent copies in internal storage, isolating/mounting on a pure white background,
 * and loading in high-resolution for PDF, Word, and Compose UI screens.
 */
object ProcedurePhotoStorageHelper {

    private const val FOLDER_NAME = "procedure_photos"

    private fun getProcedureDir(context: Context): File {
        val dir = File(context.filesDir, FOLDER_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Persistently saves a selected Image Uri into app internal storage.
     * Optionally places or mounts the photo on a clean white background canvas for pristine technical reports.
     */
    fun saveUriToProcedureFile(
        context: Context,
        sourceUri: Uri,
        applyPureWhiteCanvas: Boolean = true
    ): String? {
        return try {
            val dir = getProcedureDir(context)
            val file = File(dir, "glove_procedure_${System.currentTimeMillis()}.jpg")
            val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            if (inputStream != null) {
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (originalBitmap != null) {
                    val finalBitmap = if (applyPureWhiteCanvas) {
                        mountOnWhiteCanvas(originalBitmap)
                    } else {
                        originalBitmap
                    }

                    val fos = FileOutputStream(file)
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                    fos.flush()
                    fos.close()

                    if (finalBitmap != originalBitmap) {
                        finalBitmap.recycle()
                    }
                    originalBitmap.recycle()

                    file.absolutePath
                } else {
                    // Fallback to direct stream copy
                    val rawStream = context.contentResolver.openInputStream(sourceUri)
                    if (rawStream != null) {
                        val fos = FileOutputStream(file)
                        rawStream.copyTo(fos)
                        fos.flush()
                        fos.close()
                        rawStream.close()
                        file.absolutePath
                    } else null
                }
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Mounts an image onto a seamless pure white studio canvas,
     * ensuring that even photos taken in rooms or workshops display on a spotless white background in technical reports.
     */
    fun mountOnWhiteCanvas(src: Bitmap, targetWidth: Int = 1200, targetHeight: Int = 900): Bitmap {
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        // Draw solid pure white background
        canvas.drawColor(Color.WHITE)

        val srcW = src.width.toFloat()
        val srcH = src.height.toFloat()
        val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()
        val srcAspect = srcW / srcH

        // We fit the photo nicely with a small clean white margin (e.g. 10px)
        val pad = 8f
        val availW = targetWidth - (pad * 2f)
        val availH = targetHeight - (pad * 2f)

        val drawW: Float
        val drawH: Float
        if (srcAspect > availW / availH) {
            drawW = availW
            drawH = availW / srcAspect
        } else {
            drawH = availH
            drawW = availH * srcAspect
        }

        val left = (targetWidth - drawW) / 2f
        val top = (targetHeight - drawH) / 2f
        val dstRect = RectF(left, top, left + drawW, top + drawH)

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }

        canvas.drawBitmap(src, Rect(0, 0, src.width, src.height), dstRect, paint)
        return output
    }

    /**
     * Loads the glove procedure bitmap:
     * 1. If [customUri] is set and the file exists, decodes and scales it.
     * 2. Otherwise loads the built-in drawable [defaultResId].
     */
    fun loadGloveProcedureBitmap(
        context: Context,
        customUri: String?,
        reqWidth: Int = 1200,
        reqHeight: Int = 900,
        defaultResId: Int = 0
    ): Bitmap? {
        if (!customUri.isNullOrBlank()) {
            try {
                val file = if (customUri.startsWith("file://")) {
                    File(Uri.parse(customUri).path ?: "")
                } else if (customUri.startsWith("/")) {
                    File(customUri)
                } else {
                    null
                }

                if (file != null && file.exists()) {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(file.absolutePath, options)

                    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                    options.inJustDecodeBounds = false
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888

                    val bmp = BitmapFactory.decodeFile(file.absolutePath, options)
                    if (bmp != null) return bmp
                }

                // Try content resolver uri
                val uri = Uri.parse(customUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) return bmp
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (defaultResId == 0) return null

        // Fallback to resource
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeResource(context.resources, defaultResId, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            BitmapFactory.decodeResource(context.resources, defaultResId, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Loads the glove procedure image as Base64 JPEG string for embedding in Word (.docx) HTML reports.
     */
    fun loadGloveProcedureBase64(
        context: Context,
        customUri: String?,
        reqWidth: Int = 1200,
        reqHeight: Int = 900,
        defaultResId: Int = 0
    ): String? {
        val bitmap = loadGloveProcedureBitmap(context, customUri, reqWidth, reqHeight, defaultResId) ?: return null
        return try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
            val bytes = baos.toByteArray()
            baos.close()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes a previously saved custom procedure photo.
     */
    fun deleteProcedureFile(filePath: String) {
        try {
            if (filePath.isNotBlank()) {
                val file = File(filePath)
                if (file.exists() && (file.parentFile?.name == FOLDER_NAME || file.name.startsWith("glove_procedure_"))) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
