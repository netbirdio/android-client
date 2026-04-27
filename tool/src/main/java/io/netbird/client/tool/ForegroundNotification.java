package io.netbird.client.tool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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
        service.startForeground(
                NOTIFICATION_ID,
                buildNotification(
                        service.getResources().getString(R.string.fg_notification_text),
                        true));
    }

    public void stopForeground() {
        service.stopForeground(true);
    }

    public void showNotification(String text) {
        getNotificationManager().notify(NOTIFICATION_ID, buildNotification(text, false));
    }

    private Notification buildNotification(String text, boolean ongoing) {
        ensureNotificationChannel();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                service.getApplication(),
                service.getPackageName())
                .setSmallIcon(R.drawable.notification_icon)
                .setColor(Color.GRAY)
                .setContentTitle(service.getResources().getString(R.string.service_name))
                .setContentText(text)
                .setAutoCancel(!ongoing)
                .setOngoing(ongoing);

        PendingIntent pendingIntent = createLaunchAppPendingIntent();
        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent);
        }

        return builder.build();
    }

    private void ensureNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                service.getPackageName(),
                service.getResources().getString(R.string.fg_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        getNotificationManager().createNotificationChannel(channel);
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private PendingIntent createLaunchAppPendingIntent() {
        Intent notificationIntent = service.getPackageManager().getLaunchIntentForPackage(service.getPackageName());
        if (notificationIntent == null) {
            notificationIntent = new Intent();
            notificationIntent.setClassName(
                    service.getPackageName(),
                    service.getPackageName() + ".MainActivity");
        }

        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                service,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
