package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppSettingEntity
import com.example.data.model.TestRecordEntity
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.util.PdfExporter
import com.example.util.WordExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog for Batch Emission (Emissão Agrupada) of multiple Technical Reports (Laudos)
 * and Certificates (Certificados de Rigidez Dielétrica NR-10) in PDF and Word (.doc).
 */
@Composable
fun BatchEmissionDialog(
    selectedTests: List<TestRecordEntity>,
    company: AppSettingEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var currentAction by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    val approvedTests = selectedTests.filter { it.result == "APROVADO" }
    val rejectedCount = selectedTests.size - approvedTests.size

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(NavyPrimary, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Emissão Agrupada em Lote",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = NavyPrimary
                            )
                            Text(
                                text = "${selectedTests.size} itens selecionados • Laudos e Certificados",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    IconButton(
                        onClick = { if (!isProcessing) onDismiss() },
                        enabled = !isProcessing
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = Color(0xFF64748B))
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Summary Chips & Items Preview
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE0F2FE), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${selectedTests.size} Laudos",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricBlue
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .background(StatusGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${approvedTests.size} Certificados (Aptos)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusGreen
                                    )
                                }

                                if (rejectedCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .background(StatusRed.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "$rejectedCount Reprovados",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusRed
                                        )
                                    }
                                }
                            }
                        }

                        // Compact horizontal/vertical list of selected items tags
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 90.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(selectedTests) { test ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(6.dp))
                                        .border(0.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    if (test.result == "APROVADO") StatusGreen else StatusRed,
                                                    CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${test.reportNumber} • ${test.equipmentTag} (${test.equipmentType.replace("_", " ")})",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF1E293B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = test.clientName,
                                        fontSize = 9.sp,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Processing Indicator if running
                if (isProcessing) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = ElectricBlue,
                                strokeWidth = 2.5.dp
                            )
                            Column {
                                Text(
                                    text = statusMessage.ifEmpty { "Gerando documentos agrupados..." },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = NavyPrimary
                                )
                                Text(
                                    text = "Por favor, aguarde a compilação completa dos arquivos.",
                                    fontSize = 10.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                    }
                }

                // Group 1: Laudos Técnicos Agrupados
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "1. LAUDOS TÉCNICOS AGRUPADOS (${selectedTests.size} ITENS)",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = NavyPrimary
                            )
                        }
                        Text(
                            text = "Gera um único documento compilado contendo todos os laudos técnicos completos em sequência, com capa técnica, tabulação e assinaturas digitais.",
                            fontSize = 10.sp,
                            color = Color(0xFF475569),
                            lineHeight = 13.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // PDF Actions
                            Button(
                                onClick = {
                                    if (!isProcessing) {
                                        isProcessing = true
                                        currentAction = "LAUDO_PDF_OPEN"
                                        statusMessage = "Gerando PDF dos ${selectedTests.size} laudos..."
                                        scope.launch {
                                            try {
                                                val file = withContext(Dispatchers.IO) {
                                                    PdfExporter.generateMultipleLaudosPdf(context, selectedTests, company)
                                                }
                                                PdfExporter.openPdf(context, file)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Erro ao gerar PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                isProcessing = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isProcessing,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("batch_laudos_pdf_button")
                            ) {
                                Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PDF Completo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (!isProcessing) {
                                        isProcessing = true
                                        currentAction = "LAUDO_PDF_SHARE"
                                        statusMessage = "Preparando compartilhamento PDF..."
                                        scope.launch {
                                            try {
                                                val file = withContext(Dispatchers.IO) {
                                                    PdfExporter.generateMultipleLaudosPdf(context, selectedTests, company)
                                                }
                                                PdfExporter.sharePdf(context, file, "Laudos Agrupados (${selectedTests.size} itens)")
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Erro ao compartilhar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                isProcessing = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isProcessing,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(0.9f).testTag("batch_laudos_pdf_share_button")
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Enviar PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Word Actions
                            Button(
                                onClick = {
                                    if (!isProcessing) {
                                        isProcessing = true
                                        currentAction = "LAUDO_DOC_OPEN"
                                        statusMessage = "Gerando Word (.doc) dos laudos..."
                                        scope.launch {
                                            try {
                                                val file = withContext(Dispatchers.IO) {
                                                    WordExporter.generateMultipleLaudosDoc(context, selectedTests, company)
                                                }
                                                WordExporter.openDoc(context, file)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Erro ao gerar Word: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                isProcessing = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isProcessing,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B579A)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("batch_laudos_doc_button")
                            ) {
                                Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Word (.doc)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (!isProcessing) {
                                        isProcessing = true
                                        currentAction = "LAUDO_DOC_SHARE"
                                        statusMessage = "Preparando compartilhamento Word..."
                                        scope.launch {
                                            try {
                                                val file = withContext(Dispatchers.IO) {
                                                    WordExporter.generateMultipleLaudosDoc(context, selectedTests, company)
                                                }
                                                WordExporter.shareDoc(context, file, "Laudos Agrupados Word (${selectedTests.size} itens)")
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Erro ao compartilhar Word: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                isProcessing = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isProcessing,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF185ABD)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(0.9f).testTag("batch_laudos_doc_share_button")
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Enviar Word", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Group 2: Certificados Agrupados
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, SafetyOrange.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = SafetyOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "2. CERTIFICADOS AGRUPADOS (${approvedTests.size} ITENS)",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = NavyPrimary
                                )
                            }
                            if (rejectedCount > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusRed, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Exceto $rejectedCount reprovados", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = StatusRed)
                                }
                            }
                        }
                        Text(
                            text = "Compila os certificados oficiais de ensaio e calibração dielétrica de todos os itens aprovados em um único arquivo, individualizados por página para impressão ou envio.",
                            fontSize = 10.sp,
                            color = Color(0xFF475569),
                            lineHeight = 13.5.sp
                        )

                        if (approvedTests.isEmpty()) {
                            Text(
                                text = "Nenhum dos itens selecionados possui resultado APROVADO para emissão de certificado.",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusRed
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Cert PDF
                                Button(
                                    onClick = {
                                        if (!isProcessing) {
                                            isProcessing = true
                                            currentAction = "CERT_PDF_OPEN"
                                            statusMessage = "Gerando PDF dos ${approvedTests.size} certificados..."
                                            scope.launch {
                                                try {
                                                    val file = withContext(Dispatchers.IO) {
                                                        PdfExporter.generateMultipleCertificadosPdf(context, approvedTests, company)
                                                    }
                                                    PdfExporter.openPdf(context, file)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Erro ao gerar Certificados PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                } finally {
                                                    isProcessing = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isProcessing,
                                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("batch_certs_pdf_button")
                                ) {
                                    Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PDF Certificados", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        if (!isProcessing) {
                                            isProcessing = true
                                            currentAction = "CERT_PDF_SHARE"
                                            statusMessage = "Preparando compartilhamento dos certificados..."
                                            scope.launch {
                                                try {
                                                    val file = withContext(Dispatchers.IO) {
                                                        PdfExporter.generateMultipleCertificadosPdf(context, approvedTests, company)
                                                    }
                                                    PdfExporter.sharePdf(context, file, "Certificados Agrupados (${approvedTests.size} itens)")
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Erro ao compartilhar certificados: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                } finally {
                                                    isProcessing = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isProcessing,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(0.9f).testTag("batch_certs_pdf_share_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Enviar PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                // Cert Word
                                Button(
                                    onClick = {
                                        if (!isProcessing) {
                                            isProcessing = true
                                            currentAction = "CERT_DOC_OPEN"
                                            statusMessage = "Gerando Word (.doc) dos certificados..."
                                            scope.launch {
                                                try {
                                                    val file = withContext(Dispatchers.IO) {
                                                        WordExporter.generateMultipleCertificadosDoc(context, approvedTests, company)
                                                    }
                                                    WordExporter.openDoc(context, file)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Erro ao gerar Certificados Word: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                } finally {
                                                    isProcessing = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isProcessing,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B579A)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("batch_certs_doc_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Word (.doc)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        if (!isProcessing) {
                                            isProcessing = true
                                            currentAction = "CERT_DOC_SHARE"
                                            statusMessage = "Preparando compartilhamento Word..."
                                            scope.launch {
                                                try {
                                                    val file = withContext(Dispatchers.IO) {
                                                        WordExporter.generateMultipleCertificadosDoc(context, approvedTests, company)
                                                    }
                                                    WordExporter.shareDoc(context, file, "Certificados Word (${approvedTests.size} itens)")
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Erro ao compartilhar Certificados Word: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                } finally {
                                                    isProcessing = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isProcessing,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF185ABD)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(0.9f).testTag("batch_certs_doc_share_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Enviar Word", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Footer close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { if (!isProcessing) onDismiss() },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Fechar")
                    }
                }
            }
        }
    }
}
