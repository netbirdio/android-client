package io.netbird.client.tool.networks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public class ConcreteNetworkAvailabilityListener implements NetworkAvailabilityListener {
    private final Map<Integer, Boolean> availableNetworkTypes;
    private final BooleanSupplier shouldNotify;
    private NetworkToggleListener listener;

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
        boolean isWifiAvailable = Boolean.TRUE.equals(availableNetworkTypes.get(Constants.NetworkType.WIFI));

        availableNetworkTypes.put(networkType, true);

        // if wifi is available and wasn't before, notifies listener.
        // Android prioritizes wifi over mobile data network by default.
        if (!isWifiAvailable && networkType == Constants.NetworkType.WIFI) {
            notifyListener();
        }
    }

    @Override
    public void onNetworkLost(@Constants.NetworkType int networkType) {
        boolean isMobileAvailable = Boolean.TRUE.equals(availableNetworkTypes.get(Constants.NetworkType.MOBILE));

        availableNetworkTypes.remove(networkType);

        // if wifi is lost and mobile data is available, notifies listener.
        // No use to notify it if there's no other type of network available.
        if (isMobileAvailable && networkType == Constants.NetworkType.WIFI) {
            notifyListener();
        }
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
