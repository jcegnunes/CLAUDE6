package br.com.jvmengenharia.spda;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;

/**
 * Reinicia o app inteiro (botão "Reiniciar o app" da tela de carregamento, ou WebView que caiu).
 * Roda num processo separado (":restart"): encerra o processo principal — o que destrava o
 * armazenamento do WebView, o mesmo efeito de reiniciar o celular — e abre o app de novo.
 */
public class RestartActivity extends Activity {
    static final String EXTRA_PID = "main_pid";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pid = getIntent().getIntExtra(EXTRA_PID, -1);
        if (pid > 0 && pid != Process.myPid()) {
            try { Process.killProcess(pid); } catch (Exception ignored) {}
        }
        Intent main = new Intent(this, MainActivity.class);
        main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(main);
        finish();
        Process.killProcess(Process.myPid());
    }
}
