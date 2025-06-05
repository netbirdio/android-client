package io.netbird.client;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import androidx.core.app.NotificationCompat;

import java.util.Arrays;

public class RouteChangeReceiver extends BroadcastReceiver {
    private final int NOTIFICATION_ID = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (isAppInBackground(context) && !isNotificationVisible(context)) {
            showNotification(context);
        }
    }

    private void showNotification(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "routes_changed";
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationChannel notificationChannel = new NotificationChannel(channelId, "My Notifications", NotificationManager.IMPORTANCE_HIGH);

        // Configure the notification channel.
        notificationChannel.setDescription("Channel description");
        notificationChannel.enableLights(true);
        notificationChannel.setLightColor(Color.RED);
        notificationManager.createNotificationChannel(notificationChannel);


        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, channelId);

        notificationBuilder.setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(io.netbird.client.tool.R.drawable.notification_icon)
                .setColor(Color.GRAY)
                .setContentTitle("Stale configuration")
                .setContentText("Your network administrator changed the configuration.")
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true);

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private boolean isNotificationVisible(Context context) {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent test = PendingIntent.getActivity(context, 123,
                notificationIntent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        return test != null;
    }

    public static boolean isAppInBackground(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        String packageName = context.getPackageName();

        for (ActivityManager.RunningAppProcessInfo process : am.getRunningAppProcesses()) {
            if(process.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                continue;
            }
            if (!Arrays.asList(process.pkgList).contains(packageName)) {
                continue;
            }
            return false; // App is in the foreground
        }
        return true; // App is not in the foreground
    }
}