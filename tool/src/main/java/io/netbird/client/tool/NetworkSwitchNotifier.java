package io.netbird.client.tool;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import io.netbird.client.tool.networks.NetworkToggleListener;

/**
 * <p>NetworkSwitchNotifier reacts to network type changes (e.g. cellular to
 * WiFi).</p>
 * <p>After a debounce it tells the Go core to cut its service connections,
 * whose sockets are bound to the old network, so their reconnect loops redial
 * immediately on the new one. The engine, the TUN device, the WireGuard config
 * and the peer state stay untouched. It replaces the former EngineRestarter,
 * which handled the same trigger with a full engine restart.</p>
 */
class NetworkSwitchNotifier implements NetworkToggleListener {
    private static final String LOGTAG = NetworkSwitchNotifier.class.getSimpleName();

    // Debounce for network change bursts (e.g. cellular and WiFi flapping
    // while Android settles on a default network). Cutting connections is
    // cheap and idempotent — unlike the historical full restart — so 1s is
    // enough headroom.
    private static final long DEBOUNCE_DELAY_MS = 1000;

    private final EngineRunner engineRunner;
    private final Handler handler;
    private final Runnable notifyRunnable;

    private boolean notifyScheduled = false;
    private final Object lock = new Object();
    private final Runnable connectedObserver = this::onEngineReconnected;

    NetworkSwitchNotifier(EngineRunner engineRunner) {
        this.engineRunner = engineRunner;
        this.handler = new Handler(Looper.getMainLooper());
        this.notifyRunnable = this::notifyNetworkChange;
        engineRunner.addOnConnectedObserver(connectedObserver);
    }

    @Override
    public void onNetworkTypeChanged() {
        Log.d(LOGTAG, "network type changed, scheduling network change action with "
                + DEBOUNCE_DELAY_MS + "ms debounce.");

        synchronized (lock) {
            notifyScheduled = true;
            handler.removeCallbacks(notifyRunnable);
            handler.postDelayed(notifyRunnable, DEBOUNCE_DELAY_MS);
        }
    }

    /**
     * Cancels any pending debounced action. Called whenever an external actor
     * (typically a user-driven Connect/Disconnect) takes over the engine
     * lifecycle, so the network-change-driven cut does not interfere with
     * that explicit action.
     */
    public void cancelPendingAction() {
        synchronized (lock) {
            notifyScheduled = false;
            handler.removeCallbacks(notifyRunnable);
        }
    }

    /**
     * Releases every resource held by this notifier: the pending debounced
     * action and the reconnect observer. Called when the service shuts down.
     */
    public void cleanup() {
        cancelPendingAction();
        engineRunner.removeOnConnectedObserver(connectedObserver);
    }

    private void onEngineReconnected() {
        // The Go core reconnected on its own, so its connections already live
        // on the current network; cancel the debounced action so we do not
        // cut a working connection.
        synchronized (lock) {
            if (notifyScheduled) {
                Log.d(LOGTAG, "engine reconnected on its own, cancelling pending network change action");
                notifyScheduled = false;
                handler.removeCallbacks(notifyRunnable);
            }
        }
    }

    private void notifyNetworkChange() {
        synchronized (lock) {
            notifyScheduled = false;
        }

        if (!engineRunner.isRunning()) {
            Log.d(LOGTAG, "engine not running, skipping network change notification");
            return;
        }

        Log.d(LOGTAG, "network changed, cutting Go service connections");
        engineRunner.notifyNetworkChange();
    }
}
