package io.netbird.client;

import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;
import static io.netbird.client.MainActivity.MESSAGE_KEY;
import static io.netbird.client.MainActivity.NEW_ROUTE_MESSAGE;
import static io.netbird.client.MainActivity.NOTIFICATION_IN_APP_KEY;
import static io.netbird.client.MainActivity.NOTIFICATION_KEY;
import static io.netbird.client.MainActivity.SEND_EVENT_ACTION;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.List;
import java.util.Objects;

public class NotificationReceiver extends BroadcastReceiver {
    private final String TAG = "NotificationReceiver";
    private final int NOTIFICATION_ID = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: ");
        if (isBackgroundRunning(context)) {
            if (!isNotificationVisible(context)) {
                showNotification(context);
                sendNotificationEvent(context, NOTIFICATION_KEY, NEW_ROUTE_MESSAGE);
            }
        } else {
            sendNotificationEvent(context, NOTIFICATION_IN_APP_KEY, NEW_ROUTE_MESSAGE);
        }
    }

    private void sendNotificationEvent(Context context, String messageKey, String message) {
        try {
            MainApplication application = (MainApplication) context;
            new Thread(() -> runOnUiThread(() -> {
                WritableMap params = Arguments.createMap();
                params.putString(messageKey, message);
                sendEvent(Objects.requireNonNull(application.getReactNativeHost().getReactInstanceManager().getCurrentReactContext()), "onError", params);
            })).start();
        } catch (ClassCastException exception) {
            Log.e(TAG, "exception message -> " + exception.getMessage());
            SharedPreferences sharedPreferences = context.getApplicationContext().getSharedPreferences(SEND_EVENT_ACTION, Context.MODE_PRIVATE);
            sharedPreferences.edit().putString(MESSAGE_KEY, messageKey).apply();
        }
    }

    private void showNotification(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String NOTIFICATION_CHANNEL_ID = "my_channel_id_01";
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "My Notifications", NotificationManager.IMPORTANCE_HIGH);

        // Configure the notification channel.
        notificationChannel.setDescription("Channel description");
        notificationChannel.enableLights(true);
        notificationChannel.setLightColor(Color.RED);
        notificationManager.createNotificationChannel(notificationChannel);


        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID);

        notificationBuilder.setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.notification_icon)
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

    public static void sendEvent(ReactContext reactContext,
                           String eventName,
                           @Nullable Object params) {

        try {
            DeviceEventManagerModule.RCTDeviceEventEmitter jsm = reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);
            if (jsm != null) {
                jsm.emit(eventName, params);
            }
        } catch (Error e) {
            Log.d("NotificationReceiver", "Error: " + e.getMessage());
        }
    }

    public static boolean isBackgroundRunning(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
            if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                for (String activeProcess : processInfo.pkgList) {
                    if (activeProcess.equals(context.getPackageName())) {
                        //If your app is the process in foreground, then it's not in running in background
                        return false;
                    }
                }
            }
        }
        return true;
    }
}