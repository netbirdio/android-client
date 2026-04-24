package io.netbird.client.tool;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import io.netbird.gomobile.android.ManagedConfig;

/**
 * Receives {@code Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED} broadcasts
 * when the MDM/EMM pushes updated managed configuration to the device.
 *
 * <p>This receiver re-reads the managed configuration and applies it to the
 * Go SDK config file. If the VPN engine is running and the management URL changed,
 * the engine should be restarted (handled by the EngineRunner via its existing
 * restart mechanism).</p>
 *
 * <p>Register this receiver in AndroidManifest.xml or dynamically in the VPNService.</p>
 */
public class ManagedConfigReceiver extends BroadcastReceiver {

    private static final String TAG = "ManagedConfigReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED.equals(intent.getAction())) {
            return;
        }

        Log.i(TAG, "Application restrictions changed, re-reading MDM config");

        ManagedConfig config = ManagedConfigReader.read(context);
        if (config == null || !config.hasConfig()) {
            Log.d(TAG, "No MDM config after restrictions change");
            return;
        }

        try {
            ProfileManagerWrapper profileManager = new ProfileManagerWrapper(context);
            String configPath = profileManager.getActiveConfigPath();
            config.apply(configPath);
            Log.i(TAG, "MDM config re-applied after restrictions change");
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply MDM config after restrictions change", e);
        }
    }
}
