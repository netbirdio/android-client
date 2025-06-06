package io.netbird.client.tool;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.netbird.gomobile.android.NetworkChangeListener;

public class NetworkChangeNotifier implements NetworkChangeListener {

    public static final String action = "action.NETWORK_CHANGED";

    private final Context context;

    NetworkChangeNotifier(Context context) {
        this.context = context;
    }

    @Override
    public void onNetworkChanged(String routes) {
        sendBroadcast();
    }

    @Override
    public void setInterfaceIP(String ip) {

    }

    private void sendBroadcast() {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}