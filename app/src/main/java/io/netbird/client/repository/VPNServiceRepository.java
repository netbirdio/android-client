package io.netbird.client.repository;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.tool.RouteChangeListener;
import io.netbird.client.tool.VPNService;
import io.netbird.client.ui.home.Resource;
import io.netbird.client.ui.home.RoutingPeer;
import io.netbird.client.ui.home.Status;
import io.netbird.gomobile.android.NetworkDomains;
import io.netbird.gomobile.android.PeerRoutes;

public class VPNServiceRepository {
    private VPNService.MyLocalBinder binder;
    private final Context context;
    private VPNServiceBindListener serviceBindListener;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (VPNService.MyLocalBinder) service;
            if (serviceBindListener != null) {
                serviceBindListener.onServiceBind();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (binder != null) {
                binder = null;
            }

            serviceBindListener = null;
        }
    };

    public VPNServiceRepository(Context context) {
        this.context = context;
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

    private List<String> createNetworkDomainsList(NetworkDomains networkDomains) {
        List<String> domains = new ArrayList<>();

        try {
            for (int i = 0; i < networkDomains.size(); i++) {
                domains.add(networkDomains.get(i));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return domains;
    }

    public void setServiceBindListener(VPNServiceBindListener listener) {
        this.serviceBindListener = listener;
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

    public List<Resource> getNetworks() {
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

    public List<RoutingPeer> getRoutingPeers() {
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

    public void addRouteChangeListener(RouteChangeListener listener) {
        if (binder != null) {
            binder.addRouteChangeListener(listener);
        }
    }

    public void removeRouteChangeListener(RouteChangeListener listener) {
        if (binder != null) {
            binder.removeRouteChangeListener(listener);
        }
    }

    public void selectRoute(String route) {
        if (binder != null) {
            binder.selectRoute(route);
        }
    }

    public void deselectRoute(String route) {
        if (binder != null) {
            binder.deselectRoute(route);
        }
    }
}
