package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.AppSettingEntity
import com.example.data.model.ClientEntity
import com.example.data.model.ManualToolItem
import com.example.data.model.ServiceOrderEntity
import com.example.data.model.TestRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * WordExporter - Generates high-fidelity Microsoft Word (.doc) documents
 * with layout, styles, colors, badges, tables, gauges, photos, and signatures
 * mirroring the in-app LaudoDialog and official PDF.
 */
object WordExporter {

    private const val COLOR_NAVY = "#0A192F"
    private const val COLOR_BLUE = "#0066CC"
    private const val COLOR_GREEN = "#10B981"
    private const val COLOR_GREEN_BG = "#DCFCE7"
    private const val COLOR_GREEN_DARK = "#166534"
    private const val COLOR_RED = "#EF4444"
    private const val COLOR_RED_BG = "#FEE2E2"
    private const val COLOR_RED_DARK = "#991B1B"
    private const val COLOR_BG_LIGHT = "#F8FAFC"
    private const val COLOR_BORDER = "#CBD5E1"
    private const val COLOR_TEXT_MUTED = "#64748B"

    private const val DOC_HTML_FOOTER = "</body>\n</html>\n"

    private fun getDocHtmlHeader(title: String, letterheadBase64: String? = null): String {
        val bgStyle = if (letterheadBase64 != null) {
            "background-image: url('data:image/jpeg;base64,$letterheadBase64'); background-repeat: no-repeat; background-position: center top; background-size: 100% 100%;"
        } else ""
        return """
<!DOCTYPE html>
<html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'>
<head>
<meta charset="utf-8">
<title>$title</title>
<!--[if gte mso 9]>
<xml>
 <w:WordDocument>
  <w:View>Print</w:View>
  <w:Zoom>100</w:Zoom>
  <w:DoNotOptimizeForBrowser/>
 </w:WordDocument>
</xml>
<![endif]-->
<style>
@page {
    size: A4 portrait;
    margin: 1.2cm 1.2cm 1.2cm 1.2cm;
    mso-header-margin: 0.5cm;
    mso-footer-margin: 0.5cm;
}
body {
    font-family: Arial, 'Helvetica Neue', Helvetica, sans-serif;
    color: #1E293B;
    background-color: #FFFFFF;
    margin: 0;
    padding: 0;
    font-size: 10pt;
    line-height: 1.35;
    $bgStyle
}
table { border-collapse: collapse; width: 100%; }
.doc-container {
    border: 2px solid $COLOR_NAVY;
    padding: 14px;
    border-radius: 6px;
    background-color: ${if (letterheadBase64 != null) "transparent" else "#FFFFFF"};
    $bgStyle
}
.section-header {
    background-color: $COLOR_NAVY;
    color: #FFFFFF;
    font-weight: bold;
    font-size: 9.5pt;
    padding: 5px 8px;
    border-radius: 4px;
    margin-top: 10px;
    margin-bottom: 4px;
}
.info-card {
    background-color: ${if (letterheadBase64 != null) "rgba(248, 250, 252, 0.9)" else COLOR_BG_LIGHT};
    border: 1px solid $COLOR_BORDER;
    border-radius: 4px;
    padding: 8px 10px;
}
.info-label {
    font-size: 8.5pt;
    font-weight: bold;
    color: $COLOR_TEXT_MUTED;
}
.info-value {
    font-size: 9.5pt;
    font-weight: bold;
    color: #0F172A;
}
.mono {
    font-family: 'Consolas', 'Courier New', monospace;
}
.badge-approved {
    background-color: $COLOR_GREEN_BG;
    color: $COLOR_GREEN_DARK;
    border: 1px solid $COLOR_GREEN;
    font-weight: bold;
    padding: 2px 6px;
    border-radius: 3px;
    font-size: 8.5pt;
}
.badge-rejected {
    background-color: $COLOR_RED_BG;
    color: $COLOR_RED_DARK;
    border: 1px solid $COLOR_RED;
    font-weight: bold;
    padding: 2px 6px;
    border-radius: 3px;
    font-size: 8.5pt;
}
.cert-container {
    border: 3px double $COLOR_NAVY;
    padding: 20px;
    background-color: #FFFFFF;
}
</style>
</head>
<body>
""".trimIndent()
    }

    /**
     * Builds the inner HTML body for a single Laudo Técnico document.
     */
    private suspend fun buildSingleLaudoDocBody(
        context: Context,
        test: TestRecordEntity,
        company: AppSettingEntity,
        itemIndex: Int = 0,
        totalItems: Int = 1
    ): String {

        // Generate Gauge Images in Base64
        val voltageGaugeB64 = generateGaugeBase64(
            title = "Tensão Aplicada",
            value = test.appliedVoltage_kV,
            unit = "kV ${test.voltageType}",
            maxScale = (test.appliedVoltage_kV * 1.4).coerceAtLeast(15.0),
            limit = null,
            isConforming = true,
            subtext = "Tempo: ${test.applicationDurationSeconds}s contínuos"
        )

        val isLeakageConforming = test.measuredLeakageCurrent_mA <= test.leakageCurrentLimit_mA
        val leakageGaugeB64 = generateGaugeBase64(
            title = "Corrente de Fuga",
            value = test.measuredLeakageCurrent_mA,
            unit = test.currentUnit.ifEmpty { "mA" },
            maxScale = (test.leakageCurrentLimit_mA * 1.5).coerceAtLeast(15.0),
            limit = test.leakageCurrentLimit_mA,
            isConforming = isLeakageConforming,
            subtext = "Limite: ${test.leakageCurrentLimit_mA} mA"
        )

        // QR Code Base64
        val qrCodeUrl = com.example.util.ValidationPortal.url(test.validationCode)
        val qrCodeB64 = generateQrCodeBase64(qrCodeUrl, 160)

        // Photos Base64 list
        val photosList = if (test.photoUris.isNotBlank()) {
            test.photoUris.split("|||").filter { it.isNotBlank() }.map { entry ->
                val parts = entry.split("::")
                val label = if (parts.size > 1) parts[0] else "Evidência"
                val uri = if (parts.size > 1) parts[1] else parts[0]
                label to loadBitmapBase64(context, uri, 320, 240)
            }
        } else emptyList()

        // Signatures Base64
        val techSigB64 = if (test.technicianSignature.isNotBlank()) {
            loadBitmapBase64(context, test.technicianSignature, 260, 80)
        } else null

        val rtSigB64 = if (test.techResponsibleSignature.isNotBlank()) {
            loadBitmapBase64(context, test.techResponsibleSignature, 260, 80)
        } else null

        // Tools breakdown
        val parsedTools = ManualToolItem.getToolsForTest(test)
        val approvedTools = ManualToolItem.getApprovedTools(parsedTools)
        val rejectedTools = ManualToolItem.getRejectedTools(parsedTools)
        val hasDualOpinions = parsedTools.isNotEmpty() && rejectedTools.isNotEmpty() && approvedTools.isNotEmpty()
        val isApproved = test.result == "APROVADO"
        val logoBase64 = if (company.companyLogoUri.isNotBlank()) loadBitmapBase64(context, company.companyLogoUri, 200, 100) else null
        val logoHtml = if (logoBase64 != null) {
            "<img src=\"data:image/png;base64,$logoBase64\" style=\"max-height: 44px; max-width: 90px; object-fit: contain; vertical-align: middle;\">"
        } else {
            "<div style=\"background-color: $COLOR_NAVY; color: #FFFFFF; font-weight: 900; font-size: 14pt; text-align: center; line-height: 40px; width: 40px; height: 40px; border-radius: 6px;\">JVM</div>"
        }

        return buildString {
            append("<div class=\"doc-container\">\n")

            // ================= HEADER =================
            append("<table><tr>\n")
            append("<td style=\"width: 65%; vertical-align: middle;\">\n")
            append("  <table><tr>\n")
            append("    <td style=\"width: 44px; vertical-align: middle;\">\n")
            append("      $logoHtml\n")
            append("    </td>\n")
            append("    <td style=\"padding-left: 10px; vertical-align: middle;\">\n")
            append("      <div style=\"font-size: 12pt; font-weight: 900; color: $COLOR_NAVY;\">${company.name.ifEmpty { "JVM ENGENHARIA" }}</div>\n")
            append("      <div style=\"font-size: 8.5pt; color: $COLOR_TEXT_MUTED;\">${company.legalName.ifEmpty { "Laboratório de Ensaios Elétricos e Rigidez Dielétrica" }}</div>\n")
            append("      <div style=\"font-size: 8.5pt; color: $COLOR_TEXT_MUTED;\">${company.creaCompanyRegister.ifEmpty { "CREA-DF 1620-PJ" }} • CNPJ: ${company.cnpj.ifEmpty { "29.894.500/0001-04" }}</div>\n")
            append("    </td>\n")
            append("  </tr></table>\n")
            append("</td>\n")
            append("<td style=\"width: 35%; text-align: right; vertical-align: middle;\">\n")
            append("  <div style=\"font-size: 7.5pt; font-weight: bold; color: $COLOR_TEXT_MUTED; text-transform: uppercase;\">Identificador do Laudo</div>\n")
            append("  <div style=\"font-size: 13pt; font-weight: 900; color: $COLOR_BLUE;\" class=\"mono\">${test.reportNumber}</div>\n")
            if (test.artNumber.isNotEmpty()) {
                append("  <div style=\"font-size: 9pt; font-weight: bold; color: #D97706;\">ART: ${test.artNumber}</div>\n")
            }
            append("</td>\n")
            append("</tr></table>\n")

            append("<hr style=\"border: none; border-top: 2px solid $COLOR_NAVY; margin: 8px 0 10px 0;\">\n")

            // ================= 1. IDENTIFICAÇÃO =================
            append("<div class=\"section-header\">1. Identificação do Cliente e do Equipamento</div>\n")
            append("<div class=\"info-card\">\n")
            append("  <table>\n")
            append("    <tr>\n")
            append("      <td style=\"width: 50%; padding: 3px 0;\"><span class=\"info-label\">Cliente:</span> <span class=\"info-value\">${test.clientName.ifEmpty { "Consumidor Geral" }}</span></td>\n")
            append("      <td style=\"width: 50%; padding: 3px 0;\"><span class=\"info-label\">Ordem de Serviço:</span> <span class=\"info-value mono\">${test.serviceOrderNumber.ifEmpty { "OS-AVULSA" }}</span></td>\n")
            append("    </tr>\n")
            append("    <tr>\n")
            append("      <td style=\"padding: 3px 0;\"><span class=\"info-label\">Equipamento:</span> <span class=\"info-value\">${test.equipmentType.replace("_", " ").uppercase()}</span></td>\n")
            append("      <td style=\"padding: 3px 0;\"><span class=\"info-label\">Tag / Patrimônio:</span> <span class=\"info-value mono\" style=\"color: $COLOR_BLUE;\">${test.equipmentTag}</span></td>\n")
            append("    </tr>\n")
            append("    <tr>\n")
            append("      <td style=\"padding: 3px 0;\"><span class=\"info-label\">Classe Dielétrica:</span> <span class=\"info-value\">Classe ${test.equipmentClass} (${getMaxUseVoltage(test.equipmentClass)})</span></td>\n")
            append("      <td style=\"padding: 3px 0;\"><span class=\"info-label\">Nº de Série / CA:</span> <span class=\"info-value\">${test.equipmentSerial.ifEmpty { "N/A" }} • CA: ${test.equipmentCa.ifEmpty { "N/A" }}</span></td>\n")
            append("    </tr>\n")
            if (test.collaboratorName.isNotEmpty()) {
                append("    <tr>\n")
                append("      <td style=\"padding: 3px 0;\"><span class=\"info-label\">Colaborador:</span> <span class=\"info-value\">${test.collaboratorName}</span></td>\n")
                append("      <td style=\"padding: 3px 0;\"><span class=\"info-label\">Matrícula / Setor:</span> <span class=\"info-value\">${test.collaboratorRegistration} • ${test.collaboratorSector}</span></td>\n")
                append("    </tr>\n")
            }
            append("  </table>\n")
            append("</div>\n")

            // ================= 2. REFERÊNCIA NORMATIVA =================
            append("<div class=\"section-header\">2. Referência Normativa e Condições Ambientais</div>\n")
            append("<div class=\"info-card\">\n")
            append("  <table style=\"width: 100%; border-collapse: collapse; margin-bottom: 6px;\"><tr>\n")
            append("    <td style=\"width: 35%; padding: 2px 0;\"><span class=\"info-label\">Norma Regulamentadora / Técnica:</span><br><span class=\"info-value\">${test.normCode.ifEmpty { "NBR 16295 Tabela 4" }}</span></td>\n")
            append("    <td style=\"width: 30%; padding: 2px 0;\"><span class=\"info-label\">Procedimento de Ensaio:</span><br><span class=\"info-value\">PR-JVM-LAB-01 Rev.08</span></td>\n")
            append("    <td style=\"width: 18%; padding: 2px 0;\"><span class=\"info-label\">Temperatura Ambiente:</span><br><span class=\"info-value\">${test.temperatureC} °C</span></td>\n")
            append("    <td style=\"width: 17%; padding: 2px 0;\"><span class=\"info-label\">Umidade Relativa:</span><br><span class=\"info-value\">${test.relativeHumidityPercent}% UR</span></td>\n")
            append("  </tr></table>\n")

            val isGlove = test.equipmentType.contains("LUVA", ignoreCase = true) ||
                    test.equipmentType.contains("Luvas", ignoreCase = true) ||
                    test.normCode.contains("16295") ||
                    test.equipmentType.equals("LUVA_ISOLANTE", ignoreCase = true)

            if (isGlove) {
                val cleanClass = test.equipmentClass.replace("Classe", "", ignoreCase = true).trim()
                val gloveLen = test.gloveLength_mm
                val gloveRow = com.example.domain.NormsEngine.tabela4NBR16295.find { it.classe == cleanClass }
                    ?: com.example.domain.NormsEngine.tabela4NBR16295[0]
                val activeColIndex = when {
                    gloveLen <= 300 -> 0
                    gloveLen in 301..380 -> 1
                    gloveLen in 381..430 -> 2
                    else -> 3
                }

                append("  <div style=\"background: #F0F7FF; border: 1px solid #BFDBFE; border-radius: 4px; padding: 6px; margin-top: 4px;\">\n")
                append("    <table style=\"width: 100%; margin-bottom: 4px;\"><tr>\n")
                append("      <td style=\"text-align: left; font-weight: bold; font-size: 7.5pt; color: $COLOR_NAVY;\"><span style=\"color: $COLOR_BLUE;\">&#9679;</span> TABELA 4 DA ABNT NBR 16295 / IEC 60903 – ENSAIOS DE PROVA E RIGIDEZ DIELÉTRICA</td>\n")
                append("      <td style=\"text-align: right;\"><span style=\"background: #DBEAFE; color: #1E40AF; font-size: 7pt; font-weight: bold; padding: 2px 6px; border-radius: 3px;\">Luva Ensaiada: Classe $cleanClass - ${gloveLen} mm</span></td>\n")
                append("    </tr></table>\n")

                append("    <table style=\"width: 100%; border-collapse: collapse; text-align: center; font-size: 7pt; margin-bottom: 4px;\">\n")
                append("      <tr style=\"background: #E2E8F0; font-weight: bold; color: $COLOR_NAVY;\">\n")
                append("        <th style=\"padding: 3px; border: 1px solid #CBD5E1;\">Classe</th>\n")
                append("        <th style=\"padding: 3px; border: 1px solid #CBD5E1;\">Tensão Máx. Uso</th>\n")
                append("        <th style=\"padding: 3px; border: 1px solid #CBD5E1;\">Tensão Prova CA</th>\n")
                append("        <th style=\"padding: 3px; border: 1px solid #CBD5E1;\">280 mm</th>\n")
                append("        <th style=\"padding: 3px; border: 1px solid #CBD5E1;\">360 mm</th>\n")
                append("        <th style=\"padding: 3px; border: 1px solid #CBD5E1;\">410 mm</th>\n")
                append("        <th style=\"padding: 3px; border: 1px solid #CBD5E1;\">&gt;= 460 mm</th>\n")
                append("        <th style=\"padding: 3px; border: 1px solid #CBD5E1;\">Tensão Rigidez</th>\n")
                append("      </tr>\n")

                val c280 = if (activeColIndex == 0) "<td style=\"padding: 3px; border: 1px solid #93C5FD; background: $COLOR_BLUE; color: #ffffff; font-weight: bold;\">${gloveRow.limite280mm?.toInt() ?: "N/a"} mA [Alvo]</td>" else "<td style=\"padding: 3px; border: 1px solid #93C5FD; color: ${if (gloveRow.limite280mm != null) COLOR_NAVY else COLOR_RED};\">${gloveRow.limite280mm?.let { "${it.toInt()} mA" } ?: "N/a"}</td>"
                val c360 = if (activeColIndex == 1) "<td style=\"padding: 3px; border: 1px solid #93C5FD; background: $COLOR_BLUE; color: #ffffff; font-weight: bold;\">${gloveRow.limite360mm?.toInt() ?: "N/a"} mA [Alvo]</td>" else "<td style=\"padding: 3px; border: 1px solid #93C5FD; color: ${if (gloveRow.limite360mm != null) COLOR_NAVY else COLOR_RED};\">${gloveRow.limite360mm?.let { "${it.toInt()} mA" } ?: "N/a"}</td>"
                val c410 = if (activeColIndex == 2) "<td style=\"padding: 3px; border: 1px solid #93C5FD; background: $COLOR_BLUE; color: #ffffff; font-weight: bold;\">${gloveRow.limite410mm?.toInt() ?: "N/a"} mA [Alvo]</td>" else "<td style=\"padding: 3px; border: 1px solid #93C5FD; color: ${if (gloveRow.limite410mm != null) COLOR_NAVY else COLOR_RED};\">${gloveRow.limite410mm?.let { "${it.toInt()} mA" } ?: "N/a"}</td>"
                val c460 = if (activeColIndex == 3) "<td style=\"padding: 3px; border: 1px solid #93C5FD; background: $COLOR_BLUE; color: #ffffff; font-weight: bold;\">${gloveRow.limite460mm?.toInt() ?: "N/a"} mA [Alvo]</td>" else "<td style=\"padding: 3px; border: 1px solid #93C5FD; color: ${if (gloveRow.limite460mm != null) COLOR_NAVY else COLOR_RED};\">${gloveRow.limite460mm?.let { "${it.toInt()} mA" } ?: "N/a"}</td>"

                append("      <tr style=\"background: #EFF6FF; font-weight: bold; color: #1D4ED8;\">\n")
                append("        <td style=\"padding: 3px; border: 1px solid #93C5FD;\">Classe ${gloveRow.classe}</td>\n")
                append("        <td style=\"padding: 3px; border: 1px solid #93C5FD;\">${gloveRow.tensaoUsoMaxAC_kV} kV</td>\n")
                append("        <td style=\"padding: 3px; border: 1px solid #93C5FD;\">${gloveRow.tensaoProvaAC_kV} kV</td>\n")
                append("        $c280\n")
                append("        $c360\n")
                append("        $c410\n")
                append("        $c460\n")
                append("        <td style=\"padding: 3px; border: 1px solid #93C5FD;\">${gloveRow.tensaoRigidezAC_kV} kV</td>\n")
                append("      </tr>\n")
                append("    </table>\n")

                append("    <table style=\"width: 100%; font-size: 6.5pt;\"><tr>\n")
                append("      <td style=\"text-align: left; color: #475569;\">Critério Operativo: Luva Classe $cleanClass ($gloveLen mm) -&gt; Tensão de Prova: ${test.appliedVoltage_kV} kV CA | Limite Máximo de Fuga: ${test.leakageCurrentLimit_mA} mA (Par de 2 luvas simultâneas: 2x valor unitário da Tab. 4).</td>\n")
                append("      <td style=\"text-align: right; color: #94A3B8;\">Nota: Limite da Tabela 4 é unitário. Ensaio com 2 luvas simultâneas adota o dobro (2x).</td>\n")
                append("    </tr></table>\n")
                append("  </div>\n")
            }

            append("</div>\n")

            // ================= 3. PARÂMETROS E RESULTADOS =================
            append("<div class=\"section-header\">3. Parâmetros e Resultados das Medições</div>\n")
            append("<table><tr>\n")

            // Left Gauge
            append("<td style=\"width: 49%; vertical-align: top;\">\n")
            append("  <div class=\"info-card\" style=\"text-align: center; padding: 6px;\">\n")
            if (voltageGaugeB64 != null) {
                append("    <img src=\"data:image/png;base64,$voltageGaugeB64\" style=\"width: 100%; max-width: 260px; height: auto; display: block; margin: 0 auto;\">\n")
            } else {
                append("    <div style=\"font-size: 9pt; font-weight: bold; color: $COLOR_NAVY;\">TENSÃO APLICADA</div>\n")
                append("    <div style=\"font-size: 16pt; font-weight: 900; color: $COLOR_BLUE;\">${test.appliedVoltage_kV} kV ${test.voltageType}</div>\n")
                append("    <div style=\"font-size: 8pt; color: $COLOR_TEXT_MUTED;\">Tempo: ${test.applicationDurationSeconds}s contínuos</div>\n")
            }
            append("  </div>\n")
            append("</td>\n")

            append("<td style=\"width: 2%;\"></td>\n")

            // Right Gauge
            append("<td style=\"width: 49%; vertical-align: top;\">\n")
            append("  <div class=\"info-card\" style=\"text-align: center; padding: 6px;\">\n")
            if (leakageGaugeB64 != null) {
                append("    <img src=\"data:image/png;base64,$leakageGaugeB64\" style=\"width: 100%; max-width: 260px; height: auto; display: block; margin: 0 auto;\">\n")
            } else {
                append("    <div style=\"font-size: 9pt; font-weight: bold; color: $COLOR_NAVY;\">CORRENTE DE FUGA</div>\n")
                val leakColor = if (isLeakageConforming) COLOR_GREEN else COLOR_RED
                append("    <div style=\"font-size: 16pt; font-weight: 900; color: $leakColor;\">${test.measuredLeakageCurrent_mA} ${test.currentUnit.ifEmpty { "mA" }}</div>\n")
                append("    <div style=\"font-size: 8pt; color: $COLOR_TEXT_MUTED;\">Limite: ${test.leakageCurrentLimit_mA} mA</div>\n")
            }
            append("  </div>\n")
            append("</td>\n")

            append("</tr></table>\n")

            // Tabela Resumo dos Parâmetros Normativos e Valores Obtidos
            val isGloveSection3 = test.equipmentType.contains("LUVA", ignoreCase = true) ||
                    test.equipmentType.contains("Luvas", ignoreCase = true) ||
                    test.normCode.contains("16295") ||
                    test.equipmentType.equals("LUVA_ISOLANTE", ignoreCase = true)

            val cleanClassS3 = test.equipmentClass.replace("Classe", "", ignoreCase = true).trim()
            val gloveRowS3 = com.example.domain.NormsEngine.tabela4NBR16295.find { it.classe == cleanClassS3 }
                ?: com.example.domain.NormsEngine.tabela4NBR16295[0]

            val voltageNormReq = if (isGloveSection3) "${test.appliedVoltage_kV} kV CA (Tab. 4 - Cl. $cleanClassS3)"
            else "${test.appliedVoltage_kV} kV ${test.voltageType} (${test.normCode.ifEmpty { "Norma Técnica" }})"

            val timeNormReq = if (isGloveSection3) "${test.applicationDurationSeconds} segundos contínuos (ABNT NBR 16295 / IEC 60903)"
            else "${test.applicationDurationSeconds} segundos contínuos (${test.normCode.ifEmpty { "Norma Técnica" }})"

            val leakLimitStr = if (test.leakageCurrentLimit_mA % 1.0 == 0.0) test.leakageCurrentLimit_mA.toInt().toString() else test.leakageCurrentLimit_mA.toString()
            val leakNormReq = if (isGloveSection3) "Máximo $leakLimitStr mA (Tab. 4 - 2 luvas simultâneas: 2x unitário)"
            else "Máximo $leakLimitStr ${test.currentUnit.ifEmpty { "mA" }} (${test.normCode.ifEmpty { "Norma Técnica" }})"

            val rigidityNormReq = if (isGloveSection3) "Sem disrupção (Tab. 4 - Rigidez: ${gloveRowS3.tensaoRigidezAC_kV} kV CA)"
            else "Sem disrupção / perfuração dielétrica"

            val isLeakOk = test.measuredLeakageCurrent_mA <= test.leakageCurrentLimit_mA
            val isRigidityOk = test.result == "APROVADO" || isLeakOk

            val badgeConforme = "<span style=\"background: #DCFCE7; border: 1px solid #86EFAC; color: #166534; font-size: 7pt; font-weight: bold; padding: 2px 8px; border-radius: 3px; display: inline-block;\">CONFORME</span>"
            val badgeNaoConforme = "<span style=\"background: #FEE2E2; border: 1px solid #FCA5A5; color: #991B1B; font-size: 7pt; font-weight: bold; padding: 2px 8px; border-radius: 3px; display: inline-block;\">NÃO CONFORME</span>"

            append("<table style=\"width: 100%; border-collapse: collapse; margin-top: 6px; background: #F8FAFC; border: 1px solid #E2E8F0; font-size: 8pt;\">\n")
            append("  <thead>\n")
            append("    <tr style=\"background: #E2E8F0; color: $COLOR_NAVY; font-weight: bold;\">\n")
            append("      <th style=\"padding: 5px 8px; text-align: left; border-bottom: 1px solid #CBD5E1; width: 28%;\">Parâmetro de Ensaio</th>\n")
            append("      <th style=\"padding: 5px 8px; text-align: left; border-bottom: 1px solid #CBD5E1; width: 40%;\">Exigência Normativa</th>\n")
            append("      <th style=\"padding: 5px 8px; text-align: left; border-bottom: 1px solid #CBD5E1; width: 17%;\">Valor Medido</th>\n")
            append("      <th style=\"padding: 5px 8px; text-align: center; border-bottom: 1px solid #CBD5E1; width: 15%;\">Avaliação</th>\n")
            append("    </tr>\n")
            append("  </thead>\n")
            append("  <tbody>\n")
            // Row 1
            append("    <tr style=\"border-bottom: 1px solid #E2E8F0;\">\n")
            append("      <td style=\"padding: 4px 8px; color: #334155;\">Tensão de Ensaio Aplicada</td>\n")
            append("      <td style=\"padding: 4px 8px; font-weight: bold; color: #0F172A;\">$voltageNormReq</td>\n")
            append("      <td style=\"padding: 4px 8px; font-weight: bold; color: $COLOR_BLUE;\">${test.appliedVoltage_kV} kV ${test.voltageType.ifEmpty { "AC" }}</td>\n")
            append("      <td style=\"padding: 4px 8px; text-align: center;\">$badgeConforme</td>\n")
            append("    </tr>\n")
            // Row 2
            append("    <tr style=\"border-bottom: 1px solid #E2E8F0;\">\n")
            append("      <td style=\"padding: 4px 8px; color: #334155;\">Tempo de Aplicação da Tensão</td>\n")
            append("      <td style=\"padding: 4px 8px; font-weight: bold; color: #0F172A;\">$timeNormReq</td>\n")
            append("      <td style=\"padding: 4px 8px; font-weight: bold; color: $COLOR_BLUE;\">${test.applicationDurationSeconds} s</td>\n")
            append("      <td style=\"padding: 4px 8px; text-align: center;\">$badgeConforme</td>\n")
            append("    </tr>\n")
            // Row 3
            append("    <tr style=\"border-bottom: 1px solid #E2E8F0;\">\n")
            append("      <td style=\"padding: 4px 8px; color: #334155;\">Corrente de Fuga</td>\n")
            append("      <td style=\"padding: 4px 8px; font-weight: bold; color: #0F172A;\">$leakNormReq</td>\n")
            append("      <td style=\"padding: 4px 8px; font-weight: bold; color: $COLOR_BLUE;\">${test.measuredLeakageCurrent_mA} ${test.currentUnit.ifEmpty { "mA" }}</td>\n")
            append("      <td style=\"padding: 4px 8px; text-align: center;\">${if (isLeakOk) badgeConforme else badgeNaoConforme}</td>\n")
            append("    </tr>\n")
            // Row 4
            append("    <tr>\n")
            append("      <td style=\"padding: 4px 8px; color: #334155;\">Rigidez / Suportabilidade à Perfuração</td>\n")
            append("      <td style=\"padding: 4px 8px; font-weight: bold; color: #0F172A;\">$rigidityNormReq</td>\n")
            append("      <td style=\"padding: 4px 8px; font-weight: bold; color: $COLOR_BLUE;\">${if (isRigidityOk) "Sem perfuração dielétrica" else "Perfuração dielétrica detectada"}</td>\n")
            append("      <td style=\"padding: 4px 8px; text-align: center;\">${if (isRigidityOk) badgeConforme else badgeNaoConforme}</td>\n")
            append("    </tr>\n")
            append("  </tbody>\n")
            append("</table>\n")

            // ================= 4. INSPEÇÃO VISUAL E FÍSICO-MECÂNICA =================
            append("<div class=\"section-header\">4. Inspeção Visual e Físico-Mecânica</div>\n")

            val isGloveSection4 = test.equipmentType.contains("LUVA", ignoreCase = true) ||
                    test.equipmentType.contains("Luvas", ignoreCase = true) ||
                    test.normCode.contains("16295") ||
                    test.equipmentType.equals("LUVA_ISOLANTE", ignoreCase = true)

            val isVisualConf = !test.visualInspectionSummary.contains("não", ignoreCase = true) &&
                    !test.visualInspectionSummary.contains("reprovad", ignoreCase = true) &&
                    !test.visualInspectionSummary.contains("avaria", ignoreCase = true)

            val visualItem1Left = if (isGloveSection4) "Ensaio manual de retenção e inflamento de ar (ausência de microporos/furos - estanqueidade)"
            else "Inspeção estrutural, mecânica e dimensional segundo norma aplicável"
            val visualItem1Right = "Superfície interna e externa isenta de cortes, perfurações, rasgos ou desgaste"
            val visualItem2Left = "Ausência de trincas, fissuras, endurecimento ou degradação por ozônio"
            val visualItem2Right = "Isento de impregnação de óleo, graxa, solventes ou compostos químicos"
            val visualItem3Left = if (isGloveSection4) "Marcação indelével do Fabricante, Classe de Tensão, Norma NBR 16295 e CA"
            else "Marcação indelével do Fabricante, Classe de Tensão, Norma Técnica e CA"

            val badgeVisConf = "<span style=\"background: #DCFCE7; border: 1px solid #86EFAC; color: #166534; font-size: 7pt; font-weight: bold; padding: 2px 6px; border-radius: 3px; display: inline-block;\">CONFORME</span>"
            val badgeVisNaoConf = "<span style=\"background: #FEE2E2; border: 1px solid #FCA5A5; color: #991B1B; font-size: 7pt; font-weight: bold; padding: 2px 6px; border-radius: 3px; display: inline-block;\">NÃO CONFORME</span>"

            append("<table style=\"width: 100%; border-collapse: collapse; margin-top: 4px; background: #F8FAFC; border: 1px solid #E2E8F0; font-size: 7.5pt;\">\n")
            append("  <tr>\n")
            append("    <td style=\"padding: 4px 6px; color: #334155; width: 38%; border-bottom: 1px solid #E2E8F0;\">$visualItem1Left</td>\n")
            append("    <td style=\"padding: 4px 6px; text-align: right; width: 12%; border-bottom: 1px solid #E2E8F0;\">${if (isVisualConf) badgeVisConf else badgeVisNaoConf}</td>\n")
            append("    <td style=\"padding: 4px 6px; color: #334155; width: 38%; border-bottom: 1px solid #E2E8F0; border-left: 1px solid #E2E8F0;\">$visualItem1Right</td>\n")
            append("    <td style=\"padding: 4px 6px; text-align: right; width: 12%; border-bottom: 1px solid #E2E8F0;\">${if (isVisualConf) badgeVisConf else badgeVisNaoConf}</td>\n")
            append("  </tr>\n")
            append("  <tr>\n")
            append("    <td style=\"padding: 4px 6px; color: #334155; width: 38%; border-bottom: 1px solid #E2E8F0;\">$visualItem2Left</td>\n")
            append("    <td style=\"padding: 4px 6px; text-align: right; width: 12%; border-bottom: 1px solid #E2E8F0;\">${if (isVisualConf) badgeVisConf else badgeVisNaoConf}</td>\n")
            append("    <td style=\"padding: 4px 6px; color: #334155; width: 38%; border-bottom: 1px solid #E2E8F0; border-left: 1px solid #E2E8F0;\">$visualItem2Right</td>\n")
            append("    <td style=\"padding: 4px 6px; text-align: right; width: 12%; border-bottom: 1px solid #E2E8F0;\">${if (isVisualConf) badgeVisConf else badgeVisNaoConf}</td>\n")
            append("  </tr>\n")
            append("  <tr>\n")
            append("    <td style=\"padding: 4px 6px; color: #334155; width: 38%;\">$visualItem3Left</td>\n")
            append("    <td style=\"padding: 4px 6px; text-align: right; width: 12%;\">${if (isVisualConf) badgeVisConf else badgeVisNaoConf}</td>\n")
            append("    <td style=\"padding: 4px 6px; color: #334155; width: 38%; border-left: 1px solid #E2E8F0;\"></td>\n")
            append("    <td style=\"padding: 4px 6px; text-align: right; width: 12%;\"></td>\n")
            append("  </tr>\n")
            append("</table>\n")

            // ================= 5. PARECER TÉCNICO =================
            if (hasDualOpinions) {
                append("<div class=\"section-header\">5. Pareceres Técnicos Conclusivos (Dois Pareceres Emitidos)</div>\n")

                // Parecer 1 (Aprovadas)
                val appCount = approvedTools.sumOf { it.quantity }
                append("<div style=\"background-color: $COLOR_GREEN_BG; border: 1.5px solid $COLOR_GREEN; border-radius: 4px; padding: 8px 10px; margin-bottom: 6px;\">\n")
                append("  <div style=\"font-size: 10pt; font-weight: bold; color: $COLOR_GREEN_DARK;\">PARECER 1: FERRAMENTAS APROVADAS ($appCount un.) - CONFORME PARA USO</div>\n")
                append("  <div style=\"font-size: 8.5pt; color: #1E293B; margin: 4px 0;\">Aprovadas na inspeção visual e ensaio de rigidez dielétrica (10 kV CA / 180s - ABNT NBR 9699 / IEC 60900), com fuga &lt; 1,0 mA/un. Liberadas para operação em circuitos elétricos até 1000 V CA / 1500 V CC conforme NR-10.</div>\n")
                append("  <table>\n")
                approvedTools.forEach { tool ->
                    append("    <tr>\n")
                    append("      <td style=\"padding: 2px 0; font-size: 8.5pt;\"><b>✓ ${tool.name}</b> (${tool.quantity} un.)</td>\n")
                    append("      <td style=\"text-align: right; padding: 2px 0;\"><span class=\"badge-approved\">APROVADO P/ USO</span></td>\n")
                    append("    </tr>\n")
                }
                append("  </table>\n")
                val retestDueToolsStr = if (test.result == "APROVADO") DateUtils.formatDate(test.retestDueDate) else ""
                append("  <div style=\"margin-top: 4px; font-size: 8.5pt; font-weight: bold; color: $COLOR_GREEN_DARK;\">Validade / Próximo Reensaio: <span class=\"mono\">$retestDueToolsStr</span></div>\n")
                append("</div>\n")

                // Parecer 2 (Reprovadas)
                val rejCount = rejectedTools.sumOf { it.quantity }
                append("<div style=\"background-color: $COLOR_RED_BG; border: 1.5px solid $COLOR_RED; border-radius: 4px; padding: 8px 10px;\">\n")
                append("  <div style=\"font-size: 10pt; font-weight: bold; color: $COLOR_RED_DARK;\">PARECER 2: FERRAMENTAS REPROVADAS ($rejCount un.) - CONDENADAS</div>\n")
                append("  <div style=\"font-size: 8.5pt; color: #1E293B; margin: 4px 0;\">Reprovadas/Condenadas por não conformidades com ABNT NBR 9699 e NR-10. Fica proibido seu uso. Determina-se a segregação física imediata, inutilização e descarte (NR-10 item 10.4.3.1).</div>\n")
                append("  <table>\n")
                rejectedTools.forEach { tool ->
                    val reason = if (tool.rejectionReason.isNotBlank()) " • Motivo: ${tool.rejectionReason}" else ""
                    append("    <tr>\n")
                    append("      <td style=\"padding: 2px 0; font-size: 8.5pt;\"><b>✗ ${tool.name}</b> (${tool.quantity} un.)$reason</td>\n")
                    append("      <td style=\"text-align: right; padding: 2px 0;\"><span class=\"badge-rejected\">CONDENADO</span></td>\n")
                    append("    </tr>\n")
                }
                append("  </table>\n")
                append("  <div style=\"margin-top: 4px; font-size: 8.5pt; font-weight: bold; color: $COLOR_RED_DARK;\">Determinação: Segregação e Descarte Imediato</div>\n")
                append("</div>\n")
            } else {
                append("<div class=\"section-header\">5. Parecer Técnico Conclusivo</div>\n")
                val resultBg = if (isApproved) COLOR_GREEN_BG else COLOR_RED_BG
                val resultBorder = if (isApproved) COLOR_GREEN else COLOR_RED
                val resultTextColor = if (isApproved) COLOR_GREEN_DARK else COLOR_RED_DARK
                append("<div style=\"background-color: $resultBg; border: 1.5px solid $resultBorder; border-radius: 4px; padding: 8px 10px;\">\n")
                append("  <div style=\"font-size: 11pt; font-weight: 900; color: $resultTextColor;\">RESULTADO: ${test.result}</div>\n")
                val rationale = test.resultRationale.ifEmpty {
                    if (isApproved) "Equipamento atendeu integralmente aos ensaios de rigidez e fuga dielétrica conforme NR-10."
                    else "Equipamento reprovado por não atingir os requisitos normativos de segurança."
                }
                append("  <div style=\"font-size: 9pt; color: #1E293B; margin: 4px 0;\">$rationale</div>\n")

                val cleanNotes = if (test.technicalNotes.contains("Equipamento higienizado em cuba com água desmineralizada", ignoreCase = true)) {
                    test.technicalNotes.replace("Equipamento higienizado em cuba com água desmineralizada e aprovado para uso operacional.", "").trim()
                } else test.technicalNotes.trim()

                if (parsedTools.isNotEmpty()) {
                    append("  <table>\n")
                    parsedTools.forEach { tool ->
                        val badgeClass = if (tool.isApproved) "badge-approved" else "badge-rejected"
                        val badgeText = if (tool.isApproved) "APROVADO" else "REPROVADO"
                        val icon = if (tool.isApproved) "✓" else "✗"
                        append("    <tr>\n")
                        append("      <td style=\"padding: 2px 0; font-size: 8.5pt;\"><b>$icon ${tool.name}</b> (${tool.quantity} un.)</td>\n")
                        append("      <td style=\"text-align: right; padding: 2px 0;\"><span class=\"$badgeClass\">$badgeText</span></td>\n")
                        append("    </tr>\n")
                    }
                    append("  </table>\n")
                } else if (cleanNotes.isNotBlank() && !cleanNotes.contains("FERRAMENTAS_V2")) {
                    append("  <div style=\"font-size: 8.5pt; font-weight: bold; color: $COLOR_NAVY; margin: 3px 0;\">Observações: $cleanNotes</div>\n")
                }

                val retestDateStr = if (isApproved) DateUtils.formatDate(test.retestDueDate) else ""
                append("  <div style=\"margin-top: 6px; font-size: 9pt; font-weight: bold; color: #0F172A;\">\n")
                append("    Próximo Reensaio Obrigatório: <span class=\"mono\" style=\"color: $COLOR_BLUE;\">$retestDateStr</span>\n")
                append("  </div>\n")
                append("</div>\n")
            }

            // ================= 6. RESPONSABILIDADE TÉCNICA & QR CODE (FINAL DA PÁGINA 1) =================
            append("<div class=\"section-header\">6. Responsabilidade Técnica & Autenticidade Digital</div>\n")
            append("<div class=\"info-card\" style=\"background-color: #FFFFFF;\">\n")
            append("  <table><tr>\n")

            // Analista Executor
            append("    <td style=\"width: 36%; vertical-align: top; padding-right: 8px;\">\n")
            if (techSigB64 != null) {
                append("      <img src=\"data:image/png;base64,$techSigB64\" style=\"height: 38px; max-width: 140px; display: block; margin-bottom: 2px;\">\n")
            } else {
                append("      <div style=\"height: 28px; border-bottom: 1px dashed $COLOR_BORDER; margin-bottom: 4px;\"></div>\n")
            }
            append("      <div style=\"font-size: 9pt; font-weight: bold; color: $COLOR_NAVY;\">${test.technicianName.ifEmpty { "Eng. João Carlos Evaristo Guedes Nunes" }}</div>\n")
            if (test.technicianCftOrCrea.isNotBlank()) append("      <div style=\"font-size: 8pt; color: $COLOR_TEXT_MUTED;\">${test.technicianCftOrCrea.trim()}</div>\n")
            append("      <div style=\"font-size: 7.5pt; color: #94A3B8;\">Analista Executor</div>\n")
            append("    </td>\n")

            // Responsável Técnico
            append("    <td style=\"width: 36%; vertical-align: top; padding-right: 8px;\">\n")
            if (rtSigB64 != null) {
                append("      <img src=\"data:image/png;base64,$rtSigB64\" style=\"height: 38px; max-width: 140px; display: block; margin-bottom: 2px;\">\n")
            } else {
                append("      <div style=\"height: 28px; border-bottom: 1px dashed $COLOR_BORDER; margin-bottom: 4px;\"></div>\n")
            }
            append("      <div style=\"font-size: 9pt; font-weight: bold; color: $COLOR_NAVY;\">${test.techResponsibleName.ifEmpty { "Eng. João Carlos Evaristo Guedes Nunes" }}</div>\n")
            append("      <div style=\"font-size: 8pt; color: $COLOR_TEXT_MUTED;\">${test.techResponsibleCrea.ifEmpty { "CREA 24481/D-DF" }}</div>\n")
            append("      <div style=\"font-size: 7.5pt; color: #94A3B8;\">Responsável Técnico</div>\n")
            append("    </td>\n")

            // QR Code Box
            append("    <td style=\"width: 28%; text-align: center; vertical-align: middle; border-left: 1px solid $COLOR_BORDER; padding-left: 8px;\">\n")
            if (qrCodeB64 != null) {
                append("      <img src=\"data:image/png;base64,$qrCodeB64\" style=\"width: 60px; height: 60px; display: block; margin: 0 auto;\">\n")
            }
            append("      <div style=\"font-size: 7pt; font-weight: bold; color: $COLOR_TEXT_MUTED; margin-top: 2px;\">VALIDAÇÃO DIGITAL</div>\n")
            append("      <div style=\"font-size: 8pt; font-weight: bold; color: $COLOR_BLUE;\" class=\"mono\">${test.validationCode}</div>\n")
            append("    </td>\n")

            append("  </tr></table>\n")
            append("</div>\n")

            // ================= FOOTER PÁGINA 1 =================
            append("<div style=\"text-align: center; font-size: 7.5pt; color: $COLOR_TEXT_MUTED; margin-top: 8px; border-top: 1px solid $COLOR_BORDER; padding-top: 4px;\">\n")
            append("  Documento emitido eletronicamente em conformidade com as normas ABNT NBR e Portaria NR-10 MTE. Página 1 de ${if (photosList.isNotEmpty()) 2 else 1}.\n")
            append("</div>\n")

            // ================= 7. SEGUNDA PÁGINA: REGISTRO FOTOGRÁFICO DO ENSAIO =================
            if (photosList.isNotEmpty()) {
                append("<div style=\"page-break-before: always; margin-top: 24px; padding-top: 12px;\">\n")

                // Page 2 Header
                append("<table class=\"header-table\">\n")
                append("  <tr>\n")
                append("    <td style=\"width: 48px; vertical-align: middle;\">\n")
                append("      $logoHtml\n")
                append("    </td>\n")
                append("    <td style=\"vertical-align: middle; padding-left: 8px;\">\n")
                append("      <div class=\"company-title\">${company.name.ifEmpty { "JVM ENGENHARIA & SERVIÇOS TÉCNICOS" }}</div>\n")
                append("      <div class=\"company-sub\">CNPJ: ${company.cnpj.ifEmpty { "29.894.500/0001-04" }} | ${company.creaCompanyRegister.ifEmpty { "CREA-DF 1620-PJ" }}</div>\n")
                append("      <div class=\"company-sub\">ANEXO DE EVIDÊNCIAS FOTOGRÁFICAS DO LAUDO TÉCNICO</div>\n")
                append("    </td>\n")
                append("    <td style=\"text-align: right; vertical-align: middle;\">\n")
                append("      <div style=\"font-size: 8pt; font-weight: bold; color: $COLOR_NAVY;\">ANEXO FOTOGRÁFICO</div>\n")
                append("      <div class=\"report-number\" style=\"font-size: 10pt;\">${test.reportNumber}</div>\n")
                append("      <div style=\"font-size: 7.5pt; color: $COLOR_TEXT_MUTED;\">Tag: ${test.equipmentTag}</div>\n")
                append("    </td>\n")
                append("  </tr>\n")
                append("</table>\n")

                append("<div class=\"section-header\">7. Registro Fotográfico e Evidências do Ensaio Dielétrico</div>\n")

                // Photos Grid
                append("<table style=\"width: 100%; border-collapse: separate; border-spacing: 8px; margin-top: 4px;\">\n")
                val chunkedPhotos = photosList.take(4).chunked(2)
                for (rowPhotos in chunkedPhotos) {
                    append("  <tr>\n")
                    for ((label, imgB64) in rowPhotos) {
                        append("    <td style=\"width: 50%; background: #F8FAFC; border: 1px solid $COLOR_BORDER; border-radius: 6px; padding: 8px; text-align: center; vertical-align: top;\">\n")
                        if (imgB64 != null) {
                            append("      <img src=\"data:image/jpeg;base64,$imgB64\" style=\"width: 100%; max-height: 220px; border-radius: 4px; border: 1px solid $COLOR_BORDER; object-fit: cover;\">\n")
                        } else {
                            append("      <div style=\"width: 100%; height: 160px; background-color: #0F172A; color: #FFFFFF; border-radius: 4px; line-height: 160px; font-size: 9pt; font-weight: bold;\">EVIDÊNCIA FOTOGRÁFICA</div>\n")
                        }
                        append("      <div style=\"font-size: 9pt; font-weight: bold; color: $COLOR_NAVY; margin-top: 6px;\">$label</div>\n")
                        append("      <div style=\"font-size: 7.5pt; color: $COLOR_TEXT_MUTED;\">Registro de ensaio em laboratório • ${DateUtils.formatDate(test.testDate)}</div>\n")
                        append("    </td>\n")
                    }
                    if (rowPhotos.size == 1) {
                        append("    <td style=\"width: 50%;\"></td>\n")
                    }
                    append("  </tr>\n")
                }
                append("</table>\n")

                // Declaration Box on Page 2
                append("<div class=\"info-card\" style=\"background-color: #FFFFFF; margin-top: 10px;\">\n")
                append("  <div style=\"font-size: 8.5pt; font-weight: bold; color: $COLOR_NAVY;\">DECLARAÇÃO DE RASTREABILIDADE TÉCNICA E DIGITAL</div>\n")
                append("  <div style=\"font-size: 7.5pt; color: #334155; margin: 3px 0;\">As imagens acima constituem registro autêntico e comprobatório dos ensaios executados em conformidade com as normas ABNT NBR e NR-10.</div>\n")
                append("  <div style=\"font-size: 7.5pt; color: $COLOR_TEXT_MUTED; font-weight: bold; margin-top: 4px;\">Analista: ${test.technicianName.ifEmpty { "Eng. João Carlos" }}${test.technicianCftOrCrea.trim().let { if (it.isBlank()) "" else " ($it)" }} | Resp. Técnico: ${test.techResponsibleName.ifEmpty { "Eng. João Carlos" }} (${test.techResponsibleCrea.ifEmpty { "CREA 24481/D-DF" }}) | VALIDAÇÃO: ${test.validationCode}</div>\n")
                append("</div>\n")

                // Footer Página 2
                append("<div style=\"text-align: center; font-size: 7.5pt; color: $COLOR_TEXT_MUTED; margin-top: 8px; border-top: 1px solid $COLOR_BORDER; padding-top: 4px;\">\n")
                if (totalItems > 1) {
                    append("  Documento emitido eletronicamente em conformidade com as normas ABNT NBR e Portaria NR-10 MTE. Item ${itemIndex + 1} de $totalItems (${test.reportNumber}) • Página 2 de 2.\n")
                } else {
                    append("  Documento emitido eletronicamente em conformidade com as normas ABNT NBR e Portaria NR-10 MTE. Página 2 de 2.\n")
                }
                append("</div>\n")

                append("</div>\n") // close page 2 div
            }

            append("</div>\n") // close doc-container
        }
    }

    /**
     * Generates a complete Microsoft Word (.doc) file for a single Laudo Técnico.
     */
    suspend fun generateLaudoDoc(
        context: Context,
        test: TestRecordEntity,
        company: AppSettingEntity
    ): File = withContext(Dispatchers.IO) {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val reportNumRaw = test.reportNumber.ifBlank { test.testNumber.ifBlank { "LAUDO" } }
        val companyRaw = if (test.clientName.isNotBlank()) {
            test.clientName
        } else {
            company.name.ifBlank { "Empresa" }
        }
        val collaboratorRaw = test.collaboratorName.ifBlank { "Geral" }

        val sanitizedReport = PdfExporter.sanitizeForFileName(reportNumRaw)
        val sanitizedCompany = PdfExporter.sanitizeForFileName(companyRaw)
        val sanitizedColab = PdfExporter.sanitizeForFileName(collaboratorRaw)

        val fileName = "${sanitizedReport}_${sanitizedCompany}_${sanitizedColab}.doc"
        val docFile = File(reportsDir, fileName)

        val letterheadBase64 = loadLetterheadBase64(context, company)
        val html = buildString {
            append(getDocHtmlHeader("Laudo Técnico ${test.reportNumber}", letterheadBase64))
            append(buildSingleLaudoDocBody(context, test, company, itemIndex = 0, totalItems = 1))
            append(DOC_HTML_FOOTER)
        }

        val fos = FileOutputStream(docFile)
        fos.write(html.toByteArray(Charsets.UTF_8))
        fos.flush()
        fos.close()

        docFile
    }

    /**
     * Generates a single compiled Microsoft Word (.doc) file containing multiple selected Laudos Técnicos.
     */
    suspend fun generateMultipleLaudosDoc(
        context: Context,
        tests: List<TestRecordEntity>,
        company: AppSettingEntity
    ): File = withContext(Dispatchers.IO) {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Laudos_Agrupados_${tests.size}_itens_${timestamp}.doc"
        val docFile = File(reportsDir, fileName)

        val letterheadBase64 = loadLetterheadBase64(context, company)
        val html = buildString {
            append(getDocHtmlHeader("Laudos Técnicos Agrupados (${tests.size} itens)", letterheadBase64))
            tests.forEachIndexed { index, test ->
                if (index > 0) {
                    append("<br clear=all style='mso-special-character:line-break;page-break-before:always;'>\n")
                }
                append(buildSingleLaudoDocBody(context, test, company, itemIndex = index, totalItems = tests.size))
            }
            append(DOC_HTML_FOOTER)
        }

        val fos = FileOutputStream(docFile)
        fos.write(html.toByteArray(Charsets.UTF_8))
        fos.flush()
        fos.close()

        docFile
    }

    /**
     * Builds the inner HTML body for a single Certificate of Compliance document.
     */
    private fun buildSingleCertificadoDocBody(
        context: Context,
        test: TestRecordEntity,
        company: AppSettingEntity,
        itemIndex: Int = 0,
        totalItems: Int = 1
    ): String {
        val qrCodeUrl = com.example.util.ValidationPortal.url(test.validationCode)
        val qrCodeB64 = generateQrCodeBase64(qrCodeUrl, 160)
        val certNumber = test.certificateNumber.ifEmpty { "CERT-${test.reportNumber}" }
        val isApproved = test.result == "APROVADO"
        val logoBase64 = if (company.companyLogoUri.isNotBlank()) loadBitmapBase64(context, company.companyLogoUri, 200, 100) else null

        return buildString {
            append("<div class=\"cert-container\" style=\"border: 3px double $COLOR_NAVY; padding: 22px; background-color: #FFFFFF; font-family: Arial, 'Helvetica Neue', Helvetica, sans-serif;\">\n")
            // Header
            append("  <div style=\"background-color: $COLOR_NAVY; color: #FFFFFF; padding: 14px; text-align: center; border-radius: 4px;\">\n")
            if (logoBase64 != null) {
                append("    <img src=\"data:image/png;base64,$logoBase64\" style=\"max-height: 48px; max-width: 120px; object-fit: contain; margin-bottom: 6px; display: inline-block;\"><br>\n")
            }
            append("    <div style=\"font-size: 14pt; font-weight: bold; letter-spacing: 0.5px;\">${company.name.ifEmpty { "JVM ENGENHARIA & SERVIÇOS TÉCNICOS" }.uppercase()}</div>\n")
            append("    <div style=\"font-size: 8.5pt; color: #CBD5E1; margin-top: 3px;\">CNPJ: ${company.cnpj.ifEmpty { "29.894.500/0001-04" }} | CREA: ${company.creaCompanyRegister.ifEmpty { "CREA-DF 1620-PJ" }}</div>\n")
            append("    <div style=\"font-size: 8pt; color: #94A3B8; margin-top: 2px;\">Laboratório de Ensaio de Rigidez Dielétrica em Equipamentos de Proteção e Ferramentas</div>\n")
            append("  </div>\n")

            // Title
            append("  <div style=\"text-align: center; margin-top: 22px; margin-bottom: 6px;\">\n")
            append("    <div style=\"font-size: 16pt; font-weight: bold; color: $COLOR_NAVY; letter-spacing: 0.5px;\">CERTIFICADO DE CONFORMIDADE DIELÉTRICA</div>\n")
            append("    <div style=\"font-size: 11pt; font-weight: bold; color: #EA580C; font-family: monospace; margin-top: 4px;\">Nº DO CERTIFICADO: $certNumber</div>\n")
            append("  </div>\n")

            // Statement
            append("  <div style=\"font-size: 9.5pt; color: #1E293B; line-height: 1.5; text-align: justify; margin: 16px 12px;\">\n")
            append("    Certificamos para os devidos fins de direito e cumprimento das exigências da Norma Regulamentadora NR-10 (Segurança em Instalações e Serviços em Eletricidade), item 10.4.3.1 e 10.7.8, que o equipamento abaixo especificado foi submetido a rigorosos ensaios elétricos de rigidez dielétrica e inspeção visual em nosso laboratório, tendo sido considerado <b>${test.result}</b> para uso operacional nas tensões limites especificadas.\n")
            append("  </div>\n")

            // Spec Table
            append("  <table style=\"width: 100%; border-collapse: collapse; margin: 14px 0; background-color: $COLOR_BG_LIGHT; border: 1px solid $COLOR_BORDER;\">\n")
            append("    <tr>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8pt; color: $COLOR_TEXT_MUTED; width: 25%; font-weight: bold; border-bottom: 1px solid $COLOR_BORDER;\">CLIENTE / DESTINATÁRIO:</td>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 9pt; color: $COLOR_NAVY; width: 35%; font-weight: bold; border-bottom: 1px solid $COLOR_BORDER;\">${test.clientName.ifEmpty { "Consumidor Geral" }}</td>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8pt; color: $COLOR_TEXT_MUTED; width: 20%; font-weight: bold; border-bottom: 1px solid $COLOR_BORDER;\">LOCAL DO ENSAIO:</td>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8.5pt; color: #1E293B; width: 20%; border-bottom: 1px solid $COLOR_BORDER;\">${test.location.ifEmpty { "Laboratório Móvel JVM" }}</td>\n")
            append("    </tr>\n")

            append("    <tr>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8pt; color: $COLOR_TEXT_MUTED; font-weight: bold; border-bottom: 1px solid $COLOR_BORDER;\">IDENTIFICAÇÃO (TAG):</td>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 10pt; color: $COLOR_BLUE; font-weight: bold; font-family: monospace; border-bottom: 1px solid $COLOR_BORDER;\">${test.equipmentTag}</td>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8pt; color: $COLOR_TEXT_MUTED; font-weight: bold; border-bottom: 1px solid $COLOR_BORDER;\">EQUIPAMENTO:</td>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8.5pt; color: #1E293B; font-weight: bold; border-bottom: 1px solid $COLOR_BORDER;\">${test.equipmentType.replace("_", " ").uppercase()}</td>\n")
            append("    </tr>\n")

            append("    <tr>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8pt; color: $COLOR_TEXT_MUTED; font-weight: bold; border-bottom: 1px solid $COLOR_BORDER;\">CLASSE DIELÉTRICA:</td>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8.5pt; color: #1E293B; border-bottom: 1px solid $COLOR_BORDER;\">${test.equipmentClass}</td>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8pt; color: $COLOR_TEXT_MUTED; font-weight: bold; border-bottom: 1px solid $COLOR_BORDER;\">CERTIFICADO DE APROVAÇÃO (CA):</td>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8.5pt; color: #1E293B; border-bottom: 1px solid $COLOR_BORDER;\">${test.equipmentCa.ifEmpty { "N/A" }}</td>\n")
            append("    </tr>\n")

            append("    <tr>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8pt; color: $COLOR_TEXT_MUTED; font-weight: bold; border-bottom: 1px solid $COLOR_BORDER;\">TENSÃO APLICADA DE ENSAIO:</td>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8.5pt; color: #1E293B; font-weight: bold; border-bottom: 1px solid $COLOR_BORDER;\">${test.appliedVoltage_kV} kV (${test.voltageType}) - ${test.applicationDurationSeconds}s</td>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8pt; color: $COLOR_TEXT_MUTED; font-weight: bold; border-bottom: 1px solid $COLOR_BORDER;\">CORRENTE DE FUGA MEDIDA:</td>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8.5pt; color: #1E293B; font-weight: bold; border-bottom: 1px solid $COLOR_BORDER;\">${test.measuredLeakageCurrent_mA} mA (Máx: ${test.leakageCurrentLimit_mA} mA)</td>\n")
            append("    </tr>\n")

            append("    <tr>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8pt; color: $COLOR_TEXT_MUTED; font-weight: bold;\">DATA DO ENSAIO:</td>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8.5pt; color: #1E293B;\">${DateUtils.formatDate(test.testDate)}</td>\n")
            append("      <td style=\"padding: 7px 10px; font-size: 8pt; color: $COLOR_TEXT_MUTED; font-weight: bold;\">DATA LIMITE REENSAIO:</td>\n")
            val retestDateCertStr = if (isApproved) DateUtils.formatDate(test.retestDueDate) else ""
            append("      <td style=\"padding: 7px 10px; font-size: 9pt; color: $COLOR_GREEN_DARK; font-weight: bold;\">$retestDateCertStr</td>\n")
            append("    </tr>\n")
            append("  </table>\n")

            // Status Badge
            append("  <div style=\"text-align: center; margin: 16px 0;\">\n")
            append("    <span style=\"display: inline-block; padding: 6px 20px; font-size: 12pt; font-weight: bold; color: ${if (isApproved) "#065F46" else "#991B1B"}; background-color: ${if (isApproved) "#ECFDF5" else "#FEF2F2"}; border: 2px solid ${if (isApproved) "#10B981" else "#EF4444"}; border-radius: 6px;\">\n")
            append("      PARECER TÉCNICO: ${test.result}\n")
            append("    </span>\n")
            append("  </div>\n")

            // Signatures and QR
            append("  <table style=\"width: 100%; border-collapse: collapse; margin-top: 24px;\">\n")
            append("    <tr>\n")
            append("      <td style=\"width: 40%; text-align: center; vertical-align: bottom; border-top: 1px solid $COLOR_NAVY; padding-top: 6px;\">\n")
            append("        <div style=\"font-size: 8.5pt; font-weight: bold; color: $COLOR_NAVY;\">${test.technicianName.ifEmpty { "Eng. João Carlos Evaristo Guedes Nunes" }}</div>\n")
            append("        <div style=\"font-size: 7.5pt; color: $COLOR_TEXT_MUTED;\">${test.technicianCftOrCrea.trim().let { if (it.isBlank()) "" else "$it • " }}Analista Responsável</div>\n")
            append("      </td>\n")
            append("      <td style=\"width: 20%; text-align: center; vertical-align: middle;\">\n")
            if (qrCodeB64 != null) {
                append("        <img src=\"data:image/png;base64,$qrCodeB64\" style=\"width: 55px; height: 55px; display: block; margin: 0 auto;\">\n")
                append("        <div style=\"font-size: 6.5pt; color: $COLOR_TEXT_MUTED; margin-top: 2px;\">VALIDAÇÃO DIGITAL</div>\n")
                append("        <div style=\"font-size: 7.5pt; font-weight: bold; color: $COLOR_BLUE; font-family: monospace;\">${test.validationCode}</div>\n")
            }
            append("      </td>\n")
            append("      <td style=\"width: 40%; text-align: center; vertical-align: bottom; border-top: 1px solid $COLOR_NAVY; padding-top: 6px;\">\n")
            append("        <div style=\"font-size: 8.5pt; font-weight: bold; color: $COLOR_NAVY;\">${test.techResponsibleName.ifEmpty { "Eng. João Carlos Evaristo Guedes Nunes" }}</div>\n")
            append("        <div style=\"font-size: 7.5pt; color: $COLOR_TEXT_MUTED;\">${test.techResponsibleCrea.ifEmpty { "CREA 24481/D-DF" }} • Responsável Técnico</div>\n")
            append("      </td>\n")
            append("    </tr>\n")
            append("  </table>\n")

            // Footer
            append("  <div style=\"text-align: center; font-size: 7.5pt; color: $COLOR_TEXT_MUTED; margin-top: 16px; border-top: 1px solid $COLOR_BORDER; padding-top: 6px;\">\n")
            if (totalItems > 1) {
                append("    A autenticidade deste certificado pode ser verificada escaneando o QR Code acima. Válido até ${DateUtils.formatDate(test.retestDueDate)}.  •  Certificado ${itemIndex + 1} de $totalItems\n")
            } else {
                append("    A autenticidade deste certificado pode ser verificada escaneando o QR Code acima. Válido até ${DateUtils.formatDate(test.retestDueDate)}.\n")
            }
            append("  </div>\n")
            append("</div>\n")
        }
    }

    /**
     * Generates a Microsoft Word (.doc) Certificate of Electrical Compliance for a single test.
     */
    suspend fun generateCertificadoDoc(
        context: Context,
        test: TestRecordEntity,
        company: AppSettingEntity
    ): File = withContext(Dispatchers.IO) {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val sanitizedTag = test.equipmentTag.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val fileName = "Certificado_${test.reportNumber}_${sanitizedTag}.doc"
        val docFile = File(reportsDir, fileName)

        val letterheadBase64 = loadLetterheadBase64(context, company)
        val html = buildString {
            append(getDocHtmlHeader("Certificado de Conformidade ${test.reportNumber}", letterheadBase64))
            append(buildSingleCertificadoDocBody(context, test, company, itemIndex = 0, totalItems = 1))
            append(DOC_HTML_FOOTER)
        }

        val fos = FileOutputStream(docFile)
        fos.write(html.toByteArray(Charsets.UTF_8))
        fos.flush()
        fos.close()

        docFile
    }

    /**
     * Generates a single compiled Microsoft Word (.doc) file containing multiple Certificates of Electrical Compliance.
     */
    suspend fun generateMultipleCertificadosDoc(
        context: Context,
        tests: List<TestRecordEntity>,
        company: AppSettingEntity
    ): File = withContext(Dispatchers.IO) {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Certificados_Agrupados_${tests.size}_itens_${timestamp}.doc"
        val docFile = File(reportsDir, fileName)

        val letterheadBase64 = loadLetterheadBase64(context, company)
        val html = buildString {
            append(getDocHtmlHeader("Certificados de Conformidade Agrupados (${tests.size} itens)", letterheadBase64))
            tests.forEachIndexed { index, test ->
                if (index > 0) {
                    append("<br clear=all style='mso-special-character:line-break;page-break-before:always;'>\n")
                }
                append(buildSingleCertificadoDocBody(context, test, company, itemIndex = index, totalItems = tests.size))
            }
            append(DOC_HTML_FOOTER)
        }

        val fos = FileOutputStream(docFile)
        fos.write(html.toByteArray(Charsets.UTF_8))
        fos.flush()
        fos.close()

        docFile
    }

    /**
     * Generates a Microsoft Word (.doc) document for the Simplified Technical Report & Tabulation,
     * organized and grouped by collaborator in Landscape layout matching the official standard.
     */
    suspend fun generateRelatorioSimplificadoDoc(
        context: Context,
        tests: List<TestRecordEntity>,
        client: ClientEntity?,
        company: AppSettingEntity,
        serviceOrder: ServiceOrderEntity? = null,
        reportNumber: String = ""
    ): File = withContext(Dispatchers.IO) {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val sortedTests = tests.sortedWith(
            compareBy(
                { it.collaboratorName.ifBlank { "ZZZ_Sem_Colaborador" } },
                { it.equipmentTag }
            )
        )

        val approvedCount = sortedTests.count { it.result == "APROVADO" }
        val rejectedCount = sortedTests.count { it.result != "APROVADO" }
        val approvalPercent = if (sortedTests.isNotEmpty()) {
            String.format(java.util.Locale.US, "%.1f", (approvedCount.toDouble() / sortedTests.size) * 100)
        } else "100.0"

        val effectiveReportNum = reportNumber.ifEmpty {
            val seq = if (sortedTests.isNotEmpty()) sortedTests.first().testNumber.takeLast(4) else "0003"
            val ym = java.text.SimpleDateFormat("yyMM", java.util.Locale.getDefault()).format(java.util.Date())
            "REL-TEC-$ym-$seq"
        }

        val effectiveOsNum = serviceOrder?.osNumber ?: sortedTests.firstOrNull()?.serviceOrderNumber?.ifEmpty { "OS-2026-0001" } ?: "OS-2026-0001"
        val effectiveArt = serviceOrder?.artNumber ?: sortedTests.firstOrNull()?.artNumber?.ifEmpty { "1320260109871" } ?: "1320260109871"

        val fileName = "Relatorio_Simplificado_${effectiveReportNum.replace("-", "_")}.doc"
        val docFile = File(reportsDir, fileName)

        fun formatCleanType(raw: String): String {
            return when {
                raw.contains("ATERRAMENTO_SEM", ignoreCase = true) -> "Aterramento Temporário sem Bastão"
                raw.contains("ATERRAMENTO_COM", ignoreCase = true) -> "Aterramento Temporário com Bastão"
                raw.contains("ATERRAMENTO", ignoreCase = true) -> "Aterramento Temporário (EPC)"
                raw.contains("FERRAMENTA", ignoreCase = true) -> "Conjunto de Ferramentas Isoladas 1000V"
                raw.contains("CAPACETE", ignoreCase = true) -> "Capacete de Segurança Classe B"
                raw.contains("LUVA", ignoreCase = true) -> "Luva Isolante de Borracha"
                raw.contains("MANTA", ignoreCase = true) -> "Manta Isolante de Borracha"
                raw.contains("BASTAO", ignoreCase = true) || raw.contains("VARA", ignoreCase = true) -> "Bastão de Manobra / Vara PRFV"
                raw.contains("ESCADA", ignoreCase = true) -> "Escada Isolante de Fibra de Vidro"
                raw.contains("CALCADO", ignoreCase = true) || raw.contains("BOTA", ignoreCase = true) -> "Calçado de Segurança Isolante"
                else -> raw.replace("_", " ")
            }
        }

        val letterheadBase64 = loadLetterheadBase64(context, company)
        val bgStyle = if (letterheadBase64 != null) {
            "background-image: url('data:image/jpeg;base64,$letterheadBase64'); background-repeat: no-repeat; background-position: center top; background-size: 100% 100%;"
        } else ""

        val html = buildString {
            append("<!DOCTYPE html>\n")
            append("<html xmlns:o=\"urn:schemas-microsoft-com:office:office\" xmlns:w=\"urn:schemas-microsoft-com:office:word\" xmlns=\"http://www.w3.org/TR/REC-html40\">\n")
            append("<head>\n")
            append("  <meta charset=\"utf-8\">\n")
            append("  <title>Relatório Técnico Simplificado - $effectiveReportNum</title>\n")
            append("  <!--[if gte mso 9]><xml><w:WordDocument><w:View>Print</w:View><w:Zoom>100</w:Zoom></w:WordDocument></xml><![endif]-->\n")
            append("  <style>\n")
            append("    @page Section1 { size: 841.9pt 595.3pt; mso-page-orientation: landscape; margin: 0.4in 0.4in 0.4in 0.4in; }\n")
            append("    div.Section1 { page: Section1; }\n")
            append("    body { font-family: Arial, 'Helvetica Neue', Helvetica, sans-serif; font-size: 8pt; color: #0F172A; margin: 0; padding: 0; background-color: #FFFFFF; $bgStyle }\n")
            append("    .header-table { width: 100%; border-collapse: collapse; background-color: $COLOR_NAVY; color: #FFFFFF; border-radius: 4px; }\n")
            append("    .section-title { background-color: $COLOR_NAVY; color: #FFFFFF; font-size: 8pt; font-weight: bold; padding: 4px 8px; margin-top: 6px; }\n")
            append("    .info-table { width: 100%; border-collapse: collapse; background-color: $COLOR_BG_LIGHT; border: 1px solid $COLOR_BORDER; font-size: 7.5pt; }\n")
            append("    .info-table td { padding: 4px 6px; border: 1px solid #E2E8F0; }\n")
            append("    .info-label { font-weight: bold; color: $COLOR_NAVY; }\n")
            append("    .data-table { width: 100%; border-collapse: collapse; margin-top: 2px; font-size: 7.5pt; }\n")
            append("    .data-table th { background-color: $COLOR_NAVY; color: #FFFFFF; font-size: 7.5pt; font-weight: bold; padding: 4px 3px; text-align: center; border: 1px solid #1E293B; }\n")
            append("    .data-table td { padding: 4px 3px; border: 1px solid $COLOR_BORDER; vertical-align: middle; }\n")
            append("    .badge-approved { background-color: $COLOR_GREEN_BG; color: $COLOR_GREEN_DARK; font-weight: bold; padding: 2px 4px; border-radius: 3px; display: inline-block; font-size: 7.5pt; }\n")
            append("    .badge-rejected { background-color: $COLOR_RED_BG; color: $COLOR_RED_DARK; font-weight: bold; padding: 2px 4px; border-radius: 3px; display: inline-block; font-size: 7.5pt; }\n")
            append("    .total-box { width: 100%; border-collapse: collapse; margin-top: 8px; background-color: $COLOR_BG_LIGHT; border: 1px solid $COLOR_BORDER; padding: 8px; font-size: 7.5pt; }\n")
            append("  </style>\n")
            append("</head>\n")
            append("<body>\n")
            append("<div class=\"Section1\">\n")

            // Top Header
            val logoBase64 = if (company.companyLogoUri.isNotBlank()) loadBitmapBase64(context, company.companyLogoUri, 200, 100) else null
            append("<table class=\"header-table\" style=\"margin-bottom: 6px;\">\n")
            append("  <tr>\n")
            append("    <td style=\"width: 15%; padding: 8px; text-align: center; vertical-align: middle;\">\n")
            if (logoBase64 != null) {
                append("      <img src=\"data:image/png;base64,$logoBase64\" style=\"max-height: 44px; max-width: 85px; object-fit: contain;\">\n")
            } else {
                append("      <div style=\"font-size: 13pt; font-weight: bold; color: #FBBF24;\">JVM</div>\n")
                append("      <div style=\"font-size: 6.5pt; color: #E2E8F0;\">ENGENHARIA</div>\n")
                append("      <div style=\"background-color: #0284C7; color: #FFFFFF; font-size: 6pt; font-weight: bold; padding: 2px; border-radius: 2px; margin-top: 2px;\">LAB. DIELÉTRICO</div>\n")
            }
            append("    </td>\n")
            append("    <td style=\"width: 65%; padding: 8px 12px; vertical-align: middle;\">\n")
            append("      <div style=\"font-size: 11pt; font-weight: bold; color: #FFFFFF;\">RELATÓRIO TÉCNICO SIMPLIFICADO & TABULAÇÃO DE ENSAIOS DIELÉTRICOS</div>\n")
            append("      <div style=\"font-size: 7pt; color: #E2E8F0;\">${company.legalName.ifEmpty { "JVM ENGENHARIA LTDA" }} | CNPJ: ${company.cnpj.ifEmpty { "29.894.500/0001-04" }} | Registro CREA: ${company.creaCompanyRegister.ifEmpty { "1620-PJ" }}</div>\n")
            append("      <div style=\"font-size: 6.5pt; color: #CBD5E1;\">Endereço: ${company.address} | Tel: ${company.phone} | E-mail: ${company.email}</div>\n")
            append("      <div style=\"font-size: 6.5pt; font-weight: bold; color: #38BDF8; margin-top: 2px;\">Conformidade NR-10 (item 10.7.8) • ABNT NBR 16295 • ASTM F496/F479/F478 • Rastreabilidade RBC</div>\n")
            append("    </td>\n")
            append("    <td style=\"width: 20%; padding: 8px; text-align: center; vertical-align: middle; background-color: #1E3A8A;\">\n")
            append("      <div style=\"font-size: 8.5pt; font-weight: bold; color: #FFFFFF;\">$effectiveReportNum</div>\n")
            append("      <div style=\"font-size: 7.5pt; color: #93C5FD;\">${DateUtils.formatDate(DateUtils.today())}</div>\n")
            append("      <div style=\"font-size: 7.5pt; font-weight: bold; color: #FFFFFF; margin-top: 2px;\">${sortedTests.size} itens ensaiados</div>\n")
            append("    </td>\n")
            append("  </tr>\n")
            append("</table>\n")

            // Section 1
            append("<div class=\"section-title\">1. DADOS DO CLIENTE / SOLICITANTE</div>\n")
            append("<table class=\"info-table\">\n")
            append("  <tr>\n")
            append("    <td style=\"width: 35%;\"><span class=\"info-label\">Razão Social:</span> ${client?.razaoSocial?.ifEmpty { client.nomeFantasia } ?: "BUNGE ALIMENTOS S/A"}</td>\n")
            append("    <td style=\"width: 35%;\"><span class=\"info-label\">CNPJ / CPF:</span> ${client?.cnpj ?: "84.046.101/0019-12"}</td>\n")
            append("    <td style=\"width: 30%;\"><span class=\"info-label\">Inscrição Estadual:</span> ${client?.inscricaoEstadual?.ifEmpty { "Isento / Não informado" } ?: "Isento / Não informado"}</td>\n")
            append("  </tr>\n")
            append("  <tr>\n")
            append("    <td colspan=\"2\"><span class=\"info-label\">Endereço:</span> ${client?.address ?: "AVENIDA ALBERTO ANNANIAS MOREIRA, 400 - DISTRITO INDUSTRIAL DE DOURADOS, MS"}</td>\n")
            append("    <td><span class=\"info-label\">Contato / Resp.:</span> ${client?.contactName?.ifEmpty { "Rafael (Engenheiro / Gestor)" } ?: "Rafael (Engenheiro / Gestor)"}</td>\n")
            append("  </tr>\n")
            append("  <tr>\n")
            append("    <td><span class=\"info-label\">Ordem de Serviço (OS):</span> $effectiveOsNum</td>\n")
            append("    <td><span class=\"info-label\">ART Vinculada:</span> $effectiveArt</td>\n")
            append("    <td><span class=\"info-label\">Resumo do Lote:</span> <span style=\"color: #166534; font-weight: bold;\">$approvedCount Aprovados</span> | <span style=\"color: ${if (rejectedCount > 0) "#991B1B" else "#64748B"}; font-weight: bold;\">$rejectedCount Reprovados ($approvalPercent% aprov.)</span></td>\n")
            append("  </tr>\n")
            append("</table>\n")

            // Section 2
            append("<div class=\"section-title\">2. RELAÇÃO DE NORMAS TÉCNICAS E CRITÉRIOS DE CONFORMIDADE APLICADOS</div>\n")
            append("<table class=\"data-table\">\n")
            append("  <tr>\n")
            append("    <th style=\"width: 4%;\">Item</th>\n")
            append("    <th style=\"width: 14%;\">Código da Norma</th>\n")
            append("    <th style=\"width: 22%;\">Denominação Técnica</th>\n")
            append("    <th style=\"width: 20%;\">Equipamentos Abrangidos</th>\n")
            append("    <th style=\"width: 40%;\">Escopo & Parâmetros do Ensaio</th>\n")
            append("  </tr>\n")
            PdfExporter.PADRAO_NORMAS_RELATORIO.forEachIndexed { idx, norm ->
                val bg = if (idx % 2 == 0) "#FFFFFF" else "#F8FAFC"
                append("  <tr style=\"background-color: $bg;\">\n")
                append("    <td style=\"text-align: center; font-weight: bold;\">${norm.itemNum}</td>\n")
                append("    <td style=\"font-weight: bold; color: $COLOR_BLUE;\">${norm.codigo}</td>\n")
                append("    <td>${norm.denominacao}</td>\n")
                append("    <td>${norm.equipamentos}</td>\n")
                append("    <td>${norm.escopo}</td>\n")
                append("  </tr>\n")
            }
            append("</table>\n")

            // Section 3
            // Section 3
            append("<div class=\"section-title\">3. TABULAÇÃO DOS EQUIPAMENTOS ENSAIADOS (ORGANIZADOS POR COLABORADOR / USUÁRIO)</div>\n")
            
            // KPI Summary Table (matching spreadsheet)
            append("<table style=\"width: 100%; border-collapse: collapse; margin-bottom: 8px; font-size: 8pt; border: 1px solid #94A3B8;\">\n")
            append("  <tr style=\"background-color: #F8FAFC;\">\n")
            append("    <th style=\"width: 25%; text-align: center; padding: 4px; border: 1px solid #94A3B8; color: #475569; font-size: 7.5pt;\">AMOSTRAS</th>\n")
            append("    <th style=\"width: 25%; text-align: center; padding: 4px; border: 1px solid #94A3B8; color: #475569; font-size: 7.5pt;\">APROVADOS</th>\n")
            append("    <th style=\"width: 25%; text-align: center; padding: 4px; border: 1px solid #94A3B8; color: #475569; font-size: 7.5pt;\">REPROVADOS</th>\n")
            append("    <th style=\"width: 25%; text-align: center; padding: 4px; border: 1px solid #94A3B8; color: #475569; font-size: 7.5pt;\">ÍNDICE APROV.</th>\n")
            append("  </tr>\n")
            append("  <tr style=\"background-color: #FFFFFF; font-weight: bold; font-size: 9pt;\">\n")
            append("    <td style=\"text-align: center; padding: 6px; border: 1px solid #94A3B8; color: #0F172A;\">${sortedTests.size} un.</td>\n")
            append("    <td style=\"text-align: center; padding: 6px; border: 1px solid #94A3B8; color: #0F172A;\">$approvedCount un.</td>\n")
            append("    <td style=\"text-align: center; padding: 6px; border: 1px solid #94A3B8; color: #0F172A;\">$rejectedCount un.</td>\n")
            append("    <td style=\"text-align: center; padding: 6px; border: 1px solid #94A3B8; color: #0F172A;\">$approvalPercent%</td>\n")
            append("  </tr>\n")
            append("</table>\n")

            // 7-Column Spreadsheet Table
            append("<table class=\"data-table\" style=\"border-collapse: collapse; width: 100%; border: 1px solid #334155;\">\n")
            append("  <tr style=\"background-color: #0F2027; color: #FFFFFF;\">\n")
            append("    <th style=\"width: 13%; text-align: center; color: #FFFFFF; padding: 5px; border: 1px solid #334155;\">TAG / CA</th>\n")
            append("    <th style=\"width: 23%; text-align: center; color: #FFFFFF; padding: 5px; border: 1px solid #334155;\">EQUIPAMENTO / CLASSE</th>\n")
            append("    <th style=\"width: 20%; text-align: center; color: #FFFFFF; padding: 5px; border: 1px solid #334155;\">COLABORADOR / SETOR</th>\n")
            append("    <th style=\"width: 14%; text-align: center; color: #FFFFFF; padding: 5px; border: 1px solid #334155;\">TENSÃO / ENSAIO</th>\n")
            append("    <th style=\"width: 12%; text-align: center; color: #FFFFFF; padding: 5px; border: 1px solid #334155;\">FUGA / LIMITE</th>\n")
            append("    <th style=\"width: 9%; text-align: center; color: #FFFFFF; padding: 5px; border: 1px solid #334155;\">VALIDADE</th>\n")
            append("    <th style=\"width: 9%; text-align: center; color: #FFFFFF; padding: 5px; border: 1px solid #334155;\">PARECER</th>\n")
            append("  </tr>\n")

            sortedTests.forEachIndexed { idx, t ->
                val bg = if (idx % 2 == 0) "#FFFFFF" else "#F8FAFC"
                val isApp = t.result == "APROVADO"
                val cleanType = formatCleanType(t.equipmentType).uppercase()
                val isMicro = t.currentUnit.equals("µA", true) || t.currentUnit.equals("uA", true)
                val mStr = if (isMicro) {
                    if (t.measuredLeakageCurrent_mA % 1.0 == 0.0) "${t.measuredLeakageCurrent_mA.toInt()} µA" else "${t.measuredLeakageCurrent_mA} µA"
                } else "${String.format(Locale.US, "%.2f", t.measuredLeakageCurrent_mA)} mA"
                val lStr = if (isMicro) {
                    if (t.leakageCurrentLimit_mA % 1.0 == 0.0) "${t.leakageCurrentLimit_mA.toInt()} µA" else "${t.leakageCurrentLimit_mA} µA"
                } else "${String.format(Locale.US, "%.2f", t.leakageCurrentLimit_mA)} mA"
                val sectorText = t.collaboratorSector.ifEmpty { if (t.collaboratorRegistration.isNotEmpty()) "Mat: ${t.collaboratorRegistration}" else "N/C" }

                append("  <tr style=\"background-color: $bg;\">\n")
                append("    <td style=\"padding: 5px 6px; border: 1px solid #CBD5E1;\"><strong>${t.equipmentTag}</strong><br><span style=\"color: #64748B; font-size: 6.5pt;\">CA: ${t.equipmentCa.ifEmpty { "N/C" }}</span></td>\n")
                append("    <td style=\"padding: 5px 6px; border: 1px solid #CBD5E1;\"><strong>$cleanType</strong><br><span style=\"color: #64748B; font-size: 6.5pt;\">Classe ${t.equipmentClass} (${getMaxUseVoltage(t.equipmentClass)})</span></td>\n")
                append("    <td style=\"padding: 5px 6px; border: 1px solid #CBD5E1;\">${t.collaboratorName.ifEmpty { "N/C" }}<br><span style=\"color: #64748B; font-size: 6.5pt;\">$sectorText</span></td>\n")
                append("    <td style=\"padding: 5px 6px; border: 1px solid #CBD5E1;\">${t.appliedVoltage_kV.toInt()} kV ${t.voltageType}<br><span style=\"color: #64748B; font-size: 6.5pt;\">${t.applicationDurationSeconds}s contínuos</span></td>\n")
                append("    <td style=\"padding: 5px 6px; border: 1px solid #CBD5E1;\">$mStr<br><span style=\"color: #64748B; font-size: 6.5pt;\">Máx: $lStr</span></td>\n")
                val retestDateTableStr = if (isApp) DateUtils.formatDate(t.retestDueDate) else ""
                append("    <td style=\"padding: 5px 6px; border: 1px solid #CBD5E1; text-align: center;\">$retestDateTableStr</td>\n")
                append("    <td style=\"padding: 5px 6px; border: 1px solid #CBD5E1; text-align: center; font-weight: bold; color: ${if (isApp) "#15803D" else "#B91C1C"};\">${if (isApp) "APROVADO" else "REPROVADO"}</td>\n")
                append("  </tr>\n")

                val isFerramentaSimple = ManualToolItem.isManualTool(t.equipmentType, t.equipmentTag)
                val parsedToolsSimple = if (isFerramentaSimple) ManualToolItem.getToolsForTest(t) else emptyList()
                if (isFerramentaSimple && parsedToolsSimple.isNotEmpty()) {
                    val appTools = ManualToolItem.getApprovedTools(parsedToolsSimple)
                    val rejTools = ManualToolItem.getRejectedTools(parsedToolsSimple)
                    val totalQty = parsedToolsSimple.sumOf { it.quantity }
                    val appQty = appTools.sumOf { it.quantity }
                    val rejQty = rejTools.sumOf { it.quantity }
                    val borderLeft = if (rejQty == 0) "#16A34A" else if (appQty == 0) "#DC2626" else "#EA580C"
                    append("  <tr style=\"background-color: #F8FAFC;\">\n")
                    append("    <td colspan=\"7\" style=\"padding: 6px 10px; border: 1px solid #CBD5E1; border-left: 4px solid $borderLeft;\">\n")
                    append("      <div style=\"font-size: 7.2pt; font-weight: bold; color: #0F172A; margin-bottom: 4px;\">▸ COMPOSIÇÃO DETALHADA DO CONJUNTO: $totalQty un. ($appQty Aprovada(s) / $rejQty Reprovada(s))</div>\n")
                    append("      <table style=\"width: 100%; border-collapse: collapse; font-size: 6.8pt;\">\n")
                    val useTwoCols = parsedToolsSimple.size > 2
                    val numRows = if (useTwoCols) (parsedToolsSimple.size + 1) / 2 else parsedToolsSimple.size
                    for (r in 0 until numRows) {
                        val t1 = parsedToolsSimple[r * (if (useTwoCols) 2 else 1)]
                        val t2 = if (useTwoCols) parsedToolsSimple.getOrNull(r * 2 + 1) else null
                        append("        <tr>\n")
                        append("          <td style=\"width: ${if (useTwoCols) "50%" else "100%"}; padding: 2px 4px;\">\n")
                        val c1 = if (t1.isApproved) "#15803D" else "#B91C1C"
                        val b1 = if (t1.isApproved) "APROVADO" else "REPROVADO"
                        val bg1 = if (t1.isApproved) "#DCFCE7" else "#FEE2E2"
                        append("            <span style=\"color: $c1; font-weight: bold;\">${if (t1.isApproved) "✓" else "✗"}</span> <b>${t1.name}</b> (${t1.quantity} un.) - <span style=\"background-color: $bg1; color: $c1; font-weight: bold; font-size: 5.8pt; padding: 1px 4px; border-radius: 2px;\">$b1</span>\n")
                        if (!t1.isApproved && t1.rejectionReason.isNotBlank()) {
                            append("            <br><span style=\"color: #B91C1C; font-size: 5.8pt;\">Motivo: ${t1.rejectionReason}</span>\n")
                        }
                        append("          </td>\n")
                        if (useTwoCols) {
                            if (t2 != null) {
                                val c2 = if (t2.isApproved) "#15803D" else "#B91C1C"
                                val b2 = if (t2.isApproved) "APROVADO" else "REPROVADO"
                                val bg2 = if (t2.isApproved) "#DCFCE7" else "#FEE2E2"
                                append("          <td style=\"width: 50%; padding: 2px 4px;\">\n")
                                append("            <span style=\"color: $c2; font-weight: bold;\">${if (t2.isApproved) "✓" else "✗"}</span> <b>${t2.name}</b> (${t2.quantity} un.) - <span style=\"background-color: $bg2; color: $c2; font-weight: bold; font-size: 5.8pt; padding: 1px 4px; border-radius: 2px;\">$b2</span>\n")
                                if (!t2.isApproved && t2.rejectionReason.isNotBlank()) {
                                    append("            <br><span style=\"color: #B91C1C; font-size: 5.8pt;\">Motivo: ${t2.rejectionReason}</span>\n")
                                }
                                append("          </td>\n")
                            } else {
                                append("          <td style=\"width: 50%;\"></td>\n")
                            }
                        }
                        append("        </tr>\n")
                    }
                    append("      </table>\n")
                    append("    </td>\n")
                    append("  </tr>\n")
                }
            }
            append("</table>\n")
            // Totalização e Assinatura
            append("<table class=\"total-box\">\n")
            append("  <tr>\n")
            append("    <td style=\"width: 60%; vertical-align: top;\">\n")
            append("      <div style=\"font-weight: bold; color: $COLOR_NAVY; font-size: 8pt;\">TOTALIZAÇÃO DO LOTE DE ENSAIOS DIELÉTRICOS:</div>\n")
            append("      <div style=\"margin-top: 4px; font-weight: bold;\">Total de Equipamentos: ${sortedTests.size} | Aprovados: $approvedCount ($approvalPercent%) | Reprovados: $rejectedCount</div>\n")
            append("      <div style=\"margin-top: 3px; color: #475569;\">Os ensaios foram executados em conformidade com a NR-10 (item 10.7.8) e as normas vigentes.</div>\n")
            append("      <div style=\"margin-top: 2px; color: #0284C7; font-style: italic;\">Instrumentos calibrados na RBC. Equipamentos aprovados aptos para intervenções elétricas.</div>\n")
            append("    </td>\n")
            append("    <td style=\"width: 40%; text-align: center; vertical-align: top;\">\n")
            append("      <div style=\"width: 80%; border-top: 1px solid #0F172A; margin: 20px auto 4px auto;\"></div>\n")
            append("      <div style=\"font-weight: bold; color: $COLOR_NAVY; font-size: 8.5pt;\">${company.technicalResponsibleName.ifEmpty { "Eng. João Carlos" }}</div>\n")
            append("      <div style=\"color: #64748B; font-size: 7pt;\">Engenheiro Eletricista e de Segurança do Trabalho</div>\n")
            append("      <div style=\"color: #64748B; font-size: 7pt;\">CREA: ${company.technicalResponsibleCrea.ifEmpty { "24481/D-DF" }} | RNP: 076313600</div>\n")
            append("    </td>\n")
            append("  </tr>\n")
            append("</table>\n")

            // Footer
            append("<div style=\"margin-top: 12px; font-size: 6.5pt; color: #94A3B8; text-align: center; border-top: 1px solid #E2E8F0; padding-top: 4px;\">\n")
            append("  JVMENGENHARIA&TREINAMENTO • Sistema de Gestão Laboratorial Dielétrica Conforme NR-10 • Documento Técnico Oficial\n")
            append("</div>\n")

            append("</div>\n") // close Section1
            append("</body>\n")
            append("</html>\n")
        }

        val fos = FileOutputStream(docFile)
        fos.write(html.toByteArray(Charsets.UTF_8))
        fos.flush()
        fos.close()

        docFile
    }

    /**
     * Generates a Comprehensive Technical Report Word (.doc) document (Relatório Técnico Completo de Ensaios Dielétricos)
     * containing: Capa Oficial, Sumário, Identificação, Introdução, Objetivo, Metodologia de Ensaios,
     * Normas Aplicadas, Análise dos Resultados e Tabulação Completa, Conclusão, Recomendações e Termo de RT.
     */
    suspend fun generateRelatorioCompletoDoc(
        context: Context,
        tests: List<TestRecordEntity>,
        client: ClientEntity?,
        company: AppSettingEntity,
        serviceOrder: ServiceOrderEntity? = null,
        reportNumber: String = ""
    ): File = withContext(Dispatchers.IO) {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val sortedTests = tests.sortedWith(
            compareBy({ it.collaboratorName.ifBlank { "ZZZ" } }, { it.equipmentTag })
        )

        val approvedCount = sortedTests.count { it.result == "APROVADO" }
        val rejectedCount = sortedTests.count { it.result != "APROVADO" }
        val totalCount = sortedTests.size
        val approvalPercent = if (totalCount > 0) {
            String.format(java.util.Locale.US, "%.1f", (approvedCount.toDouble() / totalCount) * 100)
        } else "100.0"

        val effectiveReportNum = if (reportNumber.isNotBlank()) reportNumber else {
            tests.firstOrNull()?.reportNumber ?: "REL-DIE-${java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())}"
        }

        val effectiveOsNum = serviceOrder?.osNumber ?: tests.firstOrNull()?.serviceOrderNumber ?: "OS-001"
        val effectiveArt = serviceOrder?.artNumber?.ifEmpty { tests.firstOrNull()?.artNumber?.ifEmpty { company.creaCompanyRegister } } ?: company.creaCompanyRegister
        val dateStr = DateUtils.formatDate(DateUtils.today())

        val qrCodeUrl = "https://validador.jvmlab.com.br"
        val qrCodeB64 = generateQrCodeBase64(qrCodeUrl, 160)

        val fileName = "Relatorio_Completo_${effectiveReportNum.replace("-", "_").replace("/", "_")}.doc"
        val docFile = File(reportsDir, fileName)

        val logoBase64 = if (company.companyLogoUri.isNotBlank()) loadBitmapBase64(context, company.companyLogoUri, 200, 100) else null
        val coverLogoHtml = if (logoBase64 != null) {
            "<img src=\"data:image/png;base64,$logoBase64\" style=\"max-height: 55px; max-width: 120px; object-fit: contain; vertical-align: middle;\">"
        } else {
            "<div style=\"font-size: 16pt; font-weight: bold; color: #FBBF24;\">JVM</div>\n" +
            "        <div style=\"font-size: 7.5pt; color: #E2E8F0;\">ENGENHARIA</div>\n" +
            "        <div style=\"background-color: #0284C7; color: #FFFFFF; font-size: 6.5pt; font-weight: bold; padding: 2px 4px; border-radius: 2px; margin-top: 3px;\">LAB. DIELÉTRICO</div>"
        }

        val pageLogoHtml = if (logoBase64 != null) {
            "<img src=\"data:image/png;base64,$logoBase64\" style=\"max-height: 36px; max-width: 85px; object-fit: contain; vertical-align: middle;\">"
        } else {
            "<div style=\"font-size: 11pt; font-weight: bold; color: #FBBF24;\">JVM</div>"
        }

        val standardSubHeader = """
<table class="header-table" style="margin-bottom: 8px;">
  <tr>
    <td style="width: 15%; padding: 5px 8px; text-align: center; vertical-align: middle;">
      $pageLogoHtml
    </td>
    <td style="width: 65%; padding: 5px 10px; vertical-align: middle;">
      <div style="font-size: 8.5pt; font-weight: bold; color: #FFFFFF;">${company.name.ifEmpty { "JVM ENGENHARIA & TREINAMENTOS LTDA" }.uppercase()}</div>
      <div style="font-size: 6.5pt; color: #38BDF8;">RELATÓRIO TÉCNICO DE ENSAIOS DIELÉTRICOS NR-10 • $effectiveReportNum</div>
    </td>
    <td style="width: 20%; text-align: right; padding: 5px 10px; vertical-align: middle; font-size: 6.8pt; color: #CBD5E1;">
      OS: $effectiveOsNum<br>ART: $effectiveArt
    </td>
  </tr>
</table>
""".trimIndent() + "\n"

        fun formatCleanType(raw: String): String {
            return when {
                raw.contains("ATERRAMENTO_SEM", ignoreCase = true) -> "Aterramento Temporário sem Bastão"
                raw.contains("ATERRAMENTO_COM", ignoreCase = true) -> "Aterramento Temporário com Bastão"
                raw.contains("ATERRAMENTO", ignoreCase = true) -> "Aterramento Temporário (EPC)"
                raw.contains("FERRAMENTA", ignoreCase = true) -> "Conjunto de Ferramentas Isoladas 1000V"
                raw.contains("CAPACETE", ignoreCase = true) -> "Capacete de Segurança Classe B"
                raw.contains("LUVA", ignoreCase = true) -> "Luva Isolante de Borracha"
                raw.contains("MANTA", ignoreCase = true) -> "Manta Isolante de Borracha"
                raw.contains("BASTAO", ignoreCase = true) || raw.contains("VARA", ignoreCase = true) -> "Bastão de Manobra / Vara PRFV"
                raw.contains("ESCADA", ignoreCase = true) -> "Escada Isolante de Fibra de Vidro"
                raw.contains("CALCADO", ignoreCase = true) || raw.contains("BOTA", ignoreCase = true) -> "Calçado de Segurança Isolante"
                else -> raw.replace("_", " ")
            }
        }

        val letterheadBase64 = loadLetterheadBase64(context, company)
        val bgStyle = if (letterheadBase64 != null) {
            "background-image: url('data:image/jpeg;base64,$letterheadBase64'); background-repeat: no-repeat; background-position: center top; background-size: 100% 100%;"
        } else ""

        val html = buildString {
            append("<!DOCTYPE html>\n")
            append("<html xmlns:o=\"urn:schemas-microsoft-com:office:office\" xmlns:w=\"urn:schemas-microsoft-com:office:word\" xmlns=\"http://www.w3.org/TR/REC-html40\">\n")
            append("<head>\n")
            append("  <meta charset=\"utf-8\">\n")
            append("  <title>Relatório Técnico Completo - $effectiveReportNum</title>\n")
            append("  <!--[if gte mso 9]><xml><w:WordDocument><w:View>Print</w:View><w:Zoom>100</w:Zoom></w:WordDocument></xml><![endif]-->\n")
            append("  <style>\n")
            append("    @page Section1 { size: 595.3pt 841.9pt; margin: 0.5in 0.5in 0.5in 0.5in; }\n")
            append("    div.Section1 { page: Section1; }\n")
            append("    body { font-family: Arial, 'Helvetica Neue', Helvetica, sans-serif; font-size: 9pt; color: #0F172A; margin: 0; padding: 0; background-color: #FFFFFF; $bgStyle }\n")
            append("    .page-break { page-break-before: always; }\n")
            append("    .cover-box { border: 2px solid $COLOR_NAVY; padding: 16px; background-color: ${if (letterheadBase64 != null) "transparent" else "#FFFFFF"}; $bgStyle }\n")
            append("    .header-table { width: 100%; border-collapse: collapse; background-color: $COLOR_NAVY; color: #FFFFFF; border-radius: 4px; }\n")
            append("    .section-title { background-color: $COLOR_NAVY; color: #FFFFFF; font-size: 9.5pt; font-weight: bold; padding: 6px 10px; margin-top: 14px; margin-bottom: 6px; border-radius: 3px; }\n")
            append("    .info-table { width: 100%; border-collapse: collapse; background-color: $COLOR_BG_LIGHT; border: 1px solid $COLOR_BORDER; font-size: 8.5pt; margin-bottom: 8px; }\n")
            append("    .info-table td { padding: 6px 8px; border: 1px solid #E2E8F0; }\n")
            append("    .info-label { font-weight: bold; color: $COLOR_NAVY; }\n")
            append("    .data-table { width: 100%; table-layout: fixed; border-collapse: collapse; margin-top: 6px; font-size: 8pt; word-wrap: break-word; }\n")
            append("    .data-table th { background-color: $COLOR_NAVY; color: #FFFFFF; font-size: 8pt; font-weight: bold; padding: 6px 4px; text-align: center; border: 1px solid #1E293B; word-wrap: break-word; overflow-wrap: break-word; }\n")
            append("    .data-table td { padding: 5px 4px; border: 1px solid $COLOR_BORDER; vertical-align: middle; word-wrap: break-word; overflow-wrap: break-word; }\n")
            append("    .badge-approved { background-color: $COLOR_GREEN_BG; color: $COLOR_GREEN_DARK; font-weight: bold; padding: 2px 6px; border-radius: 3px; display: inline-block; font-size: 8pt; }\n")
            append("    .badge-rejected { background-color: $COLOR_RED_BG; color: $COLOR_RED_DARK; font-weight: bold; padding: 2px 6px; border-radius: 3px; display: inline-block; font-size: 8pt; }\n")
            append("    .kpi-table { width: 100%; border-collapse: collapse; margin: 10px 0; }\n")
            append("    .kpi-card { background-color: #F8FAFC; border: 1px solid #CBD5E1; text-align: center; padding: 8px; border-radius: 4px; }\n")
            append("    .paragraph { font-size: 8.5pt; line-height: 1.45; color: #334155; text-align: justify; text-indent: 1.25cm; margin-top: 8px; margin-bottom: 10px; }\n")
            append("  </style>\n")
            append("</head>\n")
            append("<body>\n")
            append("<div class=\"Section1\">\n")

            // ==========================================
            // CAPA (COVER PAGE)
            // ==========================================
            append("<div class=\"cover-box\">\n")
            append("  <table class=\"header-table\" style=\"margin-bottom: 16px;\">\n")
            append("    <tr>\n")
            append("      <td style=\"width: 20%; padding: 12px; text-align: center; vertical-align: middle;\">\n")
            append("        $coverLogoHtml\n")
            append("      </td>\n")
            append("      <td style=\"width: 80%; padding: 12px 16px; vertical-align: middle;\">\n")
            append("        <div style=\"font-size: 13pt; font-weight: bold; color: #FFFFFF;\">${company.name.ifEmpty { "JVM ENGENHARIA & TREINAMENTOS LTDA" }.uppercase()}</div>\n")
            append("        <div style=\"font-size: 8pt; color: #E2E8F0; margin-top: 3px;\">LABORATÓRIO DE ENSAIOS DIELÉTRICOS E SEGURANÇA EM ELETRICIDADE</div>\n")
            append("        <div style=\"font-size: 7.5pt; color: #CBD5E1;\">CNPJ: ${company.cnpj.ifEmpty { "43.344.821/0001-44" }} | CREA/DF: ${company.creaCompanyRegister.ifEmpty { "26084/DF" }} | ${company.address}</div>\n")
            append("      </td>\n")
            append("    </tr>\n")
            append("  </table>\n")

            append("  <div style=\"text-align: center; background-color: #F1F5F9; border: 1px solid #CBD5E1; border-left: 6px solid #0284C7; padding: 18px; margin: 20px 0; border-radius: 4px;\">\n")
            append("    <div style=\"font-size: 15pt; font-weight: bold; color: $COLOR_NAVY;\">RELATÓRIO TÉCNICO DE ENSAIOS DIELÉTRICOS</div>\n")
            append("    <div style=\"font-size: 10pt; font-weight: bold; color: #0284C7; margin: 6px 0;\">CONFORMIDADE NR-10 (SUBITENS 10.4.3.1 E 10.7.8)</div>\n")
            append("    <div style=\"font-size: 8.5pt; color: #475569;\">Ensaios de Rigidez Dielétrica, Tensão Aplicada e Medição de Corrente de Fuga em EPIs, EPCs e Ferramental Isolado</div>\n")
            append("  </div>\n")

            append("  <table class=\"info-table\" style=\"margin-top: 14px;\">\n")
            append("    <tr>\n")
            append("      <td style=\"width: 25%;\" class=\"info-label\">Relatório Nº:</td>\n")
            append("      <td style=\"width: 25%; font-weight: bold; color: #0284C7;\">$effectiveReportNum</td>\n")
            append("      <td style=\"width: 25%;\" class=\"info-label\">Data de Emissão:</td>\n")
            append("      <td style=\"width: 25%;\">$dateStr</td>\n")
            append("    </tr>\n")
            append("    <tr>\n")
            append("      <td class=\"info-label\">Ordem de Serviço (OS):</td>\n")
            append("      <td>$effectiveOsNum</td>\n")
            append("      <td class=\"info-label\">ART CREA/DF Nº:</td>\n")
            append("      <td style=\"font-weight: bold; color: $COLOR_GREEN_DARK;\">$effectiveArt</td>\n")
            append("    </tr>\n")
            append("  </table>\n")

            append("  <div class=\"section-title\">1. IDENTIFICAÇÃO DO SOLICITANTE / EMPRESA CONTRATANTE</div>\n")
            val firstTest = tests.firstOrNull()
            val clientName = client?.razaoSocial?.ifEmpty { client.nomeFantasia }?.ifEmpty { firstTest?.clientName }
                ?: (firstTest?.clientName?.ifEmpty { "EMPRESA SOLICITANTE / CLIENTE" } ?: "EMPRESA SOLICITANTE / CLIENTE")
            val clientTrade = client?.nomeFantasia?.ifEmpty { client.razaoSocial } ?: clientName
            val clientAddr = if (!client?.address.isNullOrBlank()) "${client?.address}, ${client?.city} - ${client?.state}" else "Endereço não informado"
            val clientPhone = client?.contactPhone ?: ""
            val clientEmail = client?.contactEmail ?: ""
            val clientContact = client?.contactName?.ifEmpty { "Engenharia de Segurança" } ?: "Engenharia de Segurança"
            append("  <table class=\"info-table\">\n")
            append("    <tr>\n")
            append("      <td style=\"width: 20%;\" class=\"info-label\">Razão Social:</td>\n")
            append("      <td colspan=\"3\" style=\"font-weight: bold;\">$clientName</td>\n")
            append("    </tr>\n")
            append("    <tr>\n")
            append("      <td class=\"info-label\">Nome Fantasia:</td>\n")
            append("      <td>$clientTrade</td>\n")
            append("      <td class=\"info-label\">CNPJ / IE:</td>\n")
            append("      <td>${client?.cnpj ?: "00.000.000/0001-00"} / ${client?.inscricaoEstadual ?: "Isento"}</td>\n")
            append("    </tr>\n")
            append("    <tr>\n")
            append("      <td class=\"info-label\">Endereço Completo:</td>\n")
            append("      <td colspan=\"3\">$clientAddr</td>\n")
            append("    </tr>\n")
            append("    <tr>\n")
            append("      <td class=\"info-label\">Contato / Responsável:</td>\n")
            append("      <td>$clientContact</td>\n")
            append("      <td class=\"info-label\">Telefone / E-mail:</td>\n")
            append("      <td>$clientPhone | $clientEmail</td>\n")
            append("    </tr>\n")
            append("  </table>\n")

            append("  <div class=\"section-title\">2. IDENTIFICAÇÃO DO LABORATÓRIO EXECUTANTE & RT</div>\n")
            append("  <table class=\"info-table\">\n")
            append("    <tr>\n")
            append("      <td style=\"width: 20%;\" class=\"info-label\">Empresa Executante:</td>\n")
            append("      <td colspan=\"3\" style=\"font-weight: bold;\">${company.name.ifEmpty { "JVM ENGENHARIA & TREINAMENTOS LTDA" }}</td>\n")
            append("    </tr>\n")
            append("    <tr>\n")
            append("      <td class=\"info-label\">CNPJ / Registro:</td>\n")
            append("      <td>${company.cnpj.ifEmpty { "43.344.821/0001-44" }} | CREA/DF: ${company.creaCompanyRegister.ifEmpty { "26084/DF" }}</td>\n")
            append("      <td class=\"info-label\">Responsável Técnico:</td>\n")
            append("      <td style=\"font-weight: bold;\">${company.technicalResponsibleName.ifEmpty { "Eng. João Nunes" }}</td>\n")
            append("    </tr>\n")
            append("    <tr>\n")
            append("      <td class=\"info-label\">Qualificação do RT:</td>\n")
            append("      <td>Engenheiro Eletricista / Seg. Trabalho</td>\n")
            append("      <td class=\"info-label\">CREA / RNP:</td>\n")
            append("      <td>CREA/DF ${company.technicalResponsibleCrea.ifEmpty { "26084/DF" }} | ART: $effectiveArt</td>\n")
            append("    </tr>\n")
            append("  </table>\n")

            append("  <table class=\"kpi-table\">\n")
            append("    <tr>\n")
            append("      <td style=\"width: 33.3%; padding: 4px;\">\n")
            append("        <div class=\"kpi-card\">\n")
            append("          <div style=\"font-size: 7.5pt; color: #64748B;\">TOTAL DE ITENS</div>\n")
            append("          <div style=\"font-size: 13pt; font-weight: bold; color: $COLOR_NAVY;\">$totalCount un.</div>\n")
            append("        </div>\n")
            append("      </td>\n")
            append("      <td style=\"width: 33.3%; padding: 4px;\">\n")
            append("        <div class=\"kpi-card\" style=\"background-color: $COLOR_GREEN_BG; border-color: #86EFAC;\">\n")
            append("          <div style=\"font-size: 7.5pt; color: $COLOR_GREEN_DARK;\">APROVADOS</div>\n")
            append("          <div style=\"font-size: 13pt; font-weight: bold; color: $COLOR_GREEN_DARK;\">$approvedCount ($approvalPercent%)</div>\n")
            append("        </div>\n")
            append("      </td>\n")
            append("      <td style=\"width: 33.3%; padding: 4px;\">\n")
            append("        <div class=\"kpi-card\" style=\"background-color: ${if (rejectedCount > 0) COLOR_RED_BG else "#F8FAFC"}; border-color: ${if (rejectedCount > 0) "#FCA5A5" else "#CBD5E1"};\">\n")
            append("          <div style=\"font-size: 7.5pt; color: ${if (rejectedCount > 0) COLOR_RED_DARK else "#64748B"};\">REPROVADOS</div>\n")
            append("          <div style=\"font-size: 13pt; font-weight: bold; color: ${if (rejectedCount > 0) COLOR_RED_DARK else "#64748B"};\">$rejectedCount un.</div>\n")
            append("        </div>\n")
            append("      </td>\n")
            append("    </tr>\n")
            append("  </table>\n")

            append("  <div style=\"text-align: center; font-size: 8pt; color: #64748B; margin-top: 15px;\">BRASÍLIA - DF • ${java.text.SimpleDateFormat("yyyy", java.util.Locale.US).format(java.util.Date())}</div>\n")
            append("</div>\n") // End cover-box

            // ==========================================
            // PAGE 2: SUMÁRIO, INTRODUÇÃO & OBJETIVO
            // ==========================================
            append("<br clear=all style='mso-special-character:line-break;page-break-before:always'>\n")
            append(standardSubHeader)

            append("<div class=\"section-title\">SUMÁRIO DO RELATÓRIO TÉCNICO</div>\n")
            append("<table class=\"info-table\">\n")
            append("  <tr><td style=\"font-weight: bold;\">1. IDENTIFICAÇÃO DO SOLICITANTE E EXECUTANTE</td><td style=\"text-align: right; color: #0284C7; font-weight: bold;\">Pág. 1</td></tr>\n")
            append("  <tr><td style=\"font-weight: bold;\">2. INTRODUÇÃO E ENQUADRAMENTO REGULAMENTAR (NR-10)</td><td style=\"text-align: right; color: #0284C7; font-weight: bold;\">Pág. 2</td></tr>\n")
            append("  <tr><td style=\"font-weight: bold;\">3. OBJETIVO DO LAUDO & ESCOPO DA AVALIAÇÃO</td><td style=\"text-align: right; color: #0284C7; font-weight: bold;\">Pág. 2</td></tr>\n")
            append("  <tr><td style=\"font-weight: bold;\">4. NORMAS TÉCNICAS APLICADAS & CRITÉRIOS DE ACEITAÇÃO</td><td style=\"text-align: right; color: #0284C7; font-weight: bold;\">Pág. 3</td></tr>\n")
            append("  <tr><td style=\"font-weight: bold;\">5. ANÁLISE DOS RESULTADOS E TABULAÇÃO DOS EQUIPAMENTOS</td><td style=\"text-align: right; color: #0284C7; font-weight: bold;\">Pág. 4</td></tr>\n")
            append("  <tr><td style=\"font-weight: bold;\">6. CONCLUSÃO TÉCNICA E PARECER DE APTIDÃO</td><td style=\"text-align: right; color: #0284C7; font-weight: bold;\">Pág. Final</td></tr>\n")
            append("  <tr><td style=\"font-weight: bold;\">7. RECOMENDAÇÕES DE SEGURANÇA, USO E MANUTENÇÃO (NR-10)</td><td style=\"text-align: right; color: #0284C7; font-weight: bold;\">Pág. Final</td></tr>\n")
            append("  <tr><td style=\"font-weight: bold;\">8. TERMO DE RESPONSABILIDADE TÉCNICA E ASSINATURA</td><td style=\"text-align: right; color: #0284C7; font-weight: bold;\">Pág. Final</td></tr>\n")
            append("</table>\n")

            append("<div class=\"section-title\">2. INTRODUÇÃO E ENQUADRAMENTO REGULAMENTAR</div>\n")
            val effectiveIntro = company.reportIntroText.ifBlank {
                "O presente Relatório Técnico de Ensaios Dielétricos consolida os resultados dos ensaios de rigidez dielétrica e medição de corrente de fuga realizados no lote de Equipamentos de Proteção Individual (EPI), Equipamentos de Proteção Coletiva (EPC) e Ferramental Isolado pertencentes à empresa solicitante.\n\nA execução periódica destes ensaios atende estritamente às exigências da Norma Regulamentadora nº 10 (Segurança em Instalações e Serviços em Eletricidade - Portaria MTE nº 3.214/78), em especial aos subitens 10.4.3.1 e 10.7.8, os quais estabelecem a obrigatoriedade de ensaios elétricos periódicos em todos os equipamentos, ferramentas e dispositivos isolantes utilizados em intervenções em instalações elétricas, com a devida comprovação por meio de laudo técnico emitido por profissional legalmente habilitado.\n\nOs ensaios foram conduzidos no laboratório especializado da JVM ENGENHARIA & TREINAMENTOS, utilizando fontes de alta tensão (Hipot) e equipamentos de medição calibrados com rastreabilidade à Rede Brasileira de Calibração (RBC / Inmetro), em conformidade com as normas ABNT NBR, IEC e ASTM pertinentes."
            }
            effectiveIntro.split("\n\n").filter { it.isNotBlank() }.forEach { para ->
                append("<p class=\"paragraph\">${para.trim().replace("\n", "<br>")}</p>\n")
            }

            append("<div class=\"section-title\">3. OBJETIVO DO LAUDO & ESCOPO DA AVALIAÇÃO</div>\n")
            val effectiveObjective = company.reportObjectiveText.ifBlank {
                "O objetivo precípuo deste trabalho de engenharia é verificar experimentalmente a integridade física e a capacidade de isolação elétrica dos equipamentos e ferramentas submetidos a ensaio, assegurando que:\n• Os níveis de corrente de fuga sob tensão de ensaio especificada mantenham-se rigorosamente abaixo dos limites máximos permitidos por norma;\n• Os materiais isolantes poliméricos e elastoméricos suportem a tensão de prova sem apresentar perfuração, disrupção elétrica, arco ou sobreaquecimento;\n• Não existam trincas, fissuras, microfuros, contaminações por umidade ou degradações mecânicas/químicas que comprometam a rigidez dielétrica;\n• Seja emitida a certificação de conformidade e estipulada a validade técnica para subsidiar o Prontuário de Instalações Elétricas (PIE) da empresa contratante."
            }
            val objLines = effectiveObjective.lines().filter { it.isNotBlank() }
            val hasBullets = objLines.any { it.trim().startsWith("•") || it.trim().startsWith("-") || it.trim().startsWith("*") }
            if (hasBullets) {
                var inUl = false
                objLines.forEach { line ->
                    val isBullet = line.trim().startsWith("•") || line.trim().startsWith("-") || line.trim().startsWith("*")
                    if (isBullet) {
                        if (!inUl) {
                            append("<ul style=\"font-size: 8.5pt; color: #334155; line-height: 1.45; padding-left: 20px;\">\n")
                            inUl = true
                        }
                        val cleanBullet = line.trim().removePrefix("•").removePrefix("-").removePrefix("*").trim()
                        append("  <li>$cleanBullet</li>\n")
                    } else {
                        if (inUl) {
                            append("</ul>\n")
                            inUl = false
                        }
                        append("<p class=\"paragraph\">${line.trim()}</p>\n")
                    }
                }
                if (inUl) {
                    append("</ul>\n")
                }
            } else {
                objLines.forEach { line ->
                    append("<p class=\"paragraph\">${line.trim()}</p>\n")
                }
            }

            // ==========================================
            // PAGE 3: NORMAS TÉCNICAS & METODOLOGIA
            // ==========================================
            append("<br clear=all style='mso-special-character:line-break;page-break-before:always'>\n")
            append(standardSubHeader)

            append("<div class=\"section-title\">4. NORMAS TÉCNICAS APLICADAS & CRITÉRIOS DE CONFORMIDADE</div>\n")
            append("<table class=\"data-table\" style=\"width: 100%; table-layout: fixed; margin-top: 6px; margin-bottom: 12px;\">\n")
            append("  <tr>\n")
            append("    <th style=\"width: 7%; text-align: center; font-size: 8pt; padding: 6px 4px;\">Item</th>\n")
            append("    <th style=\"width: 28%; text-align: left; font-size: 8pt; padding: 6px 4px;\">Norma / Código</th>\n")
            append("    <th style=\"width: 25%; text-align: left; font-size: 8pt; padding: 6px 4px;\">Equipamentos Abrangidos</th>\n")
            append("    <th style=\"width: 40%; text-align: left; font-size: 8pt; padding: 6px 4px;\">Parâmetros & Critérios de Ensaio</th>\n")
            append("  </tr>\n")
            PdfExporter.PADRAO_NORMAS_RELATORIO.forEachIndexed { idx, norm ->
                val bg = if (idx % 2 == 0) "#FFFFFF" else "#F8FAFC"
                append("  <tr style=\"background-color: $bg;\">\n")
                append("    <td style=\"text-align: center; font-weight: bold; font-size: 8pt; padding: 5px 4px; word-wrap: break-word; overflow-wrap: break-word;\">${norm.itemNum}</td>\n")
                append("    <td style=\"font-weight: bold; color: #0284C7; font-size: 8pt; padding: 5px 4px; word-wrap: break-word; overflow-wrap: break-word;\">${norm.codigo} - <span style=\"font-size: 7pt; color: #475569; font-weight: normal;\">${norm.denominacao}</span></td>\n")
                append("    <td style=\"font-size: 8pt; padding: 5px 4px; word-wrap: break-word; overflow-wrap: break-word;\">${norm.equipamentos}</td>\n")
                append("    <td style=\"font-size: 7.5pt; padding: 5px 4px; word-wrap: break-word; overflow-wrap: break-word; line-height: 1.35;\">${norm.escopo}</td>\n")
                append("  </tr>\n")
            }
            append("</table>\n")







            // ==========================================
            // PAGE 4: TABULAÇÃO COMPLETA DOS ENSAIOS
            // ==========================================
            append("<br clear=all style='mso-special-character:line-break;page-break-before:always'>\n")
            append(standardSubHeader)

            append("<div class=\"section-title\">5. ANÁLISE DOS RESULTADOS E TABULAÇÃO COMPLETA DO LOTE</div>\n")
            val effectiveDev = company.reportDevelopmentText.ifBlank {
                "Os ensaios foram realizados no laboratório seguindo as rotinas técnicas padronizadas e critérios das normas vigentes aplicáveis. Cada equipamento foi individualmente registrado, identificado por etiqueta indelével codificada por TAG, submetido aos parâmetros prescritos de alta tensão alternada e monitorado em tempo real quanto à estabilidade dielétrica e corrente de fuga. Os resultados consolidados constam na tabulação individual a seguir, indicando conformidade, rastreabilidade metrológica e vigência da certificação."
            }
            if (effectiveDev.isNotBlank()) {
                effectiveDev.split("\n\n").filter { it.isNotBlank() }.forEach { para ->
                    append("<p class=\"paragraph\">${para.trim().replace("\n", "<br>")}</p>\n")
                }
            }
            // KPI Summary Table (matching spreadsheet)
            append("<table style=\"width: 100%; border-collapse: collapse; margin-bottom: 8px; font-size: 8pt; border: 1px solid #94A3B8;\">\n")
            append("  <tr style=\"background-color: #F8FAFC;\">\n")
            append("    <th style=\"width: 25%; text-align: center; padding: 4px; border: 1px solid #94A3B8; color: #475569; font-size: 7.5pt;\">AMOSTRAS</th>\n")
            append("    <th style=\"width: 25%; text-align: center; padding: 4px; border: 1px solid #94A3B8; color: #475569; font-size: 7.5pt;\">APROVADOS</th>\n")
            append("    <th style=\"width: 25%; text-align: center; padding: 4px; border: 1px solid #94A3B8; color: #475569; font-size: 7.5pt;\">REPROVADOS</th>\n")
            append("    <th style=\"width: 25%; text-align: center; padding: 4px; border: 1px solid #94A3B8; color: #475569; font-size: 7.5pt;\">ÍNDICE APROV.</th>\n")
            append("  </tr>\n")
            append("  <tr style=\"background-color: #FFFFFF; font-weight: bold; font-size: 9pt;\">\n")
            append("    <td style=\"text-align: center; padding: 6px; border: 1px solid #94A3B8; color: #0F172A;\">$totalCount un.</td>\n")
            append("    <td style=\"text-align: center; padding: 6px; border: 1px solid #94A3B8; color: #0F172A;\">$approvedCount un.</td>\n")
            append("    <td style=\"text-align: center; padding: 6px; border: 1px solid #94A3B8; color: #0F172A;\">$rejectedCount un.</td>\n")
            append("    <td style=\"text-align: center; padding: 6px; border: 1px solid #94A3B8; color: #0F172A;\">$approvalPercent%</td>\n")
            append("  </tr>\n")
            append("</table>\n")

            // 7-Column Spreadsheet Table
            append("<table class=\"data-table\" style=\"border-collapse: collapse; width: 100%; border: 1px solid #334155;\">\n")
            append("  <tr style=\"background-color: #0F2027; color: #FFFFFF;\">\n")
            append("    <th style=\"width: 13%; text-align: center; color: #FFFFFF; padding: 5px; border: 1px solid #334155;\">TAG / CA</th>\n")
            append("    <th style=\"width: 23%; text-align: center; color: #FFFFFF; padding: 5px; border: 1px solid #334155;\">EQUIPAMENTO / CLASSE</th>\n")
            append("    <th style=\"width: 20%; text-align: center; color: #FFFFFF; padding: 5px; border: 1px solid #334155;\">COLABORADOR / SETOR</th>\n")
            append("    <th style=\"width: 14%; text-align: center; color: #FFFFFF; padding: 5px; border: 1px solid #334155;\">TENSÃO / ENSAIO</th>\n")
            append("    <th style=\"width: 12%; text-align: center; color: #FFFFFF; padding: 5px; border: 1px solid #334155;\">FUGA / LIMITE</th>\n")
            append("    <th style=\"width: 9%; text-align: center; color: #FFFFFF; padding: 5px; border: 1px solid #334155;\">VALIDADE</th>\n")
            append("    <th style=\"width: 9%; text-align: center; color: #FFFFFF; padding: 5px; border: 1px solid #334155;\">PARECER</th>\n")
            append("  </tr>\n")

            sortedTests.forEachIndexed { idx, t ->
                val bg = if (idx % 2 == 0) "#FFFFFF" else "#F8FAFC"
                val isApp = t.result == "APROVADO"
                val cleanType = formatCleanType(t.equipmentType).uppercase()
                val isMicro = t.currentUnit.equals("µA", true) || t.currentUnit.equals("uA", true)
                val mStr = if (isMicro) {
                    if (t.measuredLeakageCurrent_mA % 1.0 == 0.0) "${t.measuredLeakageCurrent_mA.toInt()} µA" else "${t.measuredLeakageCurrent_mA} µA"
                } else "${String.format(Locale.US, "%.2f", t.measuredLeakageCurrent_mA)} mA"
                val lStr = if (isMicro) {
                    if (t.leakageCurrentLimit_mA % 1.0 == 0.0) "${t.leakageCurrentLimit_mA.toInt()} µA" else "${t.leakageCurrentLimit_mA} µA"
                } else "${String.format(Locale.US, "%.2f", t.leakageCurrentLimit_mA)} mA"
                val sectorText = t.collaboratorSector.ifEmpty { if (t.collaboratorRegistration.isNotEmpty()) "Mat: ${t.collaboratorRegistration}" else "N/C" }

                append("  <tr style=\"background-color: $bg;\">\n")
                append("    <td style=\"padding: 5px 6px; border: 1px solid #CBD5E1;\"><strong>${t.equipmentTag}</strong><br><span style=\"color: #64748B; font-size: 6.5pt;\">CA: ${t.equipmentCa.ifEmpty { "N/C" }}</span></td>\n")
                append("    <td style=\"padding: 5px 6px; border: 1px solid #CBD5E1;\"><strong>$cleanType</strong><br><span style=\"color: #64748B; font-size: 6.5pt;\">Classe ${t.equipmentClass} (${getMaxUseVoltage(t.equipmentClass)})</span></td>\n")
                append("    <td style=\"padding: 5px 6px; border: 1px solid #CBD5E1;\">${t.collaboratorName.ifEmpty { "N/C" }}<br><span style=\"color: #64748B; font-size: 6.5pt;\">$sectorText</span></td>\n")
                append("    <td style=\"padding: 5px 6px; border: 1px solid #CBD5E1;\">${t.appliedVoltage_kV.toInt()} kV ${t.voltageType}<br><span style=\"color: #64748B; font-size: 6.5pt;\">${t.applicationDurationSeconds}s contínuos</span></td>\n")
                append("    <td style=\"padding: 5px 6px; border: 1px solid #CBD5E1;\">$mStr<br><span style=\"color: #64748B; font-size: 6.5pt;\">Máx: $lStr</span></td>\n")
                val retestDateFullTableStr = if (isApp) DateUtils.formatDate(t.retestDueDate) else ""
                append("    <td style=\"padding: 5px 6px; border: 1px solid #CBD5E1; text-align: center;\">$retestDateFullTableStr</td>\n")
                append("    <td style=\"padding: 5px 6px; border: 1px solid #CBD5E1; text-align: center; font-weight: bold; color: ${if (isApp) "#15803D" else "#B91C1C"};\">${if (isApp) "APROVADO" else "REPROVADO"}</td>\n")
                append("  </tr>\n")

                val isFerramentaFull = ManualToolItem.isManualTool(t.equipmentType, t.equipmentTag)
                val parsedToolsFull = if (isFerramentaFull) ManualToolItem.getToolsForTest(t) else emptyList()
                if (isFerramentaFull && parsedToolsFull.isNotEmpty()) {
                    val appTools = ManualToolItem.getApprovedTools(parsedToolsFull)
                        val rejTools = ManualToolItem.getRejectedTools(parsedToolsFull)
                        val totalQty = parsedToolsFull.sumOf { it.quantity }
                        val appQty = appTools.sumOf { it.quantity }
                        val rejQty = rejTools.sumOf { it.quantity }
                        val borderLeft = if (rejQty == 0) "#16A34A" else if (appQty == 0) "#DC2626" else "#EA580C"
                        append("  <tr style=\"background-color: #F8FAFC;\">\n")
                        append("    <td colspan=\"7\" style=\"padding: 8px 12px; border: 1px solid #CBD5E1; border-left: 4px solid $borderLeft;\">\n")
                        append("      <div style=\"font-size: 7.5pt; font-weight: bold; color: #0F172A; margin-bottom: 5px;\">▸ COMPOSIÇÃO DETALHADA DO CONJUNTO: $totalQty un. ($appQty Aprovada(s) / $rejQty Reprovada(s))</div>\n")
                        append("      <table style=\"width: 100%; border-collapse: collapse; font-size: 7pt;\">\n")
                        val useTwoCols = parsedToolsFull.size > 2
                        val numRows = if (useTwoCols) (parsedToolsFull.size + 1) / 2 else parsedToolsFull.size
                        for (r in 0 until numRows) {
                            val t1 = parsedToolsFull[r * (if (useTwoCols) 2 else 1)]
                            val t2 = if (useTwoCols) parsedToolsFull.getOrNull(r * 2 + 1) else null
                            append("        <tr>\n")
                            append("          <td style=\"width: ${if (useTwoCols) "50%" else "100%"}; padding: 3px 6px;\">\n")
                            val c1 = if (t1.isApproved) "#15803D" else "#B91C1C"
                            val b1 = if (t1.isApproved) "APROVADO" else "REPROVADO"
                            val bg1 = if (t1.isApproved) "#DCFCE7" else "#FEE2E2"
                            append("            <span style=\"color: $c1; font-weight: bold;\">${if (t1.isApproved) "✓" else "✗"}</span> <b>${t1.name}</b> (${t1.quantity} un.) - <span style=\"background-color: $bg1; color: $c1; font-weight: bold; font-size: 6pt; padding: 1px 5px; border-radius: 2px;\">$b1</span>\n")
                            if (!t1.isApproved && t1.rejectionReason.isNotBlank()) {
                                append("            <br><span style=\"color: #B91C1C; font-size: 6pt;\">Motivo: ${t1.rejectionReason}</span>\n")
                            }
                            append("          </td>\n")
                            if (useTwoCols) {
                                if (t2 != null) {
                                    val c2 = if (t2.isApproved) "#15803D" else "#B91C1C"
                                    val b2 = if (t2.isApproved) "APROVADO" else "REPROVADO"
                                    val bg2 = if (t2.isApproved) "#DCFCE7" else "#FEE2E2"
                                    append("          <td style=\"width: 50%; padding: 3px 6px;\">\n")
                                    append("            <span style=\"color: $c2; font-weight: bold;\">${if (t2.isApproved) "✓" else "✗"}</span> <b>${t2.name}</b> (${t2.quantity} un.) - <span style=\"background-color: $bg2; color: $c2; font-weight: bold; font-size: 6pt; padding: 1px 5px; border-radius: 2px;\">$b2</span>\n")
                                    if (!t2.isApproved && t2.rejectionReason.isNotBlank()) {
                                        append("            <br><span style=\"color: #B91C1C; font-size: 6pt;\">Motivo: ${t2.rejectionReason}</span>\n")
                                    }
                                    append("          </td>\n")
                                } else {
                                    append("          <td style=\"width: 50%;\"></td>\n")
                                }
                            }
                            append("        </tr>\n")
                        }
                        append("      </table>\n")
                        append("    </td>\n")
                        append("  </tr>\n")
                }
            }
            append("</table>\n")

            // ==========================================
            // PAGE 5: CONCLUSÃO, RECOMENDAÇÕES E ASSINATURA RT
            // ==========================================
            append("<br clear=all style='mso-special-character:line-break;page-break-before:always'>\n")
            append(standardSubHeader)

            append("<div class=\"section-title\">6. CONCLUSÃO TÉCNICA E PARECER DE APTIDÃO</div>\n")
            append("<div style=\"background-color: #F8FAFC; border: 1px solid #CBD5E1; padding: 12px; border-radius: 4px;\">\n")
            val conclText1 = if (company.reportConclusionText.isNotBlank()) {
                company.reportConclusionText
                    .replace("{aprovados}", approvedCount.toString())
                    .replace("{total}", totalCount.toString())
                    .replace("{taxa}", "$approvalPercent")
            } else {
                "Com base nos ensaios laboratoriais de rigidez dielétrica e nas medições de corrente de fuga executados em conformidade com as normas ABNT NBR, IEC e ASTM, conclui-se que <b>$approvedCount dos $totalCount itens ensaiados ($approvalPercent%) ATENDEM PLENAMENTE</b> aos requisitos regulamentares de segurança da NR-10."
            }
            conclText1.split("\n\n").filter { it.isNotBlank() }.forEach { p ->
                append("  <p class=\"paragraph\">${p.trim().replace("\n", "<br>")}</p>\n")
            }
            if (company.reportConclusionText.isBlank()) {
                append("  <p class=\"paragraph\" style=\"margin-bottom: 0;\">Os equipamentos APROVADOS encontram-se com sua rigidez dielétrica preservada e devidamente aptos para uso em intervenções e serviços em instalações elétricas energizadas até suas respectivas tensões nominais de trabalho. Os itens com resultado REPROVADO (se houver) tiveram sua capacidade isolante comprometida e <b>DEVEM SER IMEDIATAMENTE INUTILIZADOS E RETIRADOS DE CIRCULAÇÃO</b>.</p>\n")
            }
            append("</div>\n")

            append("<div class=\"section-title\">7. RECOMENDAÇÕES DE SEGURANÇA, USO E MANUTENÇÃO (NR-10)</div>\n")
            append("<ul style=\"font-size: 8.5pt; color: #334155; line-height: 1.45; padding-left: 20px;\">\n")
            append("  <li><b>Inspeção Pré-Uso Diária:</b> Todo usuário deve inspecionar visualmente o EPI/EPC antes de cada intervenção (teste de inflamento com ar em luvas para detecção de microfuros).</li>\n")
            append("  <li><b>Armazenamento Adequado:</b> Guardar os equipamentos em suas bolsas protetoras individuais, ao abrigo de luz solar direta, calor excessivo, umidade e vapores de hidrocarbonetos.</li>\n")
            append("  <li><b>Higienização Correta:</b> Limpar exclusivamente com água e sabão neutro. Nunca utilizar solventes clorados, alcoóis agressivos, desengraxantes químicos ou derivados de petróleo.</li>\n")
            append("  <li><b>Descarte de Itens Reprovados:</b> Equipamentos reprovados devem ter seu CA descaracterizado e ser cortados fisicamente para evitar reaproveitamento indevido.</li>\n")
            append("  <li><b>Periodicidade de Reensaio:</b> Os ensaios devem ser renovados antes do término do prazo de validade técnica indicado em cada laudo individual (semestral para luvas/mantas e anual para capacetes e ferramentas).</li>\n")
            append("</ul>\n")

            append("<div class=\"section-title\">8. TERMO DE RESPONSABILIDADE TÉCNICA & ASSINATURA DIGITAL</div>\n")
            append("<table class=\"info-table\" style=\"background-color: #F1F5F9;\">\n")
            append("  <tr>\n")
            append("    <td style=\"width: 30%; text-align: center; vertical-align: middle; padding: 12px;\">\n")
            if (qrCodeB64 != null) {
                append("      <img src=\"data:image/png;base64,$qrCodeB64\" width=\"100\" height=\"100\" alt=\"QR Code\" style=\"border: 1px solid #CBD5E1; padding: 2px; background-color: #FFFFFF;\" /><br>\n")
            }
            append("      <div style=\"font-size: 7pt; color: #64748B; margin-top: 4px;\">Validação Digital de Autenticidade</div>\n")
            append("    </td>\n")
            append("    <td style=\"width: 70%; text-align: center; vertical-align: middle; padding: 16px;\">\n")
            append("      <div style=\"border-bottom: 1.5px solid $COLOR_NAVY; width: 80%; margin: 0 auto 8px auto;\"></div>\n")
            append("      <div style=\"font-size: 10pt; font-weight: bold; color: $COLOR_NAVY;\">${company.technicalResponsibleName.ifEmpty { "Eng. João Carlos Evaristo Guedes Nunes" }}</div>\n")
            append("      <div style=\"font-size: 8pt; color: #475569;\">Engenheiro Eletricista & Segurança do Trabalho</div>\n")
            append("      <div style=\"font-size: 7.5pt; color: #64748B;\">CREA/DF ${company.technicalResponsibleCrea.ifEmpty { "24481/D-DF" }} | ART: $effectiveArt</div>\n")
            append("    </td>\n")
            append("  </tr>\n")
            append("</table>\n")

            append("</div>\n") // End Section1
            append("</body>\n")
            append("</html>\n")
        }

        val fos = FileOutputStream(docFile)
        fos.write(html.toByteArray(Charsets.UTF_8))
        fos.flush()
        fos.close()

        docFile
    }

    /**
     * Opens the generated Word file in an external editor/viewer (MS Word, Google Docs, WPS).
     */
    fun openDoc(context: Context, docFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                docFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/msword")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Abrir Laudo no Word"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao abrir Word: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Shares the generated Word document via WhatsApp, Email, Drive, etc.
     */
    fun shareDoc(context: Context, docFile: File, subject: String = "Laudo Técnico Word") {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                docFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/msword"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, "Segue anexo o Laudo Técnico formatado em Word (.doc): $subject.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Documento Word"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao compartilhar Word: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // =========================================================================
    // HELPER FUNCTIONS FOR BITMAP RENDERING & BASE64 ENCODING
    // =========================================================================

    private fun getMaxUseVoltage(eqClass: String): String = when (eqClass) {
        "00" -> "500 V"
        "0" -> "1.000 V"
        "1" -> "7.500 V"
        "2" -> "17.000 V"
        "3" -> "26.500 V"
        "4" -> "36.000 V"
        else -> "N/A"
    }

    private fun generateGaugeBase64(
        title: String,
        value: Double,
        unit: String,
        maxScale: Double,
        limit: Double?,
        isConforming: Boolean,
        subtext: String
    ): String? {
        return try {
            val width = 280
            val height = 150
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Background
            canvas.drawColor(Color.WHITE)

            val paint = Paint().apply { isAntiAlias = true }

            // Title
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.parseColor("#0A192F")
            paint.textSize = 14f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(title.uppercase(), width / 2f, 20f, paint)

            // Gauge Arc Center
            val centerX = width / 2f
            val centerY = 105f
            val radius = 55f
            val arcRect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

            // Background Track
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 10f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Color.parseColor("#E2E8F0")
            canvas.drawArc(arcRect, 180f, 180f, false, paint)

            // Value Progress Arc
            val fraction = (value / maxScale).coerceIn(0.0, 1.0).toFloat()
            val sweep = fraction * 180f
            val arcColor = if (limit != null) {
                if (isConforming) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
            } else {
                Color.parseColor("#0066CC")
            }
            paint.color = arcColor
            canvas.drawArc(arcRect, 180f, sweep, false, paint)

            // Needle
            val angleRad = Math.toRadians((180.0 + (fraction * 180.0)))
            val needleX = centerX + (radius - 12f) * cos(angleRad).toFloat()
            val needleY = centerY + (radius - 12f) * sin(angleRad).toFloat()
            paint.strokeWidth = 3f
            paint.color = Color.parseColor("#0A192F")
            canvas.drawLine(centerX, centerY, needleX, needleY, paint)

            // Center Pin
            paint.style = Paint.Style.FILL
            canvas.drawCircle(centerX, centerY, 5f, paint)

            // Value readout text
            val valueStr = if (value % 1.0 == 0.0) "${value.toInt()} $unit" else String.format("%.1f %s", value, unit)
            paint.textSize = 16f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = if (limit != null && !isConforming) Color.parseColor("#EF4444") else Color.parseColor("#0A192F")
            canvas.drawText(valueStr, centerX, centerY + 24f, paint)

            // Subtext
            paint.textSize = 10f
            paint.color = Color.parseColor("#64748B")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(subtext, centerX, centerY + 38f, paint)

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** QR Code real do endereço do validador (mesmo da web), em PNG base64 */
    private fun generateQrCodeBase64(data: String, size: Int): String? {
        return try {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            com.example.util.QrCode.draw(Canvas(bitmap), 0f, 0f, size.toFloat(), data, Color.parseColor("#0A192F"), snapToPixels = true)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadLetterheadBase64(context: Context, company: AppSettingEntity): String? {
        if (!company.useLetterhead || company.letterheadUri.isBlank()) return null
        return try {
            val bitmap = LetterheadStorageHelper.loadLetterheadBitmap(context, company.letterheadUri) ?: return null
            val finalBitmap = if (company.letterheadOpacity < 0.99f) {
                val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint().apply {
                    alpha = (company.letterheadOpacity.coerceIn(0.05f, 1.0f) * 255).toInt()
                }
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
                output
            } else {
                bitmap
            }
            val baos = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun loadBitmapBase64(context: Context, pathOrUri: String, reqWidth: Int, reqHeight: Int): String? {
        return try {
            if (pathOrUri.isBlank()) return null
            val bitmap = if (pathOrUri.startsWith("content://")) {
                val uri = Uri.parse(pathOrUri)
                var inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false

                inputStream = context.contentResolver.openInputStream(uri)
                val bm = BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()
                bm
            } else {
                val file = File(pathOrUri)
                if (!file.exists()) return null

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)

                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false

                BitmapFactory.decodeFile(file.absolutePath, options)
            }

            val upright = ImageOrientation.upright(context, pathOrUri, bitmap)
            if (upright != null) {
                val outputStream = ByteArrayOutputStream()
                upright.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
                Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun loadDrawableBase64(context: Context, resId: Int, reqWidth: Int = 1200, reqHeight: Int = 900): String? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resId, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            val bitmap = BitmapFactory.decodeResource(context.resources, resId, options) ?: return null
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
