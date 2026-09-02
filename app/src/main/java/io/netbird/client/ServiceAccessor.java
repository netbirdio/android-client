package io.netbird.client;

import androidx.annotation.Nullable;

import io.netbird.client.tool.RouteChangeListener;
import io.netbird.gomobile.android.FileDrop;
import io.netbird.gomobile.android.NetworkArray;
import io.netbird.gomobile.android.PeerInfoArray;
import io.netbird.gomobile.android.SSHClient;
import io.netbird.gomobile.android.URLOpener;

public interface ServiceAccessor {
    // Add methods to interact with your service
    void switchConnection(boolean isConnected);

    /** Peer list snapshot, or null while the VPN service is not bound. */
    @Nullable
    PeerInfoArray getPeersList();

    /** Network/resource list snapshot, or null while the VPN service is not bound. */
    @Nullable
    NetworkArray getNetworks();

    void stopEngine();

    void selectRoute(String route) throws Exception;
    void deselectRoute(String route) throws Exception;

    void addRouteChangeListener(RouteChangeListener listener);
    void removeRouteChangeListener(RouteChangeListener listener);

    String debugBundle(boolean anonymize) throws Exception;

    /** SSO session deadline as unix seconds; 0 when unknown or not bound. */
    long sessionExpiresAt();

    /** Starts the interactive SSO flow to extend the session without dropping the tunnel. */
    void extendSession();

    SSHClient newSSHClient();

    /**
     * File drop handle of the active profile, or null while the VPN service is
     * not bound. Usable with the engine stopped; only sending needs the tunnel.
     */
    @Nullable
    FileDrop fileDrop();

    URLOpener getSSHURLOpener();
}