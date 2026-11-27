package com.example.data.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Medidor de temperatura e umidade UNI-T UT333BT pelo Bluetooth.
 * Protocolo (do registro HCI): anuncia-se como "UT333BT"; depois de conectar, liga a notificação em 0xFF02
 * e fica "cutucando" o aparelho (escreve o byte 0x5E em 0xFF01) — a cada cutucada ele manda uma leitura.
 * A leitura vem em TEXTO: umidade "69.4%RH" ou temperatura "28.0 °C" (alternadas).
 */
@SuppressLint("MissingPermission")
class UnitEnvManager(private val context: Context) {

    data class Found(val address: String, val name: String, val rssi: Int)

    data class EnvState(
        val scanning: Boolean = false,
        val connecting: Boolean = false,
        val connected: Boolean = false,
        val deviceName: String? = null,
        val devices: List<Found> = emptyList(),
        val temperatureC: Double? = null,
        val humidityPercent: Double? = null,
        val error: String? = null
    )

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(EnvState())
    val state: StateFlow<EnvState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var scanCb: ScanCallback? = null
    private var device: android.bluetooth.BluetoothDevice? = null
    private var deviceName = "UT333BT"
    private var retries = 0
    private var everConnected = false
    private var polling = false
    private val foundMap = LinkedHashMap<String, Found>()

    companion object {
        private val SVC = UUID.fromString("0000ff12-0000-1000-8000-00805f9b34fb")
        private val CH_NOTIFY = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
        private val CH_WRITE = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val POLL = 0x5E.toByte()
        private const val POLL_MS = 300L
        private const val SCAN_TIMEOUT_MS = 15000L
        private fun isUnit(name: String?): Boolean =
            name != null && (name.contains("UT333", true) || name.contains("UT-333", true) || name.startsWith("UT", true))

        /** Decodifica a notificação (texto): devolve 'T' (temperatura) ou 'H' (umidade) e o valor. Público p/ teste. */
        fun decode(b: ByteArray): Pair<Char, Double>? {
            if (b.size < 8) return null
            val txt = StringBuilder()
            for (i in 4 until b.size - 3) { val c = b[i].toInt() and 0xFF; if (c in 32..126) txt.append(c.toChar()) }
            val s = txt.toString()
            // o valor real tem casa decimal ("69.4", "28.0"); o 1º dígito do pacote (código) e o sufixo não têm,
            // por isso exigimos o ponto decimal para não pegar o dígito errado
            val num = Regex("-?\\d+\\.\\d+").find(s)?.value?.toDoubleOrNull()
                ?: Regex("-?\\d+\\.?\\d*").findAll(s).map { it.value }.firstOrNull { it.contains('.') }?.toDoubleOrNull()
                ?: return null
            val isHum = s.contains("%") || s.contains("RH", true) || (b.size > 4 && b[4].toInt() and 0xFF == 0x34)
            return (if (isHum) 'H' else 'T') to num
        }
    }

    fun setError(msg: String) { _state.value = EnvState(error = msg) }
    fun problem(): String? {
        if (adapter == null) return "Este celular não tem Bluetooth."
        if (adapter.isEnabled != true) return "Ligue o Bluetooth do celular e tente de novo."
        return null
    }

    fun startScanAndConnect() {
        val p = problem()
        if (p != null) { _state.value = EnvState(error = p); return }
        stopScan(); foundMap.clear()
        _state.value = EnvState(scanning = true)
        val scanner = adapter?.bluetoothLeScanner ?: run { _state.value = EnvState(error = "Não foi possível iniciar a busca Bluetooth."); return }
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val rec = result.scanRecord
                val name = rec?.deviceName ?: result.device.name
                val hasSvc = rec?.serviceUuids?.any { it.uuid == SVC || it.uuid.toString().contains("ff12", true) } == true
                if (isUnit(name) || hasSvc) { stopScan(); connect(result.device, name ?: "UT333BT"); return }
                val addr = result.device.address ?: return
                foundMap[addr] = Found(addr, name ?: "", result.rssi)
                _state.value = _state.value.copy(devices = foundMap.values.sortedByDescending { it.rssi })
            }
            override fun onScanFailed(errorCode: Int) { _state.value = EnvState(error = "A busca Bluetooth falhou (código $errorCode).") }
        }
        scanCb = cb
        try { scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb) }
        catch (e: Exception) { _state.value = EnvState(error = "Não foi possível buscar: ${e.message}"); return }
        main.postDelayed({
            if (_state.value.scanning) {
                stopScan()
                _state.value = if (foundMap.isEmpty()) _state.value.copy(scanning = false, error = "Nenhum aparelho Bluetooth encontrado. Ligue o medidor e aproxime do celular.")
                else _state.value.copy(scanning = false)
            }
        }, SCAN_TIMEOUT_MS)
    }

    fun connectTo(address: String) {
        stopScan()
        val dev = try { adapter?.getRemoteDevice(address) } catch (_: Exception) { null } ?: run { _state.value = _state.value.copy(error = "Endereço Bluetooth inválido."); return }
        connect(dev, foundMap[address]?.name?.takeIf { it.isNotBlank() } ?: "Medidor")
    }

    private fun stopScan() {
        scanCb?.let { try { adapter?.bluetoothLeScanner?.stopScan(it) } catch (_: Exception) {} }
        scanCb = null
    }

    private fun connect(dev: android.bluetooth.BluetoothDevice, name: String) {
        device = dev; deviceName = name; retries = 0; everConnected = false
        doConnect()
    }
    private fun doConnect() {
        val dev = device ?: return
        _state.value = EnvState(connecting = true, deviceName = deviceName)
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) dev.connectGatt(context, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
        else dev.connectGatt(context, false, callback)
        if (gatt == null) _state.value = EnvState(error = "Não foi possível conectar ao medidor.")
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            val g = gatt ?: return
            val ch = g.getService(SVC)?.getCharacteristic(CH_WRITE) ?: return
            try {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(ch, byteArrayOf(POLL), BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                else { ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; ch.value = byteArrayOf(POLL); g.writeCharacteristic(ch) }
            } catch (_: Exception) {}
            if (polling) main.postDelayed(this, POLL_MS)
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                everConnected = true
                main.postDelayed({ try { g.discoverServices() } catch (_: Exception) {} }, 300)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                polling = false
                try { g.close() } catch (_: Exception) {}
                gatt = null
                if (!everConnected && retries < 2) { retries += 1; main.postDelayed({ if (_state.value.connecting) doConnect() }, 700) }
                else {
                    val msg = if (!everConnected) "Não foi possível conectar ao medidor. Ligue-o, aproxime do celular, desligue e ligue o Bluetooth e tente de novo."
                    else "A conexão com o medidor caiu. Tente de novo."
                    main.post { _state.value = _state.value.copy(connecting = false, connected = false, error = msg) }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(SVC)?.getCharacteristic(CH_NOTIFY)
            if (ch == null) { main.post { _state.value = _state.value.copy(connecting = false, error = "Este aparelho não é um UT333BT (medição não encontrada).") }; return }
            g.setCharacteristicNotification(ch, true)
            val d = ch.getDescriptor(CCCD)
            if (d != null) {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                else { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) }
            }
            main.post { _state.value = _state.value.copy(connecting = false, connected = true, error = null) }
            polling = true
            main.postDelayed(pollRunnable, 400)
        }

        private fun handle(value: ByteArray) {
            val r = decode(value) ?: return
            main.post {
                _state.value = if (r.first == 'H') _state.value.copy(humidityPercent = r.second, connected = true, error = null)
                else _state.value.copy(temperatureC = r.second, connected = true, error = null)
            }
        }
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) { if (ch.uuid == CH_NOTIFY) handle(ch.value ?: return) }
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) { if (ch.uuid == CH_NOTIFY) handle(value) }
    }

    fun disconnect() {
        polling = false
        stopScan()
        main.removeCallbacksAndMessages(null)
        gatt?.let { try { it.disconnect() } catch (_: Exception) {}; try { it.close() } catch (_: Exception) {} }
        gatt = null; device = null; retries = 0; everConnected = false
        _state.value = EnvState()
    }
}
