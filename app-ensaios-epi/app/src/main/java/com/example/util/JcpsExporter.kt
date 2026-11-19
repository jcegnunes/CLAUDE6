package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.AppSettingEntity
import com.example.data.model.TestRecordEntity
import com.example.data.service.NiimbotPrinterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result of saving a JCPS label template file.
 */
data class SavedJcpsResult(
    val file: File,
    val uri: Uri?,
    val fileName: String,
    val folderPath: String,
    val fullPath: String,
    val success: Boolean = true,
    val message: String = ""
)

/**
 * Exporter responsible for generating official JCPS (JingChen Print Schema / Niimbot Template)
 * files compatible with Niimbot B1 thermal label printers (standard 50x30 mm dimensions at 203 DPI).
 * Saves files into the dedicated local folder "etiqueta" with the report number as the filename.
 */
object JcpsExporter {

    private const val TAG = "JcpsExporter"
    const val FOLDER_NAME = "etiqueta"
    const val DEFAULT_WIDTH_MM = 50.0
    const val DEFAULT_HEIGHT_MM = 30.0
    const val DEFAULT_DPI = 203
    const val DOTS_WIDTH = 384
    const val DOTS_HEIGHT = 240

    /**
     * Sanitizes the technical report number for use as a valid filename.
     * Example: "LDO-2026-001/01" -> "LDO-2026-001_01"
     */
    fun sanitizeReportNumber(reportNumber: String?, fallbackTag: String? = null): String {
        val base = when {
            !reportNumber.isNullOrBlank() -> reportNumber.trim()
            !fallbackTag.isNullOrBlank() -> "Laudo_${fallbackTag.trim()}"
            else -> "Laudo_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
        }
        return base
            .replace(Regex("[\\\\/:*?\"<>|\\s+]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(60)
    }

    /**
     * Returns the dedicated local "etiqueta" folder on the device.
     * Tries public Documents/etiqueta first, then external app storage, then internal storage.
     */
    fun getEtiquetaDirectory(context: Context): File {
        // 1. Try public Documents/etiqueta
        try {
            val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val etiquetaDir = File(publicDocs, FOLDER_NAME)
            if (etiquetaDir.exists() || etiquetaDir.mkdirs()) {
                return etiquetaDir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access public Documents/etiqueta: ${e.message}")
        }

        // 2. Try public Downloads/etiqueta
        try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val etiquetaDir = File(publicDownloads, FOLDER_NAME)
            if (etiquetaDir.exists() || etiquetaDir.mkdirs()) {
                return etiquetaDir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access public Downloads/etiqueta: ${e.message}")
        }

        // 3. Try context.getExternalFilesDir("etiqueta")
        try {
            val extFilesDir = context.getExternalFilesDir(FOLDER_NAME)
            if (extFilesDir != null && (extFilesDir.exists() || extFilesDir.mkdirs())) {
                return extFilesDir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access getExternalFilesDir(etiqueta): ${e.message}")
        }

        // 4. Fallback: Internal storage filesDir/etiqueta
        val internalDir = File(context.filesDir, FOLDER_NAME)
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        return internalDir
    }

    /**
     * Returns a human-friendly display path for the etiqueta folder.
     */
    fun getEtiquetaDisplayPath(context: Context): String {
        val dir = getEtiquetaDirectory(context)
        return try {
            if (dir.absolutePath.contains("Documents")) {
                "Documentos/$FOLDER_NAME"
            } else if (dir.absolutePath.contains("Download")) {
                "Downloads/$FOLDER_NAME"
            } else {
                dir.absolutePath
            }
        } catch (e: Exception) {
            "$FOLDER_NAME/"
        }
    }

    /**
     * Generates a complete, structured JCPS (JingChen / Niimbot B1 50x30mm) JSON representation.
     */
    fun generateJcpsJson(
        test: TestRecordEntity,
        company: AppSettingEntity,
        previewBitmap: Bitmap? = null,
        size: com.example.data.service.LabelPaperSize = com.example.data.service.LabelPaperSize.SIZE_50X30
    ): String {
        val root = JSONObject()

        // Niimbot B1 Schema Metadata
        root.put("fileFormat", "JCPS")
        root.put("schemaVersion", "2.1.0")
        root.put("generator", "JVM Laudos & Ensaios Elétricos")
        root.put("generatorVersion", "2.5.0")
        root.put("createTime", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        root.put("timestamp", System.currentTimeMillis())

        // Printer & Paper Specification (Niimbot B1 - 50x30mm)
        val printerObj = JSONObject().apply {
            put("brand", "NIIMBOT")
            put("model", "B1")
            put("aliases", JSONArray(listOf("B1", "B21", "D110", "B203", "B3S")))
            put("dpi", DEFAULT_DPI)
            put("dotsPerMm", 8.0)
            put("printableWidthDots", size.widthDots)
            put("printableHeightDots", size.heightDots)
        }
        root.put("printer", printerObj)

        val paperObj = JSONObject().apply {
            put("type", "GAP") // Gap label paper (50x30mm)
            put("widthMm", size.widthMm.toDouble())
            put("heightMm", size.heightMm.toDouble())
            put("gapMm", 2.0)
            put("orientation", "HORIZONTAL") // 0 deg
            put("rotation", 0)
        }
        root.put("paper", paperObj)

        // Technical Report & Equipment Data
        val metaObj = JSONObject().apply {
            put("reportNumber", test.reportNumber)
            put("equipmentTag", test.equipmentTag)
            put("equipmentType", test.equipmentType)
            put("equipmentClass", test.equipmentClass)
            put("normCode", test.normCode)
            put("appliedVoltage_kV", test.appliedVoltage_kV)
            put("measuredLeakageCurrent_mA", test.measuredLeakageCurrent_mA)
            put("leakageCurrentLimit_mA", test.leakageCurrentLimit_mA)
            put("testDate", test.testDate)
            put("retestDueDate", test.retestDueDate)
            put("result", test.result)
            put("companyName", company.name)
            put("companyCnpj", company.cnpj)
            put("technicianName", test.techResponsibleName)
            put("technicianCrea", test.techResponsibleCrea)
            put("validationCode", test.validationCode)
            put("validationUrl", com.example.util.ValidationPortal.url(test.validationCode))
        }
        root.put("metadata", metaObj)

        // Elementos editáveis: o mesmo modelo da etiqueta impressa e da plataforma web (pontos, 203 DPI)
        val model = LabelModels.build(test, size)
        val elementsArray = JSONArray()
        for (p in model.primitives) {
            when (p) {
                is LabelPrimitive.Rect -> elementsArray.put(JSONObject().apply {
                    put("type", "RECTANGLE")
                    put("name", p.id)
                    put("x", p.x.toDouble()); put("y", p.y.toDouble())
                    put("width", p.w.toDouble()); put("height", p.h.toDouble())
                    put("filled", p.fill)
                    if (!p.fill) put("strokeWidth", p.stroke.toDouble())
                    put("color", "#000000")
                })
                is LabelPrimitive.Qr -> elementsArray.put(JSONObject().apply {
                    put("type", "QRCODE")
                    put("name", "validation_qrcode")
                    put("data", p.content)
                    put("x", p.x.toDouble()); put("y", p.y.toDouble())
                    put("width", p.size.toDouble()); put("height", p.size.toDouble())
                    put("errorCorrection", "M")
                })
                is LabelPrimitive.Text -> elementsArray.put(JSONObject().apply {
                    put("type", "TEXT")
                    put("name", p.id)
                    put("text", p.content)
                    put("x", p.x.toDouble()); put("y", p.y.toDouble())
                    put("fontSize", p.size.toDouble())
                    put("fontFamily", if (p.mono) "Monospace" else "Roboto-Bold")
                    put("bold", p.bold)
                    put("textAlign", p.align.name)
                    if (p.rotate != 0f) put("rotation", (360 + p.rotate.toInt()) % 360) // 270 = vertical
                    if (p.middle) put("verticalAlign", "MIDDLE")
                    p.maxWidth?.let { put("maxWidth", it.toDouble()) }
                    put("color", if (p.white) "#FFFFFF" else "#000000")
                })
            }
        }
        root.put("elements", elementsArray)

        // Comandos TSPL equivalentes (impressoras térmicas TSC/TSPL)
        val tsplBuilder = StringBuilder().apply {
            appendLine("SIZE ${size.widthMm} mm, ${size.heightMm} mm")
            appendLine("GAP 2 mm, 0 mm")
            appendLine("DIRECTION 1")
            appendLine("CLS")
            for (p in model.primitives) {
                when (p) {
                    is LabelPrimitive.Rect ->
                        if (p.fill) appendLine("BAR ${p.x.toInt()},${p.y.toInt()},${p.w.toInt()},${p.h.toInt()}")
                        else appendLine("BOX ${p.x.toInt()},${p.y.toInt()},${(p.x + p.w).toInt()},${(p.y + p.h).toInt()},${p.stroke.toInt()}")
                    is LabelPrimitive.Qr -> {
                        val cell = maxOf(1, (p.size / 37).toInt())
                        appendLine("QRCODE ${p.x.toInt()},${p.y.toInt()},M,$cell,A,0,\"${p.content}\"")
                    }
                    is LabelPrimitive.Text -> {
                        // fontes TSPL internas: 1 (8x12), 2 (12x20), 3 (16x24), 4 (24x32), 5 (32x48)
                        val font = when {
                            p.size <= 9 -> "1"; p.size <= 13 -> "2"; p.size <= 19 -> "3"; p.size <= 26 -> "4"; else -> "5"
                        }
                        val rotation = if (p.rotate != 0f) 270 else 0
                        val top = if (p.middle) p.y - p.size / 2 else p.y - p.size
                        if (p.white) appendLine("REVERSE ${p.x.toInt()},${top.toInt()},1,1") // texto branco sobre faixa preta
                        appendLine("TEXT ${p.x.toInt()},${top.toInt()},\"$font\",$rotation,1,1,\"${p.content.replace("\"", "'")}\"")
                    }
                }
            }
            appendLine("PRINT 1")
        }
        root.put("tsplScript", tsplBuilder.toString())

        // Rendered Bitmap Base64 Preview (Exact 203 DPI Raster)
        if (previewBitmap != null) {
            try {
                val stream = ByteArrayOutputStream()
                previewBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val byteArray = stream.toByteArray()
                val b64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                root.put("previewImageBase64", "data:image/png;base64,$b64")
                root.put("previewPngBase64", b64)
            } catch (e: Exception) {
                Log.e(TAG, "Error encoding preview bitmap to base64: ${e.message}")
            }
        }

        return root.toString(2)
    }

    /**
     * Exports the JCPS file to the local "etiqueta" folder with the technical report number as filename.
     */
    suspend fun exportJcpsToFile(
        context: Context,
        test: TestRecordEntity,
        company: AppSettingEntity,
        density: Float = 1.0f,
        size: com.example.data.service.LabelPaperSize = com.example.data.service.LabelPaperSize.SIZE_50X30
    ): SavedJcpsResult = withContext(Dispatchers.IO) {
        val printerManager = NiimbotPrinterManager(context)
        val bitmap = printerManager.generateLabelBitmap(test, company, density, size)
        val jsonContent = generateJcpsJson(test, company, bitmap, size)

        val dir = getEtiquetaDirectory(context)
        val sanitizedReport = sanitizeReportNumber(test.reportNumber, test.equipmentTag)
        val fileName = "$sanitizedReport.jcps"
        val targetFile = File(dir, fileName)

        // Write file
        FileOutputStream(targetFile).use { fos ->
            fos.write(jsonContent.toByteArray(Charsets.UTF_8))
            fos.flush()
        }

        // Trigger MediaScanner so file appears immediately in storage browsers & USB
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf("application/json", "text/plain", "application/octet-stream"),
                null
            )
        } catch (e: Exception) {
            Log.w(TAG, "MediaScanner error: ${e.message}")
        }

        // Get FileProvider URI for sharing
        val contentUri: Uri? = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider error: ${e.message}")
            null
        }

        val displayFolder = getEtiquetaDisplayPath(context)
        Log.i(TAG, "Saved JCPS file successfully: ${targetFile.absolutePath}")

        return@withContext SavedJcpsResult(
            file = targetFile,
            uri = contentUri,
            fileName = fileName,
            folderPath = displayFolder,
            fullPath = targetFile.absolutePath,
            success = true,
            message = "Arquivo $fileName salvo com sucesso na pasta '$FOLDER_NAME'!"
        )
    }

    /**
     * Shares the exported JCPS file via standard Android share sheet (WhatsApp, Drive, Email, Niimbot app).
     */
    fun shareJcpsFile(context: Context, file: File, reportNumber: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Etiqueta Niimbot B1 (JCPS) - Laudo $reportNumber")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Arquivo de modelo de etiqueta Niimbot no formato JCPS para o Laudo Nº $reportNumber."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Modelo JCPS"))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing JCPS: ${e.message}")
        }
    }

    /**
     * Opens the exported JCPS file or its parent folder.
     */
    fun openJcpsFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/json")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(openIntent, "Abrir Arquivo JCPS"))
        } catch (e: Exception) {
            // Fallback: open text/plain
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "text/plain")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (err: Exception) {
                Log.e(TAG, "Error opening JCPS: ${err.message}")
            }
        }
    }
}
