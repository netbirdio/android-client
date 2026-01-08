package io.netbird.client;

import org.jetbrains.annotations.NotNull;

public class PeersStateListenerAdapter extends StateListenerAdapter {
    private PeersStateListener listener;

    public PeersStateListenerAdapter(@NotNull PeersStateListener listener) {
        this.listener = listener;
    }

    public void clearListener() {
        this.listener = null;
    }

    @Override
    public void onPeersListChanged(long totalPeers) {
        if (listener == null) return;

        listener.onPeersChanged(totalPeers);
    }
}
