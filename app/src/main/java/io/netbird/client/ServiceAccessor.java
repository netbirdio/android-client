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

    String debugBundle(boolean anonymize) throws Exception;

    /**
     * Returns the canonical name (e.g. "p2p-dynamic") of the connection
     * mode the management server most recently pushed. Empty string when
     * the engine has not connected yet or no value has been pushed --
     * the UI should then hide the "(currently: ...)" suffix on the
     * Follow-server entry of the override dropdown.
     */
    String getServerPushedConnectionMode();

    /**
     * Returns the relay timeout (seconds) the management server most
     * recently pushed. 0 when not yet known. Used as a hint in the
     * override field so the user can see the value they are about to
     * override.
     */
    long getServerPushedRelayTimeoutSecs();

    /**
     * Returns the p2p (ICE-only) timeout in seconds most recently pushed.
     */
    long getServerPushedP2pTimeoutSecs();

    /**
     * Returns the p2p retry-max cap in seconds most recently pushed.
     */
    long getServerPushedP2pRetryMaxSecs();
}