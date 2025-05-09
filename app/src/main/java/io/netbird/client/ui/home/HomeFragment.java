package io.netbird.client.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

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

    private TextView textHostname;
    private TextView textNetworkAddress;
    private TextView textEngineStatus;
    private TextView textConnStatus;


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (context instanceof ServiceAccessor) {
            serviceAccessor = (ServiceAccessor) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement ServiceAccessor");
        }

        // Register this fragment as a service state listener
        if (context instanceof StateListenerRegistry) {
            ((StateListenerRegistry) context).registerServiceStateListener(this);
        }
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

        textEngineStatus = binding.textEngineStatus;
        homeViewModel.getText().observe(getViewLifecycleOwner(), textEngineStatus::setText);

        textConnStatus = binding.textConnectionStatus;
        homeViewModel.getText().observe(getViewLifecycleOwner(), textConnStatus::setText);

        updatePeerCount(0,0);

        final Button buttonConnect = binding.btnConnect;
        buttonConnect.setOnClickListener(v -> {
            if (serviceAccessor == null) {
                return;
            }

            serviceAccessor.switchConnection(true);
        });

        MaterialCardView openPanelCardView = binding.peersBtn;
        openPanelCardView.setOnClickListener(v -> {
            PeersFragment peerFragment = new PeersFragment();
            peerFragment.show(getParentFragmentManager(), peerFragment.getTag());
        });
        return root;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // Unregister this fragment as a service state listener
        if (getActivity() instanceof StateListenerRegistry) {
            ((StateListenerRegistry) getActivity()).unregisterServiceStateListener(this);
        }


        serviceAccessor = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onEngineStarted() {
        if (textEngineStatus != null) {
            textEngineStatus.post(() -> textEngineStatus.setText("Connected"));
        }
    }

    @Override
    public void onEngineStopped() {
        if (textEngineStatus != null) {
            textEngineStatus.post(() -> textEngineStatus.setText("Disconnected"));
        }
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
        if (textConnStatus == null) {
            return;
        }
        textConnStatus.post(() -> textEngineStatus.setText("Connected"));

    }

    @Override
    public void onConnecting() {
        if (textConnStatus == null) {
            return;
        }
        textConnStatus.post(() -> textEngineStatus.setText("Connecting"));
    }

    @Override
    public void onDisconnected() {
        if (textConnStatus == null) {
            return;
        }
        textConnStatus.post(() -> textEngineStatus.setText("Disconnected"));
    }

    @Override
    public void onDisconnecting() {
        if (textConnStatus == null) {
            return;
        }
        textConnStatus.post(() -> textEngineStatus.setText("Disconnecting"));
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
        TextView textPeersCount = binding.textOpenPanel;
        String text = getString(R.string.peers_connected, connectedPeers, totalPeers);
        textPeersCount.post(() ->
                textPeersCount.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY))
        );
    }
}