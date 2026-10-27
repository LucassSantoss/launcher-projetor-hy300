package com.mlab.projetor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        // Receber o broadcast ja cria o processo (App.onCreate sobe o servidor).
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) App.inst.startKeepAlive();
    }
}
