package io.netbird.client.ui.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.ServiceAccessor;
import io.netbird.client.StateListener;
import io.netbird.client.tool.RouteChangeListener;
import io.netbird.gomobile.android.NetworkDomains;
import io.netbird.gomobile.android.PeerRoutes;

public class NetworksFragmentViewModel extends ViewModel implements RouteChangeListener, StateListener {
    private final ServiceAccessor serviceAccessor;
    private final MutableLiveData<NetworksFragmentUiState> uiState =
            new MutableLiveData<>(new NetworksFragmentUiState(new ArrayList<>(), new ArrayList<>()));

    public NetworksFragmentViewModel(ServiceAccessor serviceAccessor) {
        this.serviceAccessor = serviceAccessor;
        serviceAccessor.addRouteChangeListener(this);
    }

    public static ViewModelProvider.Factory getFactory(ServiceAccessor serviceAccessor) {
        return new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                if (modelClass.isAssignableFrom(NetworksFragmentViewModel.class)) {
                    return (T) new NetworksFragmentViewModel(serviceAccessor);
                }
                throw new IllegalArgumentException("Unknown ViewModel class");
            }
        };
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        serviceAccessor.removeRouteChangeListener(this);
    }

    public LiveData<NetworksFragmentUiState> getUiState() {
        return uiState;
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

    private List<Resource> getNetworks() {
        var resources = new ArrayList<Resource>();
        var networks = serviceAccessor.getNetworks();

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
        var peers = new ArrayList<RoutingPeer>();
        var peersFromEngine = serviceAccessor.getPeersList();

        for (int i = 0; i < peersFromEngine.size(); i++) {
            var peerInfo = peersFromEngine.get(i);
            var peerRoutes = peerInfo.getPeerRoutes();

            peers.add(new RoutingPeer(
                    Status.fromString(peerInfo.getConnStatus()),
                    createPeerRoutesList(peerRoutes)));
        }

        return peers;
    }

    private void postResources() {
        var resources = getNetworks();
        var peers = getRoutingPeers();

        // This value will be set from a background thread.
        uiState.postValue(new NetworksFragmentUiState(resources, peers));
    }

    @Override
    public void onRouteChanged(String routes) {
        postResources();
    }

    public void selectRoute(String route) throws Exception {
        this.serviceAccessor.selectRoute(route);
    }

    public void deselectRoute(String route) throws Exception {
        this.serviceAccessor.deselectRoute(route);
    }

    // region StateListener implementation
    @Override
    public void onEngineStarted() {

    }

    @Override
    public void onEngineStopped() {

    }

    @Override
    public void onAddressChanged(String var1, String var2) {

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
    public void onPeersListChanged(long var1) {
        postResources();
    }
    // endregion
}
