package com.mlab.projetor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        // Receber o broadcast ja cria o processo (App.onCreate sobe o servidor).
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)) {
            App.inst.startKeepAlive();
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            // Atualizou: o Android encerrou o app; volta para a tela inicial (o controle do celular volta junto).
            App.inst.startKeepAlive();
            Intent home = new Intent(c, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(home);
            } catch (RuntimeException ignored) {
            }
            App.inst.controller.hud.message("Projetor atualizado");
        }
    }
}
