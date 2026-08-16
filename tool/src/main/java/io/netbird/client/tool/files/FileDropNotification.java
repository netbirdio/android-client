package io.netbird.client.tool.files;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import io.netbird.client.tool.R;

/**
 * Posts the consent prompt for an incoming transfer, so an offer is answerable
 * without the app in the foreground. The accept and decline buttons go to
 * {@link FileDropActionReceiver} rather than to an activity: answering an offer
 * needs no UI, and opening one would be an interruption in its own right.
 * <p>
 * Notification ids are derived from the transfer id so several offers coexist
 * instead of overwriting each other.
 */
public class FileDropNotification {

    private static final String LOGTAG = "FileDropNotification";
    private static final String CHANNEL_ID = "netbird_file_drop";
    private static final int NOTIFICATION_ID_BASE = 200;

    private final Context context;

    public FileDropNotification(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    public void showOffer(@NonNull FileDropManager.Transfer transfer) {
        NotificationManager manager = manager();
        createChannel(manager);

        String title = context.getString(R.string.file_drop_notification_offer_title, transfer.peerName());
        String text = transfer.label();

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.file_drop_notification_decline),
                        actionIntent(FileDropActionReceiver.ACTION_DECLINE, transfer.id()))
                .addAction(0, context.getString(R.string.file_drop_notification_accept),
                        actionIntent(FileDropActionReceiver.ACTION_ACCEPT, transfer.id()))
                .build();

        post(manager, notificationId(transfer.id()), notification);
    }

    public void cancelOffer(@NonNull String transferId) {
        manager().cancel(notificationId(transferId));
    }

    /**
     * Tracks one transfer through to its outcome. While it runs the bar shows
     * progress, and the final state replaces it in place, so a send started from
     * the share sheet stays visible without the app in the foreground.
     */
    public void showProgress(@NonNull FileDropManager.Transfer transfer) {
        NotificationManager manager = manager();
        createChannel(manager);

        int percent = transfer.totalSize() > 0
                ? (int) (transfer.transferred() * 100 / transfer.totalSize())
                : 0;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(transfer.label())
                .setContentText(context.getString(transfer.outgoing()
                                ? R.string.file_drop_notification_sending_to
                                : R.string.file_drop_notification_receiving_from,
                        transfer.peerName()))
                .setProgress(100, percent, transfer.totalSize() <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        post(manager, notificationId(transfer.id()), builder.build());
    }

    /** Replaces a progress notification with its terminal outcome. */
    public void showOutcome(@NonNull FileDropManager.Transfer transfer) {
        NotificationManager manager = manager();
        createChannel(manager);

        String title = transfer.label();
        String text;
        if (transfer.isFailed()) {
            text = context.getString(R.string.file_drop_notification_failed, transfer.peerName());
        } else if (transfer.outgoing()) {
            text = context.getString(R.string.file_drop_notification_sent, transfer.peerName());
        } else {
            text = context.getString(R.string.file_drop_notification_received, transfer.peerName());
        }

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();

        post(manager, notificationId(transfer.id()), notification);
    }

    /**
     * Notification id for a transfer. Hash collisions only mean two offers share
     * a notification slot, which is why the id also travels in the intent extra.
     */
    static int notificationId(@NonNull String transferId) {
        return NOTIFICATION_ID_BASE + Math.abs(transferId.hashCode() % 1000);
    }

    private NotificationManager manager() {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void createChannel(NotificationManager manager) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.file_drop_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        manager.createNotificationChannel(channel);
    }

    private PendingIntent actionIntent(String action, String transferId) {
        Intent intent = new Intent(context, FileDropActionReceiver.class);
        intent.setAction(action);
        intent.putExtra(FileDropActionReceiver.EXTRA_TRANSFER_ID, transferId);

        // The request code has to distinguish accept from decline for the same
        // transfer, or FLAG_UPDATE_CURRENT would fold them into one intent.
        int requestCode = (transferId + action).hashCode();
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void post(NotificationManager manager, int id, Notification notification) {
        try {
            manager.notify(id, notification);
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS runtime permission not granted (API 33+)
            Log.w(LOGTAG, "cannot post file drop notification", e);
        }
    }
}
