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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.example.data.service.UnitEnvManager
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.StatusRed
import java.util.Locale

/**
 * Botão "Medir pelo Bluetooth (UT333BT)" ao lado de Temperatura/Umidade: conecta no medidor UNI-T,
 * lê temperatura e umidade ao vivo e, ao confirmar, preenche os dois campos.
 */
@Composable
fun UnitEnvMeasureButton(onUse: (temperatureC: Double, humidityPercent: Double) -> Unit) {
    val context = LocalContext.current
    val manager = remember { UnitEnvManager(context) }
    val state by manager.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) { open = true; manager.startScanAndConnect() }
        else manager.setError("Permita o Bluetooth (dispositivos próximos) para medir com o aparelho.")
    }
    fun askAndScan() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val granted = perms.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (granted) { open = true; manager.startScanAndConnect() } else permLauncher.launch(perms)
    }

    OutlinedButton(onClick = { askAndScan() }, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Medir temperatura/umidade pelo Bluetooth (UT333BT)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
    }

    if (open) {
        Dialog(onDismissRequest = { open = false; manager.disconnect() }, properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true)) {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Medidor UT333BT", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                Spacer(Modifier.height(12.dp))

                val hasReading = state.temperatureC != null || state.humidityPercent != null
                when {
                    state.error != null -> Text(state.error!!, color = StatusRed, fontSize = 12.sp)
                    hasReading -> {
                        Text((state.temperatureC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "-- °C"), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Text((state.humidityPercent?.let { String.format(Locale.US, "%.1f %% UR", it) } ?: "-- % UR"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0369A1))
                        Text("Confira no visor do aparelho antes de usar.", color = Color(0xFF64748B), fontSize = 11.sp)
                    }
                    state.connecting -> RowBusy("Conectando…")
                    state.connected -> RowBusy("Conectado. Aguardando a leitura…")
                    state.devices.isNotEmpty() -> {
                        Text(if (state.scanning) "Procurando… toque no medidor (UT333BT):" else "Toque no medidor na lista (UT333BT):", fontSize = 12.sp, color = NavyPrimary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                            state.devices.forEach { dev ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)).clickable { manager.connectTo(dev.address) }.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(if (dev.name.isNotBlank()) dev.name else "(sem nome)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                                    Text("${dev.address} · sinal ${dev.rssi} dBm", fontSize = 10.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }
                    state.scanning -> RowBusy("Procurando o medidor…")
                    else -> RowBusy("Iniciando…")
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { open = false; manager.disconnect() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("Fechar", color = NavyPrimary) }
                    Button(
                        onClick = { onUse(state.temperatureC ?: 0.0, state.humidityPercent ?: 0.0); open = false; manager.disconnect() },
                        enabled = state.temperatureC != null && state.humidityPercent != null,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Usar")
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
