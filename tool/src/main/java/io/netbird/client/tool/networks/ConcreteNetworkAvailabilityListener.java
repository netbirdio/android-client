package io.netbird.client.tool.networks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ConcreteNetworkAvailabilityListener implements NetworkAvailabilityListener {
    private static final int UNKNOWN_NETWORK_TYPE = -1;
    private final Map<Integer, Boolean> availableNetworkTypes;
    private final BooleanSupplier shouldNotify;
    private final Consumer<Boolean> internetAvailabilityConsumer;
    private NetworkToggleListener listener;
    private volatile int lastDefaultType = UNKNOWN_NETWORK_TYPE;
    private volatile int currentTransport = UNKNOWN_NETWORK_TYPE;

    public ConcreteNetworkAvailabilityListener() {
        this(() -> true, available -> {});
    }

    public ConcreteNetworkAvailabilityListener(BooleanSupplier shouldNotify) {
        this(shouldNotify, available -> {});
    }

    // shouldNotify is consulted before each listener notification. Pass
    // engineRunner::isRunning to swallow the initial onAvailable burst that
    // fires right after registerNetworkCallback; until the engine is actually
    // running there is nothing to restart.
    //
    // internetAvailabilityConsumer receives transitions between "some
    // internet-capable network exists" and "none at all" (e.g. airplane mode).
    // It is invoked unconditionally so the Go client's network gate stays
    // correct regardless of engine state.
    public ConcreteNetworkAvailabilityListener(BooleanSupplier shouldNotify, Consumer<Boolean> internetAvailabilityConsumer) {
        this.availableNetworkTypes = new ConcurrentHashMap<>();
        this.shouldNotify = shouldNotify;
        this.internetAvailabilityConsumer = internetAvailabilityConsumer;
    }

    @Override
    public void onNetworkAvailable(@Constants.NetworkType int networkType) {
        boolean isNew = availableNetworkTypes.put(networkType, true) == null;
        if (!isNew) {
            return;
        }
        // Only notify when the new transport displaces the current one by
        // priority (WiFi > cellular). The default-network callback does not
        // fire for these while the VPN is the default.
        if (currentTransport == UNKNOWN_NETWORK_TYPE) {
            currentTransport = networkType;
        } else if (isHigherPriority(networkType, currentTransport)) {
            currentTransport = networkType;
            notifyListener();
        }
    }

    @Override
    public void onNetworkLost(@Constants.NetworkType int networkType) {
        availableNetworkTypes.remove(networkType);
        if (networkType == currentTransport) {
            currentTransport = highestAvailableTransport();
        }
    }

    @Override
    public void onInternetAvailabilityChanged(boolean available) {
        internetAvailabilityConsumer.accept(available);
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

    // WiFi > cellular > unknown
    private static boolean isHigherPriority(@Constants.NetworkType int candidate, int current) {
        if (candidate == Constants.NetworkType.WIFI) {
            return current != Constants.NetworkType.WIFI;
        }
        if (candidate == Constants.NetworkType.MOBILE) {
            return current != Constants.NetworkType.WIFI
                    && current != Constants.NetworkType.MOBILE;
        }
        return false;
    }

    private int highestAvailableTransport() {
        if (availableNetworkTypes.containsKey(Constants.NetworkType.WIFI)) {
            return Constants.NetworkType.WIFI;
        }
        if (availableNetworkTypes.containsKey(Constants.NetworkType.MOBILE)) {
            return Constants.NetworkType.MOBILE;
        }
        return UNKNOWN_NETWORK_TYPE;
    }
}
