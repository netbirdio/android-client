package io.netbird.client.domain;

import java.util.List;

public class NetworkResourceGroup {
    private final List<Resource> networkResources;
    private final List<RoutingPeer> routingPeers;

    public NetworkResourceGroup(List<Resource> networkResources, List<RoutingPeer> routingPeers) {
        this.networkResources = networkResources;
        this.routingPeers = routingPeers;
    }

    public List<Resource> getNetworkResources() {
        return networkResources;
    }

    public List<RoutingPeer> getRoutingPeers() {
        return routingPeers;
    }
}
