package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.service.SupabaseService
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.StatusRed
import com.example.ui.theme.appTextFieldColors
import com.example.ui.theme.loginTextFieldColors
import com.example.ui.viewmodel.DielectricViewModel
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: DielectricViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var showDbSyncDialog by remember { mutableStateOf(false) }

    val supabaseConnStatus by viewModel.supabaseConnectionStatus.collectAsStateWithLifecycle()
    val isSupabaseTesting by viewModel.isSupabaseTesting.collectAsStateWithLifecycle()
    val isSupabaseSyncing by viewModel.isSupabaseSyncing.collectAsStateWithLifecycle()
    val supabaseSyncResult by viewModel.supabaseSyncResult.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    fun executeLogin() {
        val cleanUser = username.trim()
        val cleanPass = password.trim()

        if (cleanUser.isBlank()) {
            loginError = "Por favor, digite seu usuário ou e-mail cadastrado."
            return
        }
        if (cleanPass.isBlank()) {
            loginError = "Por favor, digite sua senha de acesso."
            return
        }

        loginError = null
        isLoggingIn = true

        coroutineScope.launch {
            val (success, err) = viewModel.login(cleanUser, cleanPass)
            isLoggingIn = false
            if (!success) {
                loginError = err ?: "Usuário ou senha incorretos. Verifique suas credenciais no banco de dados."
            } else {
                Toast.makeText(context, "Acesso autorizado com sucesso!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NavyDark,
                        NavyPrimary,
                        Color(0xFF0F172A)
                    )
                )
            )
    ) {
        // Top Bar Shortcuts (Settings & Database Sync Button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .clickable { showDbSyncDialog = true }
                    .testTag("login_open_db_sync_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Configurações e Sincronização com Banco de Dados",
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Banco & Sincronização",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            // Logo Badge
            Surface(
                shape = CircleShape,
                color = SafetyOrange,
                shadowElevation = 10.dp,
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.FlashOn,
                        contentDescription = "Logo JVM Lab",
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "JVM DIELECTRIC LAB",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Sistema de Ensaios Dielétricos & Laudos NR-10",
                color = Color(0xFFCBD5E1),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
            )

            // Login Card (Exclusively User and Password)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Acesso ao Sistema",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Informe seu usuário e senha cadastrados no banco de dados.",
                            fontSize = 12.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // Error Notification Box
                    if (loginError != null) {
                        Surface(
                            color = Color(0xFF450A0A),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7F1D1D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = StatusRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = loginError ?: "",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Field 1: User / Username / Email
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            loginError = null
                        },
                        placeholder = { Text("Usuário ou E-mail") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = if (username.isNotBlank()) SafetyOrange else Color(0xFF94A3B8)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        colors = loginTextFieldColors(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_username_input")
                    )

                    // Field 2: Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            loginError = null
                        },
                        placeholder = { Text("Senha") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (password.isNotBlank()) SafetyOrange else Color(0xFF94A3B8)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.testTag("login_password_visibility_button")
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                executeLogin()
                            }
                        ),
                        colors = loginTextFieldColors(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_password_input")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Login Action Button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            executeLogin()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SafetyOrange,
                            disabledContainerColor = SafetyOrange.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoggingIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("login_submit_button")
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Autenticando...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Acessar Plataforma",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Direct button to show Database Sync and Status
            OutlinedButton(
                onClick = {
                    showDbSyncDialog = true
                    viewModel.testSupabaseConnection()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Configurações & Sincronização com Banco de Dados",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE2E8F0)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Security & Database Status Footer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Banco de Dados Central: Supabase PostgreSQL Nuvem",
                    color = Color(0xFF64748B),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            // versão do APK instalado (muda a cada build): mostra qual atualização está no aparelho
            Text(
                text = "Versão ${com.example.BuildConfig.VERSION_NAME}",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                modifier = Modifier.testTag("login_app_version")
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Modal Dialog: Database Sync & Status
    if (showDbSyncDialog) {
        AlertDialog(
            onDismissRequest = { showDbSyncDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = Color(0xFF059669),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Banco de Dados & Sincronização",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = NavyPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "O sistema está configurado para operar com o banco de dados oficial do Supabase em nuvem.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )

                    // Endpoint Information
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Servidor Supabase:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                            Text(
                                SupabaseService.DEFAULT_URL,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = NavyPrimary
                            )
                        }
                    }

                    // Status Pill
                    val isConnected = supabaseConnStatus?.isConnected == true
                    val hasTested = supabaseConnStatus != null
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            isSupabaseTesting -> ElectricBlue.copy(alpha = 0.12f)
                            isConnected -> Color(0xFFDCFCE7)
                            hasTested -> Color(0xFFFEE2E2)
                            else -> Color(0xFFF8FAFC)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when {
                                isConnected -> Color(0xFF86EFAC)
                                hasTested -> Color(0xFFFCA5A5)
                                else -> Color(0xFFE2E8F0)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSupabaseTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ElectricBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testando conexão...", fontSize = 11.5.sp, color = ElectricBlue, fontWeight = FontWeight.Bold)
                            } else if (isConnected) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Conectado ao Supabase (${supabaseConnStatus?.latencyMs}ms)", fontSize = 11.5.sp, color = Color(0xFF166534), fontWeight = FontWeight.Bold)
                            } else if (hasTested) {
                                Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = StatusRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Falha na conexão: ${supabaseConnStatus?.message}", fontSize = 11.sp, color = StatusRed, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Conexão não testada", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    }

                    // Botão para Copiar SQL de Liberação
                    if (hasTested && !isConnected) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val sqlScript = "GRANT USAGE ON SCHEMA public TO anon, authenticated;\nGRANT ALL ON ALL TABLES IN SCHEMA public TO anon, authenticated;\nGRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated;\nALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated;"
                                val clip = ClipData.newPlainText("SQL Supabase", sqlScript)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Comando SQL copiado! Cole no SQL Editor do Supabase.", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB91C1C)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copiar Comando SQL de Liberação", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Configuração Opcional de Chave (ex: Service Role Key)
                    var showKeyConfig by remember { mutableStateOf(false) }
                    var customApiKey by remember { mutableStateOf("") }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showKeyConfig = !showKeyConfig },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showKeyConfig) "▲ Ocultar troca de Chave" else "▼ Inserir Service Role Key / Trocar Chave",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ElectricBlue
                        )
                    }

                    if (showKeyConfig) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Se preferir, cole a chave 'service_role secret' do Supabase (que ignora permissões de RLS):", fontSize = 10.sp, color = Color(0xFF64748B))
                            OutlinedTextField(
                                value = customApiKey,
                                onValueChange = { customApiKey = it },
                                placeholder = { Text("Chave service_role ou anon do Supabase", fontSize = 11.sp) },
                                singleLine = true,
                                colors = appTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (customApiKey.isNotBlank()) {
                                        val curSetting = settings
                                        val updated = (curSetting ?: com.example.data.model.AppSettingEntity()).copy(supabaseAnonKey = customApiKey.trim())
                                        viewModel.saveSettings(updated)
                                        viewModel.testSupabaseConnection(customKey = customApiKey.trim())
                                        Toast.makeText(context, "Chave atualizada e testada!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Salvar e Testar Nova Chave", fontSize = 11.sp)
                            }
                        }
                    }

                    // Remote Counts if connected
                    if (supabaseConnStatus?.isConnected == true) {
                        val status = supabaseConnStatus!!
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${status.remoteUsersCount}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                                Text("Usuários", fontSize = 9.sp, color = Color(0xFF64748B))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${status.remoteCompaniesCount}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                                Text("Empresas", fontSize = 9.sp, color = Color(0xFF64748B))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${status.remoteClientsCount}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                                Text("Clientes", fontSize = 9.sp, color = Color(0xFF64748B))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${status.remoteTestsCount}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                                Text("Laudos", fontSize = 9.sp, color = Color(0xFF64748B))
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // Action: Test Connection
                    OutlinedButton(
                        onClick = { viewModel.testSupabaseConnection() },
                        enabled = !isSupabaseTesting,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testar Conexão Agora", fontSize = 11.5.sp)
                    }

                    // Action: Synchronize All Data Now
                    Button(
                        onClick = {
                            viewModel.syncAllSupabase()
                            Toast.makeText(context, "Sincronização iniciada...", Toast.LENGTH_SHORT).show()
                        },
                        enabled = !isSupabaseSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSupabaseSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sincronizando...", fontSize = 11.5.sp, color = Color.White)
                        } else {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Puxar Dados do Supabase Agora", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Result of sync if any
                    if (supabaseSyncResult != null) {
                        val res = supabaseSyncResult!!
                        Surface(
                            color = if (res.success) Color(0xFFDCFCE7) else Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${res.message} (${res.usersPulled} usuários carregados)",
                                fontSize = 10.5.sp,
                                color = if (res.success) Color(0xFF166534) else StatusRed,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDbSyncDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Fechar", fontSize = 12.sp)
                }
            }
        )
    }
}
