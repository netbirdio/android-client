package io.netbird.client.domain;

import java.util.List;

import io.netbird.client.ui.home.Status;

public class RoutingPeer {
    private final Status status;
    private final List<String> routes;
    public RoutingPeer(Status status, List<String> routes) {
        this.status = status;
        this.routes = routes;
    }

    public Status getStatus() {
        return this.status;
    }

    public List<String> getRoutes() {
        return this.routes;
    }
}
