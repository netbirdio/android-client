package io.netbird.client.tool.networks;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkChangeDetector {
    private static final String LOGTAG = NetworkChangeDetector.class.getSimpleName();
    // Transport we do not classify (e.g. ethernet, bluetooth tethering); such
    // networks still count as internet connectivity.
    private static final int TYPE_UNCLASSIFIED = -1;

    private final ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConnectivityManager.NetworkCallback defaultNetworkCallback;
    private volatile NetworkAvailabilityListener listener;
    private boolean defaultNetworkCallbackActive = false;
    private final Object networkCallbackLock = new Object();

    // Networks currently matching the registered request (internet-capable,
    // non-VPN), keyed by the Network object so onLost can be resolved even
    // though the lost network's capabilities are no longer queryable.
    private final Map<Network, Integer> availableNetworks = new ConcurrentHashMap<>();
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
        return TYPE_UNCLASSIFIED;
    }

    private void initNetworkCallback() {
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                int type = classifyTransport(network);
                availableNetworks.put(network, type);

                NetworkAvailabilityListener localListener = listener;
                if (localListener != null && type != TYPE_UNCLASSIFIED) {
                    localListener.onNetworkAvailable(type);
                }
                updateInternetAvailability();
            }

            @Override
            public void onLost(@NonNull Network network) {
                Integer type = availableNetworks.remove(network);

                NetworkAvailabilityListener localListener = listener;
                if (localListener != null && type != null && type != TYPE_UNCLASSIFIED) {
                    localListener.onNetworkLost(type);
                }
                updateInternetAvailability();
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities);

                Log.d(LOGTAG, String.format("Network %s had their capabilities changed: %s", network, networkCapabilities));
            }
        };
    }

    // updateInternetAvailability notifies the listener when the device
    // transitions between having some internet-capable network and none.
    private void updateInternetAvailability() {
        boolean available = !availableNetworks.isEmpty();
        synchronized (internetStateLock) {
            if (available == internetAvailable) {
                return;
            }
            internetAvailable = available;
        }
        Log.i(LOGTAG, "internet availability changed: " + available);
        NetworkAvailabilityListener localListener = listener;
        if (localListener != null) {
            localListener.onInternetAvailabilityChanged(available);
        }
    }

    public boolean hasInternetConnectivity() {
        synchronized (internetStateLock) {
            return internetAvailable;
        }
    }

    private void initDefaultNetworkCallback() {
        defaultNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                NetworkAvailabilityListener listenerToNotify = null;
                int notifyType = 0;
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
                        listenerToNotify = listener;
                        notifyType = Constants.NetworkType.WIFI;
                    } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        listenerToNotify = listener;
                        notifyType = Constants.NetworkType.MOBILE;
                    }
                    Log.d(LOGTAG, "default network became " + network);
                }
                if (listenerToNotify != null) {
                    listenerToNotify.onDefaultNetworkTypeChanged(notifyType);
                }
            }
        };
    }

    public void registerNetworkCallback() {
        // Seed the availability state before callbacks arrive: when the device
        // starts with no connectivity at all (e.g. airplane mode), no
        // onAvailable ever fires, so the initial value must already be correct.
        synchronized (internetStateLock) {
            internetAvailable = connectivityManager.getActiveNetwork() != null;
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
    }

    public void subscribe(NetworkAvailabilityListener listener) {
        this.listener = listener;
    }

    public void unsubscribe() {
        this.listener = null;
    }
}
