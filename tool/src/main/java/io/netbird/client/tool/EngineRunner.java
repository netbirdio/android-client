package io.netbird.client.tool;


import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.Client;
import io.netbird.gomobile.android.ConnectionListener;
import io.netbird.gomobile.android.DNSList;
import io.netbird.gomobile.android.EnvList;
import io.netbird.gomobile.android.NetworkArray;
import io.netbird.gomobile.android.NetworkChangeListener;
import io.netbird.gomobile.android.PeerInfoArray;
import io.netbird.gomobile.android.TunAdapter;
import io.netbird.gomobile.android.URLOpener;

class EngineRunner {

    private static final String LOGTAG = "EngineRunner";
    private boolean engineIsRunning = false;
    Set<ServiceStateListener> serviceStateListeners = new HashSet<>();
    private final Client goClient;

    public EngineRunner(String configurationFilePath, NetworkChangeListener networkChangeListener, TunAdapter tunAdapter, IFaceDiscover iFaceDiscover, String versionName, boolean isTraceLogEnabled, boolean isDebuggable) {
        goClient = Android.newClient(
                configurationFilePath,
                androidSDKVersion(),
                DeviceName.getDeviceName(),
                versionName,
                tunAdapter,
                iFaceDiscover,
                networkChangeListener);

        updateLogLevel(isTraceLogEnabled, isDebuggable);
    }

    public void run(@NotNull DNSWatch dnsWatch, @NotNull Preferences preferences, boolean isDebuggable, @NotNull URLOpener urlOpener) {
        runClient(dnsWatch, preferences, isDebuggable, urlOpener);
    }

    public void runWithoutAuth(@NotNull DNSWatch dnsWatch, @NotNull Preferences preferences, boolean isDebuggable) {
        runClient(dnsWatch, preferences, isDebuggable, null);
    }

    private synchronized void runClient(@NotNull DNSWatch dnsWatch, @NotNull Preferences preferences, boolean isDebuggable, @Nullable URLOpener urlOpener) {
        Log.d(LOGTAG, "run engine");
        if (engineIsRunning) {
            Log.e(LOGTAG, "engine already running");
            return;
        }

        updateLogLevel(preferences.isTraceLogEnabled(), isDebuggable);

        engineIsRunning = true;
        Runnable r = () -> {
//            DNSWatch dnsWatch = new DNSWatch(context);
//            Preferences preferences = new Preferences(context);
            var envList = EnvVarPackager.getEnvironmentVariables(preferences);

            try {
                notifyServiceStateListeners(true);
                if(urlOpener == null) {
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

    private void updateLogLevel(boolean isTraceLogEnabled , boolean isDebuggable) {
        if (isDebuggable || isTraceLogEnabled) {
            goClient.setTraceLogLevel();
        } else {
            goClient.setInfoLogLevel();
        }
    }

    private int androidSDKVersion() {
       return Build.VERSION.SDK_INT ;
    }
}
