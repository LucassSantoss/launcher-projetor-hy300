package com.mlab.projetor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recebe o resultado final da sessao assincrona do PackageInstaller. */
public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Updater.onInstallResult(context, intent);
    }
}
