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
import kotlin.math.pow

/**
 * Alicate de corrente de fuga FLUKE 369 FC pelo Bluetooth (Fluke Connect).
 * Protocolo em app-ensaios-epi/PROTOCOLO_FLUKE_369FC.md:
 *  - o aparelho se anuncia como "369FC" e transmite a leitura ao vivo (notificações) em b698290f;
 *  - cada notificação tem 8 bytes: bytes 0-1 = inteiro (int16 LE) = valor; byte 5 = código de escala;
 *  - corrente (mA) = valor x 10^(byte5 - 14). byte 2 com bit 0x80 = leitura instável (descartar).
 * Cobre as duas faixas do 369 FC (A = código 0x0e, mA = código 0x0d) automaticamente.
 */
@SuppressLint("MissingPermission")
class FlukeLeakageManager(private val context: Context) {

    data class Found(val address: String, val name: String, val rssi: Int, val likelyFluke: Boolean)

    data class FlukeState(
        val scanning: Boolean = false,
        val connecting: Boolean = false,
        val connected: Boolean = false,
        val deviceName: String? = null,
        /** aparelhos Bluetooth encontrados na busca (para o usuário escolher se o nome não vier) */
        val devices: List<Found> = emptyList(),
        /** última leitura em mA (null = sem leitura ainda) */
        val leakageMilliAmp: Double? = null,
        val stable: Boolean = true,
        val error: String? = null
    )

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(FlukeState())
    val state: StateFlow<FlukeState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var scanCb: ScanCallback? = null
    private var device: android.bluetooth.BluetoothDevice? = null
    private var deviceName: String = "369FC"
    private var retries: Int = 0
    private var everConnected: Boolean = false

    companion object {
        private val SVC = UUID.fromString("b6981800-7562-11e2-b50d-00163e46f8fe")
        private val CH_MEASURE = UUID.fromString("b698290f-7562-11e2-b50d-00163e46f8fe")
        /** b698290e (handle 0x30): sincronização de hora — o app Fluke Connect escreve logo após conectar */
        private val CH_TIME = UUID.fromString("b698290e-7562-11e2-b50d-00163e46f8fe")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val SCAN_TIMEOUT_MS = 15000L
        /** Nome anunciado pelo 369 FC */
        private fun isFluke(name: String?): Boolean =
            name != null && (name.equals("369FC", true) || name.contains("369", true) || name.contains("fluke", true))

        /** Decodifica os 8 bytes da notificação em corrente (mA), ou null se inválido. Público para teste. */
        fun decodeMilliAmp(b: ByteArray): Double? {
            if (b.size < 6) return null
            val value = (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8) // int16 LE (corrente AC = magnitude)
            val scaleCode = b[5].toInt() and 0xFF
            return value * 10.0.pow(scaleCode - 14)
        }
    }

    fun setError(msg: String) { _state.value = FlukeState(error = msg) }

    fun problem(): String? {
        if (adapter == null) return "Este celular não tem Bluetooth."
        if (adapter.isEnabled != true) return "Ligue o Bluetooth do celular e tente de novo."
        return null
    }

    private val foundMap = LinkedHashMap<String, Found>()

    fun startScanAndConnect() {
        val p = problem()
        if (p != null) { _state.value = FlukeState(error = p); return }
        stopScan()
        foundMap.clear()
        _state.value = FlukeState(scanning = true)
        val scanner = adapter?.bluetoothLeScanner ?: run { _state.value = FlukeState(error = "Não foi possível iniciar a busca Bluetooth."); return }
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val rec = result.scanRecord
                val name = rec?.deviceName ?: result.device.name
                // o 369 FC também pode ser identificado pelo UUID de serviço da Fluke, quando o nome não vem
                val hasFlukeSvc = rec?.serviceUuids?.any { it.uuid == SVC || it.uuid.toString().contains("b69818", true) || it.uuid.toString().contains("b69829", true) } == true
                val likely = isFluke(name) || hasFlukeSvc
                if (likely) { stopScan(); connect(result.device, name ?: "369FC"); return }
                // guarda na lista para o usuário escolher (nome quando houver, senão o endereço)
                val addr = result.device.address ?: return
                foundMap[addr] = Found(addr, name ?: "", result.rssi, false)
                _state.value = _state.value.copy(devices = foundMap.values.sortedByDescending { it.rssi })
            }
            override fun onScanFailed(errorCode: Int) {
                _state.value = FlukeState(error = "A busca Bluetooth falhou (código $errorCode).")
            }
        }
        scanCb = cb
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try { scanner.startScan(null, settings, cb) } catch (e: Exception) { _state.value = FlukeState(error = "Não foi possível buscar: ${e.message}"); return }
        main.postDelayed({
            if (_state.value.scanning) {
                stopScan()
                _state.value = if (foundMap.isEmpty())
                    _state.value.copy(scanning = false, error = "Nenhum aparelho Bluetooth encontrado. Ligue o alicate, aproxime do celular e confira o Bluetooth.")
                else _state.value.copy(scanning = false) // mantém a lista para o usuário escolher
            }
        }, SCAN_TIMEOUT_MS)
    }

    /** Conecta num aparelho escolhido pelo usuário na lista. */
    fun connectTo(address: String) {
        stopScan()
        val dev = try { adapter?.getRemoteDevice(address) } catch (_: Exception) { null }
        if (dev == null) { _state.value = _state.value.copy(error = "Endereço Bluetooth inválido.") ; return }
        val nm = foundMap[address]?.name?.takeIf { it.isNotBlank() } ?: "Alicate"
        connect(dev, nm)
    }

    private fun stopScan() {
        scanCb?.let { try { adapter?.bluetoothLeScanner?.stopScan(it) } catch (_: Exception) {} }
        scanCb = null
    }

    private fun connect(dev: android.bluetooth.BluetoothDevice, name: String) {
        device = dev
        deviceName = name
        retries = 0
        everConnected = false
        doConnect()
    }

    private fun doConnect() {
        val dev = device ?: return
        _state.value = FlukeState(connecting = true, deviceName = deviceName)
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            dev.connectGatt(context, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
        else dev.connectGatt(context, false, callback)
        if (gatt == null) _state.value = FlukeState(error = "Não foi possível conectar ao alicate.")
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                everConnected = true
                main.postDelayed({ try { g.discoverServices() } catch (_: Exception) {} }, 300)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                try { g.close() } catch (_: Exception) {}
                gatt = null
                if (!everConnected && retries < 2) {
                    // falha momentânea (ex.: erro 133): tenta de novo
                    retries += 1
                    main.postDelayed({ if (_state.value.connecting) doConnect() }, 700)
                } else {
                    val msg = if (!everConnected)
                        "Não foi possível conectar ao alicate. Feche o app Fluke Connect (ele fica conectado ao alicate), desligue e ligue o Bluetooth e tente de novo."
                    else "A conexão com o alicate caiu. Tente de novo."
                    main.post { _state.value = _state.value.copy(connecting = false, connected = false, error = msg) }
                }
            }
        }

        // Handshake como o app Fluke Connect: MTU maior -> acerta a hora -> liga a notificação da medição.
        // As operações BLE são seriais: cada passo dispara o próximo no seu callback.
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g.getService(SVC)?.getCharacteristic(CH_MEASURE) == null) {
                main.post { _state.value = _state.value.copy(connecting = false, error = "Este aparelho não é um Fluke 369 FC (medição não encontrada).") }
                return
            }
            try { if (!g.requestMtu(517)) writeTimeSync(g) } catch (_: Exception) { writeTimeSync(g) }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { writeTimeSync(g) }

        private fun writeTimeSync(g: BluetoothGatt) {
            val ch = g.getService(SVC)?.getCharacteristic(CH_TIME)
            if (ch == null) { enableMeasure(g); return }
            val secs = (System.currentTimeMillis() / 1000L)
            val payload = byteArrayOf(
                0x01,
                (secs and 0xFF).toByte(), ((secs shr 8) and 0xFF).toByte(), ((secs shr 16) and 0xFF).toByte(), ((secs shr 24) and 0xFF).toByte(),
                0, 0, 0, 0
            )
            try {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 33) {
                    if (g.writeCharacteristic(ch, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) != BluetoothGatt.GATT_SUCCESS) enableMeasure(g)
                } else {
                    ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ch.value = payload
                    if (!g.writeCharacteristic(ch)) enableMeasure(g)
                }
            } catch (_: Exception) { enableMeasure(g) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            if (ch.uuid == CH_TIME) enableMeasure(g)
        }

        private fun enableMeasure(g: BluetoothGatt) {
            val ch = g.getService(SVC)?.getCharacteristic(CH_MEASURE) ?: return
            g.setCharacteristicNotification(ch, true)
            val d = ch.getDescriptor(CCCD)
            if (d != null) {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                else { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) }
            }
            main.post { _state.value = _state.value.copy(connecting = false, connected = true, error = null) }
        }

        private fun handle(value: ByteArray) {
            val mA = decodeMilliAmp(value) ?: return
            val unstable = value.size > 2 && (value[2].toInt() and 0x80) != 0
            main.post { _state.value = _state.value.copy(leakageMilliAmp = mA, stable = !unstable, connected = true, error = null) }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (ch.uuid == CH_MEASURE) handle(ch.value ?: return)
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            if (ch.uuid == CH_MEASURE) handle(value)
        }
    }

    fun disconnect() {
        stopScan()
        main.removeCallbacksAndMessages(null)
        gatt?.let { try { it.disconnect() } catch (_: Exception) {}; try { it.close() } catch (_: Exception) {} }
        gatt = null
        device = null
        retries = 0
        everConnected = false
        _state.value = FlukeState()
    }
}
