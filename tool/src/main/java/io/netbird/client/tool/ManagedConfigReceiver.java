package io.netbird.client.tool;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.netbird.gomobile.android.ManagedConfig;

/**
 * Receives {@code Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED} broadcasts
 * when the MDM/EMM pushes updated managed configuration to the device.
 *
 * <p>This receiver re-reads the managed configuration and applies it to the
 * Go SDK config file. Work is performed off the main thread via {@code goAsync()}
 * to avoid ANRs.</p>
 *
 * <p>Register this receiver in AndroidManifest.xml or dynamically in the VPNService.</p>
 */
public class ManagedConfigReceiver extends BroadcastReceiver {

    private static final String TAG = "ManagedConfigReceiver";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED.equals(intent.getAction())) {
            return;
        }

        Log.i(TAG, "Application restrictions changed, re-reading MDM config");

        final PendingResult pendingResult = goAsync();
        EXECUTOR.execute(() -> {
            try {
                synchronized (EngineRunner.MDM_CONFIG_LOCK) {
                    ManagedConfig config = ManagedConfigReader.read(context);
                    if (config == null || !config.hasConfig()) {
                        Log.d(TAG, "No MDM config after restrictions change");
                        return;
                    }

                    ProfileManagerWrapper profileManager = new ProfileManagerWrapper(context);
                    String configPath = profileManager.getActiveConfigPath();
                    config.apply(configPath);
                    Log.i(TAG, "MDM config re-applied after restrictions change");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply MDM config after restrictions change", e);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
