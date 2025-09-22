package io.netbird.client.tool;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.netbird.gomobile.android.NetworkChangeListener;

public class NetworkChangeNotifier implements NetworkChangeListener {

    public static final String action = "action.NETWORK_CHANGED";

    private final Context context;

    private RouteChangeListener routeChangeListener;

    NetworkChangeNotifier(Context context) {
        this.context = context;
    }

    @Override
    public void onNetworkChanged(String routes) {
        if (routes != null) {
            routes = routes.replace(",", ";");
        }

        if (this.routeChangeListener != null) {
            this.routeChangeListener.onRouteChanged(routes);
        }

        sendBroadcast(routes);
    }

    @Override
    public void setInterfaceIP(String ip) {

    }

    public void setRouteChangeListener(RouteChangeListener routeChangeListener) {
        this.routeChangeListener = routeChangeListener;
    }

    private void sendBroadcast(String routes) {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("routes", routes);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}