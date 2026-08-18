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
import android.text.format.DateUtils;

import androidx.core.app.NotificationCompat;

class ForegroundNotification {
    private static final int NOTIFICATION_ID = 102;

    /**
     * Connection states the status-bar icon distinguishes, mirroring the
     * desktop tray's iconForState(). The glyphs are the macOS template icons
     * from client/ui/assets: Android tints the small icon itself and reads
     * only its alpha, so the colored Windows/Linux variants are unusable and
     * the desktop's needs-login glyph is byte-identical to the error one as a
     * template (the two differ by color alone, which a tinted small icon
     * cannot carry), so NEEDS_LOGIN shares the icon and is told apart by its
     * text. NO_NETWORK has no desktop tray glyph of its own either, so it
     * reuses the disconnected one on the same terms.
     */
    enum State {
        CONNECTING(R.drawable.notification_icon_connecting),
        CONNECTED(R.drawable.notification_icon_connected),
        DISCONNECTED(R.drawable.notification_icon_disconnected),
        NO_NETWORK(R.drawable.notification_icon_disconnected),
        NEEDS_LOGIN(R.drawable.notification_icon_error),
        ERROR(R.drawable.notification_icon_error);

        final int icon;

        State(int icon) {
            this.icon = icon;
        }
    }

    private final VpnService service;
    private boolean foregroundActive;
    private long sessionExpiresAtUnixSeconds;
    private State state = State.CONNECTING;

    public ForegroundNotification(android.net.VpnService vpnService) {
        this.service = vpnService;
    }

    // The methods below are synchronized: they are called from the main
    // thread and from the Go engine's callback threads, and each one reads
    // and writes the state fields around a notify.

    public synchronized void startForeground() {
        foregroundActive = true;
        service.startForeground(NOTIFICATION_ID, buildNotification());
    }

    public synchronized void stopForeground() {
        foregroundActive = false;
        service.stopForeground(true);
    }

    /**
     * Swaps the status-bar icon and text for the new connection state. The
     * notification is only re-posted while the service is in the foreground;
     * otherwise the state is remembered for the next {@link #startForeground}.
     */
    public synchronized void setState(State newState) {
        if (state == newState) {
            return;
        }
        state = newState;
        if (!foregroundActive) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification());
    }

    /**
     * Shows the session deadline (live countdown + "Extend session" action)
     * on the persistent notification, or reverts to the plain connected text
     * when the deadline is cleared (0). No-op while the service is not in
     * the foreground.
     */
    public synchronized void updateSessionDeadline(long expiresAtUnixSeconds) {
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
                .setSmallIcon(state.icon)
                .setColor(Color.GRAY)
                .setContentTitle(service.getResources().getString(R.string.service_name))
                .setContentIntent(pendingIntent)
                .setAutoCancel(false);  // Keep notification after tap

        if (state != State.CONNECTED) {
            builder.setContentText(service.getResources().getString(statusText()));
        } else if (sessionExpiresAtUnixSeconds > 0) {
            long expiresAtMs = sessionExpiresAtUnixSeconds * 1000L;
            // Always day + time ("today, 9:15 PM" / "tomorrow, 9:15 AM"), so a
            // deadline past midnight cannot be misread as today's clock time.
            // DateUtils localizes the day wording for every locale we ship.
            String expiresAt = DateUtils.getRelativeDateTimeString(service, expiresAtMs,
                    DateUtils.DAY_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString();
            builder.setContentText(service.getString(R.string.fg_notification_session_text, expiresAt))
                    // The chronometer renders a system-driven live countdown
                    // in the header — no periodic re-posting needed.
                    .setWhen(expiresAtMs)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .addAction(0, service.getString(R.string.session_notification_extend), extendIntent());
        } else {
            // Connected, but the server published no session deadline (e.g. a
            // setup-key peer): state the connection rather than the service.
            builder.setContentText(service.getResources().getString(R.string.fg_notification_connected));
        }

        return builder.build();
    }

    private int statusText() {
        switch (state) {
            case CONNECTING:
                return R.string.fg_notification_connecting;
            case DISCONNECTED:
                return R.string.fg_notification_disconnected;
            case NO_NETWORK:
                return R.string.fg_notification_no_network;
            case NEEDS_LOGIN:
                return R.string.fg_notification_needs_login;
            case ERROR:
                return R.string.fg_notification_error;
            default:
                return R.string.fg_notification_connected;
        }
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
