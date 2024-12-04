package io.netbird.client.tool;


import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;

import java.util.LinkedList;

import io.netbird.gomobile.android.TunAdapter;
import io.netbird.client.tool.wg.BackendException;
import io.netbird.client.tool.wg.InetNetwork;

class IFace implements TunAdapter {

    private static final String LOGTAG = "IFace";
    private final VPNService vpnService;

    public IFace(VPNService vpnService) {
        this.vpnService = vpnService;
    }

    @Override
    public long configureInterface(String address, long mtu, String dns, String searchDomainsString, String routesString) throws Exception {
        String[] searchDomains = toSearchDomains(searchDomainsString);
        LinkedList<Route> routes = toRoutes(routesString);

        InetNetwork addr = InetNetwork.parse(address);
        try {
            return createTun(addr.getAddress().getHostAddress(), addr.getMask(), (int) mtu, dns, searchDomains, routes);
        }catch (Exception e) {
            Log.e(LOGTAG, "failed to create tunnel", e);
            throw e;
        }
    }

    @Override
    public boolean protectSocket(int fd) {
        vpnService.protect(fd);
        // Ignore the error to allow the app to connect to a Management server before the VPN service
        // is up and running. This is just a workaround and should be removed in the future.
        return true;
    }

    private int createTun(String ip, int prefixLength, int mtu, String dns, String[] searchDomains, LinkedList<Route> routes) throws Exception {
        VpnService.Builder builder = vpnService.getBuilder();
        builder.addAddress(ip, prefixLength);
        builder.allowFamily(OsConstants.AF_INET);
        builder.allowFamily(OsConstants.AF_INET6);
        builder.setMtu(mtu);
        prepareDnsSetting(builder, dns);
        for (String sd : searchDomains) {
            builder.addSearchDomain(sd);
            Log.d(LOGTAG,"add search domain: "+ sd);
        }

        for (Route r : routes) {
            builder.addRoute(r.addr, r.prefixLength);
            Log.d(LOGTAG, "add route: "+r.addr+"/"+r.prefixLength);
        }

        disallowApp(builder, "com.google.android.projection.gearhead");
        disallowApp(builder, "com.google.android.apps.chromecast.app");
        disallowApp(builder, "com.google.android.apps.messaging");
        disallowApp(builder, "com.google.stadia.android");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            vpnService.setUnderlyingNetworks(null);
        }

        builder.setBlocking(true);
        try (final ParcelFileDescriptor tun = builder.establish()) {
            if (tun == null) {
                throw new BackendException(BackendException.Reason.TUN_CREATION_ERROR);
            }
            return tun.detachFd();
        }
    }

    private void prepareDnsSetting(VpnService.Builder builder, String dns) {
        if(dns == null) {
            return;
        }

        if(dns.isEmpty()) {
            return;
        }

        if(new DNSWatch(vpnService).isPrivateDnsActive()) {
            Log.d(LOGTAG, "ignore DNS because private dns is active");
            return;
        }

        builder.addDnsServer(dns);
    }

    private void disallowApp(VpnService.Builder builder, String packageName) {
        try {
            builder.addDisallowedApplication(packageName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
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