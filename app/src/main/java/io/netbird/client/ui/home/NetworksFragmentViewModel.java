package io.netbird.client.ui.home;

import static androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewmodel.ViewModelInitializer;

import java.util.ArrayList;

import io.netbird.client.MyApplication;
import io.netbird.client.StateListener;
import io.netbird.client.repository.VPNServiceBindListener;
import io.netbird.client.repository.VPNServiceRepository;
import io.netbird.client.tool.RouteChangeListener;

public class NetworksFragmentViewModel extends ViewModel implements VPNServiceBindListener, RouteChangeListener, StateListener {
    private final VPNServiceRepository repository;
    private final MutableLiveData<NetworksFragmentUiState> uiState =
            new MutableLiveData<>(new NetworksFragmentUiState(new ArrayList<>(), new ArrayList<>()));

    public NetworksFragmentViewModel(VPNServiceRepository repository) {
        this.repository = repository;
        this.repository.setServiceBindListener(this);
        this.repository.bindService();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.removeRouteChangeListener(this);
        repository.unbindService();
    }

    public LiveData<NetworksFragmentUiState> getUiState() {
        return uiState;
    }

    private void postResources() {
        var resources = repository.getNetworks();
        var peers = repository.getRoutingPeers();

        // This value will be set from a background thread.
        uiState.postValue(new NetworksFragmentUiState(resources, peers));
    }

    static final ViewModelInitializer<NetworksFragmentViewModel> initializer = new ViewModelInitializer<>(
            NetworksFragmentViewModel.class,
            creationExtras -> {
                MyApplication app = (MyApplication) creationExtras.get(APPLICATION_KEY);
                assert app != null;
                return new NetworksFragmentViewModel(app.getVPNServiceRepository());
            }
    );

    @Override
    public void onServiceBind() {
        this.repository.addRouteChangeListener(this);
        postResources();
    }

    @Override
    public void onRouteChanged(String routes) {
        postResources();
    }

    public void selectRoute(String route) throws Exception {
        this.repository.selectRoute(route);
    }

    public void deselectRoute(String route) throws Exception {
        this.repository.deselectRoute(route);
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
    public void routeChanged() {

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
