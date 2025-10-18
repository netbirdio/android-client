package io.netbird.client.tool;

public class TUNParameters {
    String address;
    long mtu;
    String dns;
    String searchDomainsString;
    String routesString;

    public TUNParameters(String address, long mtu, String dns, String searchDomainsString, String routesString) {
        this.address = address;
        this.mtu = mtu;
        this.dns = dns;
        this.searchDomainsString = searchDomainsString;
        this.routesString = routesString;
    }

    public boolean didRoutesChange(String routesString) {
        if (this.routesString != null) {
            return !this.routesString.equals(routesString);
        } else {
            return routesString != null;
        }
    }
}
