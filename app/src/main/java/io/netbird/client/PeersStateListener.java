package io.netbird.client;

public interface PeersStateListener {
    void onPeersChanged(long totalPeers);
}
