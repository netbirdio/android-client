package io.netbird.client.tool.networks;

public interface NetworkAvailabilityListener {
    void onNetworkAvailable(@Constants.NetworkType int networkType);
    void onNetworkLost(@Constants.NetworkType int networkType);
}
