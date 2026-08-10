package io.netbird.client.tool;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.netbird.client.tool.networks.NetworkToggleListener;
import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.ConnectionListener;

/**
 * <p>EngineRestarter restarts the Go engine.</p>
 * <p>It implements {@link NetworkToggleListener} to restart the engine when the available network type changes.</p>
 */
class EngineRestarter implements NetworkToggleListener {
    private static final String LOGTAG = EngineRestarter.class.getSimpleName();
    private static final long DEBOUNCE_DELAY_MS = 2000; // 2 seconds delay
    private static final long RESTART_TIMEOUT_MS = 30000; // 30 seconds
    private final EngineRunner engineRunner;
    private final Handler handler;
    private final Runnable restartRunnable;
    private Runnable timeoutCallback;
    private ServiceStateListener currentListener;

    private volatile boolean isRestartInProgress = false;
    private boolean restartScheduled = false;
    private final Object restartLock = new Object();
    private final AtomicReference<List<ServiceStateListener>> suppressedHolder = new AtomicReference<>();
    private final Runnable connectedObserver = this::onEngineReconnected;

    public EngineRestarter(EngineRunner engineRunner) {
        this.engineRunner = engineRunner;
        this.handler = new Handler(Looper.getMainLooper());
        this.restartRunnable = this::restartEngine;
        this.engineRunner.addOnConnectedObserver(connectedObserver);
    }

    private void onEngineReconnected() {
        // The Go core reconnected on its own; the pending restart is no
        // longer needed. Cancel the debounced restart so we do not tear
        // down a working connection.
        synchronized (restartLock) {
            if (restartScheduled) {
                Log.d(LOGTAG, "engine reconnected on its own, cancelling pending restart");
                restartScheduled = false;
                handler.removeCallbacks(restartRunnable);
            }
        }
    }

    /**
     * <p>Restarts the Go engine currently running.</p>
     * <p>It registers an anonymous implementation of {@link ServiceStateListener}
     * in order to know for sure when the engine actually stops in order to restart it.</p>
     * <p>If the engine isn't running, this method does nothing.</p>
     */
    private void restartEngine() {
        synchronized (restartLock) {
            restartScheduled = false;
        }

        // Prevent concurrent restarts
        if (isRestartInProgress) {
            Log.d(LOGTAG, "restart already in progress, ignoring duplicate request");
            return;
        }

        if (!engineRunner.isRunning()) {
            Log.d(LOGTAG, "engine not running, skipping restart");
            return;
        }

        isRestartInProgress = true;

        // Snapshot the current listener and wrap it so disconnect events from
        // the old engine teardown — and the synthetic Disconnected the new
        // engine emits before its first ClientStart() — do not reach the UI.
        // Unwrap any leftover FilteringConnectionListener from a previous
        // restart so wrappers do not stack on each cycle.
        ConnectionListener savedListener = unwrapFilter(engineRunner.getConnectionListener());
        FilteringConnectionListener filteringListener =
                savedListener != null ? new FilteringConnectionListener(savedListener) : null;
        if (filteringListener != null) {
            engineRunner.setConnectionListener(filteringListener);
        }

        timeoutCallback = () -> {
            if (isRestartInProgress) {
                Log.e(LOGTAG, "engine restart timeout - forcing flag reset");
                isRestartInProgress = false;
                if (filteringListener != null) {
                    filteringListener.allowAll();
                }
                unsuppressAll(suppressedHolder.getAndSet(null));
                // Unregister so a late onStopped can no longer trigger
                // runWithoutAuth against this stale listener.
                if (currentListener != null) {
                    engineRunner.removeServiceStateListener(currentListener);
                    currentListener = null;
                }
                notifyDisconnected(savedListener);
            }
        };

        // Safety timeout in case restart never completes
        handler.postDelayed(timeoutCallback, RESTART_TIMEOUT_MS);

        Log.d(LOGTAG, "initiating engine restart due to network change");

        var serviceStateListener = new ServiceStateListener() {
            @Override
            public void onStarted() {
                Log.d(LOGTAG, "engine restarted successfully");
                isRestartInProgress = false;  // Reset flag on success
                handler.removeCallbacks(timeoutCallback);  // Cancel timeout
                engineRunner.removeServiceStateListener(this);
                currentListener = null;
                // Deliberately leave the filtering wrapper installed: onStarted
                // fires before goClient.run(), so the new engine's replayed
                // Disconnected is still to come and swapping the wrapper out
                // here would let it through. The wrapper disarms itself on the
                // new engine's first non-disconnect state, and the next restart
                // unwraps it, so it neither suppresses anything afterwards nor
                // accumulates.
                unsuppressAll(suppressedHolder.getAndSet(null));
            }

            @Override
            public void onStopped() {
                Log.d(LOGTAG, "engine is stopped, restarting...");
                // The old engine's run() has returned, so every callback from
                // here on belongs to the new one. Only now may the filter treat
                // a non-disconnect state as proof that the restart is over.
                if (filteringListener != null) {
                    filteringListener.expectNewEngine();
                }
                engineRunner.runWithoutAuth();
            }

            @Override
            public void onError(String msg) {
                Log.e(LOGTAG, "restart failed: " + msg);
                isRestartInProgress = false; // Resetting flag on error as well
                handler.removeCallbacks(timeoutCallback);  // Cancel timeout
                engineRunner.removeServiceStateListener(this);
                currentListener = null;
                if (filteringListener != null) {
                    filteringListener.allowAll();
                }
                unsuppressAll(suppressedHolder.getAndSet(null));
                notifyDisconnected(savedListener);
            }
        };
        currentListener = serviceStateListener;

        // Atomically check and register to avoid race condition
        if (!engineRunner.addServiceStateListenerForRestart(serviceStateListener)) {
            Log.d(LOGTAG, "engine stopped before restart could begin - aborting");
            handler.removeCallbacks(timeoutCallback);
            isRestartInProgress = false;
            if (filteringListener != null) {
                engineRunner.setConnectionListener(savedListener);
            }
            return;
        }

        // Suppress external service-state listeners so the old engine's
        // onStopped (and the new engine's onStarted) do not reach the UI;
        // we drive UI state through ConnectionListener exclusively during
        // the restart window.
        List<ServiceStateListener> suppressed =
                engineRunner.snapshotExternalListeners(serviceStateListener);
        for (ServiceStateListener s : suppressed) {
            engineRunner.suppressServiceStateListener(s);
        }
        suppressedHolder.set(suppressed);

        Log.d(LOGTAG, "engine is running, stopping due to network change");
        notifyConnecting(savedListener);
        engineRunner.stop();
    }

    private void unsuppressAll(List<ServiceStateListener> suppressed) {
        if (suppressed == null) return;
        for (ServiceStateListener s : suppressed) {
            engineRunner.unsuppressServiceStateListener(s);
        }
    }

    private static ConnectionListener unwrapFilter(ConnectionListener listener) {
        ConnectionListener current = listener;
        while (current instanceof FilteringConnectionListener) {
            current = ((FilteringConnectionListener) current).delegate;
        }
        return current;
    }

    private void notifyConnecting(ConnectionListener listener) {
        if (listener == null) {
            return;
        }
        try {
            listener.onConnecting();
        } catch (Exception e) {
            Log.w(LOGTAG, "onConnecting notification failed: " + e.getMessage());
        }
    }

    private void notifyDisconnected(ConnectionListener listener) {
        if (listener == null) {
            return;
        }
        try {
            listener.onDisconnected();
        } catch (Exception e) {
            Log.w(LOGTAG, "onDisconnected notification failed: " + e.getMessage());
        }
    }

    /**
     * Wraps a ConnectionListener and drops Disconnecting/Disconnected events
     * during a restart. Disconnects from the old engine's teardown — and the
     * default-state replay the Go notifier sends to a listener attached
     * before the new engine's ClientStart() — would otherwise flash the UI
     * to Disconnected.
     *
     * <p>The filter disarms itself on the first non-disconnect state reported
     * <em>after</em> expectNewEngine, which the restart calls from onStopped.
     * Neither half of that condition is sufficient alone: onStarted fires
     * before goClient.run(), so releasing on it would still let the new
     * engine's replayed Disconnected through, while state alone cannot tell the
     * new engine's Connecting from the one the old engine emits as it is torn
     * down. The restart timeout is the backstop if no state ever arrives.</p>
     */
    private static final class FilteringConnectionListener implements ConnectionListener {
        final ConnectionListener delegate;
        private volatile boolean dropDisconnects = true;
        private volatile boolean newEngineExpected = false;

        FilteringConnectionListener(ConnectionListener delegate) {
            this.delegate = delegate;
        }

        void allowAll() {
            dropDisconnects = false;
        }

        /**
         * Marks the old engine as fully stopped. Until this is called a
         * non-disconnect state proves nothing, because the old engine emits
         * Connecting on its way out: tearing it down drops its management and
         * signal links, and the Go notifier recomputes the state from those.
         */
        void expectNewEngine() {
            newEngineExpected = true;
        }

        private void release(String trigger) {
            if (!newEngineExpected || !dropDisconnects) {
                return;
            }
            dropDisconnects = false;
            Log.d(LOGTAG, "restart filter released by " + trigger);
        }

        @Override
        public void onConnecting() {
            release("onConnecting");
            try {
                delegate.onConnecting();
            } catch (Exception e) {
                Log.w(LOGTAG, "delegate onConnecting failed: " + e.getMessage());
            }
        }

        @Override
        public void onConnected() {
            release("onConnected");
            try {
                delegate.onConnected();
            } catch (Exception e) {
                Log.w(LOGTAG, "delegate onConnected failed: " + e.getMessage());
            }
        }

        @Override
        public void onStateChanged(long state) {
            // mirror the per-state filtering: disconnect-flavored states are
            // suppressed during the restart window
            if (state == Android.ClientStateDisconnected || state == Android.ClientStateDisconnecting) {
                if (dropDisconnects) {
                    Log.d(LOGTAG, "filtered onStateChanged(" + state + ") during restart");
                    return;
                }
            } else {
                // Connecting, Connected or NoNetwork — ends the restart window
                // once the old engine is known to be gone.
                release("onStateChanged(" + state + ")");
            }
            try {
                delegate.onStateChanged(state);
            } catch (Exception e) {
                Log.w(LOGTAG, "delegate onStateChanged failed: " + e.getMessage());
            }
        }

        @Override
        public void onDisconnecting() {
            if (dropDisconnects) {
                Log.d(LOGTAG, "filtered onDisconnecting during restart");
                return;
            }
            try {
                delegate.onDisconnecting();
            } catch (Exception e) {
                Log.w(LOGTAG, "delegate onDisconnecting failed: " + e.getMessage());
            }
        }

        @Override
        public void onDisconnected() {
            if (dropDisconnects) {
                Log.d(LOGTAG, "filtered onDisconnected during restart");
                return;
            }
            try {
                delegate.onDisconnected();
            } catch (Exception e) {
                Log.w(LOGTAG, "delegate onDisconnected failed: " + e.getMessage());
            }
        }

        @Override
        public void onAddressChanged(String fqdn, String ip) {
            try {
                delegate.onAddressChanged(fqdn, ip);
            } catch (Exception e) {
                Log.w(LOGTAG, "delegate onAddressChanged failed: " + e.getMessage());
            }
        }

        @Override
        public void onPeersListChanged(long numberOfPeers) {
            try {
                delegate.onPeersListChanged(numberOfPeers);
            } catch (Exception e) {
                Log.w(LOGTAG, "delegate onPeersListChanged failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void onNetworkTypeChanged() {
        Log.d(LOGTAG, "network type changed, scheduling restart with "
                + DEBOUNCE_DELAY_MS + "ms debounce.");

        synchronized (restartLock) {
            restartScheduled = true;
            handler.removeCallbacks(restartRunnable);
            handler.postDelayed(restartRunnable, DEBOUNCE_DELAY_MS);
        }
    }

    /**
     * Cancels any pending debounced restart. Called whenever an external
     * actor (typically a user-driven Connect/Disconnect) takes over the
     * engine lifecycle, so the network-change-driven restart does not
     * interfere with that explicit action.
     */
    public void cancelPendingRestart() {
        synchronized (restartLock) {
            if (restartScheduled) {
                Log.d(LOGTAG, "external action took over engine lifecycle; cancelling pending restart");
                handler.removeCallbacks(restartRunnable);
                restartScheduled = false;
            }
        }
    }

    /**
     * <p>Cleans up resources, like the restart runnable and timeout callback.</p>
     * <p>Call this when the EngineRestarter is no longer needed to prevent memory leaks.</p>
     */
    public void cleanup() {
        synchronized (restartLock) {
            handler.removeCallbacks(restartRunnable);
            restartScheduled = false;
        }

        if (timeoutCallback != null) {
            handler.removeCallbacks(timeoutCallback);
        }

        if (currentListener != null) {
            engineRunner.removeServiceStateListener(currentListener);
            currentListener = null;
        }

        // Restore visibility for any external listeners that were suppressed
        // by an in-flight restart, so the upcoming engine stop is delivered
        // to them rather than swallowed by the suppression set.
        unsuppressAll(suppressedHolder.getAndSet(null));

        engineRunner.removeOnConnectedObserver(connectedObserver);

        isRestartInProgress = false;
    }
}
