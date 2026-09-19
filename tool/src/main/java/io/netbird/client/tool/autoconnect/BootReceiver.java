package io.netbird.client.tool.autoconnect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.util.Log;

import androidx.core.content.ContextCompat;

import io.netbird.client.tool.VPNService;

/**
 * Re-arms auto-connect monitoring after a reboot or an app update, so a
 * trigger configured before the device restarted keeps working without the
 * user having to reopen the app. Apps actively handling BOOT_COMPLETED are
 * exempt from the background foreground-service-start restrictions that
 * would otherwise block this.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String LOGTAG = "AutoConnectBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        AutoConnectPreferences preferences = new AutoConnectPreferences(context);
        if (!preferences.isAnyTriggerArmed()) {
            return;
        }

        if (VpnService.prepare(context) != null) {
            // Consent missing/revoked — cannot be granted from the background.
            AutoConnectConsentNotification.show(context);
            return;
        }

        Log.d(LOGTAG, "re-arming auto-connect monitoring after " + action);
        Intent serviceIntent = new Intent(context, VPNService.class)
                .setAction(VPNService.ACTION_START_MONITORING);
        ContextCompat.startForegroundService(context, serviceIntent);
    }
}
