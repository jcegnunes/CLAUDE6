package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.StatusGreen
import com.example.util.SignatureStorageHelper
import java.io.File

@Composable
fun SignaturePad(
    title: String,
    signerName: String,
    signerRole: String,
    initialSignature: String = "",
    savedProfileSignature: String = "",
    onSignatureConfirmed: (String) -> Unit,
    onSaveToProfile: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentSignaturePath by remember { mutableStateOf(initialSignature.ifBlank { savedProfileSignature }) }
    var isConfirmed by remember { mutableStateOf((initialSignature.ifBlank { savedProfileSignature }).isNotBlank()) }
    var selectedTab by remember { mutableIntStateOf(if (savedProfileSignature.isNotBlank()) 0 else 1) }
    var saveAsProfileDefault by remember { mutableStateOf(false) }

    // Drawing points
    val points = remember { mutableStateListOf<Offset?>() }

    // Launcher for selecting an image from gallery / files
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val savedPath = SignatureStorageHelper.saveUriToSignatureFile(context, it, "sig_${signerName.take(4)}")
            if (savedPath != null) {
                currentSignaturePath = savedPath
                isConfirmed = true
                onSignatureConfirmed(savedPath)
                if (saveAsProfileDefault) {
                    onSaveToProfile?.invoke(savedPath)
                }
                Toast.makeText(context, "Assinatura em imagem carregada com sucesso!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto-confirm and update if initialSignature or savedProfileSignature changes
    LaunchedEffect(initialSignature, savedProfileSignature) {
        if (initialSignature.isNotBlank()) {
            currentSignaturePath = initialSignature
            isConfirmed = true
        } else if (savedProfileSignature.isNotBlank() && currentSignaturePath.isBlank()) {
            currentSignaturePath = savedProfileSignature
            isConfirmed = true
            onSignatureConfirmed(savedProfileSignature)
        }
    }

    Column(
        modifier = modifier
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header with Signer details and Quick Load from Settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Create,
                        contentDescription = null,
                        tint = NavyPrimary,
                        modifier = Modifier.padding(end = 4.dp).size(16.dp)
                    )
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                }
                Text(
                    text = "$signerName • $signerRole",
                    fontSize = 11.sp,
                    color = Color(0xFF475569)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (savedProfileSignature.isNotBlank() && currentSignaturePath != savedProfileSignature) {
                    OutlinedButton(
                        onClick = {
                            currentSignaturePath = savedProfileSignature
                            isConfirmed = true
                            selectedTab = 0
                            onSignatureConfirmed(savedProfileSignature)
                            Toast.makeText(context, "Assinatura das Configurações carregada!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(imageVector = Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Carregar Padrão", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (isConfirmed && currentSignaturePath.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xFFD1FAE5), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusGreen,
                            modifier = Modifier.padding(end = 4.dp).size(14.dp)
                        )
                        Text(
                            text = "Assinado",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusGreen
                        )
                    }
                }
            }
        }

        // Mode Selection Tabs (Profile Saved / Upload Image / Draw Stylus)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFFE2E8F0),
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Padrão Configurações", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Carregar Imagem", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Gesture, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Desenhar / Caneta", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // TAB 0: Saved Profile / Default Settings Signature
        if (selectedTab == 0) {
            if (savedProfileSignature.isNotBlank() && (File(savedProfileSignature).exists() || savedProfileSignature.startsWith("content://") || savedProfileSignature.startsWith("http"))) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = if (savedProfileSignature.startsWith("/")) File(savedProfileSignature) else Uri.parse(savedProfileSignature),
                        contentDescription = "Assinatura do Perfil",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Assinatura oficial cadastrada nas Configurações", fontSize = 10.sp, color = Color(0xFF64748B))

                    Button(
                        onClick = {
                            currentSignaturePath = savedProfileSignature
                            isConfirmed = true
                            onSignatureConfirmed(savedProfileSignature)
                            Toast.makeText(context, "Assinatura das Configurações aplicada!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (currentSignaturePath == savedProfileSignature && isConfirmed) StatusGreen else NavyPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (currentSignaturePath == savedProfileSignature && isConfirmed) "Aplicada" else "Usar Assinatura Padrão", fontSize = 11.sp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Nenhuma assinatura padrão salva no Menu Configurações.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "Você pode carregar uma imagem ou desenhar nas abas ao lado e clicar em 'Salvar nas Configurações'.",
                            fontSize = 9.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        // TAB 1: Upload Image (PNG/JPG)
        else if (selectedTab == 1) {
            if (currentSignaturePath.isNotBlank() && (File(currentSignaturePath).exists() || currentSignaturePath.startsWith("content://"))) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = if (currentSignaturePath.startsWith("/")) File(currentSignaturePath) else Uri.parse(currentSignaturePath),
                        contentDescription = "Imagem da Assinatura",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    IconButton(
                        onClick = {
                            currentSignaturePath = ""
                            isConfirmed = false
                            onSignatureConfirmed("")
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .background(Color(0x88000000), RoundedCornerShape(12.dp))
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Remover", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                        .clickable { imagePickerLauncher.launch("image/*") }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Toque para carregar imagem da assinatura (PNG/JPG)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Text("Recomendado fundo transparente ou branco", fontSize = 9.5.sp, color = Color(0xFF64748B))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Selecionar Arquivo", fontSize = 11.sp)
                }

                if (onSaveToProfile != null && currentSignaturePath.isNotBlank()) {
                    Button(
                        onClick = {
                            onSaveToProfile(currentSignaturePath)
                            Toast.makeText(context, "Salvo como assinatura padrão nas Configurações!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(imageVector = Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Salvar como Padrão", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // TAB 2: Draw / Stylus
        else if (selectedTab == 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .border(1.dp, if (points.isNotEmpty()) NavyPrimary.copy(alpha = 0.5f) else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                points.add(offset)
                                isConfirmed = false
                            },
                            onDrag = { change, _ ->
                                points.add(change.position)
                            },
                            onDragEnd = {
                                points.add(null) // separator
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val path = Path()
                    var isFirst = true

                    points.forEach { point ->
                        if (point == null) {
                            isFirst = true
                        } else if (isFirst) {
                            path.moveTo(point.x, point.y)
                            isFirst = false
                        } else {
                            path.lineTo(point.x, point.y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = NavyPrimary,
                        style = Stroke(
                            width = 4f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                if (points.isEmpty()) {
                    Text(
                        text = "Toque e assine aqui com o dedo ou caneta stylus",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Save to profile checkbox & action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onSaveToProfile != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { saveAsProfileDefault = !saveAsProfileDefault }
                    ) {
                        Checkbox(
                            checked = saveAsProfileDefault,
                            onCheckedChange = { saveAsProfileDefault = it },
                            colors = CheckboxDefaults.colors(checkedColor = NavyPrimary),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Salvar no Padrão Configurações", fontSize = 10.sp, color = Color(0xFF475569), fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            points.clear()
                            isConfirmed = false
                            currentSignaturePath = ""
                            onSignatureConfirmed("")
                        },
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Limpar", fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = {
                            if (points.isNotEmpty()) {
                                val filePath = SignatureStorageHelper.savePointsToSignatureFile(
                                    context = context,
                                    points = points,
                                    prefix = "draw_${signerName.take(4)}"
                                )
                                if (filePath != null) {
                                    currentSignaturePath = filePath
                                    isConfirmed = true
                                    onSignatureConfirmed(filePath)
                                    if (saveAsProfileDefault) {
                                        onSaveToProfile?.invoke(filePath)
                                    }
                                    Toast.makeText(context, "Assinatura confirmada" + if (saveAsProfileDefault) " e salva como padrão!" else "!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = points.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isConfirmed) StatusGreen else NavyPrimary),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(if (isConfirmed) "Confirmada" else "Confirmar", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
