package io.netbird.client.ui.home;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netbird.client.PeersStateListener;
import io.netbird.client.PeersStateListenerAdapter;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.StateListener;
import io.netbird.gomobile.android.PeerInfo;
import io.netbird.gomobile.android.PeerInfoArray;
import io.netbird.gomobile.android.PeerRoutes;

public class PeersFragmentViewModel extends ViewModel implements PeersStateListener {
    private static final String TAG = "PeersFragmentViewModel";

    private final PeersStateListenerAdapter peersAdapter;

    // The accessor is the Activity, which this ViewModel outlives across configuration
    // changes (language switch, rotation). The fragment re-supplies the current one on
    // every view creation and clears it on view destruction; holding a reference
    // captured at construction would keep reading through a dead Activity's unbound
    // service connection forever.
    private volatile ServiceAccessor serviceAccessor;

    // serializes peer-list refreshes off the UI thread; serviceAccessor.getPeersList()
    // is a JNI call into Go that can take seconds during engine bootstrap/teardown
    private final ExecutorService refreshExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isCleared = new AtomicBoolean(false);

    private final MutableLiveData<PeersFragmentUiState> uiState =
            new MutableLiveData<>(new PeersFragmentUiState(new ArrayList<>()));

    public PeersFragmentViewModel() {
        peersAdapter = new PeersStateListenerAdapter(this);
    }

    public void setServiceAccessor(@Nullable ServiceAccessor serviceAccessor) {
        this.serviceAccessor = serviceAccessor;
    }

    private List<Peer> getPeers(PeerInfoArray peersInfo) {
        List<Peer> peers = new ArrayList<>();
        PeerInfo peerInfo;
        Status status;

        for (int i = 0; i < peersInfo.size(); i++) {
            peerInfo = peersInfo.get(i);

            if (peerInfo == null) {
                continue;
            }

            status = Status.fromLong(peerInfo.getConnStatus());
            peers.add(new Peer(
                    status,
                    peerInfo.getIP(),
                    peerInfo.getIPv6(),
                    peerInfo.getFQDN(),
                    peerInfo.getPubKey(),
                    peerInfo.getLatency(),
                    peerInfo.getLatencyMs(),
                    peerInfo.getBytesRx(),
                    peerInfo.getBytesTx(),
                    peerInfo.getConnStatusUpdate(),
                    peerInfo.getRelayed(),
                    peerInfo.getRosenpassEnabled(),
                    peerInfo.getLastWireguardHandshake(),
                    peerInfo.getLocalIceCandidateType(),
                    peerInfo.getRemoteIceCandidateType(),
                    peerInfo.getLocalIceCandidateEndpoint(),
                    peerInfo.getRemoteIceCandidateEndpoint(),
                    getRoutes(peerInfo)));
        }
        return peers;
    }

    private static List<String> getRoutes(PeerInfo peerInfo) {
        PeerRoutes peerRoutes = peerInfo.getPeerRoutes();
        if (peerRoutes == null) {
            return Collections.emptyList();
        }

        List<String> routes = new ArrayList<>();
        for (int i = 0; i < peerRoutes.size(); i++) {
            try {
                routes.add(peerRoutes.get(i));
            } catch (Exception e) {
                // The list is read back index by index, so a shrinking list mid-read
                // is the only way this throws; the routes gathered so far still stand.
                Log.w(TAG, "Failed to read route at index " + i, e);
                break;
            }
        }
        // The Go side iterates a map, so the order changes between fetches; sort for
        // a stable display and so Peer's value equality isn't defeated by shuffling.
        Collections.sort(routes);
        return routes;
    }

    public LiveData<PeersFragmentUiState> getUiState() {
        return uiState;
    }

    public StateListener getStateListener() {
        return this.peersAdapter;
    }

    @Override
    protected void onCleared() {
        isCleared.set(true);
        peersAdapter.clearListener();
        refreshExecutor.shutdown();
        super.onCleared();
    }

    @Override
    public void onPeersChanged(long totalPeers) {
        refreshPeers();
    }

    /**
     * Re-reads the peer list off the UI thread. Transfer counters and handshake
     * times only advance when the list is fetched, so the detail screen calls this
     * to pull fresh numbers without waiting for a peer-list change event.
     */
    public void refreshPeers() {
        if (isCleared.get()) {
            return;
        }
        try {
            refreshExecutor.execute(() -> {
                ServiceAccessor accessor = serviceAccessor;
                if (accessor == null) {
                    return;
                }
                PeerInfoArray peersInfo = accessor.getPeersList();
                if (peersInfo == null) {
                    // Service not bound (yet): keep the last snapshot instead of
                    // flashing an empty list. Another refresh follows once the binder
                    // arrives and the engine replays its state.
                    return;
                }
                uiState.postValue(new PeersFragmentUiState(getPeers(peersInfo)));
            });
        } catch (RejectedExecutionException ignored) {
            // executor shut down concurrently in onCleared; safe to drop
        }
    }
}
