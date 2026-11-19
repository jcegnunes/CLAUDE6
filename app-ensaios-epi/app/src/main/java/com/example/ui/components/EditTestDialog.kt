package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DielectricClass
import com.example.data.model.EquipmentType
import com.example.data.model.ManualToolItem
import com.example.data.model.TestRecordEntity
import com.example.data.model.TestResult
import com.example.domain.NormsEngine
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTestDialog(
    test: TestRecordEntity,
    onDismiss: () -> Unit,
    onSave: (TestRecordEntity) -> Unit
) {
    val context = LocalContext.current

    // Form states initialized with original test record data
    var reportNumber by remember { mutableStateOf(test.reportNumber) }
    var certificateNumber by remember { mutableStateOf(test.certificateNumber) }
    var testNumber by remember { mutableStateOf(test.testNumber) }
    var serviceOrderNumber by remember { mutableStateOf(test.serviceOrderNumber) }
    var artNumber by remember { mutableStateOf(test.artNumber) }
    var validationCode by remember { mutableStateOf(test.validationCode) }

    // Photos state with OS storage folder support
    val photoList = remember {
        mutableStateListOf<TestPhotoItem>().apply {
            if (test.photoUris.isNotBlank()) {
                test.photoUris.split("|||").filter { it.isNotBlank() }.forEach { entry ->
                    val parts = entry.split("::", limit = 2)
                    val label = parts.getOrNull(0) ?: "Equipamento em Ensaio"
                    val uriStr = parts.getOrNull(1) ?: parts[0]
                    add(TestPhotoItem(uriString = uriStr, label = label))
                }
            }
        }
    }

    // Client & Collaborator
    var clientName by remember { mutableStateOf(test.clientName) }
    var collaboratorName by remember { mutableStateOf(test.collaboratorName) }
    var collaboratorRegistration by remember { mutableStateOf(test.collaboratorRegistration) }
    var collaboratorSector by remember { mutableStateOf(test.collaboratorSector) }

    // Equipment
    var equipmentTag by remember { mutableStateOf(test.equipmentTag) }
    var selectedEquipmentType by remember {
        mutableStateOf(
            EquipmentType.fromString(test.equipmentType)
        )
    }
    var equipmentTypeExpanded by remember { mutableStateOf(false) }

    var selectedEquipmentClass by remember {
        mutableStateOf(
            DielectricClass.fromString(test.equipmentClass)
        )
    }
    var equipmentClassExpanded by remember { mutableStateOf(false) }

    var equipmentSerial by remember { mutableStateOf(test.equipmentSerial) }
    var equipmentCa by remember { mutableStateOf(test.equipmentCa) }
    var gloveLengthMm by remember { mutableIntStateOf(test.gloveLength_mm) }
    // bastões/varas e escada: quantidade de pontos ensaiados (limite = pontos × limite da tabela)
    var stickPointsInput by remember { mutableStateOf(test.stickTestPoints.coerceAtLeast(1).toString()) }
    val stickTestPoints: Int = stickPointsInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
    // escada: critério do ensaio salvo (mesma norma e tensão); sem correspondência, o padrão EN 50528:2024
    val ladderCriterionId = remember { NormsEngine.ladderCriterionIdFor(test.normCode, test.appliedVoltage_kV) }
    val ladderPerPointLimit = NormsEngine.getStandardCriteria(EquipmentType.ESCADA_ISOLADA, test.equipmentClass, criterionId = ladderCriterionId).leakageLimit_mA

    // Test Parameters
    var normCode by remember { mutableStateOf(test.normCode) }
    var procedureCode by remember { mutableStateOf(test.procedureCode) }
    var appliedVoltageKV by remember { mutableStateOf(numberText(test.appliedVoltage_kV)) }
    var voltageType by remember { mutableStateOf(test.voltageType) }
    var durationSeconds by remember { mutableStateOf(if (test.applicationDurationSeconds > 0) test.applicationDurationSeconds.toString() else "") }
    var measuredLeakageMA by remember { mutableStateOf(numberText(test.measuredLeakageCurrent_mA)) }
    var leakageLimitMA by remember { mutableStateOf(numberText(test.leakageCurrentLimit_mA)) }
    var withstandWithoutPuncture by remember { mutableStateOf(test.withstandWithoutPuncture) }
    var visualInspectionSummary by remember { mutableStateOf(test.visualInspectionSummary) }
    var temperatureC by remember { mutableStateOf(numberText(test.temperatureC)) }
    var relativeHumidityPercent by remember { mutableStateOf(numberText(test.relativeHumidityPercent)) }
    var location by remember { mutableStateOf(test.location) }

    // Manual Tools State
    val initialTools = remember {
        val parsed = ManualToolItem.getToolsForTest(test)
        if (parsed.isNotEmpty()) parsed
        else listOf(
            ManualToolItem(name = "Alicate Universal 1000V", quantity = 1, isApproved = test.result == "APROVADO"),
            ManualToolItem(name = "Chave de Fenda Isolada", quantity = 1, isApproved = true),
            ManualToolItem(name = "Chave Phillips Isolada", quantity = 1, isApproved = true)
        )
    }
    val manualToolsList = remember { mutableStateListOf<ManualToolItem>().apply { addAll(initialTools) } }
    var customToolNameInput by remember { mutableStateOf("") }
    var customToolQuantityInput by remember { mutableIntStateOf(1) }

    // Results & Notes
    var testResult by remember { mutableStateOf(test.result) }
    var resultRationale by remember { mutableStateOf(test.resultRationale) }
    val initialNotes = run {
        var n = test.technicalNotes
        if (n.contains("Equipamento higienizado em cuba com água desmineralizada", ignoreCase = true)) {
            n = n.replace("Equipamento higienizado em cuba com água desmineralizada e aprovado para uso operacional.", "").trim()
        }
        if (n.contains("FERRAMENTAS_V2:")) {
            n = n.substringBefore("FERRAMENTAS_V2:").substringBefore("Ferramentas Ensaidas").substringBefore("Ferramentas Ensaiadas").trim()
        }
        n
    }
    var technicalNotes by remember { mutableStateOf(initialNotes) }

    // Dates & Personnel
    var testDate by remember { mutableStateOf(com.example.util.DateUtils.formatDate(test.testDate)) }
    var testTime by remember { mutableStateOf(test.testTime) }
    var retestDueDate by remember { mutableStateOf(if (test.result == "REPROVADO") "" else com.example.util.DateUtils.formatDate(test.retestDueDate)) }
    var technicianName by remember { mutableStateOf(test.technicianName) }
    var technicianCftOrCrea by remember { mutableStateOf(test.technicianCftOrCrea) }
    var techResponsibleName by remember { mutableStateOf(test.techResponsibleName) }
    var techResponsibleCrea by remember { mutableStateOf(test.techResponsibleCrea) }

    val isFerramenta = selectedEquipmentType == EquipmentType.FERRAMENTA_ISOLADA

    fun recalculateViaNorms() {
        try {
            val vApplied = appliedVoltageKV.replace(",", ".").toDoubleOrNull() ?: 10.0
            val dur = durationSeconds.toIntOrNull() ?: 60
            val leak = measuredLeakageMA.replace(",", ".").toDoubleOrNull() ?: 0.0
            val totalTools = if (isFerramenta) manualToolsList.sumOf { it.quantity } else 1
            val rejectedTools = if (isFerramenta) manualToolsList.filter { !it.isApproved }.sumOf { it.quantity } else 0

            val rejectedSummary = if (rejectedTools > 0) {
                manualToolsList.filter { !it.isApproved }.joinToString("; ") { tool ->
                    "${tool.name} (${tool.quantity} un.${if (tool.rejectionReason.isNotBlank()) " - Motivo: ${tool.rejectionReason}" else ""})"
                }
            } else ""

            val evalInput = NormsEngine.EvaluationInput(
                equipmentType = selectedEquipmentType,
                dielectricClass = selectedEquipmentClass.label,
                appliedVoltage_kV = vApplied,
                durationSeconds = dur,
                measuredLeakage_mA = leak,
                withstandWithoutPuncture = withstandWithoutPuncture,
                visualPassed = !visualInspectionSummary.contains("não conforme", ignoreCase = true) &&
                        !visualInspectionSummary.contains("nao conforme", ignoreCase = true) &&
                        !visualInspectionSummary.contains("reprovado", ignoreCase = true),
                gloveLength_mm = gloveLengthMm,
                toolsCount = totalTools,
                individualToolsRejectedCount = rejectedTools,
                rejectedToolsSummary = rejectedSummary,
                stickDiameter_mm = NormsEngine.closestPoleDiameter(gloveLengthMm),
                stickTestPoints = stickTestPoints,
                criterionId = if (selectedEquipmentType == EquipmentType.ESCADA_ISOLADA) ladderCriterionId else null
            )

            val evalResult = NormsEngine.evaluate(evalInput)
            testResult = evalResult.result.name
            leakageLimitMA = String.format(Locale.US, "%.2f", evalResult.leakageLimit_mA)
            resultRationale = evalResult.rationale
            normCode = evalResult.normReference

            // Recalculate retest date in dd/MM/yyyy
            retestDueDate = if (evalResult.result == TestResult.APROVADO) {
                com.example.util.DateUtils.calculateRetestDueDate(testDate, evalResult.recommendedRetestMonths)
            } else ""

            Toast.makeText(context, "Critérios recalculados conforme norma ${evalResult.normReference}!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao calcular: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Header Bar
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
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = SafetyOrange,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Edição de Laudo e Certificado",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "${test.reportNumber} • TAG: ${test.equipmentTag}",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Quick Recalculate Action Banner
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Assistente Normativo NR-10", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                                    Text("Ajuste os valores e recalcule limites e parecer automaticamente.", fontSize = 10.sp, color = Color(0xFF475569))
                                }
                            }

                            Button(
                                onClick = { recalculateViaNorms() },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Recalcular", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 1. Identificação & Numeração
                    EditSectionCard(title = "1. Numeração e Identificação do Documento", icon = Icons.Default.Description) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = reportNumber,
                                onValueChange = { reportNumber = it },
                                label = { Text("Nº do Laudo") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = certificateNumber,
                                onValueChange = { certificateNumber = it },
                                label = { Text("Nº do Certificado") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = serviceOrderNumber,
                                onValueChange = { serviceOrderNumber = it },
                                label = { Text("Nº da O.S.") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = artNumber,
                                onValueChange = { artNumber = it },
                                label = { Text("ART / RRT") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = validationCode,
                            onValueChange = { validationCode = it },
                            label = { Text("Código de Validação Digital") },
                            colors = editTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 2. Cliente e Colaborador
                    EditSectionCard(title = "2. Cliente e Colaborador Usuário", icon = Icons.Default.Person) {
                        OutlinedTextField(
                            value = clientName,
                            onValueChange = { clientName = it },
                            label = { Text("Razão Social do Cliente") },
                            colors = editTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = collaboratorName,
                                onValueChange = { collaboratorName = it },
                                label = { Text("Colaborador Usuário") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1.2f)
                            )
                            OutlinedTextField(
                                value = collaboratorRegistration,
                                onValueChange = { collaboratorRegistration = it },
                                label = { Text("Matrícula") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(0.8f)
                            )
                        }

                        OutlinedTextField(
                            value = collaboratorSector,
                            onValueChange = { collaboratorSector = it },
                            label = { Text("Setor / Unidade") },
                            colors = editTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 3. Equipamento
                    EditSectionCard(title = "3. Dados do Equipamento", icon = Icons.Default.Build) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = equipmentTag,
                                onValueChange = { equipmentTag = it },
                                label = { Text("TAG do Equipamento") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )

                            // Dropdown Tipo de Equipamento
                            ExposedDropdownMenuBox(
                                expanded = equipmentTypeExpanded,
                                onExpandedChange = { equipmentTypeExpanded = !equipmentTypeExpanded },
                                modifier = Modifier.weight(1.2f)
                            ) {
                                OutlinedTextField(
                                    value = selectedEquipmentType.label,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Tipo Equipamento") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = equipmentTypeExpanded) },
                                    colors = editTextFieldColors(),
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = equipmentTypeExpanded,
                                    onDismissRequest = { equipmentTypeExpanded = false }
                                ) {
                                    EquipmentType.entries.forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(type.label, fontSize = 12.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Medium) },
                                            onClick = {
                                                selectedEquipmentType = type
                                                equipmentTypeExpanded = false
                                                if (type == EquipmentType.LUVA_ISOLANTE) {
                                                    val unitLimit = NormsEngine.getGloveUnitaryLeakageLimit(selectedEquipmentClass.label, gloveLengthMm)
                                                    val pairLimit = unitLimit * 2.0
                                                    leakageLimitMA = if (pairLimit % 1.0 == 0.0) pairLimit.toInt().toString() else pairLimit.toString()
                                                    appliedVoltageKV = selectedEquipmentClass.testVoltageAC.toString()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Dropdown Classe Dielétrica
                            ExposedDropdownMenuBox(
                                expanded = equipmentClassExpanded,
                                onExpandedChange = { equipmentClassExpanded = !equipmentClassExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = "Classe ${selectedEquipmentClass.label}",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Classe") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = equipmentClassExpanded) },
                                    colors = editTextFieldColors(),
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = equipmentClassExpanded,
                                    onDismissRequest = { equipmentClassExpanded = false }
                                ) {
                                    DielectricClass.entries.forEach { dClass ->
                                        DropdownMenuItem(
                                            text = { Text("Classe ${dClass.label} (Até ${dClass.maxUseVoltageAC} kV)", fontSize = 12.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Medium) },
                                            onClick = {
                                                selectedEquipmentClass = dClass
                                                equipmentClassExpanded = false
                                                if (selectedEquipmentType == EquipmentType.LUVA_ISOLANTE) {
                                                    val unitLimit = NormsEngine.getGloveUnitaryLeakageLimit(dClass.label, gloveLengthMm)
                                                    val pairLimit = unitLimit * 2.0
                                                    leakageLimitMA = if (pairLimit % 1.0 == 0.0) pairLimit.toInt().toString() else pairLimit.toString()
                                                    appliedVoltageKV = dClass.testVoltageAC.toString()
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = equipmentSerial,
                                onValueChange = { equipmentSerial = it },
                                label = { Text("Nº Série / Lote") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = equipmentCa,
                                onValueChange = { equipmentCa = it },
                                label = { Text("Nº C.A.") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(0.8f)
                            )
                        }

                        if (selectedEquipmentType == EquipmentType.LUVA_ISOLANTE) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Comprimento da Luva (Ensaio de 2 luvas simultâneas - Limite 2x):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(280, 360, 410, 460).forEach { len ->
                                        val cleanClass = selectedEquipmentClass.label
                                        val unitLimit = NormsEngine.getGloveUnitaryLeakageLimit(cleanClass, len)
                                        val pairLimit = unitLimit * 2.0
                                        OutlinedButton(
                                            onClick = {
                                                gloveLengthMm = len
                                                leakageLimitMA = if (pairLimit % 1.0 == 0.0) pairLimit.toInt().toString() else pairLimit.toString()
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (gloveLengthMm == len) NavyPrimary else Color.Transparent
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text(
                                                "$len mm (${pairLimit.toInt()} mA)",
                                                fontSize = 10.sp,
                                                color = if (gloveLengthMm == len) Color.White else Color.Black,
                                                fontWeight = if (gloveLengthMm == len) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (com.example.domain.NormsEngine.isStickType(selectedEquipmentType)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Diâmetro do Bastão / Limite ABNT NBR 11854 (50 kV):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(32 to "5 µA", 38 to "6 µA", 51 to "8 µA", 64 to "10 µA").forEach { (diam, limStr) ->
                                        OutlinedButton(
                                            onClick = {
                                                gloveLengthMm = diam
                                                leakageLimitMA = (NormsEngine.nbr11854LeakageLimit_uA(diam) * stickTestPoints).toString()
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (gloveLengthMm == diam) NavyPrimary else Color.Transparent
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text(
                                                "Ø $diam mm ($limStr)",
                                                fontSize = 9.sp,
                                                color = if (gloveLengthMm == diam) Color.White else Color.Black,
                                                fontWeight = if (gloveLengthMm == diam) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = stickPointsInput,
                                    onValueChange = { input ->
                                        stickPointsInput = input.filter { it.isDigit() }.take(3)
                                        val pts = stickPointsInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                        leakageLimitMA = (NormsEngine.nbr11854LeakageLimit_uA(NormsEngine.closestPoleDiameter(gloveLengthMm)) * pts).toString()
                                    },
                                    label = { Text("Quantidade de pontos ensaiados", color = Color.Black) },
                                    supportingText = {
                                        val perPoint = NormsEngine.nbr11854LeakageLimit_uA(NormsEngine.closestPoleDiameter(gloveLengthMm)).toInt()
                                        Text("Limite de fuga: $stickTestPoints × $perPoint µA = ${stickTestPoints * perPoint} µA", fontSize = 10.sp, color = Color(0xFF15803D))
                                    },
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                    colors = editTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // 4. Se for Ferramenta Isolada: Composição detalhada
                    if (isFerramenta) {
                        EditSectionCard(
                            title = "4. Composição do Conjunto de Ferramentas Manuais",
                            icon = Icons.Default.Build
                        ) {
                            Text(
                                "ABNT NBR 9699 / IEC 60900 (Limite Normativo: 1.0 mA por ferramenta)",
                                fontSize = 10.sp,
                                color = Color(0xFF475569)
                            )

                            // KPI Resumo do Conjunto
                            val totalUnits = manualToolsList.sumOf { it.quantity }
                            val rejectedUnits = manualToolsList.filter { !it.isApproved }.sumOf { it.quantity }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Total de Itens", fontSize = 9.sp, color = Color(0xFF64748B))
                                    Text("$totalUnits un.", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                                }
                                Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFFCBD5E1)))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Limite Fuga Calculado", fontSize = 9.sp, color = Color(0xFF64748B))
                                    Text("${String.format(Locale.US, "%.1f", totalUnits * 1.0)} mA", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SafetyOrange)
                                }
                                Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFFCBD5E1)))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Itens Reprovados", fontSize = 9.sp, color = Color(0xFF64748B))
                                    Text(
                                        if (rejectedUnits == 0) "Nenhum (100%)" else "$rejectedUnits un.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rejectedUnits == 0) StatusGreen else StatusRed
                                    )
                                }
                            }

                            // Ações rápidas de ferramentas
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        manualToolsList.forEachIndexed { idx, tool ->
                                            manualToolsList[idx] = tool.copy(isApproved = true, rejectionReason = "")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Aprovar Todas", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val calculatedLimit = totalUnits * 1.0
                                        leakageLimitMA = String.format(Locale.US, "%.1f", calculatedLimit)
                                        Toast.makeText(context, "Limite atualizado para $calculatedLimit mA", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                ) {
                                    Text("Atualizar Limite", fontSize = 10.sp, color = NavyPrimary)
                                }
                            }

                            // Lista de ferramentas
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                manualToolsList.forEachIndexed { index, toolItem ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (toolItem.isApproved) Color.White else Color(0xFFFEF2F2)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (toolItem.isApproved) Color(0xFFCBD5E1) else StatusRed
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = toolItem.name,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                IconButton(
                                                    onClick = {
                                                        if (manualToolsList.size > 1) {
                                                            manualToolsList.removeAt(index)
                                                        } else {
                                                            Toast.makeText(context, "Mantenha ao menos 1 ferramenta no conjunto.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Remover", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                                }
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Stepper de Quantidade
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Qtd:", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    IconButton(
                                                        onClick = {
                                                            if (toolItem.quantity > 1) {
                                                                manualToolsList[index] = toolItem.copy(quantity = toolItem.quantity - 1)
                                                            }
                                                        },
                                                        enabled = toolItem.quantity > 1,
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.Remove, contentDescription = "Diminuir", modifier = Modifier.size(12.dp))
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .background(Color.White, RoundedCornerShape(4.dp))
                                                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("${toolItem.quantity} un.", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            manualToolsList[index] = toolItem.copy(quantity = toolItem.quantity + 1)
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = "Aumentar", modifier = Modifier.size(12.dp))
                                                    }
                                                }

                                                // Parecer Individual
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Button(
                                                        onClick = {
                                                            manualToolsList[index] = toolItem.copy(isApproved = true, rejectionReason = "")
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (toolItem.isApproved) StatusGreen else Color(0xFFE2E8F0)
                                                        ),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.height(26.dp)
                                                    ) {
                                                        Text(
                                                            "Aprovado",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (toolItem.isApproved) Color.White else Color.Black
                                                        )
                                                    }

                                                    Button(
                                                        onClick = {
                                                            manualToolsList[index] = toolItem.copy(
                                                                isApproved = false,
                                                                rejectionReason = if (toolItem.rejectionReason.isBlank()) "Isolação danificada / perfuração" else toolItem.rejectionReason
                                                            )
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (!toolItem.isApproved) StatusRed else Color(0xFFE2E8F0)
                                                        ),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.height(26.dp)
                                                    ) {
                                                        Text(
                                                            "Reprovado",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (!toolItem.isApproved) Color.White else Color.Black
                                                        )
                                                    }
                                                }
                                            }

                                            if (!toolItem.isApproved) {
                                                OutlinedTextField(
                                                    value = toolItem.rejectionReason,
                                                    onValueChange = { newReason ->
                                                        manualToolsList[index] = toolItem.copy(rejectionReason = newReason)
                                                    },
                                                    placeholder = { Text("Motivo da reprovação individual...", fontSize = 9.sp) },
                                                    colors = editTextFieldColors(),
                                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Adicionar nova ferramenta
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = customToolNameInput,
                                    onValueChange = { customToolNameInput = it },
                                    placeholder = { Text("Nome da ferramenta...", fontSize = 10.sp) },
                                    colors = editTextFieldColors(),
                                    modifier = Modifier.weight(1f).height(44.dp)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .height(44.dp)
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = { if (customToolQuantityInput > 1) customToolQuantityInput-- },
                                        enabled = customToolQuantityInput > 1,
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(12.dp))
                                    }
                                    Text("$customToolQuantityInput un.", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    IconButton(
                                        onClick = { customToolQuantityInput++ },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (customToolNameInput.isNotBlank()) {
                                            manualToolsList.add(
                                                ManualToolItem(
                                                    name = customToolNameInput.trim(),
                                                    quantity = customToolQuantityInput,
                                                    isApproved = true,
                                                    isSelected = true
                                                )
                                            )
                                            customToolNameInput = ""
                                            customToolQuantityInput = 1
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Add", fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // 5. Parâmetros Elétricos & Medições
                    EditSectionCard(title = "5. Parâmetros do Ensaio Dielétrico", icon = Icons.Default.ElectricBolt) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = normCode,
                                onValueChange = { normCode = it },
                                label = { Text("Norma Aplicável") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1.2f)
                            )
                            OutlinedTextField(
                                value = procedureCode,
                                onValueChange = { procedureCode = it },
                                label = { Text("Procedimento") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(0.8f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = appliedVoltageKV,
                                onValueChange = { appliedVoltageKV = it },
                                label = { Text("Tensão Aplicada (kV)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("AC", "DC").forEach { type ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(if (voltageType == type) NavyPrimary else Color.Transparent, RoundedCornerShape(6.dp))
                                            .clickable { voltageType = type },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            type,
                                            fontWeight = if (voltageType == type) FontWeight.Bold else FontWeight.Normal,
                                            color = if (voltageType == type) Color.White else Color(0xFF475569),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = durationSeconds,
                                onValueChange = { durationSeconds = it },
                                label = { Text("Duração (s)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(0.8f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = measuredLeakageMA,
                                onValueChange = { input -> measuredLeakageMA = input.filter { it.isDigit() || it == '.' || it == ',' } },
                                label = { Text("Fuga Medida (mA)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = leakageLimitMA,
                                onValueChange = { leakageLimitMA = it },
                                label = { Text("Limite de Fuga (mA)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (selectedEquipmentType == EquipmentType.ESCADA_ISOLADA) {
                            // Escada: limite de fuga = pontos ensaiados × limite da norma
                            OutlinedTextField(
                                value = stickPointsInput,
                                onValueChange = { input ->
                                    stickPointsInput = input.filter { it.isDigit() }.take(3)
                                    val pts = stickPointsInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                    leakageLimitMA = String.format(Locale.US, "%.2f", ladderPerPointLimit * pts)
                                },
                                label = { Text("Quantidade de pontos ensaiados", color = Color.Black) },
                                supportingText = {
                                    Text("Limite de fuga: $stickTestPoints × ${String.format(Locale.US, "%.2f", ladderPerPointLimit)} mA = ${String.format(Locale.US, "%.2f", ladderPerPointLimit * stickTestPoints)} mA", fontSize = 10.sp, color = Color(0xFF15803D))
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = editTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Switches e Inspeção
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Suportabilidade Dielétrica", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = NavyPrimary)
                                Text("Sem disrupção, centelhamento ou perfuração durante a aplicação.", fontSize = 10.sp, color = Color(0xFF64748B))
                            }
                            Switch(
                                checked = withstandWithoutPuncture,
                                onCheckedChange = { withstandWithoutPuncture = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = StatusGreen
                                )
                            )
                        }

                        OutlinedTextField(
                            value = visualInspectionSummary,
                            onValueChange = { visualInspectionSummary = it },
                            label = { Text("Inspeção Visual e Mecânica") },
                            colors = editTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val checklist = NormsEngine.getVisualChecklist(selectedEquipmentType)
                                    visualInspectionSummary = "100% Conforme (${checklist.size}/${checklist.size} itens conformes segundo $normCode)"
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("100% Conforme", fontSize = 10.sp, color = StatusGreen, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    visualInspectionSummary = "Não Conforme: Apresenta avaria/trinca/ressecamento físico-mecânico"
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Não Conforme", fontSize = 10.sp, color = StatusRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 6. Condições Ambientais & Local
                    EditSectionCard(title = "6. Condições Ambientais & Local", icon = Icons.Default.Thermostat) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = temperatureC,
                                onValueChange = { input -> temperatureC = input.filter { it.isDigit() || it == '.' || it == ',' || it == '-' } },
                                label = { Text("Temperatura (°C)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = relativeHumidityPercent,
                                onValueChange = { input -> relativeHumidityPercent = input.filter { it.isDigit() || it == '.' || it == ',' } },
                                label = { Text("Umidade Relativa (%)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("Local de Realização do Ensaio") },
                            colors = editTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 7. Registro Fotográfico do Ensaio (Pasta da OS)
                    PhotoCaptureSection(
                        photos = photoList,
                        onPhotosChanged = { updatedList ->
                            photoList.clear()
                            photoList.addAll(updatedList)
                        },
                        serviceOrderNumber = serviceOrderNumber.ifBlank { test.serviceOrderNumber.ifBlank { "OS-AVULSA" } },
                        equipmentTag = equipmentTag,
                        clientName = clientName
                    )

                    // 8. Parecer Técnico Conclusivo
                    EditSectionCard(title = "8. Parecer Técnico & Validade", icon = Icons.Default.Check) {
                        // Toggle de Resultado
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    testResult = "APROVADO"
                                    if (retestDueDate.isBlank()) {
                                        retestDueDate = com.example.util.DateUtils.calculateRetestDueDate(testDate, 6)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (testResult == "APROVADO") StatusGreen else Color.Transparent
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "APROVADO",
                                    fontWeight = FontWeight.Bold,
                                    color = if (testResult == "APROVADO") Color.White else Color(0xFF475569),
                                    fontSize = 12.sp
                                )
                            }

                            Button(
                                onClick = {
                                    testResult = "REPROVADO"
                                    retestDueDate = ""
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (testResult == "REPROVADO") StatusRed else Color.Transparent
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "REPROVADO",
                                    fontWeight = FontWeight.Bold,
                                    color = if (testResult == "REPROVADO") Color.White else Color(0xFF475569),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        val hasDualOpinions = isFerramenta && manualToolsList.any { !it.isApproved } && manualToolsList.any { it.isApproved }
                        if (hasDualOpinions) {
                            val appTools = manualToolsList.filter { it.isApproved }
                            val rejTools = manualToolsList.filter { !it.isApproved }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFD97706), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "⚡ Emissão de 2 Pareceres Técnicos Independentes (NR-10)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E)
                                    )
                                    Text(
                                        text = "• Parecer 1 (Aprovadas - ${appTools.sumOf { it.quantity }} un.): Conforme NBR 9699, liberadas para uso até 1000V.\n• Parecer 2 (Reprovadas - ${rejTools.sumOf { it.quantity }} un.): Condenadas para segregação e descarte.",
                                        fontSize = 10.sp,
                                        color = Color(0xFF78350F)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = resultRationale,
                            onValueChange = { resultRationale = it },
                            label = { Text("Justificativa Técnica do Resultado / Pareceres") },
                            colors = editTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        OutlinedTextField(
                            value = technicalNotes,
                            onValueChange = { technicalNotes = it },
                            label = { Text("Observações / Composição do Ensaio") },
                            colors = editTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = testDate,
                                onValueChange = { testDate = it },
                                label = { Text("Data do Ensaio (dd/mm/aaaa)") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = testTime,
                                onValueChange = { testTime = it },
                                label = { Text("Hora") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(0.8f)
                            )
                        }

                        // Validade com atalhos rápidos
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = retestDueDate,
                                onValueChange = { retestDueDate = it },
                                label = { Text("Data de Validade / Reensaio (dd/mm/aaaa)") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(3 to "+3 Meses", 6 to "+6 Meses", 12 to "+12 Meses").forEach { (months, label) ->
                                    OutlinedButton(
                                        onClick = {
                                            retestDueDate = com.example.util.DateUtils.calculateRetestDueDate(testDate, months)
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f).height(30.dp)
                                    ) {
                                        Text(label, fontSize = 9.sp, color = NavyPrimary)
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = technicianName,
                                onValueChange = { technicianName = it },
                                label = { Text("Analista / Executor") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = technicianCftOrCrea,
                                onValueChange = { technicianCftOrCrea = it },
                                label = { Text("Registro Executor") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = techResponsibleName,
                                onValueChange = { techResponsibleName = it },
                                label = { Text("Responsável Técnico") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = techResponsibleCrea,
                                onValueChange = { techResponsibleCrea = it },
                                label = { Text("CREA Resp. Técnico") },
                                colors = editTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Bottom Action Buttons (Cancelar e Salvar)
                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("Cancelar", fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    }

                    Button(
                        onClick = {
                            val vApplied = appliedVoltageKV.replace(",", ".").toDoubleOrNull() ?: test.appliedVoltage_kV
                            val dur = durationSeconds.toIntOrNull() ?: test.applicationDurationSeconds
                            val leak = measuredLeakageMA.replace(",", ".").toDoubleOrNull() ?: test.measuredLeakageCurrent_mA
                            val limit = leakageLimitMA.replace(",", ".").toDoubleOrNull() ?: test.leakageCurrentLimit_mA
                            val temp = temperatureC.replace(",", ".").toDoubleOrNull() ?: test.temperatureC
                            val hum = relativeHumidityPercent.replace(",", ".").toDoubleOrNull() ?: test.relativeHumidityPercent

                            val updatedNotes = if (isFerramenta) {
                                val encoded = ManualToolItem.encodeListToNotes(manualToolsList)
                                val summaryDisplay = manualToolsList.joinToString("; ") { it.toDisplayText() }
                                val cleanUserNotes = technicalNotes.substringBefore("FERRAMENTAS_V2:").substringBefore("Ferramentas Ensaidas").trim()
                                if (cleanUserNotes.isNotBlank()) "$cleanUserNotes\n\nFerramentas Ensaidas (${manualToolsList.sumOf { it.quantity }} un.):\n$summaryDisplay\n$encoded"
                                else "Ferramentas Ensaidas (${manualToolsList.sumOf { it.quantity }} un.):\n$summaryDisplay\n$encoded"
                            } else {
                                technicalNotes
                            }

                            val updatedEntity = test.copy(
                                reportNumber = reportNumber.trim(),
                                certificateNumber = certificateNumber.trim(),
                                testNumber = testNumber.trim(),
                                serviceOrderNumber = serviceOrderNumber.trim(),
                                artNumber = artNumber.trim(),
                                validationCode = validationCode.trim(),
                                clientName = clientName.trim(),
                                collaboratorName = collaboratorName.trim(),
                                collaboratorRegistration = collaboratorRegistration.trim(),
                                collaboratorSector = collaboratorSector.trim(),
                                equipmentTag = equipmentTag.trim(),
                                equipmentType = selectedEquipmentType.name,
                                equipmentClass = selectedEquipmentClass.label,
                                equipmentSerial = equipmentSerial.trim(),
                                equipmentCa = equipmentCa.trim(),
                                gloveLength_mm = gloveLengthMm,
                                normCode = normCode.trim(),
                                procedureCode = procedureCode.trim(),
                                appliedVoltage_kV = vApplied,
                                voltageType = voltageType,
                                applicationDurationSeconds = dur,
                                measuredLeakageCurrent_mA = leak,
                                leakageCurrentLimit_mA = limit,
                                withstandWithoutPuncture = withstandWithoutPuncture,
                                visualInspectionSummary = visualInspectionSummary.trim(),
                                temperatureC = temp,
                                relativeHumidityPercent = hum,
                                location = location.trim(),
                                result = testResult,
                                resultRationale = resultRationale.trim(),
                                currentUnit = if (com.example.domain.NormsEngine.isStickType(selectedEquipmentType)) "µA" else test.currentUnit.ifBlank { "mA" },
                                technicalNotes = updatedNotes,
                                testDate = testDate.trim(),
                                testTime = testTime.trim(),
                                retestDueDate = if (testResult == "REPROVADO") "" else retestDueDate.trim(),
                                technicianName = technicianName.trim(),
                                technicianCftOrCrea = technicianCftOrCrea.trim(),
                                techResponsibleName = techResponsibleName.trim(),
                                techResponsibleCrea = techResponsibleCrea.trim(),
                                photoUris = photoList.joinToString("|||") { "${it.label}::${it.uriString}" },
                                stickTestPoints = if (com.example.domain.NormsEngine.isStickType(selectedEquipmentType) || selectedEquipmentType == EquipmentType.ESCADA_ISOLADA) stickTestPoints else 1,
                                updatedAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                            )

                            onSave(updatedEntity)
                            Toast.makeText(context, "Laudo e Certificado atualizados com sucesso!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.5f).height(46.dp).testTag("save_edited_test_button")
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvar Alterações", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
            }
            content()
        }
    }
}

@Composable
private fun editTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFF0F172A),
    unfocusedTextColor = Color(0xFF0F172A),
    disabledTextColor = Color(0xFF334155),
    focusedBorderColor = NavyPrimary,
    unfocusedBorderColor = Color(0xFF94A3B8),
    disabledBorderColor = Color(0xFFCBD5E1),
    focusedLabelColor = NavyPrimary,
    unfocusedLabelColor = Color(0xFF334155),
    disabledLabelColor = Color(0xFF64748B),
    cursorColor = NavyPrimary,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color(0xFFF8FAFC),
    focusedSupportingTextColor = Color(0xFF475569),
    unfocusedSupportingTextColor = Color(0xFF475569),
    focusedPrefixColor = Color(0xFF0F172A),
    unfocusedPrefixColor = Color(0xFF0F172A),
    focusedSuffixColor = Color(0xFF0F172A),
    unfocusedSuffixColor = Color(0xFF0F172A),
    focusedPlaceholderColor = Color(0xFF94A3B8),
    unfocusedPlaceholderColor = Color(0xFF94A3B8)
)

/** Valor numérico para campo de texto: vazio quando zero e sem ".0" desnecessário. */
private fun numberText(value: Double): String = when {
    value == 0.0 -> ""
    value % 1.0 == 0.0 -> value.toLong().toString()
    else -> value.toString()
}
