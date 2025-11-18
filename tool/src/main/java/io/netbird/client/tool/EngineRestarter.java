package io.netbird.client.tool;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import io.netbird.client.tool.networks.NetworkToggleListener;

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
    public EngineRestarter(EngineRunner engineRunner) {
        this.engineRunner = engineRunner;
        this.handler = new Handler(Looper.getMainLooper());
        this.restartRunnable = this::restartEngine;
    }

    /**
     * <p>Restarts the Go engine currently running.</p>
     * <p>It registers an anonymous implementation of {@link ServiceStateListener}
     * in order to know for sure when the engine actually stops in order to restart it.</p>
     * <p>If the engine isn't running, this method does nothing.</p>
     */
    private void restartEngine() {
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

        timeoutCallback = () -> {
            if (isRestartInProgress) {
                Log.e(LOGTAG, "engine restart timeout - forcing flag reset");
                isRestartInProgress = false;
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
            }

            @Override
            public void onStopped() {
                Log.d(LOGTAG, "engine is stopped, restarting...");
                engineRunner.runWithoutAuth();
            }

            @Override
            public void onError(String msg) {
                Log.e(LOGTAG, "restart failed: " + msg);
                isRestartInProgress = false; // Resetting flag on error as well
                handler.removeCallbacks(timeoutCallback);  // Cancel timeout
                engineRunner.removeServiceStateListener(this);
            }
        };
        currentListener = serviceStateListener;

        // Atomically check and register to avoid race condition
        if (!engineRunner.addServiceStateListenerForRestart(serviceStateListener)) {
            Log.d(LOGTAG, "engine stopped before restart could begin - aborting");
            handler.removeCallbacks(timeoutCallback);
            isRestartInProgress = false;
            return;
        }

        Log.d(LOGTAG, "engine is running, stopping due to network change");
        engineRunner.stop();
    }

    @Override
    public void onNetworkTypeChanged() {
        Log.d(LOGTAG, "network type changed, scheduling restart with "
                + DEBOUNCE_DELAY_MS + "ms debounce.");

        handler.removeCallbacks(restartRunnable);
        handler.postDelayed(restartRunnable, DEBOUNCE_DELAY_MS);
    }

    /**
     * <p>Cleans up resources, like the restart runnable and timeout callback.</p>
     * <p>Call this when the EngineRestarter is no longer needed to prevent memory leaks.</p>
     */
    public void cleanup() {
        handler.removeCallbacks(restartRunnable);

        if (timeoutCallback != null) {
            handler.removeCallbacks(timeoutCallback);
        }

        if (currentListener != null) {
            engineRunner.removeServiceStateListener(currentListener);
            currentListener = null;
        }

        isRestartInProgress = false;
    }
}
