package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppSettingEntity
import com.example.data.model.EquipmentType
import com.example.data.model.TestRecordEntity
import com.example.ui.components.BatchEmissionDialog
import com.example.ui.components.StatusBadge
import com.example.ui.theme.appTextFieldColors
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.viewmodel.DielectricViewModel

@Composable
fun TestsListScreen(
    viewModel: DielectricViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tests by viewModel.tests.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentCompany by viewModel.currentCompany.collectAsStateWithLifecycle()
    val filterByMyCompany by viewModel.filterByMyCompany.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCompanyFilter by remember { mutableStateOf("") }
    var selectedCollaboratorFilter by remember { mutableStateOf("") }
    var selectedResultFilter by remember { mutableStateOf("ALL") }
    var selectedTypeFilter by remember { mutableStateOf("") }
    // Sort options: "SEQUENCE_ASC" (Nº Laudo Crescente), "SEQUENCE_DESC" (Nº Laudo Decrescente), "DATE_DESC" (Data Recente), "DATE_ASC" (Data Antiga)
    var selectedSortOrder by remember { mutableStateOf("SEQUENCE_ASC") }

    var selectedTestIds by remember { mutableStateOf(setOf<String>()) }
    var testToDelete by remember { mutableStateOf<TestRecordEntity?>(null) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showBatchDialog by remember { mutableStateOf(false) }

    // Helper to extract numeric suffix and sequence components from reportNumber (e.g., "LAU-2026-0001" or "LAU-2609-0002" or "001")
    fun parseReportSequenceKey(reportNum: String): Pair<String, Long> {
        val clean = reportNum.trim()
        val digits = clean.filter { it.isDigit() }
        val num = digits.toLongOrNull() ?: 0L
        return Pair(clean, num)
    }

    // Derive dynamic options for dropdown filters
    val companyOptions = remember(clients, tests) {
        val names = mutableSetOf<String>()
        clients.forEach { c ->
            val name = c.nomeFantasia.ifBlank { c.razaoSocial }.trim()
            if (name.isNotBlank()) names.add(name)
        }
        tests.forEach { t ->
            if (t.clientName.isNotBlank()) names.add(t.clientName.trim())
        }
        listOf("Todas as Empresas") + names.sorted()
    }

    val collaboratorOptions = remember(tests) {
        val cols = tests.mapNotNull { it.collaboratorName.trim().takeIf { c -> c.isNotEmpty() } }.distinct().sorted()
        listOf("Todos os Colaboradores") + cols
    }

    val resultOptions = listOf("Todos os Resultados", "Aprovados", "Reprovados")

    val equipmentTypeOptions = remember(tests) {
        val types = tests.mapNotNull { it.equipmentType.trim().takeIf { t -> t.isNotEmpty() } }
            .map { EquipmentType.fromString(it).label }
            .distinct()
            .sorted()
        listOf("Todos os Tipos de EPI / EPC") + types
    }

    val filteredTests = tests.filter { test ->
        val matchesQuery = searchQuery.isEmpty() ||
                test.reportNumber.contains(searchQuery, ignoreCase = true) ||
                test.equipmentTag.contains(searchQuery, ignoreCase = true) ||
                test.serviceOrderNumber.contains(searchQuery, ignoreCase = true) ||
                test.clientName.contains(searchQuery, ignoreCase = true) ||
                test.collaboratorName.contains(searchQuery, ignoreCase = true) ||
                test.validationCode.contains(searchQuery, ignoreCase = true) ||
                test.equipmentSerial.contains(searchQuery, ignoreCase = true)

        val matchesCompany = selectedCompanyFilter.isEmpty() ||
                test.clientName.equals(selectedCompanyFilter, ignoreCase = true) ||
                clients.find { it.id == test.clientId }?.let { c ->
                    c.nomeFantasia.equals(selectedCompanyFilter, ignoreCase = true) ||
                    c.razaoSocial.equals(selectedCompanyFilter, ignoreCase = true)
                } == true

        val matchesCollaborator = selectedCollaboratorFilter.isEmpty() ||
                test.collaboratorName.equals(selectedCollaboratorFilter, ignoreCase = true)

        val matchesResult = when (selectedResultFilter) {
            "ALL" -> true
            "APROVADO" -> test.result == "APROVADO"
            "REPROVADO" -> test.result == "REPROVADO"
            else -> true
        }

        val matchesType = selectedTypeFilter.isEmpty() ||
                test.equipmentType.equals(selectedTypeFilter, ignoreCase = true) ||
                EquipmentType.fromString(test.equipmentType).label.equals(selectedTypeFilter, ignoreCase = true) ||
                EquipmentType.fromString(test.equipmentType).name.equals(selectedTypeFilter, ignoreCase = true)

        val matchesMyCompany = !filterByMyCompany || currentCompany == null || test.companyId == currentCompany?.id || test.companyId.isBlank()

        matchesQuery && matchesCompany && matchesCollaborator && matchesResult && matchesType && matchesMyCompany
    }.let { list ->
        when (selectedSortOrder) {
            "SEQUENCE_ASC" -> list.sortedWith(
                compareBy<TestRecordEntity> { parseReportSequenceKey(it.reportNumber).second }
                    .thenBy { it.reportNumber }
            )
            "SEQUENCE_DESC" -> list.sortedWith(
                compareByDescending<TestRecordEntity> { parseReportSequenceKey(it.reportNumber).second }
                    .thenByDescending { it.reportNumber }
            )
            "DATE_DESC" -> list.sortedWith(
                compareByDescending<TestRecordEntity> { it.testDate }
                    .thenByDescending { it.testTime }
            )
            "DATE_ASC" -> list.sortedWith(
                compareBy<TestRecordEntity> { it.testDate }
                    .thenBy { it.testTime }
            )
            else -> list
        }
    }

    fun toggleSelectTest(testId: String) {
        selectedTestIds = if (testId in selectedTestIds) {
            selectedTestIds - testId
        } else {
            selectedTestIds + testId
        }
        isSelectionMode = selectedTestIds.isNotEmpty()
    }

    fun selectAll() {
        val allFilteredIds = filteredTests.map { it.id }.toSet()
        if (allFilteredIds.isNotEmpty() && selectedTestIds.containsAll(allFilteredIds)) {
            selectedTestIds = emptySet()
            isSelectionMode = false
        } else {
            selectedTestIds = allFilteredIds
            isSelectionMode = true
        }
    }

    fun selectApproved() {
        val approvedIds = filteredTests.filter { it.result == "APROVADO" }.map { it.id }.toSet()
        if (approvedIds.isNotEmpty() && selectedTestIds.containsAll(approvedIds) && selectedTestIds.size == approvedIds.size) {
            selectedTestIds = emptySet()
            isSelectionMode = false
        } else {
            selectedTestIds = approvedIds
            isSelectionMode = true
        }
    }

    fun clearSelection() {
        selectedTestIds = emptySet()
        isSelectionMode = false
    }

    testToDelete?.let { target ->
        com.example.ui.components.ConfirmDeleteDialog(
            title = "Excluir laudo?",
            itemName = "Laudo ${target.reportNumber.ifBlank { target.testNumber }}",
            details = "TAG ${target.equipmentTag} • ${target.clientName} • ${target.result}\n" +
                "O certificado deste laudo deixa de ser validado pelo QR Code.",
            onConfirm = {
                viewModel.deleteTest(target.id)
                testToDelete = null
            },
            onDismiss = { testToDelete = null }
        )
    }

    if (showBatchDialog && selectedTestIds.isNotEmpty()) {
        val selectedRecords = tests
            .filter { it.id in selectedTestIds }
            .sortedWith(
                compareBy<TestRecordEntity> { parseReportSequenceKey(it.reportNumber).second }
                    .thenBy { it.reportNumber }
            )
        BatchEmissionDialog(
            selectedTests = selectedRecords,
            company = settings ?: AppSettingEntity(),
            onDismiss = { showBatchDialog = false }
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Laudos & Certificados", fontWeight = FontWeight.Black, fontSize = 20.sp, color = NavyPrimary)
                    Text("Gestão e emissão de laudos de ensaios dielétricos NR-10", fontSize = 11.sp, color = Color(0xFF64748B))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            if (isSelectionMode) {
                                clearSelection()
                            } else {
                                isSelectionMode = true
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelectionMode) NavyPrimary.copy(alpha = 0.1f) else Color.White,
                            contentColor = if (isSelectionMode) NavyPrimary else Color(0xFF475569)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelectionMode) NavyPrimary else Color(0xFFCBD5E1)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("toggle_selection_mode_button")
                    ) {
                        Icon(
                            imageVector = if (isSelectionMode) Icons.Default.Close else Icons.Default.Layers,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isSelectionMode) "Cancelar" else "Selecionar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.navigateTo("reports") },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Relatório", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.startNewTest() },
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Novo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Batch Action Bar when items are selected or selection mode is active
        if (isSelectionMode || selectedTestIds.isNotEmpty()) {
            item {
                val allFilteredSelected = filteredTests.isNotEmpty() && filteredTests.all { it.id in selectedTestIds }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = allFilteredSelected,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) selectAll() else clearSelection()
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = SafetyOrange,
                                    uncheckedColor = Color.White,
                                    checkmarkColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = "${selectedTestIds.size} selecionado(s)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = if (allFilteredSelected) "Todos marcados" else "Toque para marcar todos",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (selectedTestIds.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { clearSelection() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Limpar", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = { showBatchDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("batch_emission_action_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Emissão Agrupada (${selectedTestIds.size})",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Search and Filter Bar (Filtros para Consulta & Emissão de Laudos)
        item {
            val hasActiveFilters = searchQuery.isNotEmpty() ||
                    selectedCompanyFilter.isNotEmpty() ||
                    selectedCollaboratorFilter.isNotEmpty() ||
                    selectedResultFilter != "ALL" ||
                    selectedTypeFilter.isNotEmpty()

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header: Filtros para Consulta & Emissão de Laudos
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FilterAlt,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FILTROS PARA CONSULTA & EMISSÃO DE LAUDOS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A),
                                letterSpacing = 0.5.sp
                            )
                        }

                        if (hasActiveFilters) {
                            Text(
                                text = "Limpar Filtros",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7),
                                modifier = Modifier
                                    .clickable {
                                        searchQuery = ""
                                        selectedCompanyFilter = ""
                                        selectedCollaboratorFilter = ""
                                        selectedResultFilter = "ALL"
                                        selectedTypeFilter = ""
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("clear_all_filters_btn")
                            )
                        }
                    }

                    // 5 Filters Row (Scrollable horizontally)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Search Box (Pill)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .width(210.dp)
                                .height(40.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Buscar Laudo, Tag, OS...",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 12.sp,
                                            color = Color(0xFF0F172A),
                                            fontWeight = FontWeight.Medium
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("tests_search_input")
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpar busca",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { searchQuery = "" }
                                    )
                                }
                            }
                        }

                        // 2. Dropdown: Empresas
                        FilterDropdownPill(
                            displayText = if (selectedCompanyFilter.isEmpty()) "🏢 Todas as Empresas" else "🏢 $selectedCompanyFilter",
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Apartment,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            isSelected = selectedCompanyFilter.isNotEmpty(),
                            options = companyOptions,
                            onSelect = { selected ->
                                selectedCompanyFilter = if (selected == "Todas as Empresas") "" else selected
                            },
                            modifier = Modifier.testTag("filter_company_dropdown")
                        )

                        // 3. Dropdown: Colaboradores
                        FilterDropdownPill(
                            displayText = if (selectedCollaboratorFilter.isEmpty()) "👤 Todos os Colaboradores" else "👤 $selectedCollaboratorFilter",
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(0xFFEA580C),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            isSelected = selectedCollaboratorFilter.isNotEmpty(),
                            options = collaboratorOptions,
                            onSelect = { selected ->
                                selectedCollaboratorFilter = if (selected == "Todos os Colaboradores") "" else selected
                            },
                            modifier = Modifier.testTag("filter_collaborator_dropdown")
                        )

                        // 4. Dropdown: Resultados
                        val resultLabel = when (selectedResultFilter) {
                            "ALL" -> "Todos os Resultados"
                            "APROVADO" -> "Aprovados"
                            "REPROVADO" -> "Reprovados"
                            else -> "Todos os Resultados"
                        }
                        FilterDropdownPill(
                            displayText = resultLabel,
                            isSelected = selectedResultFilter != "ALL",
                            options = resultOptions,
                            onSelect = { selected ->
                                selectedResultFilter = when (selected) {
                                    "Aprovados" -> "APROVADO"
                                    "Reprovados" -> "REPROVADO"
                                    else -> "ALL"
                                }
                            },
                            modifier = Modifier.testTag("filter_result_dropdown")
                        )

                        // 5. Dropdown: Tipos de EPI / EPC
                        FilterDropdownPill(
                            displayText = if (selectedTypeFilter.isEmpty()) "Todos os Tipos de EPI / EPC" else selectedTypeFilter,
                            isSelected = selectedTypeFilter.isNotEmpty(),
                            options = equipmentTypeOptions,
                            onSelect = { selected ->
                                selectedTypeFilter = if (selected == "Todos os Tipos de EPI / EPC") "" else selected
                            },
                            modifier = Modifier.testTag("filter_equipment_type_dropdown")
                        )

                        // 6. Dropdown: Ordenação em Sequência
                        val sortOptions = listOf(
                            "🔢 Sequência: Crescente (001 ➔ 999)",
                            "🔢 Sequência: Decrescente (999 ➔ 001)",
                            "📅 Data: Mais Recente Primeiro",
                            "📅 Data: Mais Antigo Primeiro"
                        )
                        val currentSortLabel = when (selectedSortOrder) {
                            "SEQUENCE_ASC" -> "🔢 Seq. Crescente (1 ➔ N)"
                            "SEQUENCE_DESC" -> "🔢 Seq. Decrescente (N ➔ 1)"
                            "DATE_DESC" -> "📅 Data: Mais Recente"
                            "DATE_ASC" -> "📅 Data: Mais Antiga"
                            else -> "🔢 Ordenar Laudos"
                        }
                        FilterDropdownPill(
                            displayText = currentSortLabel,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            isSelected = true,
                            options = sortOptions,
                            onSelect = { selected ->
                                selectedSortOrder = when {
                                    selected.contains("Crescente") -> "SEQUENCE_ASC"
                                    selected.contains("Decrescente") -> "SEQUENCE_DESC"
                                    selected.contains("Recente") -> "DATE_DESC"
                                    selected.contains("Antigo") -> "DATE_ASC"
                                    else -> "SEQUENCE_ASC"
                                }
                            },
                            modifier = Modifier.testTag("filter_sort_dropdown")
                        )
                    }

                    // Bottom Row: Seleção para Lote + Contagem
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "SELEÇÃO PARA LOTE:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B),
                                letterSpacing = 0.5.sp
                            )

                            // Botão 1: Selecionar Todos Visíveis
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEFF6FF),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                modifier = Modifier
                                    .clickable { selectAll() }
                                    .testTag("select_all_visible_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckBox,
                                        contentDescription = null,
                                        tint = Color(0xFF1D4ED8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Selecionar Todos Visíveis (${filteredTests.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1D4ED8)
                                    )
                                }
                            }

                            // Botão 2: Selecionar Aprovados
                            val approvedCount = filteredTests.count { it.result == "APROVADO" }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFECFDF5),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0)),
                                modifier = Modifier
                                    .clickable { selectApproved() }
                                    .testTag("select_approved_visible_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF047857),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Selecionar Aprovados ($approvedCount)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF047857)
                                    )
                                }
                            }

                            if (selectedTestIds.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SafetyOrange,
                                    modifier = Modifier
                                        .clickable { showBatchDialog = true }
                                        .testTag("batch_emit_quick_btn")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Layers,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Emitir Lote (${selectedTestIds.size})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Right side: Ordenação rápida & Exibindo X de Y ensaios
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            // Quick sort toggle button
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedSortOrder.startsWith("SEQUENCE")) Color(0xFFEFF6FF) else Color(0xFFF1F5F9),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (selectedSortOrder.startsWith("SEQUENCE")) Color(0xFF93C5FD) else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .clickable {
                                        selectedSortOrder = when (selectedSortOrder) {
                                            "SEQUENCE_ASC" -> "SEQUENCE_DESC"
                                            "SEQUENCE_DESC" -> "DATE_DESC"
                                            "DATE_DESC" -> "DATE_ASC"
                                            else -> "SEQUENCE_ASC"
                                        }
                                    }
                                    .testTag("quick_toggle_sort_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = when (selectedSortOrder) {
                                            "SEQUENCE_ASC" -> Icons.Default.ArrowUpward
                                            "SEQUENCE_DESC" -> Icons.Default.ArrowDownward
                                            "DATE_DESC" -> Icons.Default.ArrowDownward
                                            else -> Icons.Default.ArrowUpward
                                        },
                                        contentDescription = "Alternar ordenação",
                                        tint = if (selectedSortOrder.startsWith("SEQUENCE")) Color(0xFF1D4ED8) else Color(0xFF475569),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = when (selectedSortOrder) {
                                            "SEQUENCE_ASC" -> "Seq. 001 ➔ 999"
                                            "SEQUENCE_DESC" -> "Seq. 999 ➔ 001"
                                            "DATE_DESC" -> "Data Recente"
                                            "DATE_ASC" -> "Data Antiga"
                                            else -> "Sequência"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedSortOrder.startsWith("SEQUENCE")) Color(0xFF1D4ED8) else Color(0xFF475569)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Exibindo ",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "${filteredTests.size}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = " de ",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "${tests.size}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = " ensaios",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tests List
        if (filteredTests.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Nenhum laudo encontrado", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF64748B))
                        Text("Tente alterar o termo da busca ou os filtros.", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }
            }
        } else {
            items(filteredTests) { test ->
                TestListItemCard(
                    test = test,
                    isSelected = test.id in selectedTestIds,
                    isSelectionMode = isSelectionMode || selectedTestIds.isNotEmpty(),
                    onToggleSelect = { toggleSelectTest(test.id) },
                    onOpenLaudo = { viewModel.openLaudo(test) },
                    onOpenCertificado = { viewModel.openCertificado(test) },
                    onOpenNiimbot = { viewModel.openNiimbotLabel(test) },
                    onEdit = { viewModel.openEditTest(test) },
                    onDelete = { testToDelete = test },
                    onShare = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Laudo Técnico ${test.reportNumber} - Tag ${test.equipmentTag} (${test.result}) - Validação: ${com.example.util.ValidationPortal.url(test.validationCode)}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Compartilhar Laudo"))
                    }
                )
            }
        }
    }
}

@Composable
private fun FilterDropdownPill(
    displayText: String,
    leadingIcon: @Composable (() -> Unit)? = null,
    isSelected: Boolean = false,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) Color(0xFFF0F9FF) else Color.White,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isSelected) Color(0xFF0284C7) else Color(0xFFCBD5E1)
            ),
            modifier = Modifier
                .height(40.dp)
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                }
                Text(
                    text = displayText,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color(0xFF0F172A) else Color(0xFF334155),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            options.forEach { option ->
                val isItemCurrent = option == displayText ||
                        (displayText.contains(option) && option.isNotBlank() && option != "Todas as Empresas" && option != "Todos os Colaboradores" && option != "Todos os Resultados" && option != "Todos os Tipos de EPI / EPC")
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            fontSize = 12.5.sp,
                            fontWeight = if (isItemCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isItemCurrent) Color(0xFF0284C7) else Color(0xFF1E293B)
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(if (isSelected) NavyPrimary else Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color(0xFF475569)
        )
    }
}

@Composable
private fun TestListItemCard(
    test: TestRecordEntity,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onOpenLaudo: () -> Unit,
    onOpenCertificado: () -> Unit,
    onOpenNiimbot: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFF0F9FF) else Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.8.dp else 1.dp,
            color = if (isSelected) ElectricBlue else Color(0xFFE2E8F0)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isSelectionMode) { onToggleSelect() }
            .testTag("test_item_card_${test.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = ElectricBlue,
                            uncheckedColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 4.dp)
                            .testTag("test_item_checkbox_${test.id}")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    StatusBadge(status = test.result)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = test.reportNumber,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = NavyPrimary
                    )
                }

                Text(
                    text = "${com.example.util.DateUtils.formatDate(test.testDate)} • ${test.testTime}",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${test.equipmentTag} • ${test.equipmentType.replace("_", " ").uppercase()} (Classe ${test.equipmentClass})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${test.clientName}${if (test.collaboratorName.isNotEmpty()) " • ${test.collaboratorName}" else ""}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val isMicro = test.currentUnit == "µA" || test.equipmentType in listOf("BASTAO_MANOBRA", "VARA_MANOBRA", "ATERRAMENTO_COM_BASTAO")
                    val leakageStr = if (isMicro) {
                        val v = if (test.measuredLeakageCurrent_mA % 1.0 == 0.0) test.measuredLeakageCurrent_mA.toInt().toString() else test.measuredLeakageCurrent_mA.toString()
                        "$v µA"
                    } else {
                        "${test.measuredLeakageCurrent_mA} mA"
                    }
                    Text(
                        text = "${test.appliedVoltage_kV} kV • $leakageStr",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = ElectricBlue
                    )
                    if (test.result == "APROVADO") {
                        Text(
                            text = "Validade: ${com.example.util.DateUtils.formatDate(test.retestDueDate)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // Linked Company & User / Technician
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Apartment,
                    contentDescription = null,
                    tint = SafetyOrange,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = test.companyName.ifBlank { "JVM Engenharia" },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SafetyOrange
                )
                val techOrUser = test.userName.ifBlank { test.technicianName }
                if (techOrUser.isNotBlank()) {
                    Text("•", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = techOrUser,
                        fontSize = 11.sp,
                        color = Color(0xFF475569)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Cód: ${test.validationCode}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF94A3B8)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onOpenNiimbot,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Etiqueta / JCPS", fontSize = 10.sp, color = SafetyOrange)
                    }

                    OutlinedButton(
                        onClick = onOpenLaudo,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Laudo", fontSize = 10.sp, color = ElectricBlue)
                    }

                    if (test.result == "APROVADO") {
                        Button(
                            onClick = onOpenCertificado,
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Certificado", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar Laudo", tint = NavyPrimary, modifier = Modifier.size(16.dp))
                    }

                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Compartilhar", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Excluir", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
