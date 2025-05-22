package io.netbird.client.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.card.MaterialCardView;

import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.StateListener;
import io.netbird.client.StateListenerRegistry;
import io.netbird.client.databinding.FragmentHomeBinding;
import io.netbird.gomobile.android.PeerInfo;
import io.netbird.gomobile.android.PeerInfoArray;

public class HomeFragment extends Fragment implements StateListener {

    private FragmentHomeBinding binding;
    private ServiceAccessor serviceAccessor;
    private StateListenerRegistry stateListenerRegistry;

    private TextView textHostname;
    private TextView textNetworkAddress;

    private LottieAnimationView buttonConnect;
    private ButtonAnimation buttonAnimation;
    private boolean isConnected;


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof ServiceAccessor) {
            serviceAccessor = (ServiceAccessor) context;
        } else {
            throw new RuntimeException(context + " must implement ServiceAccessor");
        }

        if(context instanceof StateListenerRegistry) {
            stateListenerRegistry = (StateListenerRegistry) context;
        } else {
            throw new RuntimeException(context + " must implement StateListenerRegistry");
        }
        stateListenerRegistry.registerServiceStateListener(this);

    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        textHostname = binding.textHostname;
        homeViewModel.getText().observe(getViewLifecycleOwner(), textHostname::setText);

        textNetworkAddress = binding.textNetworkAddress;
        homeViewModel.getText().observe(getViewLifecycleOwner(), textNetworkAddress::setText);

        TextView textConnStatus = binding.textConnectionStatus;
        homeViewModel.getText().observe(getViewLifecycleOwner(), textConnStatus::setText);

        updatePeerCount(0,0);

        buttonConnect = binding.btnConnect;
        buttonAnimation = new ButtonAnimation(buttonConnect, textConnStatus);
        buttonConnect.setOnClickListener(v -> {
            if (serviceAccessor == null) {
                return;
            }

            // Disable button immediately
            buttonConnect.setEnabled(false);

            if (isConnected) {
                // We're currently connected, so disconnect
                buttonAnimation.disconnecting();
                serviceAccessor.switchConnection(false);
            } else {
                // We're currently disconnected, so connect
                buttonAnimation.connecting();
                serviceAccessor.switchConnection(true);
            }
        });

        FrameLayout openPanelCardView = binding.peersBtn;
        openPanelCardView.setOnClickListener(v -> {
            PeersFragment peerFragment = new PeersFragment();
            peerFragment.show(getParentFragmentManager(), peerFragment.getTag());
        });
        return root;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        stateListenerRegistry.unregisterServiceStateListener(this);
        // todo teardown animation

        serviceAccessor = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onEngineStarted() {
    }

    @Override
    public void onEngineStopped() {
        isConnected = false;
        buttonConnect.post(() -> {
            buttonAnimation.disconnected();
            buttonConnect.setEnabled(true);
        });
    }

    @Override
    public void onAddressChanged(String netAddr, String hostname) {
        if(textNetworkAddress == null || textHostname == null) {
            return;
        }

        textNetworkAddress.post(() -> textNetworkAddress.setText(netAddr));
        textHostname.post(() -> textHostname.setText(hostname));
    }

    @Override
    public void onConnected() {
        isConnected = true;

        buttonConnect.post(() -> {
            buttonAnimation.connected();
            buttonConnect.setEnabled(true);
        });
    }

    @Override
    public void onConnecting() {
        buttonConnect.post(() -> buttonAnimation.connecting());
    }

    @Override
    public void onDisconnected() {
        isConnected = false;
        buttonConnect.post(() -> {
            buttonAnimation.disconnected();
            buttonConnect.setEnabled(true);
        });
        updatePeerCount(0, 0);
    }

    @Override
    public void onDisconnecting() {
        buttonConnect.post(() -> buttonAnimation.disconnecting());
    }

    @Override
    public void onPeersListChanged(long numberOfPeers) {
        PeerInfoArray peersList = serviceAccessor.getPeersList();
        int connected = 0;
        for (int i = 0; i < peersList.size(); i++) {
            PeerInfo peer = peersList.get(i);
            if(peer.getConnStatus().equalsIgnoreCase(Status.CONNECTED.toString())) {
                connected++;
            }
        }
        updatePeerCount(connected, peersList.size());
    }

    private void updatePeerCount(int connectedPeers, long totalPeers) {
        if(binding==null) return;
        TextView textPeersCount = binding.textOpenPanel;
        String text = getString(R.string.peers_connected, connectedPeers, totalPeers);
        textPeersCount.post(() ->
                textPeersCount.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY))
        );
    }
}