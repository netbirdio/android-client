package io.netbird.client;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Set Theme at start
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(themeMode);

        // NOTE: the MDM policy fetcher is registered on the goClient
        // instance inside EngineRunner — see EngineRunner constructor.
        // Process-wide registration was removed when the Go side moved
        // to per-Client DI for the Loader.
    }
}