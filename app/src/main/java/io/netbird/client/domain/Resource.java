package io.netbird.client.domain;

import java.util.List;

import io.netbird.client.ui.home.NetworkDomain;
import io.netbird.client.ui.home.Status;

public class Resource {
    private final Status status;
    private final String name;
    private final String address;
    private final String peer;
    private final boolean isSelected;
    private final List<NetworkDomain> domains;

    public Resource(Status status, String name, String address, String peer, boolean isSelected, List<NetworkDomain> domains) {
        this.status = status;
        this.name = name;
        this.address = address;
        this.peer = peer;
        this.isSelected = isSelected;
        this.domains = domains;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getPeer() {
        return peer;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isExitNode() {
        return address.equals("0.0.0.0/0");
    }

    public boolean isSelected() {
        return isSelected;
    }

    public List<NetworkDomain> getDomains() {
        return this.domains;
    }
}