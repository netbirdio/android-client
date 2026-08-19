package io.netbird.client.tool.networks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ConcreteNetworkAvailabilityListener implements NetworkAvailabilityListener {
    private static final int UNKNOWN_NETWORK_TYPE = -1;
    private final Map<Integer, Boolean> availableNetworkTypes;
    private final Map<Integer, Boolean> validatedNetworkTypes;
    private final BooleanSupplier shouldNotify;
    private final Consumer<Boolean> internetAvailabilityConsumer;
    private NetworkToggleListener listener;
    private volatile int lastDefaultType = UNKNOWN_NETWORK_TYPE;
    private volatile int activeValidatedType = UNKNOWN_NETWORK_TYPE;

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
        this.validatedNetworkTypes = new ConcurrentHashMap<>();
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
        validatedNetworkTypes.remove(networkType);
        // If the lost transport was the active one, recompute the active
        // validated transport; a transition here means the phone fell back to
        // a different network (e.g. WiFi lost -> cellular).
        if (networkType == activeValidatedType) {
            int previous = activeValidatedType;
            recomputeActiveValidatedType();
            if (activeValidatedType != previous && activeValidatedType != UNKNOWN_NETWORK_TYPE) {
                notifyListener();
            }
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

    @Override
    public void onNetworkValidated(@Constants.NetworkType int networkType, boolean validated) {
        if (validated) {
            validatedNetworkTypes.put(networkType, true);
        } else {
            validatedNetworkTypes.remove(networkType);
        }

        // Recompute the active validated transport, only an actual change
        // in this active transport warrants notifying the Go core.
        // A secondary transport merely becoming validated (e.g. enabling
        // cellular data while WiFi is already up and validated) does not
        // change which network the phone is actually using.
        int previous = activeValidatedType;
        recomputeActiveValidatedType();
        if (activeValidatedType != previous && activeValidatedType != UNKNOWN_NETWORK_TYPE) {
            if (previous != UNKNOWN_NETWORK_TYPE) {
                notifyListener();
            }
        }
    }

    // Determines the highest-priority transport that is both available and
    // validated. WiFi outranks Cellular because Android always prefers WiFi
    // when it has internet validation.
    private void recomputeActiveValidatedType() {
        if (isTransportValidated(Constants.NetworkType.WIFI)) {
            activeValidatedType = Constants.NetworkType.WIFI;
        } else if (isTransportValidated(Constants.NetworkType.MOBILE)) {
            activeValidatedType = Constants.NetworkType.MOBILE;
        } else {
            activeValidatedType = UNKNOWN_NETWORK_TYPE;
        }
    }

    private boolean isTransportValidated(@Constants.NetworkType int networkType) {
        return availableNetworkTypes.containsKey(networkType)
                && validatedNetworkTypes.containsKey(networkType);
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
