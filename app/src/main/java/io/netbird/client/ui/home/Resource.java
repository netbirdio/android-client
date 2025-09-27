package io.netbird.client.ui.home;

public class Resource {
    private final Status status;
    private final String name;
    private final String address;
    private final String peer;
    private final boolean isSelected;

    public Resource(Status status, String name, String address, String peer, boolean isSelected) {
        this.status = status;
        this.name = name;
        this.address = address;
        this.peer = peer;
        this.isSelected = isSelected;
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
}