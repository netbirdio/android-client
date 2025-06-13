package io.netbird.client.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.airbnb.lottie.LottieAnimationView;

import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.StateListener;
import io.netbird.client.StateListenerRegistry;
import io.netbird.client.databinding.FragmentHomeBinding;
import io.netbird.client.ui.PreferenceUI;
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
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        textHostname = binding.textHostname;
        textNetworkAddress = binding.textNetworkAddress;
        TextView textConnStatus = binding.textConnectionStatus;

        updatePeerCount(0,0);


        buttonConnect = binding.btnConnect;
        if(buttonAnimation == null) {
            buttonAnimation = new ButtonAnimation();
        }
        buttonAnimation.refresh(buttonConnect, textConnStatus);

        buttonConnect.setOnClickListener(v -> {
            if (serviceAccessor == null) {
                return;
            }

            if (isConnected) {
                // We're currently connected, so disconnect
                buttonConnect.setEnabled(false);
                buttonAnimation.disconnecting();
                serviceAccessor.switchConnection(false);
                invalidateRouteChange();
            } else {
                // We're currently disconnected, so connect
                buttonAnimation.connecting();
                serviceAccessor.switchConnection(true);
            }
        });

        // route change notification
        if(PreferenceUI.routeChangedNotification(requireContext())) {
            binding.btnRouteChanged.setVisibility(View.VISIBLE);
        } else {
            binding.btnRouteChanged.setVisibility(View.GONE);
        }

        binding.btnRouteChanged.setOnClickListener(v -> {
            requireActivity().runOnUiThread(() -> {
                showRouteChangeNotificationDialog(requireContext());
                invalidateRouteChange();
            });
        });

        // peers button
        FrameLayout openPanelCardView = binding.peersBtn;
        openPanelCardView.setOnClickListener(v -> {
            BottomDialogFragment fragment = new BottomDialogFragment();
            fragment.show(getParentFragmentManager(), fragment.getTag());
        });

        stateListenerRegistry.registerServiceStateListener(this);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        buttonAnimation.destroy();
        stateListenerRegistry.unregisterServiceStateListener(this);
        binding = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        serviceAccessor = null;
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
            invalidateRouteChange();
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
    public void routeChanged() {
        if(binding == null) {
            return;
        }
        binding.btnRouteChanged.setVisibility(View.VISIBLE);
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
            invalidateRouteChange();

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

    private void showRouteChangeNotificationDialog(Context context) {
        requireActivity().runOnUiThread(() -> {
            final View dialogView = getLayoutInflater().inflate(R.layout.dialog_route_changed, null);
            final AlertDialog alertDialog = new AlertDialog.Builder(context)
                    .setView(dialogView)
                    .create();

            dialogView.findViewById(R.id.btn_close).setOnClickListener(v -> {
                alertDialog.dismiss();
            });

            alertDialog.show();
        });
    }

    private void invalidateRouteChange() {
        PreferenceUI.routeChangedNotificationInvalidate(requireContext());
        binding.btnRouteChanged.setVisibility(View.GONE);
    }
}