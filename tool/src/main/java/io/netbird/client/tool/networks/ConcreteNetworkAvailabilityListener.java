package io.netbird.client.tool.networks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConcreteNetworkAvailabilityListener implements NetworkAvailabilityListener {
    // Grace window after subscribing a listener during which Android's initial
    // onAvailable burst is treated as state seeding, not as a transition.
    private static final long INITIAL_BURST_GRACE_MS = 3000;

    private final Map<Integer, Boolean> availableNetworkTypes;
    private NetworkToggleListener listener;
    private volatile long listenerSubscribedAt = 0;

    public ConcreteNetworkAvailabilityListener() {
        this.availableNetworkTypes = new ConcurrentHashMap<>();
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
        // Skip Android's initial onAvailable burst that fires right after the
        // NetworkCallback is registered; that is the current state, not a
        // transition, and must not trigger an engine restart.
        long subscribedAt = listenerSubscribedAt;
        if (subscribedAt != 0 && System.currentTimeMillis() - subscribedAt < INITIAL_BURST_GRACE_MS) {
            return;
        }
        l.onNetworkTypeChanged();
    }

    public void subscribe(NetworkToggleListener listener) {
        this.listener = listener;
        this.listenerSubscribedAt = System.currentTimeMillis();
    }

    public void unsubscribe() {
        this.listener = null;
        this.listenerSubscribedAt = 0;
    }
}
