package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CompanyEntity
import com.example.data.model.DielectricClass
import com.example.data.model.EquipmentEntity
import com.example.data.model.EquipmentType
import com.example.data.model.UserEntity
import com.example.ui.components.QRCodeView
import com.example.ui.components.StatusBadge
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.appTextFieldColors
import com.example.ui.viewmodel.DielectricViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EquipmentScreen(
    viewModel: DielectricViewModel,
    modifier: Modifier = Modifier
) {
    val equipmentList by viewModel.equipment.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val currentCompany by viewModel.currentCompany.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val filterByMyCompany by viewModel.filterByMyCompany.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }
    var qrPreviewEquipment by remember { mutableStateOf<EquipmentEntity?>(null) }
    var equipmentToDelete by remember { mutableStateOf<EquipmentEntity?>(null) }

    val filteredList = equipmentList.filter { eq ->
        val matchesQuery = searchQuery.isEmpty() ||
                eq.tag.contains(searchQuery, ignoreCase = true) ||
                eq.clientName.contains(searchQuery, ignoreCase = true) ||
                eq.collaboratorName.contains(searchQuery, ignoreCase = true) ||
                eq.caNumber.contains(searchQuery, ignoreCase = true) ||
                eq.serialNumber.contains(searchQuery, ignoreCase = true)

        val matchesType = selectedTypeFilter == "ALL" || eq.type == selectedTypeFilter ||
            (selectedTypeFilter == "ATERRAMENTO_TEMPORARIO" && eq.type.startsWith("ATERRAMENTO", ignoreCase = true))
        val matchesMyCompany = !filterByMyCompany || currentCompany == null || eq.companyId == currentCompany?.id || eq.companyId.isBlank()
        matchesQuery && matchesType && matchesMyCompany
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
                Column {
                    Text("Inventário de EPIs & EPCs", fontWeight = FontWeight.Black, fontSize = 20.sp, color = NavyPrimary)
                    Text("Controle de validade, classes dielétricas e usuários", fontSize = 11.sp, color = Color(0xFF64748B))
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("equipment_add_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Novo EPI/EPC", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Search and Type Filter
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar por Tag, Cliente, Usuário ou CA...", fontSize = 12.sp, color = Color.Black) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.Black) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("equipment_search_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    // Type Chips Scroll
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TypeFilterPill("Todos (${equipmentList.size})", isSelected = selectedTypeFilter == "ALL") {
                            selectedTypeFilter = "ALL"
                        }
                        TypeFilterPill("Aterramento Temp.", isSelected = selectedTypeFilter == "ATERRAMENTO_TEMPORARIO") {
                            selectedTypeFilter = "ATERRAMENTO_TEMPORARIO"
                        }
                        TypeFilterPill("Luvas", isSelected = selectedTypeFilter == "LUVA_ISOLANTE") {
                            selectedTypeFilter = "LUVA_ISOLANTE"
                        }
                        TypeFilterPill("Mangas", isSelected = selectedTypeFilter == "MANGA_ISOLANTE") {
                            selectedTypeFilter = "MANGA_ISOLANTE"
                        }
                        TypeFilterPill("Mantas", isSelected = selectedTypeFilter == "MANTA_ISOLANTE") {
                            selectedTypeFilter = "MANTA_ISOLANTE"
                        }
                        TypeFilterPill("Bastões/Varas", isSelected = selectedTypeFilter == "BASTAO_MANOBRA" || selectedTypeFilter == "VARA_MANOBRA") {
                            selectedTypeFilter = "BASTAO_MANOBRA"
                        }
                        TypeFilterPill("Ferramentas", isSelected = selectedTypeFilter == "FERRAMENTA_ISOLADA") {
                            selectedTypeFilter = "FERRAMENTA_ISOLADA"
                        }
                        TypeFilterPill("Capacetes", isSelected = selectedTypeFilter == "CAPACETE_CLASSE_B") {
                            selectedTypeFilter = "CAPACETE_CLASSE_B"
                        }
                    }
                }
            }
        }

        // Equipment List
        if (filteredList.isEmpty()) {
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
                        Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = Color.Black, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Nenhum equipamento no inventário", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                        Text("Cadastre novos equipamentos ou realize um ensaio rápido.", fontSize = 11.sp, color = Color.Black)
                    }
                }
            }
        } else {
            items(filteredList) { eq ->
                EquipmentCard(
                    equipment = eq,
                    isExpired = eq.nextTestDueDate.isNotEmpty() && com.example.util.DateUtils.isExpired(eq.nextTestDueDate),
                    onStartTest = { viewModel.startNewTest(equipmentId = eq.id) },
                    onShowQR = { qrPreviewEquipment = eq },
                    onDelete = { equipmentToDelete = eq }
                )
            }
        }
    }

    // Add Equipment Dialog
    if (showAddDialog) {
        AddEquipmentDialog(
            clients = clients,
            equipmentList = equipmentList,
            currentCompany = currentCompany,
            currentUser = currentUser,
            onDismiss = { showAddDialog = false },
            onSave = { newEq ->
                viewModel.saveEquipment(newEq)
                showAddDialog = false
            }
        )
    }

    // QR Preview Dialog
    equipmentToDelete?.let { target ->
        com.example.ui.components.ConfirmDeleteDialog(
            title = "Excluir equipamento?",
            itemName = "TAG ${target.tag}",
            details = listOf(target.clientName, target.collaboratorName).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { null },
            onConfirm = {
                viewModel.deleteEquipment(target.id)
                equipmentToDelete = null
            },
            onDismiss = { equipmentToDelete = null }
        )
    }

    if (qrPreviewEquipment != null) {
        val eq = qrPreviewEquipment!!
        Dialog(onDismissRequest = { qrPreviewEquipment = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("QR Code de Rastreabilidade", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                    Text("TAG: ${eq.tag}", fontWeight = FontWeight.Black, fontSize = 15.sp, fontFamily = FontFamily.Monospace, color = Color.Black)

                    QRCodeView(data = "JVM-TAG:${eq.tag}|CLIENTE:${eq.clientName}|CLASSE:${eq.dielectricClass}", qrSize = 160.dp)

                    Text("${eq.type.replace("_", " ")} (Classe ${eq.dielectricClass})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("Colaborador: ${eq.collaboratorName.ifEmpty { "Geral" }}", fontSize = 11.sp, color = Color.Black)

                    Button(
                        onClick = { qrPreviewEquipment = null },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Fechar")
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeFilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(if (isSelected) NavyPrimary else Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color.Black
        )
    }
}

@Composable
private fun EquipmentCard(
    equipment: EquipmentEntity,
    isExpired: Boolean,
    onStartTest: () -> Unit,
    onShowQR: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = if (isExpired) "VENCIDO" else equipment.status)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = equipment.tag,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color.Black
                    )
                }

                IconButton(onClick = onShowQR, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.QrCode, contentDescription = "QR Code", tint = ElectricBlue)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${equipment.type.replace("_", " ")} • Classe ${equipment.dielectricClass}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "Fab: ${equipment.manufacturer.ifEmpty { "N/D" }} • SN: ${equipment.serialNumber.ifEmpty { "N/D" }} • CA: ${equipment.caNumber.ifEmpty { "N/D" }}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Black
                    )
                    if (equipment.collaboratorName.isNotEmpty()) {
                        Text(
                            text = "Usuário: ${equipment.collaboratorName} (${equipment.collaboratorRegistration})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Próximo Reensaio:", fontSize = 9.sp, color = Color.Black)
                    Text(
                        text = if (equipment.nextTestDueDate.isNotEmpty()) com.example.util.DateUtils.formatDate(equipment.nextTestDueDate) else "Pendente",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = if (isExpired) Color.Red else Color(0xFF059669)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // Linked Company & User Row
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
                    text = equipment.companyName.ifBlank { "JVM Engenharia" },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SafetyOrange
                )
                if (equipment.userName.isNotBlank()) {
                    Text("•", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = equipment.userName,
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
                Text(
                    text = equipment.clientName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onStartTest,
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Science, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Novo Ensaio", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Excluir", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEquipmentDialog(
    clients: List<com.example.data.model.ClientEntity>,
    equipmentList: List<EquipmentEntity> = emptyList(),
    currentCompany: CompanyEntity? = null,
    currentUser: UserEntity? = null,
    onDismiss: () -> Unit,
    onSave: (EquipmentEntity) -> Unit
) {
    var selectedClientId by remember { mutableStateOf(clients.firstOrNull()?.id ?: "") }
    var selectedType by remember { mutableStateOf(EquipmentType.LUVA_ISOLANTE) }
    var selectedClass by remember { mutableStateOf(DielectricClass.CLASS_2) }
    
    val initialClient = clients.find { it.id == selectedClientId }
    var tag by remember {
        mutableStateOf(
            EquipmentType.LUVA_ISOLANTE.suggestTag(
                clientName = initialClient?.nomeFantasia ?: initialClient?.razaoSocial,
                existingTags = equipmentList.map { it.tag }
            )
        )
    }
    var manufacturer by remember { mutableStateOf("") }
    var serialNumber by remember { mutableStateOf("") }
    var caNumber by remember { mutableStateOf("") }
    var collaboratorName by remember { mutableStateOf("") }
    var collaboratorReg by remember { mutableStateOf("") }
    var collaboratorSector by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cadastrar EPI / EPC", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                // Company & User Info Banner
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apartment,
                            contentDescription = null,
                            tint = SafetyOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentCompany?.name ?: "JVM Engenharia",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("•", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentUser?.name ?: "Responsável",
                            fontSize = 11.sp,
                            color = Color(0xFF334155)
                        )
                    }
                }

                // Client Selector
                var clientExpanded by remember { mutableStateOf(false) }
                val currentClient = clients.find { it.id == selectedClientId }
                ExposedDropdownMenuBox(
                    expanded = clientExpanded,
                    onExpandedChange = { clientExpanded = !clientExpanded }
                ) {
                    OutlinedTextField(
                        value = currentClient?.nomeFantasia ?: currentClient?.razaoSocial ?: "Selecione Cliente...",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Cliente", color = Color.Black) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clientExpanded) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = clientExpanded,
                        onDismissRequest = { clientExpanded = false }
                    ) {
                        clients.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.nomeFantasia.ifEmpty { c.razaoSocial }, color = Color.Black) },
                                onClick = {
                                    selectedClientId = c.id
                                    tag = selectedType.suggestTag(
                                        clientName = c.nomeFantasia.ifEmpty { c.razaoSocial },
                                        existingTags = equipmentList.map { it.tag }
                                    )
                                    clientExpanded = false
                                }
                            )
                        }
                    }
                }

                // Type & Class Selectors
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var typeExp by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = typeExp,
                        onExpandedChange = { typeExp = !typeExp },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedType.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo", color = Color.Black) },
                            colors = appTextFieldColors(),
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = typeExp, onDismissRequest = { typeExp = false }) {
                            EquipmentType.selectable.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.label, color = Color.Black) },
                                    onClick = {
                                        selectedType = t
                                        val cl = clients.find { it.id == selectedClientId }
                                        tag = t.suggestTag(
                                            clientName = cl?.nomeFantasia ?: cl?.razaoSocial,
                                            existingTags = equipmentList.map { it.tag }
                                        )
                                        typeExp = false
                                    }
                                )
                            }
                        }
                    }

                    var classExp by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = classExp,
                        onExpandedChange = { classExp = !classExp },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = "Classe ${selectedClass.label}",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Classe", color = Color.Black) },
                            colors = appTextFieldColors(),
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = classExp, onDismissRequest = { classExp = false }) {
                            DielectricClass.entries.forEach { c ->
                                DropdownMenuItem(text = { Text("Classe ${c.label}", color = Color.Black) }, onClick = { selectedClass = c; classExp = false })
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Tag / Patrimônio *", color = Color.Black) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val cl = clients.find { it.id == selectedClientId }
                                tag = selectedType.suggestTag(
                                    clientName = cl?.nomeFantasia ?: cl?.razaoSocial,
                                    existingTags = equipmentList.map { it.tag }
                                )
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
                            "Sugestão automática da plataforma (${selectedType.tagPrefix})",
                            fontSize = 10.sp,
                            color = Color(0xFF475569)
                        )
                    },
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = manufacturer, onValueChange = { manufacturer = it }, label = { Text("Fabricante", color = Color.Black) }, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = caNumber, onValueChange = { caNumber = it }, label = { Text("Nº CA", color = Color.Black) }, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = collaboratorName, onValueChange = { collaboratorName = it }, label = { Text("Colaborador Usuário", color = Color.Black) }, colors = appTextFieldColors(), modifier = Modifier.fillMaxWidth())

                Button(
                    onClick = {
                        val clientObj = clients.find { it.id == selectedClientId }
                        val newEq = EquipmentEntity(
                            id = "eq-${System.currentTimeMillis()}",
                            companyId = currentCompany?.id ?: currentUser?.companyId ?: "comp-jvm-01",
                            companyName = currentCompany?.name ?: currentUser?.companyName ?: "JVM Engenharia",
                            userId = currentUser?.id ?: "user-jvm-01",
                            userName = currentUser?.name ?: "Eng. João Carlos",
                            clientId = selectedClientId,
                            clientName = clientObj?.nomeFantasia ?: clientObj?.razaoSocial ?: "Cliente Geral",
                            type = selectedType.name,
                            tag = tag.ifEmpty { "TAG-${System.currentTimeMillis() % 10000}" },
                            qrCode = tag,
                            manufacturer = manufacturer,
                            serialNumber = serialNumber,
                            caNumber = caNumber,
                            dielectricClass = selectedClass.label,
                            collaboratorName = collaboratorName,
                            collaboratorRegistration = collaboratorReg,
                            collaboratorSector = collaboratorSector,
                            status = "EM_USO"
                        )
                        onSave(newEq)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Salvar Equipamento", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
