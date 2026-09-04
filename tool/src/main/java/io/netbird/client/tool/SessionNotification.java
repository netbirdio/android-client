package io.netbird.client.tool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * Posts the auth-session warnings on their own high-importance channel so the
 * user learns about an expiring or expired session even when no UI is running
 * (always-on VPN, boot start). MainActivity shows its own dialog for the
 * foreground case; this notification is the background fallback.
 */
class SessionNotification {
    private static final String LOGTAG = "SessionNotification";
    private static final int NOTIFICATION_ID = 103;
    private static final String CHANNEL_ID = "netbird_session";

    private final Context context;

    SessionNotification(Context context) {
        this.context = context;
    }

    void showExpiring(long leadMinutes) {
        show(context.getString(R.string.session_notification_expiring_title),
                context.getString(R.string.session_notification_expiring_text, leadMinutes));
    }

    void showExpired() {
        show(context.getString(R.string.session_notification_expired_title),
                context.getString(R.string.session_notification_expired_text));
    }

    void cancel() {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID);
    }

    private void show(String title, String text) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.session_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        manager.createNotificationChannel(channel);

        Intent intent = new Intent();
        intent.setClassName("io.netbird.client", "io.netbird.client.MainActivity");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon_error)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        try {
            manager.notify(NOTIFICATION_ID, notification);
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS runtime permission not granted (API 33+)
            Log.w(LOGTAG, "cannot post session notification", e);
        }
    }
}
