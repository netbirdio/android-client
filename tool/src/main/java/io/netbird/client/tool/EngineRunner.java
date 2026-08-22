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
import io.netbird.gomobile.android.ErrListener;
import io.netbird.gomobile.android.NetworkArray;
import io.netbird.gomobile.android.NetworkChangeListener;
import io.netbird.gomobile.android.PeerInfoArray;
import io.netbird.gomobile.android.SSHClient;
import io.netbird.gomobile.android.StateChangeListener;
import io.netbird.gomobile.android.TunAdapter;
import io.netbird.gomobile.android.TunSettings;
import io.netbird.gomobile.android.URLOpener;

class EngineRunner {

    private static final String LOGTAG = "EngineRunner";
    private final Context context;
    private final boolean isDebuggable;
    private final ProfileManagerWrapper profileManager;
    private boolean engineIsRunning = false;
    Set<ServiceStateListener> serviceStateListeners = ConcurrentHashMap.newKeySet();
    private final Set<Runnable> connectedObservers = ConcurrentHashMap.newKeySet();
    private final Set<ConnectionListener> connectionObservers = ConcurrentHashMap.newKeySet();
    private volatile SessionMonitor sessionMonitor;
    private final Client goClient;
    private ConnectionListener connectionListener;

    public EngineRunner(Context context, NetworkChangeListener networkChangeListener, TunAdapter tunAdapter,
                        IFaceDiscover iFaceDiscover, String versionName, boolean isTraceLogEnabled, boolean isDebuggable,
                        ProfileManagerWrapper profileManager) {
        this.context = context;
        this.isDebuggable = isDebuggable;
        this.profileManager = profileManager;

        goClient = Android.newClient(
                androidSDKVersion(),
                DeviceName.getDeviceName(),
                versionName,
                tunAdapter,
                iFaceDiscover,
                networkChangeListener);

        updateLogLevel(isTraceLogEnabled, isDebuggable);

        // The Go-side subscriptions are client-scoped and survive engine
        // restarts, so one registration at construction time is enough. The
        // state signal carries no payload; consumers pull the fresh state via
        // status() / sessionExpiresAt(). Expiry warnings arrive with their
        // deadline, timed by the engine's own session watcher.
        goClient.setStateChangeListener(new StateChangeListener() {
            @Override
            public void onStateChanged() {
                SessionMonitor monitor = sessionMonitor;
                if (monitor != null) {
                    monitor.onStateChanged();
                }
            }

            @Override
            public void onSessionExpiring(long expiresAtUnix, long leadMinutes, boolean finalWarning) {
                SessionMonitor monitor = sessionMonitor;
                if (monitor != null) {
                    monitor.onSessionExpiring(expiresAtUnix, leadMinutes, finalWarning);
                }
            }
        });
    }

    /** Registers the consumer of the Go session notifications. */
    public void setSessionMonitor(SessionMonitor monitor) {
        sessionMonitor = monitor;
    }

    /** The run loop's status label, e.g. "Connected" or "NeedsLogin". */
    public String status() {
        return goClient.status();
    }

    /** Session deadline as unix seconds, or 0 when none is known. */
    public long sessionExpiresAt() {
        return goClient.sessionExpiresAtUnix();
    }

    /**
     * Runs the interactive SSO flow and extends the session deadline on the
     * management server without touching the tunnel. Async; the result
     * arrives on the listener.
     */
    public void extendAuthSession(URLOpener urlOpener, boolean isAndroidTV, ErrListener resultListener) {
        goClient.extendAuthSession(urlOpener, isAndroidTV, resultListener);
    }

    /** Aborts an in-flight session extend, leaving the tunnel untouched. */
    public void cancelExtendAuthSession() {
        goClient.cancelExtendAuthSession();
    }

    // setNetworkAvailable forwards OS connectivity state to the Go client,
    // which suspends its reconnect loops while no network is available. The
    // Go-side state is process-global, so it may be called regardless of
    // whether the engine is running.
    public void setNetworkAvailable(boolean available) {
        goClient.setNetworkAvailable(available);
    }

    // notifyNetworkChange tells the Go client the OS switched networks (e.g.
    // cellular to WiFi). The Go side cuts the management, signal and relay
    // connections, whose sockets are bound to the old network, so their
    // reconnect loops redial immediately on the new one. Unlike an engine
    // restart this keeps the TUN device, the WireGuard config and the peer
    // state untouched.
    public void notifyNetworkChange() {
        goClient.notifyNetworkChange();
    }

    public void run(@NotNull URLOpener urlOpener, boolean isAndroidTV) {
        runClient(urlOpener, isAndroidTV);
    }

    public void runWithoutAuth() {
        runClient(null, false);
    }

    private synchronized void runClient(@Nullable URLOpener urlOpener, boolean isAndroidTV) {
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

            // Initialize engine with current active profile
            // Get paths from Go ProfileManager instead of constructing them in Java
            String configurationFilePath;
            String stateFilePath;
            try {
                configurationFilePath = profileManager.getActiveConfigPath();
                stateFilePath = profileManager.getActiveStateFilePath();
                Profile activeProfile = profileManager.getActiveProfile();
                Log.d(LOGTAG, "Initializing engine with profile: " + activeProfile);
                Log.d(LOGTAG, "Config path: " + configurationFilePath);
                Log.d(LOGTAG, "State path: " + stateFilePath);
            } catch (Exception e) {
                Log.e(LOGTAG, "Failed to get profile paths from ProfileManager", e);
                throw new RuntimeException("Failed to get profile paths: " + e.getMessage(), e);
            }

            // Create fresh PlatformFiles with current config/state paths
            // This allows profile switching without recreating the entire Client
            String cacheDir = context.getCacheDir().getAbsolutePath();
            var platformFiles = new AndroidPlatformFiles(configurationFilePath, stateFilePath, cacheDir);
            Log.d(LOGTAG, "Running engine with config: " + configurationFilePath + ", state: " + stateFilePath);

            try {
                notifyServiceStateListeners(true);
                if (urlOpener == null) {
                    goClient.runWithoutLogin(platformFiles, dnsWatch.dnsServers(), () -> dnsWatch.setDNSChangeListener(this::changed), envList);
                } else {
                    goClient.run(platformFiles, urlOpener, isAndroidTV, dnsWatch.dnsServers(), () -> dnsWatch.setDNSChangeListener(this::changed), envList);
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
        // Unwrap first: a null listener still gets wrapped (around a no-op) so
        // the service's own observers keep receiving events, which means the
        // wrapper can otherwise be handed back to us and stack on itself.
        ConnectionListener raw = unwrap(listener);
        ConnectionListener wrapped = raw == null
                ? new ObservingConnectionListener(NO_OP_CONNECTION_LISTENER, connectedObservers, connectionObservers)
                : new ObservingConnectionListener(raw, connectedObservers, connectionObservers);
        this.connectionListener = wrapped;
        goClient.setConnectionListener(wrapped);
    }

    /**
     * Keeps the connection callbacks flowing while no UI is bound, so the
     * service's own observers (the status-bar icon) still track the tunnel
     * during always-on / boot starts.
     */
    private static final ConnectionListener NO_OP_CONNECTION_LISTENER = new ConnectionListener() {
        @Override public void onStateChanged(long state) {}
        @Override public void onConnecting() {}
        @Override public void onConnected() {}
        @Override public void onDisconnecting() {}
        @Override public void onDisconnected() {}
        @Override public void onAddressChanged(String f, String i) {}
        @Override public void onPeersListChanged(long n) {}
    };

    /**
     * Registers a service-owned observer of the tunnel's connection phase.
     * Unlike the app's ConnectionListener this survives the UI unbinding, so
     * it stays accurate for always-on VPN and boot starts.
     */
    public synchronized void addConnectionObserver(ConnectionListener observer) {
        connectionObservers.add(observer);
        // Make sure the Go core has a listener installed even before any UI
        // binds, otherwise the observer would never be called.
        if (connectionListener == null) {
            setConnectionListener(null);
        }
    }

    private static ConnectionListener unwrap(ConnectionListener listener) {
        ConnectionListener current = listener;
        while (current instanceof ObservingConnectionListener) {
            current = ((ObservingConnectionListener) current).delegate;
        }
        return current;
    }

    private static final class ObservingConnectionListener implements ConnectionListener {
        private final ConnectionListener delegate;
        private final java.util.Set<Runnable> connectedObservers;
        private final java.util.Set<ConnectionListener> connectionObservers;

        ObservingConnectionListener(ConnectionListener delegate, java.util.Set<Runnable> connectedObservers,
                                    java.util.Set<ConnectionListener> connectionObservers) {
            this.delegate = delegate;
            this.connectedObservers = connectedObservers;
            this.connectionObservers = connectionObservers;
        }

        private void fanOut(java.util.function.Consumer<ConnectionListener> call) {
            for (ConnectionListener obs : connectionObservers) {
                try { call.accept(obs); } catch (Exception e) { Log.w(LOGTAG, "connection observer failed", e); }
            }
        }

        @Override public void onStateChanged(long state) {
            Log.d(LOGTAG, "FROM GO: onStateChanged(" + state + ")");
            delegate.onStateChanged(state);
            fanOut(obs -> obs.onStateChanged(state));
        }
        @Override public void onConnecting() {
            Log.d(LOGTAG, "FROM GO: onConnecting()");
            delegate.onConnecting();
            fanOut(ConnectionListener::onConnecting);
        }
        @Override public void onConnected() {
            Log.d(LOGTAG, "FROM GO: onConnected()");
            delegate.onConnected();
            for (Runnable obs : connectedObservers) {
                try { obs.run(); } catch (Exception e) { Log.w(LOGTAG, "connected observer failed", e); }
            }
            fanOut(ConnectionListener::onConnected);
        }
        @Override public void onDisconnecting() {
            Log.d(LOGTAG, "FROM GO: onDisconnecting()");
            delegate.onDisconnecting();
            fanOut(ConnectionListener::onDisconnecting);
        }
        @Override public void onDisconnected() {
            Log.d(LOGTAG, "FROM GO: onDisconnected()");
            delegate.onDisconnected();
            fanOut(ConnectionListener::onDisconnected);
        }
        @Override public void onAddressChanged(String f, String i) { delegate.onAddressChanged(f, i); }
        @Override public void onPeersListChanged(long n) { delegate.onPeersListChanged(n); }
    }

    /**
     * Detaches the UI's connection listener. The service's own observers are
     * kept subscribed: dropping the Go-side listener entirely would freeze the
     * status-bar icon for as long as no activity is bound, which is exactly
     * when the notification is the only status the user can see.
     */
    public synchronized void removeStatusListener() {
        if (connectionObservers.isEmpty()) {
            this.connectionListener = null;
            goClient.removeConnectionListener();
            return;
        }
        setConnectionListener(null);
    }

    /** Registers a callback that fires every time the engine reports OnConnected. */
    public void addOnConnectedObserver(Runnable observer) {
        connectedObservers.add(observer);
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

    @Nullable
    public NetworkArray networks() {
        NetworkArray networks = goClient.networks();
        if (networks == null) {
            Log.e(LOGTAG, "Failed to retrieve networks");
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

    public SSHClient newSSHClient() {
        return Android.newSSHClient(goClient);
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

    public TunSettings getTunSettings() {
        try {
            return goClient.getTunSettings();
        } catch (Exception e) {
            Log.e(LOGTAG, "failed to get TUN settings", e);
            return null;
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

    public String debugBundle(boolean anonymize) throws Exception {
        String configPath = profileManager.getActiveConfigPath();
        String statePath = profileManager.getActiveStateFilePath();
        String cacheDir = context.getCacheDir().getAbsolutePath();
        var platformFiles = new AndroidPlatformFiles(configPath, statePath, cacheDir);
        try {
            // The strict level stays unused until the troubleshoot screen
            // grows an option for it.
            return goClient.debugBundle(platformFiles, anonymize, Android.AnonymizeLevelDefault);
        } catch (Exception e) {
            Log.e(LOGTAG, "goClient error", e);
            throw e;
        }
    }
}
