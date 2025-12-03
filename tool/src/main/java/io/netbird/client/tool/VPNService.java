package io.netbird.client.tool;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import androidx.annotation.Nullable;

import io.netbird.client.tool.networks.ConcreteNetworkAvailabilityListener;
import io.netbird.client.tool.networks.NetworkChangeDetector;
import io.netbird.gomobile.android.ConnectionListener;
import io.netbird.gomobile.android.NetworkArray;
import io.netbird.gomobile.android.PeerInfoArray;
import io.netbird.gomobile.android.URLOpener;


public class VPNService extends android.net.VpnService {
    private final static String LOGTAG = "service";
    public static final String INTENT_ACTION_START = "io.netbird.client.intent.action.START_SERVICE";
    public static final String ACTION_STOP_ENGINE = "io.netbird.client.intent.action.STOP_ENGINE";
    private static final String INTENT_ALWAYS_ON_START = "android.net.VpnService";
    private final IBinder myBinder = new MyLocalBinder();
    private EngineRunner engineRunner;
    private ForegroundNotification fgNotification;
    private TUNParameters currentTUNParameters;
    private NetworkChangeNotifier notifier;

    private RouteChangeListener listener;

    private NetworkChangeDetector networkChangeDetector;
    private ConcreteNetworkAvailabilityListener networkAvailabilityListener;
    private EngineRestarter engineRestarter;
    private android.content.BroadcastReceiver stopEngineReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOGTAG, "onCreate");

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

        // Create network availability listener before initializing engine
        networkAvailabilityListener = new ConcreteNetworkAvailabilityListener();


        engineRunner = new EngineRunner(this, notifier, tunAdapter, iFaceDiscover, versionName,
                preferences.isTraceLogEnabled(), Version.isDebuggable(this), profileManager);
        engineRunner.addServiceStateListener(serviceStateListener);

        engineRestarter = new EngineRestarter(engineRunner);
        networkAvailabilityListener.subscribe(engineRestarter);

        networkChangeDetector = new NetworkChangeDetector(
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE));
        networkChangeDetector.subscribe(networkAvailabilityListener);
        networkChangeDetector.registerNetworkCallback();

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
            return START_NOT_STICKY;
        }

        if (INTENT_ALWAYS_ON_START.equals(intent.getAction())) {
            fgNotification.startForeground();
            engineRunner.runWithoutAuth();
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
        if (!engineRunner.isRunning()) {
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
        networkChangeDetector.unsubscribe();
        networkChangeDetector.unregisterNetworkCallback();
        engineRestarter.cleanup();

        engineRunner.stop();
        stopForeground(true);

        if (this.notifier != null) {
            this.notifier.removeRouteChangeListener(listener);
        }

        if (tunCreator != null) {
            tunCreator.getHandler().getLooper().quitSafely();
            tunCreator = null;
        }
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
            fgNotification.startForeground();
            engineRunner.run(urlOpener, isAndroidTV);
        }

        public void stopEngine() {
            engineRunner.stop();
        }

        public PeerInfoArray peersInfo() {
            return engineRunner.peersInfo();
        }

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

        public void selectRoute(String route) throws Exception {
            engineRunner.selectRoute(route);
        }

        public void deselectRoute(String route) throws Exception {
            engineRunner.deselectRoute(route);
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

    public ServiceStateListener serviceStateListener = new ServiceStateListener() {
        @Override
        public void onStarted() {

        }

        @Override
        public void onStopped() {
            fgNotification.stopForeground();
        }

        @Override
        public void onError(String msg) {
            fgNotification.stopForeground();
        }
    };

    private TUNCreatorLooperThread tunCreator;

    private void queueTUNRenewal(String routes) {
        if (tunCreator == null) {
            tunCreator = new TUNCreatorLooperThread(this::recreateTUN);
            tunCreator.setPriority(Thread.MAX_PRIORITY);
            tunCreator.start();
        }

        var message = tunCreator.getHandler().obtainMessage(1, routes);
        boolean isQueued = tunCreator.getHandler().sendMessage(message);

        Log.d(LOGTAG, String.format("is TUN renewal queued? %b", isQueued));
    }

    private void recreateTUN(String routes) {
        if (!engineRunner.isRunning()) return;

        // Renew TUN file descriptor if routes changed.
        if (currentTUNParameters != null && currentTUNParameters.didRoutesChange(routes)) {
            var iface = new IFace(VPNService.this);

            try {
                int fd = (int)iface.configureInterface(
                        currentTUNParameters.address,
                        currentTUNParameters.mtu,
                        currentTUNParameters.dns,
                        currentTUNParameters.searchDomainsString,
                        routes);

                if (fd != -1) {
                    this.protect(fd);
                    this.engineRunner.renewTUN(fd);
                }
            } catch (Exception e) {
                Log.e(LOGTAG, "failed to recreate tunnel after route changed", e);
            }
        }
    }

    public void setCurrentTUNParameters(TUNParameters currentTUNParameters) {
        this.currentTUNParameters = currentTUNParameters;
    }
}
