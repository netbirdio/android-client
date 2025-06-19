package io.netbird.client;

import io.netbird.gomobile.android.NetworkArray;
import io.netbird.gomobile.android.PeerInfoArray;

public interface ServiceAccessor {
    // Add methods to interact with your service
    void switchConnection(boolean isConnected);
    PeerInfoArray getPeersList();

    NetworkArray getNetworks();
    void stopEngine();
}