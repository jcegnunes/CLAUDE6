package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppSettingEntity
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.appTextFieldColors

val DEFAULT_REPORT_INTRO = """O presente Relatório Técnico de Ensaios Dielétricos consolida os resultados dos ensaios de rigidez dielétrica e medição de corrente de fuga realizados no lote de Equipamentos de Proteção Individual (EPI), Equipamentos de Proteção Coletiva (EPC) e Ferramental Isolado pertencentes à empresa solicitante.

A execução periódica destes ensaios atende estritamente às exigências da Norma Regulamentadora nº 10 (Segurança em Instalações e Serviços em Eletricidade - Portaria MTE nº 3.214/78), em especial aos subitens 10.4.3.1 e 10.7.8, os quais estabelecem a obrigatoriedade de ensaios elétricos periódicos em todos os equipamentos, ferramentas e dispositivos isolantes utilizados em intervenções em instalações elétricas, com a devida comprovação por meio de laudo técnico emitido por profissional legalmente habilitado.

Os ensaios foram conduzidos no laboratório especializado da JVM ENGENHARIA & TREINAMENTOS, utilizando fontes de alta tensão (Hipot) e equipamentos de medição calibrados com rastreabilidade à Rede Brasileira de Calibração (RBC / Inmetro), em conformidade com as normas ABNT NBR, IEC e ASTM pertinentes.""".trimIndent()

val DEFAULT_REPORT_OBJECTIVE = """O objetivo precípuo deste trabalho de engenharia é verificar experimentalmente a integridade física e a capacidade de isolação elétrica dos equipamentos e ferramentas submetidos a ensaio, assegurando que:
• Os níveis de corrente de fuga sob tensão de ensaio especificada mantenham-se rigorosamente abaixo dos limites máximos permitidos por norma;
• Os materiais isolantes poliméricos e elastoméricos suportem a tensão de prova sem apresentar perfuração, disrupção elétrica, arco ou sobreaquecimento;
• Não existam trincas, fissuras, microfuros, contaminações por umidade ou degradações mecânicas/químicas que comprometam a rigidez dielétrica;
• Seja emitida a certificação de conformidade e estipulada a validade técnica para subsidiar o Prontuário de Instalações Elétricas (PIE) da empresa contratante.""".trimIndent()

val DEFAULT_REPORT_METHODOLOGY = """5.1 Inspeção Visual e Pré-Condicionamento: Todos os itens passam por higienização com água e sabão neutro para remoção de sujidades, poeiras e graxas, seguida de secagem integral. Realiza-se rigorosa inspeção visual contra cortes, trincas, perfurações, deformações mecânicas e integridade do CA. Capacetes e ferramentas isoladas são submetidos a pré-condicionamento por imersão em água por 24h ou banho-maria termostatizado conforme ABNT NBR 8221 e NBR 9699.

5.2 Aplicação de Alta Tensão (Rigidez Dielétrica e Prova): A tensão de ensaio é aplicada gradualmente a uma taxa controlada de elevação (aprox. 1 kV/s) até atingir o patamar nominal estipulado pela classe e tipo do equipamento. A tensão é mantida de forma contínua e estabilizada pelo período regulamentar (1 minuto para luvas e mantas, 3 minutos para capacetes e ferramentas isoladas).

5.3 Medição de Corrente de Fuga e Limiares de Aceitação: Durante todo o período de aplicação da alta tensão, a corrente de fuga total (componentes capacitiva e resistiva) é monitorada continuamente através de miliamperímetro digital calibrado em série com o circuito de aterramento de segurança. O equipamento é considerado APROVADO se a corrente máxima medida não ultrapassar o valor limite especificado pela norma correspondente.

5.4 Rastreabilidade Metrológica dos Instrumentos RBC: O ensaio utiliza equipamento Hipot e instrumentos de medição de grandezas elétricas calibrados periodicamente em laboratório pertencente à Rede Brasileira de Calibração (RBC / Inmetro), garantindo a confiabilidade e rastreabilidade metrológica de todos os valores apresentados.""".trimIndent()

val DEFAULT_REPORT_DEVELOPMENT = """Os ensaios foram realizados no laboratório seguindo as rotinas técnicas padronizadas e critérios das normas vigentes aplicáveis. Cada equipamento foi individualmente registrado, identificado por etiqueta indelével codificada por TAG, submetido aos parâmetros prescritos de alta tensão alternada e monitorado em tempo real quanto à estabilidade dielétrica e corrente de fuga. Os resultados consolidados constam na tabulação individual a seguir, indicando conformidade, rastreabilidade metrológica e vigência da certificação.""".trimIndent()

val DEFAULT_REPORT_CONCLUSION = """Com base nos ensaios laboratoriais de rigidez dielétrica e nas medições de corrente de fuga executados em conformidade com as normas ABNT NBR, IEC e ASTM, conclui-se que as amostras ensaiadas ATENDEM PLENAMENTE aos requisitos regulamentares de segurança da NR-10.

Os equipamentos APROVADOS encontram-se com sua rigidez dielétrica preservada e devidamente aptos para uso em intervenções e serviços em instalações elétricas energizadas até suas respectivas tensões nominais de trabalho. Os itens com resultado REPROVADO (se houver) tiveram sua capacidade isolante comprometida e DEVEM SER IMEDIATAMENTE INUTILIZADOS E RETIRADOS DE CIRCULAÇÃO.""".trimIndent()

@Composable
fun ReportConfigDialog(
    currentSettings: AppSettingEntity,
    onSave: (AppSettingEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var intro by remember {
        mutableStateOf(
            if (currentSettings.reportIntroText.isNotBlank()) currentSettings.reportIntroText else DEFAULT_REPORT_INTRO
        )
    }
    var objective by remember {
        mutableStateOf(
            if (currentSettings.reportObjectiveText.isNotBlank()) currentSettings.reportObjectiveText else DEFAULT_REPORT_OBJECTIVE
        )
    }
    var development by remember {
        mutableStateOf(
            if (currentSettings.reportDevelopmentText.isNotBlank()) currentSettings.reportDevelopmentText else DEFAULT_REPORT_DEVELOPMENT
        )
    }
    var conclusion by remember {
        mutableStateOf(
            if (currentSettings.reportConclusionText.isNotBlank()) currentSettings.reportConclusionText else DEFAULT_REPORT_CONCLUSION
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
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
                                .size(36.dp)
                                .background(NavyPrimary, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Configuração do Relatório Completo",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = NavyPrimary
                            )
                            Text(
                                text = "Edite os textos que constarão nas seções do laudo/relatório",
                                fontSize = 10.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE2E8F0))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Introduction
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("1. Introdução e Enquadramento Regulamentar", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                        Text("Enquadramento normativo NR-10 e apresentação do laudo", fontSize = 10.sp, color = Color(0xFF64748B))
                        OutlinedTextField(
                            value = intro,
                            onValueChange = { intro = it },
                            colors = appTextFieldColors(),
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("report_config_intro_field")
                        )
                    }

                    // Objective
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("2. Objetivo do Laudo & Escopo", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                        Text("Objetivos técnicos e critérios de avaliação do ensaio", fontSize = 10.sp, color = Color(0xFF64748B))
                        OutlinedTextField(
                            value = objective,
                            onValueChange = { objective = it },
                            colors = appTextFieldColors(),
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("report_config_objective_field")
                        )
                    }

                    // Development
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("3. Desenvolvimento dos Ensaios & Análise", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                        Text("Apresentação do desenvolvimento técnico e análise dos resultados do lote", fontSize = 10.sp, color = Color(0xFF64748B))
                        OutlinedTextField(
                            value = development,
                            onValueChange = { development = it },
                            colors = appTextFieldColors(),
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("report_config_development_field")
                        )
                    }

                    // Conclusion
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("4. Conclusão Técnica e Parecer de Aptidão", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                        Text("Parecer final do engenheiro sobre conformidade e liberação", fontSize = 10.sp, color = Color(0xFF64748B))
                        OutlinedTextField(
                            value = conclusion,
                            onValueChange = { conclusion = it },
                            colors = appTextFieldColors(),
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("report_config_conclusion_field")
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE2E8F0))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            intro = DEFAULT_REPORT_INTRO
                            objective = DEFAULT_REPORT_OBJECTIVE
                            development = DEFAULT_REPORT_DEVELOPMENT
                            conclusion = DEFAULT_REPORT_CONCLUSION
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restaurar Padrão", fontSize = 10.5.sp)
                    }

                    Button(
                        onClick = {
                            val updated = currentSettings.copy(
                                reportIntroText = intro,
                                reportObjectiveText = objective,
                                reportMethodologyText = "",
                                reportDevelopmentText = development,
                                reportConclusionText = conclusion,
                                gloveProcedurePhotoUri = ""
                            )
                            onSave(updated)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("report_config_save_button")
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvar Textos", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}
