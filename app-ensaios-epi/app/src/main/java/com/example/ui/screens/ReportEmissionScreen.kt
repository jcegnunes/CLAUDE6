package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppSettingEntity
import com.example.ui.components.BatchEmissionDialog
import com.example.ui.components.LetterheadConfigDialog
import com.example.ui.components.ReportConfigDialog
import com.example.ui.components.StatusBadge
import com.example.ui.theme.appTextFieldColors
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.StatusGreen
import com.example.ui.viewmodel.DielectricViewModel
import com.example.util.DateUtils
import com.example.util.PdfExporter
import com.example.util.WordExporter
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportEmissionScreen(
    viewModel: DielectricViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val tests by viewModel.tests.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var isExportingPdf by remember { mutableStateOf(false) }
    var isExportingDoc by remember { mutableStateOf(false) }
    var isSharingPdf by remember { mutableStateOf(false) }
    var showBatchDialog by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showLetterheadDialog by remember { mutableStateOf(false) }

    var selectedClientId by remember { mutableStateOf(clients.firstOrNull()?.id ?: "") }
    var reportType by remember { mutableStateOf("COMPLETO") } // COMPLETO, SIMPLIFICADO or CONSOLIDADO

    val clientTests = tests.filter { selectedClientId.isEmpty() || it.clientId == selectedClientId }
    val currentClient = clients.find { it.id == selectedClientId }

    // Group tests by collaborator when in simplified mode
    val groupedByCollaborator = remember(clientTests) {
        clientTests.groupBy { it.collaboratorName.ifBlank { "Geral / Sem Colaborador" } }
    }

    if (showBatchDialog && clientTests.isNotEmpty()) {
        BatchEmissionDialog(
            selectedTests = clientTests,
            company = settings ?: AppSettingEntity(),
            onDismiss = { showBatchDialog = false }
        )
    }

    if (showConfigDialog) {
        ReportConfigDialog(
            currentSettings = settings ?: AppSettingEntity(),
            onSave = { updatedSettings ->
                viewModel.saveSettings(updatedSettings)
                Toast.makeText(context, "Textos do relatório atualizados com sucesso!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showConfigDialog = false }
        )
    }

    if (showLetterheadDialog) {
        LetterheadConfigDialog(
            currentSettings = settings ?: AppSettingEntity(),
            onSave = { updatedSettings ->
                viewModel.saveSettings(updatedSettings)
                showLetterheadDialog = false
                Toast.makeText(context, "Configurações de papel timbrado salvas!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showLetterheadDialog = false }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(NavyPrimary, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Emissão de Relatório Técnico", fontWeight = FontWeight.Black, fontSize = 20.sp, color = NavyPrimary)
                    Text("Relatório Completo (Capa, Normas e RT), Simplificado por colaborador ou Consolidado", fontSize = 11.sp, color = Color(0xFF64748B))
                }
            }
        }

        // Filters Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Client Selector
                    var clientExp by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = clientExp,
                        onExpandedChange = { clientExp = !clientExp }
                    ) {
                        OutlinedTextField(
                            value = currentClient?.nomeFantasia ?: currentClient?.razaoSocial ?: "Todos os Clientes",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Filtrar por Cliente", color = Color(0xFF0F172A)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clientExp) },
                            colors = appTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = clientExp, onDismissRequest = { clientExp = false }) {
                            DropdownMenuItem(text = { Text("Todos os Clientes", color = Color(0xFF0F172A), fontWeight = FontWeight.Medium) }, onClick = { selectedClientId = ""; clientExp = false })
                            clients.forEach { c ->
                                DropdownMenuItem(text = { Text(c.nomeFantasia.ifEmpty { c.razaoSocial }, color = Color(0xFF0F172A), fontWeight = FontWeight.Medium) }, onClick = { selectedClientId = c.id; clientExp = false })
                            }
                        }
                    }

                    // Report Type Toggle (3 options)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { reportType = "COMPLETO" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (reportType == "COMPLETO") NavyPrimary else Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Text("Completo (NR-10)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (reportType == "COMPLETO") Color.White else Color(0xFF475569))
                        }

                        Button(
                            onClick = { reportType = "SIMPLIFICADO" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (reportType == "SIMPLIFICADO") NavyPrimary else Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simplificado", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (reportType == "SIMPLIFICADO") Color.White else Color(0xFF475569))
                        }

                        Button(
                            onClick = { reportType = "CONSOLIDADO" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (reportType == "CONSOLIDADO") NavyPrimary else Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Consolidado", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (reportType == "CONSOLIDADO") Color.White else Color(0xFF475569))
                        }
                    }
                }
            }
        }

        // Batch Emission Option Card
        if (clientTests.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(NavyPrimary, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Emissão Agrupada (Laudos & Certificados)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = NavyPrimary
                                )
                                Text(
                                    text = "Gere em lote todos os ${clientTests.size} laudos e certificados individuais deste cliente em PDF ou Word",
                                    fontSize = 10.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }

                        Button(
                            onClick = { showBatchDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("open_batch_dialog_from_reports_button")
                        ) {
                            Icon(imageVector = Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Emitir em Lote", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Preview & Actions Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(settings?.name ?: "JVM ENGENHARIA & TREINAMENTOS", fontWeight = FontWeight.Black, fontSize = 13.sp, color = NavyPrimary)
                            Text(
                                when (reportType) {
                                    "COMPLETO" -> "Relatório Técnico Completo (Capa, Sumário, Normas, Metodologia, Tabulação, Parecer e RT)"
                                    "SIMPLIFICADO" -> "Relatório Simplificado por Colaborador (${clientTests.size} itens • ${groupedByCollaborator.size} colaboradores)"
                                    else -> "Relatório Consolidado Geral (${clientTests.size} itens)"
                                },
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // PDF Button
                            Button(
                                onClick = {
                                    if (!isExportingPdf) {
                                        isExportingPdf = true
                                        scope.launch {
                                            try {
                                                val company = settings ?: AppSettingEntity()
                                                val pdf = when (reportType) {
                                                    "COMPLETO" -> PdfExporter.generateRelatorioCompletoPdf(context, clientTests, currentClient, company)
                                                    "SIMPLIFICADO" -> PdfExporter.generateRelatorioSimplificadoPdf(context, clientTests, currentClient, company)
                                                    else -> PdfExporter.generateRelatorioConsolidadoPdf(context, clientTests, currentClient, company, reportType)
                                                }
                                                PdfExporter.openPdf(context, pdf)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Erro ao gerar PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                isExportingPdf = false
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isExportingPdf) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Word Doc Button
                            Button(
                                onClick = {
                                    if (!isExportingDoc) {
                                        isExportingDoc = true
                                        scope.launch {
                                            try {
                                                val company = settings ?: AppSettingEntity()
                                                val doc = when (reportType) {
                                                    "COMPLETO" -> WordExporter.generateRelatorioCompletoDoc(context, clientTests, currentClient, company)
                                                    "SIMPLIFICADO" -> WordExporter.generateRelatorioSimplificadoDoc(context, clientTests, currentClient, company)
                                                    else -> WordExporter.generateRelatorioSimplificadoDoc(context, clientTests, currentClient, company)
                                                }
                                                WordExporter.openDoc(context, doc)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Erro ao gerar Word: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                isExportingDoc = false
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isExportingDoc) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.Article, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Word", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Share Button
                            Button(
                                onClick = {
                                    if (!isSharingPdf) {
                                        isSharingPdf = true
                                        scope.launch {
                                            try {
                                                val company = settings ?: AppSettingEntity()
                                                val pdf = when (reportType) {
                                                    "COMPLETO" -> PdfExporter.generateRelatorioCompletoPdf(context, clientTests, currentClient, company)
                                                    "SIMPLIFICADO" -> PdfExporter.generateRelatorioSimplificadoPdf(context, clientTests, currentClient, company)
                                                    else -> PdfExporter.generateRelatorioConsolidadoPdf(context, clientTests, currentClient, company, reportType)
                                                }
                                                val label = if (reportType == "COMPLETO") "Relatório Técnico Completo NR-10" else "Relatório de Ensaios NR-10"
                                                PdfExporter.sharePdf(context, pdf, "$label - ${currentClient?.nomeFantasia ?: "Geral"}")
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Erro ao compartilhar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                isSharingPdf = false
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isSharingPdf) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Enviar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // Letterhead status banner
                    val currentSettings = settings
                    val isLetterheadActive = currentSettings?.useLetterhead == true && !currentSettings.letterheadUri.isNullOrBlank()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isLetterheadActive) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (isLetterheadActive) Color(0xFF86EFAC) else Color(0xFFE2E8F0),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = if (isLetterheadActive) StatusGreen else NavyPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isLetterheadActive && currentSettings != null)
                                    "Papel Timbrado Oficial Ativo (${(currentSettings.letterheadOpacity * 100).toInt()}% opacidade)"
                                else
                                    "Papel Timbrado: Inativo (padrão JVM)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = NavyPrimary
                            )
                        }
                        TextButton(
                            onClick = { showLetterheadDialog = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Configurar Modelo", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                        }
                    }

                    if (reportType == "COMPLETO") {
                        // Overview of the Complete Report Sections
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Estrutura do Relatório Técnico Completo", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                                Text("Conforme NR-10 e ABNT", fontSize = 10.sp, color = ElectricBlue, fontWeight = FontWeight.Bold)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("• Pág. 1: Capa Oficial com Identificação do Solicitante, Executante e Indicadores", fontSize = 10.sp, color = Color(0xFF334155))
                                Text("• Pág. 2: Sumário, Introdução Regulamentar (NR-10.4.3.1 e 10.7.8) e Objetivos", fontSize = 10.sp, color = Color(0xFF334155))
                                Text("• Pág. 3: Normas Técnicas Aplicadas (ABNT NBR 8221, NBR 9699, IEC 60903, ASTM D120) e Metodologia", fontSize = 10.sp, color = Color(0xFF334155))
                                Text("• Págs. 4+: Tabulação Detalhada dos Ensaios (${clientTests.size} itens com TAG, CA, Tensão, Fuga e Validade)", fontSize = 10.sp, color = Color(0xFF334155))
                                Text("• Pág. Final: Conclusão Técnica, Recomendações de Segurança e Termo de RT com QR Code", fontSize = 10.sp, color = Color(0xFF334155))
                            }

                            Button(
                                onClick = { showConfigDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_report_text_config_button")
                            ) {
                                Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Editar Introdução, Objetivo, Metodologia e Conclusão", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Tabulated Items
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NavyPrimary, RoundedCornerShape(6.dp))
                                .padding(vertical = 6.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tag / CA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1.2f))
                            Text("Equipamento / Colab.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1.5f))
                            Text("Tensão / Fuga", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1.2f))
                            Text("Status / Val.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1.1f))
                        }

                        clientTests.forEach { test ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(4.dp))
                                    .border(0.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                                    .padding(vertical = 5.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text(test.equipmentTag, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NavyPrimary)
                                    Text(test.equipmentCa.ifEmpty { "CA N/C" }, fontSize = 8.5.sp, color = Color(0xFF64748B))
                                }
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text(test.equipmentType.replace("_", " "), fontSize = 9.5.sp, fontWeight = FontWeight.Medium)
                                    Text(test.collaboratorName.ifEmpty { "Geral" }, fontSize = 8.5.sp, color = Color(0xFF64748B))
                                }
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text("${test.appliedVoltage_kV.toInt()} kV (${test.voltageType})", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                                    Text("${test.measuredLeakageCurrent_mA} mA", fontSize = 8.5.sp, color = Color(0xFF64748B))
                                }
                                Column(modifier = Modifier.weight(1.1f)) {
                                    StatusBadge(status = test.result)
                                    Text(DateUtils.formatDate(test.retestDueDate), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (reportType == "SIMPLIFICADO") {
                        // Display items grouped by Collaborator
                        groupedByCollaborator.forEach { (colabName, colabTests) ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Collaborator Header Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(colabName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                                        val registration = colabTests.firstOrNull()?.collaboratorRegistration?.ifEmpty { colabTests.firstOrNull()?.collaboratorSector } ?: ""
                                        if (registration.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("($registration)", fontSize = 10.sp, color = Color(0xFF64748B))
                                        }
                                    }

                                    Text("${colabTests.size} item(ns)", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = ElectricBlue)
                                }

                                // Collaborator tests table header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(NavyPrimary, RoundedCornerShape(4.dp))
                                        .padding(vertical = 4.dp, horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Tag / CA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, modifier = Modifier.weight(1.2f))
                                    Text("Equipamento", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, modifier = Modifier.weight(1.5f))
                                    Text("Tensão / Fuga", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, modifier = Modifier.weight(1.2f))
                                    Text("Validade / Status", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, modifier = Modifier.weight(1.2f))
                                }

                                colabTests.forEach { test ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White, RoundedCornerShape(4.dp))
                                            .border(0.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                                            .padding(vertical = 4.dp, horizontal = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1.2f)) {
                                            Text(test.equipmentTag, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = NavyPrimary)
                                            Text(test.equipmentCa.ifEmpty { "CA N/C" }, fontSize = 8.5.sp, color = Color(0xFF64748B))
                                        }
                                        Column(modifier = Modifier.weight(1.5f)) {
                                            Text(test.equipmentType.replace("_", " "), fontSize = 9.5.sp, fontWeight = FontWeight.Medium)
                                            Text("Classe ${test.equipmentClass}", fontSize = 8.5.sp, color = Color(0xFF64748B))
                                        }
                                        Column(modifier = Modifier.weight(1.2f)) {
                                            Text("${test.appliedVoltage_kV.toInt()} kV (${test.voltageType})", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                                            Text("${test.measuredLeakageCurrent_mA} mA", fontSize = 8.5.sp, color = Color(0xFF64748B))
                                        }
                                        Column(modifier = Modifier.weight(1.2f)) {
                                            StatusBadge(status = test.result)
                                            Text(DateUtils.formatDate(test.retestDueDate), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Standard Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NavyPrimary, RoundedCornerShape(6.dp))
                                .padding(vertical = 6.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tag / CA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1.2f))
                            Text("Colaborador", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1.4f))
                            Text("Tensão / Fuga", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1.2f))
                            Text("Status / Val.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1.2f))
                        }

                        clientTests.forEach { test ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFFF1F5F9))
                                    .padding(vertical = 6.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text(test.equipmentTag, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    Text(test.equipmentCa.ifEmpty { "CA N/A" }, fontSize = 9.sp, color = Color(0xFF64748B))
                                }
                                Column(modifier = Modifier.weight(1.4f)) {
                                    Text(test.collaboratorName.ifEmpty { "Geral" }, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                    Text(test.collaboratorSector.ifEmpty { "-" }, fontSize = 9.sp, color = Color(0xFF94A3B8))
                                }
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text("${test.appliedVoltage_kV} kV", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                                    Text("${test.measuredLeakageCurrent_mA} mA", fontSize = 9.sp, color = Color(0xFF64748B))
                                }
                                Column(modifier = Modifier.weight(1.2f)) {
                                    StatusBadge(status = test.result)
                                    Text(DateUtils.formatDate(test.retestDueDate), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF059669))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
