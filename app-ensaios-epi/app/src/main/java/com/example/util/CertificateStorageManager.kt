package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Manager for laboratory standards calibration certificates (PDFs and photos/images).
 * Handles persistent file storage in app-specific storage, opening via system viewers,
 * and sharing certificates via Android Share Sheet.
 */
object CertificateStorageManager {

    private const val CERT_DIR_NAME = "calibration_certificates"

    fun getCertificatesDirectory(context: Context): File {
        val dir = File(context.filesDir, CERT_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Copies a selected document or photo from a content URI into the app's persistent storage.
     * Returns a Pair of (absoluteFilePath, originalFileName), or null on failure.
     */
    fun saveCertificateFromUri(context: Context, sourceUri: Uri, instrumentTag: String): Pair<String, String>? {
        return try {
            val contentResolver = context.contentResolver
            var fileName = ""

            // Retrieve original display name if available
            contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: ""
                }
            }

            val cleanTag = instrumentTag.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(15)
            val extension = when {
                fileName.contains(".") -> fileName.substringAfterLast(".").lowercase()
                contentResolver.getType(sourceUri)?.contains("pdf") == true -> "pdf"
                contentResolver.getType(sourceUri)?.contains("png") == true -> "png"
                else -> "jpg"
            }

            val safeDisplayName = if (fileName.isNotBlank()) fileName else "Certificado_${cleanTag}.${extension}"
            val targetFileName = "cert_${cleanTag}_${System.currentTimeMillis()}.${extension}"
            val targetFile = File(getCertificatesDirectory(context), targetFileName)

            contentResolver.openInputStream(sourceUri)?.use { input: InputStream ->
                FileOutputStream(targetFile).use { output: FileOutputStream ->
                    input.copyTo(output)
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                Pair(targetFile.absolutePath, safeDisplayName)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a captured camera photo as the calibration certificate.
     */
    fun saveCertificateFromPhoto(context: Context, tempPhotoFile: File, instrumentTag: String): Pair<String, String>? {
        return try {
            val cleanTag = instrumentTag.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(15)
            val targetFileName = "cert_foto_${cleanTag}_${System.currentTimeMillis()}.jpg"
            val targetFile = File(getCertificatesDirectory(context), targetFileName)

            tempPhotoFile.copyTo(targetFile, overwrite = true)
            val displayName = "Foto_Certificado_${cleanTag}.jpg"

            if (targetFile.exists() && targetFile.length() > 0) {
                Pair(targetFile.absolutePath, displayName)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Determines whether the file is a PDF.
     */
    fun isPdf(filePath: String): Boolean {
        return filePath.endsWith(".pdf", ignoreCase = true)
    }

    /**
     * Determines whether the file is an image.
     */
    fun isImage(filePath: String): Boolean {
        val lower = filePath.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
    }

    /**
     * Opens the certificate in an external viewer (PDF reader, Gallery/Photo viewer).
     */
    fun openCertificate(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(context, "Arquivo do certificado não encontrado no dispositivo.", Toast.LENGTH_SHORT).show()
                return
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mimeType = when {
                isPdf(filePath) -> "application/pdf"
                isImage(filePath) -> "image/*"
                else -> "*/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Visualizar Certificado de Calibração"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Não foi possível abrir o certificado: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Shares the certificate file via WhatsApp, Email, Drive, etc.
     */
    fun shareCertificate(context: Context, filePath: String, instrumentTag: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(context, "Arquivo do certificado não encontrado.", Toast.LENGTH_SHORT).show()
                return
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mimeType = when {
                isPdf(filePath) -> "application/pdf"
                isImage(filePath) -> "image/*"
                else -> "*/*"
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Certificado de Calibração RBC - $instrumentTag")
                putExtra(Intent.EXTRA_TEXT, "Segue anexo o certificado de calibração do instrumento padrão $instrumentTag.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Compartilhar Certificado de Calibração"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao compartilhar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
