package io.netbird.client.tool.autoconnect;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import io.netbird.client.tool.R;

/**
 * Tells the user auto-connect couldn't run because VPN consent was never
 * granted or was revoked — only an Activity can show that system dialog, so
 * a background trigger can't recover from this on its own. Rate-limited so a
 * network that keeps matching a trigger while consent is missing doesn't
 * spam the user.
 */
public final class AutoConnectConsentNotification {
    private static final String LOGTAG = "AutoConnectConsent";
    private static final int NOTIFICATION_ID = 104;
    private static final String CHANNEL_ID = "netbird_auto_connect";
    private static final long MIN_INTERVAL_MS = 10 * 60 * 1000;

    private static volatile long lastShownAtMs = 0;

    private AutoConnectConsentNotification() {
    }

    public static void show(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastShownAtMs < MIN_INTERVAL_MS) {
            return;
        }
        lastShownAtMs = now;

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.auto_connect_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        manager.createNotificationChannel(channel);

        Intent intent = new Intent();
        intent.setClassName("io.netbird.client", "io.netbird.client.MainActivity");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon_error)
                .setContentTitle(context.getString(R.string.auto_connect_consent_notification_title))
                .setContentText(context.getString(R.string.auto_connect_consent_notification_text))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.auto_connect_consent_notification_text)))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        try {
            manager.notify(NOTIFICATION_ID, notification);
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS runtime permission not granted (API 33+)
            Log.w(LOGTAG, "cannot post auto-connect consent notification", e);
        }
    }
}
