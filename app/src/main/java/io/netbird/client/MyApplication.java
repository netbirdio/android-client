package io.netbird.client;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import io.netbird.client.tool.Preferences;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        registerWidgetCrashCleanup();
        // Set Theme at start
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(themeMode);
    }

    private void registerWidgetCrashCleanup() {
        Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                new Preferences(this).clearWidgetState();
                NetbirdWidgetUpdater.updateAllWidgets(this);
            } catch (Exception ignored) {
                // Keep the original crash handling path intact.
            }

            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable);
                return;
            }

            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        });
    }
}
