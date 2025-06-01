package io.netbird.client.ui;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceUI {
    private static final String PREFS_NAME = "ui_prefs";
    private static final String FIRST_LAUNCH_KEY = "first_launch";
    private static final String ROUTE_NOTIFICATION = "ROUTE_NOTIFICATION";


    public static boolean isFirstLaunch(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(FIRST_LAUNCH_KEY, true);
    }

    public static void setFirstLaunchDone(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(FIRST_LAUNCH_KEY, false).apply();
    }

    public static boolean routeChangedNotification(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(ROUTE_NOTIFICATION, false);
    }
    public static void setRouteChangedNotification(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(ROUTE_NOTIFICATION, true).apply();
    }
    public static void routeChangedNotificationInvalidate(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(ROUTE_NOTIFICATION).apply();
    }
}
