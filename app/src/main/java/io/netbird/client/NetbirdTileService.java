package io.netbird.client;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import io.netbird.client.tool.ServiceStateListener;
import io.netbird.client.tool.VPNService;

public class NetbirdTileService extends TileService {

    private static final String TAG = "NetbirdTileService";
    private VPNService.MyLocalBinder mBinder;
    private boolean isBound = false;
    private boolean isBinding = false;
    private boolean pendingClick = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mBinder = (VPNService.MyLocalBinder) binder;
            isBound = true;
            isBinding = false;
            mBinder.addServiceStateListener(serviceStateListener);
            updateTile();

            if (pendingClick) {
                pendingClick = false;
                handleToggle();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBinder = null;
            isBound = false;
            isBinding = false;
            updateTile();
        }
    };

    private final ServiceStateListener serviceStateListener = new ServiceStateListener() {
        @Override
        public void onStarted() {
            mainHandler.post(NetbirdTileService.this::updateTile);
        }

        @Override
        public void onStopped() {
            mainHandler.post(NetbirdTileService.this::updateTile);
        }

        @Override
        public void onError(String msg) {
            mainHandler.post(NetbirdTileService.this::updateTile);
        }
    };

    @Override
    public void onStartListening() {
        super.onStartListening();
        Log.d(TAG, "onStartListening");
        bindToVpnService();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        Log.d(TAG, "onStopListening");
        unbindFromVpnService();
    }

    @Override
    public void onClick() {
        super.onClick();
        Log.d(TAG, "onClick");

        if (VpnService.prepare(this) != null) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                startActivityAndCollapse(pendingIntent);
            } else {
                startActivityAndCollapse(intent);
            }
            return;
        }

        handleToggle();
    }

    private void handleToggle() {
        if (mBinder != null) {
            if (mBinder.isRunning()) {
                mBinder.stopEngine();
            } else {
                startVpnService();
                mBinder.runEngine(null, false);
            }
        } else if (!isBinding) {
            pendingClick = true;
            startAndRunVpnService();
        } else {
            pendingClick = true;
        }
    }

    private void bindToVpnService() {
        Intent intent = new Intent(this, VPNService.class);
        isBinding = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        if (!isBinding) {
            pendingClick = false;
            Log.w(TAG, "bindService failed");
        }
    }

    private void unbindFromVpnService() {
        if (isBound || isBinding) {
            if (mBinder != null) {
                mBinder.removeServiceStateListener(serviceStateListener);
            }
            try {
                unbindService(serviceConnection);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Service not bound", e);
            }
            isBound = false;
            isBinding = false;
            mBinder = null;
        }
        pendingClick = false;
    }

    private void startAndRunVpnService() {
        Intent intent = new Intent(this, VPNService.class);
        intent.setAction(VPNService.INTENT_ACTION_START);
        startForegroundService(intent);
        bindToVpnService();
    }

    private void startVpnService() {
        Intent intent = new Intent(this, VPNService.class);
        intent.setAction(VPNService.INTENT_ACTION_START);
        startForegroundService(intent);
    }

    private boolean isVpnRunning() {
        return mBinder != null && isBound && mBinder.isRunning();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        boolean running = isVpnRunning();

        tile.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
