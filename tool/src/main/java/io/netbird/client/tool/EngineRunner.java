package io.netbird.client.tool;


import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.Client;
import io.netbird.gomobile.android.ConnectionListener;
import io.netbird.gomobile.android.DNSList;
import io.netbird.gomobile.android.NetworkArray;
import io.netbird.gomobile.android.PeerInfoArray;
import io.netbird.gomobile.android.URLOpener;

class EngineRunner {

    private static final String LOGTAG = "EngineRunner";
    private final Context context;
    private boolean engineIsRunning = false;
    Set<ServiceStateListener> serviceStateListeners = ConcurrentHashMap.newKeySet();
    private final Client goClient;

    public EngineRunner(VPNService vpnService) {
        context = vpnService;
        NetworkChangeNotifier notifier = new NetworkChangeNotifier(vpnService);
        IFace iFace = new IFace(vpnService);
        goClient = Android.newClient(
                Preferences.configFile(vpnService),
                androidSDKVersion(),
                DeviceName.getDeviceName(),
                Version.getVersionName(vpnService),
                iFace,
                new IFaceDiscover(),
                notifier);

        updateLogLevel();
    }

    public void run(URLOpener urlOpener) {
        runClient(urlOpener);
    }

    public void runWithoutAuth() {
        runClient(null);
    }

    private synchronized void runClient(URLOpener urlOpener) {
        Log.d(LOGTAG, "run engine");
        if (engineIsRunning) {
            Log.e(LOGTAG, "engine already running");
            return;
        }

        updateLogLevel();

        engineIsRunning = true;
        Runnable r = () -> {
            DNSWatch dnsWatch = new DNSWatch(context);
            Preferences preferences = new Preferences(context);
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

    private void updateLogLevel() {
        Preferences pref = new Preferences(context);
        if (Version.isDebuggable(context) || pref.isTraceLogEnabled()) {
            goClient.setTraceLogLevel();
        } else {
            goClient.setInfoLogLevel();
        }
    }

    private int androidSDKVersion() {
       return Build.VERSION.SDK_INT ;
    }
}
