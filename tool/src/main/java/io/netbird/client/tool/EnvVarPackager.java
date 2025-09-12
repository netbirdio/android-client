package io.netbird.client.tool;

import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.EnvList;

public class EnvVarPackager {
    public static EnvList getEnvironmentVariables(Preferences preferences) {
        var envList = new EnvList();

        envList.put(Android.getEnvKeyNBForceRelay(), String.valueOf(preferences.isConnectionForceRelayed()));
        envList.put("NB_ICE_DISCONNECTED_TIMEOUT_SEC", "25");
        envList.put("NB_ICE_FAILED_TIMEOUT_SEC", "25");
        envList.put("NB_ICE_FORCE_RELAY_CONN", "true");
        // envList.put("NB_LOG_LEVEL", "trace");

        return envList;
    }
}
