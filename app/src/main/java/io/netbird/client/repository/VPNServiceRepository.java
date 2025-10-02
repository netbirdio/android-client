package io.netbird.client.repository;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.domain.NetworkResourceGroup;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

import io.netbird.client.tool.ConnectionChangeListener;
import io.netbird.client.tool.VPNService;
import io.netbird.client.ui.home.NetworkDomain;
import io.netbird.client.domain.Resource;
import io.netbird.client.domain.RoutingPeer;
import io.netbird.client.ui.home.Status;
import io.netbird.gomobile.android.NetworkDomains;
import io.netbird.gomobile.android.PeerRoutes;

public class VPNServiceRepository {
    private VPNService.MyLocalBinder binder;
    private final Context context;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (VPNService.MyLocalBinder) service;
            binder.setConnectionStateListener(connectionChangeListener);
            fetchNetworkResourceGroup();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (binder != null) {
                binder.removeConnectionStateListener(connectionChangeListener);
                binder = null;
            }
        }
    };

    private final ConnectionChangeListener connectionChangeListener = new ConnectionChangeListener() {
        @Override
        public void onAddressChanged(String host, String address) {

        }

        @Override
        public void onConnected() {

        }

        @Override
        public void onConnecting() {

        }

        @Override
        public void onDisconnected() {

        }

        @Override
        public void onDisconnecting() {

        }

        @Override
        public void onPeersListChanged(long size) {
            fetchNetworkResourceGroup();
        }
    };

    private final BehaviorSubject<NetworkResourceGroup> networkResourceGroup =  BehaviorSubject.createDefault(
            new NetworkResourceGroup(new ArrayList<>(), new ArrayList<>()));

    public VPNServiceRepository(Context context) {
        this.context = context;
    }

    public Observable<NetworkResourceGroup> observeNetworkResourceGroup() {
        return networkResourceGroup;
    }

    private List<String> createPeerRoutesList(PeerRoutes peerRoutes) {
        List<String> routes = new ArrayList<>();

        try {
            for (int i = 0; i < peerRoutes.size(); i++) {
                routes.add(peerRoutes.get(i));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return routes;
    }

    private List<NetworkDomain> createNetworkDomainsList(NetworkDomains networkDomains) {
        List<NetworkDomain> domains = new ArrayList<>();

        io.netbird.gomobile.android.NetworkDomain goNetworkDomain;
        NetworkDomain networkDomain;
        String ipAddress;

        try {
            for (int i = 0; i < networkDomains.size(); i++) {
                goNetworkDomain = networkDomains.get(i);
                networkDomain = new NetworkDomain(goNetworkDomain.getAddress());

                var resolvedIPs = goNetworkDomain.getResolvedIPs();

                for (int j = 0; j < resolvedIPs.size(); j++) {
                    ipAddress = resolvedIPs.get(j);
                    networkDomain.addResolvedIP(ipAddress);
                }

                domains.add(networkDomain);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return domains;
    }

    public void bindService() {
        var intent = new Intent(context, VPNService.class);
        context.bindService(intent, serviceConnection, Context.BIND_ABOVE_CLIENT);
    }

    public void unbindService() {
        if (binder != null) {
            context.unbindService(serviceConnection);
            binder = null;
        }
    }

    private List<Resource> getNetworks() {
        if (binder == null) {
            return new ArrayList<>();
        }

        var resources = new ArrayList<Resource>();
        var networks = binder.networks();

        for (int i = 0; i < networks.size(); i++) {
            var network = networks.get(i);
            var networkDomains = network.getNetworkDomains();

            resources.add(new Resource(Status.fromString(network.getStatus()),
                    network.getName(),
                    network.getNetwork(),
                    network.getPeer(),
                    network.getIsSelected(),
                    createNetworkDomainsList(networkDomains)));
        }

        return resources;
    }

    private List<RoutingPeer> getRoutingPeers() {
        if (binder == null) {
            return new ArrayList<>();
        }

        var peers = new ArrayList<RoutingPeer>();
        var peersFromEngine = binder.peersInfo();

        for (int i = 0; i < peersFromEngine.size(); i++) {
            var peerInfo = peersFromEngine.get(i);
            var peerRoutes = peerInfo.getPeerRoutes();

            peers.add(new RoutingPeer(
                    Status.fromString(peerInfo.getConnStatus()),
                    createPeerRoutesList(peerRoutes)));
        }

        return peers;
    }

    public void fetchNetworkResourceGroup() {
        var resources = this.getNetworks();
        var peers = this.getRoutingPeers();

        var group = new NetworkResourceGroup(resources, peers);

        this.networkResourceGroup.onNext(group);
    }

    public void selectRoute(String route) throws Exception {
        if (binder != null) {
            binder.selectRoute(route);
        }
    }

    public void deselectRoute(String route) throws Exception {
        if (binder != null) {
            binder.deselectRoute(route);
        }
    }
}
