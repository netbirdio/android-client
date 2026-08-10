package io.netbird.client;

public interface StateListener {
    void onEngineStarted();
    void onEngineStopped();
    void onAddressChanged(String var1, String var2);

    void onConnected();

    void onConnecting();

    /**
     * Connection attempts are suspended because the OS reports no usable
     * network; shown instead of "Connecting". Default is a no-op so only
     * listeners that display state need to implement it.
     */
    default void onNoNetwork() {
    }

    void onDisconnected();

    void onDisconnecting();

    void onPeersListChanged(long var1);

    /** Session deadline changed; 0 means no deadline is known. */
    void onSessionDeadlineChanged(long expiresAtUnixSeconds);

    /**
     * The management server rejected the peer: reconnecting needs an
     * interactive login. Outlives the engine, so it is also reported to
     * listeners that register after the fact.
     */
    void onLoginRequired();
}
