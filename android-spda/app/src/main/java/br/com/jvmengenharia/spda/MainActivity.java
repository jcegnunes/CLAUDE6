package br.com.jvmengenharia.spda;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import androidx.webkit.WebViewAssetLoader;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String TAG = "JVMDielectricLab";
    private static final String CHANNEL_ID = "jvm_downloads_channel";
    private static final int FILE_CHOOSER_RESULT_CODE = 1001;
    private static final int PERMISSION_REQUEST_CODE = 1002;
    private static final int CAMERA_PERMISSION_CODE = 1003;
    private static final int BLE_PERMISSION_CODE = 1004;
    private static final String APP_URL = "https://appassets.androidplatform.net/assets/www/index.html";
    private WebViewAssetLoader assetLoader;

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> fileUploadCallback;
    private Uri cameraPhotoUri;
    // pedidos que aguardam a permissão de câmera do Android
    private PermissionRequest pendingWebPermission;
    private Runnable pendingCameraAction;
    /** Bluetooth do miliohmímetro INBRAT INMD 1 Pro (InbratBle.java) */
    private InbratBle inbratBle;
    private Runnable pendingBleAction;
    private String pendingBleRequestId;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tela cheia e manutenção de tela ativa durante ensaios dielétricos
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        createNotificationChannel();
        checkAndRequestAppPermissions();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setGeolocationEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        
        // Identificador nativo e compatibilidade total com tablets Samsung e smartphones Xiaomi/Motorola
        String defaultUa = settings.getUserAgentString();
        settings.setUserAgentString(defaultUa + " JVMDielectricLab-Android-APK/5.2.0 (Xiaomi-Samsung-Camera-Enabled)");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        // Ponte JavaScript Nativa para Salvar Arquivos Localmente na Memória do Celular / Tablet
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");
        webView.addJavascriptInterface(new AndroidBridge(this), "Android");

        // Download Listener para Laudos PDF, Certificados, Backups JSON e CSVs
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                if (url.startsWith("data:")) {
                    saveDataUrlLocally(url, mimetype, URLUtil.guessFileName(url, contentDisposition, mimetype));
                    return;
                }
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimetype);
                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Baixando documento técnico JVM...");
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype));
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "JVM SPDA/" + URLUtil.guessFileName(url, contentDisposition, mimetype));
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    Toast.makeText(MainActivity.this, "Download iniciado para /Download/JVM SPDA/", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.w(TAG, "Download manager fallback", e);
            }
        });

        // App SPDA empacotado no APK, servido por https interno (IndexedDB, câmera e módulos JS funcionam)
        assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();
        webView.setWebViewClient(new WebViewClient() {
            /** O WebView caiu (falta de memória etc.): reinicia o app em vez de ficar em branco */
            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                Log.w(TAG, "WebView encerrado (crash=" + (detail != null && detail.didCrash()) + "): reiniciando o app");
                restartWholeApp();
                return true;
            }
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:") || url.startsWith("intent:")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception ignored) {}
                }
                // links externos (ex.: Google Maps) abrem fora do app
                if ((url.startsWith("http://") || url.startsWith("https://")) && !url.startsWith("https://appassets.androidplatform.net/")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
                    return true;
                }
                view.loadUrl(url);
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }

            // Permissão de Câmera (QR Code Scanner, Vídeo e Fotos) e Microfone
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                // câmera em tempo real (getUserMedia): só libera para a página depois que o Android
                // conceder a permissão — antes liberava na hora e a câmera não abria na 1ª vez
                runOnUiThread(() -> {
                    if (hasCameraPermission()) {
                        request.grant(request.getResources());
                    } else {
                        if (pendingWebPermission != null) pendingWebPermission.deny();
                        pendingWebPermission = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                    }
                });
            }

            // Permissão de Geolocalização (GPS de Campo para Carimbo de Laudo)
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            // Upload de Arquivos / Abertura Direta da Câmera do Celular para Registro Fotográfico
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                    fileUploadCallback = null;
                }
                fileUploadCallback = filePathCallback;
                final boolean capture = fileChooserParams.isCaptureEnabled();
                final Intent contentIntent = fileChooserParams.createIntent();
                // campo que aceita só documentos (ex.: PDF da ART / certificado): seletor de arquivos, sem câmera
                if (!acceptsImages(fileChooserParams.getAcceptTypes())) {
                    try {
                        startActivityForResult(Intent.createChooser(contentIntent, "Selecionar arquivo"), FILE_CHOOSER_RESULT_CODE);
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao abrir o seletor de arquivos", e);
                        fileUploadCallback.onReceiveValue(null);
                        fileUploadCallback = null;
                        Toast.makeText(MainActivity.this, "Não foi possível abrir os arquivos do aparelho", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                // com a permissão CAMERA declarada, o Android só abre a câmera do sistema depois de concedida
                if (!hasCameraPermission()) {
                    pendingCameraAction = () -> launchFileChooser(capture, contentIntent);
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                } else {
                    launchFileChooser(capture, contentIntent);
                }
                return true;
            }
        });

        webView.loadUrl(APP_URL);
    }

    /** true se o campo aceita imagens (ou qualquer arquivo); false se pede só documentos (ex.: application/pdf). */
    private static boolean acceptsImages(String[] types) {
        if (types == null || types.length == 0) return true;
        for (String t : types) {
            if (t == null || t.trim().isEmpty() || t.startsWith("image") || t.equals("*/*")) return true;
        }
        return false;
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
            || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    /** Abre a câmera do celular (input com capture) ou o seletor "câmera ou arquivo". */
    private void launchFileChooser(boolean capture, Intent contentIntent) {
        Intent takePictureIntent = null;
        if (hasCameraPermission()) {
            File photoFile = createPhotoFile();
            if (photoFile != null) {
                try {
                    cameraPhotoUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", photoFile);
                    takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    takePictureIntent.setClipData(android.content.ClipData.newRawUri("", cameraPhotoUri));
                } catch (Exception ex) {
                    Log.e(TAG, "Erro ao preparar arquivo para câmera", ex);
                    cameraPhotoUri = null;
                    takePictureIntent = null;
                }
            }
        }
        try {
            if (capture && takePictureIntent != null) {
                startActivityForResult(takePictureIntent, FILE_CHOOSER_RESULT_CODE);
                return;
            }
            Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
            chooserIntent.putExtra(Intent.EXTRA_INTENT, contentIntent);
            chooserIntent.putExtra(Intent.EXTRA_TITLE, "Tirar foto com a câmera ou selecionar arquivo");
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, takePictureIntent != null ? new Intent[]{takePictureIntent} : new Intent[0]);
            startActivityForResult(chooserIntent, FILE_CHOOSER_RESULT_CODE);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao abrir câmera/arquivos", e);
            if (fileUploadCallback != null) fileUploadCallback.onReceiveValue(null);
            fileUploadCallback = null;
            cameraPhotoUri = null;
            Toast.makeText(MainActivity.this, "Erro ao abrir a câmera do celular", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BLE_PERMISSION_CODE) {
            Runnable r = pendingBleAction;
            String rid = pendingBleRequestId;
            pendingBleAction = null;
            pendingBleRequestId = null;
            if (hasBlePermission()) { if (r != null) r.run(); }
            else sendBle(rid, "{\"type\":\"error\",\"message\":\"Permita o Bluetooth (dispositivos próximos) e a localização para o app nas configurações do celular.\"}");
            return;
        }
        if (requestCode != CAMERA_PERMISSION_CODE) return;
        boolean granted = hasCameraPermission();
        if (pendingWebPermission != null) {
            if (granted) pendingWebPermission.grant(pendingWebPermission.getResources());
            else pendingWebPermission.deny();
            pendingWebPermission = null;
        }
        if (pendingCameraAction != null) {
            Runnable r = pendingCameraAction;
            pendingCameraAction = null;
            r.run(); // sem permissão abre só o seletor de arquivos/galeria
        }
        if (!granted) {
            Toast.makeText(this, "Permita o acesso à câmera nas configurações do app para fotografar o visor.", Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------ Bluetooth (INMD 1 Pro)
    private String[] blePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION};
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    private boolean hasBlePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        for (String p : blePermissions()) {
            // a busca BLE devolve resultados só com localização (o manifesto não usa neverForLocation)
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    /** Roda a ação do Bluetooth depois de garantir as permissões. */
    private void withBle(String requestId, Runnable action) {
        if (inbratBle == null) inbratBle = new InbratBle(this, (rid, event) -> sendBle(rid, event.toString()));
        if (hasBlePermission()) { action.run(); return; }
        pendingBleAction = action;
        pendingBleRequestId = requestId;
        requestPermissions(blePermissions(), BLE_PERMISSION_CODE);
    }

    private void sendBle(String requestId, String json) {
        final String js = "window.__spdaBle && window.__spdaBle(" + org.json.JSONObject.quote(requestId == null ? "" : requestId) + "," + json + ")";
        runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript(js, null); });
    }

    /** Reinicia o processo inteiro (RestartActivity): destrava o armazenamento do WebView. */
    void restartWholeApp() {
        try {
            Intent i = new Intent(this, RestartActivity.class);
            i.putExtra(RestartActivity.EXTRA_PID, android.os.Process.myPid());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Log.e(TAG, "Falha ao reiniciar", e);
            recreate();
        }
    }

    @Override
    protected void onDestroy() {
        if (inbratBle != null) inbratBle.release();
        super.onDestroy();
    }

    private File createPhotoFile() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "JVM_ENSAIO_" + timeStamp + "_";
            File storageDir = getExternalCacheDir();
            if (storageDir == null) {
                storageDir = getCacheDir();
            }
            return File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao criar arquivo temporário da foto", e);
            return null;
        }
    }

    private void checkAndRequestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissionsNeeded = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.CAMERA);
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
                if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
                }
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
                }
            }

            if (!permissionsNeeded.isEmpty()) {
                requestPermissions(permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "JVM SPDA - laudos salvos";
            String description = "Notificações de laudos e relatórios de SPDA salvos no celular";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void saveDataUrlLocally(String dataUrl, String mimeType, String fileName) {
        try {
            int commaIndex = dataUrl.indexOf(",");
            if (commaIndex > 0) {
                String base64Data = dataUrl.substring(commaIndex + 1);
                new AndroidBridge(this).saveBase64File(base64Data, mimeType, fileName, true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao salvar DataUrl", e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (fileUploadCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null && data.getData() != null) {
                        // Seleção vinda da galeria / arquivos
                        results = new Uri[]{data.getData()};
                    } else if (data != null && data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    } else if (cameraPhotoUri != null) {
                        // Foto tirada diretamente pela câmera do celular
                        results = new Uri[]{cameraPhotoUri};
                    } else {
                        results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                    }
                }
                fileUploadCallback.onReceiveValue(results);
                fileUploadCallback = null;
            }
            cameraPhotoUri = null;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Ponte Nativa Android de Salvamento Local, Compartilhamento e Notificações
     */
    public class AndroidBridge {
        private final Context context;

        public AndroidBridge(Context context) {
            this.context = context;
        }

        /** Imprimir / Salvar como PDF (laudos e relatórios) pelo serviço de impressão do Android */
        @JavascriptInterface
        public void printPage(final String title) {
            MainActivity.this.runOnUiThread(() -> {
                String name = (title == null || title.isEmpty()) ? "Laudo SPDA" : title;
                android.print.PrintManager pm = (android.print.PrintManager) getSystemService(Context.PRINT_SERVICE);
                pm.print(name, webView.createPrintDocumentAdapter(name),
                    new android.print.PrintAttributes.Builder().setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4).build());
            });
        }

        /**
         * PDF do laudo/relatório aberto, gerado pelo motor de impressão do WebView direto em arquivo
         * (vetorial: texto nítido e selecionável, como o PDF do app de Ensaios de EPI).
         * action: "view" = salva em Download/JVM SPDA e abre no leitor de PDF; "share" = abre o compartilhar.
         * Ao terminar chama window.__spdaNativePdf(requestId, ok, mensagem).
         */
        @JavascriptInterface
        public void createPdf(final String fileName, final String action, final String requestId) {
            MainActivity.this.runOnUiThread(() -> {
                String base = (fileName == null || fileName.trim().isEmpty()) ? "Laudo SPDA" : fileName.trim();
                final String name = base.toLowerCase(Locale.ROOT).endsWith(".pdf") ? base : base + ".pdf";
                try {
                    android.print.PrintAttributes attrs = new android.print.PrintAttributes.Builder()
                        .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(new android.print.PrintAttributes.Resolution("pdf", "pdf", 600, 600))
                        .setColorMode(android.print.PrintAttributes.COLOR_MODE_COLOR)
                        .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                        .build();
                    final File out = new File(new File(context.getCacheDir(), "shared_docs"), name);
                    android.print.SpdaPdfWriter.write(webView.createPrintDocumentAdapter(name), attrs, out, new android.print.SpdaPdfWriter.Callback() {
                        @Override
                        public void onSuccess(File file) {
                            try {
                                if ("share".equals(action)) {
                                    Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
                                    Intent share = new Intent(Intent.ACTION_SEND);
                                    share.setType("application/pdf");
                                    share.putExtra(Intent.EXTRA_STREAM, uri);
                                    share.putExtra(Intent.EXTRA_SUBJECT, name);
                                    share.putExtra(Intent.EXTRA_TEXT, "Segue o documento técnico em PDF: " + name);
                                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(Intent.createChooser(share, "Compartilhar PDF"));
                                    notifyPdf(requestId, true, "PDF pronto para compartilhar.");
                                } else {
                                    byte[] bytes = readAll(file);
                                    boolean ok = saveBase64File(Base64.encodeToString(bytes, Base64.NO_WRAP), "application/pdf", name, true);
                                    notifyPdf(requestId, ok, ok ? "PDF salvo em Download/JVM SPDA." : "Não foi possível salvar o PDF.");
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Erro ao abrir/compartilhar o PDF", e);
                                notifyPdf(requestId, false, "Erro ao abrir o PDF: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onFailure(String message) {
                            notifyPdf(requestId, false, "Não foi possível gerar o PDF: " + message);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao gerar o PDF", e);
                    notifyPdf(requestId, false, "Não foi possível gerar o PDF: " + e.getMessage());
                }
            });
        }

        private void notifyPdf(String requestId, boolean ok, String message) {
            final String js = "window.__spdaNativePdf && window.__spdaNativePdf(" + org.json.JSONObject.quote(requestId == null ? "" : requestId)
                + "," + ok + "," + org.json.JSONObject.quote(message == null ? "" : message) + ")";
            MainActivity.this.runOnUiThread(() -> webView.evaluateJavascript(js, null));
        }

        private byte[] readAll(File file) throws java.io.IOException {
            try (InputStream in = new java.io.FileInputStream(file); java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                return bos.toByteArray();
            }
        }

        /**
         * ID fixo deste celular para a licença do app (um aparelho por usuário).
         * ANDROID_ID não muda ao reinstalar o app (só com restauração de fábrica); vai com hash.
         */
        @JavascriptInterface
        public String getDeviceId() {
            try {
                String raw = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                if (raw == null || raw.isEmpty()) return "";
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] h = md.digest(("jvm-spda:" + raw).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder("AND-");
                for (int i = 0; i < 16; i++) sb.append(String.format("%02x", h[i] & 0xff));
                return sb.toString();
            } catch (Exception e) {
                return "";
            }
        }

        /** Nome do celular (ex.: "Xiaomi 24117RN76O") para mostrar ao administrador */
        @JavascriptInterface
        public String getDeviceName() {
            String man = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
            String model = Build.MODEL == null ? "" : Build.MODEL.trim();
            if (!man.isEmpty()) man = man.substring(0, 1).toUpperCase(Locale.ROOT) + man.substring(1);
            return model.toLowerCase(Locale.ROOT).startsWith(man.toLowerCase(Locale.ROOT)) ? model : (man + " " + model).trim();
        }

        /** Botão "Reiniciar o app" da tela de carregamento */
        @JavascriptInterface
        public void restartApp() {
            MainActivity.this.runOnUiThread(MainActivity.this::restartWholeApp);
        }

        /** Bluetooth do miliohmímetro INBRAT INMD 1 Pro disponível neste app */
        @JavascriptInterface
        public boolean bleAvailable() {
            return true;
        }

        @JavascriptInterface
        public void bleScan(final String requestId, final int seconds) {
            MainActivity.this.runOnUiThread(() -> withBle(requestId, () -> inbratBle.scan(requestId, seconds)));
        }

        @JavascriptInterface
        public void bleMeasure(final String requestId, final String address, final String testName) {
            MainActivity.this.runOnUiThread(() -> withBle(requestId, () -> inbratBle.measure(requestId, address, testName)));
        }

        @JavascriptInterface
        public void bleCancel() {
            MainActivity.this.runOnUiThread(() -> { if (inbratBle != null) { inbratBle.stopScan(); inbratBle.cancel(); } });
        }

        @JavascriptInterface
        public boolean isNativeApp() {
            return true;
        }

        @JavascriptInterface
        public String getStorageDirectory() {
            return "/Download/JVM SPDA/";
        }

        @JavascriptInterface
        public void requestCameraPermissions() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
                    }
                }
            });
        }

        @JavascriptInterface
        public boolean saveBase64File(String base64Data, String mimeType, String fileName, boolean openAfterSave) {
            try {
                byte[] fileBytes = Base64.decode(base64Data, Base64.DEFAULT);
                Uri savedFileUri = null;
                File savedFile = null;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/JVM SPDA");

                    ContentResolver resolver = context.getContentResolver();
                    savedFileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                    if (savedFileUri != null) {
                        try (OutputStream os = resolver.openOutputStream(savedFileUri)) {
                            if (os != null) {
                                os.write(fileBytes);
                                os.flush();
                            }
                        }
                    }
                } else {
                    File downloadsDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "JVM SPDA");
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs();
                    }
                    savedFile = new File(downloadsDir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(savedFile)) {
                        fos.write(fileBytes);
                        fos.flush();
                    }
                    savedFileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", savedFile);
                    MediaScannerConnection.scanFile(context, new String[]{savedFile.getAbsolutePath()}, new String[]{mimeType}, null);
                }

                // Exibe Toast de Confirmação
                runOnUiThread(() -> Toast.makeText(context, "✅ Arquivo salvo: " + fileName + "\nLocal: Downloads/JVM SPDA/", Toast.LENGTH_LONG).show());

                // Emite Notificação com Ação de Abrir
                showFileSavedNotification(fileName, mimeType, savedFileUri);

                // Abre o arquivo automaticamente se solicitado
                if (openAfterSave && savedFileUri != null) {
                    Intent openIntent = new Intent(Intent.ACTION_VIEW);
                    openIntent.setDataAndType(savedFileUri, mimeType);
                    openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(Intent.createChooser(openIntent, "Abrir " + fileName));
                }

                return true;
            } catch (Exception e) {
                Log.e(TAG, "Erro ao salvar arquivo localmente", e);
                runOnUiThread(() -> Toast.makeText(context, "Erro ao salvar arquivo na memória: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                return false;
            }
        }

        /**
         * Grava uma foto automaticamente em Download/JVM SPDA/&lt;subFolder&gt;/ (ex.: OS-SPDA-2026-0001/Bloco A/Captação),
         * sem notificação nem abrir o arquivo — só um aviso curto. Usado ao tirar/anexar fotos dos ensaios.
         */
        @JavascriptInterface
        public boolean savePhotoFile(String base64Data, String mimeType, String subFolder, String fileName) {
            try {
                byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                String folder = "JVM SPDA" + (subFolder == null || subFolder.trim().isEmpty() ? "" : "/" + subFolder.trim());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + folder);
                    ContentResolver resolver = context.getContentResolver();
                    Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("não foi possível criar o arquivo");
                    try (OutputStream os = resolver.openOutputStream(uri)) {
                        if (os != null) { os.write(bytes); os.flush(); }
                    }
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folder);
                    if (!dir.exists()) dir.mkdirs();
                    File out = new File(dir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(bytes); fos.flush(); }
                    MediaScannerConnection.scanFile(context, new String[]{out.getAbsolutePath()}, new String[]{mimeType}, null);
                }
                final String where = "Download/" + folder;
                runOnUiThread(() -> Toast.makeText(context, "📷 Foto salva em " + where, Toast.LENGTH_SHORT).show());
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Erro ao salvar foto na pasta da OS", e);
                runOnUiThread(() -> Toast.makeText(context, "Não foi possível salvar a foto na pasta da OS: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                return false;
            }
        }

        @JavascriptInterface
        public boolean saveTextFile(String content, String mimeType, String fileName) {
            try {
                String base64 = Base64.encodeToString(content.getBytes("UTF-8"), Base64.NO_WRAP);
                return saveBase64File(base64, mimeType, fileName, false);
            } catch (Exception e) {
                Log.e(TAG, "Erro ao salvar arquivo de texto", e);
                return false;
            }
        }

        @JavascriptInterface
        public boolean shareBase64File(String base64Data, String mimeType, String fileName, String title) {
            try {
                byte[] fileBytes = Base64.decode(base64Data, Base64.DEFAULT);
                File cacheDir = new File(context.getCacheDir(), "shared_docs");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File tempFile = new File(cacheDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(fileBytes);
                    fos.flush();
                }

                Uri contentUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", tempFile);

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType(mimeType);
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Documento técnico emitido pelo app JVM SPDA: " + title);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

                context.startActivity(Intent.createChooser(shareIntent, "Compartilhar " + fileName));
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Erro ao compartilhar arquivo", e);
                return false;
            }
        }

        @JavascriptInterface
        public void openLocalDownloads() {
            try {
                Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(context, "Abra o aplicativo 'Arquivos' ou 'Downloads' no seu aparelho.", Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface
        public void vibrate(long ms) {
            try {
                Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    v.vibrate(ms);
                }
            } catch (Exception ignored) {}
        }

        private void showFileSavedNotification(String fileName, String mimeType, Uri fileUri) {
            try {
                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm == null) return;

                Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                viewIntent.setDataAndType(fileUri, mimeType);
                viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

                PendingIntent pi = PendingIntent.getActivity(
                    context, 
                    (int) System.currentTimeMillis(), 
                    Intent.createChooser(viewIntent, "Abrir " + fileName), 
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                );

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("📄 Documento Salvo no Celular")
                    .setContentText(fileName)
                    .setSubText("Downloads/JVM SPDA")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pi);

                nm.notify((int) (System.currentTimeMillis() % 10000), builder.build());
            } catch (Exception e) {
                Log.w(TAG, "Erro ao emitir notificação", e);
            }
        }
    }
}
