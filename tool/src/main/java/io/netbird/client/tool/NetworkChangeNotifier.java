package io.netbird.client.tool;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.netbird.gomobile.android.NetworkChangeListener;

public class NetworkChangeNotifier implements NetworkChangeListener {

    public static final String action = "action.NETWORK_CHANGED";

    private final Context context;

    private final List<RouteChangeListener> routeChangeListeners;

    NetworkChangeNotifier(Context context) {
        this.context = context;
        this.routeChangeListeners = new ArrayList<>();
    }

    @Override
    public void onNetworkChanged(String routes) {
        if (routes != null) {
            routes = routes.replace(",", ";");
        }

        for (var listener : routeChangeListeners) {
            listener.onRouteChanged(routes);
        }

        sendBroadcast(routes);
    }

    @Override
    public void setInterfaceIP(String ip) {

    }

    public void addRouteChangeListener(RouteChangeListener routeChangeListener) {
        Objects.requireNonNull(routeChangeListener);

        if (!this.routeChangeListeners.contains(routeChangeListener)) {
            this.routeChangeListeners.add(routeChangeListener);
        }
    }

    public void removeRouteChangeListener(RouteChangeListener routeChangeListener) {
        Objects.requireNonNull(routeChangeListener);
        this.routeChangeListeners.remove(routeChangeListener);
    }

    private void sendBroadcast(String routes) {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("routes", routes);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}