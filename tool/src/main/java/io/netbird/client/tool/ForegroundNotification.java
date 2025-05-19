package io.netbird.client.tool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Build;

import androidx.core.app.NotificationCompat;

class ForegroundNotification {
    private static final int NOTIFICATION_ID = 102;

    private final VpnService service;

    public ForegroundNotification(android.net.VpnService vpnService) {
        this.service = vpnService;
    }

    public void startForeground() {
        String channelId = service.getPackageName();
        NotificationChannel channel = new NotificationChannel(
                channelId,
                service.getResources().getString(R.string.fg_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        ((NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

        Intent notificationIntent = new Intent();
        notificationIntent.setClassName("io.netbird.client", "io.netbird.client.MainActivity");

        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(service, 0, notificationIntent, flags);


        Notification notification = new NotificationCompat.Builder(service.getApplication(), channelId)
                .setSmallIcon(R.drawable.notification_icon)
                .setColor(Color.GRAY)
                .setContentTitle(service.getResources().getString(R.string.service_name))
                .setContentText(service.getResources().getString(R.string.fg_notification_text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)  // Keep notification after tap
                .build();

        service.startForeground(NOTIFICATION_ID, notification);
    }

    public void stopForeground() {
        service.stopForeground(true);
    }
}
