package io.netbird.client;

import android.app.Application;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.netbird.client.repository.VPNServiceRepository;
import io.netbird.client.tool.NetworkChangeNotifier;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Set Theme at start
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(themeMode);
        registerNetworkReceiver();
    }

    public void registerNetworkReceiver() {
        RouteChangeReceiver receiver = new RouteChangeReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiver,
                new IntentFilter(NetworkChangeNotifier.action)
        );
    }

    public VPNServiceRepository getVPNServiceRepository() {
        return new VPNServiceRepository(this);
    }
}