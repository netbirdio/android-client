package io.netbird.client.ui.home;

import java.util.List;

public class NetworksFragmentUiState {
    private final List<Resource> resources;
    private final List<RoutingPeer> peers;

    public NetworksFragmentUiState(List<Resource> resources, List<RoutingPeer> peers) {
        this.resources = resources;
        this.peers = peers;
    }

    public List<Resource> getResources() {
        return resources;
    }

    public List<RoutingPeer> getPeers() { return peers;
    }
}
