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
import android.text.format.DateFormat;

import androidx.core.app.NotificationCompat;

import java.util.Date;

class ForegroundNotification {
    private static final int NOTIFICATION_ID = 102;

    private final VpnService service;
    private boolean foregroundActive;
    private long sessionExpiresAtUnixSeconds;

    public ForegroundNotification(android.net.VpnService vpnService) {
        this.service = vpnService;
    }

    public void startForeground() {
        foregroundActive = true;
        service.startForeground(NOTIFICATION_ID, buildNotification());
    }

    public void stopForeground() {
        foregroundActive = false;
        service.stopForeground(true);
    }

    /**
     * Shows the session deadline (live countdown + "Extend session" action)
     * on the persistent notification, or reverts to the plain "service is
     * running" text when the deadline is cleared (0). No-op while the
     * service is not in the foreground.
     */
    public void updateSessionDeadline(long expiresAtUnixSeconds) {
        this.sessionExpiresAtUnixSeconds = expiresAtUnixSeconds;
        if (!foregroundActive) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        String channelId = service.getPackageName();
        NotificationChannel channel = new NotificationChannel(
                channelId,
                service.getResources().getString(R.string.fg_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        channel.enableVibration(false);
        ((NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

        Intent notificationIntent = new Intent();
        notificationIntent.setClassName("io.netbird.client", "io.netbird.client.MainActivity");

        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(service, 0, notificationIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(service.getApplication(), channelId)
                .setSmallIcon(R.drawable.notification_icon)
                .setColor(Color.GRAY)
                .setContentTitle(service.getResources().getString(R.string.service_name))
                .setContentIntent(pendingIntent)
                .setAutoCancel(false);  // Keep notification after tap

        if (sessionExpiresAtUnixSeconds > 0) {
            long expiresAtMs = sessionExpiresAtUnixSeconds * 1000L;
            String expiresAt = DateFormat.getTimeFormat(service).format(new Date(expiresAtMs));
            builder.setContentText(service.getString(R.string.fg_notification_session_text, expiresAt))
                    // The chronometer renders a system-driven live countdown
                    // in the header — no periodic re-posting needed.
                    .setWhen(expiresAtMs)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .addAction(0, service.getString(R.string.session_notification_extend), extendIntent());
        } else {
            builder.setContentText(service.getResources().getString(R.string.fg_notification_text));
        }

        return builder.build();
    }

    private PendingIntent extendIntent() {
        Intent intent = new Intent();
        intent.setClassName("io.netbird.client", "io.netbird.client.MainActivity");
        intent.setAction(VPNService.ACTION_EXTEND_SESSION);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(service, 1, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
