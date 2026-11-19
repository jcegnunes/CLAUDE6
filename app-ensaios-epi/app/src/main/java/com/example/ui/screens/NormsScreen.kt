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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.NormsEngine
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.viewmodel.DielectricViewModel

@Composable
fun NormsScreen(
    viewModel: DielectricViewModel,
    modifier: Modifier = Modifier
) {
    // critérios da plataforma web (atualiza após cada sincronização)
    val normsRevision = com.example.domain.NormsCatalog.revision.intValue
    val criteria = remember(normsRevision) { com.example.domain.NormsCatalog.criteria }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(SafetyOrange, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.MenuBook, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Normas Técnicas de Ensaio", fontWeight = FontWeight.Black, fontSize = 20.sp, color = NavyPrimary)
                    Text("Tabelas regulamentares ABNT, ASTM, IEC e critérios de aprovação", fontSize = 11.sp, color = Color(0xFF64748B))
                }
            }
        }

        // Tabela 4 ABNT NBR 16295 Luvas Isolantes
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ABNT NBR 16295 - Tabela 4 (Luvas de Borracha Isolante)",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = NavyPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFDBEAFE),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF93C5FD))
                        ) {
                            Text(
                                text = "Ensaio: 2 Luvas Simultâneas (Limite 2x)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Valores de referência unitários (1 luva). No ensaio em cuba realizado com 2 luvas simultâneas, o limite adotado é o dobro (2x o valor da tabela):",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )

                    // Table Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavyPrimary, RoundedCornerShape(6.dp))
                            .padding(vertical = 6.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Classe", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text("Uso AC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text("Prova", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text("280mm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text("360mm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text("410mm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    }

                    // Table Rows
                    NormsEngine.tabela4NBR16295.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFF1F5F9))
                                .padding(vertical = 6.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Cl. ${row.classe}", fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            Text("${row.tensaoUsoMaxAC_kV} kV", fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            Text("${row.tensaoProvaAC_kV} kV", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = ElectricBlue, modifier = Modifier.weight(1f))
                            Text(row.limite280mm?.let { "$it mA" } ?: "-", fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            Text(row.limite360mm?.let { "$it mA" } ?: "-", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF059669), modifier = Modifier.weight(1f))
                            Text(row.limite410mm?.let { "$it mA" } ?: "-", fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        }
                    }

                    // Nota Explicativa de 2 luvas simultâneas
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEFF6FF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Regra Operacional: Como o ensaio dielétrico em cuba é realizado no par (duas luvas simultâneas), o limite de corrente de fuga adotado no sistema é o dobro (2x) do valor unitário da tabela acima (Ex.: Classe 2 / 360 mm = 2 x 16,0 mA = 32,0 mA).",
                                fontSize = 10.sp,
                                color = Color(0xFF1E3A8A),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Critérios normativos em uso — os mesmos da plataforma JVM Dielectric Lab
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Critérios de Aprovação por Equipamento", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NavyPrimary)
                Text(
                    "Mesma lista da plataforma web (versão oficial + alterações da empresa), atualizada a cada sincronização. Estes valores são a base de cálculo dos ensaios.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        items(criteria, key = { it.id }) { norm ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (norm.companyId.isNotBlank()) Color(0xFFFCD34D) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(norm.normCode, fontWeight = FontWeight.Black, fontSize = 13.sp, color = NavyPrimary, modifier = Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFEFF6FF)) {
                            Text("Reensaio: ${norm.defaultRetestMonths} meses", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElectricBlue, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Text(norm.normName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text(
                        "Classe: ${norm.dielectricClass}" + (if (norm.companyId.isNotBlank()) " • versão da empresa" else " • versão oficial"),
                        fontSize = 10.sp,
                        color = if (norm.companyId.isNotBlank()) Color(0xFFB45309) else Color(0xFF64748B)
                    )
                    if (norm.notes.isNotBlank()) {
                        Text(norm.notes, fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    val leak = if (norm.maxLeakageCurrent % 1.0 == 0.0) norm.maxLeakageCurrent.toInt().toString() else norm.maxLeakageCurrent.toString()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tensão: ${norm.testVoltage_kV} kV ${norm.voltageType}", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricBlue)
                        Text("Fuga máx.: $leak ${norm.currentUnit}", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF059669))
                        Text("Tempo: ${norm.testDurationSeconds}s", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF64748B))
                    }
                }
            }
        }
    }
}
