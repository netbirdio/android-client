package io.netbird.client;

public interface StateListener {
    void onEngineStarted();
    void onEngineStopped();

    void onAddressChanged(String var1, String var2);

    void onConnected();

    void onConnecting();

    void onDisconnected();

    void onDisconnecting();

    void onPeersListChanged(long var1);
}
