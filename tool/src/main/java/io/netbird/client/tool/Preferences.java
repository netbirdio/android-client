package io.netbird.client.tool;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.Set;

public class Preferences {

    private final String keyTraceLog = "tracelog";

    private final String keyForceRelayConnection = "isConnectionForceRelayed";

    private final String keySplitTunnelMode = "splitTunnelMode";

    private final String keySplitTunnelExcluded = "splitTunnelExcluded";

    private final String keySplitTunnelIncluded = "splitTunnelIncluded";

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

    public SplitTunnelConfig getSplitTunnelConfig() {
        return new SplitTunnelConfig(
                readMode(),
                sharedPref.getStringSet(keySplitTunnelExcluded, Collections.emptySet()),
                sharedPref.getStringSet(keySplitTunnelIncluded, Collections.emptySet()));
    }

    /**
     * Written in one commit so the mode and the selection it refers to can never
     * be read half-updated by the service while it rebuilds the tunnel.
     */
    public void saveSplitTunnelConfig(SplitTunnelConfig config) {
        sharedPref.edit()
                .putString(keySplitTunnelMode, config.getMode().name())
                .putStringSet(keySplitTunnelExcluded, copyForStorage(config.getExcluded()))
                .putStringSet(keySplitTunnelIncluded, copyForStorage(config.getIncluded()))
                .apply();
    }

    private SplitTunnelConfig.Mode readMode() {
        String stored = sharedPref.getString(keySplitTunnelMode, SplitTunnelConfig.Mode.OFF.name());
        try {
            return SplitTunnelConfig.Mode.valueOf(stored);
        } catch (IllegalArgumentException e) {
            // A mode written by a newer build, or a corrupted value: fall back to
            // carrying everything rather than dropping traffic on the floor.
            return SplitTunnelConfig.Mode.OFF;
        }
    }

    // SharedPreferences keeps a reference to the set it is handed and returns it
    // again on read, so it must not be one the caller can still mutate.
    private static Set<String> copyForStorage(Set<String> packages) {
        return new java.util.HashSet<>(packages);
    }

    public static String defaultServer() {
        return "https://api.netbird.io";
    }
}
