package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.local.AppDatabase
import com.example.data.model.AppSettingEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.ClientEntity
import com.example.data.model.EquipmentEntity
import com.example.data.model.LabInstrumentEntity
import com.example.data.model.NormCriterionEntity
import com.example.data.model.ServiceOrderEntity
import com.example.data.model.SyncStatus
import com.example.data.model.TestRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Data bundle representing all database entities for backup and restoration.
 */
data class BackupDataBundle(
    val exportedAt: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
    val clients: List<ClientEntity> = emptyList(),
    val equipment: List<EquipmentEntity> = emptyList(),
    val serviceOrders: List<ServiceOrderEntity> = emptyList(),
    val testRecords: List<TestRecordEntity> = emptyList(),
    val labInstruments: List<LabInstrumentEntity> = emptyList(),
    val norms: List<NormCriterionEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val settings: AppSettingEntity? = null,
    val photoCount: Int = 0
)

/**
 * Summary extracted from inspecting a backup file (.zip or .json) prior to restoration.
 */
data class BackupInspectionSummary(
    val fileName: String,
    val fileSizeFormatted: String,
    val isZip: Boolean,
    val exportedAt: String,
    val clientCount: Int,
    val equipmentCount: Int,
    val serviceOrderCount: Int,
    val testCount: Int,
    val instrumentCount: Int,
    val photoCount: Int,
    val companyName: String,
    val sourceUri: Uri
)

/**
 * Result of importing a backup.
 */
data class BackupImportResult(
    val success: Boolean,
    val message: String,
    val testsImported: Int = 0,
    val clientsImported: Int = 0,
    val equipmentImported: Int = 0,
    val photosImported: Int = 0,
    val instrumentsImported: Int = 0
)

/**
 * Local Backup Manager:
 * Handles exporting complete backups (.zip with full database JSON + all test photos, certificates and signatures)
 * and importing/restoring backups locally on the device with photo relinking and conflict handling.
 */
object LocalBackupManager {

    private const val TAG = "LocalBackupManager"
    private const val BACKUP_DIR_NAME = "JVM_Backups"
    private const val MANIFEST_FILE_NAME = "manifest.json"
    private const val DATABASE_FILE_NAME = "database.json"
    private const val PHOTOS_FOLDER = "photos"
    private const val CERTS_FOLDER = "certificates"
    private const val SIGS_FOLDER = "signatures"
    private const val LOGOS_FOLDER = "logos"
    private const val LETTERHEAD_FOLDER = "letterhead"

    /**
     * Returns the dedicated directory where local backup archives are stored.
     */
    fun getLocalBackupDirectory(context: Context): File {
        // Try public Documents/JVM_Backups
        val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val jvmBackupsDir = File(publicDocs, BACKUP_DIR_NAME)
        if (jvmBackupsDir.exists() || jvmBackupsDir.mkdirs()) {
            return jvmBackupsDir
        }

        // Fallback to app-specific external files dir
        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (extDir != null) {
            val appBackupsDir = File(extDir, BACKUP_DIR_NAME)
            if (appBackupsDir.exists() || appBackupsDir.mkdirs()) {
                return appBackupsDir
            }
        }

        // Fallback to internal files dir
        val internalDir = File(context.filesDir, BACKUP_DIR_NAME)
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        return internalDir
    }

    /**
     * Lists existing local backup files found on the device.
     */
    fun listLocalBackups(context: Context): List<File> {
        val dir = getLocalBackupDirectory(context)
        val internalDir = File(context.filesDir, BACKUP_DIR_NAME)
        
        val files = mutableListOf<File>()
        if (dir.exists()) {
            dir.listFiles { file -> file.isFile && (file.name.endsWith(".zip", ignoreCase = true) || file.name.endsWith(".json", ignoreCase = true)) }
                ?.let { files.addAll(it) }
        }
        if (internalDir.exists() && internalDir.absolutePath != dir.absolutePath) {
            internalDir.listFiles { file -> file.isFile && (file.name.endsWith(".zip", ignoreCase = true) || file.name.endsWith(".json", ignoreCase = true)) }
                ?.let { files.addAll(it) }
        }

        return files.distinctBy { it.name }.sortedByDescending { it.lastModified() }
    }

    /**
     * Formats bytes to human-readable size.
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    /**
     * Calculates total count and size of all test photos stored locally.
     */
    fun calculateLocalPhotosStats(context: Context): Pair<Int, Long> {
        var totalCount = 0
        var totalBytes = 0L

        fun checkDirectory(dir: File) {
            if (!dir.exists()) return
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    checkDirectory(file)
                } else if (file.isFile) {
                    val name = file.name.lowercase()
                    if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")) {
                        totalCount++
                        totalBytes += file.length()
                    }
                }
            }
        }

        // Public Pictures/JVM_Ensaios
        val publicPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        checkDirectory(File(publicPictures, "JVM_Ensaios"))

        // App-specific external Pictures
        val extPictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (extPictures != null) {
            checkDirectory(File(extPictures, "JVM_Ensaios"))
        }

        // Internal files test_photos
        checkDirectory(File(context.filesDir, "test_photos"))
        checkDirectory(File(context.cacheDir, "photos"))

        return Pair(totalCount, totalBytes)
    }

    // =========================================================================
    // EXPORT BACKUP (.ZIP WITH PHOTOS)
    // =========================================================================

    /**
     * Exports a complete ZIP backup containing the full database in JSON and all photos,
     * certificates and signatures.
     */
    suspend fun createZipBackup(
        context: Context,
        database: AppDatabase,
        onProgress: (String) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        onProgress("Coletando registros do banco de dados...")
        val dataBundle = fetchAllData(database)

        val timeStampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupDir = getLocalBackupDirectory(context)
        val zipFileName = "Backup_JVM_Dielectric_Lab_${timeStampStr}.zip"
        val zipFile = File(backupDir, zipFileName)

        onProgress("Iniciando compressão do backup...")
        val addedZipEntries = mutableSetOf<String>()

        FileOutputStream(zipFile).use { fos ->
            BufferedOutputStream(fos).use { bos ->
                ZipOutputStream(bos).use { zos ->

                    // 1. Collect and add test photos
                    onProgress("Empacotando fotos dos ensaios...")
                    var packedPhotoCount = 0

                    for (test in dataBundle.testRecords) {
                        if (test.photoUris.isNotBlank()) {
                            val rawItems = test.photoUris.split("|||").filter { it.isNotBlank() }
                            for (item in rawItems) {
                                val pathOrUri = if (item.contains("::")) item.substringAfter("::").trim() else item.trim()
                                val photoFile = resolvePhotoFile(context, pathOrUri, test.serviceOrderNumber)
                                if (photoFile != null && photoFile.exists() && photoFile.length() > 0) {
                                    val safeName = photoFile.name
                                    val entryPath = "$PHOTOS_FOLDER/${test.serviceOrderNumber.ifEmpty { "OS_GERAL" }}_$safeName"
                                    if (!addedZipEntries.contains(entryPath)) {
                                        addedZipEntries.add(entryPath)
                                        addFileToZip(zos, photoFile, entryPath)
                                        packedPhotoCount++
                                    }
                                }
                            }
                        }
                    }

                    // Also scan OS photo directories to ensure all captured test photos are included
                    val publicPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val jvmEnsaiosDir = File(publicPictures, "JVM_Ensaios")
                    if (jvmEnsaiosDir.exists()) {
                        jvmEnsaiosDir.walkTopDown().filter { it.isFile }.forEach { file ->
                            val lower = file.name.lowercase()
                            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")) {
                                val relPath = file.relativeTo(jvmEnsaiosDir).path.replace("\\", "/")
                                val entryPath = "$PHOTOS_FOLDER/$relPath"
                                if (!addedZipEntries.contains(entryPath)) {
                                    addedZipEntries.add(entryPath)
                                    addFileToZip(zos, file, entryPath)
                                    packedPhotoCount++
                                }
                            }
                        }
                    }

                    // 2. Add calibration certificates
                    onProgress("Empacotando certificados de calibração...")
                    for (inst in dataBundle.labInstruments) {
                        if (inst.calibrationCertificateUri.isNotBlank()) {
                            val certFile = File(inst.calibrationCertificateUri)
                            if (certFile.exists()) {
                                val entryPath = "$CERTS_FOLDER/${certFile.name}"
                                if (!addedZipEntries.contains(entryPath)) {
                                    addedZipEntries.add(entryPath)
                                    addFileToZip(zos, certFile, entryPath)
                                }
                            }
                        }
                    }

                    // 3. Add signatures
                    onProgress("Empacotando assinaturas digitais...")
                    dataBundle.settings?.let { s ->
                        listOf(s.technicalResponsibleSignatureImage, s.defaultTechnicianSignatureImage).forEach { sigPath ->
                            if (sigPath.isNotBlank()) {
                                val sigFile = File(sigPath)
                                if (sigFile.exists()) {
                                    val entryPath = "$SIGS_FOLDER/${sigFile.name}"
                                    if (!addedZipEntries.contains(entryPath)) {
                                        addedZipEntries.add(entryPath)
                                        addFileToZip(zos, sigFile, entryPath)
                                    }
                                }
                            }
                        }

                        // 3.1 Add company logo
                        val logoPath = s.companyLogoUri
                        if (logoPath.isNotBlank()) {
                            val logoFile = File(logoPath)
                            if (logoFile.exists()) {
                                val entryPath = "$LOGOS_FOLDER/${logoFile.name}"
                                if (!addedZipEntries.contains(entryPath)) {
                                addedZipEntries.add(entryPath)
                                addFileToZip(zos, logoFile, entryPath)
                                }
                            }
                        }

                        // 3.2 Add letterhead template file
                        val letterheadPath = s.letterheadUri
                        if (letterheadPath.isNotBlank()) {
                            val lhFile = File(letterheadPath)
                            if (lhFile.exists()) {
                                val entryPath = "$LETTERHEAD_FOLDER/${lhFile.name}"
                                if (!addedZipEntries.contains(entryPath)) {
                                    addedZipEntries.add(entryPath)
                                    addFileToZip(zos, lhFile, entryPath)
                                }
                            }
                        }
                    }

                    // 4. Create and write database.json
                    onProgress("Gerando dados estruturados do banco...")
                    val updatedBundle = dataBundle.copy(photoCount = packedPhotoCount)
                    val jsonString = serializeBundleToJson(updatedBundle)
                    val dbEntry = ZipEntry(DATABASE_FILE_NAME)
                    zos.putNextEntry(dbEntry)
                    zos.write(jsonString.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 5. Create and write manifest.json
                    val manifestJson = JSONObject().apply {
                        put("version", "1.0")
                        put("appName", "JVM Dielectric Lab")
                        put("exportedAt", SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()))
                        put("timestamp", System.currentTimeMillis())
                        put("totalClients", updatedBundle.clients.size)
                        put("totalEquipment", updatedBundle.equipment.size)
                        put("totalServiceOrders", updatedBundle.serviceOrders.size)
                        put("totalTests", updatedBundle.testRecords.size)
                        put("totalInstruments", updatedBundle.labInstruments.size)
                        put("totalPhotos", packedPhotoCount)
                        put("companyName", updatedBundle.settings?.name ?: "JVM Engenharia")
                    }
                    val manifestEntry = ZipEntry(MANIFEST_FILE_NAME)
                    zos.putNextEntry(manifestEntry)
                    zos.write(manifestJson.toString(2).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
        }

        onProgress("Backup concluído com sucesso!")
        zipFile
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryPath: String) {
        try {
            FileInputStream(file).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    val entry = ZipEntry(entryPath)
                    entry.time = file.lastModified()
                    entry.size = file.length()
                    zos.putNextEntry(entry)
                    bis.copyTo(zos)
                    zos.closeEntry()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to pack file $file into zip: ${e.message}")
        }
    }

    /**
     * Resolves a photo file given a path or content URI string and optional OS number.
     */
    private fun resolvePhotoFile(context: Context, pathOrUri: String, osNumber: String): File? {
        if (pathOrUri.isBlank()) return null

        // 1. Direct file path
        if (!pathOrUri.startsWith("content://")) {
            val f = File(pathOrUri)
            if (f.exists() && f.length() > 0) return f
        }

        // 2. Look in OS photos directory
        val fileName = pathOrUri.substringAfterLast("/")
        if (fileName.isNotBlank()) {
            val osDir = PhotoStorageManager.getOsPhotosDirectory(context, osNumber)
            val candidate = File(osDir, fileName)
            if (candidate.exists()) return candidate

            val internalCandidate = File(File(context.filesDir, "test_photos/${PhotoStorageManager.sanitizeOsName(osNumber)}"), fileName)
            if (internalCandidate.exists()) return internalCandidate

            val cacheCandidate = File(File(context.cacheDir, "photos"), fileName)
            if (cacheCandidate.exists()) return cacheCandidate
        }

        // 3. Try reading content URI and writing to temp cache file
        if (pathOrUri.startsWith("content://")) {
            try {
                val uri = Uri.parse(pathOrUri)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val temp = File(context.cacheDir, "temp_backup_${System.currentTimeMillis()}_$fileName")
                    FileOutputStream(temp).use { output ->
                        inputStream.copyTo(output)
                    }
                    if (temp.exists() && temp.length() > 0) return temp
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot read content uri $pathOrUri: ${e.message}")
            }
        }

        return null
    }

    /**
     * Copies an exported backup file directly to a user-selected destination URI (via Storage Access Framework).
     */
    suspend fun saveBackupToUri(context: Context, sourceFile: File, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Shares the backup file via Android Share Sheet.
     */
    fun shareBackupFile(context: Context, backupFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            backupFile
        )

        val isZip = backupFile.name.endsWith(".zip", ignoreCase = true)
        val mimeType = if (isZip) "application/zip" else "application/json"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, backupFile.name)
            putExtra(Intent.EXTRA_TEXT, "Backup local do laboratório de ensaios dielétricos JVM (${backupFile.name}).")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Compartilhar / Salvar Backup"))
    }

    // =========================================================================
    // INSPECT BACKUP (BEFORE IMPORT)
    // =========================================================================

    private fun openInputStreamSafe(context: Context, uri: Uri): InputStream? {
        return try {
            if (uri.scheme == "file") {
                FileInputStream(File(uri.path ?: ""))
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open stream for $uri: ${e.message}")
            null
        }
    }

    /**
     * Inspects a selected backup URI (either .zip or .json) and returns an informative summary.
     */
    suspend fun inspectBackupUri(context: Context, uri: Uri): BackupInspectionSummary? = withContext(Dispatchers.IO) {
        try {
            var fileName = ""
            var fileSize = 0L

            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                if (f.exists()) {
                    fileName = f.name
                    fileSize = f.length()
                }
            } else {
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: ""
                            if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "query failed: ${e.message}")
                }
            }

            if (fileName.isBlank()) {
                fileName = uri.lastPathSegment ?: "backup"
            }

            val isZip = fileName.endsWith(".zip", ignoreCase = true) ||
                    context.contentResolver.getType(uri)?.contains("zip") == true

            if (isZip) {
                inspectZipUri(context, uri, fileName, fileSize)
            } else {
                inspectJsonUri(context, uri, fileName, fileSize)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun inspectZipUri(context: Context, uri: Uri, fileName: String, fileSize: Long): BackupInspectionSummary? {
        var manifestObj: JSONObject? = null
        var databaseObj: JSONObject? = null
        var photoCount = 0

        openInputStreamSafe(context, uri)?.use { fis ->
            ZipInputStream(BufferedInputStream(fis)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.equals(MANIFEST_FILE_NAME, ignoreCase = true)) {
                        val text = BufferedReader(InputStreamReader(zis, Charsets.UTF_8)).readText()
                        manifestObj = JSONObject(text)
                    } else if (name.equals(DATABASE_FILE_NAME, ignoreCase = true)) {
                        val text = BufferedReader(InputStreamReader(zis, Charsets.UTF_8)).readText()
                        databaseObj = JSONObject(text)
                    } else if (name.startsWith(PHOTOS_FOLDER) && !entry.isDirectory) {
                        photoCount++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        if (manifestObj == null && databaseObj == null) {
            return null
        }

        val clientsCount = manifestObj?.optInt("totalClients")
            ?: databaseObj?.optJSONArray("clients")?.length() ?: 0
        val equipCount = manifestObj?.optInt("totalEquipment")
            ?: databaseObj?.optJSONArray("equipment")?.length() ?: 0
        val soCount = manifestObj?.optInt("totalServiceOrders")
            ?: databaseObj?.optJSONArray("serviceOrders")?.length() ?: 0
        val testCount = manifestObj?.optInt("totalTests")
            ?: databaseObj?.optJSONArray("testRecords")?.length() ?: 0
        val instCount = manifestObj?.optInt("totalInstruments")
            ?: databaseObj?.optJSONArray("labInstruments")?.length() ?: 0
        val reportedPhotos = manifestObj?.optInt("totalPhotos") ?: photoCount
        val companyName = manifestObj?.optString("companyName")
            ?: databaseObj?.optJSONObject("settings")?.optString("name") ?: "JVM Engenharia"
        val exportedAt = manifestObj?.optString("exportedAt")
            ?: databaseObj?.optString("exportedAt") ?: "Data não especificada"

        return BackupInspectionSummary(
            fileName = fileName,
            fileSizeFormatted = formatFileSize(fileSize),
            isZip = true,
            exportedAt = exportedAt,
            clientCount = clientsCount,
            equipmentCount = equipCount,
            serviceOrderCount = soCount,
            testCount = testCount,
            instrumentCount = instCount,
            photoCount = if (photoCount > 0) photoCount else reportedPhotos,
            companyName = companyName,
            sourceUri = uri
        )
    }

    private fun inspectJsonUri(context: Context, uri: Uri, fileName: String, fileSize: Long): BackupInspectionSummary? {
        val text = openInputStreamSafe(context, uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        } ?: return null

        val json = JSONObject(text)
        val clientsCount = json.optJSONArray("clients")?.length() ?: json.optInt("totalClients", 0)
        val equipCount = json.optJSONArray("equipment")?.length() ?: json.optInt("totalEquipment", 0)
        val soCount = json.optJSONArray("serviceOrders")?.length() ?: json.optInt("totalServiceOrders", 0)
        val testCount = json.optJSONArray("testRecords")?.length() ?: json.optInt("totalTests", 0)
        val instCount = json.optJSONArray("labInstruments")?.length() ?: json.optInt("totalInstruments", 0)
        val exportedAt = json.optString("exportedAt", "Arquivo JSON")
        val companyName = json.optJSONObject("settings")?.optString("name") ?: json.optString("company", "JVM Engenharia")

        return BackupInspectionSummary(
            fileName = fileName,
            fileSizeFormatted = formatFileSize(fileSize),
            isZip = false,
            exportedAt = exportedAt,
            clientCount = clientsCount,
            equipmentCount = equipCount,
            serviceOrderCount = soCount,
            testCount = testCount,
            instrumentCount = instCount,
            photoCount = 0,
            companyName = companyName,
            sourceUri = uri
        )
    }

    // =========================================================================
    // RESTORE / IMPORT BACKUP
    // =========================================================================

    /**
     * Restores backup from a user-selected URI.
     * @param replaceAll If true, clears current database before inserting. If false, merges and replaces conflicts by ID.
     */
    suspend fun restoreBackupFromUri(
        context: Context,
        database: AppDatabase,
        sourceUri: Uri,
        replaceAll: Boolean,
        onProgress: (String) -> Unit = {}
    ): BackupImportResult = withContext(Dispatchers.IO) {
        try {
            onProgress("Lendo arquivo de backup...")
            var isZip = false
            if (sourceUri.scheme == "file") {
                val name = sourceUri.lastPathSegment ?: ""
                isZip = name.endsWith(".zip", ignoreCase = true)
            } else {
                try {
                    context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIdx != -1) {
                            val name = cursor.getString(nameIdx) ?: ""
                            isZip = name.endsWith(".zip", ignoreCase = true)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Query failed: ${e.message}")
                }
            }
            if (!isZip) {
                val mime = context.contentResolver.getType(sourceUri) ?: ""
                isZip = mime.contains("zip") || (sourceUri.lastPathSegment?.endsWith(".zip", ignoreCase = true) == true)
            }

            if (isZip) {
                restoreZipBackup(context, database, sourceUri, replaceAll, onProgress)
            } else {
                restoreJsonBackup(context, database, sourceUri, replaceAll, onProgress)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            BackupImportResult(
                success = false,
                message = "Erro ao restaurar backup: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    private suspend fun restoreZipBackup(
        context: Context,
        database: AppDatabase,
        zipUri: Uri,
        replaceAll: Boolean,
        onProgress: (String) -> Unit
    ): BackupImportResult {
        onProgress("Extraindo arquivos e fotos do ZIP...")
        val restoredPhotosMap = mutableMapOf<String, String>() // entryFileName -> localAbsolutePath
        var jsonDatabaseText = ""
        var photosExtracted = 0

        val tempDir = File(context.cacheDir, "restore_temp_${System.currentTimeMillis()}")
        if (!tempDir.exists()) tempDir.mkdirs()

        try {
            openInputStreamSafe(context, zipUri)?.use { fis ->
                ZipInputStream(BufferedInputStream(fis)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (name.equals(DATABASE_FILE_NAME, ignoreCase = true)) {
                            jsonDatabaseText = BufferedReader(InputStreamReader(zis, Charsets.UTF_8)).readText()
                        } else if (name.startsWith(PHOTOS_FOLDER) && !entry.isDirectory) {
                            val fileName = name.substringAfterLast("/")
                            val osPrefix = name.substringAfter("$PHOTOS_FOLDER/").substringBefore("_", "OS_GERAL")
                            val targetDir = PhotoStorageManager.getOsPhotosDirectory(context, osPrefix)
                            val targetFile = File(targetDir, fileName)

                            FileOutputStream(targetFile).use { fos ->
                                BufferedOutputStream(fos).use { bos ->
                                    zis.copyTo(bos)
                                }
                            }
                            if (targetFile.exists() && targetFile.length() > 0) {
                                restoredPhotosMap[fileName] = targetFile.absolutePath
                                photosExtracted++
                            }
                        } else if (name.startsWith(CERTS_FOLDER) && !entry.isDirectory) {
                            val certsDir = CertificateStorageManager.getCertificatesDirectory(context)
                            val fileName = name.substringAfterLast("/")
                            val targetFile = File(certsDir, fileName)
                            FileOutputStream(targetFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        } else if (name.startsWith(SIGS_FOLDER) && !entry.isDirectory) {
                            val sigsDir = File(context.filesDir, "signatures")
                            if (!sigsDir.exists()) sigsDir.mkdirs()
                            val fileName = name.substringAfterLast("/")
                            val targetFile = File(sigsDir, fileName)
                            FileOutputStream(targetFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        } else if (name.startsWith(LOGOS_FOLDER) && !entry.isDirectory) {
                            val logosDir = File(context.filesDir, "company_logo")
                            if (!logosDir.exists()) logosDir.mkdirs()
                            val fileName = name.substringAfterLast("/")
                            val targetFile = File(logosDir, fileName)
                            FileOutputStream(targetFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        } else if (name.startsWith(LETTERHEAD_FOLDER) && !entry.isDirectory) {
                            val lhDir = File(context.filesDir, "letterhead")
                            if (!lhDir.exists()) lhDir.mkdirs()
                            val fileName = name.substringAfterLast("/")
                            val targetFile = File(lhDir, fileName)
                            FileOutputStream(targetFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }

                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            if (jsonDatabaseText.isBlank()) {
                return BackupImportResult(
                    success = false,
                    message = "Arquivo database.json não encontrado dentro do pacote ZIP."
                )
            }

            onProgress("Importando registros para o banco de dados...")
            val jsonObject = JSONObject(jsonDatabaseText)
            val bundle = deserializeJsonToBundle(jsonObject, restoredPhotosMap, context)

            return applyBundleToDatabase(database, bundle, replaceAll, photosExtracted, onProgress)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private suspend fun restoreJsonBackup(
        context: Context,
        database: AppDatabase,
        jsonUri: Uri,
        replaceAll: Boolean,
        onProgress: (String) -> Unit
    ): BackupImportResult {
        onProgress("Lendo arquivo JSON de dados...")
        val jsonText = openInputStreamSafe(context, jsonUri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        } ?: return BackupImportResult(success = false, message = "Não foi possível abrir o arquivo JSON.")

        val jsonObject = JSONObject(jsonText)
        val bundle = deserializeJsonToBundle(jsonObject, emptyMap(), context)

        return applyBundleToDatabase(database, bundle, replaceAll, 0, onProgress)
    }

    private suspend fun applyBundleToDatabase(
        database: AppDatabase,
        bundle: BackupDataBundle,
        replaceAll: Boolean,
        photosExtracted: Int,
        onProgress: (String) -> Unit
    ): BackupImportResult {
        if (replaceAll) {
            onProgress("Limpando banco de dados atual...")
            database.clearAllTables()
        }

        onProgress("Restaurando clientes (${bundle.clients.size})...")
        if (bundle.clients.isNotEmpty()) {
            // backup restaurado: enviado sem sobrescrever o que a plataforma web alterou depois
            database.clientDao().insertAll(bundle.clients.map { it.copy(syncStatus = SyncStatus.LEGACY) })
        }

        onProgress("Restaurando equipamentos (${bundle.equipment.size})...")
        if (bundle.equipment.isNotEmpty()) {
            database.equipmentDao().insertAll(bundle.equipment.map { it.copy(syncStatus = SyncStatus.LEGACY) })
        }

        onProgress("Restaurando ordens de serviço (${bundle.serviceOrders.size})...")
        if (bundle.serviceOrders.isNotEmpty()) {
            database.serviceOrderDao().insertAll(bundle.serviceOrders.map { it.copy(syncStatus = SyncStatus.LEGACY) })
        }

        onProgress("Restaurando laudos de ensaio (${bundle.testRecords.size})...")
        if (bundle.testRecords.isNotEmpty()) {
            database.testRecordDao().insertAll(bundle.testRecords.map { it.copy(syncStatus = SyncStatus.LEGACY) })
        }

        onProgress("Restaurando padrões do laboratório (${bundle.labInstruments.size})...")
        if (bundle.labInstruments.isNotEmpty()) {
            database.labInstrumentDao().insertAll(bundle.labInstruments.map { it.copy(syncStatus = SyncStatus.LEGACY) })
        }

        onProgress("Restaurando critérios e normas (${bundle.norms.size})...")
        if (bundle.norms.isNotEmpty()) {
            database.normDao().insertAll(bundle.norms)
        }

        if (bundle.settings != null) {
            onProgress("Atualizando configurações do laboratório...")
            database.settingsDao().saveSettings(bundle.settings)
        }

        onProgress("Registrando trilha de auditoria...")
        val nowStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val log = AuditLogEntity(
            action = if (replaceAll) "RESTAURACAO_TOTAL" else "RESTAURACAO_MESCLAGEM",
            userName = "Operador do Sistema",
            userRole = "responsavel_tecnico",
            timestamp = nowStr,
            dateTime = nowStr,
            entityType = "Backup",
            entityId = UUID.randomUUID().toString(),
            details = "Backup restaurado com sucesso. Ensaios: ${bundle.testRecords.size}, Fotos: $photosExtracted, Clientes: ${bundle.clients.size}",
            description = "Importação de backup local concluída."
        )
        database.auditLogDao().insertAuditLog(log)

        return BackupImportResult(
            success = true,
            message = "Backup restaurado com sucesso! ${bundle.testRecords.size} laudos e $photosExtracted fotos vinculadas.",
            testsImported = bundle.testRecords.size,
            clientsImported = bundle.clients.size,
            equipmentImported = bundle.equipment.size,
            photosImported = photosExtracted,
            instrumentsImported = bundle.labInstruments.size
        )
    }

    // =========================================================================
    // DATA BUNDLE & JSON CONVERSION
    // =========================================================================

    suspend fun fetchAllData(database: AppDatabase): BackupDataBundle = withContext(Dispatchers.IO) {
        val clients = database.clientDao().getAllClients().first()
        val equipment = database.equipmentDao().getAllEquipment().first()
        val orders = database.serviceOrderDao().getAllServiceOrders().first()
        val tests = database.testRecordDao().getAllTests().first()
        val instruments = database.labInstrumentDao().getAllInstruments().first()
        val norms = database.normDao().getAllNorms().first()
        val logs = database.auditLogDao().getAllAuditLogs().first()
        val settings = database.settingsDao().getSettingsOnce()

        BackupDataBundle(
            exportedAt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
            timestamp = System.currentTimeMillis(),
            clients = clients,
            equipment = equipment,
            serviceOrders = orders,
            testRecords = tests,
            labInstruments = instruments,
            norms = norms,
            auditLogs = logs,
            settings = settings
        )
    }

    fun serializeBundleToJson(bundle: BackupDataBundle): String {
        val root = JSONObject()
        root.put("version", "1.0")
        root.put("exportedAt", bundle.exportedAt)
        root.put("timestamp", bundle.timestamp)
        root.put("photoCount", bundle.photoCount)

        // Clients
        val clientsArray = JSONArray()
        bundle.clients.forEach { c ->
            clientsArray.put(JSONObject().apply {
                put("id", c.id)
                put("razaoSocial", c.razaoSocial)
                put("nomeFantasia", c.nomeFantasia)
                put("cnpj", c.cnpj)
                put("inscricaoEstadual", c.inscricaoEstadual)
                put("address", c.address)
                put("city", c.city)
                put("state", c.state)
                put("cep", c.cep)
                put("contactPhone", c.contactPhone)
                put("contactEmail", c.contactEmail)
                put("contactName", c.contactName)
                put("observacoes", c.observacoes)
                put("createdAt", c.createdAt)
                put("updatedAt", c.updatedAt)
            })
        }
        root.put("clients", clientsArray)

        // Equipment
        val equipArray = JSONArray()
        bundle.equipment.forEach { eq ->
            equipArray.put(JSONObject().apply {
                put("id", eq.id)
                put("uuid", eq.uuid)
                put("clientId", eq.clientId)
                put("clientName", eq.clientName)
                put("type", eq.type)
                put("tag", eq.tag)
                put("qrCode", eq.qrCode)
                put("manufacturer", eq.manufacturer)
                put("model", eq.model)
                put("serialNumber", eq.serialNumber)
                put("assetNumber", eq.assetNumber)
                put("caNumber", eq.caNumber)
                put("dielectricClass", eq.dielectricClass)
                put("sizeOrLength", eq.sizeOrLength)
                put("gloveLength_mm", eq.gloveLength_mm)
                put("collaboratorName", eq.collaboratorName)
                put("collaboratorRegistration", eq.collaboratorRegistration)
                put("collaboratorSector", eq.collaboratorSector)
                put("serviceOrderId", eq.serviceOrderId)
                put("serviceOrderNumber", eq.serviceOrderNumber)
                put("acquisitionDate", eq.acquisitionDate)
                put("lastTestDate", eq.lastTestDate)
                put("nextTestDueDate", eq.nextTestDueDate)
                put("retestIntervalMonths", eq.retestIntervalMonths)
                put("status", eq.status)
                put("notes", eq.notes)
                put("createdAt", eq.createdAt)
                put("updatedAt", eq.updatedAt)
            })
        }
        root.put("equipment", equipArray)

        // Service Orders
        val ordersArray = JSONArray()
        bundle.serviceOrders.forEach { o ->
            ordersArray.put(JSONObject().apply {
                put("id", o.id)
                put("osNumber", o.osNumber)
                put("clientId", o.clientId)
                put("clientName", o.clientName)
                put("openDate", o.openDate)
                put("scheduledDate", o.scheduledDate)
                put("technicianId", o.technicianId)
                put("technicianName", o.technicianName)
                put("description", o.description)
                put("artNumber", o.artNumber)
                put("equipmentIds", o.equipmentIds)
                put("status", o.status)
                put("createdAt", o.createdAt)
                put("updatedAt", o.updatedAt)
            })
        }
        root.put("serviceOrders", ordersArray)

        // Test Records
        val testsArray = JSONArray()
        bundle.testRecords.forEach { t ->
            testsArray.put(JSONObject().apply {
                put("id", t.id)
                put("uuid", t.uuid)
                put("testNumber", t.testNumber)
                put("reportNumber", t.reportNumber)
                put("certificateNumber", t.certificateNumber)
                put("clientId", t.clientId)
                put("clientName", t.clientName)
                put("collaboratorName", t.collaboratorName)
                put("collaboratorRegistration", t.collaboratorRegistration)
                put("collaboratorSector", t.collaboratorSector)
                put("equipmentId", t.equipmentId)
                put("equipmentTag", t.equipmentTag)
                put("equipmentType", t.equipmentType)
                put("equipmentClass", t.equipmentClass)
                put("equipmentSerial", t.equipmentSerial)
                put("equipmentCa", t.equipmentCa)
                put("serviceOrderId", t.serviceOrderId)
                put("serviceOrderNumber", t.serviceOrderNumber)
                put("artNumber", t.artNumber)
                put("technicianId", t.technicianId)
                put("technicianName", t.technicianName)
                put("technicianCftOrCrea", t.technicianCftOrCrea)
                put("techResponsibleId", t.techResponsibleId)
                put("techResponsibleName", t.techResponsibleName)
                put("techResponsibleCrea", t.techResponsibleCrea)
                put("testDate", t.testDate)
                put("testTime", t.testTime)
                put("location", t.location)
                put("normCode", t.normCode)
                put("procedureCode", t.procedureCode)
                put("appliedVoltage_kV", t.appliedVoltage_kV)
                put("voltageType", t.voltageType)
                put("applicationDurationSeconds", t.applicationDurationSeconds)
                put("measuredLeakageCurrent_mA", t.measuredLeakageCurrent_mA)
                put("leakageCurrentLimit_mA", t.leakageCurrentLimit_mA)
                put("currentUnit", t.currentUnit)
                put("withstandWithoutPuncture", t.withstandWithoutPuncture)
                put("gloveLength_mm", t.gloveLength_mm)
                put("temperatureC", t.temperatureC)
                put("relativeHumidityPercent", t.relativeHumidityPercent)
                put("stickTestPoints", t.stickTestPoints)
                put("visualInspectionSummary", t.visualInspectionSummary)
                put("result", t.result)
                put("resultRationale", t.resultRationale)
                put("technicalNotes", t.technicalNotes)
                put("retestDueDate", t.retestDueDate)
                put("technicianSignature", t.technicianSignature)
                put("techResponsibleSignature", t.techResponsibleSignature)
                put("validationCode", t.validationCode)
                put("documentHash", t.documentHash)
                put("photoUris", t.photoUris)
                put("createdAt", t.createdAt)
                put("updatedAt", t.updatedAt)
            })
        }
        root.put("testRecords", testsArray)

        // Lab Instruments
        val instArray = JSONArray()
        bundle.labInstruments.forEach { inst ->
            instArray.put(JSONObject().apply {
                put("id", inst.id)
                put("tag", inst.tag)
                put("type", inst.type)
                put("manufacturer", inst.manufacturer)
                put("model", inst.model)
                put("serialNumber", inst.serialNumber)
                put("assetNumber", inst.assetNumber)
                put("measurementRange", inst.measurementRange)
                put("calibrationCertNumber", inst.calibrationCertNumber)
                put("calibrationDate", inst.calibrationDate)
                put("calibrationDueDate", inst.calibrationDueDate)
                put("calibrationExpiryDate", inst.calibrationExpiryDate)
                put("calibrationLab", inst.calibrationLab)
                put("maxVoltageCapacity_kV", inst.maxVoltageCapacity_kV)
                put("active", inst.active)
                put("notes", inst.notes)
                put("calibrationCertificateUri", inst.calibrationCertificateUri)
                put("calibrationCertificateFileName", inst.calibrationCertificateFileName)
            })
        }
        root.put("labInstruments", instArray)

        // Norms
        val normsArray = JSONArray()
        bundle.norms.forEach { n ->
            normsArray.put(JSONObject().apply {
                put("id", n.id)
                put("code", n.code)
                put("title", n.title)
                put("description", n.description)
                put("applicableEquipmentType", n.applicableEquipmentType)
                put("dielectricClass", n.dielectricClass)
                put("testVoltage_kV", n.testVoltage_kV)
                put("voltageType", n.voltageType)
                put("durationSeconds", n.durationSeconds)
                put("maxLeakageCurrent_mA", n.maxLeakageCurrent_mA)
                put("currentUnit", n.currentUnit)
                put("retestIntervalMonths", n.retestIntervalMonths)
                put("notes", n.notes)
            })
        }
        root.put("norms", normsArray)

        // Settings
        bundle.settings?.let { s ->
            root.put("settings", JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("legalName", s.legalName)
                put("cnpj", s.cnpj)
                put("creaCompanyRegister", s.creaCompanyRegister)
                put("address", s.address)
                put("phone", s.phone)
                put("email", s.email)
                put("website", s.website)
                put("hostingerPlatformUrl", s.hostingerPlatformUrl)
                put("technicalResponsibleName", s.technicalResponsibleName)
                put("technicalResponsibleCrea", s.technicalResponsibleCrea)
                put("technicalResponsibleSignatureImage", s.technicalResponsibleSignatureImage)
                put("defaultTechnicianName", s.defaultTechnicianName)
                put("defaultTechnicianCftOrCrea", s.defaultTechnicianCftOrCrea)
                put("defaultTechnicianSignatureImage", s.defaultTechnicianSignatureImage)
                put("defaultRetestMonths", s.defaultRetestMonths)
                put("reportIntroText", s.reportIntroText)
                put("reportObjectiveText", s.reportObjectiveText)
                put("reportMethodologyText", s.reportMethodologyText)
                put("reportDevelopmentText", s.reportDevelopmentText)
                put("reportConclusionText", s.reportConclusionText)
                put("companyLogoUri", s.companyLogoUri)
                put("letterheadUri", s.letterheadUri)
                put("useLetterhead", s.useLetterhead)
                put("letterheadOpacity", s.letterheadOpacity.toDouble())
                put("letterheadApplyMode", s.letterheadApplyMode)
                put("letterheadHeaderMode", s.letterheadHeaderMode)
            })
        }

        return root.toString(2)
    }

    private fun deserializeJsonToBundle(
        json: JSONObject,
        restoredPhotosMap: Map<String, String>,
        context: Context
    ): BackupDataBundle {
        val clients = mutableListOf<ClientEntity>()
        json.optJSONArray("clients")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                clients.add(
                    ClientEntity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        razaoSocial = o.optString("razaoSocial", ""),
                        nomeFantasia = o.optString("nomeFantasia", ""),
                        cnpj = o.optString("cnpj", ""),
                        inscricaoEstadual = o.optString("inscricaoEstadual", ""),
                        address = o.optString("address", ""),
                        city = o.optString("city", "São Paulo"),
                        state = o.optString("state", "SP"),
                        cep = o.optString("cep", "01000-000"),
                        contactPhone = o.optString("contactPhone", ""),
                        contactEmail = o.optString("contactEmail", ""),
                        contactName = o.optString("contactName", ""),
                        observacoes = o.optString("observacoes", ""),
                        createdAt = o.optString("createdAt", ""),
                        updatedAt = o.optString("updatedAt", "")
                    )
                )
            }
        }

        val equipment = mutableListOf<EquipmentEntity>()
        json.optJSONArray("equipment")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                equipment.add(
                    EquipmentEntity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        uuid = o.optString("uuid", UUID.randomUUID().toString()),
                        clientId = o.optString("clientId", ""),
                        clientName = o.optString("clientName", ""),
                        type = o.optString("type", "LUVA_ISOLANTE"),
                        tag = o.optString("tag", ""),
                        qrCode = o.optString("qrCode", o.optString("tag", "")),
                        manufacturer = o.optString("manufacturer", "Homologado"),
                        model = o.optString("model", "Padrão"),
                        serialNumber = o.optString("serialNumber", "S/N"),
                        assetNumber = o.optString("assetNumber", ""),
                        caNumber = o.optString("caNumber", ""),
                        dielectricClass = o.optString("dielectricClass", "2"),
                        sizeOrLength = o.optString("sizeOrLength", ""),
                        gloveLength_mm = o.optInt("gloveLength_mm", 360),
                        collaboratorName = o.optString("collaboratorName", ""),
                        collaboratorRegistration = o.optString("collaboratorRegistration", ""),
                        collaboratorSector = o.optString("collaboratorSector", ""),
                        serviceOrderId = o.optString("serviceOrderId", ""),
                        serviceOrderNumber = o.optString("serviceOrderNumber", ""),
                        acquisitionDate = o.optString("acquisitionDate", ""),
                        lastTestDate = o.optString("lastTestDate", ""),
                        nextTestDueDate = o.optString("nextTestDueDate", ""),
                        retestIntervalMonths = o.optInt("retestIntervalMonths", 6),
                        status = o.optString("status", "DISPONIVEL"),
                        notes = o.optString("notes", ""),
                        createdAt = o.optString("createdAt", ""),
                        updatedAt = o.optString("updatedAt", "")
                    )
                )
            }
        }

        val serviceOrders = mutableListOf<ServiceOrderEntity>()
        json.optJSONArray("serviceOrders")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                serviceOrders.add(
                    ServiceOrderEntity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        osNumber = o.optString("osNumber", ""),
                        clientId = o.optString("clientId", ""),
                        clientName = o.optString("clientName", ""),
                        openDate = o.optString("openDate", ""),
                        scheduledDate = o.optString("scheduledDate", ""),
                        technicianId = o.optString("technicianId", ""),
                        technicianName = o.optString("technicianName", ""),
                        description = o.optString("description", ""),
                        artNumber = o.optString("artNumber", ""),
                        equipmentIds = o.optString("equipmentIds", ""),
                        status = o.optString("status", "ABERTA"),
                        createdAt = o.optString("createdAt", ""),
                        updatedAt = o.optString("updatedAt", "")
                    )
                )
            }
        }

        val testRecords = mutableListOf<TestRecordEntity>()
        json.optJSONArray("testRecords")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val originalPhotoUris = o.optString("photoUris", "")

                // Remap photo paths if restored files were mapped
                val updatedPhotoUris = if (originalPhotoUris.isNotBlank() && restoredPhotosMap.isNotEmpty()) {
                    originalPhotoUris.split("|||").filter { it.isNotBlank() }.joinToString("|||") { item ->
                        val label = if (item.contains("::")) item.substringBefore("::") else "Foto"
                        val oldPath = if (item.contains("::")) item.substringAfter("::") else item
                        val oldFileName = oldPath.substringAfterLast("/")

                        // Check if file was extracted
                        val newPath = restoredPhotosMap[oldFileName]
                            ?: restoredPhotosMap.entries.firstOrNull { it.key.contains(oldFileName) }?.value
                            ?: oldPath
                        "$label::$newPath"
                    }
                } else {
                    originalPhotoUris
                }

                testRecords.add(
                    TestRecordEntity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        uuid = o.optString("uuid", UUID.randomUUID().toString()),
                        testNumber = o.optString("testNumber", ""),
                        reportNumber = o.optString("reportNumber", ""),
                        certificateNumber = o.optString("certificateNumber", ""),
                        clientId = o.optString("clientId", ""),
                        clientName = o.optString("clientName", ""),
                        collaboratorName = o.optString("collaboratorName", ""),
                        collaboratorRegistration = o.optString("collaboratorRegistration", ""),
                        collaboratorSector = o.optString("collaboratorSector", ""),
                        equipmentId = o.optString("equipmentId", ""),
                        equipmentTag = o.optString("equipmentTag", ""),
                        equipmentType = o.optString("equipmentType", ""),
                        equipmentClass = o.optString("equipmentClass", ""),
                        equipmentSerial = o.optString("equipmentSerial", ""),
                        equipmentCa = o.optString("equipmentCa", ""),
                        serviceOrderId = o.optString("serviceOrderId", ""),
                        serviceOrderNumber = o.optString("serviceOrderNumber", ""),
                        artNumber = o.optString("artNumber", ""),
                        technicianId = o.optString("technicianId", ""),
                        technicianName = o.optString("technicianName", "Eng. João Carlos Evaristo Guedes Nunes"),
                        technicianCftOrCrea = o.optString("technicianCftOrCrea", "CREA 24481/D-DF"),
                        techResponsibleId = o.optString("techResponsibleId", ""),
                        techResponsibleName = o.optString("techResponsibleName", "Eng. João Carlos Evaristo Guedes Nunes"),
                        techResponsibleCrea = o.optString("techResponsibleCrea", "CREA 24481/D-DF"),
                        testDate = o.optString("testDate", ""),
                        testTime = o.optString("testTime", "10:00"),
                        location = o.optString("location", "Laboratório Móvel JVM"),
                        normCode = o.optString("normCode", ""),
                        procedureCode = o.optString("procedureCode", ""),
                        appliedVoltage_kV = o.optDouble("appliedVoltage_kV", 20.0),
                        voltageType = o.optString("voltageType", "AC"),
                        applicationDurationSeconds = o.optInt("applicationDurationSeconds", 60),
                        measuredLeakageCurrent_mA = o.optDouble("measuredLeakageCurrent_mA", 0.0),
                        leakageCurrentLimit_mA = o.optDouble("leakageCurrentLimit_mA", 16.0),
                        currentUnit = o.optString("currentUnit", "mA"),
                        withstandWithoutPuncture = o.optBoolean("withstandWithoutPuncture", true),
                        gloveLength_mm = o.optInt("gloveLength_mm", 360),
                        temperatureC = o.optDouble("temperatureC", 22.0),
                        relativeHumidityPercent = o.optDouble("relativeHumidityPercent", 50.0),
                        stickTestPoints = o.optInt("stickTestPoints", 1).coerceAtLeast(1),
                        visualInspectionSummary = o.optString("visualInspectionSummary", "Conforme"),
                        result = o.optString("result", "APROVADO"),
                        resultRationale = o.optString("resultRationale", ""),
                        technicalNotes = o.optString("technicalNotes", ""),
                        retestDueDate = o.optString("retestDueDate", ""),
                        technicianSignature = o.optString("technicianSignature", ""),
                        techResponsibleSignature = o.optString("techResponsibleSignature", ""),
                        validationCode = o.optString("validationCode", ""),
                        documentHash = o.optString("documentHash", ""),
                        photoUris = updatedPhotoUris,
                        createdAt = o.optString("createdAt", ""),
                        updatedAt = o.optString("updatedAt", "")
                    )
                )
            }
        }

        val labInstruments = mutableListOf<LabInstrumentEntity>()
        json.optJSONArray("labInstruments")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val oldCertUri = o.optString("calibrationCertificateUri", "")
                val certFileName = o.optString("calibrationCertificateFileName", "")
                val certsDir = CertificateStorageManager.getCertificatesDirectory(context)
                val newCertFile = if (certFileName.isNotBlank()) File(certsDir, certFileName) else null
                val updatedCertUri = if (newCertFile != null && newCertFile.exists()) newCertFile.absolutePath else oldCertUri

                labInstruments.add(
                    LabInstrumentEntity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        tag = o.optString("tag", ""),
                        type = o.optString("type", ""),
                        manufacturer = o.optString("manufacturer", ""),
                        model = o.optString("model", ""),
                        serialNumber = o.optString("serialNumber", ""),
                        assetNumber = o.optString("assetNumber", ""),
                        measurementRange = o.optString("measurementRange", ""),
                        calibrationCertNumber = o.optString("calibrationCertNumber", ""),
                        calibrationDate = o.optString("calibrationDate", ""),
                        calibrationDueDate = o.optString("calibrationDueDate", ""),
                        calibrationExpiryDate = o.optString("calibrationExpiryDate", ""),
                        calibrationLab = o.optString("calibrationLab", ""),
                        maxVoltageCapacity_kV = o.optDouble("maxVoltageCapacity_kV", 100.0),
                        active = o.optBoolean("active", true),
                        notes = o.optString("notes", ""),
                        calibrationCertificateUri = updatedCertUri,
                        calibrationCertificateFileName = certFileName
                    )
                )
            }
        }

        val norms = mutableListOf<NormCriterionEntity>()
        json.optJSONArray("norms")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                norms.add(
                    NormCriterionEntity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        code = o.optString("code", ""),
                        title = o.optString("title", ""),
                        description = o.optString("description", ""),
                        applicableEquipmentType = o.optString("applicableEquipmentType", ""),
                        dielectricClass = o.optString("dielectricClass", ""),
                        testVoltage_kV = o.optDouble("testVoltage_kV", 20.0),
                        voltageType = o.optString("voltageType", "AC"),
                        durationSeconds = o.optInt("durationSeconds", 60),
                        maxLeakageCurrent_mA = o.optDouble("maxLeakageCurrent_mA", 16.0),
                        currentUnit = o.optString("currentUnit", "mA"),
                        retestIntervalMonths = o.optInt("retestIntervalMonths", 6),
                        notes = o.optString("notes", "")
                    )
                )
            }
        }

        var settings: AppSettingEntity? = null
        json.optJSONObject("settings")?.let { o ->
            settings = AppSettingEntity(
                id = o.optString("id", "company_singleton"),
                name = o.optString("name", "JVM Engenharia"),
                legalName = o.optString("legalName", "JVM Engenharia Ltda."),
                cnpj = o.optString("cnpj", "29.894.500/0001-04"),
                creaCompanyRegister = o.optString("creaCompanyRegister", "CREA-DF 1620-PJ"),
                address = o.optString("address", "Brasília - DF"),
                phone = o.optString("phone", "(61) 98115-0000"),
                email = o.optString("email", "joao.nunes@jvmengenharia.com.br"),
                website = o.optString("website", "www.jvmengenharia.com.br"),
                hostingerPlatformUrl = o.optString("hostingerPlatformUrl", "https://mediumvioletred-bison-595566.hostingersite.com"),
                technicalResponsibleName = o.optString("technicalResponsibleName", "Eng. João Carlos Evaristo Guedes Nunes"),
                technicalResponsibleCrea = o.optString("technicalResponsibleCrea", "CREA 24481/D-DF"),
                technicalResponsibleSignatureImage = o.optString("technicalResponsibleSignatureImage", ""),
                defaultTechnicianName = o.optString("defaultTechnicianName", "Eng. João Carlos Evaristo Guedes Nunes"),
                defaultTechnicianCftOrCrea = o.optString("defaultTechnicianCftOrCrea", "CREA 24481/D-DF"),
                defaultTechnicianSignatureImage = o.optString("defaultTechnicianSignatureImage", ""),
                defaultRetestMonths = o.optInt("defaultRetestMonths", 6),
                reportIntroText = o.optString("reportIntroText", ""),
                reportObjectiveText = o.optString("reportObjectiveText", ""),
                reportMethodologyText = o.optString("reportMethodologyText", ""),
                reportDevelopmentText = o.optString("reportDevelopmentText", ""),
                reportConclusionText = o.optString("reportConclusionText", ""),
                companyLogoUri = run {
                    val rawLogo = o.optString("companyLogoUri", "")
                    if (rawLogo.isNotBlank()) {
                        val logoFileName = rawLogo.substringAfterLast("/")
                        val localLogoFile = File(File(context.filesDir, "company_logo"), logoFileName)
                        if (localLogoFile.exists()) localLogoFile.absolutePath else rawLogo
                    } else ""
                },
                letterheadUri = run {
                    val rawLh = o.optString("letterheadUri", "")
                    if (rawLh.isNotBlank()) {
                        val lhFileName = rawLh.substringAfterLast("/")
                        val localLhFile = File(File(context.filesDir, "letterhead"), lhFileName)
                        if (localLhFile.exists()) localLhFile.absolutePath else rawLh
                    } else ""
                },
                useLetterhead = o.optBoolean("useLetterhead", false),
                letterheadOpacity = o.optDouble("letterheadOpacity", 1.0).toFloat(),
                letterheadApplyMode = o.optString("letterheadApplyMode", "ALL_PAGES"),
                letterheadHeaderMode = o.optString("letterheadHeaderMode", "INTEGRATED")
            )
        }

        return BackupDataBundle(
            exportedAt = json.optString("exportedAt", ""),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            clients = clients,
            equipment = equipment,
            serviceOrders = serviceOrders,
            testRecords = testRecords,
            labInstruments = labInstruments,
            norms = norms,
            settings = settings
        )
    }
}
