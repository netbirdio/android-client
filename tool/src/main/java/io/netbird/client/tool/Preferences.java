package io.netbird.client.tool;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class Preferences {

    private final String keyTraceLog = "tracelog";

    private final String keyForceRelayConnection = "isConnectionForceRelayed";

    private final String keySplitTunnelingMode = "splitTunnelingMode";

    private final String keySplitTunnelingApps = "splitTunnelingApps";

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

    public static String defaultServer() {
        return "https://api.netbird.io";
    }

    public enum SplitTunnelingMode {
        NONE,
        EXCLUDE,
        INCLUDE
    }

    public SplitTunnelingMode getSplitTunnelingMode() {
        String mode = sharedPref.getString(keySplitTunnelingMode, SplitTunnelingMode.NONE.name());
        try {
            return SplitTunnelingMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            return SplitTunnelingMode.NONE;
        }
    }

    public void setSplitTunnelingMode(SplitTunnelingMode mode) {
        sharedPref.edit().putString(keySplitTunnelingMode, mode.name()).apply();
    }

    public Set<String> getSplitTunnelingApps() {
        return sharedPref.getStringSet(keySplitTunnelingApps, new HashSet<>());
    }

    public void setSplitTunnelingApps(Set<String> apps) {
        sharedPref.edit().putStringSet(keySplitTunnelingApps, apps).apply();
    }
}
