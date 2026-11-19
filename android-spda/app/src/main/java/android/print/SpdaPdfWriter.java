package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

import java.io.File;

/**
 * Grava em arquivo o PDF gerado pelo motor de impressão do WebView (o mesmo do
 * "Imprimir → Salvar como PDF"), sem abrir a janela de impressão.
 * PDF vetorial: texto nítido e selecionável, linhas e tabelas em vetor, fotos na resolução original.
 *
 * Fica no pacote android.print porque os construtores de LayoutResultCallback e
 * WriteResultCallback não são públicos fora dele.
 */
public final class SpdaPdfWriter {

    public interface Callback {
        void onSuccess(File file);
        void onFailure(String message);
    }

    private SpdaPdfWriter() {}

    public static void write(final PrintDocumentAdapter adapter, final PrintAttributes attributes, final File out, final Callback callback) {
        try {
            adapter.onStart();
        } catch (Exception ignored) { /* nem todo adaptador precisa */ }
        adapter.onLayout(null, attributes, null, new PrintDocumentAdapter.LayoutResultCallback() {
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                final ParcelFileDescriptor pfd;
                try {
                    File dir = out.getParentFile();
                    if (dir != null && !dir.exists()) dir.mkdirs();
                    pfd = ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_READ_WRITE);
                } catch (Exception e) {
                    finish(adapter);
                    callback.onFailure("não foi possível criar o arquivo: " + e.getMessage());
                    return;
                }
                adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, pfd, new CancellationSignal(), new PrintDocumentAdapter.WriteResultCallback() {
                    @Override
                    public void onWriteFinished(PageRange[] pages) {
                        close(pfd);
                        finish(adapter);
                        if (out.length() > 0) callback.onSuccess(out);
                        else callback.onFailure("o PDF saiu vazio");
                    }

                    @Override
                    public void onWriteFailed(CharSequence error) {
                        close(pfd);
                        finish(adapter);
                        callback.onFailure(error == null ? "falha ao gravar o PDF" : error.toString());
                    }

                    @Override
                    public void onWriteCancelled() {
                        close(pfd);
                        finish(adapter);
                        callback.onFailure("geração do PDF cancelada");
                    }
                });
            }

            @Override
            public void onLayoutFailed(CharSequence error) {
                finish(adapter);
                callback.onFailure(error == null ? "falha ao montar as páginas" : error.toString());
            }

            @Override
            public void onLayoutCancelled() {
                finish(adapter);
                callback.onFailure("geração do PDF cancelada");
            }
        }, null);
    }

    private static void close(ParcelFileDescriptor pfd) {
        try { pfd.close(); } catch (Exception ignored) { }
    }

    private static void finish(PrintDocumentAdapter adapter) {
        try { adapter.onFinish(); } catch (Exception ignored) { }
    }
}
