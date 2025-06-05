package io.netbird.client;

import android.app.Application;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.netbird.client.tool.NetworkChangeNotifier;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        registerNetworkReceiver();
    }

    public void registerNetworkReceiver() {
        RouteChangeReceiver receiver = new RouteChangeReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiver,
                new IntentFilter(NetworkChangeNotifier.action)
        );
    }
}