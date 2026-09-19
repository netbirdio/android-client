package io.netbird.client.tool.networks;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class NetworkChangeDetector {
    private static final String LOGTAG = NetworkChangeDetector.class.getSimpleName();
    // Transport we do not classify (e.g. bluetooth tethering); such networks
    // still count as internet connectivity.
    private static final int TYPE_UNCLASSIFIED = -1;

    private final ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConnectivityManager.NetworkCallback defaultNetworkCallback;
    private final List<NetworkAvailabilityListener> listeners = new CopyOnWriteArrayList<>();
    private boolean defaultNetworkCallbackActive = false;
    private final Object networkCallbackLock = new Object();

    // Networks currently matching the registered request (internet-capable,
    // non-VPN), keyed by the Network object so onLost can be resolved even
    // though the lost network's capabilities are no longer queryable.
    private final Map<Network, Integer> availableNetworks = new ConcurrentHashMap<>();
    // Tracks the NET_CAPABILITY_VALIDATED state per Network so we only fire
    // onNetworkValidated on actual true<->false transitions, not on every
    // capabilities update Android sends us.
    private final Map<Network, Boolean> validatedNetworks = new ConcurrentHashMap<>();
    private final Object internetStateLock = new Object();
    private boolean internetAvailable = true;

    public NetworkChangeDetector(ConnectivityManager connectivityManager) {
        this.connectivityManager = connectivityManager;
        initNetworkCallback();
        initDefaultNetworkCallback();
    }

    private int classifyTransport(Network network) {
        var capabilities = connectivityManager.getNetworkCapabilities(network);
        if (capabilities == null) return TYPE_UNCLASSIFIED;

        Log.d(LOGTAG, String.format("Network %s has capabilities: %s", network, capabilities));

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return Constants.NetworkType.WIFI;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return Constants.NetworkType.MOBILE;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return Constants.NetworkType.ETHERNET;
        }
        return TYPE_UNCLASSIFIED;
    }

    private void initNetworkCallback() {
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                int type = classifyTransport(network);
                availableNetworks.put(network, type);

                // Seed and forward the validation state so onCapabilitiesChanged
                // can detect subsequent transitions.
                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
                boolean validated = caps != null
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                Boolean wasValidated = validatedNetworks.put(network, validated);

                if (type != TYPE_UNCLASSIFIED) {
                    for (NetworkAvailabilityListener l : listeners) {
                        l.onNetworkAvailable(type);
                        if (wasValidated == null || wasValidated != validated) {
                            l.onNetworkValidated(type, validated);
                        }
                    }
                }
                updateInternetAvailability();
            }

            @Override
            public void onLost(@NonNull Network network) {
                Integer type = availableNetworks.remove(network);
                validatedNetworks.remove(network);

                // During a same-type handover the replacement network is
                // already tracked when the old one drops; the transport
                // itself is not lost, so do not report it as such.
                if (type != null && type != TYPE_UNCLASSIFIED
                        && !availableNetworks.containsValue(type)) {
                    for (NetworkAvailabilityListener l : listeners) {
                        l.onNetworkLost(type);
                    }
                }
                updateInternetAvailability();
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities);

                Log.d(LOGTAG, String.format("Network %s had their capabilities changed: %s", network, networkCapabilities));

                // Detect validation state transitions and forward them.
                // Unlike onAvailable, this fires only after Android has
                // confirmed the network actually reaches the internet.
                boolean validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                Boolean wasValidated = validatedNetworks.put(network, validated);

                Integer type = availableNetworks.get(network);
                if (type != null && type != TYPE_UNCLASSIFIED
                        && (wasValidated == null || wasValidated != validated)) {
                    for (NetworkAvailabilityListener l : listeners) {
                        l.onNetworkValidated(type, validated);
                    }
                }
            }
        };
    }

    // updateInternetAvailability notifies the listener when the device
    // transitions between having some internet-capable network and none.
    //
    // Availability deliberately requires only NET_CAPABILITY_INTERNET, not
    // NET_CAPABILITY_VALIDATED: validation lands seconds after every
    // transport switch and can be revoked transiently on weak networks, so
    // gating on it would suspend the Go reconnect loops and flash NO_NETWORK
    // in the middle of exactly the handovers the fast path is built for. The
    // accepted cost is that a captive-portal network counts as internet until
    // its login completes, so the client shows Connecting there instead of
    // NO_NETWORK.
    private void updateInternetAvailability() {
        boolean available = !availableNetworks.isEmpty();
        synchronized (internetStateLock) {
            if (available == internetAvailable) {
                return;
            }
            internetAvailable = available;
        }
        Log.i(LOGTAG, "internet availability changed: " + available);
        for (NetworkAvailabilityListener l : listeners) {
            l.onInternetAvailabilityChanged(available);
        }
    }

    public boolean hasInternetConnectivity() {
        synchronized (internetStateLock) {
            return internetAvailable;
        }
    }

    // Synchronous snapshot used only for seeding: getAllNetworks() is
    // deprecated in favor of callback tracking, but the seed runs before any
    // callback can have fired and there is no other synchronous enumeration.
    private boolean hasNonVpnInternetNetwork() {
        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
            if (caps == null) {
                continue;
            }
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                return true;
            }
        }
        return false;
    }

    private void initDefaultNetworkCallback() {
        defaultNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                boolean notify = false;
                int notifyType = 0;
                long notifyHandle = network.getNetworkHandle();
                synchronized (networkCallbackLock) {
                    if (!defaultNetworkCallbackActive) {
                        Log.d(LOGTAG, "ignoring onAvailable for " + network + "; default callback inactive");
                        return;
                    }
                    NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
                    if (caps == null) {
                        Log.w(LOGTAG, "default network " + network + " has no capabilities");
                        return;
                    }
                    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                        Log.d(LOGTAG, "default network " + network + " is a VPN; ignoring");
                        return;
                    }
                    // The default-network signal is the authoritative source of
                    // the active transport type; the per-network onAvailable/onLost
                    // pairing can miss seamless WiFi→cellular→WiFi handovers.
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        notify = true;
                        notifyType = Constants.NetworkType.WIFI;
                    } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        notify = true;
                        notifyType = Constants.NetworkType.MOBILE;
                    } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        notify = true;
                        notifyType = Constants.NetworkType.ETHERNET;
                    }
                    Log.d(LOGTAG, "default network became " + network);
                }
                if (notify) {
                    for (NetworkAvailabilityListener l : listeners) {
                        l.onDefaultNetworkTypeChanged(notifyType, notifyHandle);
                    }
                }
            }
        };
    }

    public void registerNetworkCallback() {
        // Seed the availability state before callbacks arrive: when the device
        // starts with no connectivity at all (e.g. airplane mode), no
        // onAvailable ever fires, so the initial value must already be correct.
        // Use the same criteria as the registered request below: our own VPN
        // network is excluded, so an up tunnel cannot mask a missing
        // underlying network.
        synchronized (internetStateLock) {
            internetAvailable = hasNonVpnInternetNetwork();
        }

        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        connectivityManager.registerNetworkCallback(builder.build(), networkCallback);
        synchronized (networkCallbackLock) {
            defaultNetworkCallbackActive = true;
            connectivityManager.registerDefaultNetworkCallback(defaultNetworkCallback);
        }
    }

    public void unregisterNetworkCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception e) {
            Log.e(LOGTAG, "failed to unregister network callback", e);
        }
        synchronized (networkCallbackLock) {
            defaultNetworkCallbackActive = false;
            try {
                connectivityManager.unregisterNetworkCallback(defaultNetworkCallback);
            } catch (Exception e) {
                Log.e(LOGTAG, "failed to unregister default network callback", e);
            }
        }
        availableNetworks.clear();
        validatedNetworks.clear();
    }

    public void subscribe(NetworkAvailabilityListener listener) {
        listeners.add(listener);
    }

    public void unsubscribe(NetworkAvailabilityListener listener) {
        listeners.remove(listener);
    }
}
