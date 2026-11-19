package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppSettingEntity
import com.example.data.model.ClientEntity
import com.example.data.model.CompanyEntity
import com.example.data.model.ServiceOrderEntity
import com.example.data.model.UserEntity
import com.example.ui.components.OsPhotoFolderDialog
import com.example.ui.components.StatusBadge
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.appTextFieldColors
import com.example.util.PdfExporter
import com.example.ui.viewmodel.DielectricViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ServiceOrdersScreen(
    viewModel: DielectricViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val orders by viewModel.serviceOrders.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val tests by viewModel.tests.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentCompany by viewModel.currentCompany.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingOS by remember { mutableStateOf<ServiceOrderEntity?>(null) }
    var osToDelete by remember { mutableStateOf<ServiceOrderEntity?>(null) }
    var selectedOSForFolder by remember { mutableStateOf<String?>(null) }

    val filteredOrders = orders.filter { os ->
        searchQuery.isEmpty() ||
                os.osNumber.contains(searchQuery, ignoreCase = true) ||
                os.clientName.contains(searchQuery, ignoreCase = true) ||
                os.artNumber.contains(searchQuery, ignoreCase = true) ||
                os.technicianName.contains(searchQuery, ignoreCase = true)
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
                    Text("Ordens de Serviço (OS)", fontWeight = FontWeight.Black, fontSize = 20.sp, color = NavyPrimary)
                    Text("Contratos de ensaio, ARTs e lotes de inspeção", fontSize = 11.sp, color = Color(0xFF64748B))
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("os_add_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nova OS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Search Bar
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por Nº de OS, Cliente ou ART...", fontSize = 12.sp) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        }

        // Orders List
        if (filteredOrders.isEmpty()) {
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
                        Icon(imageVector = Icons.Default.Assignment, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Nenhuma ordem de serviço cadastrada", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF64748B))
                        Text("Abra uma nova OS para organizar ensaios por lote e cliente.", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }
            }
        } else {
            items(filteredOrders) { os ->
                val osTests = tests.filter { it.serviceOrderId == os.id || it.serviceOrderNumber == os.osNumber }
                ServiceOrderCard(
                    order = os,
                    testsCount = osTests.size,
                    onStartTest = { viewModel.startNewTest(osId = os.id) },
                    onViewPhotos = { selectedOSForFolder = os.osNumber },
                    onExportReport = {
                        if (osTests.isEmpty()) {
                            Toast.makeText(context, "Nenhum laudo registrado para a ${os.osNumber}", Toast.LENGTH_SHORT).show()
                        } else {
                            coroutineScope.launch {
                                try {
                                    val client = clients.find { it.id == os.clientId || it.razaoSocial == os.clientName || it.nomeFantasia == os.clientName }
                                    val pdf = PdfExporter.generateRelatorioCompletoPdf(
                                        context = context,
                                        tests = osTests,
                                        client = client,
                                        company = settings ?: AppSettingEntity(),
                                        serviceOrder = os
                                    )
                                    PdfExporter.openPdf(context, pdf)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Erro ao emitir relatório: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    onEdit = { editingOS = os },
                    onDelete = { osToDelete = os }
                )
            }
        }
    }

    if (showAddDialog) {
        ServiceOrderFormDialog(
            order = null,
            clients = clients,
            currentUser = currentUser,
            currentCompany = currentCompany,
            onDismiss = { showAddDialog = false },
            onSave = { newOs ->
                viewModel.saveServiceOrder(newOs)
                showAddDialog = false
                Toast.makeText(context, "Ordem de Serviço ${newOs.osNumber} criada com sucesso!", Toast.LENGTH_SHORT).show()
            },
            onSaveClient = { newClient ->
                viewModel.saveClient(newClient)
            },
            onGenerateOSNumber = { viewModel.generateNextOSNumber() }
        )
    }

    if (editingOS != null) {
        val targetOS = editingOS!!
        ServiceOrderFormDialog(
            order = targetOS,
            clients = clients,
            currentUser = currentUser,
            currentCompany = currentCompany,
            onDismiss = { editingOS = null },
            onSave = { updatedOs ->
                viewModel.saveServiceOrder(updatedOs)
                editingOS = null
                Toast.makeText(context, "Ordem de Serviço ${updatedOs.osNumber} atualizada com sucesso!", Toast.LENGTH_SHORT).show()
            },
            onSaveClient = { newClient ->
                viewModel.saveClient(newClient)
            },
            onGenerateOSNumber = { viewModel.generateNextOSNumber() }
        )
    }

    if (osToDelete != null) {
        val os = osToDelete!!
        val osTestsCount = tests.count { it.serviceOrderId == os.id || it.serviceOrderNumber == os.osNumber }
        AlertDialog(
            onDismissRequest = { osToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Excluir Ordem de Serviço", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tem certeza que deseja excluir a Ordem de Serviço ${os.osNumber}?")
                    Text("Cliente: ${os.clientName}", fontSize = 12.sp, color = Color(0xFF475569))
                    if (os.artNumber.isNotBlank()) {
                        Text("ART: ${os.artNumber}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF64748B))
                    }
                    if (osTestsCount > 0) {
                        Text(
                            "Atenção: Esta OS possui $osTestsCount laudo(s) associado(s). Ao excluir a OS, os laudos perderão o vínculo.",
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
                        viewModel.deleteServiceOrder(os.id)
                        osToDelete = null
                        Toast.makeText(context, "Ordem de Serviço ${os.osNumber} excluída!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Excluir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { osToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (selectedOSForFolder != null) {
        val osNum = selectedOSForFolder!!
        OsPhotoFolderDialog(
            osNumber = osNum,
            currentPhotos = emptyList(),
            onDismiss = { selectedOSForFolder = null },
            onSyncAll = {}
        )
    }
}

@Composable
private fun ServiceOrderCard(
    order: ServiceOrderEntity,
    testsCount: Int,
    onStartTest: () -> Unit,
    onViewPhotos: () -> Unit,
    onExportReport: () -> Unit,
    onEdit: () -> Unit,
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
                    StatusBadge(status = order.status)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = order.osNumber,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = NavyPrimary
                    )
                }

                Text(
                    text = com.example.util.DateUtils.formatDate(order.openDate),
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }

            Text(
                text = order.clientName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            if (order.artNumber.isNotEmpty()) {
                Text(
                    text = "Anotação de Responsabilidade Técnica (ART): ${order.artNumber}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97706)
                )
            }

            Text(
                text = "Responsável: ${order.technicianName} • ${order.description}",
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )

            HorizontalDivider(color = Color(0xFFF1F5F9))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$testsCount Laudo(s) Vinculado(s)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricBlue
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onExportReport,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyPrimary)
                    ) {
                        Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Relatório", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onViewPhotos,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue)
                    ) {
                        Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fotos", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp).testTag("os_card_edit_button_${order.osNumber}"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar OS", modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Editar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onStartTest,
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Science, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ensaio", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp).testTag("os_card_delete_button_${order.osNumber}")
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Excluir OS", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceOrderFormDialog(
    order: ServiceOrderEntity? = null,
    clients: List<ClientEntity>,
    currentUser: UserEntity? = null,
    currentCompany: CompanyEntity? = null,
    onDismiss: () -> Unit,
    onSave: (ServiceOrderEntity) -> Unit,
    onSaveClient: (ClientEntity) -> Unit,
    onGenerateOSNumber: suspend () -> String
) {
    val isEdit = order != null
    val coroutineScope = rememberCoroutineScope()
    var selectedClientId by remember { mutableStateOf(order?.clientId ?: clients.firstOrNull()?.id ?: "") }
    var osNumber by remember { mutableStateOf(order?.osNumber ?: "") }
    var artNumber by remember { mutableStateOf(order?.artNumber ?: "ART-2026-DF-7841") }
    var technicianName by remember { mutableStateOf(order?.technicianName ?: currentUser?.name ?: "Eng. João Carlos Evaristo Guedes Nunes") }
    var description by remember { mutableStateOf(order?.description ?: "Ensaio Dielétrico Semestral Periódico NR-10 em EPIs de Alta Tensão") }
    var status by remember { mutableStateOf(order?.status ?: "ABERTA") }
    var showNewClientDialog by remember { mutableStateOf(false) }

    LaunchedEffect(order?.id) {
        if (!isEdit && osNumber.isBlank()) {
            osNumber = onGenerateOSNumber()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isEdit) "Editar Ordem de Serviço" else "Criar Nova Ordem de Serviço",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = NavyPrimary
                        )
                        if (isEdit && order != null) {
                            Text(
                                text = "Código ID: ${order.id}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                // Client Selector with Quick CNPJ Creator
                var clientExpanded by remember { mutableStateOf(false) }
                val currentClient = clients.find { it.id == selectedClientId }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExposedDropdownMenuBox(
                        expanded = clientExpanded,
                        onExpandedChange = { clientExpanded = !clientExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = currentClient?.nomeFantasia?.ifEmpty { currentClient.razaoSocial }
                                ?: currentClient?.razaoSocial ?: "Selecione o Cliente...",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cliente / Contratante *", color = Color.Black) },
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
                                    text = {
                                        Column {
                                            Text(c.nomeFantasia.ifEmpty { c.razaoSocial }, fontWeight = FontWeight.Bold, color = Color.Black)
                                            if (c.cnpj.isNotBlank()) {
                                                Text(c.cnpj, fontSize = 10.sp, color = Color(0xFF64748B))
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedClientId = c.id
                                        clientExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { showNewClientDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("+ CNPJ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (showNewClientDialog) {
                    ClientFormDialog(
                        client = null,
                        existingClients = clients,
                        onDismiss = { showNewClientDialog = false },
                        onSave = { newClient ->
                            onSaveClient(newClient)
                            selectedClientId = newClient.id
                            showNewClientDialog = false
                        }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = osNumber,
                        onValueChange = { osNumber = it },
                        label = { Text("Nº da OS *", color = Color.Black) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.weight(1f).testTag("dialog_os_number_input")
                    )
                    OutlinedTextField(
                        value = artNumber,
                        onValueChange = { artNumber = it },
                        label = { Text("Nº da ART", color = Color.Black) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.weight(1f).testTag("dialog_art_number_input")
                    )
                }

                // Status Selector
                var statusExpanded by remember { mutableStateOf(false) }
                val statusOptions = listOf("ABERTA", "EM_ANDAMENTO", "CONCLUIDA", "CANCELADA")
                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = !statusExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = status,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Status da OS", color = Color.Black) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        statusOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt, fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    status = opt
                                    statusExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = technicianName,
                    onValueChange = { technicianName = it },
                    label = { Text("Responsável Técnico / Executor", color = Color.Black) },
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("dialog_tech_name_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Escopo / Descrição dos Serviços", color = Color.Black) },
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("dialog_desc_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            val clientObj = clients.find { it.id == selectedClientId }
                            val today = com.example.util.DateUtils.today()
                            val targetId = order?.id ?: "os-${System.currentTimeMillis()}"
                            val openDate = order?.openDate ?: today
                            val scheduledDate = order?.scheduledDate ?: today

                            val updatedOs = ServiceOrderEntity(
                                id = targetId,
                                osNumber = osNumber.trim().ifEmpty { order?.osNumber ?: "OS-${System.currentTimeMillis() % 10000}" },
                                clientId = selectedClientId,
                                clientName = clientObj?.nomeFantasia?.ifEmpty { clientObj.razaoSocial } ?: clientObj?.razaoSocial ?: order?.clientName ?: "Cliente Geral",
                                artNumber = artNumber.trim(),
                                technicianId = order?.technicianId ?: (currentUser?.id ?: ""),
                                technicianName = technicianName.trim().ifEmpty { currentUser?.name ?: "Engenheiro Responsável" },
                                openDate = openDate,
                                scheduledDate = scheduledDate,
                                status = status,
                                description = description.trim(),
                                companyId = order?.companyId ?: (currentCompany?.id ?: currentUser?.companyId ?: "comp-jvm-01"),
                                companyName = order?.companyName ?: (currentCompany?.name ?: currentUser?.companyName ?: "JVM Engenharia")
                            )
                            onSave(updatedOs)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        modifier = Modifier.weight(1.5f).height(46.dp).testTag("save_service_order_button")
                    ) {
                        Text(if (isEdit) "Salvar Alterações" else "Salvar Ordem de Serviço", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AddServiceOrderDialog(
    clients: List<ClientEntity>,
    onDismiss: () -> Unit,
    onSave: (ServiceOrderEntity) -> Unit,
    onSaveClient: (ClientEntity) -> Unit,
    onGenerateOSNumber: suspend () -> String
) {
    ServiceOrderFormDialog(
        order = null,
        clients = clients,
        onDismiss = onDismiss,
        onSave = onSave,
        onSaveClient = onSaveClient,
        onGenerateOSNumber = onGenerateOSNumber
    )
}
