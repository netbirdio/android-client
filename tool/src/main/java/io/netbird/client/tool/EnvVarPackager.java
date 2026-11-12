package io.netbird.client.tool;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.EnvList;

public class EnvVarPackager {
    private static final String LOGTAG = "EnvVarPackager";

    // TEST BUILD (stage battery test, do not merge): the T = 60 s heartbeat knobs and
    // the WS relay transport apply only when the profile points at the stage
    // Management server. Any other server gets the stock values.
    static final String BAKED_MANAGEMENT_HOST = "api.stage.netbird.io";
    static final String BAKED_RELAY_TRANSPORT = "ws";
    // Map.of needs API 30, minSdk is 26.
    static final Map<String, String> BAKED_TEST_QUIET = new HashMap<>();

    static {
        BAKED_TEST_QUIET.put("NB_TEST_GRPC_KEEPALIVE_SEC", "70");
        BAKED_TEST_QUIET.put("NB_TEST_HB_DYNAMIC", "1");
        BAKED_TEST_QUIET.put("NB_TEST_HB_STALE", "1");
        BAKED_TEST_QUIET.put("NB_TEST_HB_PERIOD_SEC", "60");
        BAKED_TEST_QUIET.put("NB_TEST_RELAY_WG_KA_OFF", "1");
        BAKED_TEST_QUIET.put("NB_TEST_SIGNAL_WATCHDOG_SEC", "75");
    }

    // Every test key is always put: os.Setenv persists in the process across engine runs.
    static final String[] TEST_QUIET_KEYS = {
            "NB_TEST_ABLATE",
            "NB_TEST_GRPC_KEEPALIVE_SEC",
            "NB_TEST_HB_CLIENT_PHASE_MS",
            "NB_TEST_HB_DYNAMIC",
            "NB_TEST_HB_PERIOD_SEC",
            "NB_TEST_HB_STALE",
            "NB_TEST_RELAY_WG_KA_OFF",
            "NB_TEST_SIGNAL_WATCHDOG_SEC",
            "NB_TEST_TCP_KEEPALIVE_SEC",
    };

    public static EnvList getEnvironmentVariables(Preferences preferences) {
        return getEnvironmentVariables(preferences, false);
    }

    public static EnvList getEnvironmentVariables(Preferences preferences, Context context, String configPath) {
        boolean baked = isBakedServer(context, configPath);
        Log.i(LOGTAG, "TEST BUILD: baked knobs " + (baked ? "on" : "off"));
        return getEnvironmentVariables(preferences, baked);
    }

    static EnvList getEnvironmentVariables(Preferences preferences, boolean baked) {
        var envList = new EnvList();

        envList.put(Android.getEnvKeyNBForceRelay(), String.valueOf(preferences.isConnectionForceRelayed()));

        String relayTransport = baked ? BAKED_RELAY_TRANSPORT : "auto";
        envList.put("NB_RELAY_TRANSPORT", relayTransport);
        Log.i(LOGTAG, "LOCAL TEST: NB_RELAY_TRANSPORT=" + relayTransport);

        Map<String, String> quiet = new TreeMap<>();
        for (String key : TEST_QUIET_KEYS) {
            String value = baked ? BAKED_TEST_QUIET.get(key) : null;
            quiet.put(key, value != null ? value : "");
        }
        StringBuilder line = new StringBuilder("LOCAL TEST: quiet");
        for (Map.Entry<String, String> e : quiet.entrySet()) {
            envList.put(e.getKey(), e.getValue());
            line.append(' ').append(e.getKey()).append('=').append(e.getValue());
        }
        Log.i(LOGTAG, line.toString());

        return envList;
    }

    // True when the profile config points at the stage Management server. An
    // unreadable config counts as not stage.
    static boolean isBakedServer(Context context, String configPath) {
        if (configPath == null) {
            return false;
        }
        try {
            String url = MDMBridge.openPreferences(context, configPath).getManagementURL();
            String host = Uri.parse(url).getHost();
            return host != null && host.equalsIgnoreCase(BAKED_MANAGEMENT_HOST);
        } catch (Exception e) {
            Log.w(LOGTAG, "TEST BUILD: could not read the Management URL", e);
            return false;
        }
    }
}
