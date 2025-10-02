package io.netbird.client.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.netbird.gomobile.android.ConnectionListener;

public class ConnectionChangeNotifier implements ConnectionListener {
    private final List<ConnectionChangeListener> listeners;

    public ConnectionChangeNotifier() {
        this.listeners = Collections.synchronizedList(new ArrayList<>());
    }

    public void addConnectionChangeListener(ConnectionChangeListener listener) {
        Objects.requireNonNull(listener);

        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    public void removeConnectionChangeListener(ConnectionChangeListener listener) {
        Objects.requireNonNull(listener);

        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    @Override
    public void onAddressChanged(String host, String address) {
        synchronized (listeners) {
            for (var listener : listeners) {
                listener.onAddressChanged(host, address);
            }
        }
    }

    @Override
    public void onConnected() {
        synchronized (listeners) {
            for (var listener : listeners) {
                listener.onConnected();
            }
        }
    }

    @Override
    public void onConnecting() {
        synchronized (listeners) {
            for (var listener : listeners) {
                listener.onConnecting();
            }
        }
    }

    @Override
    public void onDisconnected() {
        synchronized (listeners) {
            for (var listener : listeners) {
                listener.onDisconnected();
            }
        }
    }

    @Override
    public void onDisconnecting() {
        synchronized (listeners) {
            for (var listener : listeners) {
                listener.onDisconnecting();
            }
        }
    }

    @Override
    public void onPeersListChanged(long size) {
        synchronized (listeners) {
            for (var listener : listeners) {
                listener.onPeersListChanged(size);
            }
        }
    }
}
