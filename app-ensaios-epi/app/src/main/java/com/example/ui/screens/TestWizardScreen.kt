package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppSettingEntity
import com.example.data.model.DielectricClass
import com.example.data.model.ManualToolItem
import com.example.data.model.ServiceOrderEntity
import com.example.data.model.EquipmentEntity
import com.example.data.model.EquipmentType
import com.example.data.model.TestRecordEntity
import com.example.data.model.TestResult
import com.example.domain.NormsEngine
import com.example.domain.VisualChecklistItem
import com.example.ui.components.PhotoCaptureSection
import com.example.ui.components.SignaturePad
import com.example.ui.components.SpeedometerGauge
import com.example.ui.components.StatusBadge
import com.example.ui.components.TestPhotoItem
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.appTextFieldColors
import com.example.ui.viewmodel.DielectricViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ChecklistStatus(val label: String, val shortLabel: String) {
    CONFORME("Conforme", "OK"),
    NAO_SE_APLICA("Não se Aplica", "N/A"),
    NAO_CONFORME("Não Conforme", "Não Conf.")
}

data class VisualChecklistEntry(
    val item: VisualChecklistItem,
    val status: ChecklistStatus = ChecklistStatus.CONFORME,
    val observation: String = ""
) {
    val isConforme: Boolean get() = status == ChecklistStatus.CONFORME
    val isNaoSeAplica: Boolean get() = status == ChecklistStatus.NAO_SE_APLICA
    val isNaoConforme: Boolean get() = status == ChecklistStatus.NAO_CONFORME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestWizardScreen(
    viewModel: DielectricViewModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val equipmentList by viewModel.equipment.collectAsStateWithLifecycle()
    val serviceOrders by viewModel.serviceOrders.collectAsStateWithLifecycle()
    val tests by viewModel.tests.collectAsStateWithLifecycle()
    val instruments by viewModel.instruments.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentCompany by viewModel.currentCompany.collectAsStateWithLifecycle()
    val preSelectedEqId by viewModel.wizardEquipmentId.collectAsStateWithLifecycle()
    val preSelectedOSId by viewModel.wizardOSId.collectAsStateWithLifecycle()
    val editingTest by viewModel.editingTest.collectAsStateWithLifecycle()
    val isEditMode = editingTest != null

    var currentStep by remember { mutableIntStateOf(1) }
    var loadedEditingTestId by remember { mutableStateOf<String?>(null) }
    var lastEquipmentType by remember { mutableStateOf<EquipmentType?>(null) }
    var lastDielectricClass by remember { mutableStateOf<DielectricClass?>(null) }
    var testDateInput by remember { mutableStateOf(com.example.util.DateUtils.today()) }
    var testTimeInput by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }

    // Step 1: Identification & Equipment
    var reportNumberInput by remember { mutableStateOf("") }
    var selectedClientId by remember { mutableStateOf("") }
    var selectedOSId by remember { mutableStateOf("") }
    var showEditOSDialog by remember { mutableStateOf(false) }
    var showDeleteOSConfirm by remember { mutableStateOf(false) }
    var osToEditInWizard by remember { mutableStateOf<ServiceOrderEntity?>(null) }
    var selectedEquipmentType by remember { mutableStateOf(EquipmentType.LUVA_ISOLANTE) }
    var selectedDielectricClass by remember { mutableStateOf(DielectricClass.CLASS_2) }
    var equipmentTag by remember { mutableStateOf("TAG-LUV-01") }
    var equipmentSerial by remember { mutableStateOf("") }
    var equipmentCa by remember { mutableStateOf("") }
    var equipmentManufacturer by remember { mutableStateOf("") }
    var collaboratorName by remember { mutableStateOf("") }
    var collaboratorRegistration by remember { mutableStateOf("") }
    var collaboratorSector by remember { mutableStateOf("") }
    var gloveLength_mm by remember { mutableIntStateOf(360) }
    var stickDiameter_mm by remember { mutableIntStateOf(38) }
    // bastões/varas e escada: quantidade de pontos ensaiados (limite = pontos × limite da tabela)
    var stickPointsInput by remember { mutableStateOf("1") }
    val stickTestPoints: Int = stickPointsInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
    var currentUnit by remember { mutableStateOf("mA") }

    // Ferramentas Manuais Isoladas (lista de itens com quantidade e aprovação individual)
    val defaultManualToolsList = listOf(
        ManualToolItem(name = "Chave de Fenda", quantity = 1, isApproved = true, isSelected = true),
        ManualToolItem(name = "Chave de Fenda Cruzada (Phillips)", quantity = 1, isApproved = true, isSelected = true),
        ManualToolItem(name = "Alicate Universal", quantity = 1, isApproved = true, isSelected = true),
        ManualToolItem(name = "Alicate de Bico", quantity = 1, isApproved = true, isSelected = false),
        ManualToolItem(name = "Alicate de Corte", quantity = 1, isApproved = true, isSelected = false),
        ManualToolItem(name = "Chave Canhão", quantity = 1, isApproved = true, isSelected = false),
        ManualToolItem(name = "Chave Faca / Desencapador", quantity = 1, isApproved = true, isSelected = false),
        ManualToolItem(name = "Ponteira de Prova Isolada", quantity = 1, isApproved = true, isSelected = false),
        ManualToolItem(name = "Chave Combinada / Estrela", quantity = 1, isApproved = true, isSelected = false),
        ManualToolItem(name = "Chave Soquete Isolada", quantity = 1, isApproved = true, isSelected = false),
        ManualToolItem(name = "Chave Ajustável", quantity = 1, isApproved = true, isSelected = false),
        ManualToolItem(name = "Chave de Torque", quantity = 1, isApproved = true, isSelected = false),
        ManualToolItem(name = "Chave Allen", quantity = 1, isApproved = true, isSelected = false)
    )
    val manualToolsList = remember {
        mutableStateListOf<ManualToolItem>().apply { addAll(defaultManualToolsList) }
    }
    var customToolNameInput by remember { mutableStateOf("") }
    var customToolQuantityInput by remember { mutableIntStateOf(1) }

    val activeManualTools = manualToolsList.filter { it.isSelected }
    val totalManualToolsCount = if (selectedEquipmentType == EquipmentType.FERRAMENTA_ISOLADA) {
        activeManualTools.sumOf { it.quantity }.coerceAtLeast(1)
    } else 1

    val rejectedManualToolsList = activeManualTools.filter { !it.isApproved }
    val rejectedManualToolsCount = if (selectedEquipmentType == EquipmentType.FERRAMENTA_ISOLADA) {
        rejectedManualToolsList.sumOf { it.quantity }
    } else 0

    val rejectedManualToolsSummary = if (selectedEquipmentType == EquipmentType.FERRAMENTA_ISOLADA) {
        rejectedManualToolsList.joinToString("; ") { tool ->
            "${tool.name} (${tool.quantity} un.${if (tool.rejectionReason.isNotBlank()) " - Motivo: ${tool.rejectionReason}" else ""})"
        }
    } else ""

    // Step 2: Environmental
    // Campos digitados como texto: apagar não reescreve "0.0" nem outro valor padrão
    var temperatureInput by remember { mutableStateOf("") }
    var humidityInput by remember { mutableStateOf("") }
    val temperatureC: Double = temperatureInput.replace(",", ".").toDoubleOrNull() ?: 0.0
    val relativeHumidityPercent: Double = humidityInput.replace(",", ".").toDoubleOrNull() ?: 0.0
    var environmentPrefilled by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf("Laboratório Móvel JVM") }

    // Step 3: Visual Checklist (Dynamic per equipment type)
    val visualItems = remember { mutableStateListOf<VisualChecklistEntry>() }

    // Dynamically update visual checklist and auto-suggest TAG whenever equipment type changes
    LaunchedEffect(selectedEquipmentType, selectedClientId) {
        if (isEditMode && loadedEditingTestId == editingTest?.id && lastEquipmentType == selectedEquipmentType) {
            return@LaunchedEffect
        }
        val standardChecklist = NormsEngine.getVisualChecklist(selectedEquipmentType)
        visualItems.clear()
        visualItems.addAll(standardChecklist.map { VisualChecklistEntry(item = it, status = ChecklistStatus.CONFORME) })

        if (!isEditMode && preSelectedEqId.isNullOrEmpty()) {
            val clientObj = clients.find { it.id == selectedClientId }
            val cName = clientObj?.nomeFantasia ?: clientObj?.razaoSocial
            equipmentTag = selectedEquipmentType.suggestTag(clientName = cName, existingTags = equipmentList.map { it.tag })
        }
        currentUnit = if (com.example.domain.NormsEngine.isStickType(selectedEquipmentType)) "µA" else "mA"
        lastEquipmentType = selectedEquipmentType
    }

    // Step 4: Electrical Measurements & Norm Criteria Customization
    var isEditingCriteria by remember { mutableStateOf(false) }
    var isCriteriaCustomized by remember { mutableStateOf(false) }
    // Norma e critério técnico escolhidos (como na web); vazio = critério da lista para o tipo e a classe
    var selectedNormId by remember { mutableStateOf("") }
    val normsRevision = com.example.domain.NormsCatalog.revision.intValue
    // Detector por indução: distância (cm) e "sinalizou" em cada tensão da tabela (1 a 50 kV)
    val inductionDistances = remember { mutableStateListOf(*Array(com.example.domain.InductionDetector.VOLTAGES_KV.size) { "" }) }
    val inductionDetected = remember { mutableStateListOf(*Array(com.example.domain.InductionDetector.VOLTAGES_KV.size) { true }) }
    val inductionPointsCurrent = com.example.domain.InductionDetector.VOLTAGES_KV.mapIndexed { i, v ->
        com.example.domain.InductionPoint(v, inductionDistances[i].replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 }, inductionDetected[i])
    }
    var customNormCodeInput by remember { mutableStateOf("") }
    var customTargetVoltageInput by remember { mutableStateOf("") }
    var customLeakageLimitInput by remember { mutableStateOf("") }
    var customDurationSecondsInput by remember { mutableStateOf("") }
    var customRetestMonthsInput by remember { mutableStateOf("") }

    var appliedVoltage_kV by remember { mutableDoubleStateOf(20.0) }
    var voltageType by remember { mutableStateOf("AC") }
    var durationSeconds by remember { mutableIntStateOf(60) }
    var appliedVoltageInput by remember { mutableStateOf(formatNumberInput(20.0)) }
    var durationInput by remember { mutableStateOf("60") }
    // valores definidos pela norma/critério atualizam o texto do campo
    LaunchedEffect(appliedVoltage_kV) {
        if (appliedVoltageInput.replace(",", ".").toDoubleOrNull() != appliedVoltage_kV) {
            appliedVoltageInput = formatNumberInput(appliedVoltage_kV)
        }
    }
    LaunchedEffect(durationSeconds) {
        if (durationInput.toIntOrNull() != durationSeconds) durationInput = durationSeconds.toString()
    }
    var measuredLeakageInput by remember { mutableStateOf("") }
    val measuredLeakage_mA: Double = remember(measuredLeakageInput) {
        measuredLeakageInput.replace(",", ".").toDoubleOrNull() ?: 0.0
    }
    var withstandWithoutPuncture by remember { mutableStateOf(true) }
    val photoList = remember { mutableStateListOf<TestPhotoItem>() }

    // Step 5: Lab Instruments
    val selectedInstrumentIds = remember { mutableStateListOf<String>() }

    // Step 6: Signatures & Finish
    var currentTechName by remember { mutableStateOf("") }
    var currentTechCft by remember { mutableStateOf("") }
    var currentRtName by remember { mutableStateOf("") }
    var currentRtCrea by remember { mutableStateOf("") }
    var technicianSig by remember { mutableStateOf("") }
    var rtSig by remember { mutableStateOf("") }
    var technicalNotes by remember { mutableStateOf("") }

    // Initialize technical data and signatures from settings
    LaunchedEffect(settings) {
        settings?.let { s ->
            if (currentTechName.isBlank()) currentTechName = s.defaultTechnicianName
            if (currentTechCft.isBlank()) currentTechCft = s.defaultTechnicianCftOrCrea
            if (currentRtName.isBlank()) currentRtName = s.technicalResponsibleName
            if (currentRtCrea.isBlank()) currentRtCrea = s.technicalResponsibleCrea
            if (technicianSig.isBlank()) technicianSig = s.defaultTechnicianSignatureImage
            if (rtSig.isBlank()) rtSig = s.technicalResponsibleSignatureImage
        }
    }

    // Load existing test record into state when editing
    LaunchedEffect(editingTest?.id) {
        val test = editingTest
        if (test != null && loadedEditingTestId != test.id) {
            loadedEditingTestId = test.id
            reportNumberInput = test.reportNumber
            selectedClientId = test.clientId
            selectedOSId = test.serviceOrderId
            val eqType = EquipmentType.fromString(test.equipmentType)
            val dieClass = DielectricClass.fromString(test.equipmentClass)
            selectedEquipmentType = eqType
            selectedDielectricClass = dieClass
            lastEquipmentType = eqType
            lastDielectricClass = dieClass

            equipmentTag = test.equipmentTag
            equipmentSerial = test.equipmentSerial
            equipmentCa = test.equipmentCa
            val matchedEq = equipmentList.find { it.id == test.equipmentId || it.tag == test.equipmentTag }
            if (matchedEq != null && matchedEq.manufacturer.isNotBlank()) {
                equipmentManufacturer = matchedEq.manufacturer
            }
            collaboratorName = test.collaboratorName
            collaboratorRegistration = test.collaboratorRegistration
            collaboratorSector = test.collaboratorSector
            gloveLength_mm = test.gloveLength_mm
            if (com.example.domain.NormsEngine.isStickType(eqType)) {
                stickDiameter_mm = if (test.gloveLength_mm in listOf(32, 38, 51, 64)) test.gloveLength_mm else 38
                currentUnit = test.currentUnit.ifBlank { "µA" }
            } else {
                currentUnit = test.currentUnit.ifBlank { "mA" }
            }

            testDateInput = test.testDate.ifBlank { com.example.util.DateUtils.today() }
            testTimeInput = test.testTime.ifBlank { "10:00" }

            if (eqType == EquipmentType.FERRAMENTA_ISOLADA) {
                val parsedTools = ManualToolItem.parseListFromNotes(test.technicalNotes)
                if (parsedTools.isNotEmpty()) {
                    manualToolsList.clear()
                    manualToolsList.addAll(parsedTools)
                }
            }

            environmentPrefilled = true
            temperatureInput = formatNumberInput(test.temperatureC)
            stickPointsInput = test.stickTestPoints.coerceAtLeast(1).toString()
            // detector por indução: tabela distância × tensão salva
            com.example.domain.InductionDetector.normalize(com.example.domain.InductionDetector.parse(test.inductionPointsJson)).forEach { p ->
                val i = com.example.domain.InductionDetector.VOLTAGES_KV.indexOf(p.voltage_kV)
                if (i >= 0) {
                    inductionDistances[i] = p.distance_cm?.let { d -> com.example.domain.InductionDetector.fmtBr1(d) } ?: ""
                    inductionDetected[i] = p.detected
                }
            }
            humidityInput = formatNumberInput(test.relativeHumidityPercent)
            location = test.location

            val standardChecklist = NormsEngine.getVisualChecklist(eqType)
            visualItems.clear()
            visualItems.addAll(standardChecklist.map { item ->
                val isNonConf = test.visualInspectionSummary.contains(item.title, ignoreCase = true) &&
                        (test.visualInspectionSummary.contains("Não Conforme", ignoreCase = true) || test.visualInspectionSummary.contains("reprovado", ignoreCase = true))
                VisualChecklistEntry(
                    item = item,
                    status = if (isNonConf) ChecklistStatus.NAO_CONFORME else ChecklistStatus.CONFORME
                )
            })

            appliedVoltage_kV = test.appliedVoltage_kV
            voltageType = test.voltageType
            durationSeconds = test.applicationDurationSeconds
            measuredLeakageInput = if (test.measuredLeakageCurrent_mA > 0.0) {
                if (test.measuredLeakageCurrent_mA % 1.0 == 0.0) test.measuredLeakageCurrent_mA.toInt().toString()
                else String.format(Locale.US, "%.2f", test.measuredLeakageCurrent_mA)
            } else ""
            withstandWithoutPuncture = test.withstandWithoutPuncture

            val std = NormsEngine.getStandardCriteria(
                equipmentType = eqType,
                dielectricClass = dieClass.label,
                gloveLength_mm = test.gloveLength_mm,
                toolsCount = if (eqType == EquipmentType.FERRAMENTA_ISOLADA) manualToolsList.filter { it.isSelected }.sumOf { it.quantity }.coerceAtLeast(1) else 1,
                stickDiameter_mm = if (com.example.domain.NormsEngine.isStickType(eqType)) {
                    if (test.gloveLength_mm in listOf(32, 38, 51, 64)) test.gloveLength_mm else 38
                } else 38,
                stickTestPoints = test.stickTestPoints
            )
            val customVolt = kotlin.math.abs(test.appliedVoltage_kV - std.targetVoltage_kV) > 0.1
            val customLimit = kotlin.math.abs(test.leakageCurrentLimit_mA - std.leakageLimit_mA) > 0.05
            val customDur = test.applicationDurationSeconds != std.durationSeconds
            val customNorm = test.normCode != std.normCode && test.normCode.isNotBlank()

            if (customVolt || customLimit || customDur || customNorm) {
                isCriteriaCustomized = true
                customNormCodeInput = test.normCode
                customTargetVoltageInput = test.appliedVoltage_kV.toString()
                customLeakageLimitInput = String.format(Locale.US, "%.2f", test.leakageCurrentLimit_mA)
                customDurationSecondsInput = test.applicationDurationSeconds.toString()
            } else {
                isCriteriaCustomized = false
                customNormCodeInput = std.normCode
                customTargetVoltageInput = std.targetVoltage_kV.toString()
                customLeakageLimitInput = String.format(Locale.US, "%.2f", std.leakageLimit_mA)
                customDurationSecondsInput = std.durationSeconds.toString()
            }

            photoList.clear()
            if (test.photoUris.isNotBlank()) {
                test.photoUris.split("|||").filter { it.isNotBlank() }.forEach { entry ->
                    val parts = entry.split("::", limit = 2)
                    if (parts.size == 2) {
                        photoList.add(TestPhotoItem(label = parts[0], uriString = parts[1]))
                    } else {
                        photoList.add(TestPhotoItem(label = "Foto do Ensaio", uriString = entry))
                    }
                }
            }

            currentTechName = test.technicianName
            currentTechCft = test.technicianCftOrCrea
            currentRtName = test.techResponsibleName
            currentRtCrea = test.techResponsibleCrea
            technicianSig = test.technicianSignature
            rtSig = test.techResponsibleSignature

            val cleanNotes = if (test.technicalNotes.contains("FERRAMENTAS_V2:")) {
                test.technicalNotes.substringBefore("FERRAMENTAS_V2:").substringBefore("Ferramentas Ensaidas").trim()
            } else test.technicalNotes
            technicalNotes = if (cleanNotes.contains("Equipamento higienizado em cuba com água desmineralizada", ignoreCase = true)) {
                cleanNotes.replace("Equipamento higienizado em cuba com água desmineralizada e aprovado para uso operacional.", "").trim()
            } else cleanNotes
        } else if (test == null) {
            if (loadedEditingTestId != null) {
                loadedEditingTestId = null
                reportNumberInput = ""
            }
            if (reportNumberInput.isBlank()) {
                reportNumberInput = viewModel.generateNextReportNumber()
            }
        }
    }

    // Initialize defaults based on pre-selections or available OS
    LaunchedEffect(preSelectedEqId, equipmentList, preSelectedOSId, serviceOrders, clients, tests) {
        if (editingTest != null) return@LaunchedEffect

        // Temperatura e umidade: sempre partem do último ensaio registrado
        if (!environmentPrefilled) {
            // 1º: valores digitados no último ensaio salvo neste aparelho
            val prefs = context.getSharedPreferences(ENV_PREFS, android.content.Context.MODE_PRIVATE)
            val savedTemp = prefs.getString(KEY_LAST_TEMPERATURE, "").orEmpty()
            val savedHum = prefs.getString(KEY_LAST_HUMIDITY, "").orEmpty()
            if (temperatureInput.isBlank()) temperatureInput = savedTemp
            if (humidityInput.isBlank()) humidityInput = savedHum
            // 2º: ensaio mais recente pela data/hora do ensaio (datas em dd/MM/aaaa não ordenam como texto)
            if (temperatureInput.isBlank() || humidityInput.isBlank()) {
                val previous = tests
                    .filter { it.temperatureC > 0.0 || it.relativeHumidityPercent > 0.0 }
                    .maxByOrNull { testTimestamp(it.testDate, it.testTime) }
                if (previous != null) {
                    if (temperatureInput.isBlank() && previous.temperatureC > 0.0) temperatureInput = formatNumberInput(previous.temperatureC)
                    if (humidityInput.isBlank() && previous.relativeHumidityPercent > 0.0) humidityInput = formatNumberInput(previous.relativeHumidityPercent)
                }
            }
            // só encerra quando achou valores ou a lista de ensaios já carregou
            if ((temperatureInput.isNotBlank() && humidityInput.isNotBlank()) || tests.isNotEmpty()) {
                environmentPrefilled = true
            }
        }

        val osId = preSelectedOSId
        if (!osId.isNullOrEmpty()) {
            selectedOSId = osId
            val os = serviceOrders.find { it.id == osId }
            if (os != null) {
                selectedClientId = os.clientId
            }
        } else if (selectedOSId.isEmpty() && serviceOrders.isNotEmpty()) {
            val defaultOs = serviceOrders.first()
            selectedOSId = defaultOs.id
            selectedClientId = defaultOs.clientId
        } else if (selectedClientId.isEmpty() && clients.isNotEmpty()) {
            selectedClientId = clients.first().id
        }

        val eqId = preSelectedEqId
        if (!eqId.isNullOrEmpty()) {
            val eq = equipmentList.find { it.id == eqId }
            if (eq != null) {
                selectedClientId = eq.clientId
                selectedEquipmentType = EquipmentType.fromString(eq.type)
                selectedDielectricClass = DielectricClass.fromString(eq.dielectricClass)
                equipmentTag = eq.tag
                equipmentSerial = eq.serialNumber
                equipmentCa = eq.caNumber
                equipmentManufacturer = eq.manufacturer
                collaboratorName = eq.collaboratorName
                collaboratorRegistration = eq.collaboratorRegistration
                collaboratorSector = eq.collaboratorSector
                gloveLength_mm = eq.gloveLength_mm
            }
        }
        // Carregar colaborador, matrícula e setor do último ensaio cadastrado se ainda estiverem em branco
        val lastTestWithCollaborator = tests.firstOrNull { it.collaboratorName.isNotBlank() }
            ?: viewModel.tests.value.firstOrNull { it.collaboratorName.isNotBlank() }
        if (lastTestWithCollaborator != null) {
            if (collaboratorName.isBlank()) {
                collaboratorName = lastTestWithCollaborator.collaboratorName
            }
            if (collaboratorSector.isBlank() && lastTestWithCollaborator.collaboratorSector.isNotBlank()) {
                collaboratorSector = lastTestWithCollaborator.collaboratorSector
            }
            if (collaboratorRegistration.isBlank() && lastTestWithCollaborator.collaboratorRegistration.isNotBlank()) {
                collaboratorRegistration = lastTestWithCollaborator.collaboratorRegistration
            }
        }
        if (selectedInstrumentIds.isEmpty() && instruments.isNotEmpty()) {
            selectedInstrumentIds.addAll(instruments.map { it.id })
        }
    }

    // Auto-update applied voltage and norm parameters when type or class changes
    LaunchedEffect(selectedEquipmentType, selectedDielectricClass, gloveLength_mm, stickDiameter_mm, stickTestPoints, totalManualToolsCount, rejectedManualToolsCount, rejectedManualToolsSummary, selectedNormId, normsRevision) {
        if (isEditMode && loadedEditingTestId == editingTest?.id && lastEquipmentType == selectedEquipmentType && lastDielectricClass == selectedDielectricClass) {
            return@LaunchedEffect
        }
        // tipo ou classe mudou: volta ao critério da lista para o novo tipo/classe (como na web)
        if (lastEquipmentType != null && (lastEquipmentType != selectedEquipmentType || lastDielectricClass != selectedDielectricClass) && selectedNormId.isNotBlank()) {
            val chosen = com.example.domain.NormsCatalog.criteria.find { it.id == selectedNormId }
            if (chosen == null || NormsEngine.webType(selectedEquipmentType) !in chosen.applicableEquipmentTypes || chosen.dielectricClass != selectedDielectricClass.label) {
                selectedNormId = ""
            }
        }
        val std = NormsEngine.getStandardCriteria(
            equipmentType = selectedEquipmentType,
            dielectricClass = selectedDielectricClass.label,
            gloveLength_mm = gloveLength_mm,
            toolsCount = totalManualToolsCount,
            stickDiameter_mm = stickDiameter_mm,
            criterionId = selectedNormId,
            stickTestPoints = stickTestPoints
        )
        if (!isCriteriaCustomized) {
            customNormCodeInput = std.normCode
            customTargetVoltageInput = std.targetVoltage_kV.toString()
            customLeakageLimitInput = if (com.example.domain.NormsEngine.isStickType(selectedEquipmentType)) {
                std.leakageLimit_mA.toInt().toString()
            } else {
                String.format(Locale.US, "%.2f", std.leakageLimit_mA)
            }
            customDurationSecondsInput = std.durationSeconds.toString()
            customRetestMonthsInput = std.retestIntervalMonths.toString()
            appliedVoltage_kV = std.targetVoltage_kV
            durationSeconds = std.durationSeconds
        }
        lastEquipmentType = selectedEquipmentType
        lastDielectricClass = selectedDielectricClass
    }

    // Real-time evaluation calculation with custom criteria support
    val customTargetVoltage = if (isCriteriaCustomized) customTargetVoltageInput.toDoubleOrNull() else null
    val customLeakageLimit = if (isCriteriaCustomized) customLeakageLimitInput.toDoubleOrNull() else null
    val customDuration = if (isCriteriaCustomized) customDurationSecondsInput.toIntOrNull() else null
    val customRetest = if (isCriteriaCustomized) customRetestMonthsInput.toIntOrNull() else null
    val customNormRef = if (isCriteriaCustomized && customNormCodeInput.isNotBlank()) customNormCodeInput else null

    val currentEvaluation = NormsEngine.evaluate(
        NormsEngine.EvaluationInput(
            equipmentType = selectedEquipmentType,
            dielectricClass = selectedDielectricClass.label,
            appliedVoltage_kV = appliedVoltage_kV,
            durationSeconds = durationSeconds,
            measuredLeakage_mA = measuredLeakage_mA,
            withstandWithoutPuncture = withstandWithoutPuncture,
            visualPassed = visualItems.none { it.isNaoConforme },
            gloveLength_mm = gloveLength_mm,
            toolsCount = totalManualToolsCount,
            individualToolsRejectedCount = rejectedManualToolsCount,
            rejectedToolsSummary = rejectedManualToolsSummary,
            customTargetVoltage_kV = customTargetVoltage,
            customLeakageLimit_mA = customLeakageLimit,
            customDurationSeconds = customDuration,
            customRetestMonths = customRetest,
            customNormReference = customNormRef,
            criterionId = selectedNormId,
            visualFailedItems = visualItems.filter { it.isNaoConforme }.map { it.item.title },
            stickDiameter_mm = stickDiameter_mm,
            stickTestPoints = stickTestPoints,
            inductionPoints = if (selectedEquipmentType == EquipmentType.DETECTOR_INDUCAO) inductionPointsCurrent else null
        )
    )

    val isApproved = currentEvaluation.result == TestResult.APROVADO
    val effTechName = currentTechName.ifBlank { settings?.defaultTechnicianName ?: "Eng. João Carlos Evaristo Guedes Nunes" }
    val effTechRole = currentTechCft.trim().let { if (it.isBlank()) "Analista Executor" else "$it • Analista Executor" }
    val effRtName = currentRtName.ifBlank { settings?.technicalResponsibleName ?: "Eng. João Carlos Evaristo Guedes Nunes" }
    val effRtRole = "${currentRtCrea.ifBlank { settings?.technicalResponsibleCrea ?: "CREA 24481/D-DF" }} • Responsável Técnico"

    // Detector por indução: grava tensão = maior da tabela, tempo 0, fuga 0 e sem perfuração (como a web)
    val isInductionTest = selectedEquipmentType == EquipmentType.DETECTOR_INDUCAO

    val saveTestAction: suspend () -> Unit = saveAction@{
        // Sem critério normativo a web não avalia nem emite laudo; o app segue a mesma regra
        if (!currentEvaluation.canEvaluate) {
            val msg = if (selectedEquipmentType == EquipmentType.DETECTOR_INDUCAO) currentEvaluation.rationale
                else "Critério técnico não configurado para este equipamento/classe. Escolha a norma no passo de medições ou cadastre o critério na plataforma web."
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            return@saveAction
        }
        val today = com.example.util.DateUtils.today()
        val clientObj = clients.find { it.id == selectedClientId }
        val osObj = serviceOrders.find { it.id == selectedOSId }

        val finalTechnicalNotes = if (selectedEquipmentType == EquipmentType.FERRAMENTA_ISOLADA) {
            val encodedTools = ManualToolItem.encodeListToNotes(activeManualTools)
            val toolsDisplay = activeManualTools.joinToString("; ") { it.toDisplayText() }
            val summaryHeader = "Ferramentas Ensaidas (${totalManualToolsCount} un. / ${activeManualTools.size} itens):\n$toolsDisplay"
            if (technicalNotes.isNotBlank()) "$technicalNotes\n\n$summaryHeader\n$encodedTools"
            else "$summaryHeader\n$encodedTools"
        } else {
            technicalNotes.trim()
        }

        val visualSummary = when {
            visualItems.any { it.isNaoConforme } -> {
                val nonConf = visualItems.filter { it.isNaoConforme }
                "Não Conforme (${nonConf.size}/${visualItems.size} item(ns) reprovado(s)): " + nonConf.joinToString("; ") { entry ->
                    if (entry.observation.isNotBlank()) "${entry.item.title} [${entry.observation}]" else entry.item.title
                }
            }
            visualItems.any { it.isNaoSeAplica } -> {
                val conforms = visualItems.count { it.isConforme }
                val nas = visualItems.count { it.isNaoSeAplica }
                "Conforme ($conforms Aprovados, $nas N/A de ${visualItems.size} itens inspecionados conforme ${currentEvaluation.normReference})"
            }
            else -> {
                "100% Conforme (${visualItems.size}/${visualItems.size} itens inspecionados conforme ${currentEvaluation.normReference})"
            }
        }

        if (isEditMode && editingTest != null) {
            val originalTest = editingTest!!
            val testDate = testDateInput.ifBlank { originalTest.testDate }
            val retestDue = if (isApproved) {
                com.example.util.DateUtils.calculateRetestDueDate(testDate, currentEvaluation.recommendedRetestMonths)
            } else ""
            val finalReportNumber = reportNumberInput.trim().ifBlank { originalTest.reportNumber }

            val updatedTest = originalTest.copy(
                reportNumber = finalReportNumber,
                clientId = selectedClientId,
                clientName = clientObj?.nomeFantasia ?: clientObj?.razaoSocial ?: originalTest.clientName,
                collaboratorName = collaboratorName,
                collaboratorRegistration = collaboratorRegistration,
                collaboratorSector = collaboratorSector,
                equipmentTag = equipmentTag,
                equipmentType = selectedEquipmentType.name,
                equipmentClass = selectedDielectricClass.label,
                equipmentSerial = equipmentSerial,
                equipmentCa = equipmentCa,
                serviceOrderId = selectedOSId,
                serviceOrderNumber = osObj?.osNumber ?: originalTest.serviceOrderNumber,
                artNumber = osObj?.artNumber?.ifEmpty { originalTest.artNumber } ?: originalTest.artNumber,
                technicianName = effTechName,
                technicianCftOrCrea = currentTechCft.trim(),
                techResponsibleName = effRtName,
                techResponsibleCrea = currentRtCrea.ifBlank { originalTest.techResponsibleCrea },
                testDate = testDate,
                testTime = testTimeInput.ifBlank { originalTest.testTime },
                location = location,
                normCode = currentEvaluation.normReference,
                appliedVoltage_kV = if (isInductionTest) inductionPointsCurrent.maxOf { it.voltage_kV } else appliedVoltage_kV,
                voltageType = voltageType,
                applicationDurationSeconds = if (isInductionTest) 0 else durationSeconds,
                measuredLeakageCurrent_mA = if (isInductionTest) 0.0 else measuredLeakage_mA,
                leakageCurrentLimit_mA = currentEvaluation.leakageLimit_mA,
                withstandWithoutPuncture = if (isInductionTest) true else withstandWithoutPuncture,
                gloveLength_mm = if (com.example.domain.NormsEngine.isStickType(selectedEquipmentType)) stickDiameter_mm else gloveLength_mm,
                currentUnit = if (com.example.domain.NormsEngine.isStickType(selectedEquipmentType)) "µA" else "mA",
                temperatureC = temperatureC,
                relativeHumidityPercent = relativeHumidityPercent,
                stickTestPoints = if (com.example.domain.NormsEngine.isStickType(selectedEquipmentType) || selectedEquipmentType == EquipmentType.ESCADA_ISOLADA) stickTestPoints else 1,
                visualInspectionSummary = visualSummary,
                result = currentEvaluation.result.name,
                resultRationale = currentEvaluation.rationale,
                technicalNotes = finalTechnicalNotes,
                retestDueDate = retestDue,
                technicianSignature = technicianSig,
                techResponsibleSignature = rtSig,
                certificateNumber = if (isApproved) {
                    if (originalTest.certificateNumber.isNotBlank()) originalTest.certificateNumber
                    else viewModel.generateNextCertificateNumber()
                } else "",
                photoUris = photoList.joinToString("|||") { "${it.label}::${it.uriString}" },
                inductionPointsJson = if (selectedEquipmentType == EquipmentType.DETECTOR_INDUCAO) com.example.domain.InductionDetector.toJson(inductionPointsCurrent) else "",
                updatedAt = today
            )

            val updatedEquipment = EquipmentEntity(
                id = updatedTest.equipmentId,
                clientId = selectedClientId,
                clientName = updatedTest.clientName,
                type = selectedEquipmentType.name,
                tag = equipmentTag,
                qrCode = equipmentTag,
                manufacturer = equipmentManufacturer,
                serialNumber = equipmentSerial,
                caNumber = equipmentCa,
                dielectricClass = selectedDielectricClass.label,
                collaboratorName = collaboratorName,
                collaboratorRegistration = collaboratorRegistration,
                collaboratorSector = collaboratorSector,
                serviceOrderId = selectedOSId,
                serviceOrderNumber = updatedTest.serviceOrderNumber,
                lastTestDate = updatedTest.testDate,
                nextTestDueDate = retestDue,
                retestIntervalMonths = currentEvaluation.recommendedRetestMonths,
                status = if (isApproved) "EM_USO" else "REPROVADO"
            )

            viewModel.saveEquipment(updatedEquipment)
            viewModel.updateTestRecord(updatedTest)
            rememberLastEnvironment(context, temperatureInput, humidityInput)
            Toast.makeText(context, "Laudo ${updatedTest.reportNumber} atualizado com sucesso!", Toast.LENGTH_SHORT).show()
            viewModel.openLaudo(updatedTest)
            viewModel.navigateTo("tests")
        } else {
            val testNum = viewModel.generateNextTestNumber()
            val repNum = reportNumberInput.trim().ifBlank { viewModel.generateNextReportNumber() }
            val certNum = if (isApproved) viewModel.generateNextCertificateNumber() else ""
            val valCode = viewModel.generateValidationCode()
            val retestDue = if (isApproved) {
                com.example.util.DateUtils.calculateRetestDueDate(today, currentEvaluation.recommendedRetestMonths)
            } else ""
            val timeNow = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            val newTest = TestRecordEntity(
                id = "ens-${System.currentTimeMillis()}",
                uuid = UUID.randomUUID().toString(),
                companyId = currentCompany?.id ?: currentUser?.companyId ?: "comp-jvm-01",
                companyName = currentCompany?.name ?: currentUser?.companyName ?: "JVM Engenharia",
                userId = currentUser?.id ?: "user-jvm-01",
                userName = currentUser?.name ?: "Eng. João Carlos",
                testNumber = testNum,
                reportNumber = repNum,
                certificateNumber = certNum,
                clientId = selectedClientId,
                clientName = clientObj?.nomeFantasia ?: clientObj?.razaoSocial ?: "Cliente Geral",
                collaboratorName = collaboratorName,
                collaboratorRegistration = collaboratorRegistration,
                collaboratorSector = collaboratorSector,
                equipmentId = "eq-${System.currentTimeMillis()}",
                equipmentTag = equipmentTag,
                equipmentType = selectedEquipmentType.name,
                equipmentClass = selectedDielectricClass.label,
                equipmentSerial = equipmentSerial,
                equipmentCa = equipmentCa,
                serviceOrderId = selectedOSId,
                serviceOrderNumber = osObj?.osNumber ?: "OS-AVULSA",
                artNumber = osObj?.artNumber ?: "",
                technicianName = effTechName,
                technicianCftOrCrea = currentTechCft.trim(), // em branco: o laudo não mostra o registro do executor
                techResponsibleName = effRtName,
                techResponsibleCrea = currentRtCrea.ifBlank { settings?.technicalResponsibleCrea ?: "CREA 24481/D-DF" },
                testDate = today,
                testTime = timeNow,
                location = location,
                normCode = currentEvaluation.normReference,
                appliedVoltage_kV = if (isInductionTest) inductionPointsCurrent.maxOf { it.voltage_kV } else appliedVoltage_kV,
                voltageType = voltageType,
                applicationDurationSeconds = if (isInductionTest) 0 else durationSeconds,
                measuredLeakageCurrent_mA = if (isInductionTest) 0.0 else measuredLeakage_mA,
                leakageCurrentLimit_mA = currentEvaluation.leakageLimit_mA,
                withstandWithoutPuncture = if (isInductionTest) true else withstandWithoutPuncture,
                gloveLength_mm = if (com.example.domain.NormsEngine.isStickType(selectedEquipmentType)) stickDiameter_mm else gloveLength_mm,
                currentUnit = if (com.example.domain.NormsEngine.isStickType(selectedEquipmentType)) "µA" else "mA",
                temperatureC = temperatureC,
                relativeHumidityPercent = relativeHumidityPercent,
                stickTestPoints = if (com.example.domain.NormsEngine.isStickType(selectedEquipmentType) || selectedEquipmentType == EquipmentType.ESCADA_ISOLADA) stickTestPoints else 1,
                visualInspectionSummary = visualSummary,
                result = currentEvaluation.result.name,
                resultRationale = currentEvaluation.rationale,
                technicalNotes = finalTechnicalNotes,
                retestDueDate = retestDue,
                technicianSignature = technicianSig,
                techResponsibleSignature = rtSig,
                validationCode = valCode,
                photoUris = photoList.joinToString("|||") { "${it.label}::${it.uriString}" },
                inductionPointsJson = if (selectedEquipmentType == EquipmentType.DETECTOR_INDUCAO) com.example.domain.InductionDetector.toJson(inductionPointsCurrent) else "",
                createdAt = today,
                updatedAt = today
            )

            val newEquipment = EquipmentEntity(
                id = newTest.equipmentId,
                companyId = currentCompany?.id ?: currentUser?.companyId ?: "comp-jvm-01",
                companyName = currentCompany?.name ?: currentUser?.companyName ?: "JVM Engenharia",
                userId = currentUser?.id ?: "user-jvm-01",
                userName = currentUser?.name ?: "Eng. João Carlos",
                clientId = selectedClientId,
                clientName = newTest.clientName,
                type = selectedEquipmentType.name,
                tag = equipmentTag,
                qrCode = equipmentTag,
                manufacturer = equipmentManufacturer,
                serialNumber = equipmentSerial,
                caNumber = equipmentCa,
                dielectricClass = selectedDielectricClass.label,
                collaboratorName = collaboratorName,
                collaboratorRegistration = collaboratorRegistration,
                collaboratorSector = collaboratorSector,
                serviceOrderId = selectedOSId,
                serviceOrderNumber = newTest.serviceOrderNumber,
                lastTestDate = today,
                nextTestDueDate = retestDue,
                retestIntervalMonths = currentEvaluation.recommendedRetestMonths,
                status = if (isApproved) "EM_USO" else "REPROVADO"
            )

            viewModel.saveEquipment(newEquipment)
            viewModel.saveTest(newTest)
            rememberLastEnvironment(context, temperatureInput, humidityInput)
            Toast.makeText(context, "Laudo ${newTest.reportNumber} gerado com sucesso!", Toast.LENGTH_SHORT).show()
            viewModel.openLaudo(newTest)
            viewModel.navigateTo("tests")
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Edit Mode Header Banner
        if (isEditMode && editingTest != null) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    border = BorderStroke(1.5.dp, ElectricBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(ElectricBlue, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Modo de Edição de Laudo", fontWeight = FontWeight.Black, fontSize = 14.sp, color = NavyPrimary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(NavyPrimary, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("EDIÇÃO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                        }
                                    }
                                    Text(
                                        text = "Laudo: ${editingTest!!.reportNumber} • OS: ${editingTest!!.serviceOrderNumber} • Tag: ${editingTest!!.equipmentTag}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF1E3A8A),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.cancelEditTest() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Cancelar", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { coroutineScope.launch { saveTestAction() } },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isApproved) StatusGreen else SafetyOrange),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Salvar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Text(
                            text = "Todos os campos estão liberados para edição. A automação das normas técnicas, cálculo de corrente de fuga limite e aprovação/reprovação é recalculada em tempo real.",
                            fontSize = 11.sp,
                            color = Color(0xFF1E293B),
                            lineHeight = 15.sp
                        )

                        HorizontalDivider(color = ElectricBlue.copy(alpha = 0.3f))

                        OutlinedTextField(
                            value = reportNumberInput,
                            onValueChange = { reportNumberInput = it },
                            label = { Text("Número do Laudo (Editável) *", fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            singleLine = true,
                            colors = appTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().testTag("wizard_edit_report_number_banner_field")
                        )
                    }
                }
            }
        }

        // Step Tracker Header
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(if (isEditMode) ElectricBlue else SafetyOrange, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = if (isEditMode) Icons.Default.Edit else Icons.Default.Science, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(if (isEditMode) "Edição de Laudo Técnico" else "Assistente de Ensaio Dielétrico", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                                Text(if (isEditMode) "Navegue pelas etapas ou salve diretamente" else "Fluxo guiado de inspeção, medição e emissão de laudo", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }

                        Text(
                            text = "Etapa $currentStep de 6",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.Black
                        )
                    }

                    // Progress / Navigation Pills with clickable shortcuts
                    val stepLabels = listOf("Equipamento", "Ambiente", "Inspeção", "Medição", "Padrões", "Conclusão")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (step in 1..6) {
                            val isCurrent = step == currentStep
                            val isPast = step < currentStep
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .background(
                                        color = when {
                                            isCurrent -> NavyPrimary
                                            isPast -> ElectricBlue.copy(alpha = 0.85f)
                                            else -> Color(0xFFE2E8F0)
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { currentStep = step }
                                    .padding(horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$step. ${stepLabels[step - 1]}",
                                    fontSize = 8.5.sp,
                                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Medium,
                                    color = if (isCurrent || isPast) Color.White else Color(0xFF64748B),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // STEP 1: Identification & Equipment
        if (currentStep == 1) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "1. Vinculação da Ordem de Serviço (OS) e Equipamento",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        OutlinedTextField(
                            value = reportNumberInput,
                            onValueChange = { reportNumberInput = it },
                            label = {
                                Text(
                                    text = if (isEditMode) "Número do Laudo (Editável) *" else "Número do Laudo *",
                                    color = Color.Black,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            singleLine = true,
                            colors = appTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().testTag("wizard_report_number_input")
                        )

                        // 1. Selector de Ordem de Serviço (OS) como Entidade Principal
                        var osExpanded by remember { mutableStateOf(false) }
                        var showNewOSDialog by remember { mutableStateOf(false) }
                        val currentOS = serviceOrders.find { it.id == selectedOSId }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExposedDropdownMenuBox(
                                expanded = osExpanded,
                                onExpandedChange = { osExpanded = !osExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = if (currentOS != null) {
                                        "${currentOS.osNumber} — ${currentOS.clientName}"
                                    } else if (serviceOrders.isNotEmpty()) {
                                        "Selecione a Ordem de Serviço..."
                                    } else {
                                        "Nenhuma OS cadastrada (Clique em + Nova OS)"
                                    },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Ordem de Serviço (OS Vinculada) *", color = Color.Black, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Assignment,
                                            contentDescription = null,
                                            tint = NavyPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = osExpanded) },
                                    colors = appTextFieldColors(),
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = osExpanded,
                                    onDismissRequest = { osExpanded = false }
                                ) {
                                    if (serviceOrders.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Nenhuma OS disponível. Clique em '+ Nova OS'", color = Color(0xFF64748B)) },
                                            onClick = {
                                                osExpanded = false
                                                showNewOSDialog = true
                                            }
                                        )
                                    } else {
                                        serviceOrders.forEach { osItem ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = osItem.osNumber,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 13.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                color = NavyPrimary
                                                            )
                                                            StatusBadge(status = osItem.status)
                                                        }
                                                        Text(
                                                            text = osItem.clientName,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFF0F172A)
                                                        )
                                                        if (osItem.artNumber.isNotBlank()) {
                                                            Text(
                                                                text = "ART: ${osItem.artNumber} • Aberta em: ${com.example.util.DateUtils.formatDate(osItem.openDate)}",
                                                                fontSize = 10.sp,
                                                                color = Color(0xFF64748B)
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    selectedOSId = osItem.id
                                                    selectedClientId = osItem.clientId
                                                    osExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { showNewOSDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(56.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Nova OS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (showNewOSDialog) {
                            AddServiceOrderDialog(
                                clients = clients,
                                onDismiss = { showNewOSDialog = false },
                                onSave = { newOs ->
                                    viewModel.saveServiceOrder(newOs)
                                    selectedOSId = newOs.id
                                    selectedClientId = newOs.clientId
                                    showNewOSDialog = false
                                },
                                onSaveClient = { newClient ->
                                    viewModel.saveClient(newClient)
                                },
                                onGenerateOSNumber = { viewModel.generateNextOSNumber() }
                            )
                        }

                        if (showEditOSDialog && osToEditInWizard != null) {
                            ServiceOrderFormDialog(
                                order = osToEditInWizard,
                                clients = clients,
                                onDismiss = {
                                    showEditOSDialog = false
                                    osToEditInWizard = null
                                },
                                onSave = { updatedOs ->
                                    viewModel.saveServiceOrder(updatedOs)
                                    if (selectedOSId == updatedOs.id) {
                                        selectedClientId = updatedOs.clientId
                                    }
                                    showEditOSDialog = false
                                    osToEditInWizard = null
                                    Toast.makeText(context, "Ordem de Serviço ${updatedOs.osNumber} atualizada!", Toast.LENGTH_SHORT).show()
                                },
                                onSaveClient = { newClient -> viewModel.saveClient(newClient) },
                                onGenerateOSNumber = { viewModel.generateNextOSNumber() }
                            )
                        }

                        val targetToDelete = osToEditInWizard
                        if (showDeleteOSConfirm && targetToDelete != null) {
                            val osTestsCount = tests.count { it.serviceOrderId == targetToDelete.id || it.serviceOrderNumber == targetToDelete.osNumber }
                            AlertDialog(
                                onDismissRequest = {
                                    showDeleteOSConfirm = false
                                    osToEditInWizard = null
                                },
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Excluir Ordem de Serviço", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Tem certeza que deseja excluir a Ordem de Serviço ${targetToDelete.osNumber}?")
                                        Text("Cliente: ${targetToDelete.clientName}", fontSize = 12.sp, color = Color(0xFF475569))
                                        if (targetToDelete.artNumber.isNotBlank()) {
                                            Text("ART: ${targetToDelete.artNumber}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF64748B))
                                        }
                                        if (osTestsCount > 0) {
                                            Text(
                                                "Atenção: Esta OS possui $osTestsCount laudo(s) associado(s).",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFDC2626)
                                            )
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.deleteServiceOrder(targetToDelete.id)
                                            if (selectedOSId == targetToDelete.id) {
                                                selectedOSId = ""
                                            }
                                            showDeleteOSConfirm = false
                                            osToEditInWizard = null
                                            Toast.makeText(context, "Ordem de Serviço ${targetToDelete.osNumber} excluída!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                                    ) {
                                        Text("Excluir", fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    OutlinedButton(onClick = {
                                        showDeleteOSConfirm = false
                                        osToEditInWizard = null
                                    }) {
                                        Text("Cancelar")
                                    }
                                }
                            )
                        }

                        // Detalhes da OS e Cliente Carregados Automaticamente
                        if (currentOS != null) {
                            val clientInfo = clients.find { it.id == currentOS.clientId || it.id == selectedClientId }
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Business,
                                                contentDescription = null,
                                                tint = ElectricBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Cliente Tomador Vinculado pela OS:",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF334155)
                                            )
                                        }
                                        StatusBadge(status = currentOS.status)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = currentOS.clientName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Black,
                                                color = NavyPrimary
                                            )
                                            if (clientInfo != null && clientInfo.cnpj.isNotBlank()) {
                                                Text(
                                                    text = "CNPJ: ${clientInfo.cnpj} • ${clientInfo.city}/${clientInfo.state}",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF475569)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (currentOS.artNumber.isNotBlank()) {
                                            Text(
                                                text = "ART: ${currentOS.artNumber}",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFD97706)
                                            )
                                        }
                                        if (currentOS.technicianName.isNotBlank()) {
                                            Text(
                                                text = "Resp: ${currentOS.technicianName}",
                                                fontSize = 10.sp,
                                                color = Color(0xFF475569)
                                            )
                                        }
                                    }

                                    if (currentOS.description.isNotBlank()) {
                                        Text(
                                            text = "Escopo: ${currentOS.description}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    HorizontalDivider(color = Color(0xFFCBD5E1))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                osToEditInWizard = currentOS
                                                showEditOSDialog = true
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(32.dp).testTag("wizard_edit_os_btn"),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyPrimary),
                                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Editar OS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        OutlinedButton(
                                            onClick = {
                                                osToEditInWizard = currentOS
                                                showDeleteOSConfirm = true
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(32.dp).testTag("wizard_delete_os_btn"),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Excluir OS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Preenchimento Rápido se houver equipamentos cadastrados para o cliente selecionado
                        val clientEquipments = equipmentList.filter { it.clientId == selectedClientId }
                        if (clientEquipments.isNotEmpty()) {
                            var eqExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = eqExpanded,
                                onExpandedChange = { eqExpanded = !eqExpanded }
                            ) {
                                OutlinedTextField(
                                    value = "Carregar dados de equipamento existente (${clientEquipments.size} disponíveis)",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Preenchimento Rápido por Equipamento Pré-cadastrado", color = ElectricBlue, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Build, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = eqExpanded) },
                                    colors = appTextFieldColors(),
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = eqExpanded,
                                    onDismissRequest = { eqExpanded = false }
                                ) {
                                    clientEquipments.forEach { eq ->
                                        DropdownMenuItem(
                                            text = {
                                                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                                    Text("${eq.tag} — ${eq.type} (${eq.dielectricClass})", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 12.sp)
                                                    Text("Série: ${eq.serialNumber.ifEmpty { "S/N" }} • CA: ${eq.caNumber.ifEmpty { "S/CA" }} • Usuário: ${eq.collaboratorName}", fontSize = 11.sp, color = Color(0xFF64748B))
                                                }
                                            },
                                            onClick = {
                                                selectedEquipmentType = EquipmentType.fromString(eq.type)
                                                selectedDielectricClass = DielectricClass.fromString(eq.dielectricClass)
                                                equipmentTag = eq.tag
                                                equipmentSerial = eq.serialNumber
                                                equipmentCa = eq.caNumber
                                                equipmentManufacturer = eq.manufacturer
                                                collaboratorName = eq.collaboratorName
                                                collaboratorRegistration = eq.collaboratorRegistration
                                                collaboratorSector = eq.collaboratorSector
                                                gloveLength_mm = eq.gloveLength_mm
                                                eqExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Equipment Type Selector
                        var typeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = typeExpanded,
                            onExpandedChange = { typeExpanded = !typeExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedEquipmentType.label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Tipo de EPI / EPC para Ensaio", color = Color.Black) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                colors = appTextFieldColors(),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = typeExpanded,
                                onDismissRequest = { typeExpanded = false }
                            ) {
                                EquipmentType.selectable.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text("${t.label} (${t.norm})", color = Color.Black) },
                                        onClick = {
                                            selectedEquipmentType = t
                                            val clientObj = clients.find { it.id == selectedClientId }
                                            val cName = clientObj?.nomeFantasia ?: clientObj?.razaoSocial
                                            equipmentTag = t.suggestTag(clientName = cName, existingTags = equipmentList.map { it.tag })
                                            typeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Dielectric Class Selector
                        var classExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = classExpanded,
                            onExpandedChange = { classExpanded = !classExpanded }
                        ) {
                            OutlinedTextField(
                                value = "Classe ${selectedDielectricClass.label} (Ensaio: ${selectedDielectricClass.testVoltageAC} kV CA)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Classe Dielétrica de Tensão", color = Color.Black) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classExpanded) },
                                colors = appTextFieldColors(),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = classExpanded,
                                onDismissRequest = { classExpanded = false }
                            ) {
                                DielectricClass.entries.forEach { cl ->
                                    DropdownMenuItem(
                                        text = { Text("Classe ${cl.label} (Uso máx: ${cl.maxUseVoltageAC} kV | Ensaio: ${cl.testVoltageAC} kV)", color = Color.Black) },
                                        onClick = {
                                            selectedDielectricClass = cl
                                            classExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Lista de Ferramentas Manuais para Ensaio (quando selecionado FERRAMENTA_ISOLADA)
                        if (selectedEquipmentType == EquipmentType.FERRAMENTA_ISOLADA) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Build,
                                                contentDescription = null,
                                                tint = NavyPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = "Composição do Conjunto de Ferramentas Manuais",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = NavyPrimary
                                                )
                                                Text(
                                                    text = "ABNT NBR 9699 / IEC 60900 (Limite: 1.0 mA por ferramenta)",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF475569)
                                                )
                                            }
                                        }
                                    }

                                    // KPI Resumo do Conjunto
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White, RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Total de Itens", fontSize = 9.sp, color = Color(0xFF64748B))
                                            Text("$totalManualToolsCount un.", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                                        }
                                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFFE2E8F0)))
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Limite Corrente Fuga", fontSize = 9.sp, color = Color(0xFF64748B))
                                            Text("${String.format(Locale.US, "%.1f", totalManualToolsCount * 1.0)} mA", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SafetyOrange)
                                        }
                                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFFE2E8F0)))
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Aprovação Individual", fontSize = 9.sp, color = Color(0xFF64748B))
                                            Text(
                                                if (rejectedManualToolsCount == 0) "100% Aprovado" else "$rejectedManualToolsCount Reprovada(s)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (rejectedManualToolsCount == 0) StatusGreen else StatusRed
                                            )
                                        }
                                    }

                                    // Quick action buttons
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
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                            modifier = Modifier.weight(1f).heightIn(min = 36.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Aprovar Todas", fontSize = 10.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                manualToolsList.forEachIndexed { idx, tool ->
                                                    manualToolsList[idx] = tool.copy(isSelected = idx < 3)
                                                }
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                            modifier = Modifier.weight(1f).heightIn(min = 36.dp)
                                        ) {
                                            Text("Kit Básico (3)", fontSize = 10.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 12.sp, color = Color.Black)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                manualToolsList.forEachIndexed { idx, tool ->
                                                    manualToolsList[idx] = tool.copy(isSelected = true)
                                                }
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                            modifier = Modifier.weight(1f).heightIn(min = 36.dp)
                                        ) {
                                            Text("Selecionar Todas", fontSize = 10.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 12.sp, color = Color.Black)
                                        }
                                    }

                                    // Lista detalhada com quantidade e status individual
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        manualToolsList.forEachIndexed { index, toolItem ->
                                            Card(
                                                shape = RoundedCornerShape(8.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (toolItem.isSelected) Color.White else Color(0xFFF8FAFC)
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (toolItem.isSelected) {
                                                        if (toolItem.isApproved) Color(0xFFCBD5E1) else StatusRed
                                                    } else Color(0xFFE2E8F0)
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    // Header do item (Checkbox + Nome + Limite)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.weight(1f).clickable {
                                                                manualToolsList[index] = toolItem.copy(isSelected = !toolItem.isSelected)
                                                            }
                                                        ) {
                                                            Checkbox(
                                                                checked = toolItem.isSelected,
                                                                onCheckedChange = { checked ->
                                                                    manualToolsList[index] = toolItem.copy(isSelected = checked)
                                                                },
                                                                colors = CheckboxDefaults.colors(
                                                                    checkedColor = NavyPrimary,
                                                                    checkmarkColor = Color.White
                                                                ),
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = toolItem.name,
                                                                fontSize = 11.sp,
                                                                fontWeight = if (toolItem.isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (toolItem.isSelected) Color.Black else Color(0xFF64748B)
                                                            )
                                                        }

                                                        if (toolItem.isSelected) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(Color(0xFFEFF6FF), RoundedCornerShape(4.dp))
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "+${String.format(Locale.US, "%.1f", toolItem.quantity * 1.0)} mA",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = NavyPrimary
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // Controles de Quantidade e Aprovação Individual quando o item está selecionado
                                                    if (toolItem.isSelected) {
                                                        // quantidade em uma linha e parecer na linha de baixo: em celular
                                                        // (ou com fonte grande) os botões não ficam cortados
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(Color(0xFFF8FAFC), RoundedCornerShape(6.dp))
                                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            // Stepper de Quantidade
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(
                                                                    text = "Qtd:",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = Color(0xFF475569)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                IconButton(
                                                                    onClick = {
                                                                        if (toolItem.quantity > 1) {
                                                                            manualToolsList[index] = toolItem.copy(quantity = toolItem.quantity - 1)
                                                                        }
                                                                    },
                                                                    enabled = toolItem.quantity > 1,
                                                                    modifier = Modifier.size(26.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Remove, contentDescription = "Diminuir", modifier = Modifier.size(14.dp))
                                                                }

                                                                Box(
                                                                    modifier = Modifier
                                                                        .background(Color.White, RoundedCornerShape(4.dp))
                                                                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                                                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = "${toolItem.quantity} un.",
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = Color.Black
                                                                    )
                                                                }

                                                                IconButton(
                                                                    onClick = {
                                                                        manualToolsList[index] = toolItem.copy(quantity = toolItem.quantity + 1)
                                                                    },
                                                                    modifier = Modifier.size(26.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Add, contentDescription = "Aumentar", modifier = Modifier.size(14.dp))
                                                                }
                                                            }

                                                            // Parecer Individual (Aprovado / Reprovado)
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Button(
                                                                    onClick = {
                                                                        manualToolsList[index] = toolItem.copy(isApproved = true, rejectionReason = "")
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(
                                                                        containerColor = if (toolItem.isApproved) StatusGreen else Color(0xFFE2E8F0)
                                                                    ),
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                                    modifier = Modifier.weight(1f).heightIn(min = 36.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Aprovado",
                                                                        maxLines = 1,
                                                                        fontSize = 11.sp,
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
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                                    modifier = Modifier.weight(1f).heightIn(min = 36.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Reprovado",
                                                                        maxLines = 1,
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (!toolItem.isApproved) Color.White else Color.Black
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        // Se Reprovado, exibir campo de motivo e chips de defeitos comuns
                                                        if (!toolItem.isApproved) {
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .background(Color(0xFFFEF2F2), RoundedCornerShape(6.dp))
                                                                    .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(6.dp))
                                                                    .padding(6.dp),
                                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = "Motivo da Reprovação Individual:",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = StatusRed
                                                                )
                                                                // motivos rápidos: rolam para o lado em tela estreita (não cortam)
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                ) {
                                                                    listOf("Isolação Danificada", "Trinca/Fissura", "Perfuração", "Sem 1000V").forEach { quickReason ->
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .background(Color.White, RoundedCornerShape(4.dp))
                                                                                .border(0.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                                                                                .clickable {
                                                                                    manualToolsList[index] = toolItem.copy(rejectionReason = quickReason)
                                                                                }
                                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                                        ) {
                                                                            Text(quickReason, fontSize = 8.sp, color = Color(0xFF991B1B))
                                                                        }
                                                                    }
                                                                }
                                                                OutlinedTextField(
                                                                    value = toolItem.rejectionReason,
                                                                    onValueChange = { newReason ->
                                                                        manualToolsList[index] = toolItem.copy(rejectionReason = newReason)
                                                                    },
                                                                    placeholder = { Text("Descreva a não-conformidade...", fontSize = 11.sp) },
                                                                    colors = appTextFieldColors(),
                                                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Adicionar ferramenta customizada com quantidade: nome em uma linha
                                    // inteira e, embaixo, quantidade + botão (em celular nada fica cortado)
                                    OutlinedTextField(
                                        value = customToolNameInput,
                                        onValueChange = { customToolNameInput = it },
                                        label = { Text("Outra ferramenta (nome)", fontSize = 11.sp) },
                                        placeholder = { Text("Ex.: Chave Torx Isolada", fontSize = 11.sp) },
                                        singleLine = true,
                                        colors = appTextFieldColors(),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Quantidade da nova ferramenta
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .heightIn(min = 44.dp)
                                                .background(Color.White, RoundedCornerShape(8.dp))
                                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 4.dp)
                                        ) {
                                            IconButton(
                                                onClick = { if (customToolQuantityInput > 1) customToolQuantityInput-- },
                                                enabled = customToolQuantityInput > 1,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Diminuir", modifier = Modifier.size(16.dp))
                                            }
                                            Text("${customToolQuantityInput} un.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1)
                                            IconButton(
                                                onClick = { customToolQuantityInput++ },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Aumentar", modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                if (customToolNameInput.isNotBlank()) {
                                                    val trimmed = customToolNameInput.trim()
                                                    val existingIdx = manualToolsList.indexOfFirst { it.name.equals(trimmed, ignoreCase = true) }
                                                    if (existingIdx >= 0) {
                                                        manualToolsList[existingIdx] = manualToolsList[existingIdx].copy(
                                                            quantity = manualToolsList[existingIdx].quantity + customToolQuantityInput,
                                                            isSelected = true
                                                        )
                                                    } else {
                                                        manualToolsList.add(
                                                            ManualToolItem(
                                                                name = trimmed,
                                                                quantity = customToolQuantityInput,
                                                                isApproved = true,
                                                                isSelected = true
                                                            )
                                                        )
                                                    }
                                                    customToolNameInput = ""
                                                    customToolQuantityInput = 1
                                                }
                                            },
                                            enabled = customToolNameInput.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Adicionar", fontSize = 12.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }

                        // Parâmetros Normativos de Luva Isolante (ABNT NBR 16295 / IEC 60903)
                        if (selectedEquipmentType == EquipmentType.LUVA_ISOLANTE) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF93C5FD)),
                                modifier = Modifier.fillMaxWidth().testTag("glove_norm_table_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Shield,
                                                contentDescription = null,
                                                tint = NavyPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = "Critérios de Ensaio - Luvas Isolantes",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = NavyPrimary
                                                )
                                                Text(
                                                    text = "ABNT NBR 16295 Tabela 4 / IEC 60903",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF475569)
                                                )
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFDBEAFE), RoundedCornerShape(6.dp))
                                                .border(1.dp, Color(0xFF93C5FD), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "2 Luvas Simultâneas (2x)",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1D4ED8)
                                            )
                                        }
                                    }

                                    // Banner explicativo da regra do ensaio com 2 luvas simultâneas
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White, RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = Color(0xFF2563EB),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "O valor de referência da Tabela 4 é para uma unidade (1 luva). Como o ensaio em cuba é realizado em duas luvas simultâneas, o limite de corrente de fuga é o dobro (2x) do valor da tabela.",
                                                fontSize = 10.sp,
                                                color = Color(0xFF1E3A8A),
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Selecione o Comprimento da Luva para definir os limites:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF334155)
                                    )

                                    // Header das colunas
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(NavyPrimary, RoundedCornerShape(6.dp))
                                            .padding(vertical = 4.dp, horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Comprimento", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                                        Text("Tab. 4 (1 Luva)", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                                        Text("Ensaio (2 Luvas)", color = Color(0xFFFDE047), fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1.2f))
                                    }

                                    val cleanClassGlove = selectedDielectricClass.label
                                    listOf(280, 360, 410, 460).forEach { len ->
                                        val isSelected = gloveLength_mm == len
                                        val unitLimit = NormsEngine.getGloveUnitaryLeakageLimit(cleanClassGlove, len)
                                        val pairLimit = unitLimit * 2.0
                                        val unitStr = if (unitLimit == 0.0) "N/a" else if (unitLimit % 1.0 == 0.0) unitLimit.toInt().toString() else unitLimit.toString()
                                        val pairStr = if (pairLimit == 0.0) "N/a" else if (pairLimit % 1.0 == 0.0) pairLimit.toInt().toString() else pairLimit.toString()

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    gloveLength_mm = len
                                                    val std = NormsEngine.getStandardCriteria(
                                                        equipmentType = selectedEquipmentType,
                                                        dielectricClass = selectedDielectricClass.label,
                                                        gloveLength_mm = len
                                                    )
                                                    customLeakageLimitInput = String.format(Locale.US, "%.2f", std.leakageLimit_mA)
                                                }
                                                .background(
                                                    if (isSelected) Color(0xFFDBEAFE) else Color.Transparent,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .border(
                                                    if (isSelected) 1.5.dp else 0.5.dp,
                                                    if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(vertical = 6.dp, horizontal = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1.2f)
                                            ) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = {
                                                        gloveLength_mm = len
                                                        val std = NormsEngine.getStandardCriteria(
                                                            equipmentType = selectedEquipmentType,
                                                            dielectricClass = selectedDielectricClass.label,
                                                            gloveLength_mm = len
                                                        )
                                                        customLeakageLimitInput = String.format(Locale.US, "%.2f", std.leakageLimit_mA)
                                                    },
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "$len mm ${if (len == 360) "(Padrão)" else ""}",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color.Black else Color(0xFF1F2937)
                                                )
                                            }
                                            Text(
                                                text = if (unitLimit == 0.0) "N/a" else "$unitStr mA",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF64748B),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = if (pairLimit == 0.0) "N/a" else "$pairStr mA",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF0F172A),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                modifier = Modifier.weight(1.2f)
                                            )
                                        }
                                    }

                                    // Resumo da Seleção Atual
                                    val currentUnit = NormsEngine.getGloveUnitaryLeakageLimit(cleanClassGlove, gloveLength_mm)
                                    val currentPair = currentUnit * 2.0
                                    val curUnitStr = if (currentUnit == 0.0) "N/a" else if (currentUnit % 1.0 == 0.0) currentUnit.toInt().toString() else currentUnit.toString()
                                    val curPairStr = if (currentPair == 0.0) "N/a" else if (currentPair % 1.0 == 0.0) currentPair.toInt().toString() else currentPair.toString()

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White, RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Comprimento:", fontSize = 9.sp, color = Color(0xFF64748B))
                                            Text("$gloveLength_mm mm", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Tensão de Prova:", fontSize = 9.sp, color = Color(0xFF64748B))
                                            Text("${selectedDielectricClass.testVoltageAC} kV CA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Limite Ensaio (2 Luvas):", fontSize = 9.sp, color = Color(0xFF64748B))
                                            Text(if (currentUnit == 0.0) "N/a — comprimento não aplicável à classe (Tabela 4)" else "2x $curUnitStr = $curPairStr mA", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                        }
                                    }
                                }
                            }
                        }

                        // Tabela Normativa de Bastão de Manobra / Vara (ABNT NBR 11854)
                        if (com.example.domain.NormsEngine.isStickType(selectedEquipmentType)) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                                modifier = Modifier.fillMaxWidth().testTag("stick_norm_table_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = null,
                                                tint = Color(0xFF15803D),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = "Tabela de Corrente de Fuga Máxima (ABNT NBR 11854)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF166534)
                                                )
                                                Text(
                                                    text = "Ensaio a 50 kV CA por 60s — limite pelo diâmetro do bastão",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF15803D)
                                                )
                                            }
                                        }
                                    }

                                    // Tabela Visual dos Critérios Normativos
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White, RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Cabeçalho da Tabela
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFDCFCE7), RoundedCornerShape(6.dp))
                                                .padding(vertical = 6.dp, horizontal = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Diâmetro do Bastão",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF14532D),
                                                modifier = Modifier.weight(1.3f)
                                            )
                                            Text(
                                                "Limite 50 kV (Alvo)",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF14532D),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier.weight(1.2f)
                                            )
                                            Text(
                                                "Limite 100 kV",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF4B5563),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        // Linhas da Tabela
                                        NormsEngine.tabelaFugaBastoes.forEach { item ->
                                            val isSelected = stickDiameter_mm == item.diameter_mm
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        stickDiameter_mm = item.diameter_mm
                                                        val std = NormsEngine.getStandardCriteria(
                                                            equipmentType = selectedEquipmentType,
                                                            dielectricClass = selectedDielectricClass.label,
                                                            stickDiameter_mm = item.diameter_mm,
                                                            stickTestPoints = stickTestPoints
                                                        )
                                                        customLeakageLimitInput = std.leakageLimit_mA.toInt().toString()
                                                    }
                                                    .background(
                                                        if (isSelected) Color(0xFFFEF3C7) else Color.Transparent,
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .border(
                                                        if (isSelected) 1.5.dp else 0.5.dp,
                                                        if (isSelected) SafetyOrange else Color(0xFFE5E7EB),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1.3f)
                                                ) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = {
                                                            stickDiameter_mm = item.diameter_mm
                                                            val std = NormsEngine.getStandardCriteria(
                                                                equipmentType = selectedEquipmentType,
                                                                dielectricClass = selectedDielectricClass.label,
                                                                stickDiameter_mm = item.diameter_mm,
                                                                stickTestPoints = stickTestPoints
                                                            )
                                                            customLeakageLimitInput = std.leakageLimit_mA.toInt().toString()
                                                        },
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Ø ${item.diameter_mm} mm ${if (item.diameter_mm == 38) "(Padrão)" else ""}",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) Color.Black else Color(0xFF1F2937)
                                                    )
                                                }
                                                Text(
                                                    text = "${item.limit_50kV_uA} µA",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) SafetyOrange else Color(0xFF166534),
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    modifier = Modifier.weight(1.2f)
                                                )
                                                Text(
                                                    text = "${item.limit_100kV_uA} µA",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF6B7280),
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }

                                    // Resumo de Seleção Atual
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White, RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Diâmetro Selecionado:", fontSize = 9.sp, color = Color(0xFF4B5563))
                                            Text("Ø $stickDiameter_mm mm", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Tensão de Ensaio:", fontSize = 9.sp, color = Color(0xFF4B5563))
                                            Text("50 kV CA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Limite de Fuga:", fontSize = 9.sp, color = Color(0xFF4B5563))
                                            val limitUa = NormsEngine.getStickMaxLeakage_uA(stickDiameter_mm, 50.0) * stickTestPoints
                                            Text("$limitUa µA (${String.format(Locale.US, "%.3f", limitUa / 1000.0)} mA)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SafetyOrange)
                                            if (stickTestPoints > 1) {
                                                Text("$stickTestPoints pontos × ${NormsEngine.getStickMaxLeakage_uA(stickDiameter_mm, 50.0)} µA", fontSize = 9.sp, color = Color(0xFF4B5563))
                                            }
                                        }
                                    }

                                    // Quantidade de pontos ensaiados: limite de fuga = pontos × limite da tabela
                                    OutlinedTextField(
                                        value = stickPointsInput,
                                        onValueChange = { input -> stickPointsInput = input.filter { it.isDigit() }.take(3) },
                                        label = { Text("Quantidade de pontos ensaiados *", color = Color.Black) },
                                        placeholder = { Text("Ex: 3", color = Color.Gray) },
                                        isError = stickPointsInput.isBlank(),
                                        supportingText = {
                                            val perPoint = NormsEngine.getStickMaxLeakage_uA(stickDiameter_mm, 50.0)
                                            Text(
                                                if (stickPointsInput.isBlank()) "Informe quantos pontos do bastão foram ensaiados (vazio conta como 1)."
                                                else "Limite de fuga: $stickTestPoints × $perPoint µA = ${stickTestPoints * perPoint} µA",
                                                color = if (stickPointsInput.isBlank()) StatusRed else Color(0xFF15803D),
                                                fontSize = 11.sp
                                            )
                                        },
                                        singleLine = true,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                        colors = appTextFieldColors(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = equipmentTag,
                                onValueChange = { equipmentTag = it },
                                label = { Text("Tag / Patrimônio *", color = Color.Black) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            val clientObj = clients.find { it.id == selectedClientId }
                                            val cName = clientObj?.nomeFantasia ?: clientObj?.razaoSocial
                                            equipmentTag = selectedEquipmentType.suggestTag(clientName = cName, existingTags = equipmentList.map { it.tag })
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Sugerir TAG",
                                            tint = NavyPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                supportingText = {
                                    Text(
                                        "Padrão: ${selectedEquipmentType.tagPrefix}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF475569)
                                    )
                                },
                                colors = appTextFieldColors(),
                                modifier = Modifier.weight(1f).testTag("wizard_tag_input")
                            )
                            OutlinedTextField(
                                value = equipmentCa,
                                onValueChange = { equipmentCa = it },
                                label = { Text("Nº do CA (MTE)", color = Color.Black) },
                                colors = appTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = equipmentManufacturer,
                                onValueChange = { equipmentManufacturer = it },
                                label = { Text("Fabricante", color = Color.Black) },
                                colors = appTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = equipmentSerial,
                                onValueChange = { equipmentSerial = it },
                                label = { Text("Nº de Série", color = Color.Black) },
                                colors = appTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = collaboratorName,
                            onValueChange = { collaboratorName = it },
                            label = { Text("Nome do Colaborador / Usuário do EPI", color = Color.Black) },
                            colors = appTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = collaboratorRegistration,
                                onValueChange = { collaboratorRegistration = it },
                                label = { Text("Matrícula Funcional", color = Color.Black) },
                                colors = appTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = collaboratorSector,
                                onValueChange = { collaboratorSector = it },
                                label = { Text("Setor / Lotação", color = Color.Black) },
                                colors = appTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Next / Save Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isEditMode) {
                                OutlinedButton(
                                    onClick = { coroutineScope.launch { saveTestAction() } },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusGreen),
                                    border = BorderStroke(1.dp, StatusGreen),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Salvar Alterações", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Button(
                                onClick = { currentStep = 2 },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Avançar para Ambiente", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // STEP 2: Environmental
        if (currentStep == 2) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "2. Condições Ambientais e Local de Ensaio",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = testDateInput,
                                onValueChange = { testDateInput = it },
                                label = { Text("Data do Ensaio (dd/MM/yyyy)", color = Color.Black) },
                                colors = appTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = testTimeInput,
                                onValueChange = { testTimeInput = it },
                                label = { Text("Hora do Ensaio (HH:mm)", color = Color.Black) },
                                colors = appTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = temperatureInput,
                                onValueChange = { input -> temperatureInput = input.filter { it.isDigit() || it == '.' || it == ',' || it == '-' } },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                label = { Text("Temperatura (°C)", color = Color.Black) },
                                colors = appTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = humidityInput,
                                onValueChange = { input -> humidityInput = input.filter { it.isDigit() || it == '.' || it == ',' } },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                label = { Text("Umidade Relativa (% UR)", color = Color.Black) },
                                colors = appTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Medir temperatura e umidade pelo medidor UNI-T UT333BT (Bluetooth)
                        Spacer(Modifier.height(8.dp))
                        com.example.ui.components.UnitEnvMeasureButton { t, h ->
                            temperatureInput = formatNumberInput(t)
                            humidityInput = formatNumberInput(h)
                        }

                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("Local de Realização do Ensaio", color = Color.Black) },
                            colors = appTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { currentStep = 1 },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Voltar", fontSize = 12.sp)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isEditMode) {
                                    OutlinedButton(
                                        onClick = { coroutineScope.launch { saveTestAction() } },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusGreen),
                                        border = BorderStroke(1.dp, StatusGreen),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Salvar Alterações", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Button(
                                    onClick = { currentStep = 3 },
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Avançar para Inspeção", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // STEP 3: Visual Checklist
        if (currentStep == 3) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Header info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "3. Inspeção Visual e Físico-Mecânica Preliminar",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Norma: ${currentEvaluation.normReference}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ElectricBlue
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = selectedEquipmentType.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                            }
                        }

                        // Requirement explanation banner
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = SafetyOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Critérios normativos obrigatórios conforme NR-10 itens 10.4.3.1 e 10.7.8. A reprovação em qualquer item impede a liberação operacional.",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF334155),
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        // Quick actions bar (Marcar Todos Conformes / N/A / Não Conformes)
                        val conformsCount = visualItems.count { it.isConforme }
                        val nasCount = visualItems.count { it.isNaoSeAplica }
                        val nonConfsCount = visualItems.count { it.isNaoConforme }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Itens Inspecionados ($conformsCount OK • $nasCount N/A • $nonConfsCount NC):",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        for (i in visualItems.indices) {
                                            visualItems[i] = visualItems[i].copy(status = ChecklistStatus.CONFORME)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (conformsCount == visualItems.size) Color(0xFFECFDF5) else Color.White,
                                        contentColor = StatusGreen
                                    ),
                                    border = BorderStroke(1.dp, if (conformsCount == visualItems.size) StatusGreen else Color(0xFFA7F3D0))
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Todos OK", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        for (i in visualItems.indices) {
                                            visualItems[i] = visualItems[i].copy(status = ChecklistStatus.NAO_SE_APLICA)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (nasCount == visualItems.size) Color(0xFFF1F5F9) else Color.White,
                                        contentColor = Color(0xFF475569)
                                    ),
                                    border = BorderStroke(1.dp, if (nasCount == visualItems.size) Color(0xFF475569) else Color(0xFFCBD5E1))
                                ) {
                                    Text("Todos N/A", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        for (i in visualItems.indices) {
                                            visualItems[i] = visualItems[i].copy(status = ChecklistStatus.NAO_CONFORME)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (nonConfsCount == visualItems.size) Color(0xFFFEF2F2) else Color.White,
                                        contentColor = StatusRed
                                    ),
                                    border = BorderStroke(1.dp, if (nonConfsCount == visualItems.size) StatusRed else Color(0xFFFECACA))
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Todos NC", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Individual Checklist Items
                        visualItems.forEachIndexed { index, entry ->
                            val isOk = entry.isConforme
                            val isNa = entry.isNaoSeAplica
                            val isNc = entry.isNaoConforme

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isOk -> Color.White
                                        isNa -> Color(0xFFF8FAFC)
                                        else -> Color(0xFFFEF2F2)
                                    }
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isOk -> Color(0xFFE2E8F0)
                                        isNa -> Color(0xFFCBD5E1)
                                        else -> Color(0xFFFECACA)
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Header: Number + Title + Status Pill
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(
                                                    when {
                                                        isOk -> Color(0xFFDCFCE7)
                                                        isNa -> Color(0xFFE2E8F0)
                                                        else -> Color(0xFFFEE2E2)
                                                    },
                                                    RoundedCornerShape(6.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isOk -> Color(0xFF15803D)
                                                    isNa -> Color(0xFF475569)
                                                    else -> StatusRed
                                                }
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = entry.item.title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                lineHeight = 18.sp
                                            )

                                            if (entry.item.normRef.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Ref. Normativa: ${entry.item.normRef}",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = ElectricBlue
                                                )
                                            }
                                        }

                                        // Status Pill Badge
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    when {
                                                        isOk -> Color(0xFFECFDF5)
                                                        isNa -> Color(0xFFF1F5F9)
                                                        else -> Color(0xFFFEF2F2)
                                                    },
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    when {
                                                        isOk -> Color(0xFFA7F3D0)
                                                        isNa -> Color(0xFFCBD5E1)
                                                        else -> Color(0xFFFECACA)
                                                    },
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = entry.status.label,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isOk -> Color(0xFF047857)
                                                    isNa -> Color(0xFF475569)
                                                    else -> StatusRed
                                                }
                                            )
                                        }
                                    }

                                    // Full-Width Detailed Description
                                    if (entry.item.description.isNotBlank()) {
                                        Text(
                                            text = entry.item.description,
                                            fontSize = 12.sp,
                                            color = Color(0xFF334155),
                                            lineHeight = 17.sp,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Action Buttons: Conforme | Não se Aplica (N/A) | Não Conforme
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Conforme Button
                                        Button(
                                            onClick = {
                                                visualItems[index] = entry.copy(status = ChecklistStatus.CONFORME)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isOk) StatusGreen else Color(0xFFF1F5F9),
                                                contentColor = if (isOk) Color.White else Color(0xFF334155)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(36.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Conforme",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Não se Aplica (N/A) Button
                                        Button(
                                            onClick = {
                                                visualItems[index] = entry.copy(status = ChecklistStatus.NAO_SE_APLICA)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isNa) Color(0xFF475569) else Color(0xFFF1F5F9),
                                                contentColor = if (isNa) Color.White else Color(0xFF334155)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(36.dp)
                                        ) {
                                            Text(
                                                text = "N/A",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Não Conforme Button
                                        Button(
                                            onClick = {
                                                visualItems[index] = entry.copy(status = ChecklistStatus.NAO_CONFORME)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isNc) StatusRed else Color(0xFFF1F5F9),
                                                contentColor = if (isNc) Color.White else Color(0xFF334155)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(36.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Não Conf.",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    if (isNc) {
                                        OutlinedTextField(
                                            value = entry.observation,
                                            onValueChange = { newObs ->
                                                visualItems[index] = entry.copy(observation = newObs)
                                            },
                                            label = { Text("Descreva o defeito / motivo da não conformidade *", color = Color(0xFF0F172A), fontSize = 11.sp) },
                                            placeholder = { Text("Ex: Ressecamento excessivo na borda ou microfuro detectado", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                                            colors = appTextFieldColors(),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        // Summary Status Card
                        val isVisualApproved = nonConfsCount == 0

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isVisualApproved) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isVisualApproved) Color(0xFFA7F3D0) else Color(0xFFFECACA)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (isVisualApproved) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isVisualApproved) StatusGreen else StatusRed,
                                    modifier = Modifier.size(26.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isVisualApproved) {
                                            if (nasCount > 0) "Inspeção Visual Conforme ($conformsCount Conformes, $nasCount N/A)" else "Inspeção Visual 100% Conforme ($conformsCount Conformes)"
                                        } else {
                                            "Inspeção Visual Não Conforme ($nonConfsCount item(ns) reprovado(s))"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isVisualApproved) Color(0xFF065F46) else Color(0xFF991B1B)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isVisualApproved) {
                                            "Todos os critérios aplicáveis foram inspecionados e atendidos. Equipamento apto para o ensaio elétrico de suportabilidade e rigidez dielétrica."
                                        } else {
                                            "Atenção: A presença de defeitos físico-mecânicos ou contaminações reprova preventivamente o equipamento conforme NR-10."
                                        },
                                        fontSize = 11.sp,
                                        color = if (isVisualApproved) Color(0xFF047857) else Color(0xFFB91C1C),
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { currentStep = 2 },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Voltar", fontSize = 12.sp, color = Color.Black)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isEditMode) {
                                    OutlinedButton(
                                        onClick = { coroutineScope.launch { saveTestAction() } },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusGreen),
                                        border = BorderStroke(1.dp, StatusGreen),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Salvar Alterações", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Button(
                                    onClick = { currentStep = 4 },
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Avançar para Medições", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // STEP 4: Electrical Measurements & Norm Criteria
        if (currentStep == 4) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "4. Registro de Medições Dielétricas & Critérios da Norma",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        // DETECTOR POR INDUÇÃO: tabela Distância × Tensão (mesma da plataforma web)
                        if (selectedEquipmentType == EquipmentType.DETECTOR_INDUCAO) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Ensaio de Detecção por Aproximação (classe ${com.example.domain.InductionDetector.DETECTOR_CLASS})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.Black
                                    )
                                    Text(
                                        text = "Para cada tensão, informe a distância (cm) em que o detector sinalizou a presença de tensão. Desmarque \"Detectou\" se ele não sinalizar.",
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(NavyPrimary, RoundedCornerShape(6.dp))
                                            .padding(vertical = 4.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Tensão (kV)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                                        Text("Distância obtida (cm)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                                        Text("Detectou", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                                    }
                                    com.example.domain.InductionDetector.VOLTAGES_KV.forEachIndexed { idx, kv ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().background(if (inductionDetected[idx]) Color.Transparent else Color(0xFFFEF2F2), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${com.example.domain.InductionDetector.fmtBr1(kv)} kV",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF0F172A),
                                                modifier = Modifier.weight(0.8f)
                                            )
                                            OutlinedTextField(
                                                value = inductionDistances[idx],
                                                onValueChange = { v -> inductionDistances[idx] = v.filter { it.isDigit() || it == ',' || it == '.' } },
                                                enabled = inductionDetected[idx],
                                                singleLine = true,
                                                placeholder = { Text(if (inductionDetected[idx]) "cm" else "—", fontSize = 11.sp) },
                                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                                colors = appTextFieldColors(),
                                                modifier = Modifier.weight(1.2f).padding(vertical = 2.dp)
                                            )
                                            Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
                                                androidx.compose.material3.Switch(
                                                    checked = inductionDetected[idx],
                                                    onCheckedChange = { inductionDetected[idx] = it }
                                                )
                                            }
                                        }
                                    }
                                    // gráfico "Detector de Tensão" ao vivo (mesmo desenho do laudo)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(com.example.util.InductionChartRenderer.BASE_W / com.example.util.InductionChartRenderer.BASE_H)
                                            .drawBehind {
                                                drawIntoCanvas { c ->
                                                    com.example.util.InductionChartRenderer.draw(c.nativeCanvas, 0f, 0f, size.width, size.height, inductionPointsCurrent)
                                                }
                                            }
                                    )
                                    Text(
                                        text = "Obs. no laudo: RESULTADOS SATISFATÓRIOS CONFORME ESPECIFICAÇÕES DO FABRICANTE.",
                                        fontSize = 10.sp,
                                        color = Color(0xFF1D4ED8)
                                    )
                                }
                            }
                        }

                        // CARD DE CRITÉRIOS DA NORMA TÉCNICA
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCriteriaCustomized) Color(0xFFFFFBEB) else Color(0xFFF8FAFC)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isCriteriaCustomized) SafetyOrange else Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (isCriteriaCustomized) Icons.Default.Tune else Icons.Default.Verified,
                                            contentDescription = null,
                                            tint = if (isCriteriaCustomized) SafetyOrange else NavyPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Critérios da Norma Técnica",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color.Black
                                            )
                                            Text(
                                                text = currentEvaluation.normReference,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = NavyPrimary
                                            )
                                        }
                                    }

                                    // Badge Status (Padrão ou Customizado)
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isCriteriaCustomized) Color(0xFFFEF3C7) else Color(0xFFECFDF5),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isCriteriaCustomized) Color(0xFFF59E0B) else StatusGreen,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (isCriteriaCustomized) "Critérios Editados" else "Padrão Oficial",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCriteriaCustomized) Color(0xFFB45309) else Color(0xFF047857)
                                        )
                                    }
                                }

                                // Norma e Critério Técnico de Avaliação (mesma lista da plataforma web)
                                val applicableNorms = remember(selectedEquipmentType, normsRevision) { NormsEngine.criteriaFor(selectedEquipmentType) }
                                val activeNormId = currentEvaluation.criterionId
                                Text(
                                    text = "Norma e Critério Técnico de Avaliação",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF334155)
                                )
                                if (applicableNorms.isEmpty()) {
                                    Text(
                                        text = if (selectedEquipmentType == EquipmentType.ATERRAMENTO_TEMPORARIO)
                                            "A plataforma web não tem critério para este tipo: usando o critério próprio do aplicativo."
                                        else
                                            "Critério técnico não configurado para este tipo. Consulte a norma/procedimento aplicável (cadastre o critério na plataforma web).",
                                        fontSize = 10.sp,
                                        color = Color(0xFFB45309)
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        applicableNorms.forEach { norm ->
                                            val isActive = norm.id == activeNormId
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(if (isActive) Color(0xFFEFF6FF) else Color.White, RoundedCornerShape(8.dp))
                                                    .border(1.dp, if (isActive) NavyPrimary else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        selectedNormId = norm.id
                                                        isCriteriaCustomized = false
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = isActive,
                                                    onClick = {
                                                        selectedNormId = norm.id
                                                        isCriteriaCustomized = false
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${norm.normCode} — ${norm.normName}",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                        color = Color(0xFF0F172A),
                                                        maxLines = 2,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                    val leak = if (norm.maxLeakageCurrent % 1.0 == 0.0) norm.maxLeakageCurrent.toInt().toString() else norm.maxLeakageCurrent.toString()
                                                    Text(
                                                        text = "Classe ${norm.dielectricClass} • ${norm.testVoltage_kV} kV ${norm.voltageType} • ${norm.testDurationSeconds}s • fuga máx. $leak ${norm.currentUnit} • reensaio ${norm.defaultRetestMonths} meses",
                                                        fontSize = 9.5.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 4 Param Grid Cards (Tensão, Corrente de Fuga, Tempo, Validade)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color.White, RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        Column {
                                            Text("Tensão Exigida", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                                            Text("${currentEvaluation.targetVoltage_kV} kV", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.Black)
                                            Text("Tensão alvo mín.", fontSize = 8.sp, color = Color.DarkGray)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color.White, RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        Column {
                                            Text("Limite Fuga", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                                            Text("${currentEvaluation.leakageLimit_mA} mA", fontSize = 13.sp, fontWeight = FontWeight.Black, color = if (measuredLeakage_mA <= currentEvaluation.leakageLimit_mA) StatusGreen else StatusRed)
                                            Text("Máx. permitido", fontSize = 8.sp, color = Color.DarkGray)
                                        }
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color.White, RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        Column {
                                            Text("Tempo Mínimo", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                                            Text("${currentEvaluation.durationSeconds} s", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.Black)
                                            Text("Duração ensaio", fontSize = 8.sp, color = Color.DarkGray)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color.White, RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        Column {
                                            Text("Periodicidade", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                                            Text("${currentEvaluation.recommendedRetestMonths} meses", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.Black)
                                            Text("Validade laudo", fontSize = 8.sp, color = Color.DarkGray)
                                        }
                                    }
                                }

                                // Botão para Expandir / Ocultar Edição de Critérios
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { isEditingCriteria = !isEditingCriteria },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isEditingCriteria) NavyPrimary else Color(0xFFF1F5F9)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isEditingCriteria) Icons.Default.ExpandLess else Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = if (isEditingCriteria) Color.White else Color.Black,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isEditingCriteria) "Ocultar Painel de Edição" else "Editar Critérios da Norma",
                                            fontSize = 11.sp,
                                            color = if (isEditingCriteria) Color.White else Color.Black,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (isCriteriaCustomized) {
                                        OutlinedButton(
                                            onClick = {
                                                isCriteriaCustomized = false
                                                val std = NormsEngine.getStandardCriteria(
                                                    equipmentType = selectedEquipmentType,
                                                    dielectricClass = selectedDielectricClass.label,
                                                    gloveLength_mm = gloveLength_mm,
                                                    toolsCount = totalManualToolsCount
                                                )
                                                customNormCodeInput = std.normCode
                                                customTargetVoltageInput = std.targetVoltage_kV.toString()
                                                customLeakageLimitInput = String.format(Locale.US, "%.2f", std.leakageLimit_mA)
                                                customDurationSecondsInput = std.durationSeconds.toString()
                                                customRetestMonthsInput = std.retestIntervalMonths.toString()
                                                appliedVoltage_kV = std.targetVoltage_kV
                                                durationSeconds = std.durationSeconds
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RestartAlt,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = Color.Black
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Restaurar Norma", fontSize = 10.sp, color = Color.Black)
                                        }
                                    }
                                }

                                // Painel Expansível de Edição de Critérios
                                AnimatedVisibility(visible = isEditingCriteria) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White, RoundedCornerShape(10.dp))
                                            .border(1.dp, SafetyOrange, RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = SafetyOrange, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Ajuste de Parâmetros e Critérios Normativos:",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                        }
                                        Text(
                                            text = "Altere os valores abaixo caso o ensaio exija especificações customizadas pelo cliente, norma internacional (ex: ASTM) ou procedimento interno.",
                                            fontSize = 10.sp,
                                            color = Color.DarkGray
                                        )

                                        OutlinedTextField(
                                            value = customNormCodeInput,
                                            onValueChange = {
                                                customNormCodeInput = it
                                                isCriteriaCustomized = true
                                            },
                                            label = { Text("Nome / Código da Norma Aplicável", color = Color.Black) },
                                            colors = appTextFieldColors(),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = customTargetVoltageInput,
                                                onValueChange = {
                                                    customTargetVoltageInput = it
                                                    isCriteriaCustomized = true
                                                },
                                                label = { Text("Tensão Alvo (kV)", color = Color.Black) },
                                                colors = appTextFieldColors(),
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = customLeakageLimitInput,
                                                onValueChange = {
                                                    customLeakageLimitInput = it
                                                    isCriteriaCustomized = true
                                                },
                                                label = { Text("Limite Fuga (mA)", color = Color.Black) },
                                                colors = appTextFieldColors(),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = customDurationSecondsInput,
                                                onValueChange = {
                                                    customDurationSecondsInput = it
                                                    isCriteriaCustomized = true
                                                },
                                                label = { Text("Duração (segundos)", color = Color.Black) },
                                                colors = appTextFieldColors(),
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = customRetestMonthsInput,
                                                onValueChange = {
                                                    customRetestMonthsInput = it
                                                    isCriteriaCustomized = true
                                                },
                                                label = { Text("Validade (meses)", color = Color.Black) },
                                                colors = appTextFieldColors(),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val v = customTargetVoltageInput.toDoubleOrNull()
                                                    val d = customDurationSecondsInput.toIntOrNull()
                                                    if (v != null) appliedVoltage_kV = v
                                                    if (d != null) durationSeconds = d
                                                    isCriteriaCustomized = true
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f).height(36.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Sincronizar Medições", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = { isEditingCriteria = false },
                                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Text("Concluir Edição", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Detector por indução: sem tensão aplicada contínua, tempo nem corrente de fuga
                        // (o ensaio é a tabela distância × tensão acima), como na plataforma web
                        if (selectedEquipmentType != EquipmentType.DETECTOR_INDUCAO) {
                        HorizontalDivider(color = Color(0xFFE2E8F0))

                        Text(
                            text = "Valores Medidos na Bancada (Execução do Ensaio)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = appliedVoltageInput,
                                onValueChange = { input ->
                                    appliedVoltageInput = input.filter { it.isDigit() || it == '.' || it == ',' }
                                    appliedVoltageInput.replace(",", ".").toDoubleOrNull()?.let { appliedVoltage_kV = it }
                                },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                label = { Text("Tensão Aplicada (kV)", color = Color.Black) },
                                colors = appTextFieldColors(),
                                modifier = Modifier.weight(1f).testTag("wizard_voltage_input")
                            )
                            OutlinedTextField(
                                value = durationInput,
                                onValueChange = { input ->
                                    durationInput = input.filter { it.isDigit() }
                                    durationInput.toIntOrNull()?.let { durationSeconds = it }
                                },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                label = { Text("Duração (segundos)", color = Color.Black) },
                                colors = appTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        val isStickType = com.example.domain.NormsEngine.isStickType(selectedEquipmentType)
                        val leakageUnitLabel = if (isStickType) "µA" else "mA"
                        val leakageLimitDisplay = if (isStickType) "${currentEvaluation.leakageLimit_mA.toInt()} µA" else "${currentEvaluation.leakageLimit_mA} mA"

                        if (selectedEquipmentType == EquipmentType.ESCADA_ISOLADA) {
                            // Escada: quantidade de pontos ensaiados (limite de fuga = pontos × limite da norma)
                            OutlinedTextField(
                                value = stickPointsInput,
                                onValueChange = { input -> stickPointsInput = input.filter { it.isDigit() }.take(3) },
                                label = { Text("Quantidade de pontos ensaiados *", color = Color.Black) },
                                placeholder = { Text("Ex: 3", color = Color.Gray) },
                                isError = stickPointsInput.isBlank(),
                                supportingText = {
                                    // limite por ponto do critério escolhido (padrão EN 50528:2024: 0,50 mA)
                                    val perPoint = NormsEngine.getStandardCriteria(selectedEquipmentType, selectedDielectricClass.label, criterionId = selectedNormId).leakageLimit_mA
                                    Text(
                                        if (stickPointsInput.isBlank()) "Informe quantos pontos da escada foram ensaiados (vazio conta como 1)."
                                        else "Limite de fuga: $stickTestPoints × ${String.format(Locale.US, "%.2f", perPoint)} mA = ${String.format(Locale.US, "%.2f", perPoint * stickTestPoints)} mA",
                                        color = if (stickPointsInput.isBlank()) StatusRed else Color(0xFF15803D),
                                        fontSize = 11.sp
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                colors = appTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        OutlinedTextField(
                            value = measuredLeakageInput,
                            onValueChange = { input ->
                                measuredLeakageInput = input.filter { it.isDigit() || it == '.' || it == ',' }
                            },
                            label = { Text("Corrente de Fuga Medida ($leakageUnitLabel)", color = Color.Black) },
                            placeholder = { Text(if (isStickType) "Ex: 4 (limite: $leakageLimitDisplay)" else "Ex: 1.50 (deve ser informada)", color = Color.Gray) },
                            isError = measuredLeakageInput.isBlank(),
                            supportingText = {
                                if (measuredLeakageInput.isBlank()) {
                                    Text("Campo em branco por padrão. Digite o valor medido no ensaio.", color = StatusRed, fontSize = 11.sp)
                                } else if (isStickType) {
                                    Text("Unidade: microampères (µA) conforme ABNT NBR 11854.", color = Color(0xFF15803D), fontSize = 11.sp)
                                } else if (selectedEquipmentType == EquipmentType.LUVA_ISOLANTE) {
                                    val cleanCl = selectedDielectricClass.label
                                    val uLim = NormsEngine.getGloveUnitaryLeakageLimit(cleanCl, gloveLength_mm)
                                    val uStr = if (uLim % 1.0 == 0.0) uLim.toInt().toString() else uLim.toString()
                                    val pStr = if (currentEvaluation.leakageLimit_mA % 1.0 == 0.0) currentEvaluation.leakageLimit_mA.toInt().toString() else currentEvaluation.leakageLimit_mA.toString()
                                    Text("Ensaio com 2 luvas simultâneas: limite $pStr mA (2x valor unitário de $uStr mA da Tab. 4 NBR 16295).", color = Color(0xFF1D4ED8), fontSize = 11.sp)
                                }
                            },
                            colors = appTextFieldColors(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("wizard_leakage_input")
                        )

                        // Medir a corrente de fuga pelo alicate Fluke 369 FC (Bluetooth)
                        Spacer(Modifier.height(8.dp))
                        com.example.ui.components.FlukeMeasureButton(unitLabel = leakageUnitLabel) { v ->
                            measuredLeakageInput = if (v % 1.0 == 0.0) v.toInt().toString()
                                else String.format(Locale.US, if (leakageUnitLabel == "µA") "%.1f" else "%.3f", v)
                        }

                        // Suportabilidade sem perfuração toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Suportabilidade à Alta Tensão:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = { withstandWithoutPuncture = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (withstandWithoutPuncture) StatusGreen else Color(0xFFE2E8F0)
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Sem Furo", fontSize = 10.sp, color = if (withstandWithoutPuncture) Color.White else Color.Black)
                                }
                                Button(
                                    onClick = { withstandWithoutPuncture = false },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!withstandWithoutPuncture) StatusRed else Color(0xFFE2E8F0)
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Houve Disrupção", fontSize = 10.sp, color = if (!withstandWithoutPuncture) Color.White else Color.Black)
                                }
                            }
                        }

                        // Live Gauges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpeedometerGauge(
                                title = "Tensão Aplicada",
                                value = appliedVoltage_kV,
                                unit = "kV",
                                maxScale = (appliedVoltage_kV * 1.4).coerceAtLeast(15.0),
                                isVoltage = true,
                                subtext = "Alvo: ${currentEvaluation.targetVoltage_kV} kV",
                                modifier = Modifier.weight(1f)
                            )
                            SpeedometerGauge(
                                title = "Corrente de Fuga",
                                value = measuredLeakage_mA,
                                unit = leakageUnitLabel,
                                maxScale = (currentEvaluation.leakageLimit_mA * 1.5).coerceAtLeast(if (isStickType) 12.0 else 15.0),
                                limit = currentEvaluation.leakageLimit_mA,
                                isConforming = currentEvaluation.result == TestResult.APROVADO,
                                isVoltage = false,
                                subtext = "Limite: $leakageLimitDisplay",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        }

                        // Evaluation Preview Box
                        val isApproved = currentEvaluation.result == TestResult.APROVADO
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isApproved) Color(0xFFECFDF5) else Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                                .border(1.dp, if (isApproved) StatusGreen else StatusRed, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Avaliação em Tempo Real: ${currentEvaluation.result}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isApproved) Color(0xFF065F46) else Color(0xFF991B1B)
                                    )
                                    Text(
                                        text = "Ref: ${currentEvaluation.normReference}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isApproved) Color(0xFF065F46) else Color(0xFF991B1B)
                                    )
                                }
                                Text(
                                    text = currentEvaluation.rationale,
                                    fontSize = 10.sp,
                                    color = Color.Black
                                )
                            }
                        }

                        // REGISTRO FOTOGRÁFICO DO ENSAIO (Câmera ou Galeria)
                        val activeOSObj = serviceOrders.find { it.id == selectedOSId }
                        val activeOSNumber = activeOSObj?.osNumber ?: "OS-AVULSA"
                        val activeClientName = clients.find { it.id == selectedClientId }?.let { it.nomeFantasia.ifBlank { it.razaoSocial } } ?: ""

                        PhotoCaptureSection(
                            photos = photoList,
                            onPhotosChanged = { updatedList ->
                                photoList.clear()
                                photoList.addAll(updatedList)
                            },
                            serviceOrderNumber = activeOSNumber,
                            equipmentTag = equipmentTag,
                            clientName = activeClientName
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { currentStep = 3 },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Voltar", fontSize = 12.sp, color = Color.Black)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isEditMode) {
                                    OutlinedButton(
                                        onClick = { coroutineScope.launch { saveTestAction() } },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusGreen),
                                        border = BorderStroke(1.dp, StatusGreen),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Salvar Alterações", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Button(
                                    onClick = { currentStep = 5 },
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Avançar para Instrumentos", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // STEP 5: Lab Instruments
        if (currentStep == 5) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "5. Instrumentação do Laboratório Empregada",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        instruments.forEach { inst ->
                            val isSelected = selectedInstrumentIds.contains(inst.id)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) ElectricBlue else Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (isSelected) selectedInstrumentIds.remove(inst.id) else selectedInstrumentIds.add(inst.id)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(inst.type, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
                                        Text("${inst.manufacturer} ${inst.model} • SN: ${inst.serialNumber}", fontSize = 10.sp, color = Color.Black)
                                        Text("Certificado: ${inst.calibrationCertNumber}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.Black)
                                        if (inst.calibrationCertificateUri.isNotBlank()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(10.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Certificado digital vinculado", fontSize = 9.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (isSelected) ElectricBlue else Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { currentStep = 4 },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Voltar", fontSize = 12.sp, color = Color.Black)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isEditMode) {
                                    OutlinedButton(
                                        onClick = { coroutineScope.launch { saveTestAction() } },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusGreen),
                                        border = BorderStroke(1.dp, StatusGreen),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Salvar Alterações", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Button(
                                    onClick = { currentStep = 6 },
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Avançar para Conclusão", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // STEP 6: Signatures & Finalize
        if (currentStep == 6) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "6. Conclusão Técnica, Assinaturas e Emissão",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        // Final Result Card
                        val isApproved = currentEvaluation.result == TestResult.APROVADO
                        val hasDualOpinions = selectedEquipmentType == EquipmentType.FERRAMENTA_ISOLADA && rejectedManualToolsCount > 0 && (totalManualToolsCount - rejectedManualToolsCount) > 0

                        if (hasDualOpinions) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFD97706), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "⚡ Emissão de 2 Pareceres Técnicos Distintos (Aprovadas vs. Reprovadas)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E)
                                    )
                                }

                                // Card Parecer 1: Aprovadas
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFECFDF5), RoundedCornerShape(8.dp))
                                        .border(1.5.dp, StatusGreen, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "PARECER 1: FERRAMENTAS APROVADAS (${totalManualToolsCount - rejectedManualToolsCount} un.)",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = Color(0xFF065F46)
                                        )
                                        Text(
                                            text = "Conforme NBR 9699 / IEC 60900. Liberadas para operação até 1000 V CA / 1500 V CC conforme NR-10.",
                                            fontSize = 11.sp,
                                            color = Color.Black
                                        )
                                    }
                                }

                                // Card Parecer 2: Reprovadas
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                                        .border(1.5.dp, StatusRed, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "PARECER 2: FERRAMENTAS REPROVADAS ($rejectedManualToolsCount un.)",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = Color(0xFF991B1B)
                                        )
                                        Text(
                                            text = "Condenadas por não conformidades. Determina-se segregação e descarte imediato (NR-10 10.4.3.1).",
                                            fontSize = 11.sp,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isApproved) Color(0xFFECFDF5) else Color(0xFFFEF2F2), RoundedCornerShape(10.dp))
                                    .border(1.5.dp, if (isApproved) StatusGreen else StatusRed, RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "PARECER OFICIAL: ${currentEvaluation.result}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = if (isApproved) Color(0xFF065F46) else Color(0xFF991B1B)
                                    )
                                    Text(
                                        text = currentEvaluation.rationale,
                                        fontSize = 11.sp,
                                        color = Color.Black
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = technicalNotes,
                            onValueChange = { technicalNotes = it },
                            label = { Text("Observações / Composição do Ensaio", color = Color.Black) },
                            colors = appTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Technical Responsibles & Global Settings Card
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Responsáveis Técnicos e Operacionais",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = NavyPrimary
                                        )
                                        Text(
                                            text = "Dados emitidos no Laudo e Certificado Oficial",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            settings?.let { s ->
                                                currentTechName = s.defaultTechnicianName
                                                currentTechCft = s.defaultTechnicianCftOrCrea
                                                currentRtName = s.technicalResponsibleName
                                                currentRtCrea = s.technicalResponsibleCrea
                                                technicianSig = s.defaultTechnicianSignatureImage
                                                rtSig = s.technicalResponsibleSignatureImage
                                                Toast.makeText(context, "Dados e assinaturas carregados das Configurações!", Toast.LENGTH_SHORT).show()
                                            } ?: run {
                                                Toast.makeText(context, "Nenhuma configuração cadastrada.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Carregar das Configurações", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Editable fields for Técnico Executor & RT
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = currentTechName,
                                        onValueChange = { currentTechName = it },
                                        label = { Text("Técnico / Analista Executor", color = Color.Black) },
                                        colors = appTextFieldColors(),
                                        modifier = Modifier.weight(1.3f)
                                    )
                                    OutlinedTextField(
                                        value = currentTechCft,
                                        onValueChange = { currentTechCft = it },
                                        label = { Text("Nº CFT / CREA", color = Color.Black) },
                                        colors = appTextFieldColors(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = currentRtName,
                                        onValueChange = { currentRtName = it },
                                        label = { Text("Eng. Responsável Técnico", color = Color.Black) },
                                        colors = appTextFieldColors(),
                                        modifier = Modifier.weight(1.3f)
                                    )
                                    OutlinedTextField(
                                        value = currentRtCrea,
                                        onValueChange = { currentRtCrea = it },
                                        label = { Text("Nº CREA / RNP", color = Color.Black) },
                                        colors = appTextFieldColors(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val base = settings ?: AppSettingEntity()
                                        val updated = base.copy(
                                            defaultTechnicianName = currentTechName.ifBlank { base.defaultTechnicianName },
                                            defaultTechnicianCftOrCrea = currentTechCft.ifBlank { base.defaultTechnicianCftOrCrea },
                                            defaultTechnicianSignatureImage = technicianSig.ifBlank { base.defaultTechnicianSignatureImage },
                                            technicalResponsibleName = currentRtName.ifBlank { base.technicalResponsibleName },
                                            technicalResponsibleCrea = currentRtCrea.ifBlank { base.technicalResponsibleCrea },
                                            technicalResponsibleSignatureImage = rtSig.ifBlank { base.technicalResponsibleSignatureImage }
                                        )
                                        viewModel.saveSettings(updated)
                                        Toast.makeText(context, "Dados e assinaturas salvos como padrão nas Configurações!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(34.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Salvar estes dados como Padrão do Aplicativo", fontSize = 10.5.sp, color = NavyPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Signatures Pads
                        val effTechName = currentTechName.ifBlank { settings?.defaultTechnicianName ?: "Eng. João Carlos Evaristo Guedes Nunes" }
                        val effTechRole = currentTechCft.trim().let { if (it.isBlank()) "Analista Executor" else "$it • Analista Executor" }
                        val effRtName = currentRtName.ifBlank { settings?.technicalResponsibleName ?: "Eng. João Carlos Evaristo Guedes Nunes" }
                        val effRtRole = "${currentRtCrea.ifBlank { settings?.technicalResponsibleCrea ?: "CREA 24481/D-DF" }} • Responsável Técnico"

                        SignaturePad(
                            title = "Assinatura do Analista Executor",
                            signerName = effTechName,
                            signerRole = effTechRole,
                            initialSignature = technicianSig.ifEmpty { settings?.defaultTechnicianSignatureImage ?: "" },
                            savedProfileSignature = settings?.defaultTechnicianSignatureImage ?: "",
                            onSignatureConfirmed = { technicianSig = it },
                            onSaveToProfile = { newSigPath ->
                                val base = settings ?: AppSettingEntity()
                                viewModel.saveSettings(base.copy(
                                    defaultTechnicianName = effTechName,
                                    defaultTechnicianCftOrCrea = currentTechCft.ifBlank { base.defaultTechnicianCftOrCrea },
                                    defaultTechnicianSignatureImage = newSigPath
                                ))
                            }
                        )

                        SignaturePad(
                            title = "Assinatura do Responsável Técnico",
                            signerName = effRtName,
                            signerRole = effRtRole,
                            initialSignature = rtSig.ifEmpty { settings?.technicalResponsibleSignatureImage ?: "" },
                            savedProfileSignature = settings?.technicalResponsibleSignatureImage ?: "",
                            onSignatureConfirmed = { rtSig = it },
                            onSaveToProfile = { newSigPath ->
                                val base = settings ?: AppSettingEntity()
                                viewModel.saveSettings(base.copy(
                                    technicalResponsibleName = effRtName,
                                    technicalResponsibleCrea = currentRtCrea.ifBlank { base.technicalResponsibleCrea },
                                    technicalResponsibleSignatureImage = newSigPath
                                ))
                            }
                        )

                        // Final Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = { currentStep = 5 },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Voltar", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        saveTestAction()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isApproved) StatusGreen else SafetyOrange),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("wizard_finalize_button")
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEditMode) "Salvar Alterações no Laudo"
                                           else if (isApproved) "Finalizar e Emitir Laudo & Certificado"
                                           else "Registrar Ensaio Reprovado",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Número para campo de texto, sem casas decimais desnecessárias ("22" em vez de "22.0"). */
private fun formatNumberInput(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private const val ENV_PREFS = "jvm_wizard_env"
private const val KEY_LAST_TEMPERATURE = "last_temperature"
private const val KEY_LAST_HUMIDITY = "last_humidity"

/** Guarda temperatura e umidade do ensaio salvo para pré-preencher o próximo. */
private fun rememberLastEnvironment(context: android.content.Context, temperature: String, humidity: String) {
    val editor = context.getSharedPreferences(ENV_PREFS, android.content.Context.MODE_PRIVATE).edit()
    if (temperature.isNotBlank()) editor.putString(KEY_LAST_TEMPERATURE, temperature.trim())
    if (humidity.isNotBlank()) editor.putString(KEY_LAST_HUMIDITY, humidity.trim())
    editor.apply()
}

/** Data (dd/MM/aaaa, aaaa-MM-dd...) + hora (HH:mm) do ensaio em milissegundos, para ordenar. */
private fun testTimestamp(date: String, time: String): Long {
    val day = com.example.util.DateUtils.parseDate(date)?.time ?: return 0L
    val parts = time.trim().split(":")
    val minutes = (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    return day + minutes * 60_000L
}
