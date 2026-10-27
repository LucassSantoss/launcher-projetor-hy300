package com.mlab.projetor;

import android.app.Application;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.os.StrictMode;
import android.os.UserHandle;
import android.util.Log;

public class App extends Application {
    static App inst;
    Controller controller;
    WebServer server;

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        // Permite abrir arquivos enviados pelo celular com Uri file:// nos players do sistema.
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
        Ui.init(this);
        if (Prefs.netflixFix(this)) applyNetflixFix();
        takeOverSystemUi();
        // Instalado direto no projetor (sem o instalar.bat): vira a tela inicial na primeira vez.
        Sistema.claimHomeOnce(this);
        controller = new Controller(this);
        // O servidor vive no processo do app (que e o launcher, entao esta sempre de pe);
        // o servico em primeiro plano so protege o processo de ser encerrado.
        server = new WebServer(this, controller, ControlService.PORT);
        server.start();
        startKeepAlive();
        Updater.schedule(this);
    }

    /**
     * Desliga o Codec2 para o sistema usar os decodificadores OMX. O AAC do Codec2 falha
     * (erro 0xe) quando a Netflix troca o audio do filme pelo do anuncio, gerando o erro 5.0.
     * A propriedade nao sobrevive a reinicios, por isso e reaplicada sempre que o app sobe.
     */
    private static void applyNetflixFix() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            sp.getMethod("set", String.class, String.class).invoke(null, "debug.stagefright.ccodec", "0");
        } catch (Exception e) {
            Log.w("Projetor", "nao foi possivel aplicar o ajuste da Netflix", e);
        }
    }

    /**
     * Troca os avisos do fabricante pelos do app, a cada inicializacao (o fabricante religa os dele):
     * - desliga o "modo mouse" do com.sxk.testdsndpi (avisos "enter/exit mouse");
     * - liga o KeyService, que mostra o volume do controle fisico no aviso do app.
     */
    private void takeOverSystemUi() {
        try {
            getPackageManager().setComponentEnabledSetting(
                    new ComponentName("com.sxk.testdsndpi", "com.sxk.testdsndpi.MyService"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            Log.w("Projetor", "modo mouse do fabricante nao encontrado", e);
        }
        try {
            ContentResolver cr = getContentResolver();
            // Mantem os servicos de acessibilidade de outros apps; so tira o modo mouse do fabricante.
            String mine = new ComponentName(this, KeyService.class).flattenToString();
            String cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            StringBuilder list = new StringBuilder();
            if (cur != null) {
                for (String svc : cur.split(":")) {
                    if (svc.isEmpty() || svc.equals(mine) || svc.startsWith("com.sxk.testdsndpi/")) continue;
                    list.append(svc).append(':');
                }
            }
            list.append(mine);
            // Depois de uma atualizacao do app o Android deixa o servico "habilitado mas desligado";
            // limpar e gravar de novo forca a reconexao.
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "");
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.toString());
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (Exception e) {
            Log.w("Projetor", "nao foi possivel ligar o KeyService", e);
        }
    }

    /**
     * Processos com uid de sistema precisam indicar o usuario ao iniciar servicos;
     * sem isso o Android 11 ignora a chamada em silencio.
     */
    void startKeepAlive() {
        Intent i = new Intent(this, ControlService.class);
        try {
            UserHandle user = UserHandle.getUserHandleForUid(0);
            getClass().getMethod("startForegroundServiceAsUser", Intent.class, UserHandle.class)
                    .invoke(this, i, user);
        } catch (Exception e) {
            Log.w("Projetor", "startForegroundServiceAsUser indisponivel", e);
            startForegroundService(i);
        }
    }
}
