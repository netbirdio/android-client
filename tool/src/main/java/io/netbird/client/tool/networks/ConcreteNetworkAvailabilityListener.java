package io.netbird.client.tool.networks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public class ConcreteNetworkAvailabilityListener implements NetworkAvailabilityListener {
    private static final int UNKNOWN_NETWORK_TYPE = -1;
    private final Map<Integer, Boolean> availableNetworkTypes;
    private final BooleanSupplier shouldNotify;
    private NetworkToggleListener listener;
    private volatile int lastDefaultType = UNKNOWN_NETWORK_TYPE;

    public ConcreteNetworkAvailabilityListener() {
        this(() -> true);
    }

    // shouldNotify is consulted before each listener notification. Pass
    // engineRunner::isRunning to swallow the initial onAvailable burst that
    // fires right after registerNetworkCallback; until the engine is actually
    // running there is nothing to restart.
    public ConcreteNetworkAvailabilityListener(BooleanSupplier shouldNotify) {
        this.availableNetworkTypes = new ConcurrentHashMap<>();
        this.shouldNotify = shouldNotify;
    }

    @Override
    public void onNetworkAvailable(@Constants.NetworkType int networkType) {
        availableNetworkTypes.put(networkType, true);
    }

    @Override
    public void onNetworkLost(@Constants.NetworkType int networkType) {
        availableNetworkTypes.remove(networkType);
    }

    @Override
    public void onDefaultNetworkTypeChanged(@Constants.NetworkType int networkType) {
        if (networkType == lastDefaultType) {
            return;
        }
        int previous = lastDefaultType;
        lastDefaultType = networkType;
        if (previous == UNKNOWN_NETWORK_TYPE) {
            // first observation after subscribe; not a real transition
            return;
        }
        notifyListener();
    }

    private void notifyListener() {
        NetworkToggleListener l = listener;
        if (l == null) {
            return;
        }
        if (!shouldNotify.getAsBoolean()) {
            return;
        }
        l.onNetworkTypeChanged();
    }

    public void subscribe(NetworkToggleListener listener) {
        this.listener = listener;
    }

    public void unsubscribe() {
        this.listener = null;
    }
}
