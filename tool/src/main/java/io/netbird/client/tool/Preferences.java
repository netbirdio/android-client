package io.netbird.client.tool;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {

    private final String keyTraceLog = "tracelog";

    private final String keyForceRelayConnection = "isConnectionForceRelayed";
    private final String keyLazyConnectionEnabled = "isLazyConnectionEnabled";
    private final String keyInactivityThreshold = "inactivityThreshold";

    private final SharedPreferences sharedPref;

    public Preferences(Context context) {
       sharedPref = context.getSharedPreferences("netbird", Context.MODE_PRIVATE);
    }

    public boolean isTraceLogEnabled() {
       return sharedPref.getBoolean(keyTraceLog, false);
    }
    public void enableTraceLog() {
        sharedPref.edit().putBoolean(keyTraceLog, true).apply();
    }

    public void disableTraceLog() {
        sharedPref.edit().putBoolean(keyTraceLog, false).apply();
    }

    public boolean isConnectionForceRelayed() {
        return sharedPref.getBoolean(keyForceRelayConnection, true);
    }

    public void enableForcedRelayConnection() {
        sharedPref.edit().putBoolean(keyForceRelayConnection, true).apply();
    }

    public void disableForcedRelayConnection() {
        sharedPref.edit().putBoolean(keyForceRelayConnection, false).apply();
    }

    public boolean isLazyConnectionEnabled() {
        return sharedPref.getBoolean(keyLazyConnectionEnabled, true);
    }

    public void enableLazyConnection() {
        sharedPref.edit().putBoolean(keyLazyConnectionEnabled, true).apply();
    }

    public void disableLazyConnection() {
        sharedPref.edit().putBoolean(keyLazyConnectionEnabled, false).apply();
    }

    // This value represents for how long, in minutes, it will take for a lazy connection to be considered inactive so
    // it won't attempt reconnection anymore. Currently, the user cannot change this so it's hardcoded to five minutes
    // (its default value in the SDK is 15 minutes, and accepts a minimum of 1 minute).
    public int getInactivityThreshold() {
        return sharedPref.getInt(keyInactivityThreshold, 5);
    }

    public static String defaultServer() {
        return "https://api.netbird.io";
    }
}
