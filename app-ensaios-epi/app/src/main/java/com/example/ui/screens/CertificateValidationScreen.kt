package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.QRCodeView
import com.example.ui.components.StatusBadge
import com.example.ui.theme.appTextFieldColors
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.viewmodel.DielectricViewModel

@Composable
fun CertificateValidationScreen(
    viewModel: DielectricViewModel,
    modifier: Modifier = Modifier
) {
    val validationResult by viewModel.validationResult.collectAsStateWithLifecycle()
    val hasSearched by viewModel.validationSearched.collectAsStateWithLifecycle()

    var inputCode by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(ElectricBlue, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Validação Pública de Laudos & Certificados", fontWeight = FontWeight.Black, fontSize = 18.sp, color = NavyPrimary)
                    Text("Verificação de autenticidade digital rastreável conforme NR-10", fontSize = 11.sp, color = Color(0xFF64748B))
                }
            }
        }

        // Search Input Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Digite o Código Alfanumérico de Autenticidade (8 dígitos):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = inputCode,
                            onValueChange = { inputCode = it.uppercase() },
                            placeholder = { Text("Ex: A8F9C2D1", color = Color(0xFF94A3B8)) },
                            colors = appTextFieldColors(),
                            modifier = Modifier.weight(1f).testTag("validation_code_input"),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                if (inputCode.isNotEmpty()) {
                                    viewModel.searchValidationCode(inputCode.trim())
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("validation_search_button")
                        ) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        }
                    }

                    // Quick test shortcuts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Códigos de Exemplo:", fontSize = 10.sp, color = Color(0xFF64748B))
                        OutlinedButton(
                            onClick = {
                                inputCode = "A8F9C2D1"
                                viewModel.searchValidationCode("A8F9C2D1")
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("A8F9C2D1", fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                inputCode = "B3E7A1C9"
                                viewModel.searchValidationCode("B3E7A1C9")
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("B3E7A1C9", fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Validation Result Display
        if (hasSearched) {
            if (validationResult != null) {
                val test = validationResult!!
                val isApproved = test.result == "APROVADO"
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(2.dp, if (isApproved) StatusGreen else StatusRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isApproved) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (isApproved) StatusGreen else StatusRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (isApproved) "DOCUMENTO AUTÊNTICO & VÁLIDO" else "DOCUMENTO LOCALIZADO (REPROVADO)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isApproved) Color(0xFF065F46) else Color(0xFF991B1B)
                                        )
                                        Text("Código: ${test.validationCode}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF64748B))
                                    }
                                }

                                StatusBadge(status = test.result)
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0))

                            Text("Laudo Técnico: ${test.reportNumber}", fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = NavyPrimary)
                            Text("Cliente: ${test.clientName}", fontSize = 12.sp)
                            Text("Equipamento: ${test.equipmentTag} • ${test.equipmentType.replace("_", " ").uppercase()} (Classe ${test.equipmentClass})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Colaborador Usuário: ${test.collaboratorName.ifEmpty { "Uso Geral" }}", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text("Tensão de Ensaio: ${test.appliedVoltage_kV} kV • Corrente de Fuga: ${test.measuredLeakageCurrent_mA} mA", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = ElectricBlue)
                            Text("Próximo Reensaio Obrigatório: ${test.retestDueDate}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF059669))
                            Text("Responsável Técnico: ${test.techResponsibleName} (${test.techResponsibleCrea})", fontSize = 11.sp, color = Color(0xFF64748B))

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.openLaudo(test) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Visualizar Laudo Completo", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusRed, modifier = Modifier.size(36.dp))
                            Text("Código não localizado na base oficial", fontWeight = FontWeight.Black, fontSize = 13.sp, color = StatusRed)
                            Text("Verifique se digitou o código corretamente ou escaneie o QR Code original impresso no laudo ou certificado.", fontSize = 11.sp, color = Color(0xFF64748B), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
