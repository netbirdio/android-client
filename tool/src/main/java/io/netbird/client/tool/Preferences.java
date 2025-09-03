package io.netbird.client.tool;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {

    private final String keyTraceLog = "tracelog";

    private final String keyForceRelayConnection = "isForceRelayConnectionEnabled";

    private final SharedPreferences sharedPref;

    public static String configFile(Context context){
       return context.getFilesDir().getPath() + "/netbird.cfg";
    }

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

    public boolean isRelayConnectionEnforced() {
        return sharedPref.getBoolean(keyForceRelayConnection, false);
    }

    public void enableRelayConnectionEnforcement() {
        sharedPref.edit().putBoolean(keyForceRelayConnection, true).apply();
    }

    public void disableRelayConnectionEnforcement() {
        sharedPref.edit().putBoolean(keyForceRelayConnection, false).apply();
    }

    public static String defaultServer() {
        return "https://api.netbird.io";
    }
}
