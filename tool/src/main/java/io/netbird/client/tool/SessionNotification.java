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
 * Posts the auth-session notifications on their own high-importance channel so
 * the user learns about an expiring or expired session even when no UI is
 * running (always-on VPN, boot start). The "expiring" one is posted by
 * {@link SessionWarningWorker} and the "expired" one by the service when the
 * run loop reports NeedsLogin.
 */
class SessionNotification {
    private static final String LOGTAG = "SessionNotification";
    private static final int EXPIRED_NOTIFICATION_ID = 103;
    private static final int EXPIRING_NOTIFICATION_ID = 104;
    private static final String CHANNEL_ID = "netbird_session";

    private final Context context;

    SessionNotification(Context context) {
        this.context = context;
    }

    void showExpiring(long minutesLeft, long deadlineMs) {
        show(EXPIRING_NOTIFICATION_ID,
                context.getString(R.string.session_notification_expiring_title),
                context.getString(R.string.session_notification_expiring_text, minutesLeft),
                deadlineMs - System.currentTimeMillis());
    }

    void showExpired() {
        cancelExpiring();
        show(EXPIRED_NOTIFICATION_ID,
                context.getString(R.string.session_notification_expired_title),
                context.getString(R.string.session_notification_expired_text),
                0);
    }

    void cancelExpiring() {
        manager().cancel(EXPIRING_NOTIFICATION_ID);
    }

    void cancelExpired() {
        manager().cancel(EXPIRED_NOTIFICATION_ID);
    }

    private NotificationManager manager() {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void show(int id, String title, String text, long timeoutMs) {
        NotificationManager manager = manager();
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.session_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        manager.createNotificationChannel(channel);

        Intent intent = new Intent();
        intent.setClassName("io.netbird.client", "io.netbird.client.MainActivity");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon_error)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        if (timeoutMs > 0) {
            builder.setTimeoutAfter(timeoutMs);
        }
        Notification notification = builder.build();
        try {
            manager.notify(id, notification);
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS runtime permission not granted (API 33+)
            Log.w(LOGTAG, "cannot post session notification", e);
        }
    }
}
