package com.example.ui.screens

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ClientEntity
import com.example.data.model.CompanyEntity
import com.example.data.model.UserEntity
import com.example.data.service.CnpjLookupService
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.appTextFieldColors
import com.example.ui.viewmodel.DielectricViewModel
import kotlinx.coroutines.launch

@Composable
fun ClientsScreen(
    viewModel: DielectricViewModel,
    modifier: Modifier = Modifier
) {
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val equipment by viewModel.equipment.collectAsStateWithLifecycle()
    val tests by viewModel.tests.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentCompany by viewModel.currentCompany.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<ClientEntity?>(null) }
    var clientToDelete by remember { mutableStateOf<ClientEntity?>(null) }

    val filteredClients = clients.filter { c ->
        searchQuery.isEmpty() ||
                c.razaoSocial.contains(searchQuery, ignoreCase = true) ||
                c.nomeFantasia.contains(searchQuery, ignoreCase = true) ||
                c.cnpj.contains(searchQuery, ignoreCase = true) ||
                c.city.contains(searchQuery, ignoreCase = true)
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
                    Text("Carteira de Clientes", fontWeight = FontWeight.Black, fontSize = 20.sp, color = NavyPrimary)
                    Text("Empresas tomadoras de serviços e plantas industriais", fontSize = 11.sp, color = Color(0xFF64748B))
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("clients_add_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Novo Cliente", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    placeholder = { Text("Buscar por Razão Social, Nome Fantasia ou CNPJ...", fontSize = 12.sp, color = Color.Black) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.Black) },
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        }

        // Clients List
        if (filteredClients.isEmpty()) {
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
                        Icon(imageVector = Icons.Default.Business, contentDescription = null, tint = Color.Black, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Nenhum cliente cadastrado", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                    }
                }
            }
        } else {
            items(filteredClients) { client ->
                val clientEqCount = equipment.count { it.clientId == client.id }
                val clientTestsCount = tests.count { it.clientId == client.id }
                ClientCard(
                    client = client,
                    eqCount = clientEqCount,
                    testsCount = clientTestsCount,
                    onEdit = { editingClient = client },
                    onDelete = { clientToDelete = client }
                )
            }
        }
    }

    clientToDelete?.let { target ->
        val eqCount = equipment.count { it.clientId == target.id }
        val testsCount = tests.count { it.clientId == target.id }
        com.example.ui.components.ConfirmDeleteDialog(
            title = "Excluir cliente?",
            itemName = target.razaoSocial.ifBlank { target.nomeFantasia },
            details = if (eqCount + testsCount > 0)
                "Este cliente tem $eqCount equipamento(s) e $testsCount laudo(s). Eles continuam cadastrados; só o cliente sai da lista."
            else null,
            onConfirm = {
                viewModel.deleteClient(target.id)
                clientToDelete = null
            },
            onDismiss = { clientToDelete = null }
        )
    }

    if (showAddDialog) {
        ClientFormDialog(
            client = null,
            existingClients = clients,
            currentUser = currentUser,
            currentCompany = currentCompany,
            onDismiss = { showAddDialog = false },
            onSave = { newClient ->
                viewModel.saveClient(newClient)
                showAddDialog = false
            }
        )
    }

    if (editingClient != null) {
        ClientFormDialog(
            client = editingClient,
            existingClients = clients,
            currentUser = currentUser,
            currentCompany = currentCompany,
            onDismiss = { editingClient = null },
            onSave = { updatedClient ->
                viewModel.saveClient(updatedClient)
                editingClient = null
            }
        )
    }
}

/** CNPJ no formato 00.000.000/0000-00 (mantém o texto original se não tiver 14 dígitos). */
private fun formatCnpj(raw: String): String {
    val d = raw.filter { it.isDigit() }
    return if (d.length == 14) "${d.substring(0, 2)}.${d.substring(2, 5)}.${d.substring(5, 8)}/${d.substring(8, 12)}-${d.substring(12)}" else raw.trim()
}

/** Endereço em uma linha, sem repetir a cidade quando ela já faz parte do endereço. */
private fun clientAddressLine(client: ClientEntity): String {
    val address = client.address.trim().trimEnd(',')
    val cityUf = listOf(client.city.trim(), client.state.trim()).filter { it.isNotBlank() }.joinToString(" - ")
    return when {
        address.isBlank() -> cityUf
        client.city.isNotBlank() && address.contains(client.city.trim(), ignoreCase = true) -> address
        cityUf.isBlank() -> address
        else -> "$address, $cityUf"
    }
}

/** Linha do cartão: ícone + texto em uma linha só (corta com "…"); vazio vira "—". */
@Composable
private fun ClientInfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text.ifBlank { "—" },
            fontSize = 11.5.sp,
            color = if (text.isBlank()) Color(0xFF94A3B8) else Color(0xFF1E293B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Cartão padronizado: todas as linhas têm uma linha só (texto longo termina em "…"),
 * então todos os cartões ficam com a mesma altura.
 *  1. Razão Social   2. CNPJ   3. Telefone   4. E-mail   5. Endereço   6. Totais e ações
 */
@Composable
private fun ClientCard(
    client: ClientEntity,
    eqCount: Int,
    testsCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val razaoSocial = client.razaoSocial.ifBlank { client.nomeFantasia }.trim()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = razaoSocial.uppercase(),
                fontWeight = FontWeight.Black,
                fontSize = 13.5.sp,
                color = NavyPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = "CNPJ  ${formatCnpj(client.cnpj).ifBlank { "não informado" }}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            ClientInfoLine(Icons.Default.Phone, client.contactPhone)
            ClientInfoLine(Icons.Default.Email, client.contactEmail)
            ClientInfoLine(Icons.Default.LocationOn, clientAddressLine(client))

            HorizontalDivider(color = Color(0xFFF1F5F9))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$eqCount Equipamentos • $testsCount Laudos",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 1
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar", tint = ElectricBlue, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Excluir", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ClientFormDialog(
    client: ClientEntity?,
    existingClients: List<ClientEntity> = emptyList(),
    currentUser: UserEntity? = null,
    currentCompany: CompanyEntity? = null,
    onDismiss: () -> Unit,
    onSave: (ClientEntity) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var razaoSocial by remember { mutableStateOf(client?.razaoSocial ?: "") }
    var nomeFantasia by remember { mutableStateOf(client?.nomeFantasia ?: "") }
    var cnpj by remember { mutableStateOf(client?.cnpj ?: "") }
    // texto + posição do cursor do campo com máscara (String pura deixava o cursor para trás)
    var cnpjField by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(cnpj, androidx.compose.ui.text.TextRange(cnpj.length))) }
    var contactName by remember { mutableStateOf(client?.contactName ?: "") }
    var contactEmail by remember { mutableStateOf(client?.contactEmail ?: "") }
    var contactPhone by remember { mutableStateOf(client?.contactPhone ?: "") }
    var address by remember { mutableStateOf(client?.address ?: "") }
    var city by remember { mutableStateOf(client?.city ?: "São Paulo") }
    var state by remember { mutableStateOf(client?.state ?: "SP") }

    var isLoadingCnpj by remember { mutableStateOf(false) }
    var lookupMessage by remember { mutableStateOf<String?>(null) }
    var lookupSuccess by remember { mutableStateOf(false) }
    var lastLookedUpDigits by remember { mutableStateOf(CnpjLookupService.cleanDigits(client?.cnpj ?: "")) }

    fun doLookup(rawCnpj: String, force: Boolean = false) {
        val digits = CnpjLookupService.cleanDigits(rawCnpj)
        if (digits.length != 14) return
        if (!force && digits == lastLookedUpDigits) return

        lastLookedUpDigits = digits
        isLoadingCnpj = true
        lookupMessage = "Consultando base da Receita Federal / CNPJ..."
        lookupSuccess = false

        coroutineScope.launch {
            // First check if already present in existing clients
            val existing = existingClients.find { CnpjLookupService.cleanDigits(it.cnpj) == digits }
            if (existing != null && client == null) {
                razaoSocial = existing.razaoSocial
                nomeFantasia = existing.nomeFantasia
                contactName = existing.contactName
                contactEmail = existing.contactEmail
                contactPhone = existing.contactPhone
                address = existing.address
                city = existing.city
                state = existing.state
                isLoadingCnpj = false
                lookupSuccess = true
                lookupMessage = "✓ Dados carregados do cadastro local existente"
                return@launch
            }

            val result = CnpjLookupService.lookupCnpj(digits)
            isLoadingCnpj = false
            result.onSuccess { data ->
                razaoSocial = data.razaoSocial
                nomeFantasia = data.nomeFantasia
                if (data.contactPhone.isNotEmpty()) contactPhone = data.contactPhone
                if (data.contactEmail.isNotEmpty()) contactEmail = data.contactEmail
                if (data.address.isNotEmpty()) address = data.address
                if (data.city.isNotEmpty()) city = data.city
                if (data.state.isNotEmpty()) state = data.state
                if (data.contactName.isNotEmpty()) contactName = data.contactName

                lookupSuccess = true
                lookupMessage = "✓ Dados preenchidos automaticamente via ${data.source}"
            }.onFailure { err ->
                lookupSuccess = false
                lookupMessage = "CNPJ não localizado automaticamente (${err.localizedMessage}). Preencha os campos abaixo."
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (client == null) "Novo Cliente" else "Editar Cliente",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black
                        )
                        Text(
                            "Digite o CNPJ para preenchimento automático",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                // CNPJ Input with Auto-Fill Feature
                OutlinedTextField(
                    value = cnpjField,
                    onValueChange = { newField ->
                        var rawDigits = CnpjLookupService.cleanDigits(newField.text)
                        var digitsBeforeCursor = CnpjLookupService.cleanDigits(newField.text.take(newField.selection.end)).length
                        // apagar um separador (". / -") apaga o dígito anterior a ele
                        if (newField.text.length < cnpjField.text.length &&
                            rawDigits == CnpjLookupService.cleanDigits(cnpjField.text) && digitsBeforeCursor > 0
                        ) {
                            rawDigits = rawDigits.removeRange(digitsBeforeCursor - 1, digitsBeforeCursor)
                            digitsBeforeCursor--
                        }
                        val formatted = CnpjLookupService.formatCnpj(rawDigits)
                        // A máscara insere ". / -": o cursor vai para depois do mesmo nº de dígitos
                        var cursor = 0
                        var seen = 0
                        while (cursor < formatted.length && seen < digitsBeforeCursor) {
                            if (formatted[cursor].isDigit()) seen++
                            cursor++
                        }
                        cnpjField = androidx.compose.ui.text.input.TextFieldValue(formatted, androidx.compose.ui.text.TextRange(cursor))
                        cnpj = formatted
                        val digits = CnpjLookupService.cleanDigits(formatted)
                        if (digits.length == 14 && digits != lastLookedUpDigits) {
                            doLookup(digits)
                        }
                    },
                    label = { Text("CNPJ * (Busca Automática da Receita)", color = Color.Black) },
                    placeholder = { Text("00.000.000/0000-00", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    trailingIcon = {
                        if (isLoadingCnpj) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                                color = NavyPrimary
                            )
                        } else {
                            IconButton(
                                onClick = { doLookup(cnpj, force = true) },
                                enabled = CnpjLookupService.cleanDigits(cnpj).length == 14
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Consultar CNPJ",
                                    tint = if (CnpjLookupService.cleanDigits(cnpj).length == 14) ElectricBlue else Color(0xFF94A3B8)
                                )
                            }
                        }
                    },
                    colors = appTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("client_cnpj_input")
                )

                // Lookup Status Banner
                if (lookupMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (lookupSuccess) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (lookupSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (lookupSuccess) StatusGreen else StatusRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = lookupMessage ?: "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (lookupSuccess) Color(0xFF065F46) else Color(0xFF991B1B)
                        )
                    }
                }

                OutlinedTextField(
                    value = razaoSocial,
                    onValueChange = { razaoSocial = it },
                    label = { Text("Razão Social *", color = Color.Black) },
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = nomeFantasia,
                    onValueChange = { nomeFantasia = it },
                    label = { Text("Nome Fantasia", color = Color.Black) },
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Contato / Gestor", color = Color.Black) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("Telefone / Celular", color = Color.Black) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = contactEmail,
                    onValueChange = { contactEmail = it },
                    label = { Text("E-mail de Contato", color = Color.Black) },
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Endereço / Planta Industrial", color = Color.Black) },
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("Cidade", color = Color.Black) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = state,
                        onValueChange = { state = it },
                        label = { Text("UF", color = Color.Black) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        val newC = ClientEntity(
                            id = client?.id ?: "cli-${System.currentTimeMillis()}",
                            razaoSocial = razaoSocial,
                            nomeFantasia = nomeFantasia.ifEmpty { razaoSocial },
                            cnpj = cnpj,
                            contactName = contactName,
                            contactEmail = contactEmail,
                            contactPhone = contactPhone,
                            address = address,
                            city = city,
                            state = state,
                            companyId = client?.companyId ?: (currentCompany?.id ?: currentUser?.companyId ?: "comp-jvm-01"),
                            companyName = client?.companyName ?: (currentCompany?.name ?: currentUser?.companyName ?: "JVM Engenharia")
                        )
                        onSave(newC)
                    },
                    enabled = razaoSocial.isNotBlank() && cnpj.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Salvar Cliente", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
