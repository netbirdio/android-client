package io.netbird.client.ui.home;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.text.TextWatcher;
import android.text.Editable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.databinding.FragmentPeersBinding;
import io.netbird.gomobile.android.PeerInfo;
import io.netbird.gomobile.android.PeerInfoArray;

public class PeersFragment extends Fragment {

    private FragmentPeersBinding binding;
    private ServiceAccessor serviceAccessor;
    private RecyclerView peersListView;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ServiceAccessor) {
            serviceAccessor = (ServiceAccessor) context;
        } else {
            throw new RuntimeException(context + " must implement ServiceAccessor");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPeersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        boolean isRunningOnTV = false;
        if (getArguments() != null) {
            isRunningOnTV = getArguments().getBoolean("isRunningOnTV", false);
        }

        // Hide "Learn why" button on TV and make it non-focusable
        if (isRunningOnTV) {
            binding.zeroPeerLayout.btnLearnWhy.setVisibility(View.GONE);
            // Also make search and filter non-focusable on TV when drawer is open
            binding.searchView.setFocusable(false);
            binding.searchView.setFocusableInTouchMode(false);
            binding.filterIcon.setFocusable(false);
            binding.filterIcon.setFocusableInTouchMode(false);
        } else {
            ZeroPeerView.setupLearnWhyClick(binding.zeroPeerLayout, requireContext());
        }

        PeerInfoArray peersInfo = serviceAccessor.getPeersList();
        ZeroPeerView.updateVisibility(binding.zeroPeerLayout, binding.peersList, peersInfo.size() > 0);

        List<Peer> peerList = peersInfoToPeersList(peersInfo);
        updatePeerCount(peersInfo);
        peersListView = binding.peersRecyclerView;
        peersListView.setLayoutManager(new LinearLayoutManager(requireContext()));
        PeersAdapter adapter = new PeersAdapter(peerList);
        peersListView.setAdapter(adapter);

        binding.searchView.clearFocus();
        binding.searchView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filterBySearchQuery(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.searchView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                binding.searchView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            } else {
                Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.search);
                binding.searchView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
            }
        });

        binding.filterIcon.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), binding.filterIcon);
            popup.getMenuInflater().inflate(R.menu.peer_filter_menu, popup.getMenu());

            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.all) {
                    adapter.filterByStatus(PeersAdapter.FilterStatus.ALL);
                } else if (itemId == R.id.idle) {
                    adapter.filterByStatus(PeersAdapter.FilterStatus.IDLE);
                } else if (itemId == R.id.connecting) {
                    adapter.filterByStatus(PeersAdapter.FilterStatus.CONNECTING);
                } else if (itemId == R.id.connected) {
                    adapter.filterByStatus(PeersAdapter.FilterStatus.CONNECTED);
                }
                return true;
            });

            popup.show();
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
        serviceAccessor = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private List<Peer> peersInfoToPeersList(PeerInfoArray peersInfo) {
        List<Peer> peerList = new ArrayList<>();
        for (int i = 0; i < peersInfo.size(); i++) {
            PeerInfo peerInfo = peersInfo.get(i);
            Status status = Status.fromString(peerInfo.getConnStatus());
            peerList.add(new Peer(status, peerInfo.getIP(), peerInfo.getFQDN()));
        }
        return peerList;
    }

    private void updatePeerCount(PeerInfoArray peersInfo) {
        int connected = 0;
        for (int i = 0; i < peersInfo.size(); i++) {
            PeerInfo peer = peersInfo.get(i);
            if (peer.getConnStatus().equalsIgnoreCase(Status.CONNECTED.toString())) {
                connected++;
            }
        }

        TextView textPeersCount = binding.textOpenPanel;
        String text = getString(R.string.peers_connected, connected, peersInfo.size());
        textPeersCount.post(() ->
                textPeersCount.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY))
        );
    }
}

