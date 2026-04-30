package io.netbird.client.tool;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {

    private final String keyTraceLog = "tracelog";

    private final String keyForceRelayConnection = "isConnectionForceRelayed";
    private final String keyWidgetVpnRunning = "widgetVpnRunning";
    private final String keyWidgetExitNodeActive = "widgetExitNodeActive";
    private final String keyWidgetExitNodeName = "widgetExitNodeName";

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

    public boolean isWidgetVpnRunning() {
        return sharedPref.getBoolean(keyWidgetVpnRunning, false);
    }

    public boolean isWidgetExitNodeActive() {
        return sharedPref.getBoolean(keyWidgetExitNodeActive, false);
    }

    public String getWidgetExitNodeName() {
        return sharedPref.getString(keyWidgetExitNodeName, null);
    }

    public void setWidgetState(boolean vpnRunning, boolean exitNodeActive, String exitNodeName) {
        SharedPreferences.Editor editor = sharedPref.edit()
                .putBoolean(keyWidgetVpnRunning, vpnRunning)
                .putBoolean(keyWidgetExitNodeActive, exitNodeActive);

        if (exitNodeName == null || exitNodeName.trim().isEmpty()) {
            editor.remove(keyWidgetExitNodeName);
        } else {
            editor.putString(keyWidgetExitNodeName, exitNodeName);
        }

        editor.apply();
    }

    public void clearWidgetState() {
        sharedPref.edit()
                .putBoolean(keyWidgetVpnRunning, false)
                .putBoolean(keyWidgetExitNodeActive, false)
                .remove(keyWidgetExitNodeName)
                .commit();
    }

    public static String defaultServer() {
        return "https://api.netbird.io";
    }
}
