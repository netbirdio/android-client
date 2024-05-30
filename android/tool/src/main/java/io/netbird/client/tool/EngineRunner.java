package io.netbird.client.tool;


import android.content.Context;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.Client;
import io.netbird.gomobile.android.ConnectionListener;
import io.netbird.gomobile.android.DNSList;
import io.netbird.gomobile.android.PeerInfoArray;
import io.netbird.gomobile.android.URLOpener;

class EngineRunner {

    private static final String LOGTAG = "EngineRunner";
    private final Context context;
    private boolean engineIsRunning = false;
    Set<ServiceStateListener> serviceStateListeners = new HashSet<>();
    private final Client goClient;

    public EngineRunner(VPNService vpnService) {
        context = vpnService;
        NetworkChangeNotifier notifier = new NetworkChangeNotifier(vpnService);
        IFace iFace = new IFace(vpnService);
        goClient = Android.newClient(
                Preferences.configFile(vpnService),
                DeviceName.getDeviceName(),
                BuildConfig.VERSION_NAME,
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
            return;
        }

        updateLogLevel();

        engineIsRunning = true;
        Runnable r = () -> {
            DNSWatch dnsWatch = new DNSWatch(context);
            try {
                notifyServiceStateListeners(true);
                if(urlOpener == null) {
                    goClient.runWithoutLogin(dnsWatch.dnsServers(), () -> dnsWatch.setDNSChangeListener(this::changed));
                } else {
                    goClient.run(urlOpener, dnsWatch.dnsServers(), () -> dnsWatch.setDNSChangeListener(this::changed));
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
        if (BuildConfig.DEBUG || pref.isTraceLogEnabled()) {
            goClient.setTraceLogLevel();
        } else {
            goClient.setInfoLogLevel();
        }
    }
}
