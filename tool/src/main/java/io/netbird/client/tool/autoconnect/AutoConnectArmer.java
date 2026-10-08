package io.netbird.client.tool.autoconnect;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import io.netbird.client.tool.VPNService;

/**
 * Starts or stops VPNService's background monitoring mode to match the
 * current trigger state. Called after every trigger toggle and from {@link
 * BootReceiver}, so the monitoring service is always armed exactly when at
 * least one auto-connect trigger is enabled.
 */
public final class AutoConnectArmer {
    private static final String LOGTAG = "AutoConnect";

    private AutoConnectArmer() {
    }

    public static void sync(Context context) {
        AutoConnectPreferences preferences = new AutoConnectPreferences(context);
        boolean armed = preferences.isAnyTriggerArmed();
        Log.d(LOGTAG, "AutoConnectArmer.sync(): wifi=" + preferences.isWifiTriggerEnabled()
                + " mobile=" + preferences.isMobileTriggerEnabled()
                + " ethernet=" + preferences.isEthernetTriggerEnabled()
                + " noNetwork=" + preferences.isDisconnectOnNoNetworkEnabled()
                + " -> armed=" + armed);
        Intent intent = new Intent(context, VPNService.class);
        if (armed) {
            intent.setAction(VPNService.ACTION_START_MONITORING);
            ContextCompat.startForegroundService(context, intent);
        } else {
            intent.setAction(VPNService.ACTION_STOP_MONITORING);
            context.startService(intent);
        }
    }
}
