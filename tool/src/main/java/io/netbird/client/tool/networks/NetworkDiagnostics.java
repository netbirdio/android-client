package io.netbird.client.tool.networks;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.os.Build;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * One-line, log-friendly descriptions of connectivity state. Written for
 * troubleshooting from a logcat dump: what Android reports as the active
 * network, what Private DNS is doing, and what the OS actually holds on the
 * VPN interface, as opposed to what the app asked for.
 */
public final class NetworkDiagnostics {

    private NetworkDiagnostics() {
    }

    /** Transports plus the capability flags the tunnel logic cares about. */
    public static String describe(NetworkCapabilities caps) {
        if (caps == null) {
            return "caps=null";
        }
        List<String> transports = new ArrayList<>();
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            transports.add("WIFI");
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            transports.add("CELLULAR");
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            transports.add("ETHERNET");
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            transports.add("BLUETOOTH");
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            transports.add("VPN");
        }
        return "transports=" + transports
                + " internet=" + caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                + " validated=" + caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                + " vpn=" + !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
    }

    /**
     * The network this process currently defaults to, with its resolver list
     * and Private DNS state. While the tunnel is up this may be the VPN itself,
     * which is exactly what the DNS decision in IFace needs to reveal.
     */
    public static String describeActiveNetwork(ConnectivityManager cm) {
        Network network = cm.getActiveNetwork();
        if (network == null) {
            return "active=none";
        }
        StringBuilder sb = new StringBuilder("active=").append(network)
                .append(' ').append(describe(cm.getNetworkCapabilities(network)));
        LinkProperties props = cm.getLinkProperties(network);
        if (props == null) {
            return sb.append(" link=null").toString();
        }
        sb.append(" dnsServers=").append(formatAddresses(props.getDnsServers()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String server = props.getPrivateDnsServerName();
            sb.append(" privateDnsActive=").append(props.isPrivateDnsActive())
                    .append(" privateDnsServer=").append(server == null ? "none" : server);
        }
        return sb.toString();
    }

    /**
     * What the OS holds on the VPN interface(s): DNS servers and route
     * destinations as installed, not as requested. No DNS server here while
     * the engine requested one means NetBird names cannot resolve.
     */
    @SuppressWarnings("deprecation") // getAllNetworks is the only synchronous enumeration
    public static String describeVpnNetworks(ConnectivityManager cm) {
        StringBuilder sb = new StringBuilder();
        for (Network network : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append("vpn=").append(network);
            LinkProperties props = cm.getLinkProperties(network);
            if (props == null) {
                sb.append(" link=null");
                continue;
            }
            sb.append(" iface=").append(props.getInterfaceName())
                    .append(" dns=").append(formatAddresses(props.getDnsServers()))
                    .append(" routes=").append(formatRoutes(props.getRoutes()));
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    /** Host addresses only; InetAddress.toString would prefix each with a slash. */
    public static String formatAddresses(List<InetAddress> addresses) {
        List<String> out = new ArrayList<>(addresses.size());
        for (InetAddress addr : addresses) {
            out.add(addr.getHostAddress());
        }
        return out.toString();
    }

    private static String formatRoutes(List<RouteInfo> routes) {
        List<String> out = new ArrayList<>(routes.size());
        for (RouteInfo route : routes) {
            out.add(route.getDestination().toString());
        }
        return out.toString();
    }
}
