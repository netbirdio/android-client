package io.netbird.client.tool.autoconnect;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Reads the currently-connected Wi-Fi network's SSID/BSSID. Reading either
 * reliably requires ACCESS_FINE_LOCATION (granted at runtime) with location
 * services enabled; without it Android returns masked placeholder values
 * ("&lt;unknown ssid&gt;" / "02:00:00:00:00:00") rather than throwing, which this
 * class treats as "unavailable" (null) rather than a real identity.
 */
public class WifiInfoProvider {
    private static final String MASKED_BSSID = "02:00:00:00:00:00";

    private final Context context;

    public WifiInfoProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * True once location access is granted reliably enough for evaluation
     * running from the background (VPNService, not the foreground app) to
     * actually get real SSID/BSSID values. Before Android 10 there is no
     * foreground/background distinction, so a plain grant is enough; from
     * Android 10 on, "while using the app" access is revoked exactly when
     * auto-connect needs to read it (the app isn't in the foreground), so
     * ACCESS_BACKGROUND_LOCATION — a separate, later runtime grant — is
     * required too.
     */
    public static boolean hasBackgroundCapablePermission(Context context) {
        if (!hasPermission(context)) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Nullable
    public WifiSnapshot currentWifi() {
        if (!hasPermission(context)) {
            return null;
        }
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return null;
        }
        WifiInfo info = wifiManager.getConnectionInfo();
        if (info == null) {
            return null;
        }
        String ssid = stripQuotes(info.getSSID());
        if (ssid == null || ssid.equals(WifiManager.UNKNOWN_SSID)) {
            ssid = null;
        }
        String bssid = info.getBSSID();
        if (bssid == null || bssid.equalsIgnoreCase(MASKED_BSSID)) {
            bssid = null;
        }
        if (ssid == null && bssid == null) {
            return null;
        }
        return new WifiSnapshot(ssid, bssid);
    }

    @Nullable
    private static String stripQuotes(@Nullable String value) {
        if (value == null) return null;
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    public static final class WifiSnapshot {
        @Nullable
        public final String ssid;
        @Nullable
        public final String bssid;

        WifiSnapshot(@Nullable String ssid, @Nullable String bssid) {
            this.ssid = ssid;
            this.bssid = bssid;
        }
    }
}
