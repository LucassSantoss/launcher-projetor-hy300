package com.mlab.projetor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Servico em primeiro plano que mantem vivo o processo onde roda o servidor web (ver App). */
public class ControlService extends Service {
    static final int PORT = 8080;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("ctl", "Controle pelo celular",
                NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "ctl")
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle("Controle pelo celular ativo")
                .build();
        startForeground(1, n);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
