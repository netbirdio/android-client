package io.netbird.client.ui.home;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PeersFragmentUiState {
    private final List<Peer> peers;

    public PeersFragmentUiState(List<Peer> peers) {
        this.peers = new ArrayList<>(peers);
    }

    public List<Peer> getPeers() {
        return Collections.unmodifiableList(peers);
    }
}
