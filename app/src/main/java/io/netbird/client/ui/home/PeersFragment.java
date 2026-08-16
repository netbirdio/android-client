package io.netbird.client.ui.home;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.StateListenerRegistry;
import io.netbird.client.databinding.FragmentPeersBinding;
import io.netbird.client.ui.SegmentedSwitch;

public class PeersFragment extends Fragment {

    private FragmentPeersBinding binding;
    private ServiceAccessor serviceAccessor;
    private StateListenerRegistry stateListenerRegistry;
    private PeersFragmentViewModel model;
    private final List<Peer> peers = new ArrayList<>();
    // Which half of the segmented control is showing. Peers and Resources share
    // this screen because the bottom navigation is already at its five-item
    // limit, and Files holds the slot Resources used to have.
    private boolean showingResources;
    private static final String ARG_IS_RUNNING_ON_TV = "isRunningOnTV";

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ServiceAccessor) {
            serviceAccessor = (ServiceAccessor) context;
        } else {
            throw new RuntimeException(context + " must implement ServiceAccessor");
        }

        if (context instanceof StateListenerRegistry) {
            stateListenerRegistry = (StateListenerRegistry) context;
        } else {
            throw new RuntimeException(context + " must implement StateListenerRegistry");
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

        model = new ViewModelProvider(this).get(PeersFragmentViewModel.class);
        // The model survives configuration changes (e.g. language switch) while the
        // Activity behind the accessor does not — hand it the current one every time.
        model.setServiceAccessor(serviceAccessor);
        stateListenerRegistry.registerServiceStateListener(model.getStateListener());
        // Load eagerly: a fresh fragment (e.g. returning to this tab) would otherwise
        // sit on the zero-peers view until the next peer-change event happens to fire.
        model.refreshPeers();

        boolean isRunningOnTV = false;
        if (getArguments() != null) {
            isRunningOnTV = getArguments().getBoolean(ARG_IS_RUNNING_ON_TV, false);
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

        PeersAdapter adapter = new PeersAdapter(peers, this::showPeerDetail);

        RecyclerView peersRecyclerView = binding.peersRecyclerView;
        peersRecyclerView.setAdapter(adapter);
        peersRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        model.getUiState().observe(getViewLifecycleOwner(), uiState -> {
            peers.clear();
            peers.addAll(uiState.getPeers());

            updatePeersCounter(peers);

            // The zero-peers view replaces the whole list container, so it must
            // not claim the screen while the Resources view is the one showing.
            ZeroPeerView.updateVisibility(binding.zeroPeerLayout, binding.peersList,
                    showingResources || !peers.isEmpty());
            adapter.notifyDataSetChanged();
            adapter.filterBySearchQuery(binding.searchView.getText().toString());
        });

        new SegmentedSwitch(view, R.id.toggle_peers_resources, R.id.segment_thumb,
                R.id.btn_view_peers, R.id.label_view_peers,
                R.id.btn_view_resources, R.id.label_view_resources,
                this::showResources);

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

    /** Swaps the peer list and its search controls for the resource list. */
    private void showResources(boolean resources) {
        if (binding == null) {
            return;
        }
        showingResources = resources;

        int listVisibility = resources ? View.GONE : View.VISIBLE;
        binding.searchView.setVisibility(listVisibility);
        binding.filterIcon.setVisibility(listVisibility);
        binding.peersRecyclerView.setVisibility(listVisibility);
        binding.resourcesContainer.setVisibility(resources ? View.VISIBLE : View.GONE);

        if (resources) {
            binding.searchView.clearFocus();
        }
        ZeroPeerView.updateVisibility(binding.zeroPeerLayout, binding.peersList,
                resources || !peers.isEmpty());
    }

    @Override
    public void onDetach() {
        stateListenerRegistry = null;
        serviceAccessor = null;

        super.onDetach();
    }

    @Override
    public void onDestroyView() {
        if (model != null) {
            stateListenerRegistry.unregisterServiceStateListener(model.getStateListener());
            // Drop the Activity reference so a retained model can't leak it or keep
            // reading through a dead Activity's torn-down service connection.
            model.setServiceAccessor(null);
        }
        binding = null;
        super.onDestroyView();
    }

    private void showPeerDetail(Peer peer) {
        NavHostFragment.findNavController(this)
                .navigate(R.id.nav_peer_detail, PeerDetailFragment.argsFor(peer));
    }

    private void updatePeersCounter(List<Peer> peers) {
        TextView textPeersCount = binding.textOpenPanel;

        int connected = 0;

        for (var peer : peers) {
            if (peer.getStatus() == Status.CONNECTED) {
                connected++;
            }
        }

        String text = getString(R.string.peers_connected, connected, peers.size());
        textPeersCount.post(() ->
                textPeersCount.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY))
        );
    }
}

