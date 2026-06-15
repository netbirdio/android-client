package io.netbird.client;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import io.netbird.gomobile.android.Android;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Set Theme at start
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(themeMode);

        // Register the MDM policy fetcher exactly once for the process
        // lifetime. The Go side invokes fetchJSON() on every LoadPolicy
        // call so the returned snapshot is always fresh — no caching here.
        Android.setMobilePolicyFetcher(new MDMPolicyFetcher(this));
    }
}