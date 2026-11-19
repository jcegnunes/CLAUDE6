package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppSettingEntity
import com.example.ui.components.CertificadoDialog
import com.example.ui.components.EditTestDialog
import com.example.ui.components.LaudoDialog
import com.example.ui.components.NiimbotLabelDialog
import com.example.ui.screens.BackupSettingsScreen
import com.example.ui.screens.CertificateValidationScreen
import com.example.ui.screens.ClientsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EquipmentScreen
import com.example.ui.screens.LabInstrumentsScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.NormsScreen
import com.example.ui.screens.ReportEmissionScreen
import com.example.ui.screens.ServiceOrdersScreen
import com.example.ui.screens.TestWizardScreen
import com.example.ui.screens.TestsListScreen
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.viewmodel.DielectricViewModel
import com.example.ui.viewmodel.DielectricViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: DielectricViewModel by viewModels {
        DielectricViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: DielectricViewModel) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentCompany by viewModel.currentCompany.collectAsStateWithLifecycle()
    val filterByMyCompany by viewModel.filterByMyCompany.collectAsStateWithLifecycle()

    // If user is not logged in, display the Login & Company/User Registration Screen
    if (!isLoggedIn || currentUser == null) {
        LoginScreen(viewModel = viewModel)
        return
    }

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val activeLaudo by viewModel.activeLaudoTest.collectAsStateWithLifecycle()
    val activeCertificado by viewModel.activeCertificadoTest.collectAsStateWithLifecycle()
    val activeNiimbot by viewModel.activeNiimbotTest.collectAsStateWithLifecycle()
    val activeEditingTest by viewModel.editingTest.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isSupabaseSyncing by viewModel.isSupabaseSyncing.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var userMenuExpanded by remember { mutableStateOf(false) }

    val fallbackCompany = AppSettingEntity(
        id = "company_singleton",
        name = currentCompany?.name ?: "JVM Engenharia",
        legalName = currentCompany?.legalName ?: "JVM Engenharia Ltda.",
        cnpj = currentCompany?.cnpj?.ifBlank { "29.894.500/0001-04" } ?: "29.894.500/0001-04",
        creaCompanyRegister = currentCompany?.creaCompanyRegister?.ifBlank { "CREA-DF 1620-PJ" } ?: "CREA-DF 1620-PJ",
        address = currentCompany?.address?.ifBlank { "Brasília - DF" } ?: "Brasília - DF",
        phone = currentCompany?.phone?.ifBlank { "(61) 98115-0000" } ?: "(61) 98115-0000",
        email = currentCompany?.email?.ifBlank { "joao.nunes@jvmengenharia.com.br" } ?: "joao.nunes@jvmengenharia.com.br",
        website = currentCompany?.website?.ifBlank { "www.jvmengenharia.com.br" } ?: "www.jvmengenharia.com.br",
        hostingerPlatformUrl = "https://mediumvioletred-bison-595566.hostingersite.com",
        technicalResponsibleName = currentUser?.name ?: "Eng. João Carlos Evaristo Guedes Nunes",
        technicalResponsibleCrea = currentUser?.registrationCouncil ?: "CREA 24481/D-DF",
        defaultTechnicianName = currentUser?.name ?: "Eng. João Carlos Evaristo Guedes Nunes",
        defaultTechnicianCftOrCrea = currentUser?.registrationCouncil ?: "CREA 24481/D-DF",
        defaultRetestMonths = 6
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = NavyDark,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyPrimary)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(SafetyOrange, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("JVM", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("JVM DIELECTRIC LAB", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White)
                            Text("Ensaios Dielétricos NR-10", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                    }

                    // Active User & Company Card in Drawer
                    Surface(
                        color = Color(0xFF162032),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = ElectricBlue,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = currentUser?.name?.take(2)?.uppercase() ?: "US",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentUser?.name ?: "Usuário",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentUser?.role ?: "Profissional",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Apartment,
                                    contentDescription = null,
                                    tint = SafetyOrange,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentCompany?.name ?: currentUser?.companyName ?: "JVM Engenharia",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.logout()
                                    coroutineScope.launch { drawerState.close() }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .testTag("drawer_logout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ExitToApp,
                                    contentDescription = "Sair",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Trocar / Sair", color = Color(0xFFF87171), fontSize = 11.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DrawerItem("Painel Geral", Icons.Default.Dashboard, "dashboard", currentScreen) {
                        viewModel.navigateTo("dashboard")
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Novo Ensaio (Wizard)", Icons.Default.Science, "wizard", currentScreen) {
                        viewModel.startNewTest()
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Laudos & Certificados", Icons.Default.Description, "tests", currentScreen) {
                        viewModel.navigateTo("tests")
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Inventário EPI / EPC", Icons.Default.Shield, "equipment", currentScreen) {
                        viewModel.navigateTo("equipment")
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Ordens de Serviço (OS)", Icons.Default.Assignment, "service_orders", currentScreen) {
                        viewModel.navigateTo("service_orders")
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Clientes Tomadores", Icons.Default.Business, "clients", currentScreen) {
                        viewModel.navigateTo("clients")
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Padrões do Laboratório", Icons.Default.Build, "instruments", currentScreen) {
                        viewModel.navigateTo("instruments")
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Tabelas & Normas", Icons.Default.MenuBook, "norms", currentScreen) {
                        viewModel.navigateTo("norms")
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Emissão de Relatórios", Icons.Default.Description, "reports", currentScreen) {
                        viewModel.navigateTo("reports")
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Validação Digital QR", Icons.Default.QrCodeScanner, "validation", currentScreen) {
                        viewModel.navigateTo("validation")
                        coroutineScope.launch { drawerState.close() }
                    }
                    DrawerItem("Configurações & Banco de Dados (Supabase)", Icons.Default.CloudSync, "settings", currentScreen) {
                        viewModel.navigateTo("settings")
                        coroutineScope.launch { drawerState.close() }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(SafetyOrange, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("JVM", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "JVM Ensaios Dielétricos",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("app_menu_button")
                        ) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                    },
                    actions = {
                        // Direct button to Settings & Database Synchronization
                        IconButton(
                            onClick = { viewModel.navigateTo("settings") },
                            modifier = Modifier.testTag("topbar_sync_settings_button")
                        ) {
                            if (isSupabaseSyncing) {
                                CircularProgressIndicator(
                                    color = Color(0xFF34D399),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = "Configurações e Sincronização",
                                    tint = Color(0xFF34D399)
                                )
                            }
                        }

                        Box {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF1E3A5F),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clickable { userMenuExpanded = true }
                                    .testTag("user_profile_header_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = SafetyOrange,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = currentUser?.name?.take(2)?.uppercase() ?: "US",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = currentUser?.name?.split(" ")?.firstOrNull() ?: "Perfil",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = userMenuExpanded,
                                onDismissRequest = { userMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = currentUser?.name ?: "Usuário",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "${currentUser?.role} • ${currentUser?.registrationCouncil}",
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "🏢 ${currentCompany?.name ?: currentUser?.companyName ?: "JVM Engenharia"}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = SafetyOrange
                                            )
                                        }
                                    },
                                    onClick = { userMenuExpanded = false }
                                )

                                HorizontalDivider()

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CloudSync,
                                                contentDescription = null,
                                                tint = Color(0xFF059669),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Configurações & Banco de Dados",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    },
                                    onClick = {
                                        userMenuExpanded = false
                                        viewModel.navigateTo("settings")
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (filterByMyCompany) Icons.Filled.Check else Icons.Filled.FilterAlt,
                                                contentDescription = null,
                                                tint = if (filterByMyCompany) ElectricBlue else Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (filterByMyCompany) "Exibindo apenas ${currentCompany?.name ?: "minha empresa"}" else "Exibir todos os registros",
                                                fontSize = 12.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.toggleFilterByMyCompany()
                                        userMenuExpanded = false
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.SwapHoriz,
                                                contentDescription = null,
                                                tint = ElectricBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Trocar de Usuário / Empresa", fontSize = 12.sp)
                                        }
                                    },
                                    onClick = {
                                        userMenuExpanded = false
                                        viewModel.logout()
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.ExitToApp,
                                                contentDescription = null,
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Sair do Sistema", color = Color(0xFFEF4444), fontSize = 12.sp)
                                        }
                                    },
                                    onClick = {
                                        userMenuExpanded = false
                                        viewModel.logout()
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = NavyPrimary
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentScreen == "dashboard",
                        onClick = { viewModel.navigateTo("dashboard") },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Início") },
                        label = { Text("Painel", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NavyPrimary,
                            selectedTextColor = NavyPrimary,
                            indicatorColor = Color(0xFFEFF6FF)
                        )
                    )
                    NavigationBarItem(
                        selected = currentScreen == "wizard",
                        onClick = { viewModel.startNewTest() },
                        icon = { Icon(Icons.Default.Science, contentDescription = "Novo") },
                        label = { Text(if (activeEditingTest != null && currentScreen == "wizard") "Editar Laudo" else "Novo Ensaio", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SafetyOrange,
                            selectedTextColor = SafetyOrange,
                            indicatorColor = Color(0xFFFFF7ED)
                        )
                    )
                    NavigationBarItem(
                        selected = currentScreen == "tests",
                        onClick = { viewModel.navigateTo("tests") },
                        icon = { Icon(Icons.Default.Description, contentDescription = "Laudos") },
                        label = { Text("Laudos", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NavyPrimary,
                            selectedTextColor = NavyPrimary,
                            indicatorColor = Color(0xFFEFF6FF)
                        )
                    )
                    NavigationBarItem(
                        selected = currentScreen == "equipment",
                        onClick = { viewModel.navigateTo("equipment") },
                        icon = { Icon(Icons.Default.Shield, contentDescription = "EPIs") },
                        label = { Text("EPI / EPC", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NavyPrimary,
                            selectedTextColor = NavyPrimary,
                            indicatorColor = Color(0xFFEFF6FF)
                        )
                    )
                    NavigationBarItem(
                        selected = currentScreen == "settings",
                        onClick = { viewModel.navigateTo("settings") },
                        icon = { Icon(Icons.Default.CloudSync, contentDescription = "Configurações e Banco de Dados") },
                        label = { Text("Config & Sync", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF059669),
                            selectedTextColor = Color(0xFF059669),
                            indicatorColor = Color(0xFFECFDF5)
                        )
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isSupabaseSyncing) {
                    Surface(
                        color = Color(0xFF0F766E),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sincronizando com banco Supabase... Baixando dados existentes",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            "dashboard" -> DashboardScreen(viewModel = viewModel)
                            "wizard" -> TestWizardScreen(viewModel = viewModel)
                            "tests" -> TestsListScreen(viewModel = viewModel)
                            "equipment" -> EquipmentScreen(viewModel = viewModel)
                            "service_orders" -> ServiceOrdersScreen(viewModel = viewModel)
                            "clients" -> ClientsScreen(viewModel = viewModel)
                            "instruments" -> LabInstrumentsScreen(viewModel = viewModel)
                            "norms" -> NormsScreen(viewModel = viewModel)
                            "reports" -> ReportEmissionScreen(viewModel = viewModel)
                            "validation" -> CertificateValidationScreen(viewModel = viewModel)
                            "settings" -> BackupSettingsScreen(viewModel = viewModel)
                            else -> DashboardScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    // Global Document Dialogs
    if (activeLaudo != null) {
        LaudoDialog(
            test = activeLaudo!!,
            company = settings ?: fallbackCompany,
            onDismiss = { viewModel.closeLaudo() },
            onOpenNiimbot = {
                val t = activeLaudo!!
                viewModel.closeLaudo()
                viewModel.openNiimbotLabel(t)
            },
            onEditTest = {
                val t = activeLaudo!!
                viewModel.closeLaudo()
                viewModel.openEditTest(t)
            }
        )
    }

    if (activeCertificado != null) {
        CertificadoDialog(
            test = activeCertificado!!,
            company = settings ?: fallbackCompany,
            onDismiss = { viewModel.closeCertificado() },
            onOpenNiimbot = {
                val t = activeCertificado!!
                viewModel.closeCertificado()
                viewModel.openNiimbotLabel(t)
            },
            onEditTest = {
                val t = activeCertificado!!
                viewModel.closeCertificado()
                viewModel.openEditTest(t)
            }
        )
    }

    if (activeNiimbot != null) {
        NiimbotLabelDialog(
            test = activeNiimbot!!,
            company = settings ?: fallbackCompany,
            onDismiss = { viewModel.closeNiimbotLabel() }
        )
    }


}

@Composable
private fun DrawerItem(
    title: String,
    icon: ImageVector,
    route: String,
    currentScreen: String,
    onClick: () -> Unit
) {
    val isSelected = currentScreen == route
    NavigationDrawerItem(
        label = { Text(title, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
        icon = { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        selected = isSelected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = NavyPrimary,
            selectedIconColor = SafetyOrange,
            selectedTextColor = Color.White,
            unselectedIconColor = Color(0xFF94A3B8),
            unselectedTextColor = Color(0xFFCBD5E1)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}
