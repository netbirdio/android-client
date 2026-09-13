package io.netbird.client.tool;


import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netbird.gomobile.android.TunAdapter;
import io.netbird.client.tool.networks.NetworkDiagnostics;
import io.netbird.client.tool.wg.BackendException;
import io.netbird.client.tool.wg.InetNetwork;

class IFace implements TunAdapter {

    private static final String LOGTAG = "IFace";
    private final VPNService vpnService;

    public IFace(VPNService vpnService) {
        this.vpnService = vpnService;
    }

    @Override
    public long configureInterface(String address, String addressV6, long mtu, String dns, String searchDomainsString, String routesString) throws Exception {
        String[] searchDomains = toSearchDomains(searchDomainsString);
        LinkedList<Route> routes = toRoutes(routesString);

        InetNetwork addr = InetNetwork.parse(address);
        InetNetwork addrV6 = null;
        if (addressV6 != null && !addressV6.isEmpty()) {
            addrV6 = InetNetwork.parse(addressV6);
        }
        long fd = -1;

        try {
            fd = createTun(addr.getAddress().getHostAddress(), addr.getMask(), addrV6, (int) mtu, dns, searchDomains, routes);
        } catch (Exception e) {
            Log.e(LOGTAG, "failed to create tunnel: addr=" + address + " addrV6=" + addressV6 + " mtu=" + mtu
                    + " dns=" + dns + " searchDomains=" + searchDomainsString + " routes=" + routesString, e);
        }

        // only set the currently used TUN parameters if createTun didn't throw exceptions
        if (fd != -1) {
            this.vpnService.setCurrentTUNParameters(new TUNParameters(address, addressV6, mtu, dns, searchDomainsString, routesString));
        }

        return fd;
    }

    @Override
    public boolean protectSocket(int fd) {
        vpnService.protect(fd);
        // Ignore the error to allow the app to connect to a Management server before the VPN service
        // is up and running. This is just a workaround and should be removed in the future.
        return true;
    }

    private int createTun(String ip, int prefixLength, InetNetwork addrV6, int mtu, String dns, String[] searchDomains, LinkedList<Route> routes) throws Exception {
        VpnService.Builder builder = vpnService.getBuilder();
        builder.addAddress(ip, prefixLength);
        if (addrV6 != null) {
            builder.addAddress(addrV6.getAddress().getHostAddress(), addrV6.getMask());
            Log.d(LOGTAG, "add IPv6 address: " + addrV6.getAddress().getHostAddress() + "/" + addrV6.getMask());
        }
        builder.allowFamily(OsConstants.AF_INET);
        builder.allowFamily(OsConstants.AF_INET6);
        builder.setMtu(mtu);
        boolean dnsApplied = prepareDnsSetting(builder, dns);
        for (String sd : searchDomains) {
            builder.addSearchDomain(sd);
            Log.d(LOGTAG,"add search domain: "+ sd);
        }

        if (addrV6 == null && hasIPv4DefaultRoute(routes)) {
            routes.add(new Route("::/0"));
        }

        for (Route r : routes) {
            builder.addRoute(r.addr, r.prefixLength);
            Log.d(LOGTAG, "add route: "+r.addr+"/"+r.prefixLength);
        }

        applyAppFilter(builder);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            vpnService.setUnderlyingNetworks(null);
        }

        builder.setBlocking(true);
        try (final ParcelFileDescriptor tun = builder.establish()) {
            if (tun == null) {
                // The platform contract for null: this app is not the prepared
                // VPN any more, because consent was revoked or another VPN app
                // took the slot. Not a parameter problem.
                Log.e(LOGTAG, "establish() returned null: VPN permission missing or another VPN is active");
                throw new BackendException(BackendException.Reason.TUN_CREATION_ERROR);
            }
            int fd = tun.detachFd();
            Log.i(LOGTAG, "tun established: fd=" + fd + " addr=" + ip + "/" + prefixLength
                    + " addrV6=" + (addrV6 == null ? "none" : addrV6.getAddress().getHostAddress() + "/" + addrV6.getMask())
                    + " mtu=" + mtu + " dns=" + (dnsApplied ? dns : "none")
                    + " searchDomains=" + Arrays.toString(searchDomains) + " routes=" + formatRoutes(routes));
            return fd;
        }
    }

    /**
     * Adds the NetBird resolver to the tunnel unless Android reports Private
     * DNS in use on the active network, and returns whether it was added. The
     * decision is logged together with the network it was taken on: it is
     * re-evaluated on every tunnel rebuild and depends on whatever network is
     * active at that moment, so a logcat dump has to show both.
     */
    private boolean prepareDnsSetting(VpnService.Builder builder, String dns) {
        if (dns == null || dns.isEmpty()) {
            Log.i(LOGTAG, "dns decision: no resolver requested by the engine");
            return false;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean added = new AtomicBoolean(false);

        // ConnectivityManager must to run on the main thread instead of a Go routine
        new Handler(Looper.getMainLooper()).post(() -> {
            DNSWatch dnsWatch = new DNSWatch(vpnService);
            String network = NetworkDiagnostics.describeActiveNetwork(
                    vpnService.getSystemService(ConnectivityManager.class));

            if (!dnsWatch.isPrivateDnsActive()) {
                builder.addDnsServer(dns);
                added.set(true);
                Log.i(LOGTAG, "dns decision: netbird resolver " + dns + " added; " + network);
            } else {
                Log.i(LOGTAG, "dns decision: netbird resolver " + dns + " skipped, private dns active; " + network);
            }

            latch.countDown();
        });

        try {
            latch.await(); // Will block the current thread until countDown() is called
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return added.get();
    }

    /**
     * Narrows the tunnel to the apps the user picked.
     *
     * The selection is read here rather than passed in because the tunnel is
     * also rebuilt from VPNService without going through the Go engine, and both
     * paths must see the same stored answer. It belongs to the active profile,
     * so switching profile switches which applications the tunnel carries.
     */
    private void applyAppFilter(VpnService.Builder builder) {
        PackageManager packageManager = vpnService.getPackageManager();
        SplitTunnelConfig.Resolution resolution = new SplitTunnelStore(vpnService)
                .load()
                .resolve(vpnService.getPackageName(), packageName -> {
                    try {
                        packageManager.getApplicationInfo(packageName, 0);
                        return true;
                    } catch (PackageManager.NameNotFoundException e) {
                        return false;
                    }
                });

        boolean allow = resolution.getFilter() == SplitTunnelConfig.Filter.ALLOW;
        for (String packageName : resolution.getPackages()) {
            try {
                if (allow) {
                    builder.addAllowedApplication(packageName);
                } else {
                    builder.addDisallowedApplication(packageName);
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                // Uninstalled since it was picked. Dropping the whole tunnel over a
                // stale entry would be worse than ignoring it.
            }
        }

        Log.d(LOGTAG, "app filter: " + (allow ? "allow " : "disallow ")
                + resolution.getPackages().size() + " package(s)");
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void updateAddr(String s) throws Exception {
    }

    private String[] toSearchDomains(String searchDomains) {
        LinkedList<String> list = new LinkedList<>();
        if(searchDomains == null) {
            return new String[0];
        }
        if(searchDomains.isEmpty()) {
            return new String[0];
        }
        return searchDomains.split(";");
    }

    private static String formatRoutes(List<Route> routes) {
        StringBuilder sb = new StringBuilder("[");
        for (Route r : routes) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(r.addr).append('/').append(r.prefixLength);
        }
        return sb.append(']').toString();
    }

    // Blackhole IPv6 when the tunnel has an IPv4 default route but no IPv6
    // address on the interface, to prevent IPv6 leaks around the tunnel.
    private boolean hasIPv4DefaultRoute(LinkedList<Route> routes) {
        for (Route r : routes) {
            if ("0.0.0.0".equals(r.addr) && r.prefixLength == 0) {
                return true;
            }
        }
        return false;
    }

    private LinkedList<Route> toRoutes(String routesString) {
        LinkedList<Route> routesList = new LinkedList<>();
        if(routesString == null) {
            return routesList;
        }
        if(routesString.isEmpty()) {
            return routesList;
        }
        String[] routes = routesString.split(";");
        for(String route : routes) {
            try {
                Route r = new Route(route);
                routesList.add(r);
            } catch (Exception e) {
                Log.e(LOGTAG, "invalid route: "+ route);
            }
        }
        return routesList;
    }
}