package com.mlab.projetor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        // Receber o broadcast ja cria o processo (App.onCreate sobe o servidor).
        String a = i.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(a);
        boolean atualizou = Intent.ACTION_MY_PACKAGE_REPLACED.equals(a);
        if (!boot && !atualizou) return;
        App.inst.startKeepAlive();
        // Nos dois casos a tela inicial precisa voltar:
        // - no boot, o sistema resume a tela inicial do fabricante antes do app existir
        //   (isso acontece na fase de boot travado, em que so apps directBootAware contam);
        // - depois de uma atualizacao o Android encerra o app.
        // O controle do celular volta junto.
        Intent home = new Intent(c, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(home);
        } catch (RuntimeException ignored) {
        }
        if (atualizou) App.inst.controller.hud.message("Projetor atualizado");
    }
}
