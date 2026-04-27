package io.netbird.client.tool;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {

    private final String keyTraceLog = "tracelog";

    private final String keyForceRelayConnection = "isConnectionForceRelayed";
    private final String keyLastExitNodeRoute = "lastExitNodeRoute";
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

    public String getLastExitNodeRoute() {
        return sharedPref.getString(keyLastExitNodeRoute, null);
    }

    public void setLastExitNodeRoute(String route) {
        if (route == null || route.trim().isEmpty()) {
            sharedPref.edit().remove(keyLastExitNodeRoute).apply();
            return;
        }
        sharedPref.edit().putString(keyLastExitNodeRoute, route).apply();
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

    public void setWidgetStateAndLastExitNodeRoute(String lastExitNodeRoute,
                                                   boolean vpnRunning,
                                                   boolean exitNodeActive,
                                                   String exitNodeName) {
        SharedPreferences.Editor editor = sharedPref.edit()
                .putBoolean(keyWidgetVpnRunning, vpnRunning)
                .putBoolean(keyWidgetExitNodeActive, exitNodeActive);

        if (lastExitNodeRoute == null || lastExitNodeRoute.trim().isEmpty()) {
            editor.remove(keyLastExitNodeRoute);
        } else {
            editor.putString(keyLastExitNodeRoute, lastExitNodeRoute);
        }

        if (exitNodeName == null || exitNodeName.trim().isEmpty()) {
            editor.remove(keyWidgetExitNodeName);
        } else {
            editor.putString(keyWidgetExitNodeName, exitNodeName);
        }

        editor.apply();
    }

    public static String defaultServer() {
        return "https://api.netbird.io";
    }
}
