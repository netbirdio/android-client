package io.netbird.client.tool;

public interface ConnectionChangeListener {
    void onAddressChanged(String host, String address);
    void onConnected();
    void onConnecting();
    void onDisconnected();
    void onDisconnecting();
    void onPeersListChanged(long size);
}
