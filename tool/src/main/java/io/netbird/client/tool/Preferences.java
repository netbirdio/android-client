package io.netbird.client.tool;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {

    public static final String ANONYMIZE_LEVEL_NONE = "none";

    public static final String ANONYMIZE_LEVEL_DEFAULT = "default";

    public static final String ANONYMIZE_LEVEL_STRICT = "strict";

    private final String keyTraceLog = "tracelog";

    private final String keyForceRelayConnection = "isConnectionForceRelayed";

    private final String keyAnonymizeLevel = "anonymizeLevel";

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

    public String getAnonymizeLevel() {
        return sharedPref.getString(keyAnonymizeLevel, ANONYMIZE_LEVEL_DEFAULT);
    }

    public void setAnonymizeLevel(String level) {
        sharedPref.edit().putString(keyAnonymizeLevel, level).apply();
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
}
