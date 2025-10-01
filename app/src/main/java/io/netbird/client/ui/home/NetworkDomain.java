package io.netbird.client.ui.home;

import java.util.ArrayList;
import java.util.List;

public class NetworkDomain {
    private final String address;
    private final List<String> resolvedIPs;

    public NetworkDomain(String address) {
        this.address = address;
        this.resolvedIPs = new ArrayList<>();
    }

    public String getAddress() {
        return address;
    }

    public void addResolvedIP(String ipAddress) {
        this.resolvedIPs.add(ipAddress);
    }

    public List<String> getResolvedIPs() {
        return this.resolvedIPs;
    }
}
