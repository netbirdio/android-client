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
    private volatile NetworkAvailabilityListener listener;
    private volatile Runnable networkChangedCallback;
    private volatile boolean initialCallbackReceived;

    public NetworkChangeDetector(ConnectivityManager connectivityManager) {
        this.connectivityManager = connectivityManager;
        initNetworkCallback();
    }

    /**
     * Set a callback that fires on every network availability/loss event,
     * regardless of type. Used to notify the Go layer about underlying
     * network changes for posture check re-evaluation.
     */
    public void setNetworkChangedCallback(Runnable callback) {
        this.networkChangedCallback = callback;
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
                // Skip the very first onAvailable after registerNetworkCallback().
                // Android fires this immediately for each already-connected network —
                // it is an initial state report, not an actual network change.
                if (!initialCallbackReceived) {
                    initialCallbackReceived = true;
                    Log.d(LOGTAG, "ignoring initial onAvailable (not a real network change)");
                    return;
                }
                Log.d(LOGTAG, "onAvailable: " + network);
                NetworkAvailabilityListener localListener = listener;
                if (localListener != null) {
                    checkNetworkCapabilities(network, localListener::onNetworkAvailable);
                }
                Runnable cb = networkChangedCallback;
                if (cb != null) cb.run();
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.d(LOGTAG, "onLost: " + network);
                NetworkAvailabilityListener localListener = listener;
                if (localListener != null) {
                    checkNetworkCapabilities(network, localListener::onNetworkLost);
                }
                Runnable cb = networkChangedCallback;
                if (cb != null) cb.run();
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities);

                Log.d(LOGTAG, String.format("Network %s had their capabilities changed: %s", network, networkCapabilities));
            }
        };
    }

    public void registerNetworkCallback() {
        initialCallbackReceived = false;
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        connectivityManager.registerNetworkCallback(builder.build(), networkCallback);
    }

    public void unregisterNetworkCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception e) {
            Log.e(LOGTAG, "failed to unregister network callback", e);
        }
    }

    public void subscribe(NetworkAvailabilityListener listener) {
        this.listener = listener;
    }

    public void unsubscribe() {
        this.listener = null;
    }
}
