package io.netbird.client.tool;

import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.EnvList;

public class EnvVarPackager {
    public static EnvList getEnvironmentVariables(Preferences preferences) {
        var envList = new EnvList();

        envList.put(Android.getEnvKeyNBForceRelay(), String.valueOf(preferences.isConnectionForceRelayed()));
        envList.put(Android.getEnvKeyNBLazyConn(), String.valueOf(preferences.isLazyConnectionEnabled()));
        envList.put(Android.getEnvKeyNBInactivityThreshold(), String.valueOf(preferences.getInactivityThreshold()));

        return envList;
    }
}
