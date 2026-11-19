package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.AppSettingEntity
import com.example.data.model.ManualToolItem
import com.example.data.model.TestRecordEntity
import com.example.data.service.BluetoothPrinterDevice
import com.example.data.service.LabelPaperSize
import com.example.data.service.NiimbotPrinterManager
import com.example.data.service.PrinterConnectionState
import com.example.data.service.PrinterProtocol
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import kotlinx.coroutines.launch
import com.example.util.PdfExporter
import com.example.util.WordExporter
import com.example.util.JcpsExporter
import com.example.util.SavedJcpsResult
import java.io.File

@Composable
fun LaudoDialog(
    test: TestRecordEntity,
    company: AppSettingEntity,
    onDismiss: () -> Unit,
    onOpenNiimbot: (() -> Unit)? = null,
    onEditTest: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExportingPdf by remember { mutableStateOf(false) }
    var isSharingPdf by remember { mutableStateOf(false) }
    var isExportingWord by remember { mutableStateOf(false) }
    var isSharingWord by remember { mutableStateOf(false) }
    var isExportingRelatorioCompletoPdf by remember { mutableStateOf(false) }
    var isSharingRelatorioCompletoPdf by remember { mutableStateOf(false) }
    var isExportingRelatorioCompletoDoc by remember { mutableStateOf(false) }
    val isApproved = test.result == "APROVADO"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Header Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyPrimary)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = SafetyOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Laudo Técnico de Ensaio Dielétrico",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${test.reportNumber} • OS: ${test.serviceOrderNumber}",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onEditTest != null) {
                            IconButton(
                                onClick = onEditTest,
                                modifier = Modifier.testTag("edit_laudo_header_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar Laudo",
                                    tint = SafetyOrange
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                        }
                    }
                }

                // Printable Document Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Company Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (company.companyLogoUri.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .height(42.dp)
                                        .width(70.dp)
                                        .background(Color.White, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = File(company.companyLogoUri).takeIf { it.exists() } ?: company.companyLogoUri,
                                        contentDescription = "Logo",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(NavyPrimary, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("JVM", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(company.name, fontWeight = FontWeight.Black, fontSize = 14.sp, color = NavyPrimary)
                                Text(company.legalName, fontSize = 10.sp, color = Color(0xFF64748B))
                                Text("${company.creaCompanyRegister} • CNPJ: ${company.cnpj}", fontSize = 10.sp, color = Color(0xFF64748B))
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("IDENTIFICADOR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                            Text(test.reportNumber, fontSize = 13.sp, fontWeight = FontWeight.Black, color = ElectricBlue, fontFamily = FontFamily.Monospace)
                            if (test.artNumber.isNotEmpty()) {
                                Text("ART: ${test.artNumber}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                            }
                        }
                    }

                    HorizontalDivider(color = NavyPrimary, thickness = 2.dp)

                    // 1. Identificação
                    SectionHeader("1. Identificação do Cliente e do Equipamento")
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                InfoItem("Cliente:", test.clientName, modifier = Modifier.weight(1f))
                                InfoItem("Ordem de Serviço:", test.serviceOrderNumber, isMono = true, modifier = Modifier.weight(1f))
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                InfoItem("Equipamento:", if (test.equipmentType.equals("DETECTOR_INDUCAO", ignoreCase = true)) "DETECTOR DE TENSÃO POR INDUÇÃO" else test.equipmentType.replace("_", " ").uppercase(), modifier = Modifier.weight(1f))
                                InfoItem("Tag / Patrimônio:", test.equipmentTag, isMono = true, isHighlight = true, modifier = Modifier.weight(1f))
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                InfoItem("Classe Dielétrica:", "Classe ${test.equipmentClass}", modifier = Modifier.weight(1f))
                                InfoItem("Nº de Série / CA:", "${test.equipmentSerial} • CA: ${test.equipmentCa.ifEmpty { "N/A" }}", modifier = Modifier.weight(1f))
                            }
                            if (test.collaboratorName.isNotEmpty()) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    InfoItem("Colaborador:", test.collaboratorName, modifier = Modifier.weight(1f))
                                    InfoItem("Matrícula / Setor:", "${test.collaboratorRegistration} • ${test.collaboratorSector}", modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // 2. Condições Ambientais e Normas
                    val isGlove = test.equipmentType.contains("LUVA", ignoreCase = true) ||
                            test.equipmentType.contains("Luvas", ignoreCase = true) ||
                            test.normCode.contains("16295") ||
                            test.equipmentType.equals("LUVA_ISOLANTE", ignoreCase = true)

                    SectionHeader("2. Referência Normativa e Condições Ambientais")
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                InfoItem("Norma Regulamentadora / Técnica:", test.normCode.ifEmpty { "NBR 16295 Tabela 4" }, modifier = Modifier.weight(1.3f))
                                InfoItem("Procedimento de Ensaio:", "PR-JVM-LAB-01 Rev.08", modifier = Modifier.weight(1.2f))
                                InfoItem("Temperatura Ambiente:", "${test.temperatureC} °C", modifier = Modifier.weight(0.9f))
                                InfoItem("Umidade Relativa:", "${test.relativeHumidityPercent}% UR", modifier = Modifier.weight(0.8f))
                            }

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

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFF0F7FF),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Header com título e badge da luva ensaiada
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .background(ElectricBlue, CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "TABELA 4 DA ABNT NBR 16295 / IEC 60903 – ENSAIOS DE PROVA E RIGIDEZ DIELÉTRICA",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NavyPrimary
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFDBEAFE)
                                            ) {
                                                Text(
                                                    text = "Luva Ensaiada: Classe $cleanClass - ${gloveLen} mm",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1E40AF),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        // Colunas do cabeçalho
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                                                .padding(vertical = 4.dp, horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Classe", fontWeight = FontWeight.Bold, fontSize = 8.sp, color = NavyPrimary, modifier = Modifier.weight(1f))
                                            Text("Tensão Máx. Uso", fontWeight = FontWeight.Bold, fontSize = 8.sp, color = NavyPrimary, modifier = Modifier.weight(1.1f))
                                            Text("Tensão Prova CA", fontWeight = FontWeight.Bold, fontSize = 8.sp, color = NavyPrimary, modifier = Modifier.weight(1.1f))
                                            Text("280 mm", fontWeight = FontWeight.Bold, fontSize = 8.sp, color = NavyPrimary, modifier = Modifier.weight(1f))
                                            Text("360 mm", fontWeight = FontWeight.Bold, fontSize = 8.sp, color = NavyPrimary, modifier = Modifier.weight(1.1f))
                                            Text("410 mm", fontWeight = FontWeight.Bold, fontSize = 8.sp, color = NavyPrimary, modifier = Modifier.weight(1f))
                                            Text(">= 460 mm", fontWeight = FontWeight.Bold, fontSize = 8.sp, color = NavyPrimary, modifier = Modifier.weight(1f))
                                            Text("Tensão Rigidez", fontWeight = FontWeight.Bold, fontSize = 8.sp, color = NavyPrimary, modifier = Modifier.weight(1.1f))
                                        }

                                        // Linha com o parâmetro respectivo do ensaio da luva selecionado
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFEFF6FF), RoundedCornerShape(4.dp))
                                                .border(1.dp, Color(0xFF93C5FD), RoundedCornerShape(4.dp))
                                                .padding(vertical = 4.dp, horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Classe ${gloveRow.classe}", fontWeight = FontWeight.Bold, fontSize = 8.5.sp, color = Color(0xFF1D4ED8), modifier = Modifier.weight(1f))
                                            Text("${gloveRow.tensaoUsoMaxAC_kV} kV", fontWeight = FontWeight.Bold, fontSize = 8.5.sp, color = Color(0xFF1D4ED8), modifier = Modifier.weight(1.1f))
                                            Text("${gloveRow.tensaoProvaAC_kV} kV", fontWeight = FontWeight.Bold, fontSize = 8.5.sp, color = Color(0xFF1D4ED8), modifier = Modifier.weight(1.1f))

                                            // 280 mm
                                            if (activeColIndex == 0) {
                                                Surface(color = ElectricBlue, shape = RoundedCornerShape(3.dp), modifier = Modifier.weight(1f)) {
                                                    Text("${gloveRow.limite280mm?.toInt() ?: "N/a"} mA [Alvo]", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 7.5.sp, modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp))
                                                }
                                            } else {
                                                Text(gloveRow.limite280mm?.let { "${it.toInt()} mA" } ?: "N/a", fontSize = 8.5.sp, color = if (gloveRow.limite280mm != null) Color(0xFF1E293B) else Color(0xFFDC2626), modifier = Modifier.weight(1f))
                                            }

                                            // 360 mm
                                            if (activeColIndex == 1) {
                                                Surface(color = ElectricBlue, shape = RoundedCornerShape(3.dp), modifier = Modifier.weight(1.1f)) {
                                                    Text("${gloveRow.limite360mm?.toInt() ?: "N/a"} mA [Alvo]", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 7.5.sp, modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp))
                                                }
                                            } else {
                                                Text(gloveRow.limite360mm?.let { "${it.toInt()} mA" } ?: "N/a", fontSize = 8.5.sp, color = if (gloveRow.limite360mm != null) Color(0xFF1E293B) else Color(0xFFDC2626), modifier = Modifier.weight(1.1f))
                                            }

                                            // 410 mm
                                            if (activeColIndex == 2) {
                                                Surface(color = ElectricBlue, shape = RoundedCornerShape(3.dp), modifier = Modifier.weight(1f)) {
                                                    Text("${gloveRow.limite410mm?.toInt() ?: "N/a"} mA [Alvo]", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 7.5.sp, modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp))
                                                }
                                            } else {
                                                Text(gloveRow.limite410mm?.let { "${it.toInt()} mA" } ?: "N/a", fontSize = 8.5.sp, color = if (gloveRow.limite410mm != null) Color(0xFF1E293B) else Color(0xFFDC2626), modifier = Modifier.weight(1f))
                                            }

                                            // >= 460 mm
                                            if (activeColIndex == 3) {
                                                Surface(color = ElectricBlue, shape = RoundedCornerShape(3.dp), modifier = Modifier.weight(1f)) {
                                                    Text("${gloveRow.limite460mm?.toInt() ?: "N/a"} mA [Alvo]", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 7.5.sp, modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp))
                                                }
                                            } else {
                                                Text(gloveRow.limite460mm?.let { "${it.toInt()} mA" } ?: "N/a", fontSize = 8.5.sp, color = if (gloveRow.limite460mm != null) Color(0xFF1E293B) else Color(0xFFDC2626), modifier = Modifier.weight(1f))
                                            }

                                            // Tensão Rigidez
                                            Text("${gloveRow.tensaoRigidezAC_kV} kV", fontWeight = FontWeight.Bold, fontSize = 8.5.sp, color = Color(0xFF1D4ED8), modifier = Modifier.weight(1.1f))
                                        }

                                        // Critério Operativo & Nota
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Critério Operativo: Luva Classe $cleanClass ($gloveLen mm) -> Tensão de Prova: ${test.appliedVoltage_kV} kV CA | Limite Máximo de Fuga: ${test.leakageCurrentLimit_mA} mA.",
                                                fontSize = 7.5.sp,
                                                color = Color(0xFF475569)
                                            )
                                            Text(
                                                text = "Nota: N/a = Não aplicável conforme Tabela 4 da ABNT NBR 16295.",
                                                fontSize = 7.5.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Medições Elétricas
                    SectionHeader("3. Parâmetros e Resultados das Medições")
                    if (test.equipmentType.equals("DETECTOR_INDUCAO", ignoreCase = true)) {
                        InductionLaudoSection(test)
                    } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpeedometerGauge(
                            title = "Tensão Aplicada",
                            value = test.appliedVoltage_kV,
                            unit = "kV ${test.voltageType}",
                            maxScale = (test.appliedVoltage_kV * 1.4).coerceAtLeast(15.0),
                            isVoltage = true,
                            subtext = "Tempo: ${test.applicationDurationSeconds}s contínuos",
                            modifier = Modifier.weight(1f)
                        )
                        SpeedometerGauge(
                            title = "Corrente de Fuga",
                            value = test.measuredLeakageCurrent_mA,
                            unit = test.currentUnit,
                            maxScale = (test.leakageCurrentLimit_mA * 1.5).coerceAtLeast(15.0),
                            limit = test.leakageCurrentLimit_mA,
                            isConforming = test.measuredLeakageCurrent_mA <= test.leakageCurrentLimit_mA,
                            isVoltage = false,
                            subtext = "Limite: ${test.leakageCurrentLimit_mA} mA",
                            modifier = Modifier.weight(1f)
                        )
                    }

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
                    val leakNormReq = if (isGloveSection3) "Máximo $leakLimitStr mA (Tab. 4 - Cl. $cleanClassS3 / ${test.gloveLength_mm} mm)"
                    else "Máximo $leakLimitStr ${test.currentUnit.ifEmpty { "mA" }} (${test.normCode.ifEmpty { "Norma Técnica" }})"

                    val rigidityNormReq = if (isGloveSection3) "Sem disrupção (Tab. 4 - Rigidez: ${gloveRowS3.tensaoRigidezAC_kV} kV CA)"
                    else "Sem disrupção / perfuração dielétrica"

                    val isLeakOk = test.measuredLeakageCurrent_mA <= test.leakageCurrentLimit_mA
                    val isRigidityOk = test.result == "APROVADO" || isLeakOk

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE2E8F0))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Parâmetro de Ensaio", fontWeight = FontWeight.Bold, fontSize = 8.5.sp, color = NavyPrimary, modifier = Modifier.weight(1.3f))
                                Text("Exigência Normativa", fontWeight = FontWeight.Bold, fontSize = 8.5.sp, color = NavyPrimary, modifier = Modifier.weight(1.8f))
                                Text("Valor Medido", fontWeight = FontWeight.Bold, fontSize = 8.5.sp, color = NavyPrimary, modifier = Modifier.weight(1.1f))
                                Text("Avaliação", fontWeight = FontWeight.Bold, fontSize = 8.5.sp, color = NavyPrimary, modifier = Modifier.weight(0.9f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }

                            // Linha 1: Tensão de Ensaio Aplicada
                            SummaryParamRow(
                                param = "Tensão de Ensaio Aplicada",
                                req = voltageNormReq,
                                measured = "${test.appliedVoltage_kV} kV ${test.voltageType.ifEmpty { "AC" }}",
                                isConforming = true
                            )
                            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

                            // Linha 2: Tempo de Aplicação da Tensão
                            SummaryParamRow(
                                param = "Tempo de Aplicação da Tensão",
                                req = timeNormReq,
                                measured = "${test.applicationDurationSeconds} s",
                                isConforming = true
                            )
                            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

                            // Linha 3: Corrente de Fuga
                            SummaryParamRow(
                                param = "Corrente de Fuga",
                                req = leakNormReq,
                                measured = "${test.measuredLeakageCurrent_mA} ${test.currentUnit.ifEmpty { "mA" }}",
                                isConforming = isLeakOk
                            )
                            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

                            // Linha 4: Rigidez / Suportabilidade à Perfuração
                            SummaryParamRow(
                                param = "Rigidez / Suportabilidade à Perfuração",
                                req = rigidityNormReq,
                                measured = if (isRigidityOk) "Sem perfuração dielétrica" else "Perfuração dielétrica detectada",
                                isConforming = isRigidityOk
                            )
                        }
                    }
                    }

                    // 4. Inspeção Visual e Físico-Mecânica
                    SectionHeader("4. Inspeção Visual e Físico-Mecânica")

                    val isGloveSection4 = test.equipmentType.contains("LUVA", ignoreCase = true) ||
                            test.equipmentType.contains("Luvas", ignoreCase = true) ||
                            test.normCode.contains("16295") ||
                            test.equipmentType.equals("LUVA_ISOLANTE", ignoreCase = true)

                    val isVisualConforming = !test.visualInspectionSummary.contains("não", ignoreCase = true) &&
                            !test.visualInspectionSummary.contains("reprovad", ignoreCase = true) &&
                            !test.visualInspectionSummary.contains("avaria", ignoreCase = true)

                    val visualItem1Left = if (isGloveSection4) "Ensaio manual de retenção e inflamento de ar (ausência de microporos/furos - estanqueidade)"
                    else "Inspeção estrutural, mecânica e dimensional segundo norma aplicável"

                    val visualItem1Right = "Superfície interna e externa isenta de cortes, perfurações, rasgos ou desgaste"
                    val visualItem2Left = "Ausência de trincas, fissuras, endurecimento ou degradação por ozônio"
                    val visualItem2Right = "Isento de impregnação de óleo, graxa, solventes ou compostos químicos"
                    val visualItem3Left = if (isGloveSection4) "Marcação indelével do Fabricante, Classe de Tensão, Norma NBR 16295 e CA"
                    else "Marcação indelével do Fabricante, Classe de Tensão, Norma Técnica e CA"

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                          if (test.equipmentType.equals("DETECTOR_INDUCAO", ignoreCase = true)) {
                            // detector por indução: os itens reais da inspeção (como no laudo da web)
                            val items = com.example.domain.NormsEngine.getVisualChecklist(com.example.data.model.EquipmentType.DETECTOR_INDUCAO)
                                .map { it.title to !test.visualInspectionSummary.contains(it.title, ignoreCase = true) }
                            items.chunked(2).forEachIndexed { idx, pair ->
                                if (idx > 0) HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.6.dp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    VisualInspectionItem(pair[0].first, pair[0].second, modifier = Modifier.weight(1f))
                                    if (pair.size > 1) VisualInspectionItem(pair[1].first, pair[1].second, modifier = Modifier.weight(1f))
                                    else Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                          } else {
                            // Row 1
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                VisualInspectionItem(visualItem1Left, isVisualConforming, modifier = Modifier.weight(1f))
                                VisualInspectionItem(visualItem1Right, isVisualConforming, modifier = Modifier.weight(1f))
                            }
                            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.6.dp)

                            // Row 2
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                VisualInspectionItem(visualItem2Left, isVisualConforming, modifier = Modifier.weight(1f))
                                VisualInspectionItem(visualItem2Right, isVisualConforming, modifier = Modifier.weight(1f))
                            }
                            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.6.dp)

                            // Row 3
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                VisualInspectionItem(visualItem3Left, isVisualConforming, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.weight(1f))
                            }
                          }
                        }
                    }

                    // 5. Parecer Conclusivo
                    val parsedTools = ManualToolItem.parseListFromNotes(test.technicalNotes)
                    val approvedTools = ManualToolItem.getApprovedTools(parsedTools)
                    val rejectedTools = ManualToolItem.getRejectedTools(parsedTools)
                    val hasDualOpinions = parsedTools.isNotEmpty() && rejectedTools.isNotEmpty() && approvedTools.isNotEmpty()

                    if (hasDualOpinions) {
                        SectionHeader("5. Pareceres Técnicos Conclusivos (Dois Pareceres Emitidos)")
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Parecer 1: Ferramentas Aprovadas
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFECFDF5), RoundedCornerShape(8.dp))
                                    .border(1.5.dp, StatusGreen, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = StatusGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "PARECER 1: FERRAMENTAS APROVADAS (${approvedTools.sumOf { it.quantity }} un.) - CONFORME P/ USO",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = Color(0xFF065F46)
                                        )
                                    }
                                    Text(
                                        text = "As ferramentas manuais abaixo identificadas foram aprovadas na inspeção visual e no ensaio de rigidez dielétrica (10 kV CA / 180s - ABNT NBR 9699 / IEC 60900), com corrente de fuga inferior a 1,0 mA/unidade e sem perfuração. Encontram-se LIBERADAS PARA OPERAÇÃO em circuitos elétricos até 1000 V CA / 1500 V CC conforme NR-10.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF334155),
                                        lineHeight = 15.sp
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        approvedTools.forEach { tool ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.White, RoundedCornerShape(4.dp))
                                                    .border(0.5.dp, StatusGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "✓ ${tool.name} (${tool.quantity} un.)",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF065F46)
                                                )
                                                Text(
                                                    text = "APROVADO P/ USO",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = StatusGreen
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Validade / Próximo Reensaio:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                                        val retestDateToolsStr = if (test.result == "APROVADO") com.example.util.DateUtils.formatDate(test.retestDueDate) else ""
                                        Text(retestDateToolsStr, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = Color(0xFF065F46))
                                    }
                                }
                            }

                            // Parecer 2: Ferramentas Reprovadas
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                                    .border(1.5.dp, StatusRed, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = StatusRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "PARECER 2: FERRAMENTAS REPROVADAS (${rejectedTools.sumOf { it.quantity }} un.) - CONDENADAS",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = Color(0xFF991B1B)
                                        )
                                    }
                                    Text(
                                        text = "As ferramentas manuais abaixo identificadas foram REPROVADAS / CONDENADAS por apresentarem não conformidades que violam os requisitos da ABNT NBR 9699 e NR-10. Fica terminantemente PROIBIDO SEU USO. Determina-se a SEGREGAÇÃO FÍSICA IMEDIATA, INUTILIZAÇÃO (DESTRUIÇÃO DO ISOLAMENTO) E DESCARTE (NR-10 item 10.4.3.1).",
                                        fontSize = 11.sp,
                                        color = Color(0xFF334155),
                                        lineHeight = 15.sp
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        rejectedTools.forEach { tool ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.White, RoundedCornerShape(4.dp))
                                                    .border(0.5.dp, StatusRed.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "✗ ${tool.name} (${tool.quantity} un.)",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = StatusRed
                                                    )
                                                    Text(
                                                        text = "REPROVADO / CONDENADO",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = StatusRed
                                                    )
                                                }
                                                if (tool.rejectionReason.isNotBlank()) {
                                                    Text(
                                                        text = "Motivo da Reprovação: ${tool.rejectionReason}",
                                                        fontSize = 9.sp,
                                                        color = Color(0xFF7F1D1D),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        SectionHeader("5. Parecer Técnico Conclusivo")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isApproved) Color(0xFFECFDF5) else Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                                .border(1.5.dp, if (isApproved) StatusGreen else StatusRed, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isApproved) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (isApproved) StatusGreen else StatusRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "RESULTADO: ${test.result}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = if (isApproved) Color(0xFF065F46) else Color(0xFF991B1B)
                                    )
                                }
                                Text(
                                    text = test.resultRationale.ifEmpty {
                                        if (isApproved) "Equipamento atendeu integralmente aos ensaios de rigidez e fuga dielétrica conforme NR-10."
                                        else "Equipamento reprovado por não atingir os requisitos normativos de segurança."
                                    },
                                    fontSize = 11.sp,
                                    color = Color(0xFF334155),
                                    lineHeight = 16.sp
                                )
                                if (parsedTools.isNotEmpty()) {
                                    HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Composição do Conjunto de Ferramentas (${parsedTools.sumOf { it.quantity }} un.):",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NavyPrimary
                                            )
                                            Text(
                                                text = "Limite: ${test.leakageCurrentLimit_mA} mA",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SafetyOrange
                                            )
                                        }

                                        parsedTools.forEach { tool ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(if (tool.isApproved) Color.White else Color(0xFFFEF2F2), RoundedCornerShape(6.dp))
                                                    .border(0.5.dp, if (tool.isApproved) Color(0xFFCBD5E1) else StatusRed, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${tool.name} (${tool.quantity} un.)",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.Black
                                                    )
                                                    if (!tool.isApproved && tool.rejectionReason.isNotBlank()) {
                                                        Text(
                                                            text = "Motivo: ${tool.rejectionReason}",
                                                            fontSize = 9.sp,
                                                            color = StatusRed
                                                        )
                                                    }
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .background(if (tool.isApproved) StatusGreen.copy(alpha = 0.15f) else StatusRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (tool.isApproved) "APROVADO" else "REPROVADO",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = if (tool.isApproved) StatusGreen else StatusRed
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val cleanDocNotes = if (test.technicalNotes.contains("Equipamento higienizado em cuba com água desmineralizada", ignoreCase = true)) {
                                        test.technicalNotes.replace("Equipamento higienizado em cuba com água desmineralizada e aprovado para uso operacional.", "").trim()
                                    } else test.technicalNotes.trim()

                                    if (cleanDocNotes.isNotBlank() && !cleanDocNotes.contains("FERRAMENTAS_V2")) {
                                        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "Observações / Composição do Ensaio:",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NavyPrimary
                                            )
                                            Text(
                                                text = cleanDocNotes,
                                                fontSize = 10.sp,
                                                color = Color(0xFF334155),
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Próximo Reensaio Obrigatório:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    val retestDateStr = if (isApproved) com.example.util.DateUtils.formatDate(test.retestDueDate) else ""
                                    Text(
                                        text = retestDateStr,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = ElectricBlue
                                    )
                                }
                            }
                        }
                    }

                    // 6. Responsabilidade Técnica & Autenticidade Digital (Final da Página 1)
                    SectionHeader("6. Responsabilidade Técnica & Autenticidade Digital")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Analista Executor
                            Column {
                                if (test.technicianSignature.isNotBlank() && (File(test.technicianSignature).exists() || test.technicianSignature.startsWith("content://"))) {
                                    AsyncImage(
                                        model = if (test.technicianSignature.startsWith("/")) File(test.technicianSignature) else Uri.parse(test.technicianSignature),
                                        contentDescription = "Assinatura do Analista",
                                        modifier = Modifier.height(36.dp).width(120.dp),
                                        contentScale = ContentScale.Fit,
                                        alignment = Alignment.CenterStart
                                    )
                                }
                                Text(test.technicianName, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = NavyPrimary)
                                if (test.technicianCftOrCrea.isNotBlank()) Text(test.technicianCftOrCrea, fontSize = 10.sp, color = Color(0xFF64748B))
                                Text("Analista Executor", fontSize = 9.sp, color = Color(0xFF94A3B8))
                            }

                            // Responsável Técnico
                            Column {
                                if (test.techResponsibleSignature.isNotBlank() && (File(test.techResponsibleSignature).exists() || test.techResponsibleSignature.startsWith("content://"))) {
                                    AsyncImage(
                                        model = if (test.techResponsibleSignature.startsWith("/")) File(test.techResponsibleSignature) else Uri.parse(test.techResponsibleSignature),
                                        contentDescription = "Assinatura do RT",
                                        modifier = Modifier.height(36.dp).width(120.dp),
                                        contentScale = ContentScale.Fit,
                                        alignment = Alignment.CenterStart
                                    )
                                }
                                Text(test.techResponsibleName, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = NavyPrimary)
                                Text(test.techResponsibleCrea, fontSize = 10.sp, color = Color(0xFF64748B))
                                Text("Responsável Técnico", fontSize = 9.sp, color = Color(0xFF94A3B8))
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            QRCodeView(data = com.example.util.ValidationPortal.url(test.validationCode), qrSize = 70.dp)
                            Text("VALIDAÇÃO DIGITAL", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(top = 2.dp))
                            Text(test.validationCode, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricBlue)
                        }
                    }

                    // ==========================================
                    // SEGUNDA PÁGINA: REGISTRO FOTOGRÁFICO DO ENSAIO
                    // ==========================================
                    if (test.photoUris.isNotBlank()) {
                        val photosList = test.photoUris.split("|||").filter { it.isNotBlank() }.map { entry ->
                            val parts = entry.split("::")
                            val label = if (parts.size > 1) parts[0] else "Evidência"
                            val uri = if (parts.size > 1) parts[1] else parts[0]
                            label to uri
                        }

                        if (photosList.isNotEmpty()) {
                            // Page 2 Visual Separator Indicator
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                                    .padding(vertical = 6.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "PÁGINA 02 • ANEXO DE EVIDÊNCIAS FOTOGRÁFICAS",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        tint = SafetyOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "7. REGISTRO FOTOGRÁFICO DO ENSAIO",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = NavyPrimary
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    photosList.take(4).forEach { (label, uriStr) ->
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(85.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF0F172A)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AsyncImage(
                                                    model = if (uriStr.startsWith("/")) File(uriStr) else Uri.parse(uriStr),
                                                    contentDescription = label,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = label,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF334155),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Share / Print / PDF Export buttons
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // PDF Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (!isExportingPdf) {
                                        isExportingPdf = true
                                        scope.launch {
                                            try {
                                                val pdf = PdfExporter.generateLaudoPdf(context, test, company)
                                                Toast.makeText(context, "PDF gerado: ${pdf.name}", Toast.LENGTH_SHORT).show()
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
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isExportingPdf) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Visualizar PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (!isSharingPdf) {
                                        isSharingPdf = true
                                        scope.launch {
                                            try {
                                                val pdf = PdfExporter.generateLaudoPdf(context, test, company)
                                                val shareSubject = "Laudo ${test.reportNumber} - ${test.clientName}${if (test.collaboratorName.isNotBlank()) " - ${test.collaboratorName}" else ""}"
                                                PdfExporter.sharePdf(context, pdf, shareSubject)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Erro ao compartilhar PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                isSharingPdf = false
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isSharingPdf) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Compartilhar PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Word Export Action Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (!isExportingWord) {
                                        isExportingWord = true
                                        scope.launch {
                                            try {
                                                val wordFile = WordExporter.generateLaudoDoc(context, test, company)
                                                WordExporter.openDoc(context, wordFile)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Erro ao exportar Word: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                isExportingWord = false
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B579A)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("export_word_button")
                            ) {
                                if (isExportingWord) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Exportar Word (.doc)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (!isSharingWord) {
                                        isSharingWord = true
                                        scope.launch {
                                            try {
                                                val wordFile = WordExporter.generateLaudoDoc(context, test, company)
                                                val shareSubject = "Laudo ${test.reportNumber} - ${test.clientName}${if (test.collaboratorName.isNotBlank()) " - ${test.collaboratorName}" else ""}"
                                                WordExporter.shareDoc(context, wordFile, shareSubject)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Erro ao compartilhar Word: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                isSharingWord = false
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF185ABD)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("share_word_button")
                            ) {
                                if (isSharingWord) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Compartilhar Word", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Emissão do Relatório Técnico Completo (NR-10)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Relatório Técnico Completo (NR-10)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                                    }
                                    Text("8 Seções Formais", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                                }
                                Text(
                                    text = "Emite documento estruturado completo: Capa Oficial, Sumário, Introdução Regulamentar (NR-10), Objetivo, Metodologia dos Ensaios, Normas Aplicadas, Análise dos Resultados e Conclusão com ART/CREA.",
                                    fontSize = 9.5.sp,
                                    color = Color(0xFF475569),
                                    lineHeight = 13.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (!isExportingRelatorioCompletoPdf) {
                                                isExportingRelatorioCompletoPdf = true
                                                scope.launch {
                                                    try {
                                                        val pdf = PdfExporter.generateRelatorioCompletoPdf(
                                                            context = context,
                                                            tests = listOf(test),
                                                            client = null,
                                                            company = company
                                                        )
                                                        PdfExporter.openPdf(context, pdf)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Erro ao gerar Relatório Completo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                    } finally {
                                                        isExportingRelatorioCompletoPdf = false
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1.2f).testTag("relatorio_completo_pdf_btn")
                                    ) {
                                        if (isExportingRelatorioCompletoPdf) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("PDF Completo", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            if (!isSharingRelatorioCompletoPdf) {
                                                isSharingRelatorioCompletoPdf = true
                                                scope.launch {
                                                    try {
                                                        val pdf = PdfExporter.generateRelatorioCompletoPdf(
                                                            context = context,
                                                            tests = listOf(test),
                                                            client = null,
                                                            company = company
                                                        )
                                                        PdfExporter.sharePdf(context, pdf, "Relatório Técnico Completo NR-10 - ${test.reportNumber}")
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Erro ao compartilhar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                    } finally {
                                                        isSharingRelatorioCompletoPdf = false
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isSharingRelatorioCompletoPdf) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Enviar", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            if (!isExportingRelatorioCompletoDoc) {
                                                isExportingRelatorioCompletoDoc = true
                                                scope.launch {
                                                    try {
                                                        val doc = WordExporter.generateRelatorioCompletoDoc(
                                                            context = context,
                                                            tests = listOf(test),
                                                            client = null,
                                                            company = company
                                                        )
                                                        WordExporter.openDoc(context, doc)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Erro ao gerar Word: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                    } finally {
                                                        isExportingRelatorioCompletoDoc = false
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isExportingRelatorioCompletoDoc) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Word", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (onEditTest != null) {
                            OutlinedButton(
                                onClick = onEditTest,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("edit_laudo_bottom_button")
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Editar Dados do Laudo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                            }
                        }

                        if (onOpenNiimbot != null) {
                            Button(
                                onClick = onOpenNiimbot,
                                colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Etiqueta Niimbot B1 / Exportar JCPS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CertificadoDialog(
    test: TestRecordEntity,
    company: AppSettingEntity,
    onDismiss: () -> Unit,
    onOpenNiimbot: (() -> Unit)? = null,
    onEditTest: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExportingPdf by remember { mutableStateOf(false) }
    var isSharingPdf by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Header Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SafetyOrange)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Certificado de Conformidade Dielétrica",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onEditTest != null) {
                            IconButton(
                                onClick = onEditTest,
                                modifier = Modifier.testTag("edit_certificado_header_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar Certificado",
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                        }
                    }
                }

                // Certificate Parchment Frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(3.dp, NavyPrimary, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (company.companyLogoUri.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .fillMaxWidth()
                                    .padding(bottom = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = File(company.companyLogoUri).takeIf { it.exists() } ?: company.companyLogoUri,
                                    contentDescription = "Logomarca da Empresa",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxHeight()
                                )
                            }
                        }
                        Text(
                            text = company.name.uppercase(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = NavyPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = com.example.util.CompanyLabInfo.certificateTitle(company),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SafetyOrange,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "CERTIFICADO Nº ${test.certificateNumber.ifEmpty { test.reportNumber }}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = ElectricBlue
                        )

                        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                        Text(
                            text = "Certificamos que o equipamento de proteção abaixo identificado foi submetido a ensaio de rigidez e isolamento dielétrico em nosso laboratório, atendendo integralmente aos critérios normativos de segurança da NR-10:",
                            fontSize = 11.sp,
                            color = Color(0xFF334155),
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )

                        // Equipment Summary Box
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Cliente: ${test.clientName}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Equipamento: ${test.equipmentType.replace("_", " ").uppercase()} (Classe ${test.equipmentClass})", fontSize = 11.sp)
                                Text("Tag: ${test.equipmentTag} • Série: ${test.equipmentSerial} • CA: ${test.equipmentCa.ifEmpty { "N/A" }}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text("Norma: ${test.normCode} (${test.appliedVoltage_kV} kV ${test.voltageType})", fontSize = 11.sp, color = ElectricBlue)
                            }
                        }

                        // Validity Box
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFECFDF5), RoundedCornerShape(8.dp))
                                .border(1.dp, StatusGreen, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("STATUS DO ENSAIO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                                Text("APROVADO", fontSize = 14.sp, fontWeight = FontWeight.Black, color = StatusGreen)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("PRÓXIMO REENSAIO:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                                val certRetestStr = if (test.result == "APROVADO") com.example.util.DateUtils.formatDate(test.retestDueDate) else ""
                                Text(certRetestStr, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = Color(0xFF065F46))
                            }
                        }

                        // Signature and QR
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                if (test.techResponsibleSignature.isNotBlank() && (File(test.techResponsibleSignature).exists() || test.techResponsibleSignature.startsWith("content://"))) {
                                    AsyncImage(
                                        model = if (test.techResponsibleSignature.startsWith("/")) File(test.techResponsibleSignature) else Uri.parse(test.techResponsibleSignature),
                                        contentDescription = "Assinatura do RT",
                                        modifier = Modifier.height(40.dp).width(130.dp),
                                        contentScale = ContentScale.Fit,
                                        alignment = Alignment.CenterStart
                                    )
                                }
                                Text(test.techResponsibleName, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text(test.techResponsibleCrea, fontSize = 10.sp, color = Color(0xFF64748B))
                                Text("Responsável Técnico • CREA", fontSize = 9.sp, color = Color(0xFF94A3B8))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                QRCodeView(data = com.example.util.ValidationPortal.url(test.validationCode), qrSize = 60.dp)
                                Text(test.validationCode, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricBlue)
                            }
                        }

                        // Buttons
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (!isExportingPdf) {
                                            isExportingPdf = true
                                            scope.launch {
                                                try {
                                                    val pdf = PdfExporter.generateCertificadoPdf(context, test, company)
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
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isExportingPdf) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Visualizar PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        if (!isSharingPdf) {
                                            isSharingPdf = true
                                            scope.launch {
                                                try {
                                                    val pdf = PdfExporter.generateCertificadoPdf(context, test, company)
                                                    PdfExporter.sharePdf(context, pdf, "Certificado de Conformidade ${test.certificateNumber.ifEmpty { test.reportNumber }} - Tag: ${test.equipmentTag}")
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Erro ao compartilhar PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                } finally {
                                                    isSharingPdf = false
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isSharingPdf) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Compartilhar PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (onEditTest != null) {
                                OutlinedButton(
                                    onClick = onEditTest,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("edit_certificado_bottom_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = SafetyOrange, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Editar Dados do Certificado", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SafetyOrange)
                                }
                            }

                            if (onOpenNiimbot != null) {
                                Button(
                                    onClick = onOpenNiimbot,
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Etiqueta Niimbot B1 / Exportar JCPS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NiimbotLabelDialog(
    test: TestRecordEntity,
    company: AppSettingEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val printerManager = remember { NiimbotPrinterManager(context) }
    val printerState by printerManager.uiState.collectAsState()

    var showDevicePicker by remember { mutableStateOf(false) }
    var copies by remember { mutableIntStateOf(1) }
    var printDensity by remember { mutableFloatStateOf(1.0f) }
    // tamanho do papel (lembrado para as próximas etiquetas)
    val labelPrefs = remember { context.getSharedPreferences("jvm_label", android.content.Context.MODE_PRIVATE) }
    // deslocamento horizontal da impressão (mm, negativo = para a esquerda), calibrado por impressora
    var printOffsetMm by remember { mutableFloatStateOf(labelPrefs.getFloat("offset_mm", -2f)) }
    var paperSize by remember {
        mutableStateOf(runCatching { LabelPaperSize.valueOf(labelPrefs.getString("paper_size", null) ?: "") }.getOrDefault(LabelPaperSize.SIZE_50X30))
    }
    var useBitmapPreview by remember { mutableStateOf(true) }
    var isExportingJcps by remember { mutableStateOf(false) }
    var savedJcpsResult by remember { mutableStateOf<SavedJcpsResult?>(null) }
    var showJcpsSuccessDialog by remember { mutableStateOf(false) }

    // Clean up when dialog closes
    DisposableEffect(Unit) {
        printerManager.refreshPairedDevices()
        onDispose {
            printerManager.stopDiscovery()
            printerManager.disconnect()
        }
    }

    // Permission launcher for Android 12+ (BLUETOOTH_CONNECT & BLUETOOTH_SCAN)
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            printerManager.startDiscovery()
            showDevicePicker = true
        } else {
            Toast.makeText(context, "Permissão Bluetooth necessária para conectar à Niimbot", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestBluetoothAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        } else {
            // Android 11 ou anterior: a busca Bluetooth exige permissão de localização
            bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    val labelBitmap = remember(test, company, printDensity, paperSize) {
        printerManager.generateLabelBitmap(test, company, printDensity, paperSize)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(NavyPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                tint = SafetyOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Impressão Niimbot Bluetooth",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = NavyPrimary
                            )
                            Text(
                                text = "Etiqueta Térmica NR-10 (${paperSize.label} • 203 DPI)",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                // Bluetooth Connection Status Card
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (printerState.connectionState) {
                            PrinterConnectionState.CONNECTED -> Color(0xFFF0FDF4)
                            PrinterConnectionState.CONNECTING, PrinterConnectionState.SCANNING, PrinterConnectionState.PRINTING -> Color(0xFFEFF6FF)
                            PrinterConnectionState.ERROR -> Color(0xFFFEF2F2)
                            else -> Color(0xFFF8FAFC)
                        }
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (printerState.connectionState) {
                            PrinterConnectionState.CONNECTED -> StatusGreen
                            PrinterConnectionState.CONNECTING, PrinterConnectionState.SCANNING, PrinterConnectionState.PRINTING -> ElectricBlue
                            PrinterConnectionState.ERROR -> StatusRed
                            else -> Color(0xFFE2E8F0)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        when (printerState.connectionState) {
                                            PrinterConnectionState.CONNECTED -> StatusGreen.copy(alpha = 0.15f)
                                            PrinterConnectionState.CONNECTING, PrinterConnectionState.SCANNING, PrinterConnectionState.PRINTING -> ElectricBlue.copy(alpha = 0.15f)
                                            PrinterConnectionState.ERROR -> StatusRed.copy(alpha = 0.15f)
                                            else -> Color(0xFFE2E8F0)
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (printerState.connectionState == PrinterConnectionState.CONNECTING || printerState.connectionState == PrinterConnectionState.PRINTING) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = ElectricBlue
                                    )
                                } else {
                                    Icon(
                                        imageVector = when (printerState.connectionState) {
                                            PrinterConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                                            PrinterConnectionState.ERROR -> Icons.Default.BluetoothDisabled
                                            else -> Icons.Default.Bluetooth
                                        },
                                        contentDescription = null,
                                        tint = when (printerState.connectionState) {
                                            PrinterConnectionState.CONNECTED -> StatusGreen
                                            PrinterConnectionState.ERROR -> StatusRed
                                            else -> NavyPrimary
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = when (printerState.connectionState) {
                                        PrinterConnectionState.CONNECTED -> printerState.connectedDevice?.name ?: "Niimbot Conectada"
                                        PrinterConnectionState.CONNECTING -> "Conectando..."
                                        PrinterConnectionState.SCANNING -> "Buscando impressoras..."
                                        PrinterConnectionState.PRINTING -> "Imprimindo etiqueta..."
                                        PrinterConnectionState.SUCCESS -> "Impressão concluída!"
                                        PrinterConnectionState.ERROR -> "Erro de conexão"
                                        PrinterConnectionState.DISCONNECTED -> "Nenhuma impressora conectada"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (printerState.connectedDevice != null) {
                                        "MAC: ${printerState.connectedDevice?.address} • SPP Ativo"
                                    } else {
                                        printerState.statusMessage
                                    },
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1
                                )
                            }
                        }

                        // Connect / Change Button
                        Button(
                            onClick = { requestBluetoothAndScan() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (printerState.connectedDevice != null) Color(0xFFE2E8F0) else ElectricBlue,
                                contentColor = if (printerState.connectedDevice != null) NavyPrimary else Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = if (printerState.connectedDevice != null) Icons.Default.Refresh else Icons.Default.BluetoothSearching,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (printerState.connectedDevice != null) "Trocar" else "Conectar",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Printing Progress Indicator if in progress
                if (printerState.connectionState == PrinterConnectionState.PRINTING) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { printerState.printProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = SafetyOrange,
                        )
                        Text(
                            text = printerState.statusMessage,
                            fontSize = 10.sp,
                            color = SafetyOrange,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Tamanho do papel da etiqueta
                Text(
                    text = "TAMANHO DO PAPEL:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabelPaperSize.entries.forEach { opt ->
                        val selected = paperSize == opt
                        OutlinedButton(
                            onClick = {
                                paperSize = opt
                                labelPrefs.edit().putString("paper_size", opt.name).apply()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) NavyPrimary else Color.Transparent),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text(opt.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) Color.White else Color.Black)
                        }
                    }
                }

                // Thermal Label Preview (Exact 203 DPI Raster Representation)
                Text(
                    text = "PRÉ-VISUALIZAÇÃO DA ETIQUETA (${paperSize.label}):",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, Color(0xFF0F172A), RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = labelBitmap.asImageBitmap(),
                        contentDescription = "Etiqueta Térmica Niimbot ${paperSize.label}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (paperSize == LabelPaperSize.SIZE_50X80) 280.dp else 180.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                // Protocol Selector (Niimbot / TSPL / ESC-POS)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Protocolo da Impressora:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            Text(printerState.selectedProtocol.displayName.take(18), fontSize = 9.sp, color = ElectricBlue, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PrinterProtocol.entries.forEach { proto ->
                                val isSelected = printerState.selectedProtocol == proto
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) NavyPrimary else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) NavyPrimary else Color(0xFFCBD5E1)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { printerManager.setProtocol(proto) }
                                ) {
                                    Text(
                                        text = when (proto) {
                                            PrinterProtocol.NIIMBOT -> "Niimbot"
                                            PrinterProtocol.TSPL -> "TSPL/TSC"
                                            PrinterProtocol.ESCPOS -> "ESC/POS"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color(0xFF334155),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Configuration Row: Copies & Density
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copies Selector
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cópias:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(1, 2, 3).forEach { count ->
                                    val isSelected = copies == count
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSelected) NavyPrimary else Color.White,
                                        modifier = Modifier
                                            .clickable { copies = count }
                                    ) {
                                        Text(
                                            text = "$count",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else Color(0xFF334155),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Contrast/Density Selector
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Densidade:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val isHigh = printDensity > 1.0f
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (!isHigh) NavyPrimary else Color.White,
                                    modifier = Modifier.clickable { printDensity = 1.0f }
                                ) {
                                    Text(
                                        text = "Normal",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isHigh) Color.White else Color(0xFF334155),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isHigh) NavyPrimary else Color.White,
                                    modifier = Modifier.clickable { printDensity = 1.2f }
                                ) {
                                    Text(
                                        text = "Forte",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isHigh) Color.White else Color(0xFF334155),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Ajuste horizontal: corrige a faixa em branco / corte lateral conforme o encaixe do rolo na B1
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ajuste horizontal:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                fun setOffset(v: Float) {
                                    printOffsetMm = (Math.round(v * 2) / 2f).coerceIn(-4f, 4f)
                                    labelPrefs.edit().putFloat("offset_mm", printOffsetMm).apply()
                                }
                                OutlinedButton(onClick = { setOffset(printOffsetMm - 0.5f) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(32.dp)) {
                                    Text("◀", fontSize = 11.sp, color = Color.Black)
                                }
                                Text(
                                    when {
                                        printOffsetMm < 0 -> "${String.format(java.util.Locale.US, "%.1f", -printOffsetMm).replace('.', ',')} mm à esquerda"
                                        printOffsetMm > 0 -> "${String.format(java.util.Locale.US, "%.1f", printOffsetMm).replace('.', ',')} mm à direita"
                                        else -> "sem ajuste"
                                    },
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary
                                )
                                OutlinedButton(onClick = { setOffset(printOffsetMm + 0.5f) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(32.dp)) {
                                    Text("▶", fontSize = 11.sp, color = Color.Black)
                                }
                            }
                        }
                        Text(
                            "Sobrou faixa branca à esquerda: desloque para a esquerda. Cortou do lado esquerdo: desloque para a direita.",
                            fontSize = 10.sp, color = Color(0xFF64748B)
                        )
                    }
                }

                // JCPS Export Card (Niimbot B1 - 50x30mm)
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SafetyOrange.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "FORMATO JCPS",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SafetyOrange,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Niimbot B1 (${paperSize.label})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                            }
                            Text(
                                text = "Pasta: etiqueta/",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ElectricBlue
                            )
                        }

                        Text(
                            text = "Exporta o arquivo de modelo .jcps compatível com o aplicativo Niimbot / JingChen no tamanho ${paperSize.label}, salvo na pasta local 'etiqueta' como '${JcpsExporter.sanitizeReportNumber(test.reportNumber, test.equipmentTag)}.jcps'.",
                            fontSize = 10.sp,
                            color = Color(0xFF475569),
                            lineHeight = 14.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    isExportingJcps = true
                                    coroutineScope.launch {
                                        try {
                                            val res = JcpsExporter.exportJcpsToFile(
                                                context = context,
                                                test = test,
                                                company = company,
                                                density = printDensity,
                                                size = paperSize
                                            )
                                            savedJcpsResult = res
                                            showJcpsSuccessDialog = true
                                            Toast.makeText(context, "Arquivo JCPS salvo na pasta 'etiqueta'!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Erro ao exportar JCPS: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isExportingJcps = false
                                        }
                                    }
                                },
                                enabled = !isExportingJcps,
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("export_jcps_button")
                            ) {
                                if (isExportingJcps) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Salvando...", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Salvar JCPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            val res = JcpsExporter.exportJcpsToFile(
                                                context = context,
                                                test = test,
                                                company = company,
                                                density = printDensity,
                                                size = paperSize
                                            )
                                            savedJcpsResult = res
                                            JcpsExporter.shareJcpsFile(context, res.file, test.reportNumber)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("share_jcps_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = NavyPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Compartilhar JCPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                            }
                        }
                    }
                }

                // Primary Action Button: Direct Bluetooth Print
                Button(
                    onClick = {
                        if (printerState.connectedDevice == null) {
                            // If not connected yet, trigger scan & connection
                            requestBluetoothAndScan()
                        } else {
                            coroutineScope.launch {
                                val res = printerManager.printLabel(
                                    test = test,
                                    company = company,
                                    copies = copies,
                                    density = printDensity,
                                    size = paperSize,
                                    offsetMm = printOffsetMm
                                )
                                if (res.isSuccess) {
                                    Toast.makeText(context, "Etiqueta impressa na ${printerState.connectedDevice?.name}!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Falha na impressão: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (printerState.connectedDevice != null) SafetyOrange else NavyPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("print_niimbot_bluetooth_button")
                ) {
                    Icon(
                        imageVector = if (printerState.connectedDevice != null) Icons.Default.Print else Icons.Default.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (printerState.connectedDevice != null) {
                            "Imprimir $copies ${if (copies == 1) "Etiqueta" else "Etiquetas"} (${printerState.selectedProtocol.name})"
                        } else {
                            "Conectar Impressora e Imprimir"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Fallback / Share Action
                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Etiqueta Térmica NR-10\nTAG: ${test.equipmentTag}\nEquipamento: ${test.equipmentType}\nEnsaio: ${com.example.util.DateUtils.formatDate(test.testDate)}\nValidade: ${com.example.util.DateUtils.formatDate(test.retestDueDate)}\nStatus: ${test.result}\nAutenticidade: ${com.example.util.ValidationPortal.url(test.validationCode)}"
                            )
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Compartilhar Dados da Etiqueta"))
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Compartilhar Texto da Etiqueta", fontSize = 11.sp, color = NavyPrimary)
                }
            }
        }
    }

    // Modal de Confirmação e Ações do Arquivo JCPS Exportado
    if (showJcpsSuccessDialog && savedJcpsResult != null) {
        val result = savedJcpsResult!!
        Dialog(onDismissRequest = { showJcpsSuccessDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(StatusGreen.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = "Etiqueta JCPS Exportada!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = NavyPrimary,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Nome do Arquivo (Nº do Laudo):", fontSize = 10.sp, color = Color(0xFF64748B))
                            Text(
                                result.fileName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            Text("Pasta Local de Armazenamento:", fontSize = 10.sp, color = Color(0xFF64748B))
                            Text(
                                result.folderPath,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF334155)
                            )
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            Text("Formato & Padrão:", fontSize = 10.sp, color = Color(0xFF64748B))
                            Text(
                                "JCPS • Niimbot B1 (${paperSize.label} • 203 DPI)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ElectricBlue
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                JcpsExporter.shareJcpsFile(context, result.file, test.reportNumber)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Compartilhar", fontSize = 11.sp, color = NavyPrimary)
                        }

                        Button(
                            onClick = {
                                JcpsExporter.openJcpsFile(context, result.file)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Abrir Arquivo", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    Button(
                        onClick = { showJcpsSuccessDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Concluir", color = Color(0xFF334155), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Bluetooth Device Picker Sheet / Dialog
    if (showDevicePicker) {
        Dialog(onDismissRequest = { showDevicePicker = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Impressoras Niimbot Bluetooth",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = NavyPrimary
                            )
                            Text(
                                text = "Selecione seu modelo Niimbot B1, B21, D110 ou térmica",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        IconButton(onClick = { showDevicePicker = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    if (printerState.discoveredDevices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = ElectricBlue)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Procurando impressoras Bluetooth...", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(printerState.discoveredDevices) { dev ->
                                val isConnected = printerState.connectedDevice?.address == dev.address

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isConnected) Color(0xFFECFDF5) else if (dev.isNiimbot) Color(0xFFF0F9FF) else Color(0xFFF8FAFC),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isConnected) StatusGreen else if (dev.isNiimbot) ElectricBlue.copy(alpha = 0.5f) else Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            coroutineScope.launch {
                                                val success = printerManager.connectToDevice(dev)
                                                if (success) {
                                                    Toast.makeText(context, "Conectado a ${dev.name}!", Toast.LENGTH_SHORT).show()
                                                    showDevicePicker = false
                                                } else {
                                                    Toast.makeText(context, printerManager.uiState.value.statusMessage, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(
                                                        if (dev.isNiimbot) ElectricBlue.copy(alpha = 0.15f) else Color(0xFFE2E8F0),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Print,
                                                    contentDescription = null,
                                                    tint = if (isConnected) StatusGreen else if (dev.isNiimbot) ElectricBlue else NavyPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = dev.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = Color.Black,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (dev.isNiimbot) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = ElectricBlue
                                                        ) {
                                                            Text(
                                                                text = "NIIMBOT",
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = Color.White,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = "${dev.address} ${if (dev.isBonded) "• Pareado" else ""}",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }

                                        if (isConnected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Conectado",
                                                tint = StatusGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "Conectar",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ElectricBlue
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Rescan Button
                    Button(
                        onClick = { printerManager.startDiscovery() },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Atualizar / Buscar Novas Impressoras", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavyPrimary, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isMono: Boolean = false,
    isHighlight: Boolean = false
) {
    Column(modifier = modifier) {
        Text(label, fontSize = 9.sp, color = Color(0xFF64748B))
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighlight) ElectricBlue else Color(0xFF0F172A),
            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
private fun SummaryParamRow(
    param: String,
    req: String,
    measured: String,
    isConforming: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(param, fontSize = 8.sp, color = Color(0xFF334155), modifier = Modifier.weight(1.3f))
        Text(req, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.weight(1.8f))
        Text(measured, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = ElectricBlue, modifier = Modifier.weight(1.1f))
        Box(
            modifier = Modifier
                .weight(0.9f)
                .background(
                    if (isConforming) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                    RoundedCornerShape(4.dp)
                )
                .border(
                    0.8.dp,
                    if (isConforming) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                    RoundedCornerShape(4.dp)
                )
                .padding(vertical = 2.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isConforming) "CONFORME" else "NÃO CONFORME",
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isConforming) Color(0xFF166534) else Color(0xFF991B1B),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun VisualInspectionItem(
    title: String,
    isConforming: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 8.sp,
            color = Color(0xFF334155),
            lineHeight = 11.sp,
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
        )
        Box(
            modifier = Modifier
                .background(
                    if (isConforming) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                    RoundedCornerShape(4.dp)
                )
                .border(
                    0.8.dp,
                    if (isConforming) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                    RoundedCornerShape(4.dp)
                )
                .padding(vertical = 2.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isConforming) "CONFORME" else "NÃO CONFORME",
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isConforming) Color(0xFF166534) else Color(0xFF991B1B)
            )
        }
    }
}

/**
 * Seção 3 do laudo do detector de tensão por indução (tela) — igual ao laudo da web:
 * "Classe: 1 kV a 50 kV", tabela DISTÂNCIA (cm) | TENSÃO (kV), gráfico e observação.
 */
@Composable
private fun InductionLaudoSection(test: TestRecordEntity) {
    val points = com.example.domain.InductionDetector.normalize(com.example.domain.InductionDetector.parse(test.inductionPointsJson))
    val border = Color(0xFF0F172A)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(0.42f)) {
                Box(
                    modifier = Modifier.fillMaxWidth().border(0.7.dp, border).padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Classe: ${com.example.domain.InductionDetector.DETECTOR_CLASS}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = border)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFE2E8F0)).border(0.7.dp, border)) {
                    Text("DISTÂNCIA (cm)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = border, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f).padding(vertical = 3.dp))
                    Text("TENSÃO (kV)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = border, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f).padding(vertical = 3.dp))
                }
                points.forEach { p ->
                    val missed = !p.detected
                    Row(modifier = Modifier.fillMaxWidth().background(if (missed) Color(0xFFFEE2E2) else Color.White).border(0.5.dp, border)) {
                        Text(
                            if (missed) "Não detectou" else p.distance_cm?.let { com.example.domain.InductionDetector.fmtBr1(it) } ?: "—",
                            fontSize = 9.sp,
                            color = if (missed) Color(0xFF991B1B) else border,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f).padding(vertical = 2.dp)
                        )
                        Text(
                            com.example.domain.InductionDetector.fmtBr1(p.voltage_kV),
                            fontSize = 9.sp,
                            color = border,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f).padding(vertical = 2.dp)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(0.58f)
                    .aspectRatio(com.example.util.InductionChartRenderer.BASE_W / com.example.util.InductionChartRenderer.BASE_H)
                    .drawBehind {
                        drawIntoCanvas { c ->
                            com.example.util.InductionChartRenderer.draw(c.nativeCanvas, 0f, 0f, size.width, size.height, points)
                        }
                    }
            )
        }
        val notDetected = points.filter { !it.detected }.map { "${com.example.domain.InductionDetector.fmtBr1(it.voltage_kV)} kV" }
        val obsOk = test.result == "APROVADO"
        val obsText = when {
            obsOk -> "OBS: RESULTADOS SATISFATÓRIOS CONFORME ESPECIFICAÇÕES DO FABRICANTE."
            notDetected.isNotEmpty() -> "OBS: O DETECTOR NÃO SINALIZOU EM ${notDetected.joinToString("; ")} — NÃO ATENDE ÀS ESPECIFICAÇÕES DO FABRICANTE."
            else -> "OBS: EQUIPAMENTO REPROVADO — VER PARECER TÉCNICO."
        }
        Box(modifier = Modifier.fillMaxWidth().border(0.7.dp, border).padding(5.dp), contentAlignment = Alignment.Center) {
            Text(obsText, fontSize = 9.sp, color = if (obsOk) Color(0xFF1D4ED8) else Color(0xFFB91C1C), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
