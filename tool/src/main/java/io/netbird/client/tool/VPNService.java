package io.netbird.client.tool;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import androidx.annotation.Nullable;

import io.netbird.client.tool.autoconnect.AutoConnectController;
import io.netbird.client.tool.autoconnect.AutoConnectHost;
import io.netbird.client.tool.autoconnect.AutoConnectConsentNotification;
import io.netbird.client.tool.autoconnect.AutoConnectPreferences;
import io.netbird.client.tool.autoconnect.WifiInfoProvider;
import io.netbird.client.tool.networks.ConcreteNetworkAvailabilityListener;
import io.netbird.client.tool.networks.NetworkChangeDetector;
import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.ConnectionListener;
import io.netbird.gomobile.android.ErrListener;
import io.netbird.gomobile.android.NetworkArray;
import io.netbird.gomobile.android.PeerInfoArray;
import io.netbird.gomobile.android.SSHClient;
import io.netbird.gomobile.android.TunSettings;
import io.netbird.gomobile.android.URLOpener;


public class VPNService extends android.net.VpnService implements AutoConnectHost {
    private final static String LOGTAG = "service";
    public static final String INTENT_ACTION_START = "io.netbird.client.intent.action.START_SERVICE";
    public static final String ACTION_STOP_ENGINE = "io.netbird.client.intent.action.STOP_ENGINE";
    // Launches MainActivity to run the interactive session-extend flow; set
    // on the persistent notification's "Extend session" action.
    public static final String ACTION_EXTEND_SESSION = "io.netbird.client.intent.action.EXTEND_SESSION";
    // Launches MainActivity and re-runs the interactive login; set on the
    // persistent notification's content intent while login is required, so
    // tapping it goes straight into login instead of just opening the app.
    public static final String ACTION_LOGIN_REQUIRED = "io.netbird.client.intent.action.LOGIN_REQUIRED";
    // Arms/disarms auto-connect's background network monitoring — sent by
    // AutoConnectArmer whenever a trigger is toggled and by BootReceiver.
    public static final String ACTION_START_MONITORING = "io.netbird.client.intent.action.START_MONITORING";
    public static final String ACTION_STOP_MONITORING = "io.netbird.client.intent.action.STOP_MONITORING";
    private static final String INTENT_ALWAYS_ON_START = "android.net.VpnService";
    // Run-loop status labels, as returned by EngineRunner.status(); they come
    // from internal.StatusType on the Go side.
    private static final String STATUS_CONNECTED = "Connected";
    private static final String STATUS_CONNECTING = "Connecting";
    private static final String STATUS_NEEDS_LOGIN = "NeedsLogin";
    private static final String STATUS_SESSION_EXPIRED = "SessionExpired";
    private static final String STATUS_LOGIN_FAILED = "LoginFailed";
    private final IBinder myBinder = new MyLocalBinder();
    private EngineRunner engineRunner;
    private ForegroundNotification fgNotification;
    private SessionNotification sessionNotification;
    private SessionMonitor sessionMonitor;
    private TUNParameters currentTUNParameters;
    private NetworkChangeNotifier notifier;

    private RouteChangeListener listener;

    private NetworkChangeDetector networkChangeDetector;
    private ConcreteNetworkAvailabilityListener networkAvailabilityListener;
    private NetworkSwitchNotifier networkSwitchNotifier;
    private android.content.BroadcastReceiver stopEngineReceiver;

    private AutoConnectPreferences autoConnectPreferences;
    private AutoConnectController autoConnectController;
    // True whenever at least one auto-connect trigger is armed and this
    // service should stay alive/foreground to watch for it, independent of
    // whether the engine itself is currently connected.
    private boolean monitoringActive = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOGTAG, "onCreate");

        tunCreator.setPriority(Thread.MAX_PRIORITY);
        tunCreator.start();

        var versionName = Version.getVersionName(this);
        var tunAdapter = new IFace(this);
        var iFaceDiscover = new IFaceDiscover();

        listener = this::queueTUNRenewal;

        notifier = new NetworkChangeNotifier(this);
        notifier.addRouteChangeListener(listener);

        Preferences preferences = new Preferences(this);

        // Create profile manager for managing profiles
        ProfileManagerWrapper profileManager = new ProfileManagerWrapper(this);

        // Create foreground notification before initializing engine
        fgNotification = new ForegroundNotification(this);

        engineRunner = new EngineRunner(this, notifier, tunAdapter, iFaceDiscover, versionName,
                preferences.isTraceLogEnabled(), Version.isDebuggable(this), profileManager);

        // Session tracking lives here, in the service — the Android analogue
        // of the desktop daemon — so warnings and the expired notification
        // work even when no UI is bound (always-on VPN, boot start).
        // Must be wired before addServiceStateListener below: registration
        // fires an immediate onStopped/onStarted, which touches the monitor.
        sessionNotification = new SessionNotification(this);
        sessionMonitor = new SessionMonitor(engineRunner::status, engineRunner::sessionExpiresAt);
        engineRunner.setSessionMonitor(sessionMonitor);
        sessionMonitor.addListener(sessionEventListener);
        engineRunner.addOnConnectedObserver(() -> sessionNotification.cancel());

        // Drive the status-bar icon from the tunnel's own phase rather than
        // the engine start/stop edges, so "connecting" is visible while the
        // core is still bringing the tunnel up.
        engineRunner.addConnectionObserver(connectionObserver);

        engineRunner.addServiceStateListener(serviceStateListener);

        // Create network availability listener after the engine runner so we
        // can gate notifications on the engine actually being up; this avoids
        // acting on Android's initial onAvailable burst during cold start.
        networkAvailabilityListener = new ConcreteNetworkAvailabilityListener(
                engineRunner::isRunning, engineRunner::setNetworkAvailable);

        networkSwitchNotifier = new NetworkSwitchNotifier(engineRunner);
        networkAvailabilityListener.subscribe(networkSwitchNotifier);

        networkChangeDetector = new NetworkChangeDetector(
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE));
        networkChangeDetector.subscribe(networkAvailabilityListener);

        autoConnectPreferences = new AutoConnectPreferences(this);
        autoConnectController = new AutoConnectController(
                autoConnectPreferences, new WifiInfoProvider(this), this);
        networkChangeDetector.subscribe(autoConnectController);

        networkChangeDetector.registerNetworkCallback();
        // Push the initial connectivity state into the Go client: transition
        // events alone would leave it stuck at the online default when the
        // service starts while the device has no network (e.g. airplane mode).
        engineRunner.setNetworkAvailable(networkChangeDetector.hasInternetConnectivity());

        // Register broadcast receiver for stopping engine (e.g., during profile switch)
        stopEngineReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_STOP_ENGINE.equals(intent.getAction())) {
                    Log.d(LOGTAG, "Received stop engine broadcast");
                    if (engineRunner != null) {
                        engineRunner.stop();
                    }
                }
            }
        };
        android.content.IntentFilter filter = new android.content.IntentFilter(ACTION_STOP_ENGINE);
        androidx.core.content.ContextCompat.registerReceiver(
                this,
                stopEngineReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    public int onStartCommand(@Nullable final Intent intent, final int flags, final int startId) {
        Log.d(LOGTAG, "onStartCommand");
        if (intent == null) {
            // A process restart after being killed delivers a null intent;
            // re-arm monitoring here so it isn't silently lost until the
            // next explicit trigger or reboot.
            if (autoConnectPreferences != null && autoConnectPreferences.isAnyTriggerArmed()
                    && !engineRunner.isRunning()) {
                monitoringActive = true;
                fgNotification.setState(ForegroundNotification.State.MONITORING);
                fgNotification.startForeground();
                return START_STICKY;
            }
            return START_NOT_STICKY;
        }

        if (ACTION_START_MONITORING.equals(intent.getAction())) {
            monitoringActive = true;
            fgNotification.setState(currentState());
            fgNotification.startForeground();
            // Sent every time a trigger or the trusted-network list changes
            // (not just when arming from zero), so a toggle flip is acted on
            // immediately instead of waiting for the next network event.
            autoConnectController.onSettingsChanged();
            return START_STICKY;
        }
        if (ACTION_STOP_MONITORING.equals(intent.getAction())) {
            monitoringActive = false;
            if (engineRunner.isRunning()) {
                // Every trigger just got disabled — auto-connect is fully
                // off, so drop the connection it was governing too, rather
                // than leaving it running with nothing managing it anymore.
                requestDisconnect();
            } else {
                fgNotification.stopForeground();
                stopSelf();
            }
            return START_NOT_STICKY;
        }

        if (INTENT_ALWAYS_ON_START.equals(intent.getAction())) {
            // This fires only when Android's own system-level "Always-on VPN"
            // setting (Settings > Network > VPN > gear icon > Always-on VPN)
            // is turned on for this app — a separate OS feature from
            // AutoConnect's own triggers, and it ignores them entirely.
            Log.i(LOGTAG, "OS-level Always-on VPN start requested (independent of AutoConnect triggers)");
            // CONNECTING is only a safe assumption when the run below actually
            // starts the engine; on a re-delivery over a running engine no
            // event would follow to correct it, so read the state instead.
            fgNotification.setState(engineRunner.isRunning()
                    ? currentState()
                    : ForegroundNotification.State.CONNECTING);
            fgNotification.startForeground();
            engineRunner.runWithoutAuth();
        }
        if (INTENT_ACTION_START.equals(intent.getAction())) {
            // The quick settings tile sends this via startForegroundService,
            // which obliges us to call startForeground promptly. It arrives
            // whether or not the engine is about to connect, so take the state
            // from the engine: the Go core only re-emits onConnected on an
            // actual change, and assuming CONNECTING here would stick until
            // then.
            fgNotification.setState(currentState());
            fgNotification.startForeground();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return myBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(LOGTAG, "unbind from activity");
        if (!engineRunner.isRunning() && !monitoringActive) {
            stopSelf();
        }
        return false; // false means do not call onRebind
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOGTAG, "onDestroy");

        // Unregister broadcast receiver
        if (stopEngineReceiver != null) {
            try {
                unregisterReceiver(stopEngineReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(LOGTAG, "Receiver not registered", e);
            }
        }

        networkAvailabilityListener.unsubscribe();
        networkChangeDetector.unsubscribe(networkAvailabilityListener);
        networkChangeDetector.unsubscribe(autoConnectController);
        networkChangeDetector.unregisterNetworkCallback();

        engineRunner.stop();
        stopForeground(true);

        if (this.notifier != null) {
            this.notifier.removeRouteChangeListener(listener);
        }

        tunCreator.getHandler().getLooper().quitSafely();
    }

    @Override
    public void onRevoke() {
        Log.d(LOGTAG, "VPN permission on revoke");
        if (engineRunner != null) {
            engineRunner.stop();
            stopForeground(true);
        }
    }

    public Builder getBuilder() {
        return new Builder();
    }

    // AutoConnectHost — lets AutoConnectController drive the engine without
    // depending on VPNService internals directly.

    @Override
    public boolean isEngineRunning() {
        return engineRunner.isRunning();
    }

    @Override
    public boolean hasVpnConsent() {
        return VpnService.prepare(this) == null;
    }

    @Override
    public void requestConnect() {
        Log.i(LOGTAG, "AutoConnect: requestConnect() — starting engine");
        // Same null-URLOpener pattern NetbirdTileService already uses for
        // connects triggered outside an Activity: no interactive login flow,
        // just resumes the currently-active profile's session.
        ((MyLocalBinder) myBinder).runEngine(null, false);
    }

    @Override
    public void requestDisconnect() {
        Log.i(LOGTAG, "AutoConnect: requestDisconnect() — stopping engine");
        ((MyLocalBinder) myBinder).stopEngine();
    }

    @Override
    public void notifyConsentRequired() {
        AutoConnectConsentNotification.show(this);
    }

    public class MyLocalBinder extends Binder {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == IBinder.LAST_CALL_TRANSACTION) {
                onRevoke();
                return true;
            }
            return false;
        }

        public Intent prepareVpnIntent(Activity context) {
            return VpnService.prepare(context);
        }

        public void runEngine(URLOpener urlOpener, boolean isAndroidTV) {
            fgNotification.setState(ForegroundNotification.State.CONNECTING);
            fgNotification.startForeground();
            sessionNotification.cancel();
            engineRunner.run(urlOpener, isAndroidTV);
        }

        public void stopEngine() {
            engineRunner.stop();
        }

        public boolean isRunning() {
            return engineRunner.isRunning();
        }

        // Called on every activity bind. The notification may only be
        // re-posted while the service is already in the foreground: with the
        // engine stopped the service is not foreground, and a startForeground
        // from a bind-only, background-started service is rejected on
        // Android 12+ just like a background startService would be.
        public void refreshForegroundState() {
            fgNotification.setState(currentState());
            fgNotification.refreshIfActive();
        }

        public PeerInfoArray peersInfo() {
            return engineRunner.peersInfo();
        }

        @Nullable
        public NetworkArray networks() {
            return engineRunner.networks();
        }

        public void setConnectionStateListener(ConnectionListener listener) {
            engineRunner.setConnectionListener(listener);
        }

        public void removeConnectionStateListener() {
            engineRunner.removeStatusListener();
        }

        public void addServiceStateListener(ServiceStateListener serviceStateListener) {
            engineRunner.addServiceStateListener(serviceStateListener);
        }

        public void removeServiceStateListener(ServiceStateListener serviceStateListener) {
            engineRunner.removeServiceStateListener(serviceStateListener);
        }

        public void addSessionEventListener(SessionEventListener listener) {
            sessionMonitor.addListener(listener);
        }

        public void removeSessionEventListener(SessionEventListener listener) {
            sessionMonitor.removeListener(listener);
        }

        public void extendAuthSession(URLOpener urlOpener, boolean isAndroidTV, ErrListener resultListener) {
            engineRunner.extendAuthSession(urlOpener, isAndroidTV, resultListener);
        }

        public void cancelExtendAuthSession() {
            engineRunner.cancelExtendAuthSession();
        }

        /** SSO session deadline as unix seconds, or 0 when none is known. */
        public long sessionExpiresAt() {
            return engineRunner.sessionExpiresAt();
        }

        /** True while reconnecting requires an interactive login. */
        public boolean isLoginRequired() {
            return sessionMonitor.isLoginRequired();
        }

        public void addRouteChangeListener(RouteChangeListener listener) {
            if (VPNService.this.notifier != null) {
                VPNService.this.notifier.addRouteChangeListener(listener);
            }
        }

        public void removeRouteChangeListener(RouteChangeListener listener) {
            if (VPNService.this.notifier != null) {
                VPNService.this.notifier.removeRouteChangeListener(listener);
            }
        }

        public String debugBundle(boolean anonymize) throws Exception {
            return engineRunner.debugBundle(anonymize);
        }

        /**
         * Rebuilds the tunnel so a split tunnelling change takes hold without
         * asking the user to disconnect. A no-op while the engine is down: the
         * new selection is read when the tunnel is next created.
         */
        public void applySplitTunneling() {
            queueTUNRenewal(true);
        }

        public void selectRoute(String route) throws Exception {
            engineRunner.selectRoute(route);
        }

        public void deselectRoute(String route) throws Exception {
            engineRunner.deselectRoute(route);
        }

        public SSHClient newSSHClient() {
            // An SSH session dials through the tunnel, so a client is worthless
            // until the engine runs. Refusing one here surfaces the problem where
            // the user asked to connect, rather than inside the terminal.
            if (!engineRunner.isRunning()) {
                return null;
            }
            return engineRunner.newSSHClient();
        }
    }

    public static boolean isUsingAlwaysOnVPN(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network[] networks = connectivityManager.getAllNetworks();
        for (Network network : networks) {
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);

            if (networkCapabilities != null
                    && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    && (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                    || networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND))) {

                return true;
            }
        }
        return false;
    }

    private final SessionEventListener sessionEventListener = new SessionEventListener() {
        @Override
        public void onSessionExpiring(long expiresAtUnixSeconds, long leadMinutes, boolean finalWarning) {
            sessionNotification.showExpiring(leadMinutes);
        }

        @Override
        public void onSessionExpired() {
            sessionNotification.showExpired();
        }

        @Override
        public void onSessionDeadlineChanged(long expiresAtUnixSeconds) {
            fgNotification.updateSessionDeadline(expiresAtUnixSeconds);
        }
    };

    /**
     * The icon state implied by the engine's current status label, for the
     * moments we have to paint the notification without an event to react to
     * (re-entering the foreground). Mirrors the desktop tray's iconForState()
     * priority: login trouble first, then the connection phase.
     */
    private ForegroundNotification.State currentState() {
        String status = engineRunner.status();
        if (STATUS_NEEDS_LOGIN.equals(status)
                || STATUS_SESSION_EXPIRED.equals(status)
                || STATUS_LOGIN_FAILED.equals(status)) {
            return ForegroundNotification.State.NEEDS_LOGIN;
        }
        if (STATUS_CONNECTED.equals(status)) {
            return ForegroundNotification.State.CONNECTED;
        }
        if (STATUS_CONNECTING.equals(status)) {
            // The Go status label is never "NoNetwork" — the notifier only
            // overlays it on Connecting while the OS reports no network
            // (peer/notifier.go effectiveState), so apply the same rule here.
            return networkChangeDetector.hasInternetConnectivity()
                    ? ForegroundNotification.State.CONNECTING
                    : ForegroundNotification.State.NO_NETWORK;
        }
        // Idle — the run loop is not running (never started, or stopped) —
        // and anything the Go side may add later.
        return monitoringActive ? ForegroundNotification.State.MONITORING : ForegroundNotification.State.DISCONNECTED;
    }

    /**
     * Mirrors the tunnel's connection phase onto the status-bar icon. The
     * engine start/stop edges below are too coarse for this: the engine is
     * "started" long before the tunnel is actually up.
     */
    private final ConnectionListener connectionObserver = new ConnectionListener() {
        @Override
        public void onStateChanged(long state) {
            // Same split as MainActivity's listener: the legacy per-state
            // callbacks below drive the ordinary states, and only NoNetwork —
            // which arrives exclusively here — is handled from the state code.
            if (state == Android.ClientStateNoNetwork) {
                fgNotification.setState(ForegroundNotification.State.NO_NETWORK);
            }
        }

        @Override
        public void onConnecting() {
            fgNotification.setState(ForegroundNotification.State.CONNECTING);
        }

        @Override
        public void onConnected() {
            fgNotification.setState(ForegroundNotification.State.CONNECTED);
        }

        @Override
        public void onDisconnecting() {
        }

        @Override
        public void onDisconnected() {
            fgNotification.setState(monitoringActive
                    ? ForegroundNotification.State.MONITORING
                    : ForegroundNotification.State.DISCONNECTED);
        }

        @Override
        public void onAddressChanged(String fqdn, String ip) {
        }

        @Override
        public void onPeersListChanged(long count) {
        }
    };

    public ServiceStateListener serviceStateListener = new ServiceStateListener() {
        @Override
        public void onStarted() {
            sessionMonitor.onStateChanged();
        }

        @Override
        public void onStopped() {
            // Set before tearing the notification down: stopForeground can
            // leave the notification on screen briefly (and does leave it when
            // the service keeps running for a rebind), so it must not linger
            // showing the connected icon.
            //
            // An expired session stops the engine right after onError, so keep
            // the login prompt instead of overwriting it with a plain
            // "Disconnected" — the Go side latches NeedsLogin until an actual
            // login or extend clears it, so this stays true across the stop.
            boolean loginRequired = sessionMonitor.isLoginRequired();
            fgNotification.setState(loginRequired
                    ? ForegroundNotification.State.NEEDS_LOGIN
                    : (monitoringActive ? ForegroundNotification.State.MONITORING : ForegroundNotification.State.DISCONNECTED));
            if (loginRequired) {
                // Re-post rather than just refresh: onError() may not have
                // run first (or may have been a different error), so the
                // notification isn't guaranteed to already be active here.
                fgNotification.startForeground();
            } else if (monitoringActive) {
                // Keep the foreground notification up (as the monitoring
                // state) instead of tearing it down, so the service and its
                // network-callback registration stay alive to watch for the
                // next trigger.
                fgNotification.refreshIfActive();
            } else {
                fgNotification.stopForeground();
            }
            sessionMonitor.onStateChanged();
        }

        @Override
        public void onError(String msg) {
            // An expired session surfaces here first (the run loop gives up
            // with PermissionDenied), so sample the status right away instead
            // of waiting for the monitor's next tick.
            sessionMonitor.onStateChanged();
            // Same split the desktop tray makes: the NeedsLogin status label
            // means the user has to sign in, which is worth saying outright.
            // Anything else is a generic engine failure.
            boolean loginRequired = sessionMonitor.isLoginRequired();
            fgNotification.setState(loginRequired
                    ? ForegroundNotification.State.NEEDS_LOGIN
                    : (monitoringActive ? ForegroundNotification.State.MONITORING : ForegroundNotification.State.ERROR));
            if (loginRequired || monitoringActive) {
                // Keep the notification up (re-posting, not just refreshing,
                // in case it was somehow not already active) so the user has
                // something to tap back into login with, or so the service
                // stays alive to watch for the next trigger — the onStopped()
                // that usually follows this only refreshes an already-active
                // notification, so tearing it down here would hide the
                // prompt for good instead of just updating its text.
                fgNotification.startForeground();
            } else {
                fgNotification.stopForeground();
            }
        }
    };

    private final TUNCreatorLooperThread tunCreator = new TUNCreatorLooperThread(this::recreateTUN);

    private void queueTUNRenewal(String ignoredPayload) {
        queueTUNRenewal(false);
    }

    private void queueTUNRenewal(boolean force) {
        Handler handler = tunCreator.getHandler();
        var message = handler.obtainMessage(TUNCreatorLooperThread.MSG_RENEW_TUN);
        message.arg1 = force ? TUNCreatorLooperThread.ARG_FORCE : 0;
        boolean isQueued = handler.sendMessage(message);

        Log.d(LOGTAG, String.format("is TUN renewal queued? %b (forced: %b)", isQueued, force));
    }

    private void recreateTUN(boolean force) {
        if (!engineRunner.isRunning()) return;
        if (currentTUNParameters == null) return;

        // Pull the latest settings from the engine; the notification is only
        // a trigger and carries no state.
        TunSettings settings = engineRunner.getTunSettings();
        if (settings == null) return;

        String routes = settings.getRoutes();
        String searchDomains = settings.getSearchDomains();
        // A changed app filter leaves routes and search domains untouched, so the
        // usual guard would skip the very rebuild that applies it.
        if (!force && !currentTUNParameters.didChange(routes, searchDomains)) {
            return;
        }

        var iface = new IFace(VPNService.this);
        try {
            int fd = (int)iface.configureInterface(
                    currentTUNParameters.address,
                    currentTUNParameters.addressV6,
                    currentTUNParameters.mtu,
                    currentTUNParameters.dns,
                    searchDomains,
                    routes);

            if (fd != -1) {
                this.protect(fd);
                this.engineRunner.renewTUN(fd);
            }
        } catch (Exception e) {
            Log.e(LOGTAG, "failed to recreate tunnel after settings changed", e);
        }
    }

    public void setCurrentTUNParameters(TUNParameters currentTUNParameters) {
        this.currentTUNParameters = currentTUNParameters;
    }
}
