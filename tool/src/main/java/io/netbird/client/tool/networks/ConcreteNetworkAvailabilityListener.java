package io.netbird.client.tool.networks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConcreteNetworkAvailabilityListener implements NetworkAvailabilityListener {
    private final Map<Integer, Boolean> availableNetworkTypes;
    private NetworkToggleListener listener;

    public ConcreteNetworkAvailabilityListener() {
        this.availableNetworkTypes = new ConcurrentHashMap<>();
    }

    @Override
    public void onNetworkAvailable(@Constants.NetworkType int networkType) {
        boolean hadNetwork = !availableNetworkTypes.isEmpty();
        boolean hadSameType = Boolean.TRUE.equals(availableNetworkTypes.get(networkType));

        availableNetworkTypes.put(networkType, true);

        // Notify on any network type change:
        // - new WiFi connection (Mobile → WiFi switch)
        // - new Mobile connection when WiFi was lost (WiFi → Mobile switch)
        // - first network connection
        if (!hadSameType) {
            notifyListener();
        }
    }

    @Override
    public void onNetworkLost(@Constants.NetworkType int networkType) {
        boolean wasPresent = availableNetworkTypes.remove(networkType) != null;

        // Notify when a tracked network is lost and another type is still available.
        // Guards against duplicate/out-of-order onLost callbacks.
        if (wasPresent && !availableNetworkTypes.isEmpty()) {
            notifyListener();
        }
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onNetworkTypeChanged();
        }
    }

    public void subscribe(NetworkToggleListener listener) {
        this.listener = listener;
    }

    public void unsubscribe() {
        this.listener = null;
    }
}
