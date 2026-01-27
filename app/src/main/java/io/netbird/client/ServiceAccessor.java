package io.netbird.client;

import io.netbird.client.tool.RouteChangeListener;
import io.netbird.gomobile.android.NetworkArray;
import io.netbird.gomobile.android.PeerInfoArray;

public interface ServiceAccessor {
    // Add methods to interact with your service
    void switchConnection(boolean isConnected);
    PeerInfoArray getPeersList();

    NetworkArray getNetworks();
    void stopEngine();

    void selectRoute(String route) throws Exception;
    void deselectRoute(String route) throws Exception;

    void addRouteChangeListener(RouteChangeListener listener);
    void removeRouteChangeListener(RouteChangeListener listener);
}