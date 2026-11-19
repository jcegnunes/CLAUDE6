package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.AppSettingEntity
import com.example.data.model.ClientEntity
import com.example.data.model.DielectricClass
import com.example.data.model.ManualToolItem
import com.example.data.model.ServiceOrderEntity
import com.example.data.model.TestRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * PDF Exporter for NR-10 Dielectric Laboratory Technical Reports & Certificates.
 * Generates official, high-resolution A4 PDFs complying with Brazilian electrical safety standards.
 * Synchronized with the platform layout and theme (DocumentDialogs.kt / LaudoDialog).
 */
object PdfExporter {

    // Standard A4 dimensions in points (72 points per inch)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    // Standard A4 Landscape dimensions (points)
    private const val LANDSCAPE_PAGE_WIDTH = 842
    private const val LANDSCAPE_PAGE_HEIGHT = 595

    // Palette Colors matching UI Theme
    private const val COLOR_NAVY = 0xFF0A192F.toInt()
    private const val COLOR_NAVY_LIGHT = 0xFF1E293B.toInt()
    private const val COLOR_BLUE = 0xFF0284C7.toInt()
    private const val COLOR_ORANGE = 0xFFEA580C.toInt()
    private const val COLOR_ORANGE_DARK = 0xFFD97706.toInt()
    private const val COLOR_GREEN = 0xFF16A34A.toInt()
    private const val COLOR_GREEN_DARK = 0xFF065F46.toInt()
    private const val COLOR_GREEN_BG = 0xFFECFDF5.toInt()
    private const val COLOR_RED = 0xFFDC2626.toInt()
    private const val COLOR_RED_DARK = 0xFF991B1B.toInt()
    private const val COLOR_RED_BG = 0xFFFEF2F2.toInt()
    private const val COLOR_BG_LIGHT = 0xFFF8FAFC.toInt()
    private const val COLOR_CARD_BG = 0xFFF8FAFC.toInt()
    private const val COLOR_BORDER = 0xFFE2E8F0.toInt()
    private const val COLOR_TEXT_DARK = 0xFF0F172A.toInt()
    private const val COLOR_TEXT_BODY = 0xFF334155.toInt()
    private const val COLOR_TEXT_MUTED = 0xFF64748B.toInt()
    private const val COLOR_TEXT_LIGHT_MUTED = 0xFF94A3B8.toInt()

    private var cachedCustomTypeface: Typeface? = null

    /**
     * Initializes the custom Arimo TrueType font from resources for clean typography,
     * crisp kerning, and optimal inter-word / inter-letter spacing.
     */
    fun initTypeface(context: Context) {
        if (cachedCustomTypeface == null) {
            cachedCustomTypeface = try {
                androidx.core.content.res.ResourcesCompat.getFont(context, R.font.arimo)
            } catch (e: Throwable) {
                null
            }
        }
    }

    private fun getArialTypeface(style: Int = Typeface.NORMAL): Typeface? {
        // A Arimo do app só tem a variante regular: Typeface.create(arimo, BOLD) gera um
        // negrito "falso" que engorda as letras sem aumentar o avanço, e no PDF elas se
        // sobrepõem. Negrito/itálico usam a fonte sem serifa do sistema, que tem os pesos reais.
        if (style != Typeface.NORMAL) {
            return try { Typeface.create(Typeface.SANS_SERIF, style) } catch (e: Throwable) { null }
        }
        return try {
            val base = cachedCustomTypeface ?: try {
                Typeface.create("Arial", Typeface.NORMAL)
            } catch (e: Throwable) {
                try { Typeface.SANS_SERIF } catch (t: Throwable) { null }
            }
            if (base != null) Typeface.create(base, style) else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun getMaxUseVoltage(eqClass: String): String {
        return try {
            val dClass = DielectricClass.fromString(eqClass)
            "Até ${dClass.maxUseVoltageAC} kV CA"
        } catch (e: Exception) {
            "Uso Conforme Classe $eqClass"
        }
    }

    private fun shouldApplyLetterhead(company: AppSettingEntity, pageNum: Int, totalPages: Int): Boolean {
        if (!company.useLetterhead || company.letterheadUri.isBlank()) return false
        return when (company.letterheadApplyMode) {
            "FIRST_PAGE_ONLY" -> pageNum == 1
            "INNER_PAGES_ONLY" -> pageNum > 1
            else -> true // "ALL_PAGES"
        }
    }

    private fun drawLetterheadIfEnabled(
        canvas: Canvas,
        letterheadBitmap: Bitmap?,
        company: AppSettingEntity,
        pageNum: Int,
        totalPages: Int,
        pageWidth: Int = PAGE_WIDTH,
        pageHeight: Int = PAGE_HEIGHT
    ) {
        if (letterheadBitmap == null || !shouldApplyLetterhead(company, pageNum, totalPages)) return
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
            alpha = (company.letterheadOpacity.coerceIn(0.05f, 1.0f) * 255).toInt()
        }
        val destRect = RectF(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat())
        canvas.drawBitmap(letterheadBitmap, null, destRect, paint)
    }

    /**
     * Renders a complete official A4 Laudo Técnico into a PdfDocument.
     * Supports single or multiple-item batch emission.
     */
    private suspend fun renderLaudoPagesToDocument(
        context: Context,
        pdfDocument: PdfDocument,
        test: TestRecordEntity,
        company: AppSettingEntity,
        startingPageNumber: Int = 1,
        itemIndex: Int = 0,
        totalItems: Int = 1
    ): Int {
        initTypeface(context)
        val photosList = if (test.photoUris.isNotBlank()) {
            test.photoUris.split("|||").filter { it.isNotBlank() }.map { entry ->
                val parts = entry.split("::")
                val label = if (parts.size > 1) parts[0] else "Evidência"
                val uri = if (parts.size > 1) parts[1] else parts[0]
                label to uri
            }
        } else emptyList()

        val totalPages = if (photosList.isNotEmpty()) 2 else 1

        val letterheadBitmap = if (company.useLetterhead && company.letterheadUri.isNotBlank()) {
            LetterheadStorageHelper.loadLetterheadBitmap(context, company.letterheadUri, PAGE_WIDTH * 2, PAGE_HEIGHT * 2)
        } else null

        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, startingPageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Background
        canvas.drawColor(Color.WHITE)
        drawLetterheadIfEnabled(canvas, letterheadBitmap, company, 1, totalPages, PAGE_WIDTH, PAGE_HEIGHT)

        val strokePaint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
        }
        val fillPaint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.FILL
        }
        val textPaint = Paint().apply {
            isAntiAlias = true
            isSubpixelText = true
            isLinearText = true
            isFilterBitmap = true
            isDither = true
            typeface = getArialTypeface(Typeface.NORMAL)
        }

        val margin = 18f
        val contentWidth = PAGE_WIDTH - 2 * margin

        val customLogoBitmap = if (company.companyLogoUri.isNotBlank()) {
            loadSampledBitmap(context, company.companyLogoUri, 200, 100)
        } else null

        val isP1LetterheadActive = company.useLetterhead && company.letterheadUri.isNotBlank() && letterheadBitmap != null && shouldApplyLetterhead(company, 1, totalPages)

        var currentY: Float
        if (isP1LetterheadActive) {
            // Letterhead loaded replaces the default layout frame and company header
            currentY = margin + 66f

            // Document Title Banner on top (Clean modern bar below letterhead header)
            fillPaint.color = 0xEEF8FAFC.toInt()
            canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + 34f), 5f, 5f, fillPaint)
            strokePaint.color = COLOR_BORDER
            strokePaint.strokeWidth = 0.8f
            canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + 34f), 5f, 5f, strokePaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_NAVY
            textPaint.textSize = 10.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("LAUDO TÉCNICO DE ENSAIO DIELÉTRICO NR-10", margin + 16f, currentY + 14f, textPaint)

            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6.8f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText("ENSAIOS DE RIGIDEZ DIELÉTRICA EM EPI / EPC / FERRAMENTAL DE SEGURANÇA", margin + 16f, currentY + 26f, textPaint)

            // Right Column: Identifier & ART
            val rightColX = PAGE_WIDTH - margin - 16f
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = COLOR_TEXT_LIGHT_MUTED
            textPaint.textSize = 6.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("IDENTIFICADOR DO LAUDO", rightColX, currentY + 11f, textPaint)

            textPaint.color = COLOR_BLUE
            textPaint.textSize = 10.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(test.reportNumber, rightColX, currentY + 22f, textPaint)

            if (test.artNumber.isNotEmpty()) {
                textPaint.color = COLOR_ORANGE_DARK
                textPaint.textSize = 6.8f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas.drawText("ART: ${test.artNumber}", rightColX, currentY + 31f, textPaint)
            }

            currentY += 34f + 6f
        } else {
            // Outer Page Border
            strokePaint.color = COLOR_NAVY
            strokePaint.strokeWidth = 1.2f
            canvas.drawRect(margin, margin, PAGE_WIDTH - margin, PAGE_HEIGHT - margin, strokePaint)

            // ==========================================
            // COMPANY HEADER (Mirroring LaudoDialog UI)
            // ==========================================
            currentY = margin + 12f

            // Company Logo Box (Custom Logo or Navy JVM badge)
            val logoW: Float
            if (customLogoBitmap != null) {
                val maxW = 50f
                val maxH = 34f
                val ratio = customLogoBitmap.width.toFloat() / customLogoBitmap.height.toFloat()
                val (drawW, drawH) = if (ratio > maxW / maxH) {
                    maxW to (maxW / ratio)
                } else {
                    (maxH * ratio) to maxH
                }
                val drawX = margin + 10f
                val drawY = currentY + (maxH - drawH) / 2f
                val filterPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
                canvas.drawBitmap(customLogoBitmap, null, RectF(drawX, drawY, drawX + drawW, drawY + drawH), filterPaint)
                logoW = drawW
            } else {
                val logoSize = 34f
                fillPaint.color = COLOR_NAVY
                canvas.drawRoundRect(RectF(margin + 10f, currentY, margin + 10f + logoSize, currentY + logoSize), 6f, 6f, fillPaint)

                textPaint.color = Color.WHITE
                textPaint.textSize = 12f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText("JVM", margin + 10f + (logoSize / 2f), currentY + 22f, textPaint)
                logoW = logoSize
            }

            // Company Text block
            val companyTextX = margin + 10f + logoW + 8f
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_NAVY
            textPaint.textSize = 10.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(company.name.ifEmpty { "JVM ENGENHARIA & SERVIÇOS TÉCNICOS" }, companyTextX, currentY + 11f, textPaint)

            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 7.5f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText(company.legalName.ifEmpty { "Laboratório de Ensaios Elétricos e Rigidez Dielétrica" }, companyTextX, currentY + 21f, textPaint)
            canvas.drawText("${company.creaCompanyRegister.ifEmpty { "CREA-DF 1620-PJ" }} • CNPJ: ${company.cnpj.ifEmpty { "29.894.500/0001-04" }}", companyTextX, currentY + 31f, textPaint)

            // Right Column: Identifier & ART
            val rightColX = PAGE_WIDTH - margin - 10f
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = COLOR_TEXT_LIGHT_MUTED
            textPaint.textSize = 6.8f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("IDENTIFICADOR", rightColX, currentY + 10f, textPaint)

            textPaint.color = COLOR_BLUE
            textPaint.textSize = 10.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(test.reportNumber, rightColX, currentY + 22f, textPaint)

            if (test.artNumber.isNotEmpty()) {
                textPaint.color = COLOR_ORANGE_DARK
                textPaint.textSize = 7.5f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas.drawText("ART: ${test.artNumber}", rightColX, currentY + 32f, textPaint)
            }

            // Horizontal Navy Divider
            currentY += 34f + 6f
            fillPaint.color = COLOR_NAVY
            canvas.drawRect(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + 2f, fillPaint)
            currentY += 6f
        }

        // ==============================================================
        // 1. IDENTIFICAÇÃO DO CLIENTE E DO EQUIPAMENTO
        // ==============================================================
        currentY = drawSectionHeader(canvas, "1. Identificação do Cliente e do Equipamento", margin + 8f, currentY, contentWidth - 16f)

        val hasCollaborator = test.collaboratorName.isNotEmpty()
        val card1Height = if (hasCollaborator) 58f else 46f

        fillPaint.color = COLOR_BG_LIGHT
        canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + card1Height), 6f, 6f, fillPaint)
        strokePaint.color = COLOR_BORDER
        strokePaint.strokeWidth = 0.8f
        canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + card1Height), 6f, 6f, strokePaint)

        val c1X = margin + 14f
        val c2X = margin + 290f

        // Row 1: Cliente & Ordem de Serviço
        drawInfoField(canvas, "Cliente:", test.clientName.ifEmpty { "Consumidor Geral" }, c1X, currentY + 11f, 260f, tabOffset = 72f)
        drawInfoField(canvas, "Ordem de Serviço:", test.serviceOrderNumber.ifEmpty { "OS-AVULSA" }, c2X, currentY + 11f, 250f, isMono = true, tabOffset = 78f)

        // Row 2: Equipamento & Tag
        drawInfoField(canvas, "Equipamento:", if (test.equipmentType.equals("DETECTOR_INDUCAO", ignoreCase = true)) "DETECTOR DE TENSÃO POR INDUÇÃO" else test.equipmentType.replace("_", " ").uppercase(), c1X, currentY + 23f, 260f, tabOffset = 72f)
        drawInfoField(canvas, "Tag / Patrimônio:", test.equipmentTag, c2X, currentY + 23f, 250f, isMono = true, isHighlight = true, tabOffset = 78f)

        // Row 3: Classe Dielétrica & Nº de Série / CA
        drawInfoField(canvas, "Classe Dielétrica:", "Classe ${test.equipmentClass} (${getMaxUseVoltage(test.equipmentClass)})", c1X, currentY + 35f, 260f, tabOffset = 72f)
        drawInfoField(canvas, "Nº de Série / CA:", "${test.equipmentSerial.ifEmpty { "N/A" }} • CA: ${test.equipmentCa.ifEmpty { "N/A" }}", c2X, currentY + 35f, 250f, tabOffset = 78f)

        if (hasCollaborator) {
            drawInfoField(canvas, "Colaborador:", test.collaboratorName, c1X, currentY + 47f, 260f, tabOffset = 72f)
            drawInfoField(canvas, "Matrícula / Setor:", "${test.collaboratorRegistration} • ${test.collaboratorSector}", c2X, currentY + 47f, 250f, tabOffset = 78f)
        }

        currentY += card1Height + 8f

        // ==============================================================
        // 2. REFERÊNCIA NORMATIVA E CONDIÇÕES AMBIENTAIS
        // ==============================================================
        currentY = drawSectionHeader(canvas, "2. Referência Normativa e Condições Ambientais", margin + 8f, currentY, contentWidth - 16f)

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
                gloveLen <= 300 -> 0 // 280 mm
                gloveLen in 301..380 -> 1 // 360 mm
                gloveLen in 381..430 -> 2 // 410 mm
                else -> 3 // >= 460 mm
            }

            val card2Height = 72f
            fillPaint.color = COLOR_BG_LIGHT
            canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + card2Height), 6f, 6f, fillPaint)
            strokePaint.color = COLOR_BORDER
            strokePaint.strokeWidth = 0.8f
            canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + card2Height), 6f, 6f, strokePaint)

            // 1ª linha: 4 colunas de metadados
            val mCol1 = margin + 14f
            val mCol2 = margin + 185f
            val mCol3 = margin + 350f
            val mCol4 = margin + 450f

            drawInfoField(canvas, "Norma Regulamentadora / Técnica:", test.normCode.ifEmpty { "NBR 16295 Tabela 4" }, mCol1, currentY + 12f, 165f)
            drawInfoField(canvas, "Procedimento de Ensaio:", "PR-JVM-LAB-01 Rev.08", mCol2, currentY + 12f, 160f)
            drawInfoField(canvas, "Temperatura Ambiente:", "${test.temperatureC} °C", mCol3, currentY + 12f, 95f)
            drawInfoField(canvas, "Umidade Relativa:", "${test.relativeHumidityPercent}% UR", mCol4, currentY + 12f, 85f)

            // Card / Tabela 4 interna
            val tblX = margin + 14f
            val tblW = contentWidth - 28f
            val tblY = currentY + 20f
            val tblH = 46f

            fillPaint.color = 0xFFF0F7FF.toInt()
            canvas.drawRoundRect(RectF(tblX, tblY, tblX + tblW, tblY + tblH), 4f, 4f, fillPaint)
            strokePaint.color = 0xFFBFDBFE.toInt()
            strokePaint.strokeWidth = 0.6f
            canvas.drawRoundRect(RectF(tblX, tblY, tblX + tblW, tblY + tblH), 4f, 4f, strokePaint)

            // Header da Tabela 4 com badge
            fillPaint.color = COLOR_BLUE
            canvas.drawCircle(tblX + 6f, tblY + 7f, 2.5f, fillPaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_NAVY
            textPaint.textSize = 6.4f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("TABELA 4 DA ABNT NBR 16295 / IEC 60903 – ENSAIOS DE PROVA E RIGIDEZ DIELÉTRICA", tblX + 12f, tblY + 9f, textPaint)

            // Badge pill à direita
            val badgeText = "Luva Ensaiada: Classe $cleanClass - ${gloveLen} mm"
            textPaint.textSize = 6.0f
            val badgeW = textPaint.measureText(badgeText) + 10f
            val badgeRect = RectF(tblX + tblW - badgeW - 4f, tblY + 2.5f, tblX + tblW - 4f, tblY + 12.5f)
            fillPaint.color = 0xFFDBEAFE.toInt()
            canvas.drawRoundRect(badgeRect, 3f, 3f, fillPaint)
            textPaint.color = 0xFF1E40AF.toInt()
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(badgeText, badgeRect.centerX(), tblY + 9.5f, textPaint)

            // Header das colunas
            val colY = tblY + 14f
            val colH = 10f
            fillPaint.color = 0xFFE2E8F0.toInt()
            canvas.drawRect(tblX + 2f, colY, tblX + tblW - 2f, colY + colH, fillPaint)

            val colsWidth = floatArrayOf(
                tblW * 0.12f, // Classe
                tblW * 0.14f, // Tensão Máx. Uso
                tblW * 0.14f, // Tensão Prova CA
                tblW * 0.11f, // 280 mm
                tblW * 0.14f, // 360 mm
                tblW * 0.11f, // 410 mm
                tblW * 0.11f, // >= 460 mm
                tblW * 0.13f  // Tensão Rigidez
            )
            val colHeaders = arrayOf("Classe", "Tensão Máx. Uso", "Tensão Prova CA", "280 mm", "360 mm", "410 mm", ">= 460 mm", "Tensão Rigidez")

            textPaint.color = COLOR_NAVY
            textPaint.textSize = 5.6f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            textPaint.textAlign = Paint.Align.CENTER

            var curX = tblX + 2f
            for (i in colHeaders.indices) {
                val cw = colsWidth[i]
                canvas.drawText(colHeaders[i], curX + cw / 2f, colY + 7f, textPaint)
                curX += cw
            }

            // Linha de dados com o parâmetro selecionado
            val rowY = colY + colH
            val rowH = 12f

            fillPaint.color = 0xFFEFF6FF.toInt()
            canvas.drawRect(tblX + 2f, rowY, tblX + tblW - 2f, rowY + rowH, fillPaint)
            strokePaint.color = 0xFF93C5FD.toInt()
            strokePaint.strokeWidth = 0.5f
            canvas.drawRect(tblX + 2f, rowY, tblX + tblW - 2f, rowY + rowH, strokePaint)

            val rowValues = arrayOf(
                "Classe ${gloveRow.classe}",
                "${gloveRow.tensaoUsoMaxAC_kV} kV",
                "${gloveRow.tensaoProvaAC_kV} kV",
                gloveRow.limite280mm?.let { "${it.toInt()} mA" } ?: "N/a",
                gloveRow.limite360mm?.let { "${it.toInt()} mA" } ?: "N/a",
                gloveRow.limite410mm?.let { "${it.toInt()} mA" } ?: "N/a",
                gloveRow.limite460mm?.let { "${it.toInt()} mA" } ?: "N/a",
                "${gloveRow.tensaoRigidezAC_kV} kV"
            )

            curX = tblX + 2f
            for (i in rowValues.indices) {
                val cw = colsWidth[i]
                val isLengthCol = (i in 3..6)
                val isThisActiveLength = isLengthCol && (i - 3 == activeColIndex)

                if (isThisActiveLength) {
                    val pillRect = RectF(curX + 1.5f, rowY + 1.5f, curX + cw - 1.5f, rowY + rowH - 1.5f)
                    fillPaint.color = COLOR_BLUE
                    canvas.drawRoundRect(pillRect, 2.5f, 2.5f, fillPaint)

                    textPaint.color = Color.WHITE
                    textPaint.textSize = 5.8f
                    textPaint.typeface = getArialTypeface(Typeface.BOLD)
                    textPaint.textAlign = Paint.Align.CENTER
                    val valText = rowValues[i].replace(" mA", "")
                    canvas.drawText("$valText mA [Alvo]", pillRect.centerX(), rowY + 8f, textPaint)
                } else {
                    textPaint.textSize = 5.8f
                    textPaint.textAlign = Paint.Align.CENTER
                    if (i <= 2 || i == 7) {
                        textPaint.color = 0xFF1D4ED8.toInt()
                        textPaint.typeface = getArialTypeface(Typeface.BOLD)
                    } else {
                        if (rowValues[i] == "N/a") {
                            textPaint.color = COLOR_RED
                            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                        } else {
                            textPaint.color = COLOR_TEXT_DARK
                            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                        }
                    }
                    canvas.drawText(rowValues[i], curX + cw / 2f, rowY + 8f, textPaint)
                }
                curX += cw
            }

            // Rodapé do Card da Tabela
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = 0xFF475569.toInt()
            textPaint.textSize = 5.0f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            val critText = "Critério Operativo: Luva Classe $cleanClass ($gloveLen mm) -> Tensão de Prova: ${test.appliedVoltage_kV} kV CA | Limite Máximo de Fuga: ${test.leakageCurrentLimit_mA} mA (Par de 2 luvas simultâneas: 2x valor unitário da Tab. 4)."
            canvas.drawText(critText, tblX + 4f, tblY + tblH - 2.5f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = 0xFF94A3B8.toInt()
            canvas.drawText("Nota: Limite da Tabela 4 é unitário. Ensaio com 2 luvas simultâneas adota o dobro (2x).", tblX + tblW - 4f, tblY + tblH - 2.5f, textPaint)

            currentY += card2Height + 6f
        } else {
            val card2Height = 24f
            fillPaint.color = COLOR_BG_LIGHT
            canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + card2Height), 6f, 6f, fillPaint)
            strokePaint.color = COLOR_BORDER
            strokePaint.strokeWidth = 0.8f
            canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + card2Height), 6f, 6f, strokePaint)

            val mCol1 = margin + 14f
            val mCol2 = margin + 185f
            val mCol3 = margin + 350f
            val mCol4 = margin + 450f

            drawInfoField(canvas, "Norma Aplicável:", test.normCode.ifEmpty { "ABNT NBR 16295" }, mCol1, currentY + 15f, 165f)
            drawInfoField(canvas, "Procedimento:", "PR-JVM-LAB-01 Rev.08", mCol2, currentY + 15f, 160f)
            drawInfoField(canvas, "Temperatura:", "${test.temperatureC} °C", mCol3, currentY + 15f, 95f)
            drawInfoField(canvas, "Umidade Relativa:", "${test.relativeHumidityPercent}% UR", mCol4, currentY + 15f, 85f)

            currentY += card2Height + 6f
        }

        // ==============================================================
        // 3. PARÂMETROS E RESULTADOS DAS MEDIÇÕES (SPEEDOMETERS GAUGE)
        // ==============================================================
        currentY = drawSectionHeader(canvas, "3. Parâmetros e Resultados das Medições", margin + 8f, currentY, contentWidth - 16f)

        // Detector de tensão por indução: tabela Distância × Tensão + gráfico + OBS (igual à web)
        if (test.equipmentType.equals("DETECTOR_INDUCAO", ignoreCase = true)) {
            currentY = drawInductionSection(canvas, test, margin + 8f, currentY, contentWidth - 16f)
        } else {
        val gaugeCardWidth = (contentWidth - 16f - 8f) / 2f
        val gaugeCardHeight = 74f
        val gauge1X = margin + 8f
        val gauge2X = gauge1X + gaugeCardWidth + 8f

        // Left Gauge: TENSÃO APLICADA
        val maxVoltageScale = (test.appliedVoltage_kV * 1.4).coerceAtLeast(15.0)
        drawSpeedometerGauge(
            canvas = canvas,
            title = "Tensão Aplicada",
            value = test.appliedVoltage_kV,
            unit = "kV ${test.voltageType}",
            maxScale = maxVoltageScale,
            limit = null,
            isConforming = true,
            isVoltage = true,
            subtext = "Tempo: ${test.applicationDurationSeconds}s contínuos",
            x = gauge1X,
            y = currentY,
            width = gaugeCardWidth,
            height = gaugeCardHeight
        )

        // Right Gauge: CORRENTE DE FUGA
        val maxLeakageScale = (test.leakageCurrentLimit_mA * 1.5).coerceAtLeast(15.0)
        val isLeakageConforming = test.measuredLeakageCurrent_mA <= test.leakageCurrentLimit_mA
        drawSpeedometerGauge(
            canvas = canvas,
            title = "Corrente de Fuga",
            value = test.measuredLeakageCurrent_mA,
            unit = test.currentUnit.ifEmpty { "mA" },
            maxScale = maxLeakageScale,
            limit = test.leakageCurrentLimit_mA,
            isConforming = isLeakageConforming,
            isVoltage = false,
            subtext = "Limite: ${test.leakageCurrentLimit_mA} mA",
            x = gauge2X,
            y = currentY,
            width = gaugeCardWidth,
            height = gaugeCardHeight
        )

        currentY += gaugeCardHeight + 5f

        // ==============================================================
        // TABELA RESUMO DOS PARÂMETROS NORMATIVOS E VALORES OBTIDOS
        // ==============================================================
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

        val summaryTableX = margin + 8f
        val summaryTableW = contentWidth - 16f
        val headerH = 11f
        val rowH = 10.5f
        val totalSummaryH = headerH + (rowH * 4)

        // Draw card background
        fillPaint.color = COLOR_BG_LIGHT
        canvas.drawRoundRect(RectF(summaryTableX, currentY, summaryTableX + summaryTableW, currentY + totalSummaryH), 4f, 4f, fillPaint)
        strokePaint.color = COLOR_BORDER
        strokePaint.strokeWidth = 0.8f
        canvas.drawRoundRect(RectF(summaryTableX, currentY, summaryTableX + summaryTableW, currentY + totalSummaryH), 4f, 4f, strokePaint)

        // Draw header background
        fillPaint.color = 0xFFE2E8F0.toInt()
        canvas.drawRoundRect(RectF(summaryTableX, currentY, summaryTableX + summaryTableW, currentY + headerH), 4f, 4f, fillPaint)
        canvas.drawRect(RectF(summaryTableX, currentY + headerH - 4f, summaryTableX + summaryTableW, currentY + headerH), fillPaint)

        val col1W = summaryTableW * 0.28f
        val col2W = summaryTableW * 0.40f
        val col3W = summaryTableW * 0.17f
        val col4W = summaryTableW * 0.15f

        textPaint.color = COLOR_NAVY
        textPaint.textSize = 6.2f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Parâmetro de Ensaio", summaryTableX + 6f, currentY + 7.5f, textPaint)
        canvas.drawText("Exigência Normativa", summaryTableX + col1W + 4f, currentY + 7.5f, textPaint)
        canvas.drawText("Valor Medido", summaryTableX + col1W + col2W + 4f, currentY + 7.5f, textPaint)

        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Avaliação", summaryTableX + col1W + col2W + col3W + (col4W / 2f), currentY + 7.5f, textPaint)

        // Data rows
        val summaryRows = listOf(
            Triple("Tensão de Ensaio Aplicada", voltageNormReq, "${test.appliedVoltage_kV} kV ${test.voltageType.ifEmpty { "AC" }}") to true,
            Triple("Tempo de Aplicação da Tensão", timeNormReq, "${test.applicationDurationSeconds} s") to true,
            Triple("Corrente de Fuga", leakNormReq, "${test.measuredLeakageCurrent_mA} ${test.currentUnit.ifEmpty { "mA" }}") to isLeakOk,
            Triple("Rigidez / Suportabilidade à Perfuração", rigidityNormReq, if (isRigidityOk) "Sem perfuração dielétrica" else "Perfuração dielétrica detectada") to isRigidityOk
        )

        var curRowY = currentY + headerH
        for (item in summaryRows) {
            val (param, req, measured) = item.first
            val isConf = item.second

            // Divider line
            strokePaint.color = 0xFFE2E8F0.toInt()
            strokePaint.strokeWidth = 0.5f
            canvas.drawLine(summaryTableX, curRowY, summaryTableX + summaryTableW, curRowY, strokePaint)

            // Col 1: Parâmetro
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_TEXT_DARK
            textPaint.textSize = 5.8f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText(param, summaryTableX + 6f, curRowY + 7.2f, textPaint)

            // Col 2: Exigência Normativa
            textPaint.color = COLOR_TEXT_DARK
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(req, summaryTableX + col1W + 4f, curRowY + 7.2f, textPaint)

            // Col 3: Valor Medido (Electric Blue)
            textPaint.color = COLOR_BLUE
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(measured, summaryTableX + col1W + col2W + 4f, curRowY + 7.2f, textPaint)

            // Col 4: Avaliação Badge (Pill)
            val badgeW = 42f
            val badgeH = 7.5f
            val badgeCenterX = summaryTableX + col1W + col2W + col3W + (col4W / 2f)
            val badgeRect = RectF(badgeCenterX - (badgeW / 2f), curRowY + 1.5f, badgeCenterX + (badgeW / 2f), curRowY + 1.5f + badgeH)

            fillPaint.color = if (isConf) 0xFFDCFCE7.toInt() else 0xFFFEE2E2.toInt()
            canvas.drawRoundRect(badgeRect, 2.5f, 2.5f, fillPaint)
            strokePaint.color = if (isConf) 0xFF86EFAC.toInt() else 0xFFFCA5A5.toInt()
            strokePaint.strokeWidth = 0.5f
            canvas.drawRoundRect(badgeRect, 2.5f, 2.5f, strokePaint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = if (isConf) 0xFF166534.toInt() else 0xFF991B1B.toInt()
            textPaint.textSize = 5.2f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(if (isConf) "CONFORME" else "NÃO CONFORME", badgeCenterX, curRowY + 7.0f, textPaint)

            curRowY += rowH
        }

        currentY += totalSummaryH + 6f
        }

        // ==============================================================
        // 4. INSPEÇÃO VISUAL E FÍSICO-MECÂNICA
        // ==============================================================
        currentY = drawSectionHeader(canvas, "4. Inspeção Visual e Físico-Mecânica", margin + 8f, currentY, contentWidth - 16f)

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

        val visualCardX = margin + 8f
        val visualCardW = contentWidth - 16f
        val visualRowH = 9.5f
        val visualCardH = (visualRowH * 3) + 4f

        fillPaint.color = COLOR_BG_LIGHT
        canvas.drawRoundRect(RectF(visualCardX, currentY, visualCardX + visualCardW, currentY + visualCardH), 4f, 4f, fillPaint)
        strokePaint.color = COLOR_BORDER
        strokePaint.strokeWidth = 0.8f
        canvas.drawRoundRect(RectF(visualCardX, currentY, visualCardX + visualCardW, currentY + visualCardH), 4f, 4f, strokePaint)

        val colGap = 8f
        val halfColW = (visualCardW - 16f - colGap) / 2f
        val visLeftColX = visualCardX + 8f
        val visRightColX = visualCardX + 8f + halfColW + colGap

        val visualRows = if (test.equipmentType.equals("DETECTOR_INDUCAO", ignoreCase = true)) {
            // detector por indução: os itens reais da inspeção (como no laudo da web), 2 por linha
            val items = com.example.domain.NormsEngine.getVisualChecklist(com.example.data.model.EquipmentType.DETECTOR_INDUCAO)
                .map { it.title to !test.visualInspectionSummary.contains(it.title, ignoreCase = true) }
            items.chunked(2).map { pair -> pair[0] to (pair.getOrNull(1) ?: ("" to true)) }
        } else listOf(
            (visualItem1Left to isVisualConf) to (visualItem1Right to isVisualConf),
            (visualItem2Left to isVisualConf) to (visualItem2Right to isVisualConf),
            (visualItem3Left to isVisualConf) to ("" to true)
        )

        var curVisY = currentY + 2f
        for (i in visualRows.indices) {
            val (leftPair, rightPair) = visualRows[i]

            // Left column item
            val (leftText, leftConf) = leftPair
            if (leftText.isNotBlank()) {
                val badgeW = 38f
                val badgeH = 7f
                val badgeX = visLeftColX + halfColW - badgeW
                val badgeY = curVisY + 1.2f

                fillPaint.color = if (leftConf) 0xFFDCFCE7.toInt() else 0xFFFEE2E2.toInt()
                canvas.drawRoundRect(RectF(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH), 2f, 2f, fillPaint)
                strokePaint.color = if (leftConf) 0xFF86EFAC.toInt() else 0xFFFCA5A5.toInt()
                strokePaint.strokeWidth = 0.5f
                canvas.drawRoundRect(RectF(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH), 2f, 2f, strokePaint)

                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = if (leftConf) 0xFF166534.toInt() else 0xFF991B1B.toInt()
                textPaint.textSize = 5.0f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas.drawText(if (leftConf) "CONFORME" else "NÃO CONFORME", badgeX + (badgeW / 2f), badgeY + 5.2f, textPaint)

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = COLOR_TEXT_DARK
                textPaint.textSize = 4.8f
                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                val maxTxtW = halfColW - badgeW - 4f
                val fitText = if (textPaint.measureText(leftText) > maxTxtW) {
                    var truncated = leftText
                    while (truncated.length > 5 && textPaint.measureText("$truncated...") > maxTxtW) {
                        truncated = truncated.substring(0, truncated.length - 1)
                    }
                    "$truncated..."
                } else leftText
                canvas.drawText(fitText, visLeftColX, curVisY + 6.2f, textPaint)
            }

            // Right column item
            val (rightText, rightConf) = rightPair
            if (rightText.isNotBlank()) {
                val badgeW = 38f
                val badgeH = 7f
                val badgeX = visRightColX + halfColW - badgeW
                val badgeY = curVisY + 1.2f

                fillPaint.color = if (rightConf) 0xFFDCFCE7.toInt() else 0xFFFEE2E2.toInt()
                canvas.drawRoundRect(RectF(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH), 2f, 2f, fillPaint)
                strokePaint.color = if (rightConf) 0xFF86EFAC.toInt() else 0xFFFCA5A5.toInt()
                strokePaint.strokeWidth = 0.5f
                canvas.drawRoundRect(RectF(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH), 2f, 2f, strokePaint)

                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = if (rightConf) 0xFF166534.toInt() else 0xFF991B1B.toInt()
                textPaint.textSize = 5.0f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas.drawText(if (rightConf) "CONFORME" else "NÃO CONFORME", badgeX + (badgeW / 2f), badgeY + 5.2f, textPaint)

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = COLOR_TEXT_DARK
                textPaint.textSize = 4.8f
                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                val maxTxtW = halfColW - badgeW - 4f
                val fitText = if (textPaint.measureText(rightText) > maxTxtW) {
                    var truncated = rightText
                    while (truncated.length > 5 && textPaint.measureText("$truncated...") > maxTxtW) {
                        truncated = truncated.substring(0, truncated.length - 1)
                    }
                    "$truncated..."
                } else rightText
                canvas.drawText(fitText, visRightColX, curVisY + 6.2f, textPaint)
            }

            curVisY += visualRowH
            if (i < visualRows.size - 1) {
                strokePaint.color = 0xFFE2E8F0.toInt()
                strokePaint.strokeWidth = 0.4f
                canvas.drawLine(visualCardX + 4f, curVisY - 1f, visualCardX + visualCardW - 4f, curVisY - 1f, strokePaint)
            }
        }

        currentY += visualCardH + 5f

        // ==============================================================
        // 5. PARECER TÉCNICO CONCLUSIVO (OU DOIS PARECERES)
        // ==============================================================
        val parsedTools = ManualToolItem.getToolsForTest(test)
        val approvedTools = ManualToolItem.getApprovedTools(parsedTools)
        val rejectedTools = ManualToolItem.getRejectedTools(parsedTools)
        val hasDualOpinions = parsedTools.isNotEmpty() && rejectedTools.isNotEmpty() && approvedTools.isNotEmpty()
        val isApproved = test.result == "APROVADO"

        if (hasDualOpinions) {
            currentY = drawSectionHeader(canvas, "5. Pareceres Técnicos Conclusivos (Dois Pareceres Emitidos)", margin + 8f, currentY, contentWidth - 16f)

            // Parecer 1: Ferramentas Aprovadas
            val appCount = approvedTools.sumOf { it.quantity }
            val appStatement = "Aprovadas na inspeção visual e ensaio de rigidez dielétrica (10 kV CA / 180s - ABNT NBR 9699 / IEC 60900), com fuga < 1,0 mA/un. Liberadas para operação em circuitos elétricos até 1000 V CA / 1500 V CC conforme NR-10."

            textPaint.textSize = 6.8f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            val appStatementLines = breakTextIntoLines(appStatement, contentWidth - 28f, textPaint)
            val useAppTwoCols = approvedTools.size > 3
            val appRows = if (useAppTwoCols) (approvedTools.size + 1) / 2 else approvedTools.size
            val appToolsHeight = appRows * 14.5f
            val cardAppHeight = (22f + (appStatementLines.size * 9f) + appToolsHeight + 20f).coerceAtLeast(54f)

            fillPaint.color = COLOR_GREEN_BG
            canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + cardAppHeight), 6f, 6f, fillPaint)
            strokePaint.color = COLOR_GREEN
            strokePaint.strokeWidth = 1.2f
            canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + cardAppHeight), 6f, 6f, strokePaint)

            var appY = currentY + 12f
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_GREEN_DARK
            textPaint.textSize = 8.2f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("PARECER 1: FERRAMENTAS APROVADAS ($appCount un.) - CONFORME P/ USO", margin + 14f, appY, textPaint)

            appY += 10f
            textPaint.color = COLOR_TEXT_BODY
            textPaint.textSize = 6.8f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            for (line in appStatementLines) {
                canvas.drawText(line, margin + 14f, appY, textPaint)
                appY += 9f
            }

            appY += 2f
            if (useAppTwoCols) {
                val colGap = 6f
                val colW = (contentWidth - 28f - colGap) / 2f
                for (r in 0 until appRows) {
                    val t1 = approvedTools[r * 2]
                    val t2 = approvedTools.getOrNull(r * 2 + 1)
                    drawToolBadge(
                        canvas = canvas,
                        name = "✓  ${t1.name}  (${t1.quantity} un.)",
                        badgeText = "APROVADO",
                        isApproved = true,
                        x = margin + 14f,
                        y = appY,
                        width = colW
                    )
                    if (t2 != null) {
                        drawToolBadge(
                            canvas = canvas,
                            name = "✓  ${t2.name}  (${t2.quantity} un.)",
                            badgeText = "APROVADO",
                            isApproved = true,
                            x = margin + 14f + colW + colGap,
                            y = appY,
                            width = colW
                        )
                    }
                    appY += 14.5f
                }
            } else {
                approvedTools.forEach { tool ->
                    val consumed = drawToolBadge(
                        canvas = canvas,
                        name = "✓  ${tool.name}  (${tool.quantity} un.)",
                        badgeText = "APROVADO P/ USO",
                        isApproved = true,
                        x = margin + 14f,
                        y = appY,
                        width = contentWidth - 28f
                    )
                    appY += consumed
                }
            }

            // Divider before retest footer
            appY += 1f
            strokePaint.color = 0xFFA7F3D0.toInt()
            strokePaint.strokeWidth = 0.5f
            canvas.drawLine(margin + 14f, appY, PAGE_WIDTH - margin - 14f, appY, strokePaint)
            appY += 9f

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_GREEN_DARK
            textPaint.textSize = 7.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("Validade / Próximo Reensaio:", margin + 14f, appY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(if (test.result == "APROVADO") DateUtils.formatDate(test.retestDueDate) else "", PAGE_WIDTH - margin - 14f, appY, textPaint)

            currentY += cardAppHeight + 6f

            // Parecer 2: Ferramentas Reprovadas
            val rejCount = rejectedTools.sumOf { it.quantity }
            val rejStatement = "Reprovadas/Condenadas por não conformidades com ABNT NBR 9699 e NR-10. Fica proibido seu uso. Determina-se a segregação física imediata, inutilização e descarte (NR-10 item 10.4.3.1)."

            textPaint.textSize = 6.8f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            val rejStatementLines = breakTextIntoLines(rejStatement, contentWidth - 28f, textPaint)
            var rejToolsHeight = 0f
            rejectedTools.forEach { t ->
                rejToolsHeight += if (t.rejectionReason.isNotBlank()) 20f else 14.5f
            }
            val cardRejHeight = (22f + (rejStatementLines.size * 9f) + rejToolsHeight + 20f).coerceAtLeast(54f)

            fillPaint.color = COLOR_RED_BG
            canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + cardRejHeight), 6f, 6f, fillPaint)
            strokePaint.color = COLOR_RED
            strokePaint.strokeWidth = 1.2f
            canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + cardRejHeight), 6f, 6f, strokePaint)

            var rejY = currentY + 12f
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_RED_DARK
            textPaint.textSize = 8.2f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("PARECER 2: FERRAMENTAS REPROVADAS ($rejCount un.) - CONDENADAS", margin + 14f, rejY, textPaint)

            rejY += 10f
            textPaint.color = COLOR_TEXT_BODY
            textPaint.textSize = 6.8f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            for (line in rejStatementLines) {
                canvas.drawText(line, margin + 14f, rejY, textPaint)
                rejY += 9f
            }

            rejY += 2f
            rejectedTools.forEach { tool ->
                val consumed = drawToolBadge(
                    canvas = canvas,
                    name = "✗  ${tool.name}  (${tool.quantity} un.)",
                    badgeText = "CONDENADO",
                    isApproved = false,
                    x = margin + 14f,
                    y = rejY,
                    width = contentWidth - 28f,
                    subReason = tool.rejectionReason.takeIf { it.isNotBlank() }
                )
                rejY += consumed
            }

            // Divider before footer
            rejY += 1f
            strokePaint.color = 0xFFFECACA.toInt()
            strokePaint.strokeWidth = 0.5f
            canvas.drawLine(margin + 14f, rejY, PAGE_WIDTH - margin - 14f, rejY, strokePaint)
            rejY += 9f

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_RED_DARK
            textPaint.textSize = 7.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("Determinação:", margin + 14f, rejY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Segregação e Descarte Imediato", PAGE_WIDTH - margin - 14f, rejY, textPaint)

            currentY += cardRejHeight + 6f
        } else {
            currentY = drawSectionHeader(canvas, "5. Parecer Técnico Conclusivo", margin + 8f, currentY, contentWidth - 16f)

            val rationale = test.resultRationale.ifEmpty {
                if (isApproved) "Equipamento atendeu integralmente aos ensaios de rigidez e fuga dielétrica conforme NR-10."
                else "Equipamento reprovado por não atingir os requisitos normativos de segurança."
            }

            val cleanNotes = if (test.technicalNotes.contains("Equipamento higienizado em cuba com água desmineralizada", ignoreCase = true)) {
                test.technicalNotes.replace("Equipamento higienizado em cuba com água desmineralizada e aprovado para uso operacional.", "").trim()
            } else test.technicalNotes.trim()

            val notesText = if (parsedTools.isEmpty() && cleanNotes.isNotBlank() && !cleanNotes.contains("FERRAMENTAS_V2")) {
                "Observações: $cleanNotes"
            } else ""

            textPaint.textSize = 7f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            val rationaleLines = breakTextIntoLines(rationale, contentWidth - 28f, textPaint)

            val notesLines = if (notesText.isNotBlank()) {
                textPaint.textSize = 6.8f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                breakTextIntoLines(notesText, contentWidth - 28f, textPaint)
            } else emptyList()

            val useTwoCols = parsedTools.size > 3
            val numRows = if (useTwoCols) (parsedTools.size + 1) / 2 else parsedTools.size
            var toolsBlockH = 0f
            if (parsedTools.isNotEmpty()) {
                toolsBlockH += 18f // Divider + Composition title row
                if (useTwoCols) {
                    for (r in 0 until numRows) {
                        val t1 = parsedTools[r * 2]
                        val t2 = parsedTools.getOrNull(r * 2 + 1)
                        val rowH = if ((!t1.isApproved && t1.rejectionReason.isNotBlank()) || (t2 != null && !t2.isApproved && t2.rejectionReason.isNotBlank())) 20f else 14.5f
                        toolsBlockH += rowH
                    }
                } else {
                    parsedTools.forEach { t ->
                        toolsBlockH += if (!t.isApproved && t.rejectionReason.isNotBlank()) 20f else 14.5f
                    }
                }
            }

            val headerH = 14f
            val rationaleH = (rationaleLines.size * 9.5f) + 10f
            val notesH = if (notesLines.isNotEmpty()) (notesLines.size * 9.0f) + 6f else 0f
            val footerH = 20f

            val cardResultHeight = (headerH + rationaleH + toolsBlockH + notesH + footerH + 4f).coerceAtLeast(54f)

            fillPaint.color = if (isApproved) COLOR_GREEN_BG else COLOR_RED_BG
            canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + cardResultHeight), 6f, 6f, fillPaint)
            strokePaint.color = if (isApproved) COLOR_GREEN else COLOR_RED
            strokePaint.strokeWidth = 1.2f
            canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + cardResultHeight), 6f, 6f, strokePaint)

            var curContentY = currentY + 13f
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = if (isApproved) COLOR_GREEN_DARK else COLOR_RED_DARK
            textPaint.textSize = 9f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("RESULTADO: ${test.result}", margin + 14f, curContentY, textPaint)

            curContentY += 10f
            textPaint.color = COLOR_TEXT_BODY
            textPaint.textSize = 7f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            for (line in rationaleLines) {
                canvas.drawText(line, margin + 14f, curContentY, textPaint)
                curContentY += 9.5f
            }

            if (parsedTools.isNotEmpty()) {
                curContentY += 2f
                // Divider
                strokePaint.color = if (isApproved) 0xFFA7F3D0.toInt() else 0xFFFECACA.toInt()
                strokePaint.strokeWidth = 0.6f
                canvas.drawLine(margin + 14f, curContentY, PAGE_WIDTH - margin - 14f, curContentY, strokePaint)
                curContentY += 8f

                // Header row
                val totalQty = parsedTools.sumOf { it.quantity }
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = COLOR_NAVY
                textPaint.textSize = 7.4f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas.drawText("Composição do Conjunto de Ferramentas ($totalQty un.):", margin + 14f, curContentY, textPaint)

                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.color = COLOR_ORANGE_DARK
                textPaint.textSize = 6.8f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas.drawText("Limite: ${test.leakageCurrentLimit_mA} mA", PAGE_WIDTH - margin - 14f, curContentY, textPaint)
                curContentY += 6f

                if (useTwoCols) {
                    val colGap = 6f
                    val colW = (contentWidth - 28f - colGap) / 2f
                    for (r in 0 until numRows) {
                        val t1 = parsedTools[r * 2]
                        val t2 = parsedTools.getOrNull(r * 2 + 1)
                        val b1 = if (t1.isApproved) "APROVADO" else "REPROVADO"
                        val p1 = if (t1.isApproved) "✓ " else "✗ "
                        val r1 = if (!t1.isApproved && t1.rejectionReason.isNotBlank()) t1.rejectionReason else null

                        val consumed1 = drawToolBadge(
                            canvas = canvas,
                            name = "$p1 ${t1.name}  (${t1.quantity} un.)",
                            badgeText = b1,
                            isApproved = t1.isApproved,
                            x = margin + 14f,
                            y = curContentY,
                            width = colW,
                            subReason = r1
                        )

                        var consumed2 = 0f
                        if (t2 != null) {
                            val b2 = if (t2.isApproved) "APROVADO" else "REPROVADO"
                            val p2 = if (t2.isApproved) "✓ " else "✗ "
                            val r2 = if (!t2.isApproved && t2.rejectionReason.isNotBlank()) t2.rejectionReason else null

                            consumed2 = drawToolBadge(
                                canvas = canvas,
                                name = "$p2 ${t2.name}  (${t2.quantity} un.)",
                                badgeText = b2,
                                isApproved = t2.isApproved,
                                x = margin + 14f + colW + colGap,
                                y = curContentY,
                                width = colW,
                                subReason = r2
                            )
                        }

                        curContentY += maxOf(consumed1, consumed2)
                    }
                } else {
                    parsedTools.forEach { tool ->
                        val badge = if (tool.isApproved) "APROVADO" else "REPROVADO"
                        val prefix = if (tool.isApproved) "✓ " else "✗ "
                        val reason = if (!tool.isApproved && tool.rejectionReason.isNotBlank()) tool.rejectionReason else null
                        val consumed = drawToolBadge(
                            canvas = canvas,
                            name = "$prefix ${tool.name}  (${tool.quantity} un.)",
                            badgeText = badge,
                            isApproved = tool.isApproved,
                            x = margin + 14f,
                            y = curContentY,
                            width = contentWidth - 28f,
                            subReason = reason
                        )
                        curContentY += consumed
                    }
                }
            } else if (notesLines.isNotEmpty()) {
                curContentY += 3f
                textPaint.color = COLOR_NAVY
                textPaint.textSize = 6.8f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                for (line in notesLines) {
                    canvas.drawText(line, margin + 14f, curContentY, textPaint)
                    curContentY += 9.0f
                }
            }

            // Divider before retest line
            curContentY += 2f
            strokePaint.color = if (isApproved) 0xFFA7F3D0.toInt() else 0xFFFECACA.toInt()
            strokePaint.strokeWidth = 0.6f
            canvas.drawLine(margin + 14f, curContentY, PAGE_WIDTH - margin - 14f, curContentY, strokePaint)
            curContentY += 9f

            // Última linha: Próximo Reensaio Obrigatório
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_TEXT_DARK
            textPaint.textSize = 7.6f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("Próximo Reensaio Obrigatório:", margin + 14f, curContentY, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = if (isApproved) COLOR_GREEN_DARK else COLOR_RED_DARK
            textPaint.textSize = 8.2f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(DateUtils.formatDate(test.retestDueDate), PAGE_WIDTH - margin - 14f, curContentY, textPaint)

            currentY += cardResultHeight + 6f
        }

        // ==============================================================
        // 6. ASSINATURAS E AUTENTICIDADE DIGITAL (QR CODE) - FINAL DA PÁGINA 1
        // ==============================================================
        currentY = drawSectionHeader(canvas, "6. Responsabilidade Técnica & Autenticidade Digital", margin + 8f, currentY, contentWidth - 16f)

        val signCardHeight = 56f
        fillPaint.color = Color.WHITE
        canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + signCardHeight), 6f, 6f, fillPaint)
        strokePaint.color = COLOR_BORDER
        strokePaint.strokeWidth = 0.8f
        canvas.drawRoundRect(RectF(margin + 8f, currentY, PAGE_WIDTH - margin - 8f, currentY + signCardHeight), 6f, 6f, strokePaint)

        // Signatures column 1: Analista Executor
        val sig1X = margin + 16f
        if (test.technicianSignature.isNotBlank()) {
            val sigBm = loadSampledBitmap(context, test.technicianSignature, 120, 40)
            if (sigBm != null) {
                val destRect = RectF(sig1X, currentY + 3f, sig1X + 75f, currentY + 23f)
                canvas.drawBitmap(sigBm, null, destRect, null)
            }
        }
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = COLOR_NAVY
        textPaint.textSize = 7.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText(test.technicianName.ifEmpty { "Eng. João Carlos Evaristo Guedes Nunes" }, sig1X, currentY + 32f, textPaint)

        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 6.5f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        // registro (CREA/CFT) do executor só aparece quando informado
        canvas.drawText(test.technicianCftOrCrea.trim().let { if (it.isBlank()) "Analista Executor" else "$it • Analista Executor" }, sig1X, currentY + 42f, textPaint)

        // Signatures column 2: Responsável Técnico (CREA)
        val sig2X = margin + 175f
        if (test.techResponsibleSignature.isNotBlank()) {
            val sigBm = loadSampledBitmap(context, test.techResponsibleSignature, 120, 40)
            if (sigBm != null) {
                val destRect = RectF(sig2X, currentY + 3f, sig2X + 75f, currentY + 23f)
                canvas.drawBitmap(sigBm, null, destRect, null)
            }
        }
        textPaint.color = COLOR_NAVY
        textPaint.textSize = 7.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText(test.techResponsibleName.ifEmpty { "Eng. João Carlos Evaristo Guedes Nunes" }, sig2X, currentY + 32f, textPaint)

        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 6.5f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        canvas.drawText("${test.techResponsibleCrea.ifEmpty { "CREA 24481/D-DF" }} • Responsável Técnico", sig2X, currentY + 42f, textPaint)

        // QR Code Box (Right Side)
        val qrBoxX = PAGE_WIDTH - margin - 172f
        val qrSize = 44
        drawQrCodeToCanvas(canvas, qrBoxX.toInt(), (currentY + 6f).toInt(), qrSize, com.example.util.ValidationPortal.url(test.validationCode))

        // textos ao lado do QR Code: sempre dentro do quadro (borda direita do cartão)
        val qrTextX = qrBoxX + qrSize + 7f
        val qrTextMaxW = (PAGE_WIDTH - margin - 14f) - qrTextX

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = COLOR_NAVY
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        fitTextSize(textPaint, "VALIDAÇÃO DIGITAL", qrTextMaxW, 7f, 5.5f)
        canvas.drawText("VALIDAÇÃO DIGITAL", qrTextX, currentY + 16f, textPaint)

        textPaint.color = COLOR_BLUE
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        fitTextSize(textPaint, test.validationCode, qrTextMaxW, 8f, 5.5f)
        canvas.drawText(test.validationCode, qrTextX, currentY + 27f, textPaint)

        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 6.4f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        canvas.drawText("Acesse o portal para", qrTextX, currentY + 37f, textPaint)
        canvas.drawText("autenticação do laudo", qrTextX, currentY + 46f, textPaint)

        // ==============================================================
        // BOTTOM FOOTER PÁGINA 1
        // ==============================================================
        if (!isP1LetterheadActive) {
            fillPaint.color = COLOR_CARD_BG
            canvas.drawRect(margin, PAGE_HEIGHT - margin - 18f, PAGE_WIDTH - margin, PAGE_HEIGHT - margin, fillPaint)
            strokePaint.color = COLOR_BORDER
            strokePaint.strokeWidth = 0.6f
            canvas.drawLine(margin, PAGE_HEIGHT - margin - 18f, PAGE_WIDTH - margin, PAGE_HEIGHT - margin - 18f, strokePaint)

            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6.5f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("Este documento possui validade técnica e jurídica conforme NR-10 item 10.4.3.1 e normas ABNT NBR / IEC aplicáveis. Emitido eletronicamente.", margin + 8f, PAGE_HEIGHT - margin - 6f, textPaint)
        } else {
            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6.2f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("Validade jurídica NR-10 item 10.4.3.1 • Emissão Eletrônica", margin + 8f, PAGE_HEIGHT - margin - 6f, textPaint)
        }

        textPaint.textAlign = Paint.Align.RIGHT
        if (totalItems > 1) {
            canvas.drawText("Item ${itemIndex + 1}/$totalItems (${test.reportNumber}) • Pág 1 de $totalPages", PAGE_WIDTH - margin - 8f, PAGE_HEIGHT - margin - 6f, textPaint)
        } else {
            canvas.drawText("Página 1 de $totalPages", PAGE_WIDTH - margin - 8f, PAGE_HEIGHT - margin - 6f, textPaint)
        }

        pdfDocument.finishPage(page)

        // ==============================================================
        // SEGUNDA PÁGINA: REGISTRO FOTOGRÁFICO DO ENSAIO (IF PHOTOS EXIST)
        // ==============================================================
        var pagesCount = 1
        if (photosList.isNotEmpty()) {
            val page2Info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, startingPageNumber + 1).create()
            val page2 = pdfDocument.startPage(page2Info)
            val canvas2 = page2.canvas

            // Background
            canvas2.drawColor(Color.WHITE)
            drawLetterheadIfEnabled(canvas2, letterheadBitmap, company, 2, totalPages, PAGE_WIDTH, PAGE_HEIGHT)

            val isP2LetterheadActive = company.useLetterhead && company.letterheadUri.isNotBlank() && letterheadBitmap != null && shouldApplyLetterhead(company, 2, totalPages)

            var p2Y: Float
            if (isP2LetterheadActive) {
                p2Y = margin + 66f

                // Clean Subheader below letterhead header
                fillPaint.color = 0xEEF8FAFC.toInt()
                canvas2.drawRoundRect(RectF(margin + 8f, p2Y, PAGE_WIDTH - margin - 8f, p2Y + 28f), 5f, 5f, fillPaint)
                strokePaint.color = COLOR_BORDER
                strokePaint.strokeWidth = 0.8f
                canvas2.drawRoundRect(RectF(margin + 8f, p2Y, PAGE_WIDTH - margin - 8f, p2Y + 28f), 5f, 5f, strokePaint)

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = COLOR_NAVY
                textPaint.textSize = 9.5f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas2.drawText("ANEXO DE EVIDÊNCIAS FOTOGRÁFICAS", margin + 16f, p2Y + 13f, textPaint)

                textPaint.color = COLOR_TEXT_MUTED
                textPaint.textSize = 6.5f
                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                canvas2.drawText("REGISTRO FOTOGRÁFICO DE CONFORMIDADE E ENSAIO DIELÉTRICO", margin + 16f, p2Y + 23f, textPaint)

                // Right: Laudo badge
                val p2BadgeX = PAGE_WIDTH - margin - 16f
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.color = COLOR_BLUE
                textPaint.textSize = 9.5f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas2.drawText("LAUDO ${test.reportNumber}", p2BadgeX, p2Y + 18f, textPaint)

                p2Y += 28f + 7f
            } else {
                // Outer Page Border
                strokePaint.color = COLOR_NAVY
                strokePaint.strokeWidth = 1.2f
                canvas2.drawRect(margin, margin, PAGE_WIDTH - margin, PAGE_HEIGHT - margin, strokePaint)

                // Page 2 Header
                p2Y = margin + 12f

                // Logo
                val p2LogoW: Float
                if (customLogoBitmap != null) {
                    val maxW = 46f
                    val maxH = 32f
                    val ratio = customLogoBitmap.width.toFloat() / customLogoBitmap.height.toFloat()
                    val (drawW, drawH) = if (ratio > maxW / maxH) {
                        maxW to (maxW / ratio)
                    } else {
                        (maxH * ratio) to maxH
                    }
                    val drawX = margin + 10f
                    val drawY = p2Y + (maxH - drawH) / 2f
                    val filterPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
                    canvas2.drawBitmap(customLogoBitmap, null, RectF(drawX, drawY, drawX + drawW, drawY + drawH), filterPaint)
                    p2LogoW = drawW
                } else {
                    val p2LogoSize = 32f
                    fillPaint.color = COLOR_NAVY
                    canvas2.drawRoundRect(RectF(margin + 10f, p2Y, margin + 10f + p2LogoSize, p2Y + p2LogoSize), 5f, 5f, fillPaint)
                    textPaint.color = Color.WHITE
                    textPaint.textSize = 11f
                    textPaint.typeface = getArialTypeface(Typeface.BOLD)
                    textPaint.textAlign = Paint.Align.CENTER
                    canvas2.drawText("JVM", margin + 10f + (p2LogoSize / 2f), p2Y + 21f, textPaint)
                    p2LogoW = p2LogoSize
                }

                // Company & Header info
                val p2HeaderX = margin + 10f + p2LogoW + 8f
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = COLOR_NAVY
                textPaint.textSize = 10f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas2.drawText(company.name.ifEmpty { "JVM ENGENHARIA & SERVIÇOS TÉCNICOS" }, p2HeaderX, p2Y + 11f, textPaint)

                textPaint.color = COLOR_TEXT_MUTED
                textPaint.textSize = 6.8f
                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                canvas2.drawText("CNPJ: ${company.cnpj.ifEmpty { "29.894.500/0001-04" }} • ${company.creaCompanyRegister.ifEmpty { "CREA-DF 1620-PJ" }}", p2HeaderX, p2Y + 21f, textPaint)
                canvas2.drawText("Laboratório de Ensaio de Rigidez Dielétrica em EPI / EPC / Ferramental", p2HeaderX, p2Y + 30f, textPaint)

                // Report Badge on right
                val p2BadgeW = 140f
                val p2BadgeH = 30f
                val p2BadgeX = PAGE_WIDTH - margin - 10f - p2BadgeW
                fillPaint.color = COLOR_BG_LIGHT
                canvas2.drawRoundRect(RectF(p2BadgeX, p2Y, p2BadgeX + p2BadgeW, p2Y + p2BadgeH), 4f, 4f, fillPaint)
                strokePaint.color = COLOR_BORDER
                strokePaint.strokeWidth = 0.8f
                canvas2.drawRoundRect(RectF(p2BadgeX, p2Y, p2BadgeX + p2BadgeW, p2Y + p2BadgeH), 4f, 4f, strokePaint)

                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = COLOR_NAVY
                textPaint.textSize = 7.2f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas2.drawText("ANEXO DE EVIDÊNCIAS", p2BadgeX + (p2BadgeW / 2f), p2Y + 12f, textPaint)

                textPaint.color = COLOR_BLUE
                textPaint.textSize = 8.5f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas2.drawText("LAUDO ${test.reportNumber}", p2BadgeX + (p2BadgeW / 2f), p2Y + 24f, textPaint)

                p2Y += 32f + 10f

                // Top divider
                fillPaint.color = COLOR_NAVY
                canvas2.drawRect(margin + 8f, p2Y, PAGE_WIDTH - margin - 8f, p2Y + 1.5f, fillPaint)
                p2Y += 7f
            }

            // Equipment Summary Bar on Page 2
            fillPaint.color = COLOR_BG_LIGHT
            canvas2.drawRoundRect(RectF(margin + 8f, p2Y, PAGE_WIDTH - margin - 8f, p2Y + 22f), 4f, 4f, fillPaint)
            strokePaint.color = COLOR_BORDER
            strokePaint.strokeWidth = 0.8f
            canvas2.drawRoundRect(RectF(margin + 8f, p2Y, PAGE_WIDTH - margin - 8f, p2Y + 22f), 4f, 4f, strokePaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_NAVY
            textPaint.textSize = 7.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas2.drawText("Equipamento:", margin + 14f, p2Y + 14f, textPaint)

            textPaint.color = COLOR_TEXT_DARK
            textPaint.textSize = 7.5f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            val eqSummary = "${test.equipmentType} (${test.equipmentClass}) | Tag: ${test.equipmentTag}"
            canvas2.drawText(eqSummary.take(55), margin + 70f, p2Y + 14f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = if (test.result == "APROVADO") COLOR_GREEN_DARK else COLOR_RED_DARK
            textPaint.textSize = 8.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas2.drawText("STATUS: ${test.result}", PAGE_WIDTH - margin - 14f, p2Y + 14f, textPaint)

            p2Y += 28f

            // Section Header: 7. Registro Fotográfico do Ensaio
            p2Y = drawSectionHeader(canvas2, "7. Registro Fotográfico e Evidências do Ensaio Dielétrico", margin + 8f, p2Y, contentWidth - 16f)

            // Photos Grid (Support up to 4 photos in a 2x2 grid with high resolution and rich card frames)
            val maxPhotos = photosList.take(4)
            val numCols = if (maxPhotos.size == 1) 1 else 2
            val numRows = if (maxPhotos.size <= 2) 1 else 2

            val gridW = contentWidth - 16f
            val cardGap = 12f
            val cardW = if (numCols == 1) gridW else (gridW - cardGap) / 2f
            val cardH = if (numRows == 1) 280f else 220f

            for (idx in maxPhotos.indices) {
                val (label, uriStr) = maxPhotos[idx]
                val row = idx / 2
                val col = idx % 2

                val cX = if (numCols == 1) margin + 8f else margin + 8f + col * (cardW + cardGap)
                val cY = p2Y + row * (cardH + cardGap)

                // Card frame
                fillPaint.color = COLOR_BG_LIGHT
                canvas2.drawRoundRect(RectF(cX, cY, cX + cardW, cY + cardH), 6f, 6f, fillPaint)
                strokePaint.color = COLOR_BORDER
                strokePaint.strokeWidth = 1f
                canvas2.drawRoundRect(RectF(cX, cY, cX + cardW, cY + cardH), 6f, 6f, strokePaint)

                // Photo image container
                val imgPadding = 8f
                val imgW = cardW - (2 * imgPadding)
                val imgH = cardH - 52f
                val imgRect = RectF(cX + imgPadding, cY + imgPadding, cX + imgPadding + imgW, cY + imgPadding + imgH)

                val photoBm = loadSampledBitmap(context, uriStr, 400, 300)
                if (photoBm != null) {
                    canvas2.drawBitmap(photoBm, null, imgRect, null)
                } else {
                    fillPaint.color = 0xFF1E293B.toInt()
                    canvas2.drawRoundRect(imgRect, 4f, 4f, fillPaint)
                    textPaint.textAlign = Paint.Align.CENTER
                    textPaint.color = Color.WHITE
                    textPaint.textSize = 10f
                    textPaint.typeface = getArialTypeface(Typeface.BOLD)
                    canvas2.drawText("EVIDÊNCIA FOTOGRÁFICA", imgRect.centerX(), imgRect.centerY() + 3f, textPaint)
                }

                strokePaint.color = COLOR_BORDER
                strokePaint.strokeWidth = 0.8f
                canvas2.drawRoundRect(imgRect, 4f, 4f, strokePaint)

                // Photo badge & caption bar
                val bottomBarY = cY + cardH - 36f

                // Badge Foto 0N
                val badgeText = "Foto ${idx + 1}"
                val badgeW = 42f
                val badgeH = 14f
                val badgeX = cX + imgPadding
                val badgeY = bottomBarY + 3f
                fillPaint.color = COLOR_NAVY
                canvas2.drawRoundRect(RectF(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH), 3f, 3f, fillPaint)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = Color.WHITE
                textPaint.textSize = 7f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas2.drawText(badgeText, badgeX + (badgeW / 2f), badgeY + 10f, textPaint)

                // Title label
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = COLOR_NAVY
                textPaint.textSize = 8.5f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas2.drawText(label.take(28), badgeX + badgeW + 6f, bottomBarY + 13f, textPaint)

                // Subtitle
                textPaint.color = COLOR_TEXT_MUTED
                textPaint.textSize = 6.8f
                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                canvas2.drawText("Registro de ensaio em laboratório • ${DateUtils.formatDate(test.testDate)}", badgeX, bottomBarY + 28f, textPaint)
            }

            p2Y += (numRows * (cardH + cardGap)) + 4f

            // Technical Rastreability & Validation Stamp on Page 2
            val stampH = 50f
            val stampY = PAGE_HEIGHT - margin - 18f - stampH - 8f

            fillPaint.color = Color.WHITE
            canvas2.drawRoundRect(RectF(margin + 8f, stampY, PAGE_WIDTH - margin - 8f, stampY + stampH), 6f, 6f, fillPaint)
            strokePaint.color = COLOR_BORDER
            strokePaint.strokeWidth = 0.8f
            canvas2.drawRoundRect(RectF(margin + 8f, stampY, PAGE_WIDTH - margin - 8f, stampY + stampH), 6f, 6f, strokePaint)

            // Technical note
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_NAVY
            textPaint.textSize = 7.2f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas2.drawText("DECLARAÇÃO DE RASTREABILIDADE TÉCNICA E DIGITAL", margin + 14f, stampY + 13f, textPaint)

            textPaint.color = COLOR_TEXT_BODY
            textPaint.textSize = 6.5f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas2.drawText("As imagens acima constituem registro autêntico e comprobatório dos ensaios executados em conformidade com as normas ABNT NBR e NR-10.", margin + 14f, stampY + 24f, textPaint)

            // Responsible and Validation Code line
            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            val authLine = "Analista: ${test.technicianName.ifEmpty { "Eng. João Carlos" }}${test.technicianCftOrCrea.trim().let { if (it.isBlank()) "" else " ($it)" }}  |  Resp. Técnico: ${test.techResponsibleName.ifEmpty { "Eng. João Carlos" }} (${test.techResponsibleCrea.ifEmpty { "CREA 24481/D-DF" }})"
            // a linha do analista termina antes do código de validação (alinhado à direita)
            val stampAuthSize = textPaint.textSize
            val stampCodeText = "VALIDAÇÃO: ${test.validationCode}"
            val stampCodePaint = Paint(textPaint).apply {
                textAlign = Paint.Align.RIGHT
                color = COLOR_BLUE
                textSize = 7.5f
                typeface = getArialTypeface(Typeface.BOLD)
            }
            val authMaxW = (PAGE_WIDTH - margin - 14f) - stampCodePaint.measureText(stampCodeText) - 12f - (margin + 14f)
            textPaint.textSize = stampAuthSize
            val authFitted = formatWithWordBoundaries(authLine, authMaxW, textPaint)
            canvas2.drawText(authFitted, margin + 14f, stampY + 38f, textPaint)
            canvas2.drawText(stampCodeText, PAGE_WIDTH - margin - 14f, stampY + 38f, stampCodePaint)

            // Footer Página 2
            if (!isP2LetterheadActive) {
                fillPaint.color = COLOR_CARD_BG
                canvas2.drawRect(margin, PAGE_HEIGHT - margin - 18f, PAGE_WIDTH - margin, PAGE_HEIGHT - margin, fillPaint)
                strokePaint.color = COLOR_BORDER
                strokePaint.strokeWidth = 0.6f
                canvas2.drawLine(margin, PAGE_HEIGHT - margin - 18f, PAGE_WIDTH - margin, PAGE_HEIGHT - margin - 18f, strokePaint)

                textPaint.color = COLOR_TEXT_MUTED
                textPaint.textSize = 6.5f
                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                textPaint.textAlign = Paint.Align.LEFT
                canvas2.drawText("Este documento possui validade técnica e jurídica conforme NR-10 item 10.4.3.1 e normas ABNT NBR / IEC aplicáveis. Emitido eletronicamente.", margin + 8f, PAGE_HEIGHT - margin - 6f, textPaint)
            } else {
                textPaint.color = COLOR_TEXT_MUTED
                textPaint.textSize = 6.2f
                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                textPaint.textAlign = Paint.Align.LEFT
                canvas2.drawText("Validade jurídica NR-10 item 10.4.3.1 • Emissão Eletrônica", margin + 8f, PAGE_HEIGHT - margin - 6f, textPaint)
            }

            textPaint.textAlign = Paint.Align.RIGHT
            if (totalItems > 1) {
                canvas2.drawText("Item ${itemIndex + 1}/$totalItems (${test.reportNumber}) • Pág 2 de 2 (Fotos)", PAGE_WIDTH - margin - 8f, PAGE_HEIGHT - margin - 6f, textPaint)
            } else {
                canvas2.drawText("Página 2 de 2", PAGE_WIDTH - margin - 8f, PAGE_HEIGHT - margin - 6f, textPaint)
            }

            pdfDocument.finishPage(page2)
            pagesCount = 2
        }

        return pagesCount
    }

    /**
     * Helper to sanitize any string for use in safe filenames across Android and desktop filesystems.
     * Removes diacritics (accents), invalid filesystem characters, collapses spaces and underscores.
     */
    fun sanitizeForFileName(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        return normalized
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
    }

    /**
     * Builds the standard filename for a Laudo PDF according to required format:
     * <numero_do_laudo>_<empresa>_<nome_do_colaborador>.pdf
     */
    fun buildLaudoPdfFileName(test: TestRecordEntity, company: AppSettingEntity? = null): String {
        val reportNumRaw = test.reportNumber.ifBlank { test.testNumber.ifBlank { "LAUDO" } }
        val companyRaw = if (test.clientName.isNotBlank()) {
            test.clientName
        } else {
            company?.name?.ifBlank { "Empresa" } ?: "Empresa"
        }
        val collaboratorRaw = test.collaboratorName.ifBlank { "Geral" }

        val sanitizedReport = sanitizeForFileName(reportNumRaw)
        val sanitizedCompany = sanitizeForFileName(companyRaw)
        val sanitizedColab = sanitizeForFileName(collaboratorRaw)

        return "${sanitizedReport}_${sanitizedCompany}_${sanitizedColab}.pdf"
    }

    /**
     * Safely copies the generated PDF file to public/external Documents or Downloads directory if accessible,
     * ensuring the user can easily find it in the device's file manager or downloads.
     */
    fun copyToPublicOrExternalDocuments(context: Context, sourceFile: File, fileName: String): File? {
        try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (publicDownloads.exists() || publicDownloads.mkdirs()) {
                val dest = File(publicDownloads, fileName)
                sourceFile.copyTo(dest, overwrite = true)
                return dest
            }
        } catch (_: Exception) {}

        try {
            val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (publicDocs.exists() || publicDocs.mkdirs()) {
                val dest = File(publicDocs, fileName)
                sourceFile.copyTo(dest, overwrite = true)
                return dest
            }
        } catch (_: Exception) {}

        try {
            val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (extDir != null && (extDir.exists() || extDir.mkdirs())) {
                val dest = File(extDir, fileName)
                sourceFile.copyTo(dest, overwrite = true)
                return dest
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Generates a complete, single official A4 Laudo Técnico PDF.
     * File name is saved as: <numero_do_laudo>_<empresa>_<nome_do_colaborador>.pdf
     */
    suspend fun generateLaudoPdf(
        context: Context,
        test: TestRecordEntity,
        company: AppSettingEntity
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        renderLaudoPagesToDocument(
            context = context,
            pdfDocument = pdfDocument,
            test = test,
            company = company,
            startingPageNumber = 1,
            itemIndex = 0,
            totalItems = 1
        )

        // Save PDF to App Storage
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val fileName = buildLaudoPdfFileName(test, company)
        val pdfFile = File(reportsDir, fileName)

        val fos = FileOutputStream(pdfFile)
        pdfDocument.writeTo(fos)
        fos.flush()
        fos.close()
        pdfDocument.close()

        // Also copy to public/external storage so the file is readily available on the device
        copyToPublicOrExternalDocuments(context, pdfFile, fileName)

        pdfFile
    }

    /**
     * Generates a single compiled PDF file containing multiple selected Laudos Técnicos.
     */
    suspend fun generateMultipleLaudosPdf(
        context: Context,
        tests: List<TestRecordEntity>,
        company: AppSettingEntity
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        var currentDocPage = 1
        tests.forEachIndexed { index, test ->
            val pagesAdded = renderLaudoPagesToDocument(
                context = context,
                pdfDocument = pdfDocument,
                test = test,
                company = company,
                startingPageNumber = currentDocPage,
                itemIndex = index,
                totalItems = tests.size
            )
            currentDocPage += pagesAdded
        }

        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = if (tests.size == 1) {
            buildLaudoPdfFileName(tests.first(), company)
        } else {
            val companyRaw = if (!tests.firstOrNull()?.clientName.isNullOrBlank()) {
                tests.first().clientName
            } else {
                company.name.ifBlank { "Empresa" }
            }
            val sanitizedCompany = sanitizeForFileName(companyRaw)
            "Laudos_Agrupados_${tests.size}_itens_${sanitizedCompany}_${timestamp}.pdf"
        }
        val pdfFile = File(reportsDir, fileName)

        val fos = FileOutputStream(pdfFile)
        pdfDocument.writeTo(fos)
        fos.flush()
        fos.close()
        pdfDocument.close()

        copyToPublicOrExternalDocuments(context, pdfFile, fileName)

        pdfFile
    }

    /**
     * Helper to draw a Speedometer gauge on Canvas mirroring SpeedometerGauge composable.
     */
    private fun drawSpeedometerGauge(
        canvas: Canvas,
        title: String,
        value: Double,
        unit: String,
        maxScale: Double,
        limit: Double?,
        isConforming: Boolean,
        isVoltage: Boolean,
        subtext: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
        val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        val strokePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
        val textPaint = Paint().apply { isAntiAlias = true }

        // Card Container
        fillPaint.color = Color.WHITE
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), 6f, 6f, fillPaint)
        strokePaint.color = COLOR_BORDER
        strokePaint.strokeWidth = 0.8f
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), 6f, 6f, strokePaint)

        // Header Top Bar
        fillPaint.color = COLOR_NAVY
        canvas.drawRoundRect(RectF(x, y, x + width, y + 14f), 6f, 6f, fillPaint)
        canvas.drawRect(x, y + 6f, x + width, y + 14f, fillPaint) // Square bottom of top bar

        textPaint.color = Color.WHITE
        textPaint.textSize = 7f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(title.uppercase(Locale.getDefault()), x + width / 2f, y + 10.5f, textPaint)

        // Gauge Arc Measurements
        val cx = x + width / 2f
        val cy = y + height * 0.70f
        val radius = height * 0.36f
        val arcStrokeW = 4.5f

        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // Gray background track arc (180 to 360 deg)
        strokePaint.color = COLOR_BORDER
        strokePaint.strokeWidth = arcStrokeW
        strokePaint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(oval, 180f, 180f, false, strokePaint)

        val fraction = if (maxScale > 0) (value / maxScale).coerceIn(0.0, 1.0).toFloat() else 0f

        if (isVoltage) {
            // Electric Blue arc
            strokePaint.color = COLOR_BLUE
            strokePaint.strokeWidth = arcStrokeW
            canvas.drawArc(oval, 180f, 180f * fraction, false, strokePaint)
        } else {
            // Leakage safe green zone + danger red zone
            val limitFraction = if (limit != null && maxScale > 0) (limit / maxScale).coerceIn(0.0, 1.0).toFloat() else 0.8f
            // Safe green
            strokePaint.color = COLOR_GREEN
            strokePaint.strokeWidth = arcStrokeW
            canvas.drawArc(oval, 180f, 180f * limitFraction, false, strokePaint)
            // Danger red
            strokePaint.color = COLOR_RED
            canvas.drawArc(oval, 180f + (180f * limitFraction), 180f * (1f - limitFraction), false, strokePaint)
        }

        // Pointer Needle
        val needleAngleRad = (180.0 + 180.0 * fraction) * (Math.PI / 180.0)
        val needleLen = radius - 1.5f
        val nx = cx + needleLen * cos(needleAngleRad).toFloat()
        val ny = cy + needleLen * sin(needleAngleRad).toFloat()

        strokePaint.color = COLOR_NAVY
        strokePaint.strokeWidth = 1.6f
        canvas.drawLine(cx, cy, nx, ny, strokePaint)

        // Central Pivot
        fillPaint.color = COLOR_NAVY
        canvas.drawCircle(cx, cy, 2.5f, fillPaint)

        // Digital Readout
        textPaint.color = if (isVoltage) COLOR_BLUE else (if (isConforming) COLOR_GREEN_DARK else COLOR_RED_DARK)
        textPaint.textSize = 8f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("${String.format(Locale.US, "%.1f", value)} $unit", cx, cy - 6f, textPaint)

        // Subtext / Limit Badge
        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 6.2f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        val statusText = if (isVoltage) subtext else "$subtext • ${if (isConforming) "CONFORME" else "NÃO CONF."}"
        canvas.drawText(statusText, cx, y + height - 3.5f, textPaint)
    }

    private fun formatWithWordBoundaries(text: String, maxWidth: Float, paint: Paint): String {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        if (paint.measureText(normalized) <= maxWidth) {
            return normalized
        }
        val words = normalized.split(" ")
        var result = ""
        for (word in words) {
            val test = if (result.isEmpty()) word else "$result $word"
            if (paint.measureText("$test...") > maxWidth) {
                return if (result.isNotEmpty()) "$result..." else {
                    var truncated = ""
                    for (char in word) {
                        if (paint.measureText("$truncated$char...") > maxWidth) break
                        truncated += char
                    }
                    if (truncated.isNotEmpty()) "$truncated..." else "$word..."
                }
            }
            result = test
        }
        return if (result.isNotEmpty()) "$result..." else normalized
    }

    private fun drawToolBadge(
        canvas: Canvas,
        name: String,
        badgeText: String,
        isApproved: Boolean,
        x: Float,
        y: Float,
        width: Float,
        subReason: String? = null
    ): Float {
        val fillPaint = Paint().apply { isAntiAlias = true; isDither = true; style = Paint.Style.FILL }
        val strokePaint = Paint().apply { isAntiAlias = true; isDither = true; style = Paint.Style.STROKE }
        val textPaint = Paint().apply {
            isAntiAlias = true
            isSubpixelText = true
            isLinearText = true
            isFilterBitmap = true
            isDither = true
            letterSpacing = 0.0f
        }

        val hasSubReason = !subReason.isNullOrBlank()
        val rowHeight = if (hasSubReason) 18f else 12f
        val badgeRect = RectF(x, y, x + width, y + rowHeight)

        fillPaint.color = if (isApproved) Color.WHITE else 0xFFFEF2F2.toInt()
        canvas.drawRoundRect(badgeRect, 3f, 3f, fillPaint)
        strokePaint.color = if (isApproved) 0xFFCBD5E1.toInt() else 0xFFFCA5A5.toInt()
        strokePaint.strokeWidth = 0.6f
        canvas.drawRoundRect(badgeRect, 3f, 3f, strokePaint)

        // Status Tag on right
        val tagWidth = if (width < 270f) 52f else 62f
        val tagHeight = 8f
        val tagRect = RectF(x + width - tagWidth - 3f, y + 2f, x + width - 3f, y + 2f + tagHeight)
        fillPaint.color = if (isApproved) COLOR_GREEN_BG else COLOR_RED_BG
        canvas.drawRoundRect(tagRect, 2f, 2f, fillPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = if (isApproved) COLOR_GREEN_DARK else COLOR_RED_DARK
        textPaint.textSize = 5.8f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText(badgeText, tagRect.centerX(), tagRect.centerY() + 2f, textPaint)

        // Tool name & quantity (keeps full word separation with high-definition rendering)
        val maxNameWidth = width - tagWidth - 7f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = if (isApproved) COLOR_NAVY else COLOR_RED_DARK
        textPaint.textSize = if (width < 270f) 6.4f else 6.8f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        val formattedName = formatWithWordBoundaries(name, maxNameWidth, textPaint)
        canvas.drawText(formattedName, x + 4f, y + 8.2f, textPaint)

        if (hasSubReason) {
            textPaint.color = COLOR_RED_DARK
            textPaint.textSize = 5.6f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            val subText = "Motivo: $subReason"
            val formattedSub = formatWithWordBoundaries(subText, width - 8f, textPaint)
            canvas.drawText(formattedSub, x + 4f, y + 15.2f, textPaint)
        }

        return rowHeight + 2f
    }

    /**
     * Seção 3 do laudo do detector de tensão por indução — mesmo conteúdo do laudo da
     * plataforma web (pdfGenerator.ts): "Classe: 1 kV a 50 kV", tabela DISTÂNCIA (cm) |
     * TENSÃO (kV) (linha vermelha "Não detectou"), gráfico à direita na altura da tabela
     * e a observação. Devolve o Y seguinte.
     */
    private fun drawInductionSection(canvas: Canvas, test: TestRecordEntity, x: Float, top0: Float, width: Float): Float {
        val points = com.example.domain.InductionDetector.normalize(com.example.domain.InductionDetector.parse(test.inductionPointsJson))
        val dark = COLOR_TEXT_DARK
        val tblW = width * 0.34f
        val colW = tblW / 2f
        val rowH = 10.2f
        val top = top0 + 2f
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f; color = dark }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            hinting = Paint.HINTING_OFF
            textAlign = Paint.Align.CENTER
            color = dark
        }

        // "Classe: 1 kV a 50 kV"
        text.typeface = getArialTypeface(Typeface.BOLD)
        text.textSize = 7.6f
        canvas.drawRect(x, top, x + tblW, top + 12f, line)
        canvas.drawText("Classe: ${com.example.domain.InductionDetector.DETECTOR_CLASS}", x + tblW / 2f, top + 8.3f, text)

        // cabeçalho
        var ty = top + 17f
        fill.color = 0xFFE2E8F0.toInt()
        canvas.drawRect(x, ty, x + tblW, ty + rowH + 1f, fill)
        canvas.drawRect(x, ty, x + tblW, ty + rowH + 1f, line)
        canvas.drawLine(x + colW, ty, x + colW, ty + rowH + 1f, line)
        canvas.drawText("DISTÂNCIA (cm)", x + colW / 2f, ty + 7.6f, text)
        canvas.drawText("TENSÃO (kV)", x + colW + colW / 2f, ty + 7.6f, text)
        ty += rowH + 1f

        // linhas
        text.typeface = getArialTypeface(Typeface.NORMAL)
        text.textSize = 7.3f
        points.forEach { p ->
            val missed = !p.detected
            if (missed) {
                fill.color = 0xFFFEE2E2.toInt()
                canvas.drawRect(x, ty, x + tblW, ty + rowH, fill)
            }
            canvas.drawRect(x, ty, x + tblW, ty + rowH, line)
            canvas.drawLine(x + colW, ty, x + colW, ty + rowH, line)
            text.color = if (missed) 0xFF991B1B.toInt() else dark
            val dist = if (missed) "Não detectou" else p.distance_cm?.let { com.example.domain.InductionDetector.fmtBr1(it) } ?: "—"
            canvas.drawText(dist, x + colW / 2f, ty + 7.1f, text)
            text.color = dark
            canvas.drawText(com.example.domain.InductionDetector.fmtBr1(p.voltage_kV), x + colW + colW / 2f, ty + 7.1f, text)
            ty += rowH
        }

        // gráfico à direita, na altura da tabela, mantendo a proporção 560 × 300
        val chartAreaX = x + tblW + 11f
        val chartAreaW = width - tblW - 11f
        val chartAreaH = ty - top
        var chartW = chartAreaW
        var chartH = chartW * InductionChartRenderer.BASE_H / InductionChartRenderer.BASE_W
        if (chartH > chartAreaH) {
            chartH = chartAreaH
            chartW = chartH * InductionChartRenderer.BASE_W / InductionChartRenderer.BASE_H
        }
        InductionChartRenderer.draw(canvas, chartAreaX + (chartAreaW - chartW) / 2f, top, chartW, chartH, points)

        // observação
        var y = ty + 6f
        val notDetected = points.filter { !it.detected }.map { "${com.example.domain.InductionDetector.fmtBr1(it.voltage_kV)} kV" }
        val obsOk = test.result == "APROVADO"
        val obsText = when {
            obsOk -> "OBS: RESULTADOS SATISFATÓRIOS CONFORME ESPECIFICAÇÕES DO FABRICANTE."
            notDetected.isNotEmpty() -> "OBS: O DETECTOR NÃO SINALIZOU EM ${notDetected.joinToString("; ")} — NÃO ATENDE ÀS ESPECIFICAÇÕES DO FABRICANTE."
            else -> "OBS: EQUIPAMENTO REPROVADO — VER PARECER TÉCNICO."
        }
        canvas.drawRect(x, y, x + width, y + 13f, line)
        text.color = if (obsOk) COLOR_BLUE else 0xFFB91C1C.toInt()
        text.typeface = getArialTypeface(Typeface.NORMAL)
        fitTextSize(text, obsText, width - 8f, 7.6f, 5.5f)
        canvas.drawText(obsText, x + width / 2f, y + 9f, text)
        y += 13f + 6f
        return y
    }

    /** Ajusta o tamanho da fonte (de [maxSize] até [minSize]) para o texto caber em [maxWidth]. */
    private fun fitTextSize(paint: Paint, text: String, maxWidth: Float, maxSize: Float, minSize: Float) {
        paint.textSize = maxSize
        while (paint.measureText(text) > maxWidth && paint.textSize > minSize) {
            paint.textSize -= 0.25f
        }
    }

    private fun drawInfoField(
        canvas: Canvas,
        label: String,
        value: String,
        x: Float,
        y: Float,
        width: Float,
        isMono: Boolean = false,
        isHighlight: Boolean = false,
        tabOffset: Float = -1f
    ) {
        val textPaint = Paint().apply {
            isAntiAlias = true
            isSubpixelText = true
            isLinearText = true
            // avanços sem arredondamento (evita letras encostadas em fontes pequenas no PDF)
            isLinearText = true
            hinting = Paint.HINTING_OFF
            isFilterBitmap = true
            isDither = true
            letterSpacing = 0.0f
        }
        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 7.0f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText(label, x, y, textPaint)

        // o valor nunca começa em cima do rótulo, mesmo com rótulos longos
        val labelEnd = x + textPaint.measureText(label) + 5f
        val valueStartX = if (tabOffset > 0f) maxOf(x + tabOffset, labelEnd) else labelEnd
        val maxValueWidth = (x + width) - valueStartX

        textPaint.color = if (isHighlight) COLOR_BLUE else COLOR_TEXT_DARK
        textPaint.textSize = 7.8f
        textPaint.typeface = if (isMono) Typeface.MONOSPACE else getArialTypeface(Typeface.BOLD)
        val formattedValue = formatWithWordBoundaries(value, maxValueWidth, textPaint)
        canvas.drawText(formattedValue, valueStartX, y, textPaint)
    }

    private fun loadSampledBitmap(context: Context, pathOrUri: String, reqWidth: Int, reqHeight: Int): Bitmap? =
        ImageOrientation.upright(context, pathOrUri, loadSampledBitmapRaw(context, pathOrUri, reqWidth, reqHeight))

    private fun loadSampledBitmapRaw(context: Context, pathOrUri: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val file = File(pathOrUri)
            if (file.exists()) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
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

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
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

    private fun loadResourceBitmap(context: Context, resId: Int, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resId, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeResource(context.resources, resId, options)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Renders a highly polished, summarized technical methodology procedure card with an image,
     * sequenced steps, normative parameters box, and approval/rejection criteria badges.
     */
    private fun drawMethodologyProcedureCard(
        canvas: Canvas,
        cardLeft: Float,
        cardTop: Float,
        cardRight: Float,
        cardHeight: Float,
        title: String,
        bitmap: Bitmap?,
        params: List<String>,
        steps: List<Pair<String, String>>,
        approvalText: String,
        rejectionText: String
    ) {
        val bgPaint = Paint().apply {
            color = 0xFFF8FAFC.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = 0xFFCBD5E1.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            isAntiAlias = true
        }
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardTop + cardHeight)
        canvas.drawRoundRect(cardRect, 3f, 3f, bgPaint)
        canvas.drawRoundRect(cardRect, 3f, 3f, borderPaint)

        // Card Header Banner
        val headerH = 13.5f
        val headerPaint = Paint().apply {
            color = 0xFF0F2D4A.toInt() // Deep Navy
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(cardLeft, cardTop, cardRight, cardTop + headerH), 3f, 3f, headerPaint)
        canvas.drawRect(cardLeft, cardTop + 5f, cardRight, cardTop + headerH, headerPaint)

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 6.2f
            typeface = getArialTypeface(Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(title, cardLeft + 7f, cardTop + 9.5f, titlePaint)

        // Left Column: Image Box + Technical Parameters
        val isCardTall = cardHeight >= 200f
        val imgBoxLeft = cardLeft + 6f
        val imgBoxTop = cardTop + headerH + 3f
        val imgBoxWidth = if (isCardTall) 115f else 105f
        val imgBoxHeight = if (isCardTall) 120f else 68f

        if (bitmap != null) {
            // Keep aspect ratio centered in destination box with clean border
            val bW = bitmap.width.toFloat()
            val bH = bitmap.height.toFloat()
            val scale = minOf(imgBoxWidth / bW, imgBoxHeight / bH)
            val fitW = bW * scale
            val fitH = bH * scale
            val fitLeft = imgBoxLeft + (imgBoxWidth - fitW) / 2f
            val fitTop = imgBoxTop + (imgBoxHeight - fitH) / 2f

            val phBg = Paint().apply { color = 0xFFF1F5F9.toInt(); style = Paint.Style.FILL }
            canvas.drawRoundRect(RectF(imgBoxLeft, imgBoxTop, imgBoxLeft + imgBoxWidth, imgBoxTop + imgBoxHeight), 2f, 2f, phBg)

            val src = Rect(0, 0, bitmap.width, bitmap.height)
            val dst = RectF(fitLeft, fitTop, fitLeft + fitW, fitTop + fitH)
            canvas.drawBitmap(bitmap, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawRoundRect(RectF(imgBoxLeft, imgBoxTop, imgBoxLeft + imgBoxWidth, imgBoxTop + imgBoxHeight), 2f, 2f, borderPaint)
        } else {
            val phPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
            val boxRect = RectF(imgBoxLeft, imgBoxTop, imgBoxLeft + imgBoxWidth, imgBoxTop + imgBoxHeight)
            canvas.drawRoundRect(boxRect, 2f, 2f, phPaint)
            canvas.drawRoundRect(boxRect, 2f, 2f, borderPaint)
        }

        // Parameters Box under Image
        val paramBoxTop = imgBoxTop + imgBoxHeight + 3f
        val paramBoxHeight = (cardTop + cardHeight - 4f) - paramBoxTop
        val paramBgPaint = Paint().apply {
            color = 0xFFF1F5F9.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val paramRect = RectF(imgBoxLeft, paramBoxTop, imgBoxLeft + imgBoxWidth, paramBoxTop + paramBoxHeight)
        canvas.drawRoundRect(paramRect, 2f, 2f, paramBgPaint)
        canvas.drawRoundRect(paramRect, 2f, 2f, borderPaint)

        val paramHeaderPaint = Paint().apply {
            color = 0xFF0F2D4A.toInt()
            textSize = if (isCardTall) 5.6f else 5.0f
            typeface = getArialTypeface(Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("PARÂMETROS DE ENSAIO:", imgBoxLeft + 4f, paramBoxTop + (if (isCardTall) 9f else 7.5f), paramHeaderPaint)

        val paramTextPaint = Paint().apply {
            color = 0xFF334155.toInt()
            textSize = if (isCardTall) 5.0f else 4.4f
            typeface = getArialTypeface(Typeface.NORMAL)
            isAntiAlias = true
        }
        val pLineStep = if (isCardTall) 10.5f else 7.2f
        var pY = paramBoxTop + (if (isCardTall) 20f else 15f)
        params.forEach { p ->
            canvas.drawText(p, imgBoxLeft + 4f, pY, paramTextPaint)
            pY += pLineStep
        }

        // Right Column: Sequenced Steps (1..4) + Criteria Row
        val rightColLeft = imgBoxLeft + imgBoxWidth + 7f
        val rightColWidth = cardRight - rightColLeft - 6f
        var stepY = cardTop + headerH + 4f

        val stepNumPaint = Paint().apply {
            color = 0xFF0284C7.toInt() // Blue Accent
            textSize = if (isCardTall) 6.0f else 5.2f
            typeface = getArialTypeface(Typeface.BOLD)
            isAntiAlias = true
        }
        val stepTitlePaint = Paint().apply {
            color = 0xFF0F2D4A.toInt()
            textSize = if (isCardTall) 6.0f else 5.2f
            typeface = getArialTypeface(Typeface.BOLD)
            isAntiAlias = true
        }
        val stepDescPaint = Paint().apply {
            color = 0xFF334155.toInt()
            textSize = if (isCardTall) 5.2f else 4.6f
            typeface = getArialTypeface(Typeface.NORMAL)
            isAntiAlias = true
        }

        val stepSpacing = if (isCardTall) 8.5f else 5.8f
        val maxStepLines = if (isCardTall) 3 else 2

        steps.forEach { (stTitle, stDesc) ->
            val prefix = stTitle.takeWhile { it != '.' } + "."
            val afterPrefix = stTitle.removePrefix(prefix).trim()
            val prefW = stepNumPaint.measureText("$prefix ")
            canvas.drawText(prefix, rightColLeft, stepY + (if (isCardTall) 7.5f else 6.0f), stepNumPaint)
            canvas.drawText(afterPrefix, rightColLeft + prefW, stepY + (if (isCardTall) 7.5f else 6.0f), stepTitlePaint)

            val lines = breakTextIntoLines(stDesc, rightColWidth, stepDescPaint)
            var lineY = stepY + (if (isCardTall) 15f else 12.5f)
            lines.take(maxStepLines).forEach { l ->
                canvas.drawText(l, rightColLeft, lineY, stepDescPaint)
                lineY += stepSpacing
            }
            stepY += (if (isCardTall) 15f else 12.5f) + (lines.take(maxStepLines).size * stepSpacing) + (if (isCardTall) 3f else 1.5f)
        }

        // Bottom Criteria Row (Approval Green & Rejection Red Boxes side by side)
        val critH = if (isCardTall) 38f else 28f
        val critTop = cardTop + cardHeight - critH - 4f
        val critColW = (rightColWidth - 6f) / 2f

        // Approval Box (Green)
        val appRect = RectF(rightColLeft, critTop, rightColLeft + critColW, critTop + critH)
        val appBgPaint = Paint().apply { color = 0xFFF0FDF4.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
        val appBorderPaint = Paint().apply { color = 0xFF86EFAC.toInt(); style = Paint.Style.STROKE; strokeWidth = 0.5f; isAntiAlias = true }
        canvas.drawRoundRect(appRect, 2f, 2f, appBgPaint)
        canvas.drawRoundRect(appRect, 2f, 2f, appBorderPaint)

        val appHeadPaint = Paint().apply {
            color = 0xFF15803D.toInt()
            textSize = if (isCardTall) 5.6f else 5.0f
            typeface = getArialTypeface(Typeface.BOLD)
            isAntiAlias = true
        }
        val appDescPaint = Paint().apply {
            color = 0xFF166534.toInt()
            textSize = if (isCardTall) 4.8f else 4.2f
            typeface = getArialTypeface(Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("✓ CRITÉRIO DE APROVAÇÃO", rightColLeft + 4f, critTop + (if (isCardTall) 8.5f else 7.0f), appHeadPaint)
        val appLines = breakTextIntoLines(approvalText, critColW - 8f, appDescPaint)
        var appLineY = critTop + (if (isCardTall) 16f else 13.5f)
        appLines.take(3).forEach { l ->
            canvas.drawText(l, rightColLeft + 4f, appLineY, appDescPaint)
            appLineY += (if (isCardTall) 6.8f else 5.5f)
        }

        // Rejection Box (Red)
        val rejLeft = rightColLeft + critColW + 6f
        val rejRect = RectF(rejLeft, critTop, rejLeft + critColW, critTop + critH)
        val rejBgPaint = Paint().apply { color = 0xFFFEF2F2.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
        val rejBorderPaint = Paint().apply { color = 0xFFFECACA.toInt(); style = Paint.Style.STROKE; strokeWidth = 0.5f; isAntiAlias = true }
        canvas.drawRoundRect(rejRect, 2f, 2f, rejBgPaint)
        canvas.drawRoundRect(rejRect, 2f, 2f, rejBorderPaint)

        val rejHeadPaint = Paint().apply {
            color = 0xFFB91C1C.toInt()
            textSize = if (isCardTall) 5.6f else 5.0f
            typeface = getArialTypeface(Typeface.BOLD)
            isAntiAlias = true
        }
        val rejDescPaint = Paint().apply {
            color = 0xFF991B1B.toInt()
            textSize = if (isCardTall) 4.8f else 4.2f
            typeface = getArialTypeface(Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("✗ CRITÉRIO DE REPROVAÇÃO", rejLeft + 4f, critTop + (if (isCardTall) 8.5f else 7.0f), rejHeadPaint)
        val rejLines = breakTextIntoLines(rejectionText, critColW - 8f, rejDescPaint)
        var rejLineY = critTop + (if (isCardTall) 16f else 13.5f)
        rejLines.take(3).forEach { l ->
            canvas.drawText(l, rejLeft + 4f, rejLineY, rejDescPaint)
            rejLineY += (if (isCardTall) 6.8f else 5.5f)
        }
    }

    /**
     * Renders the updated, modular procedure card for insulating gloves (Item 5.1),
     * featuring dual-column layout: photo with observation card on the left,
     * sequenced circular-badged steps on the right, testing parameters and stacked criteria below.
     */
    private fun drawModernGloveProcedureCard(
        canvas: Canvas,
        cardLeft: Float,
        cardTop: Float,
        cardRight: Float,
        cardHeight: Float,
        titleLeft: String,
        normRight: String,
        bitmap: Bitmap?,
        photoTitle: String,
        photoSubtitle: String,
        setupTitle: String,
        setupDesc: String,
        stepsTitle: String,
        steps: List<Pair<String, String>>,
        paramsTitle: String,
        params: List<String>,
        approvalText: String,
        rejectionText: String
    ) {
        val isEnlarged = cardHeight >= 350f
        val cardW = cardRight - cardLeft

        val bgPaint = Paint().apply {
            color = 0xFFF8FAFC.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = 0xFFCBD5E1.toInt()
            style = Paint.Style.STROKE
            strokeWidth = if (isEnlarged) 0.8f else 0.6f
            isAntiAlias = true
        }
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardTop + cardHeight)
        canvas.drawRoundRect(cardRect, if (isEnlarged) 6f else 4f, if (isEnlarged) 6f else 4f, bgPaint)
        canvas.drawRoundRect(cardRect, if (isEnlarged) 6f else 4f, if (isEnlarged) 6f else 4f, borderPaint)

        // Card Header Banner (Deep Navy)
        val headerH = if (isEnlarged) 22f else 14f
        val headerPaint = Paint().apply {
            color = 0xFF0F2D4A.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(cardLeft, cardTop, cardRight, cardTop + headerH), if (isEnlarged) 6f else 4f, if (isEnlarged) 6f else 4f, headerPaint)
        canvas.drawRect(cardLeft, cardTop + (if (isEnlarged) 8f else 5f), cardRight, cardTop + headerH, headerPaint)

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = if (isEnlarged) 9.0f else 6.4f
            typeface = getArialTypeface(Typeface.BOLD)
            letterSpacing = 0.0f
            isAntiAlias = true
        }
        canvas.drawText(titleLeft, cardLeft + (if (isEnlarged) 12f else 8f), cardTop + (if (isEnlarged) 14.8f else 9.8f), titlePaint)

        val normPaint = Paint().apply {
            color = 0xFFE2E8F0.toInt()
            textSize = if (isEnlarged) 7.8f else 5.6f
            typeface = getArialTypeface(Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0.0f
            isAntiAlias = true
        }
        canvas.drawText(normRight, cardRight - (if (isEnlarged) 12f else 8f), cardTop + (if (isEnlarged) 14.8f else 9.8f), normPaint)

        val whitePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
        val subBorderPaint = Paint().apply { color = 0xFFCBD5E1.toInt(); style = Paint.Style.STROKE; strokeWidth = if (isEnlarged) 0.6f else 0.5f; isAntiAlias = true }

        val pad = if (isEnlarged) 7f else 5f
        val innerLeft = cardLeft + pad
        val innerRight = cardRight - pad
        val innerW = innerRight - innerLeft

        if (isEnlarged) {
            // =========================================================================
            // FULL-PAGE LAYOUT: FULL-WIDTH PHOTO FRAME ("QUADRO INTEIRO NA PÁGINA")
            // =========================================================================
            val photoFrameTop = cardTop + headerH + 6f
            val photoFrameH = 268f
            val photoFrameRect = RectF(innerLeft, photoFrameTop, innerRight, photoFrameTop + photoFrameH)
            canvas.drawRoundRect(photoFrameRect, 5f, 5f, whitePaint)
            canvas.drawRoundRect(photoFrameRect, 5f, 5f, subBorderPaint)

            // Photo Frame Header
            val photoHeadPaint = Paint().apply {
                color = 0xFF0F2D4A.toInt()
                textSize = 8.2f
                typeface = getArialTypeface(Typeface.BOLD)
                letterSpacing = 0.0f
                isAntiAlias = true
            }
            canvas.drawText(photoTitle, innerLeft + 10f, photoFrameTop + 13f, photoHeadPaint)

            val photoSubPaint = Paint().apply {
                color = 0xFF64748B.toInt()
                textSize = 6.4f
                typeface = getArialTypeface(Typeface.NORMAL)
                letterSpacing = 0.0f
                isAntiAlias = true
            }
            canvas.drawText(photoSubtitle, innerLeft + 10f, photoFrameTop + 22.5f, photoSubPaint)

            // Standard badge on top right of photo frame
            val badgePaint = Paint().apply {
                color = 0xFF4338CA.toInt()
                textSize = 6.2f
                typeface = getArialTypeface(Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
                letterSpacing = 0.0f
                isAntiAlias = true
            }
            canvas.drawText("NBR 16295 • ASTM D120", innerRight - 10f, photoFrameTop + 14f, badgePaint)

            // High-Resolution Photo Display - SPANS THE FULL WIDTH OF THE CARD
            val imgPad = 6f
            val imgLeft = innerLeft + imgPad
            val imgRight = innerRight - imgPad
            val imgW = imgRight - imgLeft
            val imgTop = photoFrameTop + 27f
            val imgH = 208f

            // Area for photo/illustration left clean and blank in white
            if (bitmap != null) {
                val src = Rect(0, 0, bitmap.width, bitmap.height)
                val dst = RectF(imgLeft, imgTop, imgRight, imgTop + imgH)
                val imgPaint = Paint().apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                    isDither = true
                }
                canvas.drawBitmap(bitmap, src, dst, imgPaint)
                canvas.drawRoundRect(dst, 3f, 3f, subBorderPaint)
            } else {
                val blankPaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val blankRect = RectF(imgLeft, imgTop, imgRight, imgTop + imgH)
                canvas.drawRoundRect(blankRect, 3f, 3f, blankPaint)
                canvas.drawRoundRect(blankRect, 3f, 3f, subBorderPaint)
            }

            // Bottom Inset Note inside Photo Frame
            val noteTop = imgTop + imgH + 4.5f
            val noteH = 22f
            val noteRect = RectF(imgLeft, noteTop, imgRight, noteTop + noteH)
            val noteBgPaint = Paint().apply { color = 0xFFF0F9FF.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
            val noteBorderPaint = Paint().apply { color = 0xFFBAE6FD.toInt(); style = Paint.Style.STROKE; strokeWidth = 0.6f; isAntiAlias = true }
            canvas.drawRoundRect(noteRect, 3f, 3f, noteBgPaint)
            canvas.drawRoundRect(noteRect, 3f, 3f, noteBorderPaint)

            val noteHeadPaint = Paint().apply {
                color = 0xFF0284C7.toInt()
                textSize = 6.4f
                typeface = getArialTypeface(Typeface.BOLD)
                letterSpacing = 0.0f
                isAntiAlias = true
            }
            canvas.drawText(setupTitle.uppercase(), imgLeft + 8f, noteTop + 9.5f, noteHeadPaint)

            val noteDescPaint = Paint().apply {
                color = 0xFF334155.toInt()
                textSize = 5.8f
                typeface = getArialTypeface(Typeface.NORMAL)
                letterSpacing = 0.0f
                isAntiAlias = true
            }
            val noteText = "Montagem em cuba acrílica com água e recuo de borda seca • Eletrodos de alta tensão aplicados via barra de cobre com isoladores pedestal • Medição de fuga via Fluke 369FC BLE • Hipot digital BYQ5kVA 50kV."
            val noteLines = breakTextIntoLines(noteText, imgW - 16f, noteDescPaint)
            var nY = noteTop + 17f
            noteLines.take(1).forEach { l ->
                canvas.drawText(l, imgLeft + 8f, nY, noteDescPaint)
            }

            // =========================================================================
            // LOWER SECTION: 3 HORIZONTAL COLUMNS (STEPS, PARAMETERS, CRITERIA)
            // =========================================================================
            val lowerTop = photoFrameTop + photoFrameH + 6f
            val lowerH = cardTop + cardHeight - lowerTop - 6f
            val gap = 6f
            val availW = innerW - (2 * gap)
            val col1W = availW * 0.38f
            val col2W = availW * 0.28f
            val col3W = availW - col1W - col2W

            val col1Left = innerLeft
            val col2Left = col1Left + col1W + gap
            val col3Left = col2Left + col2W + gap

            // COLUMN 1: ETAPAS DO PROCEDIMENTO (NBR 16295)
            val c1Rect = RectF(col1Left, lowerTop, col1Left + col1W, lowerTop + lowerH)
            canvas.drawRoundRect(c1Rect, 4f, 4f, whitePaint)
            canvas.drawRoundRect(c1Rect, 4f, 4f, subBorderPaint)

            val colHeadPaint = Paint().apply {
                color = 0xFF0F2D4A.toInt()
                textSize = 7.6f
                typeface = getArialTypeface(Typeface.BOLD)
                letterSpacing = 0.0f
                isAntiAlias = true
            }
            canvas.drawText("ETAPAS DO PROCEDIMENTO (NBR 16295)", col1Left + 10f, lowerTop + 14f, colHeadPaint)

            val badgeBgPaint = Paint().apply { color = 0xFF0F2D4A.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
            val badgeNumPaint = Paint().apply {
                color = Color.WHITE
                textSize = 7.0f
                typeface = getArialTypeface(Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val stepHeadPaint = Paint().apply {
                color = 0xFF0F2D4A.toInt()
                textSize = 6.8f
                typeface = getArialTypeface(Typeface.BOLD)
                letterSpacing = 0.0f
                isAntiAlias = true
            }
            val stepDescPaint = Paint().apply {
                color = 0xFF334155.toInt()
                textSize = 5.8f
                typeface = getArialTypeface(Typeface.NORMAL)
                letterSpacing = 0.0f
                isAntiAlias = true
            }

            val stepSlotH = (lowerH - 22f) / 4f
            steps.forEachIndexed { i, (stTitle, stDesc) ->
                val sTop = lowerTop + 22f + (i * stepSlotH)
                val badgeRadius = 7.5f
                val cx = col1Left + 16f
                val cy = sTop + 11f
                canvas.drawCircle(cx, cy, badgeRadius, badgeBgPaint)
                canvas.drawText("${i + 1}", cx, cy + 2.5f, badgeNumPaint)

                val textLeft = col1Left + 28f
                canvas.drawText(stTitle, textLeft, sTop + 9f, stepHeadPaint)

                val textMaxW = col1W - 36f
                val lines = breakTextIntoLines(stDesc, textMaxW, stepDescPaint)
                var lY = sTop + 18f
                lines.take(2).forEach { l ->
                    canvas.drawText(l, textLeft, lY, stepDescPaint)
                    lY += 7.8f
                }

                if (i < 3) {
                    val divY = sTop + stepSlotH - 2f
                    canvas.drawLine(col1Left + 10f, divY, col1Left + col1W - 10f, divY, subBorderPaint)
                }
            }

            // COLUMN 2: PARÂMETROS DE ENSAIO
            val c2Rect = RectF(col2Left, lowerTop, col2Left + col2W, lowerTop + lowerH)
            val paramBgPaint = Paint().apply { color = 0xFFF1F5F9.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
            canvas.drawRoundRect(c2Rect, 4f, 4f, paramBgPaint)
            canvas.drawRoundRect(c2Rect, 4f, 4f, subBorderPaint)

            canvas.drawText("PARÂMETROS DE ENSAIO", col2Left + 10f, lowerTop + 14f, colHeadPaint)

            val paramTextPaint = Paint().apply {
                color = 0xFF334155.toInt()
                textSize = 6.2f
                typeface = getArialTypeface(Typeface.NORMAL)
                letterSpacing = 0.0f
                isAntiAlias = true
            }
            var pY = lowerTop + 28f
            val paramLineStep = 15.2f
            params.forEach { p ->
                canvas.drawText(p, col2Left + 10f, pY, paramTextPaint)
                pY += paramLineStep
            }

            // Additional calibrated instrumentation specs in Col 2
            val instPaint = Paint().apply {
                color = 0xFF0284C7.toInt()
                textSize = 5.8f
                typeface = getArialTypeface(Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("• Hipot: BYQ5kVA (50 kV / 5 kVA)", col2Left + 10f, pY, instPaint)
            pY += 12f
            canvas.drawText("• Alicate Fuga: Fluke 369FC BLE", col2Left + 10f, pY, instPaint)

            // COLUMN 3: CRITÉRIOS DE AVALIAÇÃO (STACKED GREEN & RED BOXES)
            val critBoxH = (lowerH - 6f) / 2f

            // 1. Approval Box (Green)
            val appRect = RectF(col3Left, lowerTop, col3Left + col3W, lowerTop + critBoxH)
            val appBgPaint = Paint().apply { color = 0xFFF0FDF4.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
            val appBorderPaint = Paint().apply { color = 0xFF86EFAC.toInt(); style = Paint.Style.STROKE; strokeWidth = 0.6f; isAntiAlias = true }
            canvas.drawRoundRect(appRect, 4f, 4f, appBgPaint)
            canvas.drawRoundRect(appRect, 4f, 4f, appBorderPaint)

            val appHeadPaint = Paint().apply {
                color = 0xFF15803D.toInt()
                textSize = 7.4f
                typeface = getArialTypeface(Typeface.BOLD)
                letterSpacing = 0.0f
                isAntiAlias = true
            }
            canvas.drawText("✓ CRITÉRIO DE APROVAÇÃO", col3Left + 10f, lowerTop + 14f, appHeadPaint)

            val appDescPaint = Paint().apply {
                color = 0xFF166534.toInt()
                textSize = 6.0f
                typeface = getArialTypeface(Typeface.NORMAL)
                letterSpacing = 0.0f
                isAntiAlias = true
            }
            val appLines = breakTextIntoLines(approvalText, col3W - 20f, appDescPaint)
            var aY = lowerTop + 26f
            appLines.take(4).forEach { l ->
                canvas.drawText(l, col3Left + 10f, aY, appDescPaint)
                aY += 9.5f
            }

            // 2. Rejection Box (Red)
            val rejTop = lowerTop + critBoxH + 6f
            val rejRect = RectF(col3Left, rejTop, col3Left + col3W, rejTop + critBoxH)
            val rejBgPaint = Paint().apply { color = 0xFFFEF2F2.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
            val rejBorderPaint = Paint().apply { color = 0xFFFECACA.toInt(); style = Paint.Style.STROKE; strokeWidth = 0.6f; isAntiAlias = true }
            canvas.drawRoundRect(rejRect, 4f, 4f, rejBgPaint)
            canvas.drawRoundRect(rejRect, 4f, 4f, rejBorderPaint)

            val rejHeadPaint = Paint().apply {
                color = 0xFFB91C1C.toInt()
                textSize = 7.4f
                typeface = getArialTypeface(Typeface.BOLD)
                letterSpacing = 0.0f
                isAntiAlias = true
            }
            canvas.drawText("✗ CRITÉRIO DE REPROVAÇÃO", col3Left + 10f, rejTop + 14f, rejHeadPaint)

            val rejDescPaint = Paint().apply {
                color = 0xFF991B1B.toInt()
                textSize = 6.0f
                typeface = getArialTypeface(Typeface.NORMAL)
                letterSpacing = 0.0f
                isAntiAlias = true
            }
            val rejLines = breakTextIntoLines(rejectionText, col3W - 20f, rejDescPaint)
            var rY = rejTop + 26f
            rejLines.take(4).forEach { l ->
                canvas.drawText(l, col3Left + 10f, rY, rejDescPaint)
                rY += 9.5f
            }

        } else {
            // =========================================================================
            // COMPACT LAYOUT (WHEN MULTIPLE METHODOLOGY CARDS CO-EXIST ON PAGE)
            // =========================================================================
            val upperTop = cardTop + headerH + 3.5f
            val photoH = if (cardHeight >= 200f) 105f else 65f
            val photoRect = RectF(innerLeft, upperTop, innerRight, upperTop + photoH)
            canvas.drawRoundRect(photoRect, 3f, 3f, whitePaint)
            canvas.drawRoundRect(photoRect, 3f, 3f, subBorderPaint)

            val colHeadPaint = Paint().apply {
                color = 0xFF0F2D4A.toInt()
                textSize = 5.8f
                typeface = getArialTypeface(Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText(photoTitle, innerLeft + 8f, upperTop + 8.5f, colHeadPaint)

            val imgLeft = innerLeft + 6f
            val imgRight = innerRight - 6f
            val imgTop = upperTop + 12f
            val imgH = photoH - 16f

            if (bitmap != null) {
                val src = Rect(0, 0, bitmap.width, bitmap.height)
                val dst = RectF(imgLeft, imgTop, imgRight, imgTop + imgH)
                val imgPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true; isDither = true }
                canvas.drawBitmap(bitmap, src, dst, imgPaint)
                canvas.drawRoundRect(dst, 2f, 2f, subBorderPaint)
            } else {
                val blankPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
                val dst = RectF(imgLeft, imgTop, imgRight, imgTop + imgH)
                canvas.drawRoundRect(dst, 2f, 2f, blankPaint)
                canvas.drawRoundRect(dst, 2f, 2f, subBorderPaint)
            }

            // Lower 2 columns
            val lowerTop = upperTop + photoH + 4f
            val lowerH = cardTop + cardHeight - lowerTop - 4f
            val gap = 5f
            val halfW = (innerW - gap) / 2f

            // Left: Steps
            val sRect = RectF(innerLeft, lowerTop, innerLeft + halfW, lowerTop + lowerH)
            canvas.drawRoundRect(sRect, 3f, 3f, whitePaint)
            canvas.drawRoundRect(sRect, 3f, 3f, subBorderPaint)
            canvas.drawText(stepsTitle, innerLeft + 6f, lowerTop + 7.5f, colHeadPaint)

            val stepHeadPaint = Paint().apply { color = 0xFF0F2D4A.toInt(); textSize = 4.8f; typeface = getArialTypeface(Typeface.BOLD); isAntiAlias = true }
            val stepDescPaint = Paint().apply { color = 0xFF334155.toInt(); textSize = 4.2f; typeface = getArialTypeface(Typeface.NORMAL); isAntiAlias = true }
            val slotH = (lowerH - 12f) / 4f
            steps.forEachIndexed { i, (stTitle, stDesc) ->
                val sY = lowerTop + 12f + (i * slotH)
                canvas.drawText("${i + 1}. $stTitle", innerLeft + 8f, sY + 4f, stepHeadPaint)
                val lines = breakTextIntoLines(stDesc, halfW - 14f, stepDescPaint)
                lines.take(1).forEach { l ->
                    canvas.drawText(l, innerLeft + 8f, sY + 9f, stepDescPaint)
                }
            }

            // Right: Parameters & Approval
            val pRect = RectF(innerLeft + halfW + gap, lowerTop, innerRight, lowerTop + lowerH)
            val paramBgPaint = Paint().apply { color = 0xFFF1F5F9.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
            canvas.drawRoundRect(pRect, 3f, 3f, paramBgPaint)
            canvas.drawRoundRect(pRect, 3f, 3f, subBorderPaint)
            canvas.drawText(paramsTitle, innerLeft + halfW + gap + 6f, lowerTop + 7.5f, colHeadPaint)

            val paramTextPaint = Paint().apply { color = 0xFF334155.toInt(); textSize = 4.2f; typeface = getArialTypeface(Typeface.NORMAL); isAntiAlias = true }
            var pY = lowerTop + 14.5f
            params.take(4).forEach { p ->
                canvas.drawText(p, innerLeft + halfW + gap + 6f, pY, paramTextPaint)
                pY += 6.5f
            }
        }
    }

    /**
     * Renders the updated methodology procedure card for Insulated Manual Tools 1000V (Item 5.3),
     * faithfully reproducing the exact layout and infographic from the normative standard (ABNT NBR 9699 / IEC 60900).
     */
    private fun drawModernToolProcedureCard(
        canvas: Canvas,
        cardLeft: Float,
        cardTop: Float,
        cardRight: Float,
        cardHeight: Float,
        bitmap: Bitmap?
    ) {
        val isEnlarged = cardHeight >= 200f
        val bgPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = 0xFFCBD5E1.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            isAntiAlias = true
        }
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardTop + cardHeight)
        canvas.drawRoundRect(cardRect, 4f, 4f, bgPaint)
        canvas.drawRoundRect(cardRect, 4f, 4f, borderPaint)

        // Card Header Banner (Deep Navy)
        val headerH = if (isEnlarged) 16f else 13.5f
        val headerPaint = Paint().apply {
            color = 0xFF0F2D4A.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(cardLeft, cardTop, cardRight, cardTop + headerH), 4f, 4f, headerPaint)
        canvas.drawRect(cardLeft, cardTop + 5f, cardRight, cardTop + headerH, headerPaint)

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = if (isEnlarged) 7.2f else 6.2f
            typeface = getArialTypeface(Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("▸ 5.3 PROCEDIMENTO: FERRAMENTAS MANUAIS ISOLADAS 1000V", cardLeft + 8f, cardTop + (if (isEnlarged) 11.2f else 9.5f), titlePaint)

        val normPaint = Paint().apply {
            color = 0xFFE2E8F0.toInt()
            textSize = if (isEnlarged) 6.4f else 5.4f
            typeface = getArialTypeface(Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText("ABNT NBR 9699 • ABNT NBR IEC 60900", cardRight - 8f, cardTop + (if (isEnlarged) 11.2f else 9.5f), normPaint)

        // Content Area inside card
        val pad = 4f
        val innerLeft = cardLeft + pad
        val innerRight = cardRight - pad
        val innerTop = cardTop + headerH + pad
        val innerBottom = cardTop + cardHeight - pad
        val innerW = innerRight - innerLeft
        val innerH = innerBottom - innerTop

        if (bitmap != null) {
            val bW = bitmap.width.toFloat()
            val bH = bitmap.height.toFloat()
            val scale = minOf(innerW / bW, innerH / bH)
            val fitW = bW * scale
            val fitH = bH * scale
            val fitLeft = innerLeft + (innerW - fitW) / 2f
            val fitTop = innerTop + (innerH - fitH) / 2f

            val src = Rect(0, 0, bitmap.width, bitmap.height)
            val dst = RectF(fitLeft, fitTop, fitLeft + fitW, fitTop + fitH)
            val imgPaint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
            }
            canvas.drawBitmap(bitmap, src, dst, imgPaint)
            val imgBorderPaint = Paint().apply {
                color = 0xFFE2E8F0.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
                isAntiAlias = true
            }
            canvas.drawRoundRect(dst, 2f, 2f, imgBorderPaint)
        } else {
            val blankPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val borderP = Paint().apply {
                color = 0xFFE2E8F0.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
                isAntiAlias = true
            }
            val dst = RectF(innerLeft + 2f, innerTop + 2f, innerRight - 2f, innerBottom - 2f)
            canvas.drawRoundRect(dst, 3f, 3f, blankPaint)
            canvas.drawRoundRect(dst, 3f, 3f, borderP)
        }
    }


    /**
     * Renders a prestigious Certificate of Electrical Compliance into a PdfDocument page.
     */
    private fun renderCertificadoToDocument(
        context: Context,
        pdfDocument: PdfDocument,
        test: TestRecordEntity,
        company: AppSettingEntity,
        pageNumber: Int = 1,
        itemIndex: Int = 0,
        totalItems: Int = 1
    ) {
        initTypeface(context)
        val letterheadBitmap = if (company.useLetterhead && company.letterheadUri.isNotBlank()) {
            LetterheadStorageHelper.loadLetterheadBitmap(context, company.letterheadUri, PAGE_WIDTH * 2, PAGE_HEIGHT * 2)
        } else null

        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawColor(Color.WHITE)
        drawLetterheadIfEnabled(canvas, letterheadBitmap, company, pageNumber, totalItems, PAGE_WIDTH, PAGE_HEIGHT)

        val strokePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
        val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        val textPaint = Paint().apply { isAntiAlias = true }

        val margin = 24f
        val isLetterheadActive = company.useLetterhead && company.letterheadUri.isNotBlank() && letterheadBitmap != null && shouldApplyLetterhead(company, pageNumber, totalItems)

        var currentY: Float
        if (!isLetterheadActive) {
            // Double Guilloche Style Frame
        strokePaint.color = COLOR_NAVY
        strokePaint.strokeWidth = 3f
        canvas.drawRect(margin, margin, PAGE_WIDTH - margin, PAGE_HEIGHT - margin, strokePaint)

        strokePaint.color = COLOR_ORANGE
        strokePaint.strokeWidth = 1.2f
        canvas.drawRect(margin + 5f, margin + 5f, PAGE_WIDTH - margin - 5f, PAGE_HEIGHT - margin - 5f, strokePaint)

        strokePaint.color = COLOR_BORDER
        strokePaint.strokeWidth = 0.5f
        canvas.drawRect(margin + 8f, margin + 8f, PAGE_WIDTH - margin - 8f, PAGE_HEIGHT - margin - 8f, strokePaint)

        // Company Header Banner
        fillPaint.color = COLOR_NAVY
        canvas.drawRect(margin + 8f, margin + 8f, PAGE_WIDTH - margin - 8f, margin + 70f, fillPaint)

        val customLogoBitmap = if (company.companyLogoUri.isNotBlank()) {
            loadSampledBitmap(context, company.companyLogoUri, 200, 100)
        } else null

        val headerTextCenterX = if (customLogoBitmap != null) {
            val logoMaxW = 65f
            val logoMaxH = 48f
            val ratio = customLogoBitmap.width.toFloat() / customLogoBitmap.height.toFloat()
            val (drawW, drawH) = if (ratio > logoMaxW / logoMaxH) {
                logoMaxW to (logoMaxW / ratio)
            } else {
                (logoMaxH * ratio) to logoMaxH
            }
            val logoX = margin + 18f
            val logoY = margin + 10f + (58f - drawH) / 2f
            val filterPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
            canvas.drawBitmap(customLogoBitmap, null, RectF(logoX, logoY, logoX + drawW, logoY + drawH), filterPaint)
            (margin + 18f + drawW + (PAGE_WIDTH - margin - 8f)) / 2f
        } else {
            PAGE_WIDTH / 2f
        }

        textPaint.color = Color.WHITE
        textPaint.textSize = 14f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(company.name.ifEmpty { "JVM ENGENHARIA" }.uppercase(), headerTextCenterX, margin + 28f, textPaint)

        textPaint.textSize = 8.5f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        canvas.drawText("CNPJ: ${company.cnpj.ifEmpty { "29.894.500/0001-04" }} | CREA: ${company.creaCompanyRegister.ifEmpty { "CREA-DF 1620-PJ" }}", headerTextCenterX, margin + 42f, textPaint)
        canvas.drawText("Laboratório de Ensaio de Rigidez Dielétrica em Equipamentos de Proteção e Ferramentas", headerTextCenterX, margin + 54f, textPaint)

            currentY = margin + 105f
        } else {
            // Letterhead model replaces default frame and company header
            currentY = margin + 74f
        }

        // Title
        textPaint.color = COLOR_NAVY
        textPaint.textSize = 16f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        // Título configurado em Parâmetros de Emissão (o mesmo da web); reduz a fonte se não couber
        val certTitle = com.example.util.CompanyLabInfo.certificateTitle(company)
        while (textPaint.measureText(certTitle) > PAGE_WIDTH - 2 * margin && textPaint.textSize > 10f) {
            textPaint.textSize -= 0.5f
        }
        canvas.drawText(certTitle, PAGE_WIDTH / 2f, currentY, textPaint)
        textPaint.textSize = 16f

        currentY += 18f
        textPaint.color = COLOR_ORANGE
        textPaint.textSize = 11f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        val certNumber = test.certificateNumber.ifEmpty { "CERT-${test.reportNumber}" }
        canvas.drawText("Nº DO CERTIFICADO: $certNumber", PAGE_WIDTH / 2f, currentY, textPaint)

        // Certificate Statement
        currentY += 30f
        textPaint.color = COLOR_TEXT_DARK
        textPaint.textSize = 9.5f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        textPaint.textAlign = Paint.Align.LEFT
        val statement = "Certificamos para os devidos fins de direito e cumprimento das exigências da Norma Regulamentadora NR-10 (Segurança em Instalações e Serviços em Eletricidade), item 10.4.3.1 e 10.7.8, que o equipamento abaixo especificado foi submetido a rigorosos ensaios elétricos de rigidez dielétrica e inspeção visual em nosso laboratório, tendo sido considerado APROVADO e CONFORME para uso operacional nas tensões limites especificadas."
        drawMultilineText(canvas, statement, margin + 25f, currentY, PAGE_WIDTH - 2 * margin - 50f, 13f, textPaint)

        // Equipment Spec Table
        currentY += 68f
        fillPaint.color = COLOR_CARD_BG
        canvas.drawRoundRect(RectF(margin + 20f, currentY, PAGE_WIDTH - margin - 20f, currentY + 160f), 8f, 8f, fillPaint)
        strokePaint.color = COLOR_BORDER
        strokePaint.strokeWidth = 1f
        canvas.drawRoundRect(RectF(margin + 20f, currentY, PAGE_WIDTH - margin - 20f, currentY + 160f), 8f, 8f, strokePaint)

        val tableX = margin + 30f
        val col2X = tableX + 252f
        val colW = 246f

        drawCertField(canvas, "CLIENTE / DESTINATÁRIO:", test.clientName.ifEmpty { "Consumidor Geral" }, tableX, currentY + 20f, colW, tabOffset = 118f)
        drawCertField(canvas, "LOCAL DO ENSAIO:", test.location.ifEmpty { "Laboratório Móvel JVM" }, col2X, currentY + 20f, colW, tabOffset = 125f)

        drawCertField(canvas, "IDENTIFICAÇÃO (TAG):", test.equipmentTag, tableX, currentY + 45f, colW, isHighlight = true, tabOffset = 118f)
        drawCertField(canvas, "TIPO DE EQUIPAMENTO:", test.equipmentType.replace("_", " ").uppercase(), col2X, currentY + 45f, colW, isHighlight = true, tabOffset = 125f)

        drawCertField(canvas, "CLASSE DIELÉTRICA:", "${test.equipmentClass} (${getMaxUseVoltage(test.equipmentClass)})", tableX, currentY + 70f, colW, tabOffset = 118f)
        drawCertField(canvas, "CERTIFICADO DE APROVAÇÃO (CA):", test.equipmentCa.ifEmpty { "N/A" }, col2X, currentY + 70f, colW, tabOffset = 125f)

        drawCertField(canvas, "TENSÃO DE ENSAIO APLICADA:", "${test.appliedVoltage_kV} kV ${test.voltageType} / 60 Hz", tableX, currentY + 95f, colW, isHighlight = true, tabOffset = 118f)
        drawCertField(canvas, "NORMA TÉCNICA APLICADA:", test.normCode.ifEmpty { "ABNT NBR 16295" }, col2X, currentY + 95f, colW, tabOffset = 125f)

        drawCertField(canvas, "CORRENTE DE FUGA MEDIDA:", "${test.measuredLeakageCurrent_mA} ${test.currentUnit} (Limite: ${test.leakageCurrentLimit_mA} ${test.currentUnit})", tableX, currentY + 120f, colW, tabOffset = 118f)
        drawCertField(canvas, "USUÁRIO / COLABORADOR:", "${test.collaboratorName.ifEmpty { "Geral" }} (${test.collaboratorSector.ifEmpty { "Manutenção" }})", col2X, currentY + 120f, colW, tabOffset = 125f)

        drawCertField(canvas, "DATA DO ENSAIO:", DateUtils.formatDate(test.testDate), tableX, currentY + 145f, colW, tabOffset = 118f)
        drawCertField(canvas, "VALIDADE / REENSAIO:", if (test.result == "APROVADO") DateUtils.formatDate(test.retestDueDate) else "", col2X, currentY + 145f, colW, isHighlight = true, customColor = COLOR_GREEN, tabOffset = 125f)

        // Large Approval Stamp
        currentY += 175f
        fillPaint.color = 0xFFDCFCE7.toInt()
        canvas.drawRoundRect(RectF(PAGE_WIDTH / 2f - 140f, currentY, PAGE_WIDTH / 2f + 140f, currentY + 36f), 6f, 6f, fillPaint)
        strokePaint.color = COLOR_GREEN
        strokePaint.strokeWidth = 1.5f
        canvas.drawRoundRect(RectF(PAGE_WIDTH / 2f - 140f, currentY, PAGE_WIDTH / 2f + 140f, currentY + 36f), 6f, 6f, strokePaint)

        textPaint.color = COLOR_GREEN
        textPaint.textSize = 13f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("✓ CONFORME - APROVADO PARA USO", PAGE_WIDTH / 2f, currentY + 23f, textPaint)

        // Signatures and QR Code
        currentY += 60f
        val sigWidth = 160f
        val sig1X = margin + 35f

        // Technician signature image
        if (test.technicianSignature.isNotBlank()) {
            val sigBm = loadSampledBitmap(context, test.technicianSignature, 140, 50)
            if (sigBm != null) {
                val destRect = RectF(sig1X + (sigWidth - 100f)/2f, currentY + 12f, sig1X + (sigWidth + 100f)/2f, currentY + 38f)
                canvas.drawBitmap(sigBm, null, destRect, null)
            }
        }

        strokePaint.color = COLOR_NAVY
        strokePaint.strokeWidth = 1f
        canvas.drawLine(sig1X, currentY + 40f, sig1X + sigWidth, currentY + 40f, strokePaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = COLOR_NAVY
        textPaint.textSize = 9f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText(test.technicianName.ifEmpty { "Eng. João Carlos Evaristo Guedes Nunes" }, sig1X + sigWidth / 2f, currentY + 52f, textPaint)

        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 8f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        canvas.drawText(test.technicianCftOrCrea.trim().let { if (it.isBlank()) "Técnico Executor" else "Técnico Executor ($it)" }, sig1X + sigWidth / 2f, currentY + 63f, textPaint)

        val sig2X = margin + 215f

        // RT signature image
        if (test.techResponsibleSignature.isNotBlank()) {
            val sigBm = loadSampledBitmap(context, test.techResponsibleSignature, 140, 50)
            if (sigBm != null) {
                val destRect = RectF(sig2X + (sigWidth - 100f)/2f, currentY + 12f, sig2X + (sigWidth + 100f)/2f, currentY + 38f)
                canvas.drawBitmap(sigBm, null, destRect, null)
            }
        }

        canvas.drawLine(sig2X, currentY + 40f, sig2X + sigWidth, currentY + 40f, strokePaint)

        textPaint.color = COLOR_NAVY
        textPaint.textSize = 9f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText(test.techResponsibleName.ifEmpty { "Eng. João Carlos Evaristo Guedes Nunes" }, sig2X + sigWidth / 2f, currentY + 52f, textPaint)

        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 8f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        canvas.drawText("Engenheiro Eletricista (${test.techResponsibleCrea.ifEmpty { "CREA 24481/D-DF" }})", sig2X + sigWidth / 2f, currentY + 63f, textPaint)

        // QR Code Validation
        val qrX = (PAGE_WIDTH - margin - 85f).toInt()
        val qrSize = 58
        // QR Code com o link do Portal de Validação (antes levava só o código)
        drawQrCodeToCanvas(canvas, qrX, (currentY + 10f).toInt(), qrSize, com.example.util.ValidationPortal.url(test.validationCode))

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = COLOR_NAVY
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        // centralizado sob o QR Code sem passar da margem direita da página
        val codeCenterX = qrX + qrSize / 2f
        val codeMaxW = 2f * ((PAGE_WIDTH - margin - 4f) - codeCenterX)
        val codeText = "CÓD: ${test.validationCode}"
        fitTextSize(textPaint, codeText, codeMaxW, 7.5f, 5.5f)
        canvas.drawText(codeText, codeCenterX, currentY + 76f, textPaint)

        // Bottom Footer
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 7.5f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        if (totalItems > 1) {
            canvas.drawText("A autenticidade deste certificado pode ser verificada escaneando o QR Code acima. Válido até ${DateUtils.formatDate(test.retestDueDate)}.  •  Certificado ${itemIndex + 1} de $totalItems", PAGE_WIDTH / 2f, PAGE_HEIGHT - margin - 15f, textPaint)
        } else {
            canvas.drawText("A autenticidade deste certificado pode ser verificada escaneando o QR Code acima. Válido até ${DateUtils.formatDate(test.retestDueDate)}.", PAGE_WIDTH / 2f, PAGE_HEIGHT - margin - 15f, textPaint)
        }

        pdfDocument.finishPage(page)
    }

    /**
     * Generates a prestigious Certificate of Electrical Compliance PDF for a single test.
     */
    suspend fun generateCertificadoPdf(
        context: Context,
        test: TestRecordEntity,
        company: AppSettingEntity
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        renderCertificadoToDocument(context, pdfDocument, test, company, pageNumber = 1, itemIndex = 0, totalItems = 1)

        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val sanitizedTag = test.equipmentTag.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Certificado_${test.reportNumber}_${sanitizedTag}.pdf"
        val pdfFile = File(reportsDir, fileName)

        val fos = FileOutputStream(pdfFile)
        pdfDocument.writeTo(fos)
        fos.flush()
        fos.close()
        pdfDocument.close()

        pdfFile
    }

    /**
     * Generates a single compiled PDF containing multiple prestigious Certificates of Electrical Compliance.
     */
    suspend fun generateMultipleCertificadosPdf(
        context: Context,
        tests: List<TestRecordEntity>,
        company: AppSettingEntity
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        tests.forEachIndexed { index, test ->
            renderCertificadoToDocument(
                context = context,
                pdfDocument = pdfDocument,
                test = test,
                company = company,
                pageNumber = index + 1,
                itemIndex = index,
                totalItems = tests.size
            )
        }

        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Certificados_Agrupados_${tests.size}_itens_${timestamp}.pdf"
        val pdfFile = File(reportsDir, fileName)

        val fos = FileOutputStream(pdfFile)
        pdfDocument.writeTo(fos)
        fos.flush()
        fos.close()
        pdfDocument.close()

        pdfFile
    }

    data class RelatorioNormaItem(
        val itemNum: String,
        val codigo: String,
        val denominacao: String,
        val equipamentos: String,
        val escopo: String
    )

    val PADRAO_NORMAS_RELATORIO = listOf(
        RelatorioNormaItem(
            "2.1",
            "NR-10 (Portaria MTE nº 3.214/78)",
            "Segurança em Instalações e Serviços em Eletricidade",
            "Todos os EPIs, EPCs e Ferramental Isolado",
            "Itens 10.4.3.1, 10.5.1 e 10.7.8: Obrigatoriedade de aterramento temporário, ensaios periódicos de isolação e emissão de laudo técnico formal."
        ),
        RelatorioNormaItem(
            "2.2",
            "ABNT NBR IEC 61230 / ASTM F855",
            "Trabalhos sob Tensão — Equipamentos Portáteis para Aterramento ou Aterramento e Curto-Circuito",
            "Conjuntos de Aterramento Temporário (EPC - BT/MT/AT)",
            "Ensaio de rigidez dielétrica nos bastões isolantes (NBR 16613), integridade de cabos extraflexíveis, grampos e continuidade elétrica."
        ),
        RelatorioNormaItem(
            "2.3",
            "ABNT NBR 8221 / ANSI Z89.1",
            "Capacetes de Segurança para Uso na Indústria — Classe B (Classe Dielétrica 2)",
            "Capacetes de Segurança Classe B (Classe 2 - Até 20.000V)",
            "Ensaio de rigidez dielétrica a 20 kV CA durante 3 minutos após imersão em água por 24h. Limite de fuga ≤ 9,0 mA e sem perfuração até 30 kV."
        ),
        RelatorioNormaItem(
            "2.4",
            "ABNT NBR 16604 / IEC 60900",
            "Ferramentas Manuais para Trabalhos em Tensão até 1 000 V CA e 1 500 V CC",
            "Conjuntos e Ferramentas Manuais Isoladas",
            "Inspeção visual da isolação polimérica e ensaio de rigidez dielétrica a 10.000 V CA por 3 minutos."
        ),
        RelatorioNormaItem(
            "2.5",
            "ABNT NBR 9699 / IEC 60900",
            "Ferramentas Manuais Isoladas (1000V CA / 1500V CC)",
            "Conjunto de Ferramentas Isoladas 1000V",
            "Ensaio dielétrico a 10.000V CA por 3 minutos (180s) após acondicionamento em banho-maria conforme ABNT NBR 9699 e IEC 60900. O limite máximo de corrente de fuga do ensaio corresponde à quantidade de ferramentas ensaiadas multiplicada por 1,0 mA."
        ),
        RelatorioNormaItem(
            "2.6",
            "ABNT NBR 16295 Tabela 4 / IEC 60903",
            "Trabalhos em Linha Viva — Luvas de Material Isolante (Tabela 4)",
            "Luvas Isolantes de Borracha (Classes 00, 0, 1, 2, 3 e 4)",
            "Tabela 4: Ensaios de prova e rigidez dielétrica em CA/CC com limites específicos de corrente de fuga."
        ),
        RelatorioNormaItem(
            "2.7",
            "ABNT NBR 11854",
            "Bastões de Manobra, Varas Telescópicas e Bastões de Resgate",
            "Bastões de Manobra e Varas Telescópicas",
            "Ensaio dielétrico a 50 kV CA por 60 segundos conforme ABNT NBR 11854. Corrente de fuga máxima pelo diâmetro do bastão: Ø32mm: 5 µA; Ø38mm: 6 µA; Ø51mm: 8 µA; Ø64mm: 10 µA (a 100 kV: 10, 12, 15 e 20 µA). Critério de aprovação: sem perfuração, disrupção ou aquecimento anormal e corrente de fuga dentro do limite do diâmetro."
        )
    )

    /**
     * Generates the official Simplified Technical Report & Test Tabulation PDF (A4 Landscape)
     * organized and grouped by collaborator, strictly mirroring the official Brazilian standard document.
     */
    suspend fun generateRelatorioSimplificadoPdf(
        context: Context,
        tests: List<TestRecordEntity>,
        client: ClientEntity?,
        company: AppSettingEntity,
        serviceOrder: ServiceOrderEntity? = null,
        reportNumber: String = ""
    ): File = withContext(Dispatchers.IO) {
        initTypeface(context)
        val pdfDocument = PdfDocument()

        val strokePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
        val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        val textPaint = Paint().apply { isAntiAlias = true }

        val margin = 16f
        val contentWidth = LANDSCAPE_PAGE_WIDTH - 2 * margin

        // Sort tests primarily by collaborator name, secondarily by tag
        val sortedTests = tests.sortedWith(
            compareBy(
                { it.collaboratorName.ifBlank { "ZZZ_Sem_Colaborador" } },
                { it.equipmentTag }
            )
        )

        val approvedCount = sortedTests.count { it.result == "APROVADO" }
        val rejectedCount = sortedTests.count { it.result != "APROVADO" }
        val approvalPercent = if (sortedTests.isNotEmpty()) {
            String.format(Locale.US, "%.1f", (approvedCount.toDouble() / sortedTests.size) * 100)
        } else "100.0"

        val effectiveReportNum = reportNumber.ifEmpty {
            val seq = if (sortedTests.isNotEmpty()) sortedTests.first().testNumber.takeLast(4) else "0003"
            val ym = SimpleDateFormat("yyMM", Locale.getDefault()).format(Date())
            "REL-TEC-$ym-$seq"
        }

        val effectiveOsNum = serviceOrder?.osNumber ?: sortedTests.firstOrNull()?.serviceOrderNumber?.ifEmpty { "OS-2026-0001" } ?: "OS-2026-0001"
        val effectiveArt = serviceOrder?.artNumber ?: sortedTests.firstOrNull()?.artNumber?.ifEmpty { "1320260109871" } ?: "1320260109871"

        val letterheadBitmap = if (company.useLetterhead && company.letterheadUri.isNotBlank()) {
            LetterheadStorageHelper.loadLetterheadBitmap(context, company.letterheadUri, LANDSCAPE_PAGE_WIDTH * 2, LANDSCAPE_PAGE_HEIGHT * 2)
        } else null

        // Determine pagination
        // Page 1 has Header, Section 1, Section 2 (Norms), Section 3 Title, KPI Summary Table, Table Header, and can fit up to 6 rows if signature is on page 1, or 5 rows if split.
        val totalRows = sortedTests.size
        val rowsOnPage1 = if (totalRows <= 6) totalRows else 5
        val remainingRows = if (totalRows > 6) sortedTests.drop(5) else emptyList()
        val rowsPerPage2Onwards = 16
        val numSubsequentPages = if (remainingRows.isEmpty()) 0 else ((remainingRows.size + rowsPerPage2Onwards - 1) / rowsPerPage2Onwards)
        val totalPages = 1 + numSubsequentPages

        // ---------------- PAGE 1 ----------------
        var currentPageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(LANDSCAPE_PAGE_WIDTH, LANDSCAPE_PAGE_HEIGHT, currentPageNum).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        canvas.drawColor(Color.WHITE)
        drawLetterheadIfEnabled(canvas, letterheadBitmap, company, 1, totalPages, LANDSCAPE_PAGE_WIDTH, LANDSCAPE_PAGE_HEIGHT)

        val isP1LetterheadActive = company.useLetterhead && company.letterheadUri.isNotBlank() && letterheadBitmap != null && shouldApplyLetterhead(company, 1, totalPages)

        var curY: Float
        if (!isP1LetterheadActive) {
            // Outer Border
            strokePaint.color = 0xFFCBD5E1.toInt()
            strokePaint.strokeWidth = 1f
            canvas.drawRect(margin, margin, LANDSCAPE_PAGE_WIDTH - margin, LANDSCAPE_PAGE_HEIGHT - margin, strokePaint)

            // Top Header Banner
            fillPaint.color = COLOR_NAVY
            canvas.drawRect(margin, margin, LANDSCAPE_PAGE_WIDTH - margin, margin + 50f, fillPaint)

            // Logo Block (Left)
            val simpLogoRect = RectF(margin + 8f, margin + 6f, margin + 82f, margin + 44f)
            val customLogoBitmap = if (company.companyLogoUri.isNotBlank()) {
                loadSampledBitmap(context, company.companyLogoUri, 200, 100)
            } else null

            if (customLogoBitmap != null) {
                val maxW = simpLogoRect.width()
                val maxH = simpLogoRect.height()
                val ratio = customLogoBitmap.width.toFloat() / customLogoBitmap.height.toFloat()
                val (drawW, drawH) = if (ratio > maxW / maxH) {
                    maxW to (maxW / ratio)
                } else {
                    (maxH * ratio) to maxH
                }
                val drawX = simpLogoRect.left + (maxW - drawW) / 2f
                val drawY = simpLogoRect.top + (maxH - drawH) / 2f
                val filterPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
                canvas.drawBitmap(customLogoBitmap, null, RectF(drawX, drawY, drawX + drawW, drawY + drawH), filterPaint)
            } else {
                fillPaint.color = 0xFF1E293B.toInt()
                canvas.drawRoundRect(simpLogoRect, 4f, 4f, fillPaint)
                textPaint.color = 0xFFFBBF24.toInt()
                textPaint.textSize = 10.5f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText("JVM", margin + 45f, margin + 20f, textPaint)
                textPaint.color = Color.WHITE
                textPaint.textSize = 5.5f
                canvas.drawText("ENGENHARIA", margin + 45f, margin + 28f, textPaint)
                fillPaint.color = 0xFF0284C7.toInt()
                canvas.drawRoundRect(RectF(margin + 14f, margin + 31f, margin + 76f, margin + 41f), 2f, 2f, fillPaint)
                textPaint.color = Color.WHITE
                textPaint.textSize = 5f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas.drawText("LAB. DIELÉTRICO", margin + 45f, margin + 38.5f, textPaint)
            }

            // Header Titles (Center)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.WHITE
            textPaint.textSize = 10.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("RELATÓRIO TÉCNICO SIMPLIFICADO & TABULAÇÃO DE ENSAIOS DIELÉTRICOS", margin + 92f, margin + 18f, textPaint)

            textPaint.color = 0xFFE2E8F0.toInt()
            textPaint.textSize = 6.8f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText(
                "${company.legalName.ifEmpty { "JVM ENGENHARIA LTDA" }} | CNPJ: ${company.cnpj.ifEmpty { "29.894.500/0001-04" }} | Registro CREA: ${company.creaCompanyRegister.ifEmpty { "1620-PJ" }}",
                margin + 92f,
                margin + 28f,
                textPaint
            )

            textPaint.color = 0xFF94A3B8.toInt()
            textPaint.textSize = 6f
            canvas.drawText(
                "Endereço: ${company.address.ifEmpty { "QUADRA EQ 304/307 CONJUNTO F LOTE, 1, 1 - BRASILIA/DF" }} | Tel: ${company.phone.ifEmpty { "(61) 3627-9930" }} | E-mail: ${company.email.ifEmpty { "joao.nunes@jvmengenharia.com.br" }}",
                margin + 92f,
                margin + 37f,
                textPaint
            )

            textPaint.color = 0xFF38BDF8.toInt()
            textPaint.textSize = 6f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(
                "Conformidade NR-10 (item 10.7.8) • ABNT NBR 16295 • ASTM F496/F479/F478 • Rastreabilidade RBC",
                margin + 92f,
                margin + 45f,
                textPaint
            )

            // Header Metadata Badge (Right)
            val rightBoxW = 120f
            val rightBoxX = LANDSCAPE_PAGE_WIDTH - margin - rightBoxW - 8f
            fillPaint.color = 0xFF1E3A8A.toInt()
            canvas.drawRoundRect(RectF(rightBoxX, margin + 6f, rightBoxX + rightBoxW, margin + 44f), 4f, 4f, fillPaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = Color.WHITE
            textPaint.textSize = 7.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(effectiveReportNum, rightBoxX + rightBoxW / 2f, margin + 18f, textPaint)
            textPaint.color = 0xFF93C5FD.toInt()
            textPaint.textSize = 7f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText(DateUtils.formatDate(DateUtils.today()), rightBoxX + rightBoxW / 2f, margin + 29f, textPaint)
            textPaint.color = Color.WHITE
            textPaint.textSize = 7f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("${sortedTests.size} itens ensaiados", rightBoxX + rightBoxW / 2f, margin + 39f, textPaint)

            curY = margin + 54f
        } else {
            // Letterhead model replaces frame and company header
            curY = margin + 48f

            fillPaint.color = 0xEEF8FAFC.toInt()
            canvas.drawRoundRect(RectF(margin + 4f, curY, LANDSCAPE_PAGE_WIDTH - margin - 4f, curY + 26f), 4f, 4f, fillPaint)
            strokePaint.color = 0xFFCBD5E1.toInt()
            strokePaint.strokeWidth = 0.8f
            canvas.drawRoundRect(RectF(margin + 4f, curY, LANDSCAPE_PAGE_WIDTH - margin - 4f, curY + 26f), 4f, 4f, strokePaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_NAVY
            textPaint.textSize = 9.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("RELATÓRIO TÉCNICO SIMPLIFICADO & TABULAÇÃO DE ENSAIOS DIELÉTRICOS", margin + 12f, curY + 11.5f, textPaint)

            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6.2f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText("Conformidade NR-10 (item 10.7.8) • ABNT NBR 16295 • ASTM F496/F479/F478 • Rastreabilidade RBC", margin + 12f, curY + 21f, textPaint)

            val rightBoxW = 120f
            val rightBoxX = LANDSCAPE_PAGE_WIDTH - margin - rightBoxW - 10f
            fillPaint.color = 0xFF1E3A8A.toInt()
            canvas.drawRoundRect(RectF(rightBoxX, curY + 3f, rightBoxX + rightBoxW, curY + 23f), 3f, 3f, fillPaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = Color.WHITE
            textPaint.textSize = 7f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(effectiveReportNum, rightBoxX + rightBoxW / 2f, curY + 12f, textPaint)

            textPaint.color = 0xFF93C5FD.toInt()
            textPaint.textSize = 6f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText("${sortedTests.size} itens • ${DateUtils.formatDate(DateUtils.today())}", rightBoxX + rightBoxW / 2f, curY + 20f, textPaint)

            curY += 26f + 5f
        }

        // Section 1: DADOS DO CLIENTE / SOLICITANTE
        fillPaint.color = COLOR_NAVY
        canvas.drawRect(margin + 4f, curY, LANDSCAPE_PAGE_WIDTH - margin - 4f, curY + 12f, fillPaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.WHITE
        textPaint.textSize = 6.8f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText("1. DADOS DO CLIENTE / SOLICITANTE", margin + 8f, curY + 9f, textPaint)

        curY += 13f
        fillPaint.color = COLOR_CARD_BG
        canvas.drawRect(margin + 4f, curY, LANDSCAPE_PAGE_WIDTH - margin - 4f, curY + 34f, fillPaint)
        strokePaint.color = 0xFFE2E8F0.toInt()
        canvas.drawRect(margin + 4f, curY, LANDSCAPE_PAGE_WIDTH - margin - 4f, curY + 34f, strokePaint)

        // Line 1
        val cL1 = margin + 8f
        val cL2 = margin + 300f
        val cL3 = margin + 580f
        drawField(canvas, "Razão Social:", client?.razaoSocial?.ifEmpty { client.nomeFantasia } ?: "BUNGE ALIMENTOS S/A", cL1, curY + 9f, 280f, true)
        drawField(canvas, "CNPJ / CPF:", client?.cnpj ?: "84.046.101/0019-12", cL2, curY + 9f, 260f)
        drawField(canvas, "Inscrição Estadual:", client?.inscricaoEstadual?.ifEmpty { "Isento / Não informado" } ?: "Isento / Não informado", cL3, curY + 9f, 220f)

        // Line 2
        val fullAddress = "${client?.address?.ifEmpty { "AVENIDA ALBERTO ANNANIAS MOREIRA, 400 - DISTRITO INDUSTRIAL DE DOURADOS, MS" } ?: "AVENIDA ALBERTO ANNANIAS MOREIRA, 400 - DISTRITO INDUSTRIAL DE DOURADOS, MS"}"
        drawField(canvas, "Endereço:", fullAddress, cL1, curY + 20f, 550f)
        drawField(canvas, "Contato / Resp.:", client?.contactName?.ifEmpty { "Rafael (Engenheiro / Gestor)" } ?: "Rafael (Engenheiro / Gestor)", cL3, curY + 20f, 220f)

        // Line 3
        drawField(canvas, "Ordem de Serviço (OS):", effectiveOsNum, cL1, curY + 30f, 280f)
        drawField(canvas, "ART Vinculada:", effectiveArt, cL2, curY + 30f, 260f)

        // Resumo do Lote with colored highlights
        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 6.8f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        canvas.drawText("Resumo do Lote: ", cL3, curY + 30f, textPaint)
        val resX = cL3 + textPaint.measureText("Resumo do Lote: ")
        textPaint.color = COLOR_GREEN
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText("$approvedCount Aprovados", resX, curY + 30f, textPaint)
        val appW = textPaint.measureText("$approvedCount Aprovados")
        textPaint.color = COLOR_TEXT_DARK
        canvas.drawText(" | ", resX + appW, curY + 30f, textPaint)
        val pipeW = textPaint.measureText(" | ")
        textPaint.color = if (rejectedCount > 0) COLOR_RED else COLOR_TEXT_MUTED
        canvas.drawText("$rejectedCount Reprovados ($approvalPercent% aprov.)", resX + appW + pipeW, curY + 30f, textPaint)

        curY += 38f

        // Section 2: RELAÇÃO DE NORMAS TÉCNICAS E CRITÉRIOS DE CONFORMIDADE APLICADOS
        fillPaint.color = COLOR_NAVY
        canvas.drawRect(margin + 4f, curY, LANDSCAPE_PAGE_WIDTH - margin - 4f, curY + 12f, fillPaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.WHITE
        textPaint.textSize = 6.8f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText("2. RELAÇÃO DE NORMAS TÉCNICAS E CRITÉRIOS DE CONFORMIDADE APLICADOS", margin + 8f, curY + 9f, textPaint)

        curY += 13f

        // Norms Table Header
        fillPaint.color = 0xFF1E293B.toInt()
        canvas.drawRect(margin + 4f, curY, LANDSCAPE_PAGE_WIDTH - margin - 4f, curY + 11f, fillPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 6.2f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)

        val nCol1 = margin + 8f
        val nCol2 = margin + 30f
        val nCol3 = margin + 175f
        val nCol4 = margin + 325f
        val nCol5 = margin + 480f

        canvas.drawText("Item", nCol1, curY + 8f, textPaint)
        canvas.drawText("Código da Norma", nCol2, curY + 8f, textPaint)
        canvas.drawText("Denominação Técnica", nCol3, curY + 8f, textPaint)
        canvas.drawText("Equipamentos Abrangidos", nCol4, curY + 8f, textPaint)
        canvas.drawText("Escopo & Parâmetros do Ensaio", nCol5, curY + 8f, textPaint)

        curY += 11f

        // Norms Table Rows (7 rows)
        val normRowH = 12f
        PADRAO_NORMAS_RELATORIO.forEachIndexed { idx, norm ->
            val isEven = idx % 2 == 0
            fillPaint.color = if (isEven) Color.WHITE else 0xFFF8FAFC.toInt()
            canvas.drawRect(margin + 4f, curY, LANDSCAPE_PAGE_WIDTH - margin - 4f, curY + normRowH, fillPaint)
            strokePaint.color = 0xFFE2E8F0.toInt()
            canvas.drawLine(margin + 4f, curY + normRowH, LANDSCAPE_PAGE_WIDTH - margin - 4f, curY + normRowH, strokePaint)

            textPaint.color = COLOR_TEXT_DARK
            textPaint.textSize = 5.6f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)

            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(norm.itemNum, nCol1, curY + 8.5f, textPaint)

            textPaint.color = 0xFF0284C7.toInt()
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(norm.codigo, nCol2, curY + 8.5f, textPaint)

            textPaint.color = COLOR_TEXT_DARK
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            val maxEscWidth = LANDSCAPE_PAGE_WIDTH - margin - 4f - nCol5 - 6f
            canvas.drawText(formatWithWordBoundaries(norm.denominacao, 145f, textPaint), nCol3, curY + 8.5f, textPaint)
            canvas.drawText(formatWithWordBoundaries(norm.equipamentos, 145f, textPaint), nCol4, curY + 8.5f, textPaint)
            canvas.drawText(formatWithWordBoundaries(norm.escopo, maxEscWidth, textPaint), nCol5, curY + 8.5f, textPaint)

            curY += normRowH
        }

        curY += 4f

        // Section 3: ANÁLISE DOS RESULTADOS E TABULAÇÃO COMPLETA DO LOTE
        fillPaint.color = COLOR_NAVY
        canvas.drawRect(margin + 4f, curY, LANDSCAPE_PAGE_WIDTH - margin - 4f, curY + 12f, fillPaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.WHITE
        textPaint.textSize = 6.8f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText("3. ANÁLISE DOS RESULTADOS E TABULAÇÃO COMPLETA DO LOTE", margin + 8f, curY + 9f, textPaint)

        curY += 16f

        val tableLeft = margin + 4f
        val tableRight = LANDSCAPE_PAGE_WIDTH - margin - 4f
        val tableWidth = tableRight - tableLeft

        // KPI Summary Table (matching spreadsheet layout)
        val kpiW = tableWidth / 4f
        fillPaint.color = 0xFFF8FAFC.toInt()
        canvas.drawRect(tableLeft, curY, tableRight, curY + 12f, fillPaint)
        strokePaint.color = 0xFF94A3B8.toInt()
        strokePaint.strokeWidth = 0.8f
        canvas.drawRect(tableLeft, curY, tableRight, curY + 28f, strokePaint)
        canvas.drawLine(tableLeft, curY + 12f, tableRight, curY + 12f, strokePaint)
        for (i in 1..3) {
            canvas.drawLine(tableLeft + i * kpiW, curY, tableLeft + i * kpiW, curY + 28f, strokePaint)
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = 0xFF475569.toInt()
        textPaint.textSize = 6.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText("AMOSTRAS", tableLeft + kpiW * 0.5f, curY + 9f, textPaint)
        canvas.drawText("APROVADOS", tableLeft + kpiW * 1.5f, curY + 9f, textPaint)
        canvas.drawText("REPROVADOS", tableLeft + kpiW * 2.5f, curY + 9f, textPaint)
        canvas.drawText("ÍNDICE APROV.", tableLeft + kpiW * 3.5f, curY + 9f, textPaint)

        textPaint.color = 0xFF0F172A.toInt()
        textPaint.textSize = 8.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText("${sortedTests.size} un.", tableLeft + kpiW * 0.5f, curY + 23f, textPaint)
        canvas.drawText("$approvedCount un.", tableLeft + kpiW * 1.5f, curY + 23f, textPaint)
        canvas.drawText("$rejectedCount un.", tableLeft + kpiW * 2.5f, curY + 23f, textPaint)
        canvas.drawText("${approvalPercent}%", tableLeft + kpiW * 3.5f, curY + 23f, textPaint)

        curY += 34f

        // 7-Column Definitions (matching spreadsheet: TAG/CA, EQUIPAMENTO/CLASSE, COLABORADOR/SETOR, TENSÃO/ENSAIO, FUGA/LIMITE, VALIDADE, PARECER)
        val c0 = tableLeft          // 20f
        val c1 = c0 + 105f          // 125f (TAG / CA: 105f)
        val c2 = c1 + 165f          // 290f (EQUIPAMENTO / CLASSE: 165f)
        val c3 = c2 + 140f          // 430f (COLABORADOR / SETOR: 140f)
        val c4 = c3 + 105f          // 535f (TENSÃO / ENSAIO: 105f)
        val c5 = c4 + 105f          // 640f (FUGA / LIMITE: 105f)
        val c6 = c5 + 85f           // 725f (VALIDADE: 85f)
        val c7 = tableRight         // 822f (PARECER: 97f)

        fun drawTestTableHeader(yPos: Float) {
            val headerH = 15f
            fillPaint.color = 0xFF0F2027.toInt()
            canvas.drawRect(tableLeft, yPos, tableRight, yPos + headerH, fillPaint)

            strokePaint.color = 0xFF334155.toInt()
            strokePaint.strokeWidth = 0.8f
            canvas.drawRect(tableLeft, yPos, tableRight, yPos + headerH, strokePaint)
            listOf(c1, c2, c3, c4, c5, c6).forEach { cx ->
                canvas.drawLine(cx, yPos, cx, yPos + headerH, strokePaint)
            }

            textPaint.color = Color.WHITE
            textPaint.textSize = 6.8f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            textPaint.textAlign = Paint.Align.CENTER

            canvas.drawText("TAG / CA", (c0 + c1) / 2f, yPos + 10.5f, textPaint)
            canvas.drawText("EQUIPAMENTO / CLASSE", (c1 + c2) / 2f, yPos + 10.5f, textPaint)
            canvas.drawText("COLABORADOR / SETOR", (c2 + c3) / 2f, yPos + 10.5f, textPaint)
            canvas.drawText("TENSÃO / ENSAIO", (c3 + c4) / 2f, yPos + 10.5f, textPaint)
            canvas.drawText("FUGA / LIMITE", (c4 + c5) / 2f, yPos + 10.5f, textPaint)
            canvas.drawText("VALIDADE", (c5 + c6) / 2f, yPos + 10.5f, textPaint)
            canvas.drawText("PARECER", (c6 + c7) / 2f, yPos + 10.5f, textPaint)
        }

        drawTestTableHeader(curY)
        curY += 15f

        val testRowH = 24f

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

        fun drawTestRow(t: TestRecordEntity, idx: Int, yPos: Float) {
            val isEven = idx % 2 == 0
            fillPaint.color = if (isEven) Color.WHITE else 0xFFF8FAFC.toInt()
            canvas.drawRect(tableLeft, yPos, tableRight, yPos + testRowH, fillPaint)

            // Cell border grid lines (matching spreadsheet)
            strokePaint.color = 0xFFCBD5E1.toInt()
            strokePaint.strokeWidth = 0.5f
            canvas.drawLine(tableLeft, yPos + testRowH, tableRight, yPos + testRowH, strokePaint)
            listOf(c0, c1, c2, c3, c4, c5, c6, c7).forEach { cx ->
                canvas.drawLine(cx, yPos, cx, yPos + testRowH, strokePaint)
            }

            // 1. TAG / CA
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = 0xFF0F172A.toInt()
            textPaint.textSize = 7.2f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(t.equipmentTag, c0 + 6f, yPos + 9.5f, textPaint)

            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6.2f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText("CA: ${t.equipmentCa.ifEmpty { "N/C" }}", c0 + 6f, yPos + 18.5f, textPaint)

            val parsedToolsSimp = ManualToolItem.getToolsForTest(t)
            val isFerramentaSimp = t.equipmentType.contains("FERRAMENTA", ignoreCase = true) || parsedToolsSimp.isNotEmpty()
            val approvedToolsSimp = ManualToolItem.getApprovedTools(parsedToolsSimp)
            val rejectedToolsSimp = ManualToolItem.getRejectedTools(parsedToolsSimp)

            // 2. EQUIPAMENTO / CLASSE
            textPaint.color = 0xFF0F172A.toInt()
            textPaint.textSize = 7.2f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            val eqName = if (isFerramentaSimp && parsedToolsSimp.isNotEmpty()) {
                "CONJUNTO FERRAMENTAS"
            } else {
                t.equipmentType.replace("_", " ").uppercase()
            }
            canvas.drawText(eqName.take(28), c1 + 6f, yPos + 9.5f, textPaint)

            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6.2f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            val clStr = if (isFerramentaSimp && parsedToolsSimp.isNotEmpty()) {
                "Classe 0 (${parsedToolsSimp.sumOf { it.quantity }} un. ensaiadas)"
            } else {
                "Classe ${t.equipmentClass} (${getMaxUseVoltage(t.equipmentClass)})"
            }
            canvas.drawText(clStr.take(30), c1 + 6f, yPos + 18.5f, textPaint)

            // 3. COLABORADOR / SETOR
            textPaint.color = 0xFF0F172A.toInt()
            textPaint.textSize = 7f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            val colabName = t.collaboratorName.ifEmpty { "N/C" }
            canvas.drawText(colabName.take(24), c2 + 6f, yPos + 9.5f, textPaint)

            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6.2f
            val sectorText = t.collaboratorSector.ifEmpty { if (t.collaboratorRegistration.isNotEmpty()) t.collaboratorRegistration else "N/C" }
            canvas.drawText(sectorText.take(24), c2 + 6f, yPos + 18.5f, textPaint)

            // 4. TENSÃO / ENSAIO
            textPaint.color = 0xFF0F172A.toInt()
            textPaint.textSize = 7f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText("${t.appliedVoltage_kV.toInt()} kV ${t.voltageType}", c3 + 6f, yPos + 9.5f, textPaint)

            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6.2f
            canvas.drawText("${t.applicationDurationSeconds}s contínuos", c3 + 6f, yPos + 18.5f, textPaint)

            // 5. FUGA / LIMITE
            val unitStr = t.currentUnit.ifEmpty { "mA" }
            val isMicro = unitStr.equals("µA", ignoreCase = true) || unitStr.equals("uA", ignoreCase = true)
            val fugaStr = if (isMicro) {
                if (t.measuredLeakageCurrent_mA % 1.0 == 0.0) "${t.measuredLeakageCurrent_mA.toInt()} µA" else "${String.format(Locale.US, "%.1f", t.measuredLeakageCurrent_mA)} µA"
            } else {
                "${String.format(Locale.US, "%.2f", t.measuredLeakageCurrent_mA)} mA"
            }
            val limStr = if (isMicro) {
                if (t.leakageCurrentLimit_mA % 1.0 == 0.0) "${t.leakageCurrentLimit_mA.toInt()} µA" else "${String.format(Locale.US, "%.1f", t.leakageCurrentLimit_mA)} µA"
            } else {
                "${String.format(Locale.US, "%.2f", t.leakageCurrentLimit_mA)} mA"
            }
            textPaint.color = 0xFF0F172A.toInt()
            textPaint.textSize = 7f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText(fugaStr, c4 + 6f, yPos + 9.5f, textPaint)

            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6.2f
            canvas.drawText("Máx: $limStr", c4 + 6f, yPos + 18.5f, textPaint)

            // 6. VALIDADE
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = 0xFF0F172A.toInt()
            textPaint.textSize = 7f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            val retestDateStr = if (t.result == "APROVADO") DateUtils.formatDate(t.retestDueDate) else ""
            canvas.drawText(retestDateStr, (c5 + c6) / 2f, yPos + 14f, textPaint)

            // 7. PARECER
            val isApp = t.result == "APROVADO"
            val (parecerSimpText, parecerSimpColor) = when {
                isFerramentaSimp && parsedToolsSimp.isNotEmpty() && rejectedToolsSimp.isNotEmpty() && approvedToolsSimp.isNotEmpty() -> {
                    "PARCIAL (${approvedToolsSimp.size}/${parsedToolsSimp.size})" to 0xFFD97706.toInt()
                }
                isFerramentaSimp && parsedToolsSimp.isNotEmpty() && rejectedToolsSimp.isNotEmpty() && approvedToolsSimp.isEmpty() -> {
                    "REPROVADO" to COLOR_RED_DARK
                }
                isApp -> "APROVADO" to 0xFF15803D.toInt()
                else -> "REPROVADO" to COLOR_RED_DARK
            }
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = parecerSimpColor
            textPaint.textSize = if (parecerSimpText.length > 9) 6.2f else 7.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(parecerSimpText, (c6 + c7) / 2f, yPos + 14f, textPaint)
        }

        fun drawPageFooter(pageNum: Int) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText(
                "JVMENGENHARIA&TREINAMENTO • Sistema de Gestão Laboratorial Dielétrica Conforme NR-10 • Documento Técnico Oficial",
                margin + 6f,
                LANDSCAPE_PAGE_HEIGHT - margin - 5f,
                textPaint
            )

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Página $pageNum de $totalPages", LANDSCAPE_PAGE_WIDTH - margin - 6f, LANDSCAPE_PAGE_HEIGHT - margin - 5f, textPaint)
        }

        fun drawTotalizationAndSignature(yPos: Float) {
            val boxH = 48f
            fillPaint.color = COLOR_CARD_BG
            canvas.drawRoundRect(RectF(margin + 4f, yPos, LANDSCAPE_PAGE_WIDTH - margin - 4f, yPos + boxH), 4f, 4f, fillPaint)
            strokePaint.color = 0xFFCBD5E1.toInt()
            canvas.drawRoundRect(RectF(margin + 4f, yPos, LANDSCAPE_PAGE_WIDTH - margin - 4f, yPos + boxH), 4f, 4f, strokePaint)

            // Left Side: Totalização
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_NAVY
            textPaint.textSize = 7.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("TOTALIZAÇÃO DO LOTE DE ENSAIOS DIELÉTRICOS:", margin + 12f, yPos + 12f, textPaint)

            textPaint.color = COLOR_TEXT_DARK
            textPaint.textSize = 6.8f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText("Total de Equipamentos: ${sortedTests.size} | Aprovados: $approvedCount ($approvalPercent%) | Reprovados: $rejectedCount", margin + 12f, yPos + 23f, textPaint)

            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6.2f
            canvas.drawText("Os ensaios foram executados em conformidade com a NR-10 (item 10.7.8) e as normas vigentes.", margin + 12f, yPos + 33f, textPaint)

            textPaint.color = 0xFF0284C7.toInt()
            textPaint.textSize = 6f
            canvas.drawText("Instrumentos calibrados na RBC. Equipamentos aprovados aptos para intervenções elétricas.", margin + 12f, yPos + 42f, textPaint)

            // Right Side: Assinatura
            val sigW = 210f
            val sigX = LANDSCAPE_PAGE_WIDTH - margin - sigW - 16f
            strokePaint.color = 0xFF0F172A.toInt()
            strokePaint.strokeWidth = 0.8f
            canvas.drawLine(sigX, yPos + 22f, sigX + sigW, yPos + 22f, strokePaint)

            // Digital signature line / name
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = COLOR_NAVY
            textPaint.textSize = 7.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(company.technicalResponsibleName.ifEmpty { "Eng. João Carlos" }, sigX + sigW / 2f, yPos + 31f, textPaint)

            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText("Engenheiro Eletricista e de Segurança do Trabalho", sigX + sigW / 2f, yPos + 39f, textPaint)
            canvas.drawText("CREA: ${company.technicalResponsibleCrea.ifEmpty { "24481/D-DF" }} | RNP: 076313600", sigX + sigW / 2f, yPos + 46f, textPaint)
        }

        // Draw first batch of rows on Page 1
        val firstBatch = sortedTests.take(rowsOnPage1)
        firstBatch.forEachIndexed { index, t ->
            drawTestRow(t, index, curY)
            curY += testRowH
        }

        if (totalPages == 1) {
            curY += 8f
            drawTotalizationAndSignature(curY)
        }

        drawPageFooter(1)
        pdfDocument.finishPage(page)

        // ---------------- SUBSEQUENT PAGES (if any) ----------------
        if (remainingRows.isNotEmpty()) {
            val chunks = remainingRows.chunked(rowsPerPage2Onwards)
            chunks.forEachIndexed { pageIdx, chunk ->
                val pNum = 2 + pageIdx
                pageInfo = PdfDocument.PageInfo.Builder(LANDSCAPE_PAGE_WIDTH, LANDSCAPE_PAGE_HEIGHT, pNum).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas

                canvas.drawColor(Color.WHITE)
                drawLetterheadIfEnabled(canvas, letterheadBitmap, company, pNum, totalPages, LANDSCAPE_PAGE_WIDTH, LANDSCAPE_PAGE_HEIGHT)

                val isPNumLetterheadActive = company.useLetterhead && company.letterheadUri.isNotBlank() && letterheadBitmap != null && shouldApplyLetterhead(company, pNum, totalPages)

                var subY: Float
                if (!isPNumLetterheadActive) {
                    // Outer Border
                    strokePaint.color = 0xFFCBD5E1.toInt()
                    strokePaint.strokeWidth = 1f
                    canvas.drawRect(margin, margin, LANDSCAPE_PAGE_WIDTH - margin, LANDSCAPE_PAGE_HEIGHT - margin, strokePaint)

                    // Compact Header on Page 2+
                    fillPaint.color = COLOR_NAVY
                    canvas.drawRect(margin, margin, LANDSCAPE_PAGE_WIDTH - margin, margin + 24f, fillPaint)
                    textPaint.textAlign = Paint.Align.LEFT
                    textPaint.color = Color.WHITE
                    textPaint.textSize = 8.5f
                    textPaint.typeface = getArialTypeface(Typeface.BOLD)
                    canvas.drawText("RELATÓRIO TÉCNICO SIMPLIFICADO & TABULAÇÃO DE ENSAIOS DIELÉTRICOS • Continuação", margin + 10f, margin + 16f, textPaint)

                    textPaint.textAlign = Paint.Align.RIGHT
                    textPaint.textSize = 7.5f
                    textPaint.typeface = getArialTypeface(Typeface.BOLD)
                    canvas.drawText("$effectiveReportNum | $effectiveOsNum", LANDSCAPE_PAGE_WIDTH - margin - 10f, margin + 16f, textPaint)

                    subY = margin + 30f
                } else {
                    subY = margin + 46f
                    fillPaint.color = 0xEEF8FAFC.toInt()
                    canvas.drawRoundRect(RectF(margin + 4f, subY, LANDSCAPE_PAGE_WIDTH - margin - 4f, subY + 20f), 3f, 3f, fillPaint)
                    strokePaint.color = 0xFFCBD5E1.toInt()
                    strokePaint.strokeWidth = 0.8f
                    canvas.drawRoundRect(RectF(margin + 4f, subY, LANDSCAPE_PAGE_WIDTH - margin - 4f, subY + 20f), 3f, 3f, strokePaint)

                    textPaint.textAlign = Paint.Align.LEFT
                    textPaint.color = COLOR_NAVY
                    textPaint.textSize = 8f
                    textPaint.typeface = getArialTypeface(Typeface.BOLD)
                    canvas.drawText("RELATÓRIO TÉCNICO SIMPLIFICADO & TABULAÇÃO DE ENSAIOS DIELÉTRICOS • Continuação", margin + 10f, subY + 13f, textPaint)

                    textPaint.textAlign = Paint.Align.RIGHT
                    textPaint.textSize = 7.5f
                    textPaint.typeface = getArialTypeface(Typeface.BOLD)
                    canvas.drawText("$effectiveReportNum | $effectiveOsNum", LANDSCAPE_PAGE_WIDTH - margin - 10f, subY + 13f, textPaint)

                    subY += 20f + 6f
                }
                drawTestTableHeader(subY)
                subY += 15f

                val offsetIdx = rowsOnPage1 + (pageIdx * rowsPerPage2Onwards)
                chunk.forEachIndexed { cIdx, t ->
                    drawTestRow(t, offsetIdx + cIdx, subY)
                    subY += testRowH
                }

                // If final page, draw Totalization and Signature
                if (pNum == totalPages) {
                    subY += 8f
                    drawTotalizationAndSignature(subY)
                }

                drawPageFooter(pNum)
                pdfDocument.finishPage(page)
            }
        }

        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val fileName = "Relatorio_Simplificado_${effectiveReportNum.replace("-", "_")}_${System.currentTimeMillis()}.pdf"
        val pdfFile = File(reportsDir, fileName)

        val fos = FileOutputStream(pdfFile)
        pdfDocument.writeTo(fos)
        fos.flush()
        fos.close()
        pdfDocument.close()

        pdfFile
    }

    /**
     * Generates a multi-item Consolidated Technical Report PDF for audits & client batches.
     */
    suspend fun generateRelatorioConsolidadoPdf(
        context: Context,
        tests: List<TestRecordEntity>,
        client: ClientEntity?,
        company: AppSettingEntity,
        reportType: String = "SIMPLIFICADO",
        serviceOrder: ServiceOrderEntity? = null
    ): File = withContext(Dispatchers.IO) {
        if (reportType == "SIMPLIFICADO" || reportType.isEmpty()) {
            return@withContext generateRelatorioSimplificadoPdf(context, tests, client, company, serviceOrder)
        }

        val letterheadBitmap = if (company.useLetterhead && company.letterheadUri.isNotBlank()) {
            LetterheadStorageHelper.loadLetterheadBitmap(context, company.letterheadUri, PAGE_WIDTH * 2, PAGE_HEIGHT * 2)
        } else null

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawColor(Color.WHITE)
        drawLetterheadIfEnabled(canvas, letterheadBitmap, company, 1, 1, PAGE_WIDTH, PAGE_HEIGHT)

        val strokePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
        val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        val textPaint = Paint().apply { isAntiAlias = true }

        val margin = 20f
        val contentWidth = PAGE_WIDTH - 2 * margin
        val isLetterheadActive = company.useLetterhead && company.letterheadUri.isNotBlank() && letterheadBitmap != null && shouldApplyLetterhead(company, 1, 1)

        var currentY: Float
        if (!isLetterheadActive) {
            // Outer Border
            strokePaint.color = COLOR_NAVY
            strokePaint.strokeWidth = 1.5f
            canvas.drawRect(margin, margin, PAGE_WIDTH - margin, PAGE_HEIGHT - margin, strokePaint)

            // Header
            fillPaint.color = COLOR_NAVY
            canvas.drawRect(margin, margin, PAGE_WIDTH - margin, margin + 55f, fillPaint)

            val customLogoBitmap = if (company.companyLogoUri.isNotBlank()) {
                loadSampledBitmap(context, company.companyLogoUri, 200, 100)
            } else null

            val textStartX: Float
            if (customLogoBitmap != null) {
                val maxW = 55f
                val maxH = 40f
                val ratio = customLogoBitmap.width.toFloat() / customLogoBitmap.height.toFloat()
                val (drawW, drawH) = if (ratio > maxW / maxH) {
                    maxW to (maxW / ratio)
                } else {
                    (maxH * ratio) to maxH
                }
                val logoX = margin + 12f
                val logoY = margin + (55f - drawH) / 2f
                val filterPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
                canvas.drawBitmap(customLogoBitmap, null, RectF(logoX, logoY, logoX + drawW, logoY + drawH), filterPaint)
                textStartX = margin + 12f + drawW + 10f
            } else {
                textStartX = margin + 12f
            }

            textPaint.color = Color.WHITE
            textPaint.textSize = 13f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(company.name.ifEmpty { "JVM ENGENHARIA & ENSAIOS ELÉTRICOS" }.uppercase(), textStartX, margin + 20f, textPaint)

            textPaint.textSize = 8.5f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText("RELATÓRIO CONSOLIDADO DE ENSAIOS DIELÉTRICOS NR-10", textStartX, margin + 34f, textPaint)
            canvas.drawText("Data de Emissão: ${DateUtils.formatDate(DateUtils.today())} | Total de Itens: ${tests.size}", textStartX, margin + 46f, textPaint)

            currentY = margin + 65f
        } else {
            currentY = margin + 56f

            fillPaint.color = 0xEEF8FAFC.toInt()
            canvas.drawRoundRect(RectF(margin + 4f, currentY, PAGE_WIDTH - margin - 4f, currentY + 28f), 4f, 4f, fillPaint)
            strokePaint.color = COLOR_BORDER
            strokePaint.strokeWidth = 0.8f
            canvas.drawRoundRect(RectF(margin + 4f, currentY, PAGE_WIDTH - margin - 4f, currentY + 28f), 4f, 4f, strokePaint)

            textPaint.color = COLOR_NAVY
            textPaint.textSize = 10f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("RELATÓRIO CONSOLIDADO DE ENSAIOS DIELÉTRICOS NR-10", margin + 12f, currentY + 12f, textPaint)

            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 7f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText("Data de Emissão: ${DateUtils.formatDate(DateUtils.today())} | Total de Itens: ${tests.size}", margin + 12f, currentY + 22f, textPaint)

            currentY += 28f + 6f
        }

        // Client & Summary Stats Card
        fillPaint.color = COLOR_CARD_BG
        canvas.drawRoundRect(RectF(margin + 6f, currentY, PAGE_WIDTH - margin - 6f, currentY + 38f), 4f, 4f, fillPaint)
        strokePaint.color = COLOR_BORDER
        strokePaint.strokeWidth = 0.8f
        canvas.drawRoundRect(RectF(margin + 6f, currentY, PAGE_WIDTH - margin - 6f, currentY + 38f), 4f, 4f, strokePaint)

        val approvedCount = tests.count { it.result == "APROVADO" }
        val rejectedCount = tests.count { it.result != "APROVADO" }

        textPaint.color = COLOR_NAVY
        textPaint.textSize = 9f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText("Cliente: ${client?.nomeFantasia?.ifEmpty { client.razaoSocial } ?: "Todos os Clientes / Geral"}", margin + 12f, currentY + 16f, textPaint)

        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 8f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        canvas.drawText("CNPJ: ${client?.cnpj ?: "Consolidado Geral"}", margin + 12f, currentY + 28f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = COLOR_GREEN
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText("Aprovados: $approvedCount", PAGE_WIDTH - margin - 12f, currentY + 16f, textPaint)

        textPaint.color = if (rejectedCount > 0) COLOR_RED else COLOR_TEXT_MUTED
        canvas.drawText("Reprovados: $rejectedCount", PAGE_WIDTH - margin - 12f, currentY + 28f, textPaint)

        // Table Header
        currentY += 46f
        fillPaint.color = COLOR_NAVY
        canvas.drawRoundRect(RectF(margin + 6f, currentY, PAGE_WIDTH - margin - 6f, currentY + 20f), 3f, 3f, fillPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.WHITE
        textPaint.textSize = 8f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)

        val c1 = margin + 10f
        val c2 = margin + 65f
        val c3 = margin + 185f
        val c4 = margin + 280f
        val c5 = margin + 360f
        val c6 = margin + 430f
        val c7 = margin + 490f

        canvas.drawText("TAG", c1, currentY + 13f, textPaint)
        canvas.drawText("TIPO / CLASSE", c2, currentY + 13f, textPaint)
        canvas.drawText("USUÁRIO / SETOR", c3, currentY + 13f, textPaint)
        canvas.drawText("TENSÃO / FUGA", c4, currentY + 13f, textPaint)
        canvas.drawText("DATA ENSAIO", c5, currentY + 13f, textPaint)
        canvas.drawText("VALIDADE", c6, currentY + 13f, textPaint)
        canvas.drawText("STATUS", c7, currentY + 13f, textPaint)

        // Table Rows (up to 18 rows fit nicely on A4)
        currentY += 22f
        val rowHeight = 26f
        strokePaint.color = COLOR_BORDER
        strokePaint.strokeWidth = 0.5f

        val sortedTests = tests.sortedWith(
            compareBy({ it.collaboratorName.ifBlank { "ZZZ" } }, { it.equipmentTag })
        )

        sortedTests.take(18).forEachIndexed { index, t ->
            val isEven = index % 2 == 0
            if (isEven) {
                fillPaint.color = COLOR_BG_LIGHT
                canvas.drawRect(margin + 6f, currentY, PAGE_WIDTH - margin - 6f, currentY + rowHeight, fillPaint)
            }
            canvas.drawLine(margin + 6f, currentY + rowHeight, PAGE_WIDTH - margin - 6f, currentY + rowHeight, strokePaint)

            textPaint.color = COLOR_NAVY
            textPaint.textSize = 7.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(t.equipmentTag, c1, currentY + 12f, textPaint)

            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6.5f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText(t.equipmentCa.ifEmpty { "CA N/A" }, c1, currentY + 21f, textPaint)

            textPaint.color = COLOR_TEXT_DARK
            textPaint.textSize = 7f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(t.equipmentType.replace("_", " ").take(24), c2, currentY + 12f, textPaint)
            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6.5f
            canvas.drawText("Classe ${t.equipmentClass} (${getMaxUseVoltage(t.equipmentClass)})", c2, currentY + 21f, textPaint)

            textPaint.color = COLOR_TEXT_DARK
            textPaint.textSize = 7f
            canvas.drawText(t.collaboratorName.take(18), c3, currentY + 12f, textPaint)
            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6.5f
            canvas.drawText(t.collaboratorSector.take(18), c3, currentY + 21f, textPaint)

            textPaint.color = COLOR_BLUE
            textPaint.textSize = 7f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("${t.appliedVoltage_kV} kV", c4, currentY + 12f, textPaint)
            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 6.5f
            canvas.drawText("${t.measuredLeakageCurrent_mA} mA", c4, currentY + 21f, textPaint)

            textPaint.color = COLOR_TEXT_DARK
            textPaint.textSize = 7f
            canvas.drawText(DateUtils.formatDate(t.testDate), c5, currentY + 16f, textPaint)

            val isApp = t.result == "APROVADO"
            textPaint.color = COLOR_GREEN
            textPaint.textSize = 7f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            val retestDateStr = if (isApp) DateUtils.formatDate(t.retestDueDate) else ""
            canvas.drawText(retestDateStr, c6, currentY + 16f, textPaint)

            textPaint.color = if (isApp) COLOR_GREEN else COLOR_RED
            textPaint.textSize = 7.5f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText(t.result, c7, currentY + 16f, textPaint)

            currentY += rowHeight
        }

        // Bottom Signatures & Footer
        val bottomY = PAGE_HEIGHT - margin - 45f
        strokePaint.color = COLOR_BORDER
        canvas.drawLine(margin + 20f, bottomY + 15f, margin + 200f, bottomY + 15f, strokePaint)
        canvas.drawLine(PAGE_WIDTH - margin - 200f, bottomY + 15f, PAGE_WIDTH - margin - 20f, bottomY + 15f, strokePaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = COLOR_NAVY
        textPaint.textSize = 7.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText("Analista Técnico Executor", margin + 110f, bottomY + 26f, textPaint)
        canvas.drawText("Responsável Técnico (CREA)", PAGE_WIDTH - margin - 110f, bottomY + 26f, textPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 6.8f
        canvas.drawText("Relatório gerado em conformidade com as exigências da NR-10 e ABNT NBR / IEC.", PAGE_WIDTH / 2f, PAGE_HEIGHT - margin - 6f, textPaint)

        pdfDocument.finishPage(page)

        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val fileName = "Relatorio_Consolidado_${System.currentTimeMillis()}.pdf"
        val pdfFile = File(reportsDir, fileName)

        val fos = FileOutputStream(pdfFile)
        pdfDocument.writeTo(fos)
        fos.flush()
        fos.close()
        pdfDocument.close()

        pdfFile
    }

    /**
     * Generates a Comprehensive Multi-Page Technical Report PDF (Relatório Técnico Completo de Ensaios Dielétricos)
     * containing: Capa Oficial, Sumário, Identificação, Introdução, Objetivo, Metodologia de Ensaios,
     * Normas Aplicadas, Análise dos Resultados e Tabulação Completa, Conclusão, Recomendações e Termo de RT.
     */
    suspend fun generateRelatorioCompletoPdf(
        context: Context,
        tests: List<TestRecordEntity>,
        client: ClientEntity?,
        company: AppSettingEntity,
        serviceOrder: ServiceOrderEntity? = null
    ): File = withContext(Dispatchers.IO) {
        initTypeface(context)
        val pdfDocument = PdfDocument()

        val strokePaint = Paint().apply { isAntiAlias = true; isDither = true; style = Paint.Style.STROKE }
        val fillPaint = Paint().apply { isAntiAlias = true; isDither = true; style = Paint.Style.FILL }
        val textPaint = Paint().apply { isAntiAlias = true; isSubpixelText = true; isLinearText = true; isFilterBitmap = true }

        val margin = 28f
        val contentWidth = PAGE_WIDTH - 2 * margin

        val effectiveReportNum = tests.firstOrNull()?.reportNumber
            ?: (serviceOrder?.osNumber?.ifEmpty { "REL-DIE-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}" } ?: "REL-DIE-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}")
        val effectiveOsNum = serviceOrder?.osNumber ?: tests.firstOrNull()?.serviceOrderNumber ?: "OS-001"
        val effectiveArtNum = serviceOrder?.artNumber?.ifEmpty { tests.firstOrNull()?.artNumber?.ifEmpty { company.creaCompanyRegister } } ?: company.creaCompanyRegister
        val effectiveDateStr = DateUtils.formatDate(DateUtils.today())

        val approvedCount = tests.count { it.result == "APROVADO" }
        val rejectedCount = tests.count { it.result != "APROVADO" }
        val totalCount = tests.size
        val approvalRate = if (totalCount > 0) (approvedCount * 100.0 / totalCount) else 100.0

        val sortedTests = tests.sortedWith(
            compareBy({ it.collaboratorName.ifBlank { "ZZZ" } }, { it.equipmentTag })
        )

        fun getTestItemRowHeight(t: TestRecordEntity): Float {
            val mainRowH = 26f
            val isFerramenta = ManualToolItem.isManualTool(t.equipmentType, t.equipmentTag)
            if (!isFerramenta) return mainRowH

            val parsedTools = ManualToolItem.getToolsForTest(t)
            if (parsedTools.isNotEmpty()) {
                val useTwoCols = parsedTools.size > 2
                val numRows = if (useTwoCols) (parsedTools.size + 1) / 2 else parsedTools.size
                var toolsContentH = 15f
                for (r in 0 until numRows) {
                    val t1 = parsedTools[r * (if (useTwoCols) 2 else 1)]
                    val t2 = if (useTwoCols) parsedTools.getOrNull(r * 2 + 1) else null
                    val hasReason = (!t1.isApproved && t1.rejectionReason.isNotBlank()) ||
                                    (t2 != null && !t2.isApproved && t2.rejectionReason.isNotBlank())
                    toolsContentH += if (hasReason) 18f else 13.5f
                }
                return mainRowH + toolsContentH + 6f
            } else {
                return mainRowH
            }
        }

        val testChunks = mutableListOf<List<TestRecordEntity>>()
        var currentChunk = mutableListOf<TestRecordEntity>()
        var currentH = 0f
        val limitFirst = 580f
        val limitNext = 640f

        sortedTests.forEach { test ->
            val testH = getTestItemRowHeight(test)
            val limit = if (testChunks.isEmpty()) limitFirst else limitNext
            if (currentChunk.isNotEmpty() && (currentH + testH > limit)) {
                testChunks.add(currentChunk)
                currentChunk = mutableListOf(test)
                currentH = testH
            } else {
                currentChunk.add(test)
                currentH += testH
            }
        }
        if (currentChunk.isNotEmpty()) {
            testChunks.add(currentChunk)
        }
        if (testChunks.isEmpty()) {
            testChunks.add(emptyList())
        }
        val totalReportPages = 4 + testChunks.size // Page 1: Capa, Page 2: Sumario/Intro/Obj, Page 3: Metodologia/Normas, Pages 4..N: Tabulacao, Last: Conclusao/Assinatura

        val customLogoBitmap = if (company.companyLogoUri.isNotBlank()) {
            loadSampledBitmap(context, company.companyLogoUri, 200, 100)
        } else null

        val letterheadBitmap = if (company.useLetterhead && company.letterheadUri.isNotBlank()) {
            LetterheadStorageHelper.loadLetterheadBitmap(context, company.letterheadUri, PAGE_WIDTH * 2, PAGE_HEIGHT * 2)
        } else null

        val isLetterheadActiveOn = { pageNum: Int ->
            company.useLetterhead && company.letterheadUri.isNotBlank() && letterheadBitmap != null && shouldApplyLetterhead(company, pageNum, totalReportPages)
        }

        fun drawStandardHeader(canvas: Canvas, pageNum: Int, sectionTitle: String) {
            drawLetterheadIfEnabled(canvas, letterheadBitmap, company, pageNum, totalReportPages, PAGE_WIDTH, PAGE_HEIGHT)

            if (!isLetterheadActiveOn(pageNum)) {
                // Header bar
                fillPaint.color = COLOR_NAVY
                canvas.drawRect(margin, margin, PAGE_WIDTH - margin, margin + 36f, fillPaint)

                // Logo mark
                val logoRect = RectF(margin + 4f, margin + 4f, margin + 40f, margin + 32f)
                if (customLogoBitmap != null) {
                    val maxW = logoRect.width()
                    val maxH = logoRect.height()
                    val ratio = customLogoBitmap.width.toFloat() / customLogoBitmap.height.toFloat()
                    val (drawW, drawH) = if (ratio > maxW / maxH) {
                        maxW to (maxW / ratio)
                    } else {
                        (maxH * ratio) to maxH
                    }
                    val drawX = logoRect.left + (maxW - drawW) / 2f
                    val drawY = logoRect.top + (maxH - drawH) / 2f
                    val filterPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
                    canvas.drawBitmap(customLogoBitmap, null, RectF(drawX, drawY, drawX + drawW, drawY + drawH), filterPaint)
                } else {
                    fillPaint.color = COLOR_NAVY_LIGHT
                    canvas.drawRoundRect(logoRect, 3f, 3f, fillPaint)
                    textPaint.textAlign = Paint.Align.CENTER
                    textPaint.color = 0xFFFBBF24.toInt()
                    textPaint.textSize = 8.5f
                    textPaint.typeface = getArialTypeface(Typeface.BOLD)
                    canvas.drawText("JVM", margin + 22f, margin + 17f, textPaint)
                    textPaint.color = Color.WHITE
                    textPaint.textSize = 4.5f
                    canvas.drawText("ENG", margin + 22f, margin + 26f, textPaint)
                }

                // Header Texts
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = Color.WHITE
                textPaint.textSize = 8f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas.drawText(company.name.ifEmpty { "JVM ENGENHARIA & TREINAMENTOS" }.uppercase(), margin + 46f, margin + 16f, textPaint)

                textPaint.color = 0xFF38BDF8.toInt()
                textPaint.textSize = 6.2f
                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                canvas.drawText("RELATÓRIO TÉCNICO DE ENSAIOS DIELÉTRICOS NR-10 • $sectionTitle", margin + 46f, margin + 27f, textPaint)

                // Right Box (Report Num & Date)
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.color = Color.WHITE
                textPaint.textSize = 6.8f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas.drawText(effectiveReportNum, PAGE_WIDTH - margin - 8f, margin + 16f, textPaint)

                textPaint.color = 0xFF94A3B8.toInt()
                textPaint.textSize = 5.8f
                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                canvas.drawText("OS: $effectiveOsNum | ART: $effectiveArtNum", PAGE_WIDTH - margin - 8f, margin + 27f, textPaint)

                // Outer Page Border
                strokePaint.color = 0xFFCBD5E1.toInt()
                strokePaint.strokeWidth = 0.8f
                canvas.drawRect(margin, margin, PAGE_WIDTH - margin, PAGE_HEIGHT - margin, strokePaint)
            } else {
                // Clean header bar below letterhead
                val subY = margin + 46f
                fillPaint.color = 0xEEF8FAFC.toInt()
                canvas.drawRoundRect(RectF(margin, subY, PAGE_WIDTH - margin, subY + 22f), 3f, 3f, fillPaint)
                strokePaint.color = 0xFFCBD5E1.toInt()
                strokePaint.strokeWidth = 0.8f
                canvas.drawRoundRect(RectF(margin, subY, PAGE_WIDTH - margin, subY + 22f), 3f, 3f, strokePaint)

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = COLOR_NAVY
                textPaint.textSize = 8f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas.drawText("RELATÓRIO TÉCNICO DE ENSAIOS DIELÉTRICOS NR-10 • $sectionTitle", margin + 8f, subY + 14f, textPaint)

                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.color = COLOR_BLUE
                textPaint.textSize = 7f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                canvas.drawText(effectiveReportNum, PAGE_WIDTH - margin - 8f, subY + 14f, textPaint)
            }
        }

        fun drawStandardFooter(canvas: Canvas, pageNum: Int) {
            val footerY = PAGE_HEIGHT - margin - 18f
            strokePaint.color = 0xFFE2E8F0.toInt()
            strokePaint.strokeWidth = 0.6f
            canvas.drawLine(margin + 6f, footerY, PAGE_WIDTH - margin - 6f, footerY, strokePaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = COLOR_TEXT_MUTED
            textPaint.textSize = 5.8f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            canvas.drawText("${company.name.ifEmpty { "JVM Engenharia" }} • CREA-DF ${company.creaCompanyRegister.ifEmpty { "26084/DF" }} • CNPJ: ${company.cnpj.ifEmpty { "43.344.821/0001-44" }}", margin + 6f, footerY + 11f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = COLOR_NAVY
            textPaint.textSize = 6.2f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            canvas.drawText("Página $pageNum de $totalReportPages", PAGE_WIDTH - margin - 6f, footerY + 11f, textPaint)
        }

        // ==========================================
        // PAGE 1: CAPA OFICIAL (COVER PAGE)
        // ==========================================
        val page1Info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page1 = pdfDocument.startPage(page1Info)
        val c1 = page1.canvas
        c1.drawColor(Color.WHITE)
        drawLetterheadIfEnabled(c1, letterheadBitmap, company, 1, totalReportPages, PAGE_WIDTH, PAGE_HEIGHT)

        val isP1Letterhead = isLetterheadActiveOn(1)
        var coverY: Float

        if (!isP1Letterhead) {
            // Outer decorative border
            strokePaint.color = COLOR_NAVY
            strokePaint.strokeWidth = 2f
            c1.drawRect(margin, margin, PAGE_WIDTH - margin, PAGE_HEIGHT - margin, strokePaint)

            strokePaint.color = 0xFF0284C7.toInt()
            strokePaint.strokeWidth = 0.6f
            c1.drawRect(margin + 4f, margin + 4f, PAGE_WIDTH - margin - 4f, PAGE_HEIGHT - margin - 4f, strokePaint)

            // Top Banner Block
            fillPaint.color = COLOR_NAVY
            c1.drawRect(margin + 5f, margin + 5f, PAGE_WIDTH - margin - 5f, margin + 85f, fillPaint)

            // Accent line under banner
            fillPaint.color = 0xFFF59E0B.toInt()
            c1.drawRect(margin + 5f, margin + 85f, PAGE_WIDTH - margin - 5f, margin + 88f, fillPaint)

            // Brand Logo Box
            val coverLogoRect = RectF(margin + 18f, margin + 15f, margin + 90f, margin + 75f)
            if (customLogoBitmap != null) {
                val maxW = coverLogoRect.width()
                val maxH = coverLogoRect.height()
                val ratio = customLogoBitmap.width.toFloat() / customLogoBitmap.height.toFloat()
                val (drawW, drawH) = if (ratio > maxW / maxH) {
                    maxW to (maxW / ratio)
                } else {
                    (maxH * ratio) to maxH
                }
                val drawX = coverLogoRect.left + (maxW - drawW) / 2f
                val drawY = coverLogoRect.top + (maxH - drawH) / 2f
                val filterPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
                c1.drawBitmap(customLogoBitmap, null, RectF(drawX, drawY, drawX + drawW, drawY + drawH), filterPaint)
            } else {
                fillPaint.color = 0xFF1E293B.toInt()
                c1.drawRoundRect(coverLogoRect, 6f, 6f, fillPaint)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = 0xFFFBBF24.toInt()
                textPaint.textSize = 18f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                c1.drawText("JVM", margin + 54f, margin + 42f, textPaint)
                textPaint.color = Color.WHITE
                textPaint.textSize = 7f
                c1.drawText("ENGENHARIA", margin + 54f, margin + 55f, textPaint)
                textPaint.color = 0xFF38BDF8.toInt()
                textPaint.textSize = 5.5f
                c1.drawText("& TREINAMENTOS", margin + 54f, margin + 65f, textPaint)
            }

            // Banner Header Texts
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.WHITE
            textPaint.textSize = 11f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            c1.drawText(company.name.ifEmpty { "JVM ENGENHARIA & TREINAMENTOS LTDA" }.uppercase(), margin + 104f, margin + 32f, textPaint)

            textPaint.color = 0xFFE2E8F0.toInt()
            textPaint.textSize = 7.2f
            textPaint.typeface = getArialTypeface(Typeface.NORMAL)
            c1.drawText("LABORATÓRIO DE ENSAIOS DIELÉTRICOS E SEGURANÇA EM ELETRICIDADE", margin + 104f, margin + 46f, textPaint)
            c1.drawText("CNPJ: ${company.cnpj.ifEmpty { "43.344.821/0001-44" }} • CREA/DF: ${company.creaCompanyRegister.ifEmpty { "26084/DF" }}", margin + 104f, margin + 58f, textPaint)
            c1.drawText("${company.address.ifEmpty { "Brasília - DF" }} • ${company.phone.ifEmpty { "(61) 3627-9930" }}", margin + 104f, margin + 70f, textPaint)

            coverY = margin + 115f
        } else {
            coverY = margin + 65f
        }
        fillPaint.color = 0xFFF1F5F9.toInt()
        c1.drawRoundRect(RectF(margin + 16f, coverY, PAGE_WIDTH - margin - 16f, coverY + 84f), 6f, 6f, fillPaint)
        strokePaint.color = 0xFFCBD5E1.toInt()
        strokePaint.strokeWidth = 1f
        c1.drawRoundRect(RectF(margin + 16f, coverY, PAGE_WIDTH - margin - 16f, coverY + 84f), 6f, 6f, strokePaint)

        // Blue strip left of title
        fillPaint.color = COLOR_BLUE
        c1.drawRoundRect(RectF(margin + 16f, coverY, margin + 22f, coverY + 84f), 3f, 3f, fillPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = COLOR_NAVY
        textPaint.textSize = 13.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c1.drawText("RELATÓRIO TÉCNICO DE ENSAIOS DIELÉTRICOS", PAGE_WIDTH / 2f, coverY + 26f, textPaint)

        textPaint.color = 0xFF0284C7.toInt()
        textPaint.textSize = 9f
        c1.drawText("CONFORMIDADE NR-10 (SUBITENS 10.4.3.1 E 10.7.8)", PAGE_WIDTH / 2f, coverY + 42f, textPaint)

        textPaint.color = COLOR_TEXT_BODY
        textPaint.textSize = 7.5f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        c1.drawText("Ensaios de Rigidez Dielétrica, Tensão Aplicada e Medição de Corrente de Fuga", PAGE_WIDTH / 2f, coverY + 58f, textPaint)
        c1.drawText("em EPIs, EPCs e Ferramental Isolado para Trabalhos em Alta e Baixa Tensão", PAGE_WIDTH / 2f, coverY + 70f, textPaint)

        // Report Reference Card
        coverY += 96f
        fillPaint.color = 0xFFF8FAFC.toInt()
        c1.drawRoundRect(RectF(margin + 16f, coverY, PAGE_WIDTH - margin - 16f, coverY + 44f), 4f, 4f, fillPaint)
        strokePaint.color = 0xFFE2E8F0.toInt()
        strokePaint.strokeWidth = 0.8f
        c1.drawRoundRect(RectF(margin + 16f, coverY, PAGE_WIDTH - margin - 16f, coverY + 44f), 4f, 4f, strokePaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = COLOR_NAVY
        textPaint.textSize = 7.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c1.drawText("DOCUMENTO Nº: ", margin + 26f, coverY + 16f, textPaint)
        textPaint.color = COLOR_BLUE
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c1.drawText(effectiveReportNum, margin + 98f, coverY + 16f, textPaint)

        textPaint.color = COLOR_NAVY
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c1.drawText("ORDEM DE SERVIÇO: ", margin + 26f, coverY + 32f, textPaint)
        textPaint.color = COLOR_TEXT_BODY
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        c1.drawText(effectiveOsNum, margin + 115f, coverY + 32f, textPaint)

        textPaint.color = COLOR_NAVY
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c1.drawText("DATA DE EMISSÃO: ", PAGE_WIDTH / 2f + 10f, coverY + 16f, textPaint)
        textPaint.color = COLOR_TEXT_BODY
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        c1.drawText(effectiveDateStr, PAGE_WIDTH / 2f + 98f, coverY + 16f, textPaint)

        textPaint.color = COLOR_NAVY
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c1.drawText("ART CREA/DF: ", PAGE_WIDTH / 2f + 10f, coverY + 32f, textPaint)
        textPaint.color = COLOR_GREEN_DARK
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c1.drawText(effectiveArtNum, PAGE_WIDTH / 2f + 78f, coverY + 32f, textPaint)

        // Solicitante / Cliente Card
        coverY += 54f
        fillPaint.color = Color.WHITE
        c1.drawRoundRect(RectF(margin + 16f, coverY, PAGE_WIDTH - margin - 16f, coverY + 95f), 4f, 4f, fillPaint)
        strokePaint.color = 0xFFCBD5E1.toInt()
        c1.drawRoundRect(RectF(margin + 16f, coverY, PAGE_WIDTH - margin - 16f, coverY + 95f), 4f, 4f, strokePaint)

        fillPaint.color = COLOR_NAVY
        c1.drawRoundRect(RectF(margin + 16f, coverY, PAGE_WIDTH - margin - 16f, coverY + 18f), 4f, 4f, fillPaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.WHITE
        textPaint.textSize = 7.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c1.drawText("1. IDENTIFICAÇÃO DO SOLICITANTE / EMPRESA CONTRATANTE", margin + 24f, coverY + 12f, textPaint)

        val firstTest = tests.firstOrNull()
        val clientName = client?.razaoSocial?.ifEmpty { client.nomeFantasia }?.ifEmpty { firstTest?.clientName }
            ?: (firstTest?.clientName?.ifEmpty { "EMPRESA SOLICITANTE / CLIENTE" } ?: "EMPRESA SOLICITANTE / CLIENTE")
        val clientTrade = client?.nomeFantasia?.ifEmpty { client.razaoSocial } ?: clientName
        val clientCnpj = client?.cnpj ?: "00.000.000/0001-00"
        val clientAddr = if (!client?.address.isNullOrBlank()) "${client?.address}, ${client?.city} - ${client?.state}" else "Endereço não informado"
        val clientContact = "${client?.contactName?.ifEmpty { "Engenharia de Segurança" } ?: "Engenharia de Segurança"} • Tel: ${client?.contactPhone ?: "(61) 9999-9999"}"

        textPaint.color = COLOR_NAVY
        textPaint.textSize = 8f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c1.drawText("Razão Social: $clientName", margin + 26f, coverY + 33f, textPaint)

        textPaint.color = COLOR_TEXT_BODY
        textPaint.textSize = 7.2f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        c1.drawText("Nome Fantasia: $clientTrade", margin + 26f, coverY + 47f, textPaint)
        c1.drawText("CNPJ: $clientCnpj | Inscrição Estadual: ${client?.inscricaoEstadual ?: "Isento"}", margin + 26f, coverY + 61f, textPaint)
        c1.drawText("Endereço: $clientAddr", margin + 26f, coverY + 75f, textPaint)
        c1.drawText("Contato / Responsável: $clientContact", margin + 26f, coverY + 89f, textPaint)

        // Executante / Laboratório Card
        coverY += 105f
        fillPaint.color = Color.WHITE
        c1.drawRoundRect(RectF(margin + 16f, coverY, PAGE_WIDTH - margin - 16f, coverY + 85f), 4f, 4f, fillPaint)
        strokePaint.color = 0xFFCBD5E1.toInt()
        c1.drawRoundRect(RectF(margin + 16f, coverY, PAGE_WIDTH - margin - 16f, coverY + 85f), 4f, 4f, strokePaint)

        fillPaint.color = COLOR_NAVY
        c1.drawRoundRect(RectF(margin + 16f, coverY, PAGE_WIDTH - margin - 16f, coverY + 18f), 4f, 4f, fillPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 7.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c1.drawText("2. IDENTIFICAÇÃO DO LABORATÓRIO EXECUTANTE & RESPONSABILIDADE TÉCNICA", margin + 24f, coverY + 12f, textPaint)

        textPaint.color = COLOR_NAVY
        textPaint.textSize = 8f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c1.drawText(company.name.ifEmpty { "JVM ENGENHARIA & TREINAMENTOS LTDA" }, margin + 26f, coverY + 33f, textPaint)

        textPaint.color = COLOR_TEXT_BODY
        textPaint.textSize = 7.2f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        c1.drawText("CNPJ: ${company.cnpj.ifEmpty { "43.344.821/0001-44" }} | Registro CREA: ${company.creaCompanyRegister.ifEmpty { "26084/DF" }}", margin + 26f, coverY + 47f, textPaint)
        c1.drawText("Responsável Técnico: ${company.technicalResponsibleName.ifEmpty { "Eng. João Carlos Evaristo Guedes Nunes" }} - Eng. Eletricista / Seg. Trabalho", margin + 26f, coverY + 61f, textPaint)
        c1.drawText("Registro Profissional: CREA/DF ${company.technicalResponsibleCrea.ifEmpty { "24481/D-DF" }} | ART: $effectiveArtNum", margin + 26f, coverY + 75f, textPaint)

        // Summary Badges Footer on Cover
        coverY += 95f
        val badgeW = (contentWidth - 32f - 16f) / 3f
        val bH = 34f

        // Badge 1: Total
        fillPaint.color = 0xFFF1F5F9.toInt()
        c1.drawRoundRect(RectF(margin + 16f, coverY, margin + 16f + badgeW, coverY + bH), 4f, 4f, fillPaint)
        strokePaint.color = 0xFFCBD5E1.toInt()
        c1.drawRoundRect(RectF(margin + 16f, coverY, margin + 16f + badgeW, coverY + bH), 4f, 4f, strokePaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 6.2f
        c1.drawText("TOTAL ENSAIADO", margin + 16f + badgeW / 2f, coverY + 12f, textPaint)
        textPaint.color = COLOR_NAVY
        textPaint.textSize = 11f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c1.drawText("$totalCount ITENS", margin + 16f + badgeW / 2f, coverY + 27f, textPaint)

        // Badge 2: Aprovados
        val b2X = margin + 16f + badgeW + 8f
        fillPaint.color = COLOR_GREEN_BG
        c1.drawRoundRect(RectF(b2X, coverY, b2X + badgeW, coverY + bH), 4f, 4f, fillPaint)
        strokePaint.color = 0xFF86EFAC.toInt()
        c1.drawRoundRect(RectF(b2X, coverY, b2X + badgeW, coverY + bH), 4f, 4f, strokePaint)
        textPaint.color = COLOR_GREEN_DARK
        textPaint.textSize = 6.2f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        c1.drawText("APROVADOS", b2X + badgeW / 2f, coverY + 12f, textPaint)
        textPaint.textSize = 11f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c1.drawText("$approvedCount (${String.format(Locale.US, "%.0f", approvalRate)}%)", b2X + badgeW / 2f, coverY + 27f, textPaint)

        // Badge 3: Reprovados
        val b3X = b2X + badgeW + 8f
        fillPaint.color = if (rejectedCount > 0) COLOR_RED_BG else 0xFFF8FAFC.toInt()
        c1.drawRoundRect(RectF(b3X, coverY, b3X + badgeW, coverY + bH), 4f, 4f, fillPaint)
        strokePaint.color = if (rejectedCount > 0) 0xFFFCA5A5.toInt() else 0xFFCBD5E1.toInt()
        c1.drawRoundRect(RectF(b3X, coverY, b3X + badgeW, coverY + bH), 4f, 4f, strokePaint)
        textPaint.color = if (rejectedCount > 0) COLOR_RED_DARK else COLOR_TEXT_MUTED
        textPaint.textSize = 6.2f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        c1.drawText("REPROVADOS", b3X + badgeW / 2f, coverY + 12f, textPaint)
        textPaint.textSize = 11f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c1.drawText("$rejectedCount ITENS", b3X + badgeW / 2f, coverY + 27f, textPaint)

        // Bottom City / Year
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 7f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c1.drawText("BRASÍLIA - DF • ${SimpleDateFormat("yyyy", Locale.US).format(Date())}", PAGE_WIDTH / 2f, PAGE_HEIGHT - margin - 14f, textPaint)

        pdfDocument.finishPage(page1)

        // ==========================================
        // PAGE 2: SUMÁRIO, INTRODUÇÃO E OBJETIVO
        // ==========================================
        val page2Info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create()
        val page2 = pdfDocument.startPage(page2Info)
        val c2 = page2.canvas
        c2.drawColor(Color.WHITE)

        drawStandardHeader(c2, 2, "SUMÁRIO & FUNDAMENTAÇÃO")
        drawStandardFooter(c2, 2)

        var p2Y = if (isLetterheadActiveOn(2)) (margin + 74f) else (margin + 46f)

        // SUMÁRIO EXECUTIVO
        fillPaint.color = 0xFFF8FAFC.toInt()
        c2.drawRoundRect(RectF(margin + 6f, p2Y, PAGE_WIDTH - margin - 6f, p2Y + 120f), 4f, 4f, fillPaint)
        strokePaint.color = 0xFFCBD5E1.toInt()
        strokePaint.strokeWidth = 0.8f
        c2.drawRoundRect(RectF(margin + 6f, p2Y, PAGE_WIDTH - margin - 6f, p2Y + 120f), 4f, 4f, strokePaint)

        fillPaint.color = COLOR_NAVY
        c2.drawRoundRect(RectF(margin + 6f, p2Y, PAGE_WIDTH - margin - 6f, p2Y + 16f), 4f, 4f, fillPaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.WHITE
        textPaint.textSize = 7.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c2.drawText("SUMÁRIO DO RELATÓRIO TÉCNICO", margin + 14f, p2Y + 11f, textPaint)

        val tocItems = listOf(
            "1. IDENTIFICAÇÃO DO SOLICITANTE E EXECUTANTE" to "Pág. 1",
            "2. INTRODUÇÃO E ENQUADRAMENTO REGULAMENTAR (NR-10)" to "Pág. 2",
            "3. OBJETIVO DO LAUDO E ESCOPO DA AVALIAÇÃO" to "Pág. 2",
            "4. NORMAS TÉCNICAS APLICADAS & CRITÉRIOS DE ACEITAÇÃO" to "Pág. 3",
            "5. ANÁLISE DOS RESULTADOS E TABULAÇÃO DOS EQUIPAMENTOS" to "Pág. 4",
            "6. CONCLUSÃO TÉCNICA E PARECER DE APTIDÃO" to "Pág. $totalReportPages",
            "7. RECOMENDAÇÕES DE SEGURANÇA, USO E MANUTENÇÃO (NR-10)" to "Pág. $totalReportPages",
            "8. TERMO DE RESPONSABILIDADE TÉCNICA E ASSINATURA" to "Pág. $totalReportPages"
        )

        var tocY = p2Y + 28f
        tocItems.forEach { (item, page) ->
            textPaint.color = COLOR_NAVY
            textPaint.textSize = 6.8f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)
            c2.drawText(item, margin + 14f, tocY, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = COLOR_BLUE
            c2.drawText(page, PAGE_WIDTH - margin - 14f, tocY, textPaint)
            textPaint.textAlign = Paint.Align.LEFT

            strokePaint.color = 0xFFE2E8F0.toInt()
            strokePaint.strokeWidth = 0.4f
            c2.drawLine(margin + 14f, tocY + 3f, PAGE_WIDTH - margin - 14f, tocY + 3f, strokePaint)

            tocY += 10.2f
        }

        // SEÇÃO 2: INTRODUÇÃO
        p2Y += 130f
        fillPaint.color = COLOR_NAVY
        c2.drawRoundRect(RectF(margin + 6f, p2Y, PAGE_WIDTH - margin - 6f, p2Y + 16f), 3f, 3f, fillPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 7.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c2.drawText("2. INTRODUÇÃO E ENQUADRAMENTO REGULAMENTAR", margin + 14f, p2Y + 11f, textPaint)

        p2Y += 24f
        textPaint.color = COLOR_TEXT_BODY
        textPaint.textSize = 7.2f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        val effectiveIntro = company.reportIntroText.ifBlank {
            "O presente Relatório Técnico de Ensaios Dielétricos consolida os resultados dos ensaios de rigidez dielétrica e medição de corrente de fuga realizados no lote de Equipamentos de Proteção Individual (EPI), Equipamentos de Proteção Coletiva (EPC) e Ferramental Isolado pertencentes à empresa solicitante.\n\nA execução periódica destes ensaios atende estritamente às exigências da Norma Regulamentadora nº 10 (Segurança em Instalações e Serviços em Eletricidade - Portaria MTE nº 3.214/78), em especial aos subitens 10.4.3.1 e 10.7.8, os quais estabelecem a obrigatoriedade de ensaios elétricos periódicos em todos os equipamentos, ferramentas e dispositivos isolantes utilizados em intervenções em instalações elétricas, com a devida comprovação por meio de laudo técnico emitido por profissional legalmente habilitado.\n\nOs ensaios foram conduzidos no laboratório especializado da JVM ENGENHARIA & TREINAMENTOS, utilizando fontes de alta tensão (Hipot) e equipamentos de medição calibrados com rastreabilidade à Rede Brasileira de Calibração (RBC / Inmetro), em conformidade com as normas ABNT NBR, IEC e ASTM pertinentes."
        }
        val introParagraphs = effectiveIntro.split("\n\n").filter { it.isNotBlank() }
        introParagraphs.forEach { para ->
            p2Y = drawWrappedTextToCanvas(c2, para.trim(), margin + 6f, p2Y, contentWidth - 12f, 10f, textPaint) + 6f
        }
        p2Y += 6f

        // SEÇÃO 3: OBJETIVO
        fillPaint.color = COLOR_NAVY
        c2.drawRoundRect(RectF(margin + 6f, p2Y, PAGE_WIDTH - margin - 6f, p2Y + 16f), 3f, 3f, fillPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 7.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c2.drawText("3. OBJETIVO DO LAUDO & ESCOPO DA AVALIAÇÃO", margin + 14f, p2Y + 11f, textPaint)

        p2Y += 24f
        val effectiveObjective = company.reportObjectiveText.ifBlank {
            "O objetivo precípuo deste trabalho de engenharia é verificar experimentalmente a integridade física e a capacidade de isolação elétrica dos equipamentos e ferramentas submetidos a ensaio, assegurando que:\n• Os níveis de corrente de fuga sob tensão de ensaio especificada mantenham-se rigorosamente abaixo dos limites máximos permitidos por norma;\n• Os materiais isolantes poliméricos e elastoméricos suportem a tensão de prova sem apresentar perfuração, disrupção elétrica, arco ou sobreaquecimento;\n• Não existam trincas, fissuras, microfuros, contaminações por umidade ou degradações mecânicas/químicas que comprometam a rigidez dielétrica;\n• Seja emitida a certificação de conformidade e estipulada a validade técnica para subsidiar o Prontuário de Instalações Elétricas (PIE) da empresa contratante."
        }
        effectiveObjective.lines().filter { it.isNotBlank() }.forEach { line ->
            val isBullet = line.trim().startsWith("•") || line.trim().startsWith("-") || line.trim().startsWith("*")
            val bulletMargin = if (isBullet) margin + 12f else margin + 6f
            val bulletWidth = if (isBullet) contentWidth - 24f else contentWidth - 12f
            p2Y = drawWrappedTextToCanvas(c2, line.trim(), bulletMargin, p2Y, bulletWidth, 9.5f, textPaint) + 3f
        }

        pdfDocument.finishPage(page2)

        // ==========================================
        // PAGE 3: METODOLOGIA E NORMAS TÉCNICAS
        // ==========================================
        val page3Info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 3).create()
        val page3 = pdfDocument.startPage(page3Info)
        val c3 = page3.canvas
        c3.drawColor(Color.WHITE)

        drawStandardHeader(c3, 3, "NORMAS TÉCNICAS APLICADAS")
        drawStandardFooter(c3, 3)

        var p3Y = if (isLetterheadActiveOn(3)) (margin + 74f) else (margin + 46f)

        // SEÇÃO 4: NORMAS TÉCNICAS
        fillPaint.color = COLOR_NAVY
        c3.drawRoundRect(RectF(margin + 6f, p3Y, PAGE_WIDTH - margin - 6f, p3Y + 14f), 3f, 3f, fillPaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.WHITE
        textPaint.textSize = 7f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        c3.drawText("4. NORMAS TÉCNICAS APLICADAS & CRITÉRIOS DE CONFORMIDADE", margin + 14f, p3Y + 10f, textPaint)

        p3Y += 18f
        // Norms Table Header
        fillPaint.color = 0xFF1E293B.toInt()
        val tableStartY = p3Y
        val tableLeft = margin + 6f
        val tableRight = PAGE_WIDTH - margin - 6f
        val headerH = 12f
        c3.drawRect(tableLeft, p3Y, tableRight, p3Y + headerH, fillPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 5.8f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        val colN1 = tableLeft + 4f
        val colN2 = tableLeft + 22f
        val colN3 = tableLeft + 136f
        val colN4 = tableLeft + 262f
        c3.drawText("ITEM", colN1, p3Y + 8.5f, textPaint)
        c3.drawText("NORMA / REFERÊNCIA", colN2, p3Y + 8.5f, textPaint)
        c3.drawText("EQUIPAMENTOS ABRANGIDOS", colN3, p3Y + 8.5f, textPaint)
        c3.drawText("PARÂMETROS & CRITÉRIOS DO ENSAIO", colN4, p3Y + 8.5f, textPaint)

        p3Y += headerH
        val normList = listOf(
            RelatorioNormaItem("4.1", "NR-10 (Portaria MTE 3.214/78)", "Segurança em Instalações Elétricas", "EPIs, EPCs e Ferramental Geral", "Itens 10.4.3.1 e 10.7.8: Exigência de ensaios periódicos de rigidez dielétrica, calibração RBC e emissão de laudo formal."),
            RelatorioNormaItem("4.2", "ABNT NBR 16295 / IEC 60903", "Trabalhos em Linha Viva - Luvas Isolantes", "Luvas Isolantes de Borracha (Cl. 00 a 4)", "Ensaio em cuba d'água com aplicação de 2,5 a 40 kV CA por 60 s. Limite de fuga de 10 a 24 mA sem perfuração."),
            RelatorioNormaItem("4.3", "ASTM D178 / ABNT NBR 14039", "Tapetes e Estrados de Material Isolante", "Tapetes e Mantas Isolantes (Cl. 0 a 4)", "Aplicação de 5 a 40 kV CA por 60 s entre eletrodos metálicos planos com recuo anti-flashover. Sem ruptura."),
            RelatorioNormaItem("4.4", "ABNT NBR 9699 / IEC 60900", "Ferramentas Manuais Isoladas até 1000V", "Ferramentas Manuais Isoladas (1000V)", "Ensaio a 10.000 V CA RMS por 180 s (3 min) em banho de água. Fuga limite ≤ 1,0 mA/200mm sem centelhamento.")
        )

        val itemPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true; isDither = true; letterSpacing = 0.0f; color = COLOR_NAVY; textSize = 5.8f; typeface = getArialTypeface(Typeface.BOLD) }
        val codePaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true; isDither = true; letterSpacing = 0.0f; color = 0xFF0284C7.toInt(); textSize = 5.4f; typeface = getArialTypeface(Typeface.BOLD) }
        val eqPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true; isDither = true; letterSpacing = 0.0f; color = COLOR_TEXT_DARK; textSize = 5.3f; typeface = getArialTypeface(Typeface.NORMAL) }
        val escPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true; isDither = true; letterSpacing = 0.0f; color = 0xFF334155.toInt(); textSize = 5.2f; typeface = getArialTypeface(Typeface.NORMAL) }

        normList.forEachIndexed { idx, norm ->
            val isEven = idx % 2 == 0
            val codeLines = breakTextIntoLines(norm.codigo, 110f, codePaint)
            val eqLines = breakTextIntoLines(norm.equipamentos, 122f, eqPaint)
            val escLines = breakTextIntoLines(norm.escopo, (tableRight - colN4 - 6f), escPaint)
            val maxLines = maxOf(codeLines.size, eqLines.size, escLines.size, 1)
            val lineHeight = 6.4f
            val normRowHeight = maxOf(14f, (maxLines * lineHeight) + 6f)

            fillPaint.color = if (isEven) Color.WHITE else 0xFFF8FAFC.toInt()
            c3.drawRect(tableLeft, p3Y, tableRight, p3Y + normRowHeight, fillPaint)
            strokePaint.color = 0xFFE2E8F0.toInt()
            strokePaint.strokeWidth = 0.5f
            c3.drawLine(tableLeft, p3Y + normRowHeight, tableRight, p3Y + normRowHeight, strokePaint)

            // Column separators
            c3.drawLine(tableLeft + 18f, p3Y, tableLeft + 18f, p3Y + normRowHeight, strokePaint)
            c3.drawLine(tableLeft + 132f, p3Y, tableLeft + 132f, p3Y + normRowHeight, strokePaint)
            c3.drawLine(tableLeft + 258f, p3Y, tableLeft + 258f, p3Y + normRowHeight, strokePaint)

            c3.drawText(norm.itemNum, colN1, p3Y + 8f, itemPaint)
            codeLines.forEachIndexed { i, line ->
                c3.drawText(line, colN2, p3Y + 7.8f + (i * lineHeight), codePaint)
            }
            eqLines.forEachIndexed { i, line ->
                c3.drawText(line, colN3, p3Y + 7.8f + (i * lineHeight), eqPaint)
            }
            escLines.forEachIndexed { i, line ->
                c3.drawText(line, colN4, p3Y + 7.8f + (i * lineHeight), escPaint)
            }

            p3Y += normRowHeight
        }

        strokePaint.color = 0xFFCBD5E1.toInt()
        strokePaint.strokeWidth = 0.6f
        c3.drawRect(tableLeft, tableStartY, tableRight, p3Y, strokePaint)

        pdfDocument.finishPage(page3)

        // ==========================================
        // PAGES 4..N: ANÁLISE DOS RESULTADOS & TABULAÇÃO
        // ==========================================
        testChunks.forEachIndexed { chunkIdx, chunkTests ->
            val pageNum = 4 + chunkIdx
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            val page = pdfDocument.startPage(pageInfo)
            val c = page.canvas
            c.drawColor(Color.WHITE)

            drawStandardHeader(c, pageNum, "TABULAÇÃO DOS ENSAIOS (PARTE ${chunkIdx + 1}/${testChunks.size})")
            drawStandardFooter(c, pageNum)

            var curY = if (isLetterheadActiveOn(pageNum)) (margin + 74f) else (margin + 46f)

            if (chunkIdx == 0) {
                // Section Title
                fillPaint.color = COLOR_NAVY
                c.drawRoundRect(RectF(margin + 6f, curY, PAGE_WIDTH - margin - 6f, curY + 16f), 3f, 3f, fillPaint)
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = Color.WHITE
                textPaint.textSize = 7.5f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                c.drawText("5. ANÁLISE DOS RESULTADOS E TABULAÇÃO COMPLETA DO LOTE", margin + 14f, curY + 11f, textPaint)

                curY += 22f

                // Unified 4-Column KPI Summary Table (matching spreadsheet)
                val tableLeft = margin + 6f
                val tableRight = PAGE_WIDTH - margin - 6f
                val tableWidth = tableRight - tableLeft
                val kpiW = tableWidth / 4f

                fillPaint.color = 0xFFF8FAFC.toInt()
                c.drawRect(tableLeft, curY, tableRight, curY + 12f, fillPaint)
                strokePaint.color = 0xFF94A3B8.toInt()
                strokePaint.strokeWidth = 0.8f
                c.drawRect(tableLeft, curY, tableRight, curY + 28f, strokePaint)
                c.drawLine(tableLeft, curY + 12f, tableRight, curY + 12f, strokePaint)
                for (i in 1..3) {
                    c.drawLine(tableLeft + i * kpiW, curY, tableLeft + i * kpiW, curY + 28f, strokePaint)
                }

                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = 0xFF475569.toInt()
                textPaint.textSize = 6f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                c.drawText("AMOSTRAS", tableLeft + kpiW * 0.5f, curY + 9f, textPaint)
                c.drawText("APROVADOS", tableLeft + kpiW * 1.5f, curY + 9f, textPaint)
                c.drawText("REPROVADOS", tableLeft + kpiW * 2.5f, curY + 9f, textPaint)
                c.drawText("ÍNDICE APROV.", tableLeft + kpiW * 3.5f, curY + 9f, textPaint)

                textPaint.color = 0xFF0F172A.toInt()
                textPaint.textSize = 8.5f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                c.drawText("$totalCount un.", tableLeft + kpiW * 0.5f, curY + 23f, textPaint)
                c.drawText("$approvedCount un.", tableLeft + kpiW * 1.5f, curY + 23f, textPaint)
                c.drawText("$rejectedCount un.", tableLeft + kpiW * 2.5f, curY + 23f, textPaint)
                c.drawText("${String.format(Locale.US, "%.1f", approvalRate)}%", tableLeft + kpiW * 3.5f, curY + 23f, textPaint)

                curY += 34f
            }

            // Table Columns Positions (7 Columns matching spreadsheet: TAG/CA, EQUIPAMENTO/CLASSE, COLABORADOR/SETOR, TENSÃO/ENSAIO, FUGA/LIMITE, VALIDADE, PARECER)
            val tableLeft = margin + 6f
            val tableRight = PAGE_WIDTH - margin - 6f

            val cx0 = tableLeft
            val cx1 = cx0 + 76f  // TAG / CA
            val cx2 = cx1 + 108f // EQUIPAMENTO / CLASSE
            val cx3 = cx2 + 94f  // COLABORADOR / SETOR
            val cx4 = cx3 + 68f  // TENSÃO / ENSAIO
            val cx5 = cx4 + 68f  // FUGA / LIMITE
            val cx6 = cx5 + 55f  // VALIDADE
            val cx7 = tableRight // PARECER (58f)

            // Table Header Bar
            val headerH = 16f
            fillPaint.color = 0xFF0F2027.toInt()
            c.drawRect(tableLeft, curY, tableRight, curY + headerH, fillPaint)

            strokePaint.color = 0xFF334155.toInt()
            strokePaint.strokeWidth = 0.8f
            c.drawRect(tableLeft, curY, tableRight, curY + headerH, strokePaint)
            listOf(cx1, cx2, cx3, cx4, cx5, cx6).forEach { cx ->
                c.drawLine(cx, curY, cx, curY + headerH, strokePaint)
            }

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = Color.WHITE
            textPaint.textSize = 6f
            textPaint.typeface = getArialTypeface(Typeface.BOLD)

            c.drawText("TAG / CA", (cx0 + cx1) / 2f, curY + 11f, textPaint)
            c.drawText("EQUIPAMENTO / CLASSE", (cx1 + cx2) / 2f, curY + 11f, textPaint)
            c.drawText("COLABORADOR / SETOR", (cx2 + cx3) / 2f, curY + 11f, textPaint)
            c.drawText("TENSÃO / ENSAIO", (cx3 + cx4) / 2f, curY + 11f, textPaint)
            c.drawText("FUGA / LIMITE", (cx4 + cx5) / 2f, curY + 11f, textPaint)
            c.drawText("VALIDADE", (cx5 + cx6) / 2f, curY + 11f, textPaint)
            c.drawText("PARECER", (cx6 + cx7) / 2f, curY + 11f, textPaint)

            curY += headerH
            val rowH = 26f

            chunkTests.forEachIndexed { rowIdx, t ->
                val isEven = rowIdx % 2 == 0
                val isFerramenta = ManualToolItem.isManualTool(t.equipmentType, t.equipmentTag)
                val parsedTools = if (isFerramenta) ManualToolItem.getToolsForTest(t) else emptyList()
                val approvedTools = ManualToolItem.getApprovedTools(parsedTools)
                val rejectedTools = ManualToolItem.getRejectedTools(parsedTools)
                val isApp = t.result == "APROVADO"

                fillPaint.color = if (isEven) Color.WHITE else 0xFFF8FAFC.toInt()
                c.drawRect(tableLeft, curY, tableRight, curY + rowH, fillPaint)

                // Spreadsheet grid cell borders
                strokePaint.color = 0xFFCBD5E1.toInt()
                strokePaint.strokeWidth = 0.5f
                c.drawLine(tableLeft, curY + rowH, tableRight, curY + rowH, strokePaint)
                listOf(cx0, cx1, cx2, cx3, cx4, cx5, cx6, cx7).forEach { cx ->
                    c.drawLine(cx, curY, cx, curY + rowH, strokePaint)
                }

                // 1. Tag & CA
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = 0xFF0F172A.toInt()
                textPaint.textSize = 6.8f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                c.drawText(t.equipmentTag.take(14), cx0 + 4f, curY + 10.5f, textPaint)

                textPaint.color = COLOR_TEXT_MUTED
                textPaint.textSize = 5.8f
                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                c.drawText("CA: ${t.equipmentCa.ifEmpty { "N/C" }}".take(14), cx0 + 4f, curY + 20.5f, textPaint)

                // 2. Equipment & Class
                textPaint.color = 0xFF0F172A.toInt()
                textPaint.textSize = 6.8f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                val eqName = if (isFerramenta && parsedTools.isNotEmpty()) {
                    "CONJUNTO FERRAMENTAS"
                } else {
                    t.equipmentType.replace("_", " ").uppercase()
                }
                c.drawText(eqName.take(20), cx1 + 4f, curY + 10.5f, textPaint)

                textPaint.color = COLOR_TEXT_MUTED
                textPaint.textSize = 5.5f
                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                val clStr = if (isFerramenta && parsedTools.isNotEmpty()) {
                    "Classe 0 (${parsedTools.sumOf { it.quantity }} un. ensaiadas)"
                } else {
                    "Classe ${t.equipmentClass} (${getMaxUseVoltage(t.equipmentClass)})"
                }
                c.drawText(clStr.take(25), cx1 + 4f, curY + 20.5f, textPaint)

                // 3. Collaborator & Sector
                textPaint.color = 0xFF0F172A.toInt()
                textPaint.textSize = 6.5f
                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                val colabName = t.collaboratorName.ifEmpty { "N/C" }
                c.drawText(colabName.take(16), cx2 + 4f, curY + 10.5f, textPaint)

                textPaint.color = COLOR_TEXT_MUTED
                textPaint.textSize = 5.5f
                val sectorText = t.collaboratorSector.ifEmpty { if (t.collaboratorRegistration.isNotEmpty()) t.collaboratorRegistration else "N/C" }
                c.drawText(sectorText.take(16), cx2 + 4f, curY + 20.5f, textPaint)

                // 4. Voltage
                textPaint.color = 0xFF0F172A.toInt()
                textPaint.textSize = 6.5f
                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                c.drawText("${t.appliedVoltage_kV.toInt()} kV ${t.voltageType}", cx3 + 4f, curY + 10.5f, textPaint)

                textPaint.color = COLOR_TEXT_MUTED
                textPaint.textSize = 5.5f
                c.drawText("${t.applicationDurationSeconds}s contínuos", cx3 + 4f, curY + 20.5f, textPaint)

                // 5. Leakage current & Limit
                val isMicro = t.currentUnit.equals("µA", true) || t.currentUnit.equals("uA", true)
                val mStr = if (isMicro) {
                    if (t.measuredLeakageCurrent_mA % 1.0 == 0.0) "${t.measuredLeakageCurrent_mA.toInt()} µA" else "${t.measuredLeakageCurrent_mA} µA"
                } else "${t.measuredLeakageCurrent_mA} mA"
                val lStr = if (isMicro) {
                    if (t.leakageCurrentLimit_mA % 1.0 == 0.0) "${t.leakageCurrentLimit_mA.toInt()} µA" else "${t.leakageCurrentLimit_mA} µA"
                } else "${t.leakageCurrentLimit_mA} mA"

                textPaint.color = 0xFF0F172A.toInt()
                textPaint.textSize = 6.5f
                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                c.drawText(mStr, cx4 + 4f, curY + 10.5f, textPaint)

                textPaint.color = COLOR_TEXT_MUTED
                textPaint.textSize = 5.5f
                c.drawText("Máx: $lStr", cx4 + 4f, curY + 20.5f, textPaint)

                // 6. Validity Date
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = 0xFF0F172A.toInt()
                textPaint.textSize = 6.5f
                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                val retestDateStr = if (isApp || (isFerramenta && approvedTools.isNotEmpty())) DateUtils.formatDate(t.retestDueDate) else ""
                c.drawText(retestDateStr, (cx5 + cx6) / 2f, curY + 15.5f, textPaint)

                // 7. Parecer
                textPaint.textAlign = Paint.Align.CENTER
                val (parecerText, parecerColor) = when {
                    isFerramenta && parsedTools.isNotEmpty() && rejectedTools.isNotEmpty() && approvedTools.isNotEmpty() -> {
                        "PARCIAL (${approvedTools.size}/${parsedTools.size})" to 0xFFD97706.toInt()
                    }
                    isFerramenta && parsedTools.isNotEmpty() && rejectedTools.isNotEmpty() && approvedTools.isEmpty() -> {
                        "REPROVADO" to COLOR_RED_DARK
                    }
                    isApp -> "APROVADO" to 0xFF15803D.toInt()
                    else -> "REPROVADO" to COLOR_RED_DARK
                }
                textPaint.color = parecerColor
                textPaint.textSize = if (parecerText.length > 9) 5.8f else 7f
                textPaint.typeface = getArialTypeface(Typeface.BOLD)
                c.drawText(parecerText, (cx6 + cx7) / 2f, curY + 15.5f, textPaint)

                // Draw sub-panel ONLY for manual tools (os demais equipamentos não precisam de detalhamento)
                if (isFerramenta && parsedTools.isNotEmpty()) {
                    val useTwoCols = parsedTools.size > 2
                    val numRows = if (useTwoCols) (parsedTools.size + 1) / 2 else parsedTools.size
                    val rowHeights = (0 until numRows).map { r ->
                        val t1 = parsedTools[r * (if (useTwoCols) 2 else 1)]
                        val t2 = if (useTwoCols) parsedTools.getOrNull(r * 2 + 1) else null
                        val hasReason = (!t1.isApproved && t1.rejectionReason.isNotBlank()) ||
                                        (t2 != null && !t2.isApproved && t2.rejectionReason.isNotBlank())
                        if (hasReason) 18f else 13.5f
                    }
                    val subCardH = 15f + rowHeights.sum() + 6f
                    val subCardTop = curY + rowH

                    // Subcard Background
                    fillPaint.color = 0xFFF8FAFC.toInt()
                    c.drawRect(tableLeft, subCardTop, tableRight, subCardTop + subCardH, fillPaint)

                    // Outer border of subcard
                    strokePaint.color = 0xFFCBD5E1.toInt()
                    strokePaint.strokeWidth = 0.5f
                    c.drawLine(tableLeft, subCardTop + subCardH, tableRight, subCardTop + subCardH, strokePaint)
                    c.drawLine(tableLeft, subCardTop, tableLeft, subCardTop + subCardH, strokePaint)
                    c.drawLine(tableRight, subCardTop, tableRight, subCardTop + subCardH, strokePaint)

                    // Left vertical accent bar
                    val accentColor = when {
                        rejectedTools.isEmpty() -> COLOR_GREEN
                        approvedTools.isEmpty() -> COLOR_RED
                        else -> 0xFFD97706.toInt()
                    }
                    strokePaint.color = accentColor
                    strokePaint.strokeWidth = 2.5f
                    c.drawLine(tableLeft + 1.25f, subCardTop, tableLeft + 1.25f, subCardTop + subCardH, strokePaint)

                    // Subheader
                    fillPaint.color = 0xFFF1F5F9.toInt()
                    c.drawRect(tableLeft + 2.5f, subCardTop, tableRight, subCardTop + 14f, fillPaint)
                    strokePaint.color = 0xFFCBD5E1.toInt()
                    strokePaint.strokeWidth = 0.5f
                    c.drawLine(tableLeft + 2.5f, subCardTop + 14f, tableRight, subCardTop + 14f, strokePaint)

                    val totalQty = parsedTools.sumOf { it.quantity }
                    val appQty = approvedTools.sumOf { it.quantity }
                    val rejQty = rejectedTools.sumOf { it.quantity }

                    textPaint.textAlign = Paint.Align.LEFT
                    textPaint.color = 0xFF0F172A.toInt()
                    textPaint.textSize = 6.2f
                    textPaint.typeface = getArialTypeface(Typeface.BOLD)
                    val headerSummary = "▸ COMPOSIÇÃO DETALHADA DO CONJUNTO: $totalQty un. ($appQty Aprovada(s) / $rejQty Reprovada(s))"
                    c.drawText(headerSummary, tableLeft + 8f, subCardTop + 10f, textPaint)

                    // Item rows
                    var itemY = subCardTop + 14f
                    val col1X = tableLeft + 8f
                    val colW = if (useTwoCols) (tableRight - tableLeft - 22f) / 2f else (tableRight - tableLeft - 16f)
                    val col2X = col1X + colW + 8f

                    for (r in 0 until numRows) {
                        val h = rowHeights[r]
                        val t1 = parsedTools[r * (if (useTwoCols) 2 else 1)]
                        val t2 = if (useTwoCols) parsedTools.getOrNull(r * 2 + 1) else null

                        val drawToolEntry = { tool: ManualToolItem, xPos: Float, width: Float ->
                            val isToolApp = tool.isApproved
                            val markColor = if (isToolApp) 0xFF15803D.toInt() else COLOR_RED_DARK
                            val badgeBg = if (isToolApp) 0xFFDCFCE7.toInt() else 0xFFFEE2E2.toInt()
                            val badgeText = if (isToolApp) "APROVADO" else "REPROVADO"

                            // Mark icon
                            textPaint.textAlign = Paint.Align.LEFT
                            textPaint.color = markColor
                            textPaint.textSize = 6.8f
                            textPaint.typeface = getArialTypeface(Typeface.BOLD)
                            c.drawText(if (isToolApp) "✓" else "✗", xPos, itemY + 9f, textPaint)

                            // Tool Name
                            textPaint.color = 0xFF1E293B.toInt()
                            textPaint.textSize = 6.0f
                            textPaint.typeface = getArialTypeface(Typeface.BOLD)
                            val nameStr = "${tool.name} (${tool.quantity} un.)"
                            val maxNameW = width - 58f
                            val measuredW = textPaint.measureText(nameStr)
                            val finalName = if (measuredW > maxNameW) nameStr.take(24) + "..." else nameStr
                            c.drawText(finalName, xPos + 10f, itemY + 9f, textPaint)

                            // Status Badge
                            val badgeW = 46f
                            val badgeH = 9.5f
                            val badgeX = xPos + width - badgeW - 2f
                            val badgeY = itemY + 1.5f
                            fillPaint.color = badgeBg
                            c.drawRoundRect(RectF(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH), 2f, 2f, fillPaint)
                            textPaint.textAlign = Paint.Align.CENTER
                            textPaint.color = markColor
                            textPaint.textSize = 5.2f
                            textPaint.typeface = getArialTypeface(Typeface.BOLD)
                            c.drawText(badgeText, badgeX + badgeW / 2f, badgeY + 7f, textPaint)

                            if (!isToolApp && tool.rejectionReason.isNotBlank()) {
                                textPaint.textAlign = Paint.Align.LEFT
                                textPaint.color = COLOR_RED_DARK
                                textPaint.textSize = 5.2f
                                textPaint.typeface = getArialTypeface(Typeface.NORMAL)
                                val rLine = "Motivo: ${tool.rejectionReason}".take(35)
                                c.drawText(rLine, xPos + 10f, itemY + 16.5f, textPaint)
                            }
                        }

                        drawToolEntry(t1, col1X, colW)
                        if (t2 != null) {
                            drawToolEntry(t2, col2X, colW)
                        }

                        itemY += h
                    }

                    curY += (rowH + subCardH)
                } else {
                    curY += rowH
                }
            }

            pdfDocument.finishPage(page)
        }

        // ==========================================
        // LAST PAGE: CONCLUSÃO, RECOMENDAÇÕES E ASSINATURA RT
        // ==========================================
        val lastPageNum = totalReportPages
        val lastPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, lastPageNum).create()
        val lastPage = pdfDocument.startPage(lastPageInfo)
        val cl = lastPage.canvas
        cl.drawColor(Color.WHITE)

        drawStandardHeader(cl, lastPageNum, "CONCLUSÃO & RECOMENDAÇÕES")
        drawStandardFooter(cl, lastPageNum)

        var lastY = if (isLetterheadActiveOn(lastPageNum)) (margin + 74f) else (margin + 46f)

        // SEÇÃO 6: CONCLUSÃO TÉCNICA
        fillPaint.color = COLOR_NAVY
        cl.drawRoundRect(RectF(margin + 6f, lastY, PAGE_WIDTH - margin - 6f, lastY + 16f), 3f, 3f, fillPaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.WHITE
        textPaint.textSize = 7.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        cl.drawText("6. CONCLUSÃO TÉCNICA E PARECER DE APTIDÃO", margin + 14f, lastY + 11f, textPaint)

        lastY += 22f
        val conclusionCard = RectF(margin + 6f, lastY, PAGE_WIDTH - margin - 6f, lastY + 68f)
        fillPaint.color = 0xFFF8FAFC.toInt()
        cl.drawRoundRect(conclusionCard, 4f, 4f, fillPaint)
        strokePaint.color = 0xFFCBD5E1.toInt()
        strokePaint.strokeWidth = 0.8f
        cl.drawRoundRect(conclusionCard, 4f, 4f, strokePaint)

        textPaint.color = COLOR_TEXT_BODY
        textPaint.textSize = 7f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        val conclText1 = if (company.reportConclusionText.isNotBlank()) {
            company.reportConclusionText
                .replace("{aprovados}", approvedCount.toString())
                .replace("{total}", totalCount.toString())
                .replace("{taxa}", String.format(Locale.US, "%.1f", approvalRate))
        } else {
            "Com base nos ensaios laboratoriais de rigidez dielétrica e nas medições de corrente de fuga executados em conformidade com as normas ABNT NBR, IEC e ASTM, conclui-se que $approvedCount dos $totalCount itens ensaiados (${String.format(Locale.US, "%.1f", approvalRate)}%) ATENDEM PLENAMENTE aos requisitos regulamentares de segurança da NR-10."
        }
        val conclText2 = "Os equipamentos APROVADOS encontram-se com sua rigidez dielétrica preservada e devidamente aptos para uso em intervenções e serviços em instalações elétricas energizadas até suas respectivas tensões nominais de trabalho. Os itens com resultado REPROVADO (se houver) tiveram sua capacidade isolante comprometida e DEVEM SER IMEDIATAMENTE INUTILIZADOS E RETIRADOS DE CIRCULAÇÃO."

        var cY = drawWrappedTextToCanvas(cl, conclText1, margin + 14f, lastY + 14f, contentWidth - 28f, 9.5f, textPaint) + 6f
        if (company.reportConclusionText.isBlank()) {
            drawWrappedTextToCanvas(cl, conclText2, margin + 14f, cY, contentWidth - 28f, 9.5f, textPaint)
        }

        // SEÇÃO 7: RECOMENDAÇÕES DE SEGURANÇA (NR-10)
        lastY += 76f
        fillPaint.color = COLOR_NAVY
        cl.drawRoundRect(RectF(margin + 6f, lastY, PAGE_WIDTH - margin - 6f, lastY + 16f), 3f, 3f, fillPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 7.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        cl.drawText("7. RECOMENDAÇÕES DE SEGURANÇA, USO E MANUTENÇÃO (NR-10)", margin + 14f, lastY + 11f, textPaint)

        lastY += 22f
        val recList = listOf(
            "• Inspeção Pré-Uso Diária: Todo usuário deve inspecionar visualmente o EPI/EPC antes de cada intervenção (teste de inflamento com ar em luvas para detecção de microfuros).",
            "• Armazenamento Adequado: Guardar os equipamentos em suas bolsas protetoras individuais, ao abrigo de luz solar direta, calor excessivo, umidade e vapores de hidrocarbonetos.",
            "• Higienização Correta: Limpar exclusivamente com água e sabão neutro. Nunca utilizar solventes clorados, alcoóis agressivos, desengraxantes químicos ou derivados de petróleo.",
            "• Descarte de Itens Reprovados: Equipamentos reprovados devem ter seu CA descaracterizado e ser cortados fisicamente para evitar reaproveitamento indevido.",
            "• Periodicidade de Reensaio: Os ensaios devem ser renovados antes do término do prazo de validade técnica indicado em cada laudo individual (semestral para luvas/mantas e anual para capacetes e ferramentas)."
        )

        recList.forEach { rec ->
            lastY = drawWrappedTextToCanvas(cl, rec, margin + 10f, lastY, contentWidth - 20f, 9.2f, textPaint) + 4f
        }

        // SEÇÃO 8: TERMO DE RESPONSABILIDADE TÉCNICA E ASSINATURA
        lastY += 8f
        fillPaint.color = COLOR_NAVY
        cl.drawRoundRect(RectF(margin + 6f, lastY, PAGE_WIDTH - margin - 6f, lastY + 16f), 3f, 3f, fillPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 7.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        cl.drawText("8. TERMO DE RESPONSABILIDADE TÉCNICA & ASSINATURA DIGITAL", margin + 14f, lastY + 11f, textPaint)

        lastY += 22f
        val sigCard = RectF(margin + 6f, lastY, PAGE_WIDTH - margin - 6f, lastY + 86f)
        fillPaint.color = 0xFFF1F5F9.toInt()
        cl.drawRoundRect(sigCard, 4f, 4f, fillPaint)
        strokePaint.color = 0xFFCBD5E1.toInt()
        strokePaint.strokeWidth = 0.8f
        cl.drawRoundRect(sigCard, 4f, 4f, strokePaint)

        // QR Code on Left
        val qrBoxSize = 64
        val qrX = margin + 16f
        val qrY = lastY + 11f
        drawQrCodeToCanvas(cl, qrX.toInt(), qrY.toInt(), qrBoxSize, "https://validador.jvmlab.com.br")

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 5.2f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        cl.drawText("Validação Digital", qrX + qrBoxSize / 2f, qrY + qrBoxSize + 7f, textPaint)

        // Signature Lines on Right
        val sigCenterX = PAGE_WIDTH - margin - 150f
        val sigLineY = lastY + 48f
        strokePaint.color = COLOR_NAVY
        strokePaint.strokeWidth = 1f
        cl.drawLine(sigCenterX - 110f, sigLineY, sigCenterX + 110f, sigLineY, strokePaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = COLOR_NAVY
        textPaint.textSize = 8.5f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        cl.drawText(company.technicalResponsibleName.ifEmpty { "Eng. João Carlos Evaristo Guedes Nunes" }, sigCenterX, sigLineY + 12f, textPaint)

        textPaint.color = COLOR_TEXT_BODY
        textPaint.textSize = 6.8f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        cl.drawText("Engenheiro Eletricista & Segurança do Trabalho", sigCenterX, sigLineY + 21f, textPaint)
        cl.drawText("CREA/DF ${company.technicalResponsibleCrea.ifEmpty { "24481/D-DF" }} • ART: $effectiveArtNum", sigCenterX, sigLineY + 30f, textPaint)

        pdfDocument.finishPage(lastPage)

        // Write to Cache
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val sanitizedClient = (client?.nomeFantasia ?: client?.razaoSocial ?: "Cliente").replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Relatorio_Completo_${effectiveReportNum}_${sanitizedClient}.pdf"
        val pdfFile = File(reportsDir, fileName)

        val fos = FileOutputStream(pdfFile)
        pdfDocument.writeTo(fos)
        fos.flush()
        fos.close()
        pdfDocument.close()

        pdfFile
    }

    /**
     * Opens the generated PDF in an external viewer or print service.
     */
    fun openPdf(context: Context, pdfFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Abrir Laudo em PDF"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao abrir PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Shares the generated PDF file via standard Android share sheet (WhatsApp, Email, Drive).
     */
    fun sharePdf(context: Context, pdfFile: File, subject: String = "Laudo Técnico NR-10") {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, "Segue anexo o documento técnico oficial em PDF: $subject.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Documento PDF"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao compartilhar PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Helper to draw section title headers
    private fun drawSectionHeader(canvas: Canvas, title: String, x: Float, y: Float, width: Float): Float {
        val fillPaint = Paint().apply {
            isAntiAlias = true
            isDither = true
            color = COLOR_NAVY
            style = Paint.Style.FILL
        }
        val textPaint = Paint().apply {
            isAntiAlias = true
            isSubpixelText = true
            isLinearText = true
            isFilterBitmap = true
            isDither = true
            letterSpacing = 0.0f
            color = Color.WHITE
            textSize = 7.5f
            typeface = getArialTypeface(Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawRoundRect(RectF(x, y, x + width, y + 14f), 2f, 2f, fillPaint)
        canvas.drawText(title, x + 8f, y + 10.2f, textPaint)
        return y + 20f
    }

    private fun drawField(
        canvas: Canvas,
        label: String,
        value: String,
        x: Float,
        y: Float,
        width: Float,
        isHighlight: Boolean = false,
        tabOffset: Float = -1f
    ) {
        val textPaint = Paint().apply {
            isAntiAlias = true
            isSubpixelText = true
            isLinearText = true
            isFilterBitmap = true
            isDither = true
            letterSpacing = 0.0f
        }
        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 6.8f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        canvas.drawText(label, x, y, textPaint)

        val valueStartX = if (tabOffset > 0f) (x + tabOffset) else (x + textPaint.measureText(label) + 4f)
        val maxValueWidth = (x + width) - valueStartX

        textPaint.color = if (isHighlight) COLOR_NAVY else COLOR_TEXT_DARK
        textPaint.textSize = 7.5f
        textPaint.typeface = getArialTypeface(if (isHighlight) Typeface.BOLD else Typeface.NORMAL)
        val formattedValue = formatWithWordBoundaries(value, maxValueWidth, textPaint)
        canvas.drawText(formattedValue, valueStartX, y, textPaint)
    }

    private fun drawCertField(
        canvas: Canvas,
        label: String,
        value: String,
        x: Float,
        y: Float,
        width: Float,
        isHighlight: Boolean = false,
        customColor: Int? = null,
        tabOffset: Float = -1f
    ) {
        val textPaint = Paint().apply {
            isAntiAlias = true
            isSubpixelText = true
            isLinearText = true
            isFilterBitmap = true
            isDither = true
            letterSpacing = 0.0f
        }
        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 7.2f
        textPaint.typeface = getArialTypeface(Typeface.NORMAL)
        canvas.drawText(label, x, y, textPaint)

        val valueStartX = if (tabOffset > 0f) (x + tabOffset) else (x + textPaint.measureText(label) + 4f)
        val maxValueWidth = (x + width) - valueStartX

        textPaint.color = customColor ?: (if (isHighlight) COLOR_NAVY else COLOR_TEXT_DARK)
        textPaint.textSize = 8.2f
        textPaint.typeface = getArialTypeface(if (isHighlight || customColor != null) Typeface.BOLD else Typeface.NORMAL)
        val formattedValue = formatWithWordBoundaries(value, maxValueWidth, textPaint)
        canvas.drawText(formattedValue, valueStartX, y, textPaint)
    }

    private fun drawParamBox(
        canvas: Canvas,
        title: String,
        value: String,
        x: Float,
        y: Float,
        width: Float,
        valueColor: Int = COLOR_NAVY
    ) {
        val fillPaint = Paint().apply {
            isAntiAlias = true
            isDither = true
            color = COLOR_CARD_BG
            style = Paint.Style.FILL
        }
        val strokePaint = Paint().apply {
            isAntiAlias = true
            isDither = true
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }
        val textPaint = Paint().apply {
            isAntiAlias = true
            isSubpixelText = true
            isLinearText = true
            isFilterBitmap = true
            isDither = true
            letterSpacing = 0.0f
            textAlign = Paint.Align.CENTER
        }

        canvas.drawRoundRect(RectF(x, y, x + width, y + 26f), 3f, 3f, fillPaint)
        canvas.drawRoundRect(RectF(x, y, x + width, y + 26f), 3f, 3f, strokePaint)

        textPaint.color = COLOR_TEXT_MUTED
        textPaint.textSize = 6.2f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText(title, x + width / 2f, y + 9f, textPaint)

        textPaint.color = valueColor
        textPaint.textSize = 8.2f
        textPaint.typeface = getArialTypeface(Typeface.BOLD)
        canvas.drawText(value, x + width / 2f, y + 21f, textPaint)
    }

    private fun breakTextIntoLines(
        text: String,
        maxWidth: Float,
        paint: Paint
    ): List<String> {
        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n")
        for (para in paragraphs) {
            val trimmedPara = para.trim()
            if (trimmedPara.isEmpty()) {
                lines.add("")
                continue
            }
            val words = trimmedPara.split(Regex("\\s+"))
            var line = ""
            for (word in words) {
                if (word.isEmpty()) continue
                val testLine = if (line.isEmpty()) word else "$line $word"
                val width = paint.measureText(testLine)
                if (width > maxWidth && line.isNotEmpty()) {
                    lines.add(line)
                    line = word
                } else {
                    line = testLine
                }
            }
            if (line.isNotEmpty()) {
                lines.add(line)
            }
        }
        return lines
    }

    private fun drawMultilineText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        lineHeight: Float,
        paint: Paint
    ): Float {
        val lines = breakTextIntoLines(text, maxWidth, paint)
        var curY = y
        for (line in lines) {
            canvas.drawText(line, x, curY, paint)
            curY += lineHeight
        }
        return curY
    }

    private fun drawWrappedTextToCanvas(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        lineHeight: Float,
        paint: Paint
    ): Float = drawMultilineText(canvas, text, x, y, maxWidth, lineHeight, paint)

    private fun drawParagraphWithIndent(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        lineHeight: Float,
        paint: Paint,
        firstLineIndent: Float = 18f,
        justify: Boolean = true
    ): Float {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return y
        val words = trimmed.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var line = ""
        var isFirst = true
        for (word in words) {
            if (word.isEmpty()) continue
            val testLine = if (line.isEmpty()) word else "$line $word"
            val allowedW = if (isFirst) (maxWidth - firstLineIndent) else maxWidth
            if (paint.measureText(testLine) > allowedW && line.isNotEmpty()) {
                lines.add(line)
                line = word
                isFirst = false
            } else {
                line = testLine
            }
        }
        if (line.isNotEmpty()) {
            lines.add(line)
        }

        var curY = y
        lines.forEachIndexed { index, l ->
            val startX = if (index == 0) (x + firstLineIndent) else x
            val isLast = index == lines.size - 1
            if (justify && !isLast) {
                val lineWords = l.split(" ").filter { it.isNotEmpty() }
                if (lineWords.size > 1) {
                    val targetW = if (index == 0) (maxWidth - firstLineIndent) else maxWidth
                    val totalWordWidth = lineWords.sumOf { paint.measureText(it).toDouble() }.toFloat()
                    val extraSpace = targetW - totalWordWidth
                    val spaceWidth = if (extraSpace > 0f) extraSpace / (lineWords.size - 1) else paint.measureText(" ")
                    var wx = startX
                    for (w in lineWords) {
                        canvas.drawText(w, wx, curY, paint)
                        wx += paint.measureText(w) + spaceWidth
                    }
                } else {
                    canvas.drawText(l, startX, curY, paint)
                }
            } else {
                canvas.drawText(l, startX, curY, paint)
            }
            curY += lineHeight
        }
        return curY
    }

    /** QR Code real do endereço do validador (mesmo da web) */
    private fun drawQrCodeToCanvas(canvas: Canvas, startX: Int, startY: Int, size: Int, data: String) {
        com.example.util.QrCode.draw(canvas, startX.toFloat(), startY.toFloat(), size.toFloat(), data, COLOR_NAVY)
    }
}
