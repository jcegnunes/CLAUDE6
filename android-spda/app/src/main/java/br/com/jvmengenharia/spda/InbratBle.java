package br.com.jvmengenharia.spda;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Bluetooth (BLE) com o miliohmímetro INBRAT INMD 1 Pro.
 * Protocolo levantado do registro HCI (android-spda/PROTOCOLO_INBRAT_INMD1PRO.md):
 *   1) liga a notificação do resultado (631c4202)
 *   2) grava o nome do ensaio (631c4201, 11 bytes ASCII)
 *   3) grava 01 na partida (631c4203)
 *   4) o resultado chega por notificação (13 bytes) e vai cru (hex) para o JavaScript decodificar.
 * Uma operação por vez; os eventos vão ao JS por Sink (window.__spdaBle).
 */
@SuppressLint("MissingPermission")
public class InbratBle {
    public interface Sink { void send(String requestId, JSONObject event); }

    private static final String TAG = "InbratBle";
    private static final UUID SVC_TEST = UUID.fromString("631c4200-e4ba-416d-bd4c-56740819941c");
    private static final UUID CH_NAME = UUID.fromString("631c4201-e4ba-416d-bd4c-56740819941c");
    private static final UUID CH_RESULT = UUID.fromString("631c4202-e4ba-416d-bd4c-56740819941c");
    private static final UUID CH_START = UUID.fromString("631c4203-e4ba-416d-bd4c-56740819941c");
    private static final UUID SVC_INFO = UUID.fromString("564b4200-1edc-4a71-8b34-b6ca02cb1e2c");
    private static final UUID CH_SERIAL = UUID.fromString("564b4201-1edc-4a71-8b34-b6ca02cb1e2c");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final long MEASURE_TIMEOUT_MS = 30000;

    private final Context context;
    private final Sink sink;
    private final Handler main = new Handler(Looper.getMainLooper());

    private ScanCallback scanCallback;
    private BluetoothGatt gatt;
    private String measureId;
    private String testName;
    private String serial = "";
    private boolean finished = true;

    public InbratBle(Context context, Sink sink) {
        this.context = context.getApplicationContext();
        this.sink = sink;
    }

    private BluetoothAdapter adapter() {
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return bm == null ? null : bm.getAdapter();
    }

    private static JSONObject ev(String type) {
        JSONObject o = new JSONObject();
        try { o.put("type", type); } catch (Exception ignored) {}
        return o;
    }

    private static JSONObject err(String message) {
        JSONObject o = ev("error");
        try { o.put("message", message); } catch (Exception ignored) {}
        return o;
    }

    /** Verifica se o Bluetooth existe e está ligado. Retorna null se ok, senão a mensagem. */
    public String problem() {
        BluetoothAdapter a = adapter();
        if (a == null) return "Este celular não tem Bluetooth.";
        if (!a.isEnabled()) return "Ligue o Bluetooth do celular e tente de novo.";
        return null;
    }

    // ------------------------------------------------------------------ busca
    public void scan(final String requestId, int seconds) {
        stopScan();
        String p = problem();
        if (p != null) { sink.send(requestId, err(p)); return; }
        final BluetoothLeScanner scanner = adapter().getBluetoothLeScanner();
        if (scanner == null) { sink.send(requestId, err("Não foi possível iniciar a busca Bluetooth.")); return; }
        final Set<String> seen = new HashSet<>();
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice d = result.getDevice();
                String name = result.getScanRecord() != null ? result.getScanRecord().getDeviceName() : null;
                if (name == null) name = d.getName();
                if (name == null || name.trim().isEmpty() || !seen.add(d.getAddress())) return;
                JSONObject o = ev("found");
                try { o.put("address", d.getAddress()); o.put("name", name.trim()); o.put("rssi", result.getRssi()); } catch (Exception ignored) {}
                sink.send(requestId, o);
            }

            @Override
            public void onScanFailed(int errorCode) {
                sink.send(requestId, err("A busca Bluetooth falhou (código " + errorCode + ")."));
            }
        };
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        try {
            scanner.startScan(null, settings, scanCallback);
        } catch (Exception e) {
            sink.send(requestId, err("Não foi possível buscar: " + e.getMessage()));
            return;
        }
        main.postDelayed(() -> { stopScan(); sink.send(requestId, ev("done")); }, Math.max(3, Math.min(seconds, 30)) * 1000L);
    }

    public void stopScan() {
        if (scanCallback == null) return;
        try {
            BluetoothAdapter a = adapter();
            if (a != null && a.getBluetoothLeScanner() != null) a.getBluetoothLeScanner().stopScan(scanCallback);
        } catch (Exception ignored) {}
        scanCallback = null;
    }

    // ------------------------------------------------------------------ medição
    public void measure(final String requestId, String address, String name) {
        stopScan();
        if (!finished) { sink.send(requestId, err("Já existe uma medição em andamento.")); return; }
        String p = problem();
        if (p != null) { sink.send(requestId, err(p)); return; }
        BluetoothDevice device;
        try {
            device = adapter().getRemoteDevice(address);
        } catch (Exception e) {
            sink.send(requestId, err("Endereço Bluetooth inválido. Pareie o instrumento de novo.")); return;
        }
        measureId = requestId;
        testName = name == null ? "" : name;
        serial = "";
        finished = false;
        sink.send(requestId, ev("connecting"));
        main.postDelayed(timeout, MEASURE_TIMEOUT_MS);
        gatt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            : device.connectGatt(context, false, callback);
        if (gatt == null) fail("Não foi possível conectar ao instrumento.");
    }

    public void cancel() {
        if (!finished) fail("Medição cancelada.");
    }

    private final Runnable timeout = () -> fail("O instrumento não respondeu. Verifique se ele está ligado, perto do celular e com as pontas conectadas.");

    private void fail(String message) {
        if (finished) return;
        finish(err(message));
    }

    private void finish(JSONObject event) {
        finished = true;
        main.removeCallbacks(timeout);
        BluetoothGatt g = gatt;
        gatt = null;
        if (g != null) {
            try { g.disconnect(); } catch (Exception ignored) {}
            try { g.close(); } catch (Exception ignored) {}
        }
        sink.send(measureId, event);
    }

    private BluetoothGattCharacteristic ch(BluetoothGatt g, UUID svc, UUID c) {
        BluetoothGattService s = g.getService(svc);
        return s == null ? null : s.getCharacteristic(c);
    }

    @SuppressWarnings("deprecation")
    private boolean write(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
        if (Build.VERSION.SDK_INT >= 33) {
            return g.writeCharacteristic(c, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == 0;
        }
        c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        c.setValue(value);
        return g.writeCharacteristic(c);
    }

    @SuppressWarnings("deprecation")
    private boolean enableNotify(BluetoothGatt g, BluetoothGattCharacteristic c) {
        g.setCharacteristicNotification(c, true);
        BluetoothGattDescriptor d = c.getDescriptor(CCCD);
        if (d == null) return false;
        if (Build.VERSION.SDK_INT >= 33) {
            return g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == 0;
        }
        d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        return g.writeDescriptor(d);
    }

    private static String hex(byte[] v) {
        StringBuilder sb = new StringBuilder();
        if (v != null) for (byte b : v) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    private static byte[] nameBytes(String name) {
        byte[] out = new byte[11];
        byte[] src = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, out, 0, Math.min(src.length, out.length));
        return out;
    }

    // passo seguinte depois de ler o nº de série (ou se não houver)
    private void armNotify(BluetoothGatt g) {
        BluetoothGattCharacteristic result = ch(g, SVC_TEST, CH_RESULT);
        if (result == null || !enableNotify(g, result)) {
            main.post(() -> fail("Este aparelho não é um INBRAT INMD 1 Pro (serviço de ensaio não encontrado)."));
        }
    }

    private void onRead(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
        if (CH_SERIAL.equals(c.getUuid())) {
            serial = new String(value == null ? new byte[0] : value, StandardCharsets.US_ASCII).replace("\u0000", "").trim();
            armNotify(g);
        }
    }

    private void onChanged(byte[] value, UUID uuid) {
        if (!CH_RESULT.equals(uuid) || finished) return;
        JSONObject o = ev("result");
        try { o.put("hex", hex(value)); o.put("serial", serial); } catch (Exception ignored) {}
        main.post(() -> finish(o));
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                sink.send(measureId, ev("connected"));
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                main.post(() -> fail("A conexão com o instrumento caiu (código " + status + "). Tente de novo."));
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) { main.post(() -> fail("Falha ao ler os serviços do instrumento.")); return; }
            BluetoothGattCharacteristic s = ch(g, SVC_INFO, CH_SERIAL);
            if (s == null || !g.readCharacteristic(s)) armNotify(g);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            onRead(g, c, c.getValue());
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value, int status) {
            onRead(g, c, value);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            if (!CCCD.equals(d.getUuid())) return;
            BluetoothGattCharacteristic n = ch(g, SVC_TEST, CH_NAME);
            if (n == null || !write(g, n, nameBytes(testName))) main.post(() -> fail("Não foi possível enviar o nome do ensaio."));
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) { main.post(() -> fail("O instrumento recusou o comando (código " + status + ").")); return; }
            if (CH_NAME.equals(c.getUuid())) {
                BluetoothGattCharacteristic st = ch(g, SVC_TEST, CH_START);
                if (st == null || !write(g, st, new byte[]{1})) main.post(() -> fail("Não foi possível iniciar o ensaio."));
            } else if (CH_START.equals(c.getUuid())) {
                sink.send(measureId, ev("measuring"));
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            onChanged(c.getValue(), c.getUuid());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
            onChanged(value, c.getUuid());
        }
    };

    public void release() {
        stopScan();
        if (!finished) fail("App fechado.");
    }

    static void log(String m) { Log.d(TAG, m); }
}
