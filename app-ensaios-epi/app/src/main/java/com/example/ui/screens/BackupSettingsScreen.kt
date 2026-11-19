package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.data.service.SupabaseService
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.AppSettingEntity
import com.example.ui.components.SignaturePad
import com.example.ui.theme.appTextFieldColors
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.viewmodel.DielectricViewModel
import com.example.util.BackupImportResult
import com.example.util.BackupInspectionSummary
import com.example.util.CompanyLogoStorageHelper
import com.example.util.LetterheadStorageHelper
import com.example.util.LocalBackupManager
import com.example.util.SignatureStorageHelper
import com.example.util.CompanyLabInfo
import com.example.util.ValidationPortal
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun BackupSettingsScreen(
    viewModel: DielectricViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val testsList by viewModel.tests.collectAsStateWithLifecycle()
    val clientsList by viewModel.clients.collectAsStateWithLifecycle()
    val equipmentList by viewModel.equipment.collectAsStateWithLifecycle()
    val ordersList by viewModel.serviceOrders.collectAsStateWithLifecycle()
    val instrumentsList by viewModel.instruments.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var localBackups by remember { mutableStateOf<List<File>>(emptyList()) }
    var photosStats by remember { mutableStateOf<Pair<Int, Long>>(Pair(0, 0L)) }

    var isExportingZip by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }
    var exportedZipFile by remember { mutableStateOf<File?>(null) }
    var showExportSuccessDialog by remember { mutableStateOf(false) }
    var showResetDemoConfirmDialog by remember { mutableStateOf(false) }

    var inspectionSummary by remember { mutableStateOf<BackupInspectionSummary?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var replaceMode by remember { mutableStateOf(false) }
    var deleteConfirmFile by remember { mutableStateOf<File?>(null) }

    fun refreshBackupsAndStats() {
        localBackups = viewModel.getLocalBackups()
        photosStats = viewModel.getLocalPhotosStats()
    }

    LaunchedEffect(Unit) {
        refreshBackupsAndStats()
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isRestoring = true
                progressStatus = "Inspecionando arquivo de backup..."
                val summary = viewModel.inspectBackup(uri)
                isRestoring = false
                if (summary != null) {
                    inspectionSummary = summary
                    showRestoreConfirmDialog = true
                } else {
                    Toast.makeText(context, "Arquivo de backup inválido ou incompatível.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { targetUri ->
        if (targetUri != null && exportedZipFile != null) {
            scope.launch {
                val success = viewModel.saveBackupToUri(exportedZipFile!!, targetUri)
                if (success) {
                    Toast.makeText(context, "Backup ZIP salvo com sucesso no local escolhido!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Falha ao salvar no local selecionado.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var companyName by remember { mutableStateOf("") }
    var legalName by remember { mutableStateOf("") }
    var cnpj by remember { mutableStateOf("") }
    var creaCompany by remember { mutableStateOf("") }
    var rtName by remember { mutableStateOf("") }
    var rtCrea by remember { mutableStateOf("") }
    var rtSignatureImage by remember { mutableStateOf("") }
    var techName by remember { mutableStateOf("") }
    var techCft by remember { mutableStateOf("") }
    var techSignatureImage by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var companyLogoUri by remember { mutableStateOf("") }

    // Campos da plataforma web (companies.lab_info), sincronizados com a nuvem
    var cep by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }
    var addressNumber by remember { mutableStateOf("") }
    var complement by remember { mutableStateOf("") }
    var neighborhood by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var uf by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var instagram by remember { mutableStateOf("") }
    var rtTitle by remember { mutableStateOf("") }
    var rtRnp by remember { mutableStateOf("") }
    var emissionMode by remember { mutableStateOf("laudo_e_certificado") }
    var validityMonths by remember { mutableStateOf("6") }
    var reportPrefix by remember { mutableStateOf("LAU-") }
    var certificatePrefix by remember { mutableStateOf("CERT-") }
    var certificateTitle by remember { mutableStateOf("") }
    var approvalText by remember { mutableStateOf("") }
    var rejectionText by remember { mutableStateOf("") }
    var observationNote by remember { mutableStateOf("") }
    var requireSignature by remember { mutableStateOf(true) }
    var enableQrValidation by remember { mutableStateOf(true) }
    var portalUrl by remember { mutableStateOf(ValidationPortal.DEFAULT_BASE_URL) }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = CompanyLogoStorageHelper.saveUriToLogoFile(context, uri)
            if (savedPath != null) {
                companyLogoUri = savedPath
                Toast.makeText(context, "Logotipo carregado! Clique em 'Salvar como Padrão' para aplicar aos relatórios.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Não foi possível carregar a imagem selecionada.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var letterheadUri by remember { mutableStateOf("") }
    var useLetterhead by remember { mutableStateOf(true) }
    var letterheadOpacity by remember { mutableFloatStateOf(1.0f) }
    var letterheadApplyMode by remember { mutableStateOf("ALL_PAGES") }
    var letterheadHeaderMode by remember { mutableStateOf("INTEGRATED") }
    var showFullscreenLetterhead by remember { mutableStateOf(false) }

    val letterheadPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = LetterheadStorageHelper.saveUriToLetterheadFile(context, uri)
            if (savedPath != null) {
                letterheadUri = savedPath
                useLetterhead = true
                Toast.makeText(context, "Papel timbrado oficial carregado com sucesso!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Erro ao processar o arquivo de papel timbrado.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var reportIntro by remember { mutableStateOf("") }
    var reportObjective by remember { mutableStateOf("") }
    var reportMethodology by remember { mutableStateOf("") }
    var reportDevelopment by remember { mutableStateOf("") }
    var reportConclusion by remember { mutableStateOf("") }

    // Supabase Cloud Integration States
    var supabaseUrl by remember { mutableStateOf(SupabaseService.DEFAULT_URL) }
    var supabaseAnonKey by remember { mutableStateOf(SupabaseService.DEFAULT_ANON_KEY) }
    var supabaseAutoSync by remember { mutableStateOf(true) }
    var showKeyVisibility by remember { mutableStateOf(false) }

    val supabaseConnStatus by viewModel.supabaseConnectionStatus.collectAsStateWithLifecycle()
    val isSupabaseTesting by viewModel.isSupabaseTesting.collectAsStateWithLifecycle()
    val isSupabaseSyncing by viewModel.isSupabaseSyncing.collectAsStateWithLifecycle()
    val supabaseSyncResult by viewModel.supabaseSyncResult.collectAsStateWithLifecycle()

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentCompany by viewModel.currentCompany.collectAsStateWithLifecycle()
    val companies by viewModel.companies.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()

    // Mesmos perfis que acessam as Configurações na plataforma web
    val canEditCompany = currentUser?.role?.trim()?.lowercase().let { it == "admin" || it == "responsavel_tecnico" }

    LaunchedEffect(settings) {
        settings?.let { s ->
            companyName = s.name
            legalName = s.legalName
            cnpj = s.cnpj
            creaCompany = s.creaCompanyRegister
            rtName = s.technicalResponsibleName
            rtCrea = s.technicalResponsibleCrea
            rtSignatureImage = s.technicalResponsibleSignatureImage
            techName = s.defaultTechnicianName
            techCft = s.defaultTechnicianCftOrCrea
            techSignatureImage = s.defaultTechnicianSignatureImage
            address = s.address
            phone = s.phone
            email = s.email
            companyLogoUri = s.companyLogoUri
            val lab = CompanyLabInfo.fromSettings(s)
            val tech = CompanyLabInfo.technical(lab)
            val em = CompanyLabInfo.emission(lab)
            // endereço estruturado da web; se ainda não houver, mostra o endereço antigo no logradouro
            val hasStructured = lab.has("cep") || lab.has("number") || lab.has("city")
            street = if (hasStructured) CompanyLabInfo.str(lab, "address") else s.address
            cep = CompanyLabInfo.str(lab, "cep")
            addressNumber = CompanyLabInfo.str(lab, "number")
            complement = CompanyLabInfo.str(lab, "complement")
            neighborhood = CompanyLabInfo.str(lab, "neighborhood")
            city = CompanyLabInfo.str(lab, "city")
            uf = CompanyLabInfo.str(lab, "state")
            website = CompanyLabInfo.str(lab, "website", s.website)
            instagram = CompanyLabInfo.str(lab, "instagram")
            rtTitle = CompanyLabInfo.str(tech, "title", "Responsável Técnico")
            rtRnp = CompanyLabInfo.str(tech, "rnp")
            emissionMode = CompanyLabInfo.str(em, "defaultEmissionMode", "laudo_e_certificado")
            validityMonths = em.optInt("defaultValidityMonths", s.defaultRetestMonths).toString()
            reportPrefix = CompanyLabInfo.str(em, "reportPrefix", "LAU-")
            certificatePrefix = CompanyLabInfo.str(em, "certificatePrefix", "CERT-")
            certificateTitle = CompanyLabInfo.str(em, "headerCustomTitle", CompanyLabInfo.DEFAULT_CERT_TITLE)
            approvalText = CompanyLabInfo.str(em, "defaultApprovalText", CompanyLabInfo.DEFAULT_APPROVAL_TEXT)
            rejectionText = CompanyLabInfo.str(em, "defaultRejectionText", CompanyLabInfo.DEFAULT_REJECTION_TEXT)
            observationNote = CompanyLabInfo.str(em, "standardObservationNote", CompanyLabInfo.DEFAULT_OBSERVATION)
            requireSignature = em.optBoolean("requireDigitalSignature", true)
            enableQrValidation = em.optBoolean("enableQrCodeValidation", true)
            portalUrl = ValidationPortal.normalize(CompanyLabInfo.str(lab, "validationBaseUrl"))
            letterheadUri = s.letterheadUri
            useLetterhead = s.useLetterhead
            letterheadOpacity = s.letterheadOpacity
            letterheadApplyMode = s.letterheadApplyMode
            letterheadHeaderMode = s.letterheadHeaderMode
            reportIntro = if (s.reportIntroText.isNotBlank()) s.reportIntroText else com.example.ui.components.DEFAULT_REPORT_INTRO
            reportObjective = if (s.reportObjectiveText.isNotBlank()) s.reportObjectiveText else com.example.ui.components.DEFAULT_REPORT_OBJECTIVE
            reportMethodology = if (s.reportMethodologyText.isNotBlank()) s.reportMethodologyText else com.example.ui.components.DEFAULT_REPORT_METHODOLOGY
            reportDevelopment = if (s.reportDevelopmentText.isNotBlank()) s.reportDevelopmentText else com.example.ui.components.DEFAULT_REPORT_DEVELOPMENT
            reportConclusion = if (s.reportConclusionText.isNotBlank()) s.reportConclusionText else com.example.ui.components.DEFAULT_REPORT_CONCLUSION
            if (s.supabaseUrl.isNotBlank()) supabaseUrl = s.supabaseUrl
            if (s.supabaseAnonKey.isNotBlank()) supabaseAnonKey = s.supabaseAnonKey
            supabaseAutoSync = s.supabaseAutoSync
        }
    }

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
                        .background(NavyPrimary, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Configurações & Padrões do Aplicativo", fontWeight = FontWeight.Black, fontSize = 18.sp, color = NavyPrimary)
                    Text("Dados cadastrais do laboratório, CREA, CFT, assinaturas padrão e backup", fontSize = 11.sp, color = Color(0xFF64748B))
                }
            }
        }

        // Active Company & User Session Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth().testTag("auth_session_settings_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(ElectricBlue, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentUser?.name?.take(2)?.uppercase() ?: "US",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Sessão Ativa no Sistema",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = currentUser?.name ?: "Usuário",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E293B)
                        ) {
                            Text(
                                text = currentUser?.role ?: "Profissional",
                                color = ElectricBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF334155))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Empresa Vinculada:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Text(
                                text = currentCompany?.name ?: currentUser?.companyName ?: "JVM Engenharia",
                                color = SafetyOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "CNPJ: ${currentCompany?.cnpj?.ifBlank { "29.894.500/0001-04" } ?: "29.894.500/0001-04"}",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Conselho / Registro:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Text(
                                text = currentUser?.registrationCouncil ?: "CREA 24481/D-DF",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = currentUser?.email ?: "joao.nunes@jvmengenharia.com.br",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${companies.size} empresas cadastradas • ${users.size} usuários",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )

                        OutlinedButton(
                            onClick = { viewModel.logout() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp).testTag("logout_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Trocar / Sair", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Company Details Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("1. Dados Cadastrais da Empresa / Laboratório", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Padrão Global Ativo", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusGreen)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (canEditCompany) Color(0xFFECFDF5) else Color(0xFFFFF7ED), RoundedCornerShape(8.dp))
                            .border(1.dp, if (canEditCompany) Color(0xFFA7F3D0) else Color(0xFFFED7AA), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = if (canEditCompany)
                                "Perfil administrador: as alterações dos dados da empresa, parâmetros de emissão e portal de validação são enviadas à plataforma web (mesmas configurações para todos os aparelhos)."
                            else
                                "Somente o administrador ou o responsável técnico da empresa altera estes dados (como na plataforma web).",
                            fontSize = 11.sp,
                            color = if (canEditCompany) Color(0xFF065F46) else Color(0xFF9A3412)
                        )
                    }

                    OutlinedTextField(value = companyName, onValueChange = { companyName = it }, enabled = canEditCompany, label = { Text("Nome Fantasia do Laboratório") }, colors = appTextFieldColors(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = legalName, onValueChange = { legalName = it }, enabled = canEditCompany, label = { Text("Razão Social Completa") }, colors = appTextFieldColors(), modifier = Modifier.fillMaxWidth())

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = cnpj, onValueChange = { cnpj = it }, enabled = canEditCompany, label = { Text("CNPJ") }, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = creaCompany, onValueChange = { creaCompany = it }, enabled = canEditCompany, label = { Text("Registro CREA Empresa") }, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = cep, onValueChange = { cep = it }, enabled = canEditCompany, label = { Text("CEP") }, singleLine = true, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = addressNumber, onValueChange = { addressNumber = it }, enabled = canEditCompany, label = { Text("Número / Lote") }, singleLine = true, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = street, onValueChange = { street = it }, enabled = canEditCompany, label = { Text("Logradouro / Rua / Avenida") }, colors = appTextFieldColors(), modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = neighborhood, onValueChange = { neighborhood = it }, enabled = canEditCompany, label = { Text("Bairro") }, singleLine = true, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = complement, onValueChange = { complement = it }, enabled = canEditCompany, label = { Text("Complemento / Bloco") }, singleLine = true, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = city, onValueChange = { city = it }, enabled = canEditCompany, label = { Text("Cidade") }, singleLine = true, colors = appTextFieldColors(), modifier = Modifier.weight(1.6f))
                        OutlinedTextField(value = uf, onValueChange = { uf = it.uppercase().take(2) }, enabled = canEditCompany, label = { Text("UF") }, singleLine = true, colors = appTextFieldColors(), modifier = Modifier.weight(0.7f))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, enabled = canEditCompany, label = { Text("Telefone / WhatsApp") }, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = email, onValueChange = { email = it }, enabled = canEditCompany, label = { Text("E-mail do Laboratório") }, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = website, onValueChange = { website = it }, enabled = canEditCompany, label = { Text("Website Oficial") }, singleLine = true, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = instagram, onValueChange = { instagram = it }, enabled = canEditCompany, label = { Text("Instagram Oficial") }, singleLine = true, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                    }

                    // Logotipo Oficial da Empresa
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth().testTag("company_logo_section_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Business,
                                        contentDescription = null,
                                        tint = NavyPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Logotipo Oficial da Empresa",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = NavyPrimary
                                    )
                                }
                                if (companyLogoUri.isNotBlank()) {
                                    Surface(
                                        color = Color(0xFFDCFCE7),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Logo Ativa", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "A logomarca da empresa será inserida nos cabeçalhos e capas de todos os laudos técnicos, certificados de conformidade e relatórios emitidos (PDF e Word).",
                                fontSize = 10.5.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 14.sp
                            )

                            if (companyLogoUri.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 90.dp, height = 55.dp)
                                            .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = File(companyLogoUri).takeIf { it.exists() } ?: companyLogoUri,
                                            contentDescription = "Prévia do Logotipo",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Arquivo: ${companyLogoUri.substringAfterLast("/")}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = NavyPrimary,
                                            maxLines = 1
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    logoPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(32.dp).testTag("change_logo_button")
                                            ) {
                                                Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Alterar", fontSize = 11.sp)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    CompanyLogoStorageHelper.deleteLogoFile(companyLogoUri)
                                                    companyLogoUri = ""
                                                    Toast.makeText(context, "Logotipo removido.", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(32.dp).testTag("remove_logo_button")
                                            ) {
                                                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Remover", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        logoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("select_company_logo_button")
                                ) {
                                    Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Selecionar Logotipo da Galeria / Arquivos", fontSize = 11.5.sp)
                                }
                            }
                        }
                    }

                    // 1.2 Papel Timbrado Oficial (Modelo de Fundo para Laudos e Relatórios)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (useLetterhead && letterheadUri.isNotBlank()) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (useLetterhead && letterheadUri.isNotBlank()) Color(0xFF86EFAC) else Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (useLetterhead && letterheadUri.isNotBlank()) StatusGreen else NavyPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Papel Timbrado Oficial (Modelo)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = NavyPrimary
                                        )
                                        Text(
                                            text = if (letterheadUri.isNotBlank()) "Fundo A4 ativo nos laudos e relatórios" else "Nenhum modelo carregado (usa cabeçalho padrão)",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }

                                if (letterheadUri.isNotBlank()) {
                                    Switch(
                                        checked = useLetterhead,
                                        onCheckedChange = { useLetterhead = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = StatusGreen
                                        ),
                                        modifier = Modifier.testTag("letterhead_switch")
                                    )
                                }
                            }

                            if (letterheadUri.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Thumbnail preview
                                    Box(
                                        modifier = Modifier
                                            .size(70.dp, 96.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                                            .background(Color.White)
                                            .clickable { showFullscreenLetterhead = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = File(letterheadUri),
                                            contentDescription = "Prévia do Papel Timbrado",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = "Ampliar",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Controls & info
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Modelo carregado com sucesso",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            color = NavyPrimary
                                        )

                                        // Opacity slider
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(imageVector = Icons.Default.Opacity, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Opacidade do Fundo: ${(letterheadOpacity * 100).roundToInt()}%",
                                                fontSize = 10.sp,
                                                color = Color(0xFF475569)
                                            )
                                        }
                                        Slider(
                                            value = letterheadOpacity,
                                            onValueChange = { letterheadOpacity = it },
                                            valueRange = 0.1f..1.0f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = NavyPrimary,
                                                activeTrackColor = ElectricBlue
                                            ),
                                            modifier = Modifier.fillMaxWidth().height(24.dp).testTag("letterhead_opacity_slider")
                                        )

                                        // Mode filter chips
                                        Text("Aplicar em:", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            FilterChip(
                                                selected = letterheadApplyMode == "ALL_PAGES",
                                                onClick = { letterheadApplyMode = "ALL_PAGES" },
                                                label = { Text("Todas as Pags", fontSize = 9.sp) },
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NavyPrimary, selectedLabelColor = Color.White),
                                                modifier = Modifier.height(28.dp)
                                            )
                                            FilterChip(
                                                selected = letterheadApplyMode == "FIRST_PAGE_ONLY",
                                                onClick = { letterheadApplyMode = "FIRST_PAGE_ONLY" },
                                                label = { Text("1ª Pág Apenas", fontSize = 9.sp) },
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NavyPrimary, selectedLabelColor = Color.White),
                                                modifier = Modifier.height(28.dp)
                                            )
                                            FilterChip(
                                                selected = letterheadApplyMode == "INNER_PAGES_ONLY",
                                                onClick = { letterheadApplyMode = "INNER_PAGES_ONLY" },
                                                label = { Text("Págs Internas", fontSize = 9.sp) },
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NavyPrimary, selectedLabelColor = Color.White),
                                                modifier = Modifier.height(28.dp)
                                            )
                                        }

                                        // Action buttons
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    letterheadPickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Trocar", fontSize = 10.sp)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    LetterheadStorageHelper.deleteLetterhead(context, letterheadUri)
                                                    letterheadUri = ""
                                                    useLetterhead = false
                                                    Toast.makeText(context, "Papel timbrado removido.", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Remover", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            } else {
                                // No letterhead loaded yet - offer Upload or Generate Default
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Carregue o papel timbrado oficial da sua empresa (PDF de 1 página ou Imagem PNG/JPG) para servir de fundo e moldura dos laudos técnicos e relatórios emitidos.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF475569),
                                        lineHeight = 15.sp
                                    )

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                letterheadPickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).testTag("select_letterhead_button")
                                        ) {
                                            Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Carregar Papel Timbrado (PDF / Imagem)", fontSize = 11.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val path = LetterheadStorageHelper.generateStandardLetterheadFile(
                                                    context,
                                                    companyName = companyName.ifEmpty { "JVM ENGENHARIA & TREINAMENTOS" },
                                                    cnpj = cnpj.ifEmpty { "43.344.821/0001-44" },
                                                    crea = creaCompany.ifEmpty { "26084/DF" },
                                                    phone = phone,
                                                    email = email,
                                                    address = address
                                                )
                                                if (path != null) {
                                                    letterheadUri = path
                                                    useLetterhead = true
                                                    Toast.makeText(context, "Modelo oficial gerado e aplicado!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.testTag("generate_letterhead_button")
                                        ) {
                                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp), tint = ElectricBlue)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Gerar Modelo Padrão", fontSize = 11.sp, color = ElectricBlue)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFE2E8F0))

                    Text("2. Responsáveis Técnicos e Operacionais (CREA / CFT)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)

                    // Engenheiro Responsável Técnico
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = rtName, onValueChange = { rtName = it }, enabled = canEditCompany, label = { Text("Nome do Responsável Técnico") }, colors = appTextFieldColors(), modifier = Modifier.weight(1.3f))
                        OutlinedTextField(value = rtTitle, onValueChange = { rtTitle = it }, enabled = canEditCompany, label = { Text("Cargo / Especialidade") }, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = rtCrea, onValueChange = { rtCrea = it }, enabled = canEditCompany, label = { Text("Nº Registro CREA do RT") }, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = rtRnp, onValueChange = { rtRnp = it }, enabled = canEditCompany, label = { Text("RNP / Registro Nacional") }, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                    }

                    // Assinatura Padrão do RT
                    SignaturePad(
                        title = "Assinatura Padrão: Responsável Técnico (CREA)",
                        signerName = rtName.ifBlank { "Engenheiro RT" },
                        signerRole = rtCrea.ifBlank { "CREA / RNP" },
                        initialSignature = rtSignatureImage,
                        savedProfileSignature = rtSignatureImage,
                        onSignatureConfirmed = { rtSignatureImage = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Analista / Técnico Executor
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = techName, onValueChange = { techName = it }, label = { Text("Analista / Técnico Executor") }, colors = appTextFieldColors(), modifier = Modifier.weight(1.3f))
                        OutlinedTextField(value = techCft, onValueChange = { techCft = it }, label = { Text("Nº CFT / CREA") }, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                    }

                    // Assinatura Padrão do Técnico
                    SignaturePad(
                        title = "Assinatura Padrão: Analista Executor (CFT/CREA)",
                        signerName = techName.ifBlank { "Analista Executor" },
                        signerRole = techCft.ifBlank { "CFT / CREA" },
                        initialSignature = techSignatureImage,
                        savedProfileSignature = techSignatureImage,
                        onSignatureConfirmed = { techSignatureImage = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFE2E8F0))

                    // Parâmetros de Emissão de Certificados & Laudos (iguais aos da web)
                    Text("Parâmetros de Emissão de Certificados & Laudos", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
                    Text("Modo Padrão de Emissão de Documentos", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            "laudo_e_certificado" to "Laudo Técnico + Certificado de Conformidade",
                            "apenas_laudo" to "Apenas Laudo Técnico Dielétrico",
                            "apenas_certificado" to "Apenas Certificado de Conformidade"
                        ).forEach { (value, text) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = emissionMode == value,
                                    onClick = { emissionMode = value },
                                    enabled = canEditCompany,
                                    colors = RadioButtonDefaults.colors(selectedColor = NavyPrimary)
                                )
                                Text(text, fontSize = 12.sp, color = Color(0xFF0F172A))
                            }
                        }
                    }
                    Text("Validade / Periodicidade Padrão de Reensaio", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("3", "6", "12", "24").forEach { months ->
                            FilterChip(
                                selected = validityMonths == months,
                                onClick = { validityMonths = months },
                                enabled = canEditCompany,
                                label = { Text("$months meses", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NavyPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = reportPrefix, onValueChange = { reportPrefix = it.uppercase() }, enabled = canEditCompany, label = { Text("Prefixo do Laudo") }, singleLine = true, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = certificatePrefix, onValueChange = { certificatePrefix = it.uppercase() }, enabled = canEditCompany, label = { Text("Prefixo do Certificado") }, singleLine = true, colors = appTextFieldColors(), modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = certificateTitle, onValueChange = { certificateTitle = it }, enabled = canEditCompany, label = { Text("Título Oficial do Certificado") }, colors = appTextFieldColors(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = approvalText, onValueChange = { approvalText = it }, enabled = canEditCompany, label = { Text("Parecer Padrão — Itens APROVADOS") }, minLines = 3, colors = appTextFieldColors(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = rejectionText, onValueChange = { rejectionText = it }, enabled = canEditCompany, label = { Text("Parecer Padrão — Itens REPROVADOS") }, minLines = 3, colors = appTextFieldColors(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = observationNote, onValueChange = { observationNote = it }, enabled = canEditCompany, label = { Text("Termo de Responsabilidade / Observações Normativas (rodapé)") }, minLines = 3, colors = appTextFieldColors(), modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Exigir Assinatura Digital do RT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            Text("Bloqueia emissão definitiva sem assinatura do responsável", fontSize = 10.sp, color = Color(0xFF64748B))
                        }
                        Switch(checked = requireSignature, onCheckedChange = { requireSignature = it }, enabled = canEditCompany)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gerar QR Code de Validação Pública", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            Text("Permite verificação instantânea por fiscalização e clientes", fontSize = 10.sp, color = Color(0xFF64748B))
                        }
                        Switch(checked = enableQrValidation, onCheckedChange = { enableQrValidation = it }, enabled = canEditCompany)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFE2E8F0))

                    // Portal Público de Validação (endereço impresso no QR Code)
                    Text("Portal Público de Validação", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
                    OutlinedTextField(
                        value = portalUrl,
                        onValueChange = { portalUrl = it.trim() },
                        enabled = canEditCompany,
                        label = { Text("URL do Portal de Validação") },
                        supportingText = { Text("Use {codigo} no lugar do código, ou só o endereço base (vira .../validar/CÓDIGO)", fontSize = 10.sp) },
                        singleLine = true,
                        colors = appTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Exemplo de link no QR Code: " + ValidationPortal.url("VAL-JVM-2610-A1B2C3D4E5", portalUrl),
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = if (canEditCompany)
                                "Ao salvar, estes dados e assinaturas serão o padrão do aplicativo e as configurações da empresa serão enviadas à plataforma web."
                            else
                                "Ao salvar, as assinaturas e preferências deste aparelho serão definidas como padrão (dados da empresa: somente administrador).",
                            fontSize = 11.sp,
                            color = NavyPrimary
                        )
                    }

                    Button(
                        onClick = {
                            val newSetting = (settings ?: AppSettingEntity()).copy(
                                id = "company_singleton",
                                name = companyName,
                                legalName = legalName,
                                cnpj = cnpj,
                                creaCompanyRegister = creaCompany,
                                technicalResponsibleName = rtName,
                                technicalResponsibleCrea = rtCrea,
                                technicalResponsibleSignatureImage = rtSignatureImage,
                                defaultTechnicianName = techName,
                                defaultTechnicianCftOrCrea = techCft,
                                defaultTechnicianSignatureImage = techSignatureImage,
                                address = address,
                                phone = phone,
                                email = email,
                                hostingerPlatformUrl = settings?.hostingerPlatformUrl ?: "https://mediumvioletred-bison-595566.hostingersite.com",
                                reportIntroText = reportIntro,
                                reportObjectiveText = reportObjective,
                                reportMethodologyText = reportMethodology,
                                reportDevelopmentText = reportDevelopment,
                                reportConclusionText = reportConclusion,
                                companyLogoUri = companyLogoUri,
                                letterheadUri = letterheadUri,
                                useLetterhead = useLetterhead && letterheadUri.isNotBlank(),
                                letterheadOpacity = letterheadOpacity,
                                letterheadApplyMode = letterheadApplyMode,
                                letterheadHeaderMode = letterheadHeaderMode,
                                supabaseUrl = supabaseUrl,
                                supabaseAnonKey = supabaseAnonKey,
                                supabaseAutoSync = supabaseAutoSync,
                                supabaseLastSyncTimestamp = settings?.supabaseLastSyncTimestamp ?: "",
                                defaultRetestMonths = validityMonths.toIntOrNull() ?: settings?.defaultRetestMonths ?: 6,
                                website = website
                            )
                            if (canEditCompany) {
                                // mesmos campos/formato da plataforma web (companies.lab_info)
                                val lab = CompanyLabInfo.fromSettings(newSetting)
                                lab.put("address", street.trim())
                                lab.put("number", addressNumber.trim())
                                lab.put("complement", complement.trim())
                                lab.put("neighborhood", neighborhood.trim())
                                lab.put("city", city.trim())
                                lab.put("state", uf.trim())
                                lab.put("cep", cep.trim())
                                lab.put("website", website.trim())
                                lab.put("instagram", instagram.trim())
                                lab.put("validationBaseUrl", ValidationPortal.normalize(portalUrl))
                                lab.put("technicalResponsible", CompanyLabInfo.technical(lab)
                                    .put("name", rtName.trim())
                                    .put("title", rtTitle.trim())
                                    .put("creaNumber", rtCrea.trim())
                                    .put("rnp", rtRnp.trim()))
                                lab.put("certificateEmissionSettings", CompanyLabInfo.emission(lab)
                                    .put("defaultEmissionMode", emissionMode)
                                    .put("defaultValidityMonths", validityMonths.toIntOrNull() ?: 6)
                                    .put("reportPrefix", reportPrefix.trim().ifBlank { "LAU-" })
                                    .put("certificatePrefix", certificatePrefix.trim().ifBlank { "CERT-" })
                                    .put("headerCustomTitle", certificateTitle.trim())
                                    .put("defaultApprovalText", approvalText.trim())
                                    .put("defaultRejectionText", rejectionText.trim())
                                    .put("standardObservationNote", observationNote.trim())
                                    .put("requireDigitalSignature", requireSignature)
                                    .put("enableQrCodeValidation", enableQrValidation))
                                viewModel.saveCompanySettings(newSetting, lab)
                                Toast.makeText(context, "Configurações salvas e enviadas à plataforma web!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.saveSettings(newSetting)
                                Toast.makeText(context, "Configurações e assinaturas salvas como padrão do aplicativo!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("settings_save_button")
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Salvar como Padrão do Aplicativo", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                }
            }
        }

        // Section 3: Textos Padrão do Relatório Completo
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("3. Conteúdo Textual do Relatório Completo (NR-10)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
                        }
                    }

                    Text(
                        "Personalize os textos oficiais de Introdução, Objetivo, Metodologia, Desenvolvimento e Conclusão que serão emitidos nos laudos e relatórios completos em PDF e Word.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )

                    // Introdução
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Introdução e Enquadramento Regulamentar", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = NavyPrimary)
                        OutlinedTextField(
                            value = reportIntro,
                            onValueChange = { reportIntro = it },
                            colors = appTextFieldColors(),
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Objetivo
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Objetivo do Laudo & Escopo da Avaliação", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = NavyPrimary)
                        OutlinedTextField(
                            value = reportObjective,
                            onValueChange = { reportObjective = it },
                            colors = appTextFieldColors(),
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Metodologia
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Metodologia e Procedimentos de Ensaio Dielétrico", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = NavyPrimary)
                        OutlinedTextField(
                            value = reportMethodology,
                            onValueChange = { reportMethodology = it },
                            colors = appTextFieldColors(),
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Desenvolvimento
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Desenvolvimento e Análise Técnica", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = NavyPrimary)
                        OutlinedTextField(
                            value = reportDevelopment,
                            onValueChange = { reportDevelopment = it },
                            colors = appTextFieldColors(),
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Conclusão
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Conclusão Técnica e Parecer de Aptidão", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = NavyPrimary)
                        OutlinedTextField(
                            value = reportConclusion,
                            onValueChange = { reportConclusion = it },
                            colors = appTextFieldColors(),
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                reportIntro = com.example.ui.components.DEFAULT_REPORT_INTRO
                                reportObjective = com.example.ui.components.DEFAULT_REPORT_OBJECTIVE
                                reportMethodology = com.example.ui.components.DEFAULT_REPORT_METHODOLOGY
                                reportDevelopment = com.example.ui.components.DEFAULT_REPORT_DEVELOPMENT
                                reportConclusion = com.example.ui.components.DEFAULT_REPORT_CONCLUSION
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restaurar Padrões", fontSize = 10.5.sp)
                        }

                        Button(
                            onClick = {
                                val newSetting = (settings ?: AppSettingEntity()).copy(
                                    id = "company_singleton",
                                    name = companyName,
                                    legalName = legalName,
                                    cnpj = cnpj,
                                    creaCompanyRegister = creaCompany,
                                    technicalResponsibleName = rtName,
                                    technicalResponsibleCrea = rtCrea,
                                    technicalResponsibleSignatureImage = rtSignatureImage,
                                    defaultTechnicianName = techName,
                                    defaultTechnicianCftOrCrea = techCft,
                                    defaultTechnicianSignatureImage = techSignatureImage,
                                    address = address,
                                    phone = phone,
                                    email = email,
                                    hostingerPlatformUrl = settings?.hostingerPlatformUrl ?: "https://mediumvioletred-bison-595566.hostingersite.com",
                                    defaultRetestMonths = settings?.defaultRetestMonths ?: 6,
                                    reportIntroText = reportIntro,
                                    reportObjectiveText = reportObjective,
                                    reportMethodologyText = reportMethodology,
                                    reportDevelopmentText = reportDevelopment,
                                    reportConclusionText = reportConclusion,
                                    companyLogoUri = companyLogoUri,
                                    letterheadUri = letterheadUri,
                                    useLetterhead = useLetterhead && letterheadUri.isNotBlank(),
                                    letterheadOpacity = letterheadOpacity,
                                    letterheadApplyMode = letterheadApplyMode,
                                    letterheadHeaderMode = letterheadHeaderMode,
                                    supabaseUrl = supabaseUrl,
                                    supabaseAnonKey = supabaseAnonKey,
                                    supabaseAutoSync = supabaseAutoSync,
                                    supabaseLastSyncTimestamp = settings?.supabaseLastSyncTimestamp ?: ""
                                )
                                viewModel.saveSettings(newSetting)
                                Toast.makeText(context, "Textos do relatório salvos com sucesso!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salvar Textos", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        // Backup & Demo Data Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth().testTag("local_backup_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(ElectricBlue.copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "4. Cópia de Segurança & Exportação Offline do Supabase",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = NavyPrimary
                            )
                        }
                    }

                    Text(
                        "Exporte e importe cópias de segurança do banco Supabase diretamente no dispositivo para contingência offline. O pacote inclui todos os cadastros, laudos e fotografias dos ensaios.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )

                    // Estatísticas atuais do banco e fotos locais
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Acervo do Dispositivo:", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                            Text(
                                "${testsList.size} laudos • ${clientsList.size} clientes • ${equipmentList.size} equip.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Fotos de Ensaios:", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                            Text(
                                "${photosStats.first} fotos (${LocalBackupManager.formatFileSize(photosStats.second)})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricBlue
                            )
                        }
                    }

                    // Botões Primários de Backup: Exportar ZIP e Importar Backup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isExportingZip = true
                                    progressStatus = "Iniciando exportação completa..."
                                    try {
                                        val file = viewModel.exportFullZipBackup { status ->
                                            progressStatus = status
                                        }
                                        exportedZipFile = file
                                        refreshBackupsAndStats()
                                        showExportSuccessDialog = true
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erro na exportação: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isExportingZip = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("export_full_zip_button")
                        ) {
                            Icon(imageVector = Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exportar Backup (.ZIP)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                openBackupLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/json",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("import_backup_button")
                        ) {
                            Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Importar Backup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Ações Secundárias
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val jsonStr = viewModel.exportBackupJson()
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_SUBJECT, "Backup_JVM_Dielectric_Lab_${System.currentTimeMillis()}.json")
                                    putExtra(Intent.EXTRA_TEXT, jsonStr)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Exportar Backup JSON"))
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("export_backup_button")
                        ) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exportar JSON", fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                        }

                        OutlinedButton(
                            onClick = {
                                showResetDemoConfirmDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("reset_demo_button")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Recarregar Supabase", fontSize = 10.5.sp, color = Color(0xFF059669), fontWeight = FontWeight.Medium)
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // Lista de Backups Locais Existentes no Aparelho
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Backups no Aparelho (Documents/JVM_Backups)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = NavyPrimary
                            )
                        }
                        IconButton(
                            onClick = { refreshBackupsAndStats() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Atualizar lista", tint = Color(0xFF64748B), modifier = Modifier.size(15.dp))
                        }
                    }

                    if (localBackups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Nenhum backup local salvo ainda. Clique em 'Exportar Backup (.ZIP)' para criar o primeiro.",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            localBackups.forEach { file ->
                                val isZip = file.name.endsWith(".zip", ignoreCase = true)
                                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                                val sizeStr = LocalBackupManager.formatFileSize(file.length())

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                        .border(0.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (isZip) Icons.Default.Archive else Icons.Default.Description,
                                            contentDescription = null,
                                            tint = if (isZip) ElectricBlue else Color(0xFF64748B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                file.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                                color = NavyPrimary,
                                                maxLines = 1
                                            )
                                            Text(
                                                "$dateStr • $sizeStr",
                                                fontSize = 9.5.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    isRestoring = true
                                                    progressStatus = "Inspecionando backup..."
                                                    val summary = viewModel.inspectBackup(Uri.fromFile(file))
                                                    isRestoring = false
                                                    if (summary != null) {
                                                        inspectionSummary = summary
                                                        showRestoreConfirmDialog = true
                                                    } else {
                                                        Toast.makeText(context, "Não foi possível inspecionar o arquivo.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Restaurar este backup",
                                                tint = StatusGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                LocalBackupManager.shareBackupFile(context, file)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Compartilhar arquivo",
                                                tint = ElectricBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                deleteConfirmFile = file
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Excluir arquivo",
                                                tint = StatusRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Supabase Cloud Integration Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3ECF8E).copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().testTag("supabase_integration_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(Color(0xFF3ECF8E).copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "3. Banco de Dados Oficial: Supabase (PostgreSQL Nuvem)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = NavyPrimary
                                )
                                Text(
                                    "Banco Único do Sistema • Sincronização em Tempo Real",
                                    fontSize = 10.sp,
                                    color = Color(0xFF059669),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Connection Status Pill
                        val isConnected = supabaseConnStatus?.isConnected == true
                        val hasTested = supabaseConnStatus != null
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = when {
                                isSupabaseTesting -> ElectricBlue.copy(alpha = 0.12f)
                                isConnected -> Color(0xFFDCFCE7)
                                hasTested -> Color(0xFFFEE2E2)
                                else -> Color(0xFFF1F5F9)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSupabaseTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = ElectricBlue)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Testando...", fontSize = 10.sp, color = ElectricBlue, fontWeight = FontWeight.Bold)
                                } else if (isConnected) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Online (${supabaseConnStatus?.latencyMs}ms)", fontSize = 10.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                                } else if (hasTested) {
                                    Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = StatusRed, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Offline / Erro", fontSize = 10.sp, color = StatusRed, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(imageVector = Icons.Default.Cloud, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Não testado", fontSize = 10.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }

                    Text(
                        "O Supabase é o único banco de dados oficial do sistema. Todos os cadastros de empresas, usuários, clientes, equipamentos, ordens de serviço e laudos técnicos são armazenados e centralizados nesta base em nuvem.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )

                    // Se a conexão foi testada com sucesso, exibe painel com contadores remotos
                    if (supabaseConnStatus?.isConnected == true) {
                        val status = supabaseConnStatus!!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF0FDF4), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.CheckCircleOutline, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(status.message, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF166534))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${status.remoteCompaniesCount}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                                    Text("Empresas", fontSize = 9.sp, color = Color(0xFF64748B))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${status.remoteUsersCount}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                                    Text("Usuários", fontSize = 9.sp, color = Color(0xFF64748B))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${status.remoteClientsCount}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                                    Text("Clientes", fontSize = 9.sp, color = Color(0xFF64748B))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${status.remoteEquipmentCount}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                                    Text("Equip.", fontSize = 9.sp, color = Color(0xFF64748B))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${status.remoteOrdersCount}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                                    Text("O.S.", fontSize = 9.sp, color = Color(0xFF64748B))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${status.remoteTestsCount}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                                    Text("Laudos", fontSize = 9.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    } else if (supabaseConnStatus != null && !supabaseConnStatus!!.isConnected) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                supabaseConnStatus!!.message,
                                fontSize = 11.sp,
                                color = StatusRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Campos de URL e Chave
                    OutlinedTextField(
                        value = supabaseUrl,
                        onValueChange = { supabaseUrl = it },
                        label = { Text("URL REST da Instância Supabase", fontSize = 11.sp) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(18.dp)) },
                        colors = appTextFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("supabase_url_field")
                    )

                    OutlinedTextField(
                        value = supabaseAnonKey,
                        onValueChange = { supabaseAnonKey = it },
                        label = { Text("Chave Pública Anônima (Anon Key)", fontSize = 11.sp) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { showKeyVisibility = !showKeyVisibility }) {
                                Icon(
                                    imageVector = if (showKeyVisibility) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showKeyVisibility) "Ocultar chave" else "Exibir chave",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (showKeyVisibility) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = appTextFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("supabase_anon_key_field")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Projeto: cdtbzbshylrcprvmjpgc",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                        OutlinedButton(
                            onClick = {
                                supabaseUrl = SupabaseService.DEFAULT_URL
                                supabaseAnonKey = SupabaseService.DEFAULT_ANON_KEY
                                Toast.makeText(context, "Credenciais oficiais redefinidas!", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Restaurar Padrão", fontSize = 10.sp)
                        }
                    }

                    // Switch Auto-Sync
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Auto-Sincronização em Tempo Real",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = NavyPrimary
                            )
                            Text(
                                "Salva dados localmente no SQLite e espelha em tempo real na nuvem Supabase",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Switch(
                            checked = supabaseAutoSync,
                            onCheckedChange = { supabaseAutoSync = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981)
                            )
                        )
                    }

                    // Ações de teste e salvar credenciais
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.testSupabaseConnection(supabaseUrl, supabaseAnonKey)
                            },
                            enabled = !isSupabaseTesting && !isSupabaseSyncing,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("test_supabase_button")
                        ) {
                            if (isSupabaseTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testar Conexão", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                val current = settings
                                val updatedSetting = (current ?: AppSettingEntity(name = companyName)).copy(
                                    supabaseUrl = supabaseUrl,
                                    supabaseAnonKey = supabaseAnonKey,
                                    supabaseAutoSync = supabaseAutoSync
                                )
                                viewModel.saveSettings(updatedSetting)
                                Toast.makeText(context, "Configurações do Supabase salvas!", Toast.LENGTH_SHORT).show()
                            },
                            enabled = !isSupabaseTesting && !isSupabaseSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("save_supabase_config_button")
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salvar Credenciais", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Hub de Sincronização Manual (Push, Pull, Sync All)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Hub de Sincronização de Dados",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = NavyPrimary
                            )
                            val lastSync = settings?.supabaseLastSyncTimestamp?.ifBlank { "Nunca" } ?: "Nunca"
                            Text(
                                "Última sincronização: $lastSync",
                                fontSize = 9.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        if (isSupabaseSyncing) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF059669))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Sincronizando com o Supabase na nuvem...", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.syncAllSupabase()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1.3f).testTag("sync_all_supabase_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sincronizar Tudo", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.pushToSupabase()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("push_supabase_button")
                                ) {
                                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp), tint = ElectricBlue)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Enviar (Push)", fontSize = 10.5.sp, color = ElectricBlue, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.pullFromSupabase()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("pull_supabase_button")
                                ) {
                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF059669))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Baixar (Pull)", fontSize = 10.5.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Audit Trail Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.History, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("5. Trilha de Auditoria e Logs do Sistema", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
                        }
                        Text("${auditLogs.size} registros", fontSize = 11.sp, color = Color(0xFF64748B))
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    if (auditLogs.isEmpty()) {
                        Text("Nenhum evento registrado até o momento.", fontSize = 11.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            auditLogs.take(8).forEach { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(log.action, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = NavyPrimary)
                                        Text(log.details.ifEmpty { log.description }, fontSize = 10.sp, color = Color(0xFF475569))
                                        Text("${log.userName} • ${log.dateTime.ifEmpty { log.timestamp }}", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 1. Diálogo de Progresso
    if (isExportingZip || isRestoring) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = {
                Text(
                    text = if (isExportingZip) "Exportando Backup Completo" else "Restaurando Dados",
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(44.dp))
                    Text(
                        text = progressStatus.ifEmpty { "Processando arquivos..." },
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        )
    }

    // 2. Diálogo de Sucesso na Exportação
    if (showExportSuccessDialog && exportedZipFile != null) {
        val zip = exportedZipFile!!
        AlertDialog(
            onDismissRequest = { showExportSuccessDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = StatusGreen,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("Backup Gerado com Sucesso!", fontWeight = FontWeight.Bold, color = NavyPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "O pacote de backup local foi criado com sucesso incluindo o banco de dados e as fotos dos ensaios.",
                        fontSize = 12.sp,
                        color = Color(0xFF334155)
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Arquivo: ${zip.name}", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = NavyPrimary)
                            Text("Tamanho: ${LocalBackupManager.formatFileSize(zip.length())}", fontSize = 11.sp, color = Color(0xFF475569))
                            Text("Pasta local: Documents/JVM_Backups", fontSize = 10.5.sp, color = Color(0xFF64748B))
                            Text("Fotos de ensaios empacotadas: ${photosStats.first} fotos", fontSize = 11.sp, color = ElectricBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Você pode salvar uma cópia em qualquer pasta do aparelho ou pendrive via SAF, ou compartilhar diretamente por WhatsApp / E-mail / Drive.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        saveZipLauncher.launch(zip.name)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salvar em Pasta (SAF)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            LocalBackupManager.shareBackupFile(context, zip)
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Compartilhar", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { showExportSuccessDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                    ) {
                        Text("Fechar", fontSize = 11.sp)
                    }
                }
            }
        )
    }

    // 3. Diálogo de Inspeção e Confirmação de Restauração
    if (showRestoreConfirmDialog && inspectionSummary != null) {
        val summary = inspectionSummary!!
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("Confirmar Restauração de Backup", fontWeight = FontWeight.Bold, color = NavyPrimary)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "O arquivo foi inspecionado com sucesso. Verifique os dados abaixo antes de prosseguir:",
                        fontSize = 11.5.sp,
                        color = Color(0xFF334155)
                    )

                    // Card de Resumo do Arquivo
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Arquivo: ${summary.fileName}", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = NavyPrimary)
                            Text("Tamanho: ${summary.fileSizeFormatted} • Tipo: ${if (summary.isZip) "Pacote ZIP com Fotos" else "Arquivo JSON"}", fontSize = 10.5.sp, color = Color(0xFF64748B))
                            Text("Exportado em: ${summary.exportedAt}", fontSize = 10.5.sp, color = Color(0xFF64748B))
                            Text("Empresa: ${summary.companyName}", fontSize = 10.5.sp, color = Color(0xFF64748B))

                            HorizontalDivider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 2.dp))

                            Text("Conteúdo a ser restaurado:", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = NavyPrimary)
                            Text("• Laudos de Ensaio: ${summary.testCount}", fontSize = 10.5.sp, color = Color(0xFF334155))
                            Text("• Fotos de Ensaios no arquivo: ${summary.photoCount}", fontSize = 10.5.sp, color = ElectricBlue, fontWeight = FontWeight.Bold)
                            Text("• Clientes cadastrados: ${summary.clientCount}", fontSize = 10.5.sp, color = Color(0xFF334155))
                            Text("• Equipamentos cadastrados: ${summary.equipmentCount}", fontSize = 10.5.sp, color = Color(0xFF334155))
                            Text("• Ordens de Serviço: ${summary.serviceOrderCount}", fontSize = 10.5.sp, color = Color(0xFF334155))
                            Text("• Instrumentos e Padrões: ${summary.instrumentCount}", fontSize = 10.5.sp, color = Color(0xFF334155))
                        }
                    }

                    // Opção de Restauração: Mesclar ou Substituir
                    Text("Modo de Restauração:", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = NavyPrimary)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { replaceMode = false }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !replaceMode,
                            onClick = { replaceMode = false },
                            colors = RadioButtonDefaults.colors(selectedColor = ElectricBlue)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Mesclar dados (Recomendado)", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = NavyPrimary)
                            Text("Adiciona e atualiza registros sem apagar dados locais não conflitantes.", fontSize = 10.sp, color = Color(0xFF64748B))
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { replaceMode = true }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = replaceMode,
                            onClick = { replaceMode = true },
                            colors = RadioButtonDefaults.colors(selectedColor = SafetyOrange)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Substituição Completa", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = SafetyOrange)
                            Text("Substitui o banco de dados atual estritamente pelo conteúdo deste backup.", fontSize = 10.sp, color = Color(0xFF64748B))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentSummary = inspectionSummary ?: return@Button
                        showRestoreConfirmDialog = false
                        scope.launch {
                            isRestoring = true
                            progressStatus = "Iniciando processo de restauração..."
                            try {
                                val result = viewModel.restoreBackup(
                                    uri = currentSummary.sourceUri,
                                    replaceAll = replaceMode,
                                    onProgress = { st -> progressStatus = st }
                                )
                                refreshBackupsAndStats()
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Erro na restauração: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isRestoring = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (replaceMode) SafetyOrange else StatusGreen)
                ) {
                    Text("Restaurar Agora", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text("Cancelar", fontSize = 11.sp)
                }
            }
        )
    }

    // 4. Diálogo de Confirmação para Excluir Arquivo de Backup Local
    if (deleteConfirmFile != null) {
        val fileToDelete = deleteConfirmFile!!
        AlertDialog(
            onDismissRequest = { deleteConfirmFile = null },
            icon = {
                Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusRed, modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Excluir Arquivo de Backup?", fontWeight = FontWeight.Bold, color = NavyPrimary)
            },
            text = {
                Text(
                    "Deseja realmente remover o arquivo '${fileToDelete.name}' do armazenamento do dispositivo? Esta ação não pode ser desfeita.",
                    fontSize = 12.sp,
                    color = Color(0xFF334155)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fileToDelete.exists()) {
                            fileToDelete.delete()
                            refreshBackupsAndStats()
                            Toast.makeText(context, "Arquivo excluído com sucesso.", Toast.LENGTH_SHORT).show()
                        }
                        deleteConfirmFile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Sim, Excluir", fontSize = 11.5.sp)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteConfirmFile = null }) {
                    Text("Cancelar", fontSize = 11.sp)
                }
            }
        )
    }

    // 5. Diálogo de Confirmação para Recarregar Dados do Supabase
    if (showResetDemoConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetDemoConfirmDialog = false },
            icon = {
                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Recarregar Dados do Supabase?", fontWeight = FontWeight.Bold, color = NavyPrimary)
            },
            text = {
                Text(
                    "Esta ação sincroniza e baixa todos os dados oficiais da sua empresa, equipamentos e laudos diretamente do banco na nuvem Supabase. Deseja continuar?",
                    fontSize = 12.sp,
                    color = Color(0xFF334155)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDemoConfirmDialog = false
                        viewModel.syncAllSupabase()
                        refreshBackupsAndStats()
                        Toast.makeText(context, "Sincronização com Supabase iniciada!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Text("Recarregar", fontSize = 11.5.sp)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetDemoConfirmDialog = false }) {
                    Text("Cancelar", fontSize = 11.sp)
                }
            }
        )
    }

    // 4. Diálogo de Visualização em Tela Cheia do Papel Timbrado
    if (showFullscreenLetterhead && letterheadUri.isNotBlank()) {
        Dialog(
            onDismissRequest = { showFullscreenLetterhead = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Visualização: Papel Timbrado Oficial", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NavyPrimary)
                            Text("Modelo de fundo para laudos técnicos e relatórios A4", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                        IconButton(onClick = { showFullscreenLetterhead = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = Color(0xFF64748B))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE2E8F0))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(595f / 842f)
                        ) {
                            AsyncImage(
                                model = File(letterheadUri),
                                contentDescription = "Papel Timbrado A4",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showFullscreenLetterhead = false },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Fechar Visualização")
                    }
                }
            }
        }
    }

    // Diálogo de Resultado da Sincronização Supabase
    if (supabaseSyncResult != null) {
        val res = supabaseSyncResult!!
        AlertDialog(
            onDismissRequest = { viewModel.clearSupabaseSyncResult() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (res.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (res.success) Color(0xFF16A34A) else StatusRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (res.success) "Sincronização Concluída" else "Sincronização com Alertas",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = NavyPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(res.message, fontSize = 12.5.sp, color = NavyPrimary)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("• Baixados da nuvem: ${res.companiesPulled} empresas, ${res.usersPulled} usuários, ${res.clientsPulled} clientes, ${res.equipmentPulled} equipamentos, ${res.ordersPulled} OS, ${res.testsPulled} laudos, ${res.instrumentsPulled} instrumentos", fontSize = 11.sp, color = Color(0xFF334155))
                        Text("• Enviados para nuvem: ${res.companiesPushed} empresas, ${res.usersPushed} usuários, ${res.clientsPushed} clientes, ${res.equipmentPushed} equipamentos, ${res.ordersPushed} OS, ${res.testsPushed} laudos, ${res.instrumentsPushed} instrumentos", fontSize = 11.sp, color = Color(0xFF334155))
                    }

                    if (res.errors.isNotEmpty()) {
                        Text("Ocorrências:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = StatusRed)
                        res.errors.forEach { err ->
                            Text("• $err", fontSize = 10.sp, color = StatusRed)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearSupabaseSyncResult() },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("OK")
                }
            }
        )
    }
}

