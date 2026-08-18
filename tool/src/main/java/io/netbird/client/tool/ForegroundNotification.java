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
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

class ForegroundNotification {
    private static final int NOTIFICATION_ID = 102;

    private final VpnService service;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = this::refreshSessionText;
    private boolean foregroundActive;
    private long sessionExpiresAtUnixSeconds;

    public ForegroundNotification(android.net.VpnService vpnService) {
        this.service = vpnService;
    }

    public void startForeground() {
        foregroundActive = true;
        service.startForeground(NOTIFICATION_ID, buildNotification());
        scheduleSessionTextRefresh();
    }

    public void stopForeground() {
        foregroundActive = false;
        refreshHandler.removeCallbacks(refreshRunnable);
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
        scheduleSessionTextRefresh();
    }

    // refreshSessionText re-posts the notification so the "expires in …" label
    // tracks the countdown, then schedules the next refresh. The channel is
    // IMPORTANCE_LOW, so re-posting is silent.
    private void refreshSessionText() {
        if (!foregroundActive || sessionExpiresAtUnixSeconds <= 0) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification());
        scheduleSessionTextRefresh();
    }

    private void scheduleSessionTextRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        if (!foregroundActive || sessionExpiresAtUnixSeconds <= 0) {
            return;
        }
        long remainingMs = sessionExpiresAtUnixSeconds * 1000L - System.currentTimeMillis();
        refreshHandler.postDelayed(refreshRunnable, refreshIntervalMs(remainingMs));
    }

    // refreshIntervalMs mirrors the desktop tray's sessionRefreshInterval:
    // coarse when the deadline is far off, down to 10s in the final two
    // minutes so the label doesn't lag the ceiling-rounded countdown.
    private static long refreshIntervalMs(long remainingMs) {
        if (remainingMs <= 0) {
            return 30_000L;
        }
        if (remainingMs <= 2 * 60_000L) {
            return 10_000L;
        }
        if (remainingMs <= 60 * 60_000L) {
            return 30_000L;
        }
        return 60_000L;
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
            builder.setContentText(formatSessionExpiry(expiresAtMs - System.currentTimeMillis()))
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

    // formatSessionExpiry renders the time left as a localised sentence,
    // matching the desktop tray and the home screen's session banner: the
    // largest non-zero unit, rounded up so the label never claims less time
    // than actually remains, with a "less than a minute" sub-minute tail.
    private String formatSessionExpiry(long remainingMs) {
        long minute = 60_000L;
        long hour = 60 * minute;
        long day = 24 * hour;
        if (remainingMs <= 0) {
            return service.getString(R.string.fg_notification_session_expired);
        }
        if (remainingMs < minute) {
            return service.getString(R.string.fg_notification_session_expires_soon);
        }
        if (remainingMs <= 59 * minute) {
            long minutes = ceilDiv(remainingMs, minute);
            return service.getResources().getQuantityString(
                    R.plurals.fg_notification_session_expires_minutes, (int) minutes, minutes);
        }
        if (remainingMs <= 23 * hour) {
            long hours = ceilDiv(remainingMs, hour);
            return service.getResources().getQuantityString(
                    R.plurals.fg_notification_session_expires_hours, (int) hours, hours);
        }
        long days = ceilDiv(remainingMs, day);
        return service.getResources().getQuantityString(
                R.plurals.fg_notification_session_expires_days, (int) days, days);
    }

    // ceilDiv divides d by unit rounding up, assuming d > 0.
    private static long ceilDiv(long d, long unit) {
        return (d + unit - 1) / unit;
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
