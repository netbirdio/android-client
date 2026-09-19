package io.netbird.client.tool.autoconnect;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-connect trigger state and the manually-entered trusted-network list.
 * These are pure Android/OS-level settings (network transport type, Wi-Fi
 * SSID/BSSID) with no equivalent in the Go engine's own config, so — unlike
 * Rosenpass/DNS/routes, which persist through the gomobile Preferences
 * binding into the engine's config.json — they live in a dedicated
 * SharedPreferences file, following the same pattern as {@link
 * io.netbird.client.tool.Preferences}.
 */
public class AutoConnectPreferences {
    private static final String LOGTAG = "AutoConnectPreferences";
    private static final String PREFS = "netbird_auto_connect";

    private static final String KEY_WIFI = "wifiEnabled";
    private static final String KEY_MOBILE = "mobileEnabled";
    private static final String KEY_ETHERNET = "ethernetEnabled";
    private static final String KEY_NO_NETWORK = "disconnectOnNoNetworkEnabled";
    private static final String KEY_TRUSTED_NETWORKS = "trustedNetworksJson";

    private final SharedPreferences sharedPref;

    public AutoConnectPreferences(Context context) {
        sharedPref = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isWifiTriggerEnabled() {
        return sharedPref.getBoolean(KEY_WIFI, false);
    }

    public void setWifiTriggerEnabled(boolean enabled) {
        sharedPref.edit().putBoolean(KEY_WIFI, enabled).apply();
    }

    public boolean isMobileTriggerEnabled() {
        return sharedPref.getBoolean(KEY_MOBILE, false);
    }

    public void setMobileTriggerEnabled(boolean enabled) {
        sharedPref.edit().putBoolean(KEY_MOBILE, enabled).apply();
    }

    public boolean isEthernetTriggerEnabled() {
        return sharedPref.getBoolean(KEY_ETHERNET, false);
    }

    public void setEthernetTriggerEnabled(boolean enabled) {
        sharedPref.edit().putBoolean(KEY_ETHERNET, enabled).apply();
    }

    public boolean isDisconnectOnNoNetworkEnabled() {
        return sharedPref.getBoolean(KEY_NO_NETWORK, false);
    }

    public void setDisconnectOnNoNetworkEnabled(boolean enabled) {
        sharedPref.edit().putBoolean(KEY_NO_NETWORK, enabled).apply();
    }

    public boolean isAnyTriggerArmed() {
        return isWifiTriggerEnabled() || isMobileTriggerEnabled()
                || isEthernetTriggerEnabled() || isDisconnectOnNoNetworkEnabled();
    }

    public List<TrustedNetwork> getTrustedNetworks() {
        List<TrustedNetwork> result = new ArrayList<>();
        String raw = sharedPref.getString(KEY_TRUSTED_NETWORKS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                result.add(TrustedNetwork.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.e(LOGTAG, "failed to parse trusted networks", e);
        }
        return result;
    }

    public void addTrustedNetwork(TrustedNetwork network) {
        List<TrustedNetwork> networks = getTrustedNetworks();
        if (networks.contains(network)) {
            return;
        }
        networks.add(network);
        saveTrustedNetworks(networks);
    }

    public void removeTrustedNetwork(TrustedNetwork network) {
        List<TrustedNetwork> networks = getTrustedNetworks();
        if (networks.remove(network)) {
            saveTrustedNetworks(networks);
        }
    }

    private void saveTrustedNetworks(List<TrustedNetwork> networks) {
        JSONArray array = new JSONArray();
        try {
            for (TrustedNetwork network : networks) {
                array.put(network.toJson());
            }
        } catch (JSONException e) {
            Log.e(LOGTAG, "failed to serialize trusted networks", e);
            return;
        }
        sharedPref.edit().putString(KEY_TRUSTED_NETWORKS, array.toString()).apply();
    }
}
