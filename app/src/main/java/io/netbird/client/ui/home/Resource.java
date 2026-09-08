package io.netbird.client.ui.home;

import java.util.List;

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
        return isExitNodeAddress(address);
    }

    /**
     * A route whose range covers all traffic (0.0.0.0/0 or ::/0) is an exit node.
     * Dual-stack exit nodes are reported as a single comma-joined field, e.g.
     * "0.0.0.0/0, ::/0", so each part must be checked individually.
     */
    public static boolean isExitNodeAddress(String address) {
        if (address == null) {
            return false;
        }
        for (String part : address.split(",")) {
            String trimmed = part.trim();
            if (trimmed.equals("0.0.0.0/0") || trimmed.equals("::/0")) {
                return true;
            }
        }
        return false;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public List<NetworkDomain> getDomains() {
        return this.domains;
    }
}