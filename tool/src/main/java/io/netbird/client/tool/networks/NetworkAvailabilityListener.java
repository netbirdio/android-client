package io.netbird.client.tool.networks;

public interface NetworkAvailabilityListener {
    void onNetworkAvailable(@Constants.NetworkType int networkType);
    void onNetworkLost(@Constants.NetworkType int networkType);
    // networkHandle identifies the concrete network (Network#getNetworkHandle),
    // so a same-type handover (WiFi AP -> different WiFi AP) is observable.
    void onDefaultNetworkTypeChanged(@Constants.NetworkType int networkType, long networkHandle);

    // Fired when the device transitions between "has at least one
    // internet-capable network" and "has none at all" (e.g. airplane mode).
    default void onInternetAvailabilityChanged(boolean available) {}

    // Fired when a network gains or loses NET_CAPABILITY_VALIDATED, i.e. when
    // Android confirms (or revokes) that the network actually reaches the internet.
    default void onNetworkValidated(@Constants.NetworkType int networkType, boolean validated) {}
}
