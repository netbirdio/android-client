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

import io.netbird.gomobile.android.ConnectionListener;
import io.netbird.gomobile.android.PeerInfoArray;
import io.netbird.gomobile.android.URLOpener;


public class VPNService extends android.net.VpnService {
    private final static String LOGTAG = "service";
    public static final String INTENT_ACTION_START = "io.netbird.client.intent.action.START_SERVICE";
    private static final String INTENT_ALWAYS_ON_START = "android.net.VpnService";
    static public final int VPN_REQUEST_CODE = 0;


    private final IBinder myBinder = new MyLocalBinder();

    private EngineRunner engineRunner;
    private ForegroundNotification fgNotification;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOGTAG, "onCreate");
        engineRunner = new EngineRunner(this);
        fgNotification = new ForegroundNotification(this);
        engineRunner.addServiceStateListener(serviceStateListener);
    }

    @Override
    public int onStartCommand(@Nullable final Intent intent, final int flags, final int startId) {
        Log.d(LOGTAG, "onStartCommand");
        if (intent == null) {
            return START_NOT_STICKY;
        }

        if(INTENT_ALWAYS_ON_START.equals(intent.getAction())) {
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
    public boolean onUnbind (Intent intent) {
        Log.d(LOGTAG, "unbind from activity");
        if(!engineRunner.isRunning()) {
            stopSelf();
        }
        return false; // false means do not call onRebind
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOGTAG, "onDestroy");
        engineRunner.stop();
        stopForeground(true);
    }

    @Override
    public void onRevoke() {
        Log.d(LOGTAG, "VPN permission on revoke");
        if(engineRunner!=null) {
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

        public void runEngine(URLOpener urlOpener) {
            fgNotification.startForeground();
            engineRunner.run(urlOpener);
        }

        public void stopEngine() {
            engineRunner.stop();
        }

        public PeerInfoArray peersInfo() {
            return engineRunner.peersInfo();
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
}
