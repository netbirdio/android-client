package com.netbirdlib;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class MonitorServiceManager extends Service {

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    try {
      String b = intent.getStringExtra("mainActivity");

      Notification.Builder builder = new Notification.Builder(this);
      builder.setContentTitle("netbird.io");
      Class c = Class.forName(b).getClass();
      Intent builderIntent = new Intent(this, c);
      PendingIntent pendingIntent = PendingIntent.getService(
        this, 0, builderIntent, PendingIntent.FLAG_UPDATE_CURRENT
      );
      builder.setContentIntent(pendingIntent);

      NotificationManager notificationManager = null;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        NotificationChannel channel = new NotificationChannel("netbird_notification_service", "netbird test", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("netbird notification description");
        notificationManager = (NotificationManager) this.getSystemService(
          Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(channel);
        builder.setChannelId(channel.getId());
      }
      builder.build();
      startForeground(400, builder.build());
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }

    return super.onStartCommand(intent, flags, startId);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

}
