package io.netbird.client;

import android.util.Log;
import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import io.netbird.gomobile.android.Android;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

	    try {
            byte[] certsPem = PlatformUtils.getSystemAndUserCertificates();
            Android.setAndroidCertificates(certsPem);
            Log.d("NetBird-CA", "System and User certificates successfully passed to Go core.");
        } catch (Exception e) {
            Log.e("NetBird-CA", "Failed to extract or pass Android certificates to Go core", e);
        }

        // Set Theme at start
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(themeMode);
    }
}