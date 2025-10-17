package io.netbird.client.tool;

import android.util.Log;

import io.netbird.client.tool.networks.NetworkToggleListener;

/**
 * <p>EngineRestarter restarts the Go engine.</p>
 * <p>It implements {@link NetworkToggleListener} to restart the engine when the available network type changes.</p>
 */
class EngineRestarter implements NetworkToggleListener {
    private static final String LOGTAG = EngineRestarter.class.getSimpleName();
    private final EngineRunner engineRunner;
    public EngineRestarter(EngineRunner engineRunner) {
        this.engineRunner = engineRunner;
    }

    /**
     * <p>Restarts the Go engine currently running.</p>
     * <p>It registers an anonymous implementation of {@link ServiceStateListener}
     * in order to know for sure when the engine actually stops in order to restart it.</p>
     * <p>If the engine isn't running, this method does nothing.</p>
     */
    private void restartEngine() {
        // Does nothing if the engine isn't running by the time this callback is fired.
        if (!engineRunner.isRunning()) return;

        var serviceStateListener = new ServiceStateListener() {
            boolean ignoreFirstOnStarted = true;

            @Override
            public void onStarted() {
                // EngineRunner calls this event as soon as a ServiceStateListener is registered to it.
                // This is to keep this instance from unregistering itself at the wrong time.
                if (ignoreFirstOnStarted) {
                    ignoreFirstOnStarted = false;
                    return;
                }

                engineRunner.removeServiceStateListener(this);
            }

            @Override
            public void onStopped() {
                Log.d(LOGTAG, "engine is stopped, re-running due to network change.");
                engineRunner.runWithoutAuth();
            }

            @Override
            public void onError(String msg) {
                Log.e(LOGTAG, msg);
                engineRunner.removeServiceStateListener(this);
            }
        };

        engineRunner.addServiceStateListener(serviceStateListener);

        Log.d(LOGTAG, "engine is running, stopping due to network change.");
        engineRunner.stop();
    }

    @Override
    public void onNetworkTypeChanged() {
        restartEngine();
    }
}
