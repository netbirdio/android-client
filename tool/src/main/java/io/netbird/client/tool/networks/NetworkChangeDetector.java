package io.netbird.client.tool.networks;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

public class NetworkChangeDetector {
    private static final String LOGTAG = NetworkChangeDetector.class.getSimpleName();
    private final ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConnectivityManager.NetworkCallback defaultNetworkCallback;
    private volatile NetworkAvailabilityListener listener;

    public NetworkChangeDetector(ConnectivityManager connectivityManager) {
        this.connectivityManager = connectivityManager;
        initNetworkCallback();
        initDefaultNetworkCallback();
    }

    private void checkNetworkCapabilities(Network network, Consumer<Integer> operation) {
        var capabilities = connectivityManager.getNetworkCapabilities(network);
        if (capabilities == null) return;

        Log.d(LOGTAG, String.format("Network %s has capabilities: %s", network, capabilities));

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            operation.accept(Constants.NetworkType.WIFI);
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            operation.accept(Constants.NetworkType.MOBILE);
        }
    }

    private void initNetworkCallback() {
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                NetworkAvailabilityListener localListener = listener;
                if (localListener == null) return;
                checkNetworkCapabilities(network, localListener::onNetworkAvailable);
            }

            @Override
            public void onLost(@NonNull Network network) {
                NetworkAvailabilityListener localListener = listener;
                if (localListener == null) return;
                checkNetworkCapabilities(network, localListener::onNetworkLost);
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities);

                Log.d(LOGTAG, String.format("Network %s had their capabilities changed: %s", network, networkCapabilities));
            }
        };
    }

    private void initDefaultNetworkCallback() {
        defaultNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.d(LOGTAG, "default network became " + network + ", binding process to it");
                try {
                    if (!connectivityManager.bindProcessToNetwork(network)) {
                        Log.w(LOGTAG, "bindProcessToNetwork returned false for " + network);
                    }
                } catch (Exception e) {
                    Log.e(LOGTAG, "bindProcessToNetwork failed", e);
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.d(LOGTAG, "default network " + network + " lost, clearing process binding");
                try {
                    connectivityManager.bindProcessToNetwork(null);
                } catch (Exception e) {
                    Log.e(LOGTAG, "bindProcessToNetwork(null) failed", e);
                }
            }
        };
    }

    public void registerNetworkCallback() {
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        connectivityManager.registerNetworkCallback(builder.build(), networkCallback);
        connectivityManager.registerDefaultNetworkCallback(defaultNetworkCallback);
    }

    public void unregisterNetworkCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception e) {
            Log.e(LOGTAG, "failed to unregister network callback", e);
        }
        try {
            connectivityManager.unregisterNetworkCallback(defaultNetworkCallback);
        } catch (Exception e) {
            Log.e(LOGTAG, "failed to unregister default network callback", e);
        }
        try {
            connectivityManager.bindProcessToNetwork(null);
        } catch (Exception e) {
            Log.e(LOGTAG, "bindProcessToNetwork(null) on unregister failed", e);
        }
    }

    public void subscribe(NetworkAvailabilityListener listener) {
        this.listener = listener;
    }

    public void unsubscribe() {
        this.listener = null;
    }
}
