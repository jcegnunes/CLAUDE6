package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.InputStream

/**
 * Orientação EXIF de fotos: a câmera (principalmente em tablets na vertical) grava a imagem
 * "deitada" e só anota o giro no EXIF. Quem lê os pixels direto (PDF, Word, envio) precisa
 * aplicar esse giro, senão a foto aparece girada.
 */
object ImageOrientation {

    /** Transformação do EXIF (null = já está na posição certa). */
    fun matrixFor(orientation: Int): Matrix? = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            else -> return null
        }
    }

    fun orientationOf(input: InputStream): Int = try {
        ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    /** Orientação EXIF de um arquivo local ou de um content:// (NORMAL se não houver). */
    fun orientationOf(context: Context, pathOrUri: String): Int = try {
        val file = File(pathOrUri)
        if (file.exists()) file.inputStream().use { orientationOf(it) }
        else context.contentResolver.openInputStream(Uri.parse(pathOrUri))?.use { orientationOf(it) } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    /** Aplica a orientação EXIF da foto de origem no bitmap já decodificado. */
    fun upright(context: Context, pathOrUri: String, bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null
        val matrix = matrixFor(orientationOf(context, pathOrUri)) ?: return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
