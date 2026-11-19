package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LabInstrumentEntity
import com.example.ui.components.StatusBadge
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.appTextFieldColors
import com.example.ui.viewmodel.DielectricViewModel
import com.example.util.CertificateStorageManager
import com.example.util.DateUtils
import java.io.File

@Composable
fun LabInstrumentsScreen(
    viewModel: DielectricViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val instruments by viewModel.instruments.collectAsStateWithLifecycle()

    var showFormDialog by remember { mutableStateOf(false) }
    var selectedInstrumentForEdit by remember { mutableStateOf<LabInstrumentEntity?>(null) }
    var instrumentToDelete by remember { mutableStateOf<LabInstrumentEntity?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("TODOS") } // "TODOS", "VALIDOS", "A_VENCER", "VENCIDOS", "COM_CERTIFICADO"

    // Statistics
    val totalCount = instruments.size
    val expiredCount = instruments.count { it.calibrationDueDate.isNotEmpty() && DateUtils.isExpired(it.calibrationDueDate) }
    val expiringSoonCount = instruments.count {
        it.calibrationDueDate.isNotEmpty() && !DateUtils.isExpired(it.calibrationDueDate) && DateUtils.isExpiringSoon(it.calibrationDueDate, 30)
    }
    val validCount = totalCount - expiredCount - expiringSoonCount
    val withCertCount = instruments.count { it.calibrationCertificateUri.isNotBlank() }

    val filteredInstruments = remember(instruments, searchQuery, filterMode) {
        instruments.filter { inst ->
            val matchesSearch = searchQuery.isBlank() ||
                    inst.tag.contains(searchQuery, ignoreCase = true) ||
                    inst.type.contains(searchQuery, ignoreCase = true) ||
                    inst.manufacturer.contains(searchQuery, ignoreCase = true) ||
                    inst.model.contains(searchQuery, ignoreCase = true) ||
                    inst.serialNumber.contains(searchQuery, ignoreCase = true) ||
                    inst.calibrationCertNumber.contains(searchQuery, ignoreCase = true) ||
                    inst.calibrationLab.contains(searchQuery, ignoreCase = true)

            val isExp = inst.calibrationDueDate.isNotEmpty() && DateUtils.isExpired(inst.calibrationDueDate)
            val isSoon = inst.calibrationDueDate.isNotEmpty() && !isExp && DateUtils.isExpiringSoon(inst.calibrationDueDate, 30)

            val matchesFilter = when (filterMode) {
                "VALIDOS" -> !isExp && !isSoon
                "A_VENCER" -> isSoon
                "VENCIDOS" -> isExp
                "COM_CERTIFICADO" -> inst.calibrationCertificateUri.isNotBlank()
                else -> true
            }

            matchesSearch && matchesFilter
        }
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
                    Text(
                        text = "Padrões do Laboratório",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = NavyPrimary
                    )
                    Text(
                        text = "Equipamentos rastreados RBC, certificados de calibração e validades",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Button(
                    onClick = {
                        selectedInstrumentForEdit = null
                        showFormDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("instrument_add_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Novo Padrão", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Summary KPI Metrics Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiChip(label = "Total", count = totalCount, color = NavyPrimary)
                KpiChip(label = "Válidos", count = validCount, color = Color(0xFF16A34A))
                KpiChip(label = "A Vencer", count = expiringSoonCount, color = Color(0xFFD97706))
                KpiChip(label = "Vencidos", count = expiredCount, color = Color(0xFFDC2626))
                KpiChip(label = "Com Certificado", count = withCertCount, color = ElectricBlue)
            }
        }

        // Search and Filter Bar
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar padrão por tag, tipo, certificado, laboratório...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpar", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = appTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "TODOS" to "Todos ($totalCount)",
                            "VALIDOS" to "Válidos ($validCount)",
                            "A_VENCER" to "A Vencer ($expiringSoonCount)",
                            "VENCIDOS" to "Vencidos ($expiredCount)",
                            "COM_CERTIFICADO" to "Com Certificado ($withCertCount)"
                        ).forEach { (mode, title) ->
                            FilterChip(
                                selected = filterMode == mode,
                                onClick = { filterMode = mode },
                                label = { Text(title, fontSize = 11.sp, fontWeight = if (filterMode == mode) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NavyPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }

        // List of Instrument Cards
        if (filteredInstruments.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(36.dp))
                        Text("Nenhum instrumento padrão encontrado.", fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Text("Tente ajustar o filtro ou cadastre um novo padrão de ensaio.", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                }
            }
        } else {
            items(filteredInstruments, key = { it.id }) { inst ->
                val isExp = inst.calibrationDueDate.isNotEmpty() && DateUtils.isExpired(inst.calibrationDueDate)
                val isSoon = inst.calibrationDueDate.isNotEmpty() && !isExp && DateUtils.isExpiringSoon(inst.calibrationDueDate, 30)

                val statusLabel = when {
                    isExp -> "VENCIDO"
                    isSoon -> "A_VENCER"
                    else -> "VALID"
                }

                InstrumentCard(
                    instrument = inst,
                    status = statusLabel,
                    onEdit = {
                        selectedInstrumentForEdit = inst
                        showFormDialog = true
                    },
                    onDelete = {
                        instrumentToDelete = inst
                    },
                    onViewCertificate = {
                        if (inst.calibrationCertificateUri.isNotBlank()) {
                            CertificateStorageManager.openCertificate(context, inst.calibrationCertificateUri)
                        } else {
                            selectedInstrumentForEdit = inst
                            showFormDialog = true
                        }
                    },
                    onShareCertificate = {
                        if (inst.calibrationCertificateUri.isNotBlank()) {
                            CertificateStorageManager.shareCertificate(context, inst.calibrationCertificateUri, inst.tag)
                        }
                    }
                )
            }
        }
    }

    // Add / Edit Instrument Dialog
    if (showFormDialog) {
        InstrumentFormDialog(
            instrument = selectedInstrumentForEdit,
            onDismiss = { showFormDialog = false },
            onSave = { savedInst ->
                viewModel.saveInstrument(savedInst)
                showFormDialog = false
                val action = if (selectedInstrumentForEdit == null) "cadastrado" else "atualizado"
                Toast.makeText(context, "Padrão ${savedInst.tag} $action com sucesso!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Delete Confirmation Dialog
    if (instrumentToDelete != null) {
        val inst = instrumentToDelete!!
        AlertDialog(
            onDismissRequest = { instrumentToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Excluir Instrumento Padrão?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Deseja realmente remover o padrão abaixo do banco de dados?")
                    Text(
                        "${inst.tag} - ${inst.type} (${inst.manufacturer} ${inst.model})",
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary,
                        fontSize = 13.sp
                    )
                    if (inst.calibrationCertificateUri.isNotBlank()) {
                        Text(
                            "O arquivo do certificado anexado também deixará de estar vinculado.",
                            fontSize = 11.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInstrument(inst.id)
                        instrumentToDelete = null
                        Toast.makeText(context, "Instrumento ${inst.tag} excluído.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Excluir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { instrumentToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun KpiChip(label: String, count: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF475569))
            Text(
                count.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}

@Composable
private fun InstrumentCard(
    instrument: LabInstrumentEntity,
    status: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewCertificate: () -> Unit,
    onShareCertificate: () -> Unit
) {
    val hasCertificate = instrument.calibrationCertificateUri.isNotBlank()
    val isPdf = hasCertificate && CertificateStorageManager.isPdf(instrument.calibrationCertificateUri)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Status, Tag and RBC Seal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = status)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = instrument.tag,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = NavyPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0xFFE0F2FE), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "RBC Rastreável",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricBlue
                    )
                }
            }

            // Instrument Type and Model
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = instrument.type,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "${instrument.manufacturer} • Modelo: ${instrument.model}",
                    fontSize = 12.sp,
                    color = Color(0xFF334155)
                )
            }

            // Serial Number & Asset Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Nº Série: ${instrument.serialNumber}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF64748B)
                )
                if (instrument.assetNumber.isNotBlank()) {
                    Text(
                        text = "Patrimônio: ${instrument.assetNumber}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Calibration Details Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Certificado RBC:", fontSize = 9.sp, color = Color(0xFF64748B))
                            Text(
                                text = instrument.calibrationCertNumber.ifEmpty { "Não informado" },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = NavyPrimary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Validade da Calibração:", fontSize = 9.sp, color = Color(0xFF64748B))
                            val isExpired = instrument.calibrationDueDate.isNotEmpty() && DateUtils.isExpired(instrument.calibrationDueDate)
                            val isSoon = instrument.calibrationDueDate.isNotEmpty() && !isExpired && DateUtils.isExpiringSoon(instrument.calibrationDueDate, 30)
                            val valColor = when {
                                isExpired -> Color(0xFFDC2626)
                                isSoon -> Color(0xFFD97706)
                                else -> Color(0xFF16A34A)
                            }
                            Text(
                                text = if (instrument.calibrationDueDate.isNotEmpty()) DateUtils.formatDate(instrument.calibrationDueDate) else "Não informada",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = valColor
                            )
                        }
                    }

                    if (instrument.calibrationLab.isNotBlank()) {
                        Text(
                            text = "Lab: ${instrument.calibrationLab}",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    if (instrument.maxVoltageCapacity_kV > 0.0) {
                        Text(
                            text = "Capacidade Máxima: ${instrument.maxVoltageCapacity_kV} kV",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }

            // CALIBRATION CERTIFICATE SECTION (Document Attachment)
            if (hasCertificate) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
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
                            Icon(
                                imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (isPdf) Color(0xFFDC2626) else Color(0xFF0284C7),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        "Certificado Anexado",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF16A34A)
                                    )
                                }
                                Text(
                                    text = instrument.calibrationCertificateFileName.ifEmpty { "Certificado_Digital" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = onViewCertificate,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Abrir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = onShareCertificate,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Compartilhar Certificado",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFFBEB),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Sem certificado digital anexado",
                                fontSize = 11.sp,
                                color = Color(0xFF92400E)
                            )
                        }

                        OutlinedButton(
                            onClick = onEdit,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Incluir", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // Bottom Actions: Edit Instrument and Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Editar Equipamento", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InstrumentFormDialog(
    instrument: LabInstrumentEntity?,
    onDismiss: () -> Unit,
    onSave: (LabInstrumentEntity) -> Unit
) {
    val context = LocalContext.current
    val isEdit = instrument != null

    var tag by remember { mutableStateOf(instrument?.tag ?: "") }
    var type by remember { mutableStateOf(instrument?.type ?: "Hipot Dielétrico AC/DC") }
    var manufacturer by remember { mutableStateOf(instrument?.manufacturer ?: "Megabras Metrologia") }
    var model by remember { mutableStateOf(instrument?.model ?: "Hipot 50kV-AC") }
    var serialNumber by remember { mutableStateOf(instrument?.serialNumber ?: "") }
    var assetNumber by remember { mutableStateOf(instrument?.assetNumber ?: "") }
    var certNumber by remember { mutableStateOf(instrument?.calibrationCertNumber ?: "CAL-RBC-2026-") }
    var calLab by remember { mutableStateOf(instrument?.calibrationLab ?: "LabMetrologia RBC Acreditado Cgcre") }
    var calDate by remember { mutableStateOf(instrument?.calibrationDate ?: DateUtils.today()) }
    var calDueDate by remember {
        mutableStateOf(
            instrument?.calibrationDueDate?.ifEmpty { null }
                ?: DateUtils.calculateRetestDueDate(instrument?.calibrationDate ?: DateUtils.today(), 12)
        )
    }
    var maxVoltage by remember { mutableStateOf((instrument?.maxVoltageCapacity_kV ?: 50.0).toString()) }
    var notes by remember { mutableStateOf(instrument?.notes ?: "") }

    // Certificate File Attachment State
    var certUri by remember { mutableStateOf(instrument?.calibrationCertificateUri ?: "") }
    var certFileName by remember { mutableStateOf(instrument?.calibrationCertificateFileName ?: "") }

    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    // File Picker Launcher for PDFs & Images
    val pickDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val safeTag = tag.ifBlank { "PADRAO" }
            val result = CertificateStorageManager.saveCertificateFromUri(context, uri, safeTag)
            if (result != null) {
                certUri = result.first
                certFileName = result.second
                Toast.makeText(context, "Certificado anexado: ${result.second}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Erro ao carregar arquivo de certificado.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Camera Picture Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile != null && tempCameraFile!!.exists()) {
            val safeTag = tag.ifBlank { "PADRAO" }
            val result = CertificateStorageManager.saveCertificateFromPhoto(context, tempCameraFile!!, safeTag)
            if (result != null) {
                certUri = result.first
                certFileName = result.second
                Toast.makeText(context, "Foto do certificado salva e vinculada!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Erro ao salvar foto do certificado.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val photosDir = File(context.cacheDir, "photos")
                if (!photosDir.exists()) photosDir.mkdirs()
                val photoFile = File(photosDir, "cert_cam_${System.currentTimeMillis()}.jpg")
                tempCameraFile = photoFile
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao abrir câmera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Permissão de câmera necessária para fotografar o certificado.", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isEdit) "Editar Instrumento Padrão" else "Cadastrar Instrumento Padrão",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = NavyPrimary
                        )
                        Text(
                            text = "Padrão rastreado do laboratório com certificado",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Basic Identification
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Tag de Identificação *", color = Color(0xFF0F172A)) },
                    placeholder = { Text("Ex: PAD-HIP-01, PAD-THG-01") },
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Type Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tipo de Padrão:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Hipot Dielétrico AC/DC",
                            "Termohigrômetro Digital",
                            "Microamperímetro / mA",
                            "Miliohmímetro Digital",
                            "Voltímetro de Alta Tensão"
                        ).forEach { suggestedType ->
                            FilterChip(
                                selected = type == suggestedType,
                                onClick = { type = suggestedType },
                                label = { Text(suggestedType, fontSize = 10.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Denominação do Tipo *", color = Color(0xFF0F172A)) },
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manufacturer,
                        onValueChange = { manufacturer = it },
                        label = { Text("Fabricante", color = Color(0xFF0F172A)) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Modelo", color = Color(0xFF0F172A)) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = serialNumber,
                        onValueChange = { serialNumber = it },
                        label = { Text("Nº Série", color = Color(0xFF0F172A)) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = assetNumber,
                        onValueChange = { assetNumber = it },
                        label = { Text("Nº Patrimônio", color = Color(0xFF0F172A)) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Metrological & Calibration Data
                Text(
                    text = "Dados da Calibração RBC",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = NavyPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                OutlinedTextField(
                    value = certNumber,
                    onValueChange = { certNumber = it },
                    label = { Text("Nº Certificado RBC / Calibração *", color = Color(0xFF0F172A)) },
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = calLab,
                    onValueChange = { calLab = it },
                    label = { Text("Laboratório de Calibração Acreditado", color = Color(0xFF0F172A)) },
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = calDate,
                        onValueChange = { calDate = it },
                        label = { Text("Data Calibração", color = Color(0xFF0F172A)) },
                        placeholder = { Text("dd/mm/aaaa") },
                        colors = appTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = calDueDate,
                        onValueChange = { calDueDate = it },
                        label = { Text("Validade (dd/mm/aaaa)", color = Color(0xFF0F172A)) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick Validity Helpers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ajuste rápido:", fontSize = 10.sp, color = Color(0xFF64748B))
                    OutlinedButton(
                        onClick = {
                            calDueDate = DateUtils.calculateRetestDueDate(calDate.ifEmpty { DateUtils.today() }, 12)
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("+ 12 Meses", fontSize = 10.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            calDueDate = DateUtils.calculateRetestDueDate(calDate.ifEmpty { DateUtils.today() }, 24)
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("+ 24 Meses", fontSize = 10.sp)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxVoltage,
                        onValueChange = { maxVoltage = it },
                        label = { Text("Capacidade Máx (kV)", color = Color(0xFF0F172A)) },
                        colors = appTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }

                // ==========================================
                // ATTACH CALIBRATION CERTIFICATE (DOCUMENT / PHOTO)
                // ==========================================
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Certificado de Calibração (Arquivo Digital)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = NavyPrimary
                            )
                        }

                        Text(
                            "Anexe o arquivo oficial em PDF ou fotografe o certificado físico de calibração emitido pelo laboratório RBC.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )

                        // If already has an attached certificate
                        if (certUri.isNotBlank()) {
                            val isPdf = CertificateStorageManager.isPdf(certUri)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description,
                                                contentDescription = null,
                                                tint = if (isPdf) Color(0xFFDC2626) else Color(0xFF0284C7),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = certFileName.ifEmpty { "Certificado_Anexado" },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                maxLines = 1
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                certUri = ""
                                                certFileName = ""
                                                Toast.makeText(context, "Certificado desvinculado.", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remover",
                                                tint = Color(0xFFDC2626),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = { CertificateStorageManager.openCertificate(context, certUri) },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Visualizar", fontSize = 10.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { CertificateStorageManager.shareCertificate(context, certUri, tag.ifEmpty { "PADRAO" }) },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Compartilhar", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Buttons to attach or replace file
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    pickDocumentLauncher.launch(arrayOf("application/pdf", "image/*"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (certUri.isNotBlank()) "Substituir Arquivo" else "Selecionar PDF / Foto",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Tirar Foto",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações Técnicas / Metrológicas", color = Color(0xFF0F172A)) },
                    placeholder = { Text("Ex: Incerteza expandida U=0.2%, fator k=2") },
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = Color(0xFF64748B))
                    }

                    Button(
                        onClick = {
                            val cleanTag = tag.trim().ifEmpty {
                                "PAD-${System.currentTimeMillis() % 1000}"
                            }
                            val maxV = maxVoltage.replace(",", ".").toDoubleOrNull() ?: 50.0

                            val finalInst = LabInstrumentEntity(
                                id = instrument?.id ?: "inst-${System.currentTimeMillis()}",
                                tag = cleanTag,
                                type = type.trim().ifEmpty { "Instrumento Padrão" },
                                manufacturer = manufacturer.trim(),
                                model = model.trim(),
                                serialNumber = serialNumber.trim().ifEmpty { "S/N" },
                                assetNumber = assetNumber.trim(),
                                measurementRange = instrument?.measurementRange ?: "",
                                calibrationCertNumber = certNumber.trim().ifEmpty { "CAL-RBC" },
                                calibrationDate = calDate.trim().ifEmpty { DateUtils.today() },
                                calibrationDueDate = calDueDate.trim(),
                                calibrationExpiryDate = calDueDate.trim(),
                                calibrationLab = calLab.trim(),
                                maxVoltageCapacity_kV = maxV,
                                active = true,
                                notes = notes.trim(),
                                calibrationCertificateUri = certUri,
                                calibrationCertificateFileName = certFileName
                            )
                            onSave(finalInst)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isEdit) SafetyOrange else NavyPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isEdit) Icons.Default.Edit else Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isEdit) "Salvar Alterações" else "Salvar Padrão",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
