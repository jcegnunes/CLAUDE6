package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.ui.components.TestPhotoItem
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Result of saving a photo to the OS storage folder.
 */
data class SavedPhotoResult(
    val filePath: String,
    val contentUri: Uri?,
    val fileName: String,
    val folderPath: String
)

/**
 * Manager responsible for creating and organizing dedicated folders in the device's storage
 * per Service Order (Ordem de Serviço - OS), saving photos, indexing in MediaStore/Gallery,
 * and facilitating sharing and file exploration.
 */
object PhotoStorageManager {

    private const val TAG = "PhotoStorageManager"
    private const val APP_ROOT_FOLDER = "JVM_Ensaios"

    /**
     * Cleans and sanitizes the OS number for safe folder and file naming.
     * Example: "OS-2026-001 / Cliente X" -> "OS-2026-001"
     */
    fun sanitizeOsName(osNumber: String?): String {
        if (osNumber.isNullOrBlank()) return "OS-AVULSA"
        return osNumber.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace("\\s+".toRegex(), "_")
            .take(40)
    }

    /**
     * Cleans a label or tag for filename inclusion.
     */
    private fun sanitizeTag(text: String?): String {
        if (text.isNullOrBlank()) return "foto"
        return text.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\s+]"), "_")
            .take(25)
    }

    /**
     * Returns the dedicated OS directory in the device's external public Pictures directory,
     * with fallback to app-specific external files dir.
     */
    fun getOsPhotosDirectory(context: Context, osNumber: String): File {
        val cleanOs = sanitizeOsName(osNumber)
        
        // 1. Try public Pictures/JVM_Ensaios/<OS_NUMBER>
        val publicPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val jvmDir = File(publicPictures, "$APP_ROOT_FOLDER/$cleanOs")
        if (jvmDir.exists() || jvmDir.mkdirs()) {
            return jvmDir
        }

        // 2. Fallback: context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val extFilesPictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (extFilesPictures != null) {
            val appJvmDir = File(extFilesPictures, "$APP_ROOT_FOLDER/$cleanOs")
            if (appJvmDir.exists() || appJvmDir.mkdirs()) {
                return appJvmDir
            }
        }

        // 3. Fallback: Internal app files directory
        val internalDir = File(context.filesDir, "test_photos/$cleanOs")
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        return internalDir
    }

    /**
     * Returns a human-friendly display path for the OS folder.
     */
    fun getOsFolderPathDisplay(context: Context, osNumber: String): String {
        val cleanOs = sanitizeOsName(osNumber)
        return "Pictures/$APP_ROOT_FOLDER/$cleanOs"
    }

    /**
     * Generates a structured filename for the photo.
     * Example: OS-2026-001_Equipamento_TAG-104_20260831_164012_a1b2.jpg
     */
    fun generatePhotoFileName(
        osNumber: String,
        label: String = "Equipamento",
        equipmentTag: String = ""
    ): String {
        val cleanOs = sanitizeOsName(osNumber)
        val cleanLabel = sanitizeTag(label)
        val cleanTag = if (equipmentTag.isNotBlank()) "_${sanitizeTag(equipmentTag)}" else ""
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val shortId = UUID.randomUUID().toString().take(4)
        return "${cleanOs}_${cleanLabel}${cleanTag}_${timestamp}_$shortId.jpg"
    }

    /**
     * Saves an image from a source Uri into the designated OS folder on the phone's memory
     * and indexes it into MediaStore (Pictures/JVM_Ensaios/<OS_NUMBER>) for gallery visibility.
     */
    /**
     * Grava em [dest] a foto de [sourceUri] na posição correta. Fotos de câmera (principalmente em
     * tablets na vertical) vêm "deitadas" com a orientação só na etiqueta EXIF; o PDF, o Word e o
     * envio leem os pixels direto e mostravam a foto girada. Aqui a rotação/espelhamento do EXIF é
     * aplicada nos pixels e a foto é regravada sem a etiqueta. Sem EXIF de giro: cópia direta.
     */
    private fun writeUprightCopy(context: Context, sourceUri: Uri, dest: File) {
        val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: throw java.io.IOException("Não foi possível ler a foto")
        val matrix = ImageOrientation.matrixFor(ImageOrientation.orientationOf(java.io.ByteArrayInputStream(bytes)))
        if (matrix == null) {
            FileOutputStream(dest).use { it.write(bytes) }
            return
        }
        // limita o lado maior a 2560 px para não estourar memória ao girar fotos grandes
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2560) sample *= 2
        val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        if (decoded == null) {
            FileOutputStream(dest).use { it.write(bytes) }
            return
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        FileOutputStream(dest).use { upright.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
    }

    fun savePhotoForOs(
        context: Context,
        sourceUri: Uri,
        osNumber: String,
        label: String = "Equipamento",
        equipmentTag: String = ""
    ): SavedPhotoResult? {
        val cleanOs = sanitizeOsName(osNumber)
        val fileName = generatePhotoFileName(cleanOs, label, equipmentTag)
        val targetDir = getOsPhotosDirectory(context, cleanOs)
        val destFile = File(targetDir, fileName)

        var savedPath: String? = null
        var mediaStoreUri: Uri? = null

        try {
            // 1. Copia já com a orientação aplicada nos pixels (a câmera grava "deitada" + EXIF)
            writeUprightCopy(context, sourceUri, destFile)
            savedPath = destFile.absolutePath

            // 2. Notify MediaScanner so the photo appears in Gallery / File Managers
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("image/jpeg")
            ) { _, uri ->
                if (uri != null) mediaStoreUri = uri
            }

            // 3. On Android 10+ (API 29+), also register in MediaStore relative path
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$APP_ROOT_FOLDER/$cleanOs")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val insertedUri = context.contentResolver.insert(collection, values)
                    if (insertedUri != null) {
                        context.contentResolver.openOutputStream(insertedUri)?.use { outStream ->
                            destFile.inputStream().use { inStream -> inStream.copyTo(outStream) }
                        }
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        context.contentResolver.update(insertedUri, values, null, null)
                        mediaStoreUri = insertedUri
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MediaStore relative path write skipped/fallback: ${e.message}")
                }
            }

            return SavedPhotoResult(
                filePath = savedPath ?: destFile.absolutePath,
                contentUri = mediaStoreUri ?: Uri.fromFile(destFile),
                fileName = fileName,
                folderPath = targetDir.absolutePath
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error saving photo for OS $cleanOs: ${e.message}", e)
            return null
        }
    }

    /**
     * Saves a Bitmap directly into the designated OS folder on the phone's memory.
     */
    fun saveBitmapForOs(
        context: Context,
        bitmap: Bitmap,
        osNumber: String,
        label: String = "Equipamento",
        equipmentTag: String = ""
    ): SavedPhotoResult? {
        val cleanOs = sanitizeOsName(osNumber)
        val fileName = generatePhotoFileName(cleanOs, label, equipmentTag)
        val targetDir = getOsPhotosDirectory(context, cleanOs)
        val destFile = File(targetDir, fileName)

        try {
            FileOutputStream(destFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                fos.flush()
            }

            // Notify MediaScanner
            var mediaStoreUri: Uri? = null
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("image/jpeg")
            ) { _, uri ->
                if (uri != null) mediaStoreUri = uri
            }

            // On Android 10+ (API 29+), also register in MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$APP_ROOT_FOLDER/$cleanOs")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val insertedUri = context.contentResolver.insert(collection, values)
                    if (insertedUri != null) {
                        context.contentResolver.openOutputStream(insertedUri)?.use { outStream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outStream)
                        }
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        context.contentResolver.update(insertedUri, values, null, null)
                        mediaStoreUri = insertedUri
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MediaStore write exception: ${e.message}")
                }
            }

            return SavedPhotoResult(
                filePath = destFile.absolutePath,
                contentUri = mediaStoreUri ?: Uri.fromFile(destFile),
                fileName = fileName,
                folderPath = targetDir.absolutePath
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap for OS $cleanOs: ${e.message}", e)
            return null
        }
    }

    /**
     * Generates a sample demonstration photo and saves it into the OS folder.
     */
    fun createSamplePhotoForOs(
        context: Context,
        osNumber: String,
        title: String,
        equipmentTag: String = ""
    ): SavedPhotoResult? {
        val cleanOs = sanitizeOsName(osNumber)
        val bitmap = Bitmap.createBitmap(800, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#0B192C"))

        val whitePaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val orangePaint = Paint().apply {
            color = Color.parseColor("#FF6B00")
            textSize = 38f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val cyanPaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 22f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("⚡ JVM ENGENHARIA ELÉTRICA", 400f, 110f, orangePaint)
        canvas.drawText("REGISTRO FOTOGRÁFICO DE ENSAIO NR-10", 400f, 170f, whitePaint)
        canvas.drawText("ORDEM DE SERVIÇO: $cleanOs", 400f, 230f, cyanPaint)
        if (equipmentTag.isNotBlank()) {
            canvas.drawText("TAG: $equipmentTag • $title", 400f, 290f, whitePaint)
        } else {
            canvas.drawText(title, 400f, 290f, whitePaint)
        }
        canvas.drawText("Data/Hora: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}", 400f, 380f, subPaint)
        canvas.drawText("Pasta: Pictures/$APP_ROOT_FOLDER/$cleanOs/", 400f, 430f, subPaint)

        return saveBitmapForOs(context, bitmap, cleanOs, title, equipmentTag)
    }

    /**
     * Lists all photo files saved in the OS folder on device memory.
     */
    fun listPhotosForOs(context: Context, osNumber: String): List<File> {
        val dir = getOsPhotosDirectory(context, osNumber)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles { file ->
            file.isFile && (file.extension.equals("jpg", true) || file.extension.equals("jpeg", true) || file.extension.equals("png", true))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Exports / ensures all photos in a list are copied and saved into the OS folder.
     */
    fun syncPhotosToOsFolder(
        context: Context,
        osNumber: String,
        photos: List<TestPhotoItem>,
        equipmentTag: String = ""
    ): Int {
        var count = 0
        val cleanOs = sanitizeOsName(osNumber)
        photos.forEach { photo ->
            try {
                val uri = if (photo.uriString.startsWith("/")) {
                    val file = File(photo.uriString)
                    if (file.exists() && !file.parentFile?.name.equals(cleanOs, ignoreCase = true)) {
                        Uri.fromFile(file)
                    } else null
                } else {
                    Uri.parse(photo.uriString)
                }

                if (uri != null) {
                    val res = savePhotoForOs(context, uri, cleanOs, photo.label, equipmentTag)
                    if (res != null) count++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing photo ${photo.label}: ${e.message}")
            }
        }
        return count
    }

    /**
     * Shares all photos from an OS folder via Android Share Sheet.
     */
    fun shareAllOsPhotos(context: Context, osNumber: String): Boolean {
        val photos = listPhotosForOs(context, osNumber)
        if (photos.isEmpty()) return false

        try {
            val uris = ArrayList<Uri>()
            photos.forEach { file ->
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                uris.add(uri)
            }

            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, "Fotos do Ensaio Dielétrico - OS $osNumber")
                putExtra(Intent.EXTRA_TEXT, "Registro fotográfico dos ensaios de rigidez dielétrica da Ordem de Serviço $osNumber - JVM Engenharia.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Compartilhar Fotos da OS $osNumber"))
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing photos for OS $osNumber: ${e.message}", e)
            return false
        }
    }
}
