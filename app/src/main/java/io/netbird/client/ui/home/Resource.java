package io.netbird.client.ui.home;

import java.util.List;

public class Resource {
    private final Status status;
    private final String name;
    private final String address;
    private final String peer;
    private final boolean isSelected;
    private final List<String> domains;

    public Resource(Status status, String name, String address, String peer, boolean isSelected, List<String> domains) {
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

    public List<String> getDomains() {
        return this.domains;
    }
}