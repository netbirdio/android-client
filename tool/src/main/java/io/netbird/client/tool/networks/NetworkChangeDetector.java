package io.netbird.client.tool.networks;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;

public class NetworkChangeDetector {

    private final ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private NetworkChangeListener listener;

    public NetworkChangeDetector(ConnectivityManager connectivityManager) {
        this.connectivityManager = connectivityManager;
        initNetworkCallback();
    }

    private void notifyListener(@Constants.NetworkType int networkType) {
        if (listener != null) {
            listener.onNetworkChanged(networkType);
        }
    }

    private void initNetworkCallback() {
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                var capabilities = connectivityManager.getNetworkCapabilities(network);

                if (capabilities != null) {
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        notifyListener(Constants.NetworkType.WIFI);
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        notifyListener(Constants.NetworkType.MOBILE);
                    }
                }
            }
        };
    }

    public void registerNetworkCallback() {
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        connectivityManager.registerNetworkCallback(builder.build(), networkCallback);
    }

    public void unregisterNetworkCallback() {
        connectivityManager.unregisterNetworkCallback(networkCallback);
    }

    public void subscribe(NetworkChangeListener listener) {
        this.listener = listener;
    }

    public void unsubscribe() {
        this.listener = null;
    }
}
