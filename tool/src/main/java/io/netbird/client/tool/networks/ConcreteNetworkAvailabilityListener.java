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
        Boolean wasAvailable = availableNetworkTypes.put(networkType, true);

        // Always notify on any network change so the engine re-syncs
        // NetworkAddresses with the management server. This ensures
        // posture checks see the current network (e.g. WiFi subnet)
        // immediately after a network switch.
        if (!Boolean.TRUE.equals(wasAvailable)) {
            notifyListener();
        }
    }

    @Override
    public void onNetworkLost(@Constants.NetworkType int networkType) {
        availableNetworkTypes.remove(networkType);

        // Notify on any network loss if another network is still available,
        // so the engine re-syncs with updated NetworkAddresses.
        if (!availableNetworkTypes.isEmpty()) {
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
