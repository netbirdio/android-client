package io.netbird.client.tool;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.Client;
import io.netbird.gomobile.android.ConnectionListener;
import io.netbird.gomobile.android.DNSList;
import io.netbird.gomobile.android.NetworkArray;
import io.netbird.gomobile.android.NetworkChangeListener;
import io.netbird.gomobile.android.PeerInfoArray;
import io.netbird.gomobile.android.TunAdapter;
import io.netbird.gomobile.android.URLOpener;

class EngineRunner {

    private static final String LOGTAG = "EngineRunner";
    private final Context context;
    private final boolean isDebuggable;
    private boolean engineIsRunning = false;
    Set<ServiceStateListener> serviceStateListeners = ConcurrentHashMap.newKeySet();
    private final Client goClient;

    public EngineRunner(Context context, String configurationFilePath, NetworkChangeListener networkChangeListener, TunAdapter tunAdapter,
                        IFaceDiscover iFaceDiscover, String versionName, boolean isTraceLogEnabled, boolean isDebuggable,
                        String stateFilePath) {
        this.context = context;
        this.isDebuggable = isDebuggable;
        var platformFiles = new AndroidPlatformFiles(configurationFilePath, stateFilePath);

        goClient = Android.newClient(
                platformFiles,
                androidSDKVersion(),
                DeviceName.getDeviceName(),
                versionName,
                tunAdapter,
                iFaceDiscover,
                networkChangeListener);

        updateLogLevel(isTraceLogEnabled, isDebuggable);
    }

    public void run(@NotNull URLOpener urlOpener) {
        runClient(urlOpener);
    }

    public void runWithoutAuth() {
        runClient( null);
    }

    private synchronized void runClient(@Nullable URLOpener urlOpener) {
        Log.d(LOGTAG, "run engine");
        if (engineIsRunning) {
            Log.e(LOGTAG, "engine already running");
            return;
        }

        // update the log levels based on the up to date user settings
        Preferences preferences = new Preferences(context);
        updateLogLevel(preferences.isTraceLogEnabled(), isDebuggable);

        engineIsRunning = true;
        Runnable r = () -> {
            DNSWatch dnsWatch = new DNSWatch(context);

            var envList = EnvVarPackager.getEnvironmentVariables(preferences);

            try {
                notifyServiceStateListeners(true);
                if (urlOpener == null) {
                    goClient.runWithoutLogin(dnsWatch.dnsServers(), () -> dnsWatch.setDNSChangeListener(this::changed), envList);
                } else {
                    goClient.run(urlOpener, dnsWatch.dnsServers(), () -> dnsWatch.setDNSChangeListener(this::changed), envList);
                }
            } catch (Exception e) {
                Log.e(LOGTAG, "goClient error", e);
                notifyError(e);
            } finally {
                engineIsRunning = false;
                dnsWatch.removeDNSChangeListener();
                notifyServiceStateListeners(false);
            }
            Log.e(LOGTAG, "service stopped");

        };
        new Thread(r).start();
    }

    private void changed(DNSList dnsServers) throws Exception {
        goClient.onUpdatedHostDNS(dnsServers);
    }

    public synchronized boolean isRunning() {
        return engineIsRunning;
    }

    public synchronized void setConnectionListener(ConnectionListener listener) {
        goClient.setConnectionListener(listener);
    }

    public synchronized void removeStatusListener() {
        goClient.removeConnectionListener();
    }

    public synchronized void addServiceStateListener(ServiceStateListener serviceStateListener) {
        if (engineIsRunning) {
            serviceStateListener.onStarted();
        } else {
            serviceStateListener.onStopped();
        }
        serviceStateListeners.add(serviceStateListener);
    }

    /**
     * Atomically adds a listener if and only if the engine is currently running.
     * Does NOT fire immediate callbacks like addServiceStateListener does.
     *
     * @return true if listener was registered (engine was running), false otherwise
     */
    public synchronized boolean addServiceStateListenerForRestart(ServiceStateListener listener) {
        if (!engineIsRunning) {
            return false;  // Engine not running, can't restart
        }
        // Add listener without firing immediate callback
        serviceStateListeners.add(listener);
        return true;
    }

    public synchronized void removeServiceStateListener(ServiceStateListener serviceStateListener) {
        serviceStateListeners.remove(serviceStateListener);
    }

    public synchronized void stop() {
        goClient.stop();
    }

    public PeerInfoArray peersInfo() {
        return goClient.peersList();
    }

    public NetworkArray networks() {
        NetworkArray networks = goClient.networks();
        if (networks == null) {
            Log.e(LOGTAG, "Failed to retrieve networks, returning empty array");
            return new NetworkArray();
        }
        return networks;
    }

    private synchronized void notifyError(Exception e) {
        for (ServiceStateListener s : serviceStateListeners) {
            s.onError(e.getMessage());
        }
    }

    private synchronized void notifyServiceStateListeners(boolean engineIsRunning) {
        for (ServiceStateListener s : serviceStateListeners) {
            if (engineIsRunning) {
                s.onStarted();
            } else {
                s.onStopped();
            }
        }
    }

    private void updateLogLevel(boolean isTraceLogEnabled, boolean isDebuggable) {
        if (isDebuggable || isTraceLogEnabled) {
            goClient.setTraceLogLevel();
        } else {
            goClient.setInfoLogLevel();
        }
    }

    private int androidSDKVersion() {
        return Build.VERSION.SDK_INT;
    }

    public void renewTUN(int fd) {
        Log.d(LOGTAG, String.format("renewing TUN fd: %d", fd));
        try {
            goClient.renewTun(fd);
        } catch (Exception e) {
            Log.e(LOGTAG, "goClient error", e);
            notifyError(e);
        }
    }

    public void selectRoute(String route) throws Exception {
        Log.d(LOGTAG, String.format("selecting route: %s", route));
        try {
            goClient.selectRoute(route);
        } catch (Exception e) {
            Log.e(LOGTAG, "goClient error", e);
            notifyError(e);
            throw e;
        }
    }

    public void deselectRoute(String route) throws Exception {
        Log.d(LOGTAG, String.format("deselecting route: %s", route));
        try {
            goClient.deselectRoute(route);
        } catch (Exception e) {
            Log.e(LOGTAG, "goClient error", e);
            notifyError(e);
            throw e;
        }
    }
}
