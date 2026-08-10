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
        availableNetworkTypes.put(networkType, true);
    }

    @Override
    public void onNetworkLost(@Constants.NetworkType int networkType) {
        availableNetworkTypes.remove(networkType);
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
}
