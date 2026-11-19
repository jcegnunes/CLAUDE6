package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.AppSettingEntity
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.util.LetterheadStorageHelper
import java.io.File
import kotlin.math.roundToInt

@Composable
fun LetterheadConfigDialog(
    currentSettings: AppSettingEntity,
    onSave: (AppSettingEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var letterheadUri by remember { mutableStateOf(currentSettings.letterheadUri) }
    var useLetterhead by remember { mutableStateOf(currentSettings.useLetterhead) }
    var letterheadOpacity by remember { mutableFloatStateOf(currentSettings.letterheadOpacity) }
    var letterheadApplyMode by remember { mutableStateOf(currentSettings.letterheadApplyMode) }
    var letterheadHeaderMode by remember { mutableStateOf(currentSettings.letterheadHeaderMode) }

    var showFullscreenPreview by remember { mutableStateOf(false) }

    // File picker launcher supporting images and PDF documents
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = LetterheadStorageHelper.saveUriToLetterheadFile(context, uri)
            if (savedPath != null) {
                letterheadUri = savedPath
                useLetterhead = true
                Toast.makeText(
                    context,
                    "Papel timbrado carregado com sucesso! Pronto para uso.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "Erro ao processar arquivo. Certifique-se de que é uma imagem ou PDF válido.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
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
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
                .testTag("letterhead_config_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyPrimary)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Papel Timbrado Oficial (Modelo A4)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Configuração do template institucional para Laudos e Relatórios",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color.White
                        )
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Status & Master Toggle
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (useLetterhead && letterheadUri.isNotBlank()) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (useLetterhead && letterheadUri.isNotBlank()) Color(0xFF86EFAC) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Usar Papel Timbrado nos Documentos",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = NavyPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (letterheadUri.isNotBlank() && useLetterhead) {
                                        Surface(
                                            color = Color(0xFFDCFCE7),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = "ATIVO",
                                                color = Color(0xFF166534),
                                                fontWeight = FontWeight.Black,
                                                fontSize = 9.5.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (letterheadUri.isNotBlank()) {
                                        if (useLetterhead) "O papel timbrado será impresso como fundo/modelo de laudos e relatórios PDF."
                                        else "Timbrado carregado, mas temporariamente desativado."
                                    } else {
                                        "Nenhum papel timbrado configurado. Carregue uma imagem ou gere o modelo oficial."
                                    },
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Switch(
                                checked = useLetterhead && letterheadUri.isNotBlank(),
                                onCheckedChange = { checked ->
                                    if (letterheadUri.isBlank() && checked) {
                                        Toast.makeText(context, "Carregue um papel timbrado primeiro.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        useLetterhead = checked
                                    }
                                },
                                enabled = letterheadUri.isNotBlank(),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = StatusGreen
                                )
                            )
                        }
                    }

                    // Preview and Load Section
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Pré-visualização do Modelo (A4)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = NavyPrimary
                            )

                            if (letterheadUri.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(10.dp))
                                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // A4 Thumbnail Box
                                    Box(
                                        modifier = Modifier
                                            .width(75.dp)
                                            .aspectRatio(1f / 1.414f) // Standard A4 aspect ratio
                                            .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
                                            .border(1.dp, Color(0xFF94A3B8), RoundedCornerShape(4.dp))
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { showFullscreenPreview = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = File(letterheadUri).takeIf { it.exists() } ?: letterheadUri,
                                            contentDescription = "Prévia do Papel Timbrado",
                                            contentScale = ContentScale.Fit,
                                            alpha = letterheadOpacity,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Arquivo: ${letterheadUri.substringAfterLast("/")}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = NavyPrimary,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "Resolução otimizada para impressão A4 oficial.",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { showFullscreenPreview = true },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Visualizar", fontSize = 10.5.sp)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    filePickerLauncher.launch(
                                                        arrayOf("image/*", "application/pdf")
                                                    )
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Trocar", fontSize = 10.5.sp)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    LetterheadStorageHelper.deleteLetterheadFile(letterheadUri)
                                                    letterheadUri = ""
                                                    useLetterhead = false
                                                    Toast.makeText(context, "Papel timbrado removido.", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Remover", fontSize = 10.5.sp)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Você pode selecionar um arquivo existente de papel timbrado (PNG, JPG ou PDF A4) ou gerar o modelo institucional padrão da JVM.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            filePickerLauncher.launch(
                                                arrayOf("image/*", "application/pdf")
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1.2f).height(40.dp).testTag("select_letterhead_file_button")
                                    ) {
                                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Carregar Imagem / PDF", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val generated = LetterheadStorageHelper.generateOfficialSampleLetterhead(context)
                                            if (generated != null) {
                                                letterheadUri = generated
                                                useLetterhead = true
                                                Toast.makeText(context, "Modelo Padrão JVM gerado e ativado com sucesso!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Falha ao gerar modelo padrão.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SafetyOrange),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).height(40.dp).testTag("generate_sample_letterhead_button")
                                    ) {
                                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Gerar Modelo JVM", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Application Mode Settings
                    if (letterheadUri.isNotBlank()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = null,
                                        tint = NavyPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Modo de Aplicação nas Páginas",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = NavyPrimary
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        "ALL_PAGES" to "Todas as Páginas",
                                        "FIRST_PAGE_ONLY" to "Apenas Capa / 1ª Pág",
                                        "INNER_PAGES_ONLY" to "Páginas Internas"
                                    ).forEach { (mode, label) ->
                                        FilterChip(
                                            selected = letterheadApplyMode == mode,
                                            onClick = { letterheadApplyMode = mode },
                                            label = { Text(label, fontSize = 10.5.sp, fontWeight = if (letterheadApplyMode == mode) FontWeight.Bold else FontWeight.Normal) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = NavyPrimary,
                                                selectedLabelColor = Color.White
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                // Header Mode Settings
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = NavyPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Integração do Cabeçalho Oficial",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = NavyPrimary
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "INTEGRATED" to "Cabeçalho Integrado\n(Fundo institucional completo)",
                                        "CLEAN_HEADER" to "Cabeçalho Limpo\n(Exibe topo pré-impresso)"
                                    ).forEach { (mode, label) ->
                                        FilterChip(
                                            selected = letterheadHeaderMode == mode,
                                            onClick = { letterheadHeaderMode = mode },
                                            label = {
                                                Text(
                                                    label,
                                                    fontSize = 10.sp,
                                                    textAlign = TextAlign.Center,
                                                    fontWeight = if (letterheadHeaderMode == mode) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = ElectricBlue,
                                                selectedLabelColor = Color.White
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                // Opacity / Watermark Slider
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Opacity,
                                        contentDescription = null,
                                        tint = NavyPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Opacidade / Intensidade: ${(letterheadOpacity * 100).roundToInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = NavyPrimary
                                    )
                                }

                                Slider(
                                    value = letterheadOpacity,
                                    onValueChange = { letterheadOpacity = it },
                                    valueRange = 0.10f..1.0f,
                                    steps = 17,
                                    colors = SliderDefaults.colors(
                                        thumbColor = NavyPrimary,
                                        activeTrackColor = ElectricBlue,
                                        inactiveTrackColor = Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = when {
                                        letterheadOpacity >= 0.9f -> "100% - Papel Timbrado Sólido (Recomendado para modelo institucional A4 completo)"
                                        letterheadOpacity >= 0.5f -> "Média Intensidade - Contraste balanceado entre arte e textos"
                                        else -> "Marca d'Água Leve - Efeito sutil de chancela de segurança no fundo"
                                    },
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }

                // Bottom Action Buttons
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Cancelar", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                val updated = currentSettings.copy(
                                    letterheadUri = letterheadUri,
                                    useLetterhead = useLetterhead && letterheadUri.isNotBlank(),
                                    letterheadOpacity = letterheadOpacity,
                                    letterheadApplyMode = letterheadApplyMode,
                                    letterheadHeaderMode = letterheadHeaderMode
                                )
                                onSave(updated)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.3f).height(44.dp).testTag("save_letterhead_settings_button")
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salvar Modelo", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }
                    }
                }
            }
        }
    }

    // Fullscreen Preview Dialog
    if (showFullscreenPreview && letterheadUri.isNotBlank()) {
        Dialog(
            onDismissRequest = { showFullscreenPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEE0A192F))
                    .clickable { showFullscreenPreview = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.85f)
                            .aspectRatio(1f / 1.414f)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = File(letterheadUri).takeIf { it.exists() } ?: letterheadUri,
                            contentDescription = "Visualização em Tela Cheia do Papel Timbrado",
                            contentScale = ContentScale.Fit,
                            alpha = letterheadOpacity,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Button(
                        onClick = { showFullscreenPreview = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = NavyPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fechar Visualização", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
