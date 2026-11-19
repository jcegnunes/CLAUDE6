package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object CompanyLogoStorageHelper {

    private fun getLogoDir(context: Context): File {
        val dir = File(context.filesDir, "company_logo")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Persistently saves a selected Image Uri into the internal app storage for company logo usage.
     * Retains aspect ratio and transparency (PNG).
     */
    fun saveUriToLogoFile(context: Context, sourceUri: Uri): String? {
        return try {
            val dir = getLogoDir(context)
            val file = File(dir, "company_logo_${System.currentTimeMillis()}.png")
            val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            if (inputStream != null) {
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (bitmap != null) {
                    val fos = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.flush()
                    fos.close()
                    bitmap.recycle()
                    file.absolutePath
                } else {
                    // Fallback to raw copy if decode fails (e.g. svg or already compressed file)
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
     * Deletes the old company logo file if it exists in internal storage.
     */
    fun deleteLogoFile(filePath: String) {
        try {
            if (filePath.isNotBlank()) {
                val file = File(filePath)
                if (file.exists() && (file.parentFile?.name == "company_logo" || file.name.startsWith("company_logo_"))) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
