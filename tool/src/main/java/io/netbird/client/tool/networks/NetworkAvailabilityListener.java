package io.netbird.client.tool.networks;

public interface NetworkAvailabilityListener {
    void onNetworkAvailable(@Constants.NetworkType int networkType);
    void onNetworkLost(@Constants.NetworkType int networkType);
    void onDefaultNetworkTypeChanged(@Constants.NetworkType int networkType);

    // Fired when the device transitions between "has at least one
    // internet-capable network" and "has none at all" (e.g. airplane mode).
    default void onInternetAvailabilityChanged(boolean available) {}
}
