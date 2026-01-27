package io.netbird.client.ui.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.PeersStateListener;
import io.netbird.client.PeersStateListenerAdapter;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.StateListener;
import io.netbird.gomobile.android.PeerInfo;
import io.netbird.gomobile.android.PeerInfoArray;

public class PeersFragmentViewModel extends ViewModel implements PeersStateListener {
    private final PeersStateListenerAdapter peersAdapter;
    private final ServiceAccessor serviceAccessor;

    private final MutableLiveData<PeersFragmentUiState> uiState =
            new MutableLiveData<>(new PeersFragmentUiState(new ArrayList<>()));

    public PeersFragmentViewModel(ServiceAccessor serviceAccessor) {
        peersAdapter = new PeersStateListenerAdapter(this);
        this.serviceAccessor = serviceAccessor;
    }

    public static ViewModelProvider.Factory getFactory(ServiceAccessor serviceAccessor) {
        return new ViewModelProvider.Factory() {
            @NonNull
            @Override
            @SuppressWarnings("unchecked")
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                if (modelClass.isAssignableFrom(PeersFragmentViewModel.class)) {
                    return (T) new PeersFragmentViewModel(serviceAccessor);
                }
                throw new IllegalArgumentException("Unknown ViewModel class");
            }
        };
    }

    private List<Peer> getPeers(PeerInfoArray peersInfo) {
        List<Peer> peers = new ArrayList<>();
        PeerInfo peerInfo;
        String connStatus;
        Status status;

        for (int i = 0; i < peersInfo.size(); i++) {
            peerInfo = peersInfo.get(i);

            if (peerInfo == null) {
                continue;
            }

            connStatus = peerInfo.getConnStatus();
            if (connStatus == null) {
                continue;
            }

            status = Status.fromString(connStatus);
            peers.add(new Peer(status, peerInfo.getIP(), peerInfo.getFQDN()));
        }
        return peers;
    }

    public LiveData<PeersFragmentUiState> getUiState() {
        return uiState;
    }

    public StateListener getStateListener() {
        return this.peersAdapter;
    }

    @Override
    protected void onCleared() {
        peersAdapter.clearListener();
        super.onCleared();
    }

    @Override
    public void onPeersChanged(long totalPeers) {
        var peers = getPeers(serviceAccessor.getPeersList());
        this.uiState.postValue(new PeersFragmentUiState(peers));
    }
}
