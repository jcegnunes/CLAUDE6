package com.example.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.util.PhotoStorageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class TestPhotoItem(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val label: String = "Equipamento em Ensaio",
    val timestamp: String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
)

val DEFAULT_PHOTO_TAGS = listOf(
    "Equipamento em Ensaio",
    "TAG / Placa de Identificação",
    "Cuba / Arranjo de Teste",
    "Resultado / Inspeção Visual",
    "Não Conformidade / Furo",
    "Condição de Limpeza / Secagem"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoCaptureSection(
    photos: List<TestPhotoItem>,
    onPhotosChanged: (List<TestPhotoItem>) -> Unit,
    serviceOrderNumber: String = "OS-AVULSA",
    equipmentTag: String = "",
    clientName: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var previewPhoto by remember { mutableStateOf<TestPhotoItem?>(null) }
    var editingPhoto by remember { mutableStateOf<TestPhotoItem?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showFolderDialog by remember { mutableStateOf(false) }

    val cleanOsNumber = PhotoStorageManager.sanitizeOsName(serviceOrderNumber)
    val osFolderPathDisplay = PhotoStorageManager.getOsFolderPathDisplay(context, cleanOsNumber)

    // Camera Launcher with FileProvider
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            val uri = tempCameraUri!!
            // Save directly into the designated OS folder on device memory
            val result = PhotoStorageManager.savePhotoForOs(
                context = context,
                sourceUri = uri,
                osNumber = cleanOsNumber,
                label = "Foto_Camera",
                equipmentTag = equipmentTag
            )

            val savedPath = result?.filePath ?: uri.toString()
            val newPhoto = TestPhotoItem(
                uriString = savedPath,
                label = "Equipamento em Ensaio"
            )
            onPhotosChanged(photos + newPhoto)
            Toast.makeText(
                context,
                "Foto salva na pasta $osFolderPathDisplay!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Permission launcher for Camera
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val photosDir = File(context.cacheDir, "photos")
                if (!photosDir.exists()) photosDir.mkdirs()
                val photoFile = File(photosDir, "cam_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                tempCameraUri = uri
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                // If FileProvider fails, create sample directly in the OS folder
                val sampleRes = PhotoStorageManager.createSamplePhotoForOs(
                    context = context,
                    osNumber = cleanOsNumber,
                    title = "Foto da Câmera (Simulador)",
                    equipmentTag = equipmentTag
                )
                if (sampleRes != null) {
                    onPhotosChanged(photos + TestPhotoItem(uriString = sampleRes.filePath, label = "Foto da Câmera"))
                    Toast.makeText(context, "Foto salva na pasta da OS!", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Permissão de câmera necessária para tirar fotos.", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery Picker Launcher
    val pickGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val result = PhotoStorageManager.savePhotoForOs(
                context = context,
                sourceUri = uri,
                osNumber = cleanOsNumber,
                label = "Galeria",
                equipmentTag = equipmentTag
            )
            val savedPath = result?.filePath ?: uri.toString()
            val newPhoto = TestPhotoItem(
                uriString = savedPath,
                label = "Equipamento / TAG"
            )
            onPhotosChanged(photos + newPhoto)
            Toast.makeText(
                context,
                "Foto importada e salva na pasta $osFolderPathDisplay!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            try {
                val photosDir = File(context.cacheDir, "photos")
                if (!photosDir.exists()) photosDir.mkdirs()
                val photoFile = File(photosDir, "cam_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                tempCameraUri = uri
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                val sampleRes = PhotoStorageManager.createSamplePhotoForOs(
                    context = context,
                    osNumber = cleanOsNumber,
                    title = "Registro Fotográfico de Campo",
                    equipmentTag = equipmentTag
                )
                if (sampleRes != null) {
                    onPhotosChanged(photos + TestPhotoItem(uriString = sampleRes.filePath, label = "Foto de Campo"))
                    Toast.makeText(context, "Foto salva na pasta da OS!", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(NavyPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = SafetyOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Registro Fotográfico do Ensaio",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "Evidências salvas na pasta da OS na memória do celular",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Photos Count Badge
            Surface(
                color = if (photos.isNotEmpty()) ElectricBlue.copy(alpha = 0.15f) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "${photos.size} ${if (photos.size == 1) "foto" else "fotos"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (photos.isNotEmpty()) ElectricBlue else Color(0xFF64748B),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Dedicated OS Storage Folder Banner
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Pasta de Salvamento no Celular:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                            Text(
                                text = osFolderPathDisplay,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = ElectricBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showFolderDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue)
                    ) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ver Pasta", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "Todas as fotos capturadas pela câmera ou importadas são salvas automaticamente nesta pasta exclusiva da Ordem de Serviço $cleanOsNumber.",
                    fontSize = 10.sp,
                    color = Color(0xFF1E3A8A),
                    lineHeight = 13.sp
                )
            }
        }

        // Action Buttons Row: Camera vs Gallery
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Camera Button
            Button(
                onClick = { launchCamera() },
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("camera_photo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Tirar Foto (Câmera)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
            }

            // Gallery Button
            Button(
                onClick = { pickGalleryLauncher.launch("image/*") },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("gallery_photo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Carregar da Galeria",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }

        // Photos Grid / List
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .border(
                        BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { launchCamera() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Nenhuma foto anexada. Toque para fotografar pela câmera ou carregar da galeria.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(photos) { index, photoItem ->
                    PhotoCardItem(
                        item = photoItem,
                        onDelete = {
                            val updated = photos.toMutableList()
                            updated.removeAt(index)
                            onPhotosChanged(updated)
                        },
                        onLabelChanged = { newLabel ->
                            val updated = photos.toMutableList()
                            updated[index] = photoItem.copy(label = newLabel)
                            onPhotosChanged(updated)
                        },
                        onEdit = {
                            editingPhoto = photoItem
                        },
                        onClick = { previewPhoto = photoItem }
                    )
                }
            }
        }

        // Bottom Controls: Preset demo and Share OS photos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "JPG/PNG • Salvo em $cleanOsNumber",
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (photos.isNotEmpty()) {
                    Text(
                        text = "📤 Compartilhar OS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SafetyOrange,
                        modifier = Modifier
                            .clickable {
                                val shared = PhotoStorageManager.shareAllOsPhotos(context, cleanOsNumber)
                                if (!shared) {
                                    Toast.makeText(context, "Nenhuma foto salva para compartilhar.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(4.dp)
                    )
                }

                Text(
                    text = "+ Amostra OS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricBlue,
                    modifier = Modifier
                        .clickable {
                            val sampleResult = PhotoStorageManager.createSamplePhotoForOs(
                                context = context,
                                osNumber = cleanOsNumber,
                                title = "Amostra Dielétrica #${photos.size + 1}",
                                equipmentTag = equipmentTag
                            )
                            if (sampleResult != null) {
                                onPhotosChanged(
                                    photos + TestPhotoItem(
                                        uriString = sampleResult.filePath,
                                        label = DEFAULT_PHOTO_TAGS.getOrElse(photos.size % DEFAULT_PHOTO_TAGS.size) { "Equipamento em Ensaio" }
                                    )
                                )
                                Toast.makeText(context, "Foto de demonstração salva na pasta da OS!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(4.dp)
                )
            }
        }
    }

    // Full Screen Photo Preview Dialog
    if (previewPhoto != null) {
        val photo = previewPhoto!!
        Dialog(onDismissRequest = { previewPhoto = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
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
                                text = photo.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = NavyPrimary
                            )
                            Text(
                                text = "Capturado em: ${photo.timestamp} • Pasta: $cleanOsNumber",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        IconButton(onClick = { previewPhoto = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = if (photo.uriString.startsWith("/")) File(photo.uriString) else Uri.parse(photo.uriString),
                            contentDescription = photo.label,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    // Change Tag in Preview
                    Text("Classificação da Evidência:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DEFAULT_PHOTO_TAGS.forEach { tag ->
                            val isSelected = photo.label == tag
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) NavyPrimary else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable {
                                    val index = photos.indexOfFirst { it.id == photo.id }
                                    if (index != -1) {
                                        val updated = photos.toMutableList()
                                        val modified = photo.copy(label = tag)
                                        updated[index] = modified
                                        onPhotosChanged(updated)
                                        previewPhoto = modified
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = tag,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color(0xFF334155)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val current = photo
                                previewPhoto = null
                                editingPhoto = current
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(44.dp)
                                .testTag("edit_photo_from_preview_button")
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Editar & Marcar Foto", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { previewPhoto = null },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(0.9f)
                                .height(44.dp)
                        ) {
                            Text("Fechar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Technical Photo Editor Dialog (Rotation & Vector Annotations)
    if (editingPhoto != null) {
        val targetEditing = editingPhoto!!
        PhotoEditorDialog(
            photo = targetEditing,
            serviceOrderNumber = cleanOsNumber,
            equipmentTag = equipmentTag,
            onDismiss = { editingPhoto = null },
            onPhotoSaved = { newUriString ->
                val targetId = targetEditing.id
                val index = photos.indexOfFirst { it.id == targetId }
                if (index != -1) {
                    val updated = photos.toMutableList()
                    updated[index] = targetEditing.copy(uriString = newUriString)
                    onPhotosChanged(updated)
                }
                editingPhoto = null
            }
        )
    }

    // OS Folder Explorer Dialog
    if (showFolderDialog) {
        OsPhotoFolderDialog(
            osNumber = cleanOsNumber,
            currentPhotos = photos,
            onDismiss = { showFolderDialog = false },
            onSyncAll = {
                val syncedCount = PhotoStorageManager.syncPhotosToOsFolder(
                    context = context,
                    osNumber = cleanOsNumber,
                    photos = photos,
                    equipmentTag = equipmentTag
                )
                Toast.makeText(context, "$syncedCount fotos sincronizadas na pasta da OS!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun OsPhotoFolderDialog(
    osNumber: String,
    currentPhotos: List<TestPhotoItem>,
    onDismiss: () -> Unit,
    onSyncAll: () -> Unit
) {
    val context = LocalContext.current
    var folderFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    fun refreshFiles() {
        folderFiles = PhotoStorageManager.listPhotosForOs(context, osNumber)
    }

    LaunchedEffect(osNumber) {
        refreshFiles()
    }

    val displayPath = PhotoStorageManager.getOsFolderPathDisplay(context, osNumber)
    val absolutePath = PhotoStorageManager.getOsPhotosDirectory(context, osNumber).absolutePath

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFEFF6FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Pasta da Ordem de Serviço",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = NavyPrimary
                            )
                            Text(
                                text = "OS: $osNumber",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = ElectricBlue
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                // Path Information Card with Copy button
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Caminho no Dispositivo:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                            Row(
                                modifier = Modifier.clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Caminho Pasta OS", absolutePath)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Caminho copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copiar", modifier = Modifier.size(12.dp), tint = ElectricBlue)
                                Text("Copiar", fontSize = 10.sp, color = ElectricBlue, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = displayPath,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF0F172A)
                        )

                        Text(
                            text = absolutePath,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // File count summary and actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Arquivos salvos nesta pasta (${folderFiles.size}):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    IconButton(onClick = { refreshFiles() }, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Atualizar", modifier = Modifier.size(16.dp))
                    }
                }

                if (folderFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhum arquivo físico salvo ainda nesta pasta. As novas fotos tiradas ou amostras adicionadas serão gravadas automaticamente aqui.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        folderFiles.forEachIndexed { idx, file ->
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF0F172A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = file,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.Black,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${file.length() / 1024} KB • ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))}",
                                            fontSize = 9.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (folderFiles.isNotEmpty()) {
                        Button(
                            onClick = {
                                PhotoStorageManager.shareAllOsPhotos(context, osNumber)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Compartilhar Todas", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Text("Concluído", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCardItem(
    item: TestPhotoItem,
    onDelete: () -> Unit,
    onLabelChanged: (String) -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = if (item.uriString.startsWith("/")) File(item.uriString) else Uri.parse(item.uriString),
                    contentDescription = item.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Edit Button Overlay (Top-Start)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(22.dp)
                        .background(ElectricBlue.copy(alpha = 0.9f), CircleShape)
                        .clickable { onEdit() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar e Fazer Marcações",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Delete Button Overlay (Top-End)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remover foto",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Zoom Icon Overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Ampliar",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = item.label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = item.timestamp,
                fontSize = 8.sp,
                color = Color(0xFF64748B),
                maxLines = 1
            )
        }
    }
}
