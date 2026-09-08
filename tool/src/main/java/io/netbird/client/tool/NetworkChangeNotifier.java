package io.netbird.client.tool;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import io.netbird.gomobile.android.NetworkChangeListener;

public class NetworkChangeNotifier implements NetworkChangeListener {

    public static final String action = "action.NETWORK_CHANGED";

    private final Context context;

    private final CopyOnWriteArrayList<RouteChangeListener> routeChangeListeners;

    NetworkChangeNotifier(Context context) {
        this.context = context;
        this.routeChangeListeners = new CopyOnWriteArrayList<>();
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

    @Override
    public void setInterfaceIPv6(String ip) {
    }

    public void addRouteChangeListener(RouteChangeListener routeChangeListener) {
        Objects.requireNonNull(routeChangeListener);
        this.routeChangeListeners.addIfAbsent(routeChangeListener);
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