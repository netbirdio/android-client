package io.netbird.client.tool;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import io.netbird.gomobile.android.DNSList;


public class DNSWatch {
    private static final String LOGTAG = "DNSWatch";
    private final ConnectivityManager connectivityManager;
    private DNSList dnsServers;
    private boolean isPrivateDnsActive;
    private DNSChangeListener listener;


    DNSWatch(Context context) {
        connectivityManager = context.getSystemService(ConnectivityManager.class);
        dnsServers  = readActiveDns();
    }

    public synchronized DNSList dnsServers() {
        return dnsServers;
    }

    public synchronized boolean isPrivateDnsActive() {
        return isPrivateDnsActive;
    }

    synchronized public void setDNSChangeListener(DNSChangeListener listener) {
        this.listener = listener;
        registerNetworkCallback();
    }

    synchronized public void removeDNSChangeListener() {
        // prevent "NetworkCallback was not registered" exception
        if(this.listener == null) {
            return;
        }
        connectivityManager.unregisterNetworkCallback(networkCallback);
        this.listener = null;
    }

    private DNSList readActiveDns() {
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if(activeNetwork == null) {
            return new DNSList();
        }
        LinkProperties props = connectivityManager.getLinkProperties(activeNetwork);
        if(props == null) {
            return new DNSList();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            isPrivateDnsActive = props.isPrivateDnsActive();
        }

        List<InetAddress>  list = extendWithFallbackDNS(props.getDnsServers());
        return toDnsList(list);
    }

    private void registerNetworkCallback() {
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build();
        connectivityManager.requestNetwork(networkRequest, networkCallback);
    }

    private synchronized void onNewDNSList(LinkProperties linkProperties) {
        List<InetAddress>  newDNSList = extendWithFallbackDNS(linkProperties.getDnsServers());


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            isPrivateDnsActive = linkProperties.isPrivateDnsActive();
        }

        if(newDNSList.size() != dnsServers.size()) {
            DNSList dnsList = toDnsList(newDNSList);
            try {
                notifyDnsWatcher(dnsList);
                dnsServers = dnsList;
            } catch (Exception e) {
               Log.e(LOGTAG, "failed to update dns servers", e);
            }
            return;
        }

        for(int i=0; i < newDNSList.size(); i++) {
            try {
                if (!newDNSList.get(i).getHostAddress().equals(dnsServers.get(i))) {
                    DNSList dnsList = toDnsList(newDNSList);
                    notifyDnsWatcher(dnsList);
                    dnsServers = dnsList;
                    return;
                }
            } catch (Exception e) {
                Log.e(LOGTAG, "failed to update dns servers", e);
                return;
            }
        }
    }

    private List<InetAddress> extendWithFallbackDNS(List<InetAddress> dnsServers) {
        List<InetAddress> modifiableDnsServers = new ArrayList<>(dnsServers);

        if (dnsServers.isEmpty()) {
            return modifiableDnsServers;
        }

        if (!dnsServers.get(0).isLinkLocalAddress()) {
            return modifiableDnsServers;
        }

        try {
            InetAddress addr = InetAddress.getByName("1.1.1.1");
            modifiableDnsServers.add(0, addr);
        } catch (UnknownHostException e) {}
        return modifiableDnsServers;
    }

    private void notifyDnsWatcher(DNSList dnsServers) throws Exception {
        listener.onChanged(dnsServers);
    }

    private DNSList toDnsList(List<InetAddress> newDNSList) {
        DNSList dnsList = new DNSList();
        for(InetAddress addr : newDNSList) {
            dnsList.add(addr.getHostAddress());
        }
        return dnsList;
    }

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {

        @Override
        public void onAvailable(@NonNull Network network) {
            super.onAvailable(network);
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities);
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            Log.d(LOGTAG, "onLinkPropertiesChanged: "+linkProperties.getDnsServers());
            onNewDNSList(linkProperties);
        }
    };
}
