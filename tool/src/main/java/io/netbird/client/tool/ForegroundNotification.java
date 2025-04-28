package io.netbird.client.tool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.net.VpnService;

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

        Notification notification = new NotificationCompat.Builder(service.getApplication(), channelId)
                .setSmallIcon(R.drawable.notification_icon)
                .setColor(Color.GRAY)
                .setContentTitle(service.getResources().getString(R.string.service_name))
                .setContentText(service.getResources().getString(R.string.fg_notification_text))
                .build();

        service.startForeground(NOTIFICATION_ID, notification);
    }

    public void stopForeground() {
        service.stopForeground(true);
    }
}
