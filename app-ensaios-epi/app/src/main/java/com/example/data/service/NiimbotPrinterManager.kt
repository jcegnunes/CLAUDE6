package com.example.data.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import com.example.data.model.AppSettingEntity
import com.example.data.model.TestRecordEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.UUID

data class BluetoothPrinterDevice(
    val name: String,
    val address: String,
    val isBonded: Boolean = false,
    val isNiimbot: Boolean = false
)

enum class PrinterConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    PRINTING,
    SUCCESS,
    ERROR
}

enum class PrinterProtocol(val displayName: String, val description: String) {
    NIIMBOT("Niimbot Oficial (B1/B21/D110/B203)", "Protocolo binário nativo Niimbot"),
    TSPL("Térmica Universal TSPL (50x30mm)", "Padrão TSC/TSPL para etiquetas térmicas"),
    ESCPOS("Térmica ESC/POS (58mm/Bobina)", "Padrão gráfico raster ESC/POS universal")
}

/** Tamanhos de etiqueta (largura x altura, mm) — 8 pontos/mm a 203 DPI. */
enum class LabelPaperSize(val label: String, val widthMm: Int, val heightMm: Int, val widthDots: Int, val heightDots: Int) {
    SIZE_30X20("30x20 mm", 30, 20, 240, 160),
    // 50 mm = 400 pontos, mas a cabeça da B1/B21 imprime 384 (48 mm)
    SIZE_50X30("50x30 mm", 50, 30, 384, 240),
    // em pé: 50 mm na largura da cabeça, 80 mm de comprimento
    SIZE_50X80("50x80 mm", 50, 80, 384, 640)
}

data class PrinterUiState(
    val connectionState: PrinterConnectionState = PrinterConnectionState.DISCONNECTED,
    val connectedDevice: BluetoothPrinterDevice? = null,
    val discoveredDevices: List<BluetoothPrinterDevice> = emptyList(),
    val statusMessage: String = "Desconectado",
    val printProgress: Float = 0f,
    val isSimulatedMode: Boolean = false,
    val selectedProtocol: PrinterProtocol = PrinterProtocol.NIIMBOT
)

/**
 * Impressão de etiquetas por Bluetooth.
 *
 * Niimbot B1 / B21 / D11 / D110: Bluetooth Low Energy (GATT) — característica com
 * "notify" + "write without response" — e protocolo de pacotes 55 55 | cmd | len | dados | xor | AA AA,
 * na sequência usada pela niimbluelib para a B1 (densidade → tipo de etiqueta → início →
 * página → tamanho → linhas → fim de página → status → fim).
 * Térmicas TSPL / ESC-POS: Bluetooth clássico (RFCOMM / SPP).
 *
 * Não há modo simulado: se a conexão ou a impressão falham, o erro real é mostrado.
 */
class NiimbotPrinterManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _uiState = MutableStateFlow(PrinterUiState())
    val uiState: StateFlow<PrinterUiState> = _uiState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isReceiverRegistered = false
    private var isLeScanning = false

    // Bluetooth clássico (TSPL / ESC-POS e Niimbot antigas com SPP)
    private var activeSocket: BluetoothSocket? = null
    private var socketReaderJob: Job? = null

    // BLE (Niimbot)
    private var gatt: BluetoothGatt? = null
    private var gattChannel: BluetoothGattCharacteristic? = null
    private var gattReady: CompletableDeferred<Boolean>? = null
    private var writeDone: CompletableDeferred<Boolean>? = null
    private var mtu = 23
    private val writeMutex = Mutex()

    // Respostas da impressora (pacotes já decodificados)
    private val incoming = Channel<NiimbotResponse>(Channel.UNLIMITED)
    private val rxBuffer = ByteArrayOutputStream()

    private class NiimbotResponse(val cmd: Int, val data: ByteArray)

    companion object {
        private const val TAG = "NiimbotPrinter"


        // Standard Serial Port Profile (SPP) UUID
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Comandos Niimbot (niimbluelib RequestCommandId) e respectivas respostas
        private const val CMD_CONNECT = 0xC1
        private const val CMD_SET_DENSITY = 0x21
        private const val CMD_SET_LABEL_TYPE = 0x23
        private const val CMD_PRINT_START = 0x01
        private const val CMD_PAGE_START = 0x03
        private const val CMD_SET_PAGE_SIZE = 0x13
        private const val CMD_PRINT_EMPTY_ROW = 0x84
        private const val CMD_PRINT_BITMAP_ROW = 0x85
        private const val CMD_PAGE_END = 0xE3
        private const val CMD_PRINT_STATUS = 0xA3
        private const val CMD_PRINT_END = 0xF3

        private val RESPONSE_FOR = mapOf(
            CMD_CONNECT to 0xC2,
            CMD_SET_DENSITY to 0x31,
            CMD_SET_LABEL_TYPE to 0x33,
            CMD_PRINT_START to 0x02,
            CMD_PAGE_START to 0x04,
            CMD_SET_PAGE_SIZE to 0x14,
            CMD_PAGE_END to 0xE4,
            CMD_PRINT_STATUS to 0xB3,
            CMD_PRINT_END to 0xF4
        )

        /** Cabeça de impressão da B1 / B21 / D110 (203 dpi): 384 pontos */
        private const val PRINTHEAD_PIXELS = 384

        fun isNiimbotName(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val upper = name.uppercase()
            return upper.contains("NIIMBOT") ||
                    upper.contains("B1") ||
                    upper.contains("B21") ||
                    upper.contains("D11") ||
                    upper.contains("D110") ||
                    upper.contains("B3S") ||
                    upper.contains("B203") ||
                    upper.contains("JC-") ||
                    upper.contains("LABEL") ||
                    upper.contains("PRINTER") ||
                    upper.contains("POS")
        }

        fun detectPreferredProtocol(deviceName: String?): PrinterProtocol {
            if (deviceName.isNullOrBlank()) return PrinterProtocol.NIIMBOT
            val upper = deviceName.uppercase()
            return when {
                upper.contains("NIIMBOT") || upper.contains("B1") || upper.contains("B21") ||
                upper.contains("D11") || upper.contains("D110") || upper.contains("B203") ||
                upper.contains("B3S") || upper.contains("JC") -> PrinterProtocol.NIIMBOT
                upper.contains("TSPL") || upper.contains("TSC") || upper.contains("LABEL") ||
                upper.contains("XP-") || upper.contains("RP") -> PrinterProtocol.TSPL
                upper.contains("POS") || upper.contains("MPT") || upper.contains("58") -> PrinterProtocol.ESCPOS
                else -> PrinterProtocol.NIIMBOT
            }
        }
    }

    // ------------------------------------------------------------------
    // Busca de impressoras (Bluetooth clássico + BLE)
    // ------------------------------------------------------------------

    private fun addDiscovered(name: String?, address: String?, bonded: Boolean) {
        if (address.isNullOrBlank()) return
        val devName = name?.takeIf { it.isNotBlank() } ?: return // sem nome: não é impressora identificável
        val currentList = _uiState.value.discoveredDevices.toMutableList()
        val existing = currentList.indexOfFirst { it.address == address }
        val dev = BluetoothPrinterDevice(devName, address, bonded, isNiimbotName(devName))
        if (existing >= 0) {
            if (currentList[existing].name == devName) return
            currentList[existing] = dev
        } else {
            currentList.add(dev)
        }
        currentList.sortByDescending { it.isNiimbot }
        _uiState.value = _uiState.value.copy(
            discoveredDevices = currentList,
            statusMessage = "Encontrados ${currentList.size} dispositivos"
        )
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    @Suppress("DEPRECATION")
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let { addDiscovered(it.name, it.address, it.bondState == BluetoothDevice.BOND_BONDED) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (_uiState.value.connectionState == PrinterConnectionState.SCANNING && !isLeScanning) {
                        finishScanState()
                    }
                }
            }
        }
    }

    private val leScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device?.name
            addDiscovered(name, result.device?.address, result.device?.bondState == BluetoothDevice.BOND_BONDED)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "Busca BLE falhou: $errorCode")
            isLeScanning = false
        }
    }

    private fun finishScanState() {
        _uiState.value = _uiState.value.copy(
            connectionState = if (_uiState.value.connectedDevice != null) PrinterConnectionState.CONNECTED else PrinterConnectionState.DISCONNECTED,
            statusMessage = if (_uiState.value.discoveredDevices.isEmpty())
                "Nenhuma impressora encontrada. Ligue a Niimbot e deixe-a perto do celular."
            else "Busca concluída (${_uiState.value.discoveredDevices.size} encontrados)"
        )
    }

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun setProtocol(protocol: PrinterProtocol) {
        _uiState.value = _uiState.value.copy(selectedProtocol = protocol)
    }

    /** Impressoras já pareadas no Android (a B1 normalmente aparece pela busca BLE, sem parear). */
    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        val pairedList = mutableListOf<BluetoothPrinterDevice>()
        try {
            if (bluetoothAdapter?.isEnabled == true) {
                bluetoothAdapter.bondedDevices?.forEach { dev ->
                    val address = dev.address ?: ""
                    if (address.isNotBlank()) {
                        val name = dev.name ?: "Dispositivo Pareado"
                        pairedList.add(BluetoothPrinterDevice(name, address, isBonded = true, isNiimbot = isNiimbotName(name)))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Sem permissão Bluetooth para listar pareados", e)
        }

        pairedList.sortByDescending { it.isNiimbot }
        _uiState.value = _uiState.value.copy(
            discoveredDevices = pairedList,
            statusMessage = when {
                bluetoothAdapter == null -> "Este aparelho não tem Bluetooth"
                bluetoothAdapter.isEnabled != true -> "Bluetooth desligado — ligue o Bluetooth do celular"
                else -> "Lista de impressoras atualizada"
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        refreshPairedDevices()
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _uiState.value = _uiState.value.copy(
                connectionState = PrinterConnectionState.DISCONNECTED,
                statusMessage = "Bluetooth desligado ou indisponível"
            )
            return
        }
        try {
            if (!isReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                context.registerReceiver(discoveryReceiver, filter)
                isReceiverRegistered = true
            }
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()

            // Niimbot B1/B21/D11: anunciam-se por BLE
            adapter.bluetoothLeScanner?.let { scanner ->
                if (isLeScanning) scanner.stopScan(leScanCallback)
                scanner.startScan(leScanCallback)
                isLeScanning = true
                scope.launch {
                    delay(12_000)
                    stopLeScan()
                    if (_uiState.value.connectionState == PrinterConnectionState.SCANNING) finishScanState()
                }
            }
            _uiState.value = _uiState.value.copy(
                connectionState = PrinterConnectionState.SCANNING,
                statusMessage = "Buscando impressoras Bluetooth próximas..."
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                connectionState = PrinterConnectionState.DISCONNECTED,
                statusMessage = "Erro ao iniciar busca Bluetooth: ${e.localizedMessage}"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopLeScan() {
        try {
            if (isLeScanning) bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
        } catch (_: Exception) {
        }
        isLeScanning = false
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) bluetoothAdapter.cancelDiscovery()
            stopLeScan()
            if (isReceiverRegistered) {
                context.unregisterReceiver(discoveryReceiver)
                isReceiverRegistered = false
            }
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------
    // Conexão
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    suspend fun connectToDevice(device: BluetoothPrinterDevice): Boolean = withContext(Dispatchers.IO) {
        _uiState.value = _uiState.value.copy(
            connectionState = PrinterConnectionState.CONNECTING,
            statusMessage = "Conectando à ${device.name}..."
        )
        disconnect()
        stopDiscovery()

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            return@withContext fail("Bluetooth desligado — ligue o Bluetooth do celular e tente de novo")
        }
        val protocol = detectPreferredProtocol(device.name)
        val remote = try {
            adapter.getRemoteDevice(device.address)
        } catch (e: IllegalArgumentException) {
            return@withContext fail("Endereço Bluetooth inválido: ${device.address}")
        }

        // Niimbot: BLE primeiro; se não conectar (ou para térmicas TSPL/ESC-POS), Bluetooth clássico
        val connected = (protocol == PrinterProtocol.NIIMBOT && connectBle(remote)) || connectClassic(remote)
        if (!connected) {
            return@withContext fail(
                "Não foi possível conectar à ${device.name}. Verifique se a impressora está ligada, " +
                    "perto do celular e não conectada a outro aparelho (feche o app da Niimbot)."
            )
        }

        _uiState.value = _uiState.value.copy(
            connectionState = PrinterConnectionState.CONNECTED,
            connectedDevice = device,
            statusMessage = "Conectado à impressora ${device.name}",
            isSimulatedMode = false,
            selectedProtocol = protocol
        )
        true
    }

    private fun fail(message: String): Boolean {
        _uiState.value = _uiState.value.copy(
            connectionState = PrinterConnectionState.ERROR,
            connectedDevice = null,
            statusMessage = message
        )
        return false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                // pacotes de linha da imagem passam de 20 bytes: pede MTU maior antes de descobrir serviços
                if (!g.requestMtu(247)) g.discoverServices()
            } else {
                gattReady?.complete(false)
                writeDone?.complete(false)
                if (newState == BluetoothProfile.STATE_DISCONNECTED && _uiState.value.connectedDevice != null && gatt === g) {
                    _uiState.value = _uiState.value.copy(
                        connectionState = PrinterConnectionState.DISCONNECTED,
                        connectedDevice = null,
                        statusMessage = "Impressora desconectada"
                    )
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) mtu = newMtu
            g.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            // mesma regra da niimbluelib: serviço próprio (UUID de 128 bits) com notify + write without response
            val ch = g.services
                .filterNot { it.uuid.toString().endsWith("-0000-1000-8000-00805f9b34fb") }
                .flatMap { it.characteristics }
                .firstOrNull {
                    it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 &&
                        it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
                }
            if (ch == null) {
                gattReady?.complete(false)
                return
            }
            gattChannel = ch
            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(CCCD_UUID)
            if (cccd == null) {
                gattReady?.complete(true)
                return
            }
            val ok = if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
            if (!ok) gattReady?.complete(true) // tenta mesmo assim; o "connect" confirma
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            gattReady?.complete(true)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeDone?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Android < 13")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            characteristic.value?.let { onBytesReceived(it) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            onBytesReceived(value)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectBle(remote: BluetoothDevice): Boolean {
        val ready = CompletableDeferred<Boolean>()
        gattReady = ready
        mtu = 23
        val g = remote.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE) ?: return false
        gatt = g
        val ok = withTimeoutOrNull(15_000) { ready.await() } == true && gattChannel != null
        if (!ok) {
            closeGatt()
            return false
        }
        // handshake (resposta 0xC2); algumas versões de firmware não respondem — segue mesmo assim
        val resp = sendAndWait(CMD_CONNECT, byteArrayOf(0x01), timeoutMs = 2_000)
        Log.i(TAG, "BLE conectado (MTU $mtu), connect=${resp != null}")
        return true
    }

    @SuppressLint("MissingPermission")
    private fun connectClassic(remote: BluetoothDevice): Boolean {
        val attempts = listOf<() -> BluetoothSocket?>(
            { remote.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
            { remote.createRfcommSocketToServiceRecord(SPP_UUID) },
            { remote.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType).invoke(remote, 1) as? BluetoothSocket },
            { remote.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(remote, 1) as? BluetoothSocket }
        )
        for (attempt in attempts) {
            val socket = try {
                attempt()?.also { it.connect() }
            } catch (e: Exception) {
                Log.d(TAG, "RFCOMM falhou: ${e.message}")
                null
            }
            if (socket != null && socket.isConnected) {
                activeSocket = socket
                socketReaderJob = scope.launch {
                    val buf = ByteArray(256)
                    try {
                        val input = socket.inputStream
                        while (isActive) {
                            val n = input.read(buf)
                            if (n < 0) break
                            if (n > 0) onBytesReceived(buf.copyOf(n))
                        }
                    } catch (_: Exception) {
                    }
                }
                return true
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        gattChannel = null
    }

    fun disconnect() {
        closeGatt()
        socketReaderJob?.cancel()
        socketReaderJob = null
        try {
            activeSocket?.close()
        } catch (_: Exception) {
        }
        activeSocket = null
        _uiState.value = _uiState.value.copy(
            connectionState = PrinterConnectionState.DISCONNECTED,
            connectedDevice = null,
            statusMessage = "Desconectado"
        )
    }

    // ------------------------------------------------------------------
    // Envio / recepção de bytes
    // ------------------------------------------------------------------

    private val isTransportOpen: Boolean
        get() = (gatt != null && gattChannel != null) || activeSocket?.isConnected == true

    /** Junta os bytes recebidos e separa os pacotes 55 55 ... AA AA */
    @Synchronized
    private fun onBytesReceived(bytes: ByteArray) {
        rxBuffer.write(bytes)
        var buf = rxBuffer.toByteArray()
        var start = 0
        while (true) {
            var i = start
            while (i + 1 < buf.size && !(buf[i] == 0x55.toByte() && buf[i + 1] == 0x55.toByte())) i++
            if (i + 4 > buf.size) { start = i; break }
            val len = buf[i + 3].toInt() and 0xFF
            val total = len + 7
            if (i + total > buf.size) { start = i; break }
            if (buf[i + total - 2] == 0xAA.toByte() && buf[i + total - 1] == 0xAA.toByte()) {
                incoming.trySend(NiimbotResponse(buf[i + 2].toInt() and 0xFF, buf.copyOfRange(i + 4, i + 4 + len)))
                start = i + total
            } else {
                start = i + 1 // pacote corrompido: procura o próximo cabeçalho
            }
        }
        rxBuffer.reset()
        if (start < buf.size) rxBuffer.write(buf, start, buf.size - start)
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendRaw(bytes: ByteArray) = writeMutex.withLock {
        val socket = activeSocket
        if (socket != null && socket.isConnected) {
            socket.outputStream.write(bytes)
            socket.outputStream.flush()
            return@withLock
        }
        val g = gatt ?: throw IllegalStateException("Impressora desconectada")
        val ch = gattChannel ?: throw IllegalStateException("Impressora desconectada")
        val chunkSize = (mtu - 3).coerceAtLeast(20)
        var offset = 0
        while (offset < bytes.size) {
            val chunk = bytes.copyOfRange(offset, minOf(bytes.size, offset + chunkSize))
            val done = CompletableDeferred<Boolean>()
            writeDone = done
            val started = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(ch, chunk, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                ch.value = chunk
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
            if (!started) {
                // fila do Bluetooth cheia: espera um pouco e tenta o mesmo pedaço de novo
                delay(15)
                if (gatt == null) throw IllegalStateException("Impressora desconectada")
                continue
            }
            withTimeoutOrNull(2_000) { done.await() } ?: throw IllegalStateException("A impressora parou de responder durante o envio")
            offset += chunk.size
        }
    }

    private suspend fun send(cmd: Int, data: ByteArray) {
        sendRaw(createNiimbotPacket(cmd.toByte(), data))
    }

    /** Envia o comando e espera a resposta correspondente (null se a impressora não responder). */
    private suspend fun sendAndWait(cmd: Int, data: ByteArray = byteArrayOf(0x01), timeoutMs: Long = 3_000): ByteArray? {
        while (incoming.tryReceive().isSuccess) { /* descarta respostas antigas */ }
        send(cmd, data)
        val expected = RESPONSE_FOR[cmd] ?: return null
        return withTimeoutOrNull(timeoutMs) {
            var r = incoming.receive()
            while (r.cmd != expected) r = incoming.receive()
            r.data
        }
    }

    private fun u16(n: Int) = byteArrayOf(((n shr 8) and 0xFF).toByte(), (n and 0xFF).toByte())

    /**
     * Etiqueta térmica no tamanho de papel escolhido (8 pontos/mm, 203 DPI), desenhada a partir do
     * modelo compartilhado com a plataforma web ([com.example.util.LabelModels]).
     */
    @Suppress("UNUSED_PARAMETER")
    fun generateLabelBitmap(
        test: TestRecordEntity,
        company: AppSettingEntity,
        density: Float = 1.0f,
        size: LabelPaperSize = LabelPaperSize.SIZE_50X30
    ): Bitmap {
        val model = com.example.util.LabelModels.build(test, size)
        val bitmap = Bitmap.createBitmap(model.width, model.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        drawLabelModel(canvas, model)
        return bitmap
    }

    /** Desenha os elementos do modelo (pontos da impressora, sem escala). */
    private fun drawLabelModel(canvas: Canvas, model: com.example.util.LabelModel) {
        for (p in model.primitives) {
            when (p) {
                is com.example.util.LabelPrimitive.Rect -> {
                    val paint = Paint().apply {
                        color = Color.BLACK
                        if (p.fill) style = Paint.Style.FILL else { style = Paint.Style.STROKE; strokeWidth = p.stroke }
                    }
                    canvas.drawRect(p.x, p.y, p.x + p.w, p.y + p.h, paint)
                }
                // QR com módulos inteiros (nítido na térmica), centralizado no quadro
                is com.example.util.LabelPrimitive.Qr ->
                    com.example.util.QrCode.draw(canvas, p.x, p.y, p.size, p.content, snapToPixels = true)
                is com.example.util.LabelPrimitive.Text -> {
                    val align = when (p.align) {
                        com.example.util.LabelPrimitive.Align.CENTER -> Paint.Align.CENTER
                        com.example.util.LabelPrimitive.Align.RIGHT -> Paint.Align.RIGHT
                        else -> Paint.Align.LEFT
                    }
                    var paint = labelPaint(p.size, p.bold, mono = p.mono, white = p.white, align = align)
                    p.maxWidth?.let { paint = fitPaint(paint, p.content, it) }
                    val y = if (p.middle) p.y - (paint.descent() + paint.ascent()) / 2f else p.y
                    if (p.rotate != 0f) {
                        canvas.save()
                        canvas.rotate(p.rotate, p.x, p.y)
                        canvas.drawText(p.content, p.x, y, paint)
                        canvas.restore()
                    } else {
                        canvas.drawText(p.content, p.x, y, paint)
                    }
                }
            }
        }
    }

    private fun labelPaint(size: Float, bold: Boolean, mono: Boolean = false, white: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint().apply {
        color = if (white) Color.WHITE else Color.BLACK
        textSize = size
        typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        isAntiAlias = true
        textAlign = align
    }

    /** Reduz a fonte só quando o texto passaria da largura disponível. */
    private fun fitPaint(paint: Paint, text: String, maxWidth: Float): Paint {
        val w = paint.measureText(text)
        if (w > maxWidth) paint.textSize = (paint.textSize * maxWidth / w).coerceAtLeast(5f)
        return paint
    }

    /**
     * Converts Bitmap into Niimbot Thermal Protocol Packet Stream
     * Niimbot Packet Format:
     * Header: 0x55 0x55 (2 bytes)
     * Type: 1 byte
     * Length: 1 byte (N)
     * Payload: N bytes
     * Checksum: 1 byte = (Type XOR Length XOR b0 XOR ... XOR bN)
     * Footer: 0xAA 0xAA (2 bytes)
     * Total Packet Size = 7 + N bytes
     */
    fun createNiimbotPacket(type: Byte, payload: ByteArray = byteArrayOf()): ByteArray {
        val length = payload.size.toByte()
        var checksum = (type.toInt() and 0xFF) xor (length.toInt() and 0xFF)
        for (b in payload) {
            checksum = checksum xor (b.toInt() and 0xFF)
        }
        val packet = ByteArray(7 + payload.size)
        packet[0] = 0x55.toByte()
        packet[1] = 0x55.toByte()
        packet[2] = type
        packet[3] = length
        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, packet, 4, payload.size)
        }
        packet[packet.size - 3] = (checksum and 0xFF).toByte()
        packet[packet.size - 2] = 0xAA.toByte()
        packet[packet.size - 1] = 0xAA.toByte()
        return packet
    }

    /**
     * TSPL/TSC protocol stream for portable thermal label printers (50x30mm)
     */
    fun createTsplPrintData(bitmap: Bitmap, copies: Int = 1, size: LabelPaperSize = LabelPaperSize.SIZE_50X30): ByteArray {
        val output = ByteArrayOutputStream()
        val widthBytes = (bitmap.width + 7) / 8
        val height = bitmap.height

        val header = "SIZE ${size.widthMm} mm, ${size.heightMm} mm\r\n" +
                "GAP 2 mm, 0 mm\r\n" +
                "DIRECTION 1\r\n" +
                "CLS\r\n" +
                "BITMAP 0,0,$widthBytes,$height,0,"
        output.write(header.toByteArray(Charsets.US_ASCII))

        val rawData = ByteArray(widthBytes * height)
        var idx = 0
        for (y in 0 until height) {
            for (xByte in 0 until widthBytes) {
                var bByte = 0
                for (bit in 0 until 8) {
                    val x = xByte * 8 + bit
                    if (x < bitmap.width) {
                        val p = bitmap.getPixel(x, y)
                        val lum = (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toInt()
                        if (lum < 128) {
                            bByte = bByte or (1 shl (7 - bit))
                        }
                    }
                }
                rawData[idx++] = (bByte xor 0xFF).toByte()
            }
        }
        output.write(rawData)

        val footer = "\r\nPRINT 1,$copies\r\n"
        output.write(footer.toByteArray(Charsets.US_ASCII))
        return output.toByteArray()
    }

    /**
     * Standard ESC/POS Raster Print Fallback for Universal Bluetooth Thermal Printers
     */
    fun createEscPosRasterData(bitmap: Bitmap): ByteArray {
        val widthBytes = (bitmap.width + 7) / 8
        val height = bitmap.height
        val output = ByteArrayOutputStream()

        // ESC @ (Initialize printer)
        output.write(byteArrayOf(0x1B, 0x40))

        // GS v 0 (Print raster bit image)
        // 0x1D 0x76 0x30 0x00 xL xH yL yH
        output.write(
            byteArrayOf(
                0x1D, 0x76, 0x30, 0x00,
                (widthBytes and 0xFF).toByte(),
                ((widthBytes shr 8) and 0xFF).toByte(),
                (height and 0xFF).toByte(),
                ((height shr 8) and 0xFF).toByte()
            )
        )

        for (y in 0 until height) {
            for (xByte in 0 until widthBytes) {
                var currentByte = 0
                for (b in 0 until 8) {
                    val x = xByte * 8 + b
                    if (x < bitmap.width) {
                        val pixel = bitmap.getPixel(x, y)
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val bVal = Color.blue(pixel)
                        val luminance = (0.299 * r + 0.587 * g + 0.114 * bVal).toInt()
                        if (luminance < 128) {
                            currentByte = currentByte or (1 shl (7 - b))
                        }
                    }
                }
                output.write(currentByte)
            }
        }

        // Feed paper (ESC d 3)
        output.write(byteArrayOf(0x1B, 0x64, 0x03))

        return output.toByteArray()
    }

    /** Linha do bitmap em bits (1 = preto; bit mais significativo = pixel da esquerda). */
    private fun rowBits(bitmap: Bitmap, y: Int, widthBytes: Int): ByteArray {
        val row = ByteArray(widthBytes)
        for (xByte in 0 until widthBytes) {
            var b = 0
            for (bit in 0 until 8) {
                val x = xByte * 8 + bit
                if (x < bitmap.width) {
                    val p = bitmap.getPixel(x, y)
                    val lum = (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toInt()
                    if (lum < 128) b = b or (1 shl (7 - bit))
                }
            }
            row[xByte] = b.toByte()
        }
        return row
    }

    /** Contagem de pixels pretos em 3 partes da cabeça de impressão (modo "split" da niimbluelib). */
    private fun pixelCountParts(row: ByteArray): ByteArray {
        val chunk = PRINTHEAD_PIXELS / 8 / 3
        val parts = IntArray(3)
        row.forEachIndexed { i, v ->
            val idx = i / chunk
            if (idx < 3) parts[idx] += Integer.bitCount(v.toInt() and 0xFF)
        }
        return byteArrayOf(parts[0].coerceAtMost(255).toByte(), parts[1].coerceAtMost(255).toByte(), parts[2].coerceAtMost(255).toByte())
    }

    /** Niimbot (cabeça de 384 pontos / 48 mm): imagem mais larga que a cabeça é girada ou reduzida. */
    /**
     * Desloca a imagem na horizontal (8 pontos/mm) mantendo a largura: a etiqueta de 50 mm é mais
     * larga que a cabeça (48 mm) e a posição em que a B1 começa a imprimir varia com o encaixe do rolo.
     */
    private fun shiftHorizontally(bitmap: Bitmap, offsetMm: Float): Bitmap {
        val dx = Math.round(offsetMm * 8f)
        if (dx == 0) return bitmap
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, dx.toFloat(), 0f, null)
        return out
    }

    private fun fitToPrinthead(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= PRINTHEAD_PIXELS) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, android.graphics.Matrix().apply { postRotate(90f) }, false)
        if (rotated.width <= PRINTHEAD_PIXELS) return rotated
        val h = Math.round(rotated.height * PRINTHEAD_PIXELS.toFloat() / rotated.width)
        return Bitmap.createScaledBitmap(rotated, PRINTHEAD_PIXELS, h, false)
    }

    /** Sequência da B1 (niimbluelib B1PrintTask): mesma usada pelo app oficial. */
    private suspend fun printNiimbot(bitmap: Bitmap, copies: Int, density: Float) {
        val pages = copies.coerceIn(1, 999)
        // densidade 1..5 (padrão 3); o controle do app vai de 0,5 a 1,5
        val densityLevel = (3 + Math.round((density - 1.0f) * 2)).coerceIn(1, 5)

        fun step(progress: Float, msg: String) {
            _uiState.value = _uiState.value.copy(printProgress = progress, statusMessage = msg)
        }

        step(0.05f, "Preparando a impressora...")
        sendAndWait(CMD_SET_DENSITY, byteArrayOf(densityLevel.toByte()))
            ?: throw IllegalStateException("A impressora não respondeu. Desligue e ligue a Niimbot e conecte de novo.")
        sendAndWait(CMD_SET_LABEL_TYPE, byteArrayOf(0x01)) // etiqueta com espaço (gap)
        sendAndWait(CMD_PRINT_START, u16(pages) + byteArrayOf(0, 0, 0, 0, 0))
            ?: throw IllegalStateException("A impressora recusou o início da impressão (tampa aberta ou sem etiqueta?)")
        sendAndWait(CMD_PAGE_START, byteArrayOf(0x01))

        val widthBytes = (bitmap.width + 7) / 8
        val rows = bitmap.height
        val cols = widthBytes * 8
        sendAndWait(CMD_SET_PAGE_SIZE, u16(rows) + u16(cols) + u16(pages))

        // linhas: brancas em bloco (0x84), demais com contagem de pixels (0x85); linhas iguais seguidas viram repetição
        var y = 0
        while (y < rows) {
            val row = rowBits(bitmap, y, widthBytes)
            var repeats = 1
            while (y + repeats < rows && repeats < 255 && rowBits(bitmap, y + repeats, widthBytes).contentEquals(row)) repeats++
            if (row.all { it == 0.toByte() }) {
                send(CMD_PRINT_EMPTY_ROW, u16(y) + byteArrayOf(repeats.toByte()))
            } else {
                send(CMD_PRINT_BITMAP_ROW, u16(y) + pixelCountParts(row) + byteArrayOf(repeats.toByte()) + row)
            }
            y += repeats
            delay(10) // mesmo intervalo entre pacotes da niimbluelib
            step(0.1f + 0.6f * y / rows, "Enviando etiqueta ($y / $rows)...")
        }

        sendAndWait(CMD_PAGE_END, byteArrayOf(0x01), timeoutMs = 5_000)

        // espera a impressora terminar (status: página atual chega ao total)
        step(0.75f, "Imprimindo...")
        val finished = withTimeoutOrNull(30_000L + 5_000L * pages) {
            while (true) {
                val st = sendAndWait(CMD_PRINT_STATUS, byteArrayOf(0x01), timeoutMs = 2_000)
                if (st != null && st.size >= 2) {
                    val page = ((st[0].toInt() and 0xFF) shl 8) or (st[1].toInt() and 0xFF)
                    step(0.75f + 0.2f * page.coerceAtMost(pages) / pages, "Imprimindo ($page / $pages)...")
                    if (page >= pages) break
                }
                delay(300)
            }
            true
        }
        sendAndWait(CMD_PRINT_END, byteArrayOf(0x01), timeoutMs = 5_000)
        if (finished == null) throw IllegalStateException("A impressora não confirmou o fim da impressão")
    }

    /**
     * Sends the formatted label to the Niimbot/Bluetooth Printer using the chosen protocol
     */
    suspend fun printLabel(
        test: TestRecordEntity,
        company: AppSettingEntity,
        copies: Int = 1,
        density: Float = 1.0f,
        overrideProtocol: PrinterProtocol? = null,
        size: LabelPaperSize = LabelPaperSize.SIZE_50X30,
        /** deslocamento horizontal da impressão em mm (negativo = para a esquerda) */
        offsetMm: Float = 0f
    ): Result<String> = withContext(Dispatchers.IO) {
        val device = _uiState.value.connectedDevice
        if (device == null || !isTransportOpen) {
            _uiState.value = _uiState.value.copy(
                connectionState = PrinterConnectionState.DISCONNECTED,
                connectedDevice = null,
                statusMessage = "Impressora desconectada — conecte de novo"
            )
            return@withContext Result.failure(Exception("Nenhuma impressora conectada via Bluetooth"))
        }

        val protocol = overrideProtocol ?: _uiState.value.selectedProtocol
        _uiState.value = _uiState.value.copy(
            connectionState = PrinterConnectionState.PRINTING,
            statusMessage = "Gerando dados térmicos (${protocol.displayName})...",
            printProgress = 0.05f
        )

        val bitmap = generateLabelBitmap(test, company, density, size)

        try {
            when (protocol) {
                PrinterProtocol.NIIMBOT -> printNiimbot(shiftHorizontally(fitToPrinthead(bitmap), offsetMm), copies, density)
                PrinterProtocol.TSPL -> sendRaw(createTsplPrintData(bitmap, copies, size))
                PrinterProtocol.ESCPOS -> {
                    for (c in 1..copies) {
                        sendRaw(createEscPosRasterData(bitmap))
                        if (c < copies) delay(200)
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                connectionState = PrinterConnectionState.SUCCESS,
                statusMessage = "Etiqueta impressa na ${device.name}!",
                printProgress = 1.0f
            )
            Result.success("Etiqueta impressa com sucesso!")
        } catch (e: Exception) {
            Log.e(TAG, "Falha na impressão", e)
            _uiState.value = _uiState.value.copy(
                connectionState = PrinterConnectionState.ERROR,
                statusMessage = "Falha ao imprimir: ${e.localizedMessage}"
            )
            Result.failure(e)
        }
    }
}
