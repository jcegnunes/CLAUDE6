package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.service.FlukeLeakageManager
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.StatusRed
import java.util.Locale

/**
 * Botão "Medir pelo Bluetooth" ao lado do campo Corrente de fuga: conecta no alicate FLUKE 369 FC,
 * lê a corrente ao vivo (FlukeLeakageManager) e, ao confirmar, devolve o valor na unidade do campo.
 * @param unitLabel "mA" ou "µA" (o valor do alicate vem em mA; converte para µA quando o campo é µA).
 * @param onUse recebe o valor já na unidade do campo.
 */
@Composable
fun FlukeMeasureButton(unitLabel: String, onUse: (Double) -> Unit) {
    val context = LocalContext.current
    val manager = remember { FlukeLeakageManager(context) }
    val state by manager.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }

    // pede a permissão ANTES de abrir a janela (se a permissão aparecer por cima, a janela fecha sozinha)
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) { open = true; manager.startScanAndConnect() }
        else manager.setError("Permita o Bluetooth (dispositivos próximos) para medir com o alicate.")
    }
    fun askAndScan() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val granted = perms.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (granted) { open = true; manager.startScanAndConnect() } else permLauncher.launch(perms)
    }

    OutlinedButton(
        onClick = { askAndScan() },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Medir pelo Bluetooth (Fluke 369 FC)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
    }

    if (open) {
        val toField = { mA: Double -> if (unitLabel == "µA") mA * 1000.0 else mA }
        Dialog(
            onDismissRequest = { open = false; manager.disconnect() },
            properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Alicate Fluke 369 FC", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                Spacer(Modifier.height(12.dp))

                when {
                    state.error != null -> Text(state.error!!, color = StatusRed, fontSize = 12.sp)
                    state.leakageMilliAmp != null -> {
                        val valField = toField(state.leakageMilliAmp!!)
                        Text(
                            String.format(Locale.US, if (unitLabel == "µA") "%.1f" else "%.3f", valField) + " $unitLabel",
                            fontSize = 30.sp, fontWeight = FontWeight.Bold,
                            color = if (state.stable) NavyPrimary else Color(0xFF92400E)
                        )
                        if (!state.stable) Text("Leitura instável — aguarde estabilizar.", color = Color(0xFF92400E), fontSize = 11.sp)
                        else Text("Confira no visor do alicate antes de usar.", color = Color(0xFF64748B), fontSize = 11.sp)
                    }
                    state.connecting -> RowBusy("Conectando…")
                    state.connected -> RowBusy("Conectado. Aguardando a leitura…")
                    state.devices.isNotEmpty() -> {
                        Text(if (state.scanning) "Procurando… toque no alicate (369FC):" else "Toque no alicate na lista (ele aparece como \"369FC\"):",
                            fontSize = 12.sp, color = NavyPrimary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                            state.devices.forEach { dev ->
                                Column(
                                    Modifier.fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                        .clickable { manager.connectTo(dev.address) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(if (dev.name.isNotBlank()) dev.name else "(sem nome)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                                    Text("${dev.address} · sinal ${dev.rssi} dBm", fontSize = 10.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }
                    state.scanning -> RowBusy("Procurando o alicate…")
                    else -> RowBusy("Iniciando…")
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { open = false; manager.disconnect() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("Fechar", color = NavyPrimary)
                    }
                    Button(
                        onClick = { state.leakageMilliAmp?.let { onUse(toField(it)) }; open = false; manager.disconnect() },
                        enabled = state.leakageMilliAmp != null && state.stable,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Usar valor")
                    }
                }
            }
        }
        DisposableEffect(Unit) { onDispose { manager.disconnect() } }
    }
}

@Composable
private fun RowBusy(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = SafetyOrange)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = NavyPrimary)
    }
}
