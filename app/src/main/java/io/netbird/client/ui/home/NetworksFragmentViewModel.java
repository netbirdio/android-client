package io.netbird.client.ui.home;

import static androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewmodel.ViewModelInitializer;

import java.util.ArrayList;

import io.netbird.client.MyApplication;
import io.netbird.client.repository.VPNServiceRepository;
import io.reactivex.rxjava3.disposables.Disposable;

public class NetworksFragmentViewModel extends ViewModel {
    private final VPNServiceRepository repository;
    private final Disposable networkResourceGroupDisposable;
    private final MutableLiveData<NetworksFragmentUiState> uiState =
            new MutableLiveData<>(new NetworksFragmentUiState(new ArrayList<>(), new ArrayList<>()));

    public NetworksFragmentViewModel(VPNServiceRepository repository) {
        this.repository = repository;
        this.repository.bindService();
        this.networkResourceGroupDisposable = this.repository.observeNetworkResourceGroup()
                .subscribe(group -> uiState.postValue(
                        new NetworksFragmentUiState(group.getNetworkResources(), group.getRoutingPeers())));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        networkResourceGroupDisposable.dispose();
        repository.unbindService();
    }

    public LiveData<NetworksFragmentUiState> getUiState() {
        return uiState;
    }

    public void fetchNetworkResourceGroup() {
        repository.fetchNetworkResourceGroup();
    }

    static final ViewModelInitializer<NetworksFragmentViewModel> initializer = new ViewModelInitializer<>(
            NetworksFragmentViewModel.class,
            creationExtras -> {
                MyApplication app = (MyApplication) creationExtras.get(APPLICATION_KEY);
                assert app != null;
                return new NetworksFragmentViewModel(app.getVPNServiceRepository());
            }
    );

    public void selectRoute(String route) throws Exception {
        this.repository.selectRoute(route);
    }

    public void deselectRoute(String route) throws Exception {
        this.repository.deselectRoute(route);
    }
}
