package io.netbird.client.ui.home;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.ServiceAccessor;
import io.netbird.client.StateListener;
import io.netbird.client.tool.RouteChangeListener;
import io.netbird.gomobile.android.NetworkArray;
import io.netbird.gomobile.android.NetworkDomains;
import io.netbird.gomobile.android.PeerInfoArray;
import io.netbird.gomobile.android.PeerRoutes;

public class NetworksFragmentViewModel extends ViewModel implements RouteChangeListener, StateListener {

    // The accessor is the Activity, which this ViewModel outlives across configuration
    // changes (language switch, rotation). The fragment re-supplies the current one on
    // every view creation and clears it on view destruction; holding a reference
    // captured at construction would keep reading through a dead Activity's unbound
    // service connection forever.
    private volatile ServiceAccessor serviceAccessor;
    private final MutableLiveData<NetworksFragmentUiState> uiState =
            new MutableLiveData<>(new NetworksFragmentUiState(new ArrayList<>(), new ArrayList<>()));

    /**
     * Swaps in the current accessor and moves the route-change registration over to it,
     * so route events keep flowing after the Activity is recreated.
     */
    public void setServiceAccessor(@Nullable ServiceAccessor accessor) {
        ServiceAccessor previous = this.serviceAccessor;
        if (previous == accessor) {
            return;
        }
        if (previous != null) {
            previous.removeRouteChangeListener(this);
        }
        this.serviceAccessor = accessor;
        if (accessor != null) {
            accessor.addRouteChangeListener(this);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        setServiceAccessor(null);
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

    private List<Resource> getNetworks(NetworkArray networks) {
        var resources = new ArrayList<Resource>();

        for (int i = 0; i < networks.size(); i++) {
            var network = networks.get(i);

            // Exit nodes are surfaced through the Home screen picker; keep them
            // out of the resources list.
            if (Resource.isExitNodeAddress(network.getNetwork())) {
                continue;
            }

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

    private List<RoutingPeer> getRoutingPeers(PeerInfoArray peersFromEngine) {
        var peers = new ArrayList<RoutingPeer>();

        for (int i = 0; i < peersFromEngine.size(); i++) {
            var peerInfo = peersFromEngine.get(i);
            var peerRoutes = peerInfo.getPeerRoutes();

            peers.add(new RoutingPeer(
                    Status.fromLong(peerInfo.getConnStatus()),
                    createPeerRoutesList(peerRoutes)));
        }

        return peers;
    }

    private void postResources() {
        ServiceAccessor accessor = serviceAccessor;
        if (accessor == null) {
            return;
        }

        var networks = accessor.getNetworks();
        var peersFromEngine = accessor.getPeersList();
        if (networks == null || peersFromEngine == null) {
            // Service not bound (yet): keep the last snapshot instead of flashing an
            // empty list. Another refresh follows once the binder arrives and the
            // engine replays its state.
            return;
        }

        // This value will be set from a background thread.
        uiState.postValue(new NetworksFragmentUiState(getNetworks(networks), getRoutingPeers(peersFromEngine)));
    }

    @Override
    public void onRouteChanged(String routes) {
        postResources();
    }

    public void selectRoute(String route) throws Exception {
        ServiceAccessor accessor = serviceAccessor;
        if (accessor != null) {
            accessor.selectRoute(route);
        }
    }

    public void deselectRoute(String route) throws Exception {
        ServiceAccessor accessor = serviceAccessor;
        if (accessor != null) {
            accessor.deselectRoute(route);
        }
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
