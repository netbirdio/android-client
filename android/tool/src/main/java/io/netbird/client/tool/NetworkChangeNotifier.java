package io.netbird.client.tool;

import android.content.Context;
import android.content.Intent;

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
        Intent intent = new Intent();
        intent.setAction(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.sendBroadcast(intent);
    }
}