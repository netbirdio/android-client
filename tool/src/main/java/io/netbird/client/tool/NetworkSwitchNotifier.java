package io.netbird.client.tool;

import android.util.Log;

import io.netbird.client.tool.networks.NetworkToggleListener;

/**
 * Forwards network type changes (e.g. cellular to WiFi) to the Go core, which
 * debounces them and sweeps the service connections still bound to the old
 * network. Connections that reconnected on their own survive the sweep, so no
 * cancellation is needed here.
 */
class NetworkSwitchNotifier implements NetworkToggleListener {
    private static final String LOGTAG = NetworkSwitchNotifier.class.getSimpleName();

    private final EngineRunner engineRunner;

    NetworkSwitchNotifier(EngineRunner engineRunner) {
        this.engineRunner = engineRunner;
    }

    @Override
    public void onNetworkTypeChanged() {
        if (!engineRunner.isRunning()) {
            Log.d(LOGTAG, "engine not running, skipping network change notification");
            return;
        }
        Log.d(LOGTAG, "network type changed, notifying Go core");
        engineRunner.notifyNetworkChange();
    }
}
