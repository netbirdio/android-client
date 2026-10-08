package io.netbird.client.tool.autoconnect;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * A manually-entered Wi-Fi network the user trusts enough not to need the
 * VPN on (e.g. home or office Wi-Fi) — auto-connect treats it as exempt
 * rather than as something to connect on. At least one of ssid/bssid is
 * always non-null. When bssid is present it is the sole match key (a
 * specific access point); otherwise ssid is matched, which allows any access
 * point broadcasting that network name.
 */
public final class TrustedNetwork {
    private static final String KEY_SSID = "ssid";
    private static final String KEY_BSSID = "bssid";

    @Nullable
    public final String ssid;
    @Nullable
    public final String bssid;

    public TrustedNetwork(@Nullable String ssid, @Nullable String bssid) {
        this.ssid = normalize(ssid);
        this.bssid = bssid == null ? null : bssid.trim().toUpperCase();
    }

    @Nullable
    private static String normalize(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public boolean matches(@Nullable String currentSsid, @Nullable String currentBssid) {
        if (bssid != null) {
            return bssid.equalsIgnoreCase(currentBssid);
        }
        return ssid != null && ssid.equals(currentSsid);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        // Omit null fields rather than writing JSONObject.NULL, so
        // optString(key, null) round-trips them cleanly on read.
        if (ssid != null) json.put(KEY_SSID, ssid);
        if (bssid != null) json.put(KEY_BSSID, bssid);
        return json;
    }

    public static TrustedNetwork fromJson(JSONObject json) {
        return new TrustedNetwork(json.optString(KEY_SSID, null), json.optString(KEY_BSSID, null));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrustedNetwork)) return false;
        TrustedNetwork other = (TrustedNetwork) o;
        return Objects.equals(ssid, other.ssid) && Objects.equals(bssid, other.bssid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ssid, bssid);
    }
}
