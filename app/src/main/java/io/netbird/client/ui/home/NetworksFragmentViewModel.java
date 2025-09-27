package io.netbird.client.ui.home;

import static androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewmodel.ViewModelInitializer;

import java.util.ArrayList;

import io.netbird.client.MyApplication;
import io.netbird.client.repository.VPNServiceBindListener;
import io.netbird.client.repository.VPNServiceRepository;
import io.netbird.client.tool.RouteChangeListener;

public class NetworksFragmentViewModel extends ViewModel implements VPNServiceBindListener, RouteChangeListener {
    private final VPNServiceRepository repository;
    private final MutableLiveData<NetworksFragmentUiState> uiState =
            new MutableLiveData<>(new NetworksFragmentUiState(new ArrayList<>()));

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

    public void getResources() {
        var resources = repository.getNetworks();

        uiState.setValue(new NetworksFragmentUiState(resources));
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
        getResources();
    }

    @Override
    public void onRouteChanged(String routes) {
        var resources = repository.getNetworks();

        // This value will be set from a background thread.
        uiState.postValue(new NetworksFragmentUiState(resources));
    }

    public void selectRoute(String route) {
        this.repository.selectRoute(route);
    }

    public void deselectRoute(String route) {
        this.repository.deselectRoute(route);
    }
}
