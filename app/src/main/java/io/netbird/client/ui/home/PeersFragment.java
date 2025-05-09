package io.netbird.client.ui.home;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.databinding.FragmentPeersBinding;
import io.netbird.gomobile.android.PeerInfoArray;

public class PeersFragment extends BottomSheetDialogFragment {

    private FragmentPeersBinding binding;
    private ServiceAccessor serviceAccessor;
    private RecyclerView peersListView;


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (context instanceof ServiceAccessor) {
            serviceAccessor = (ServiceAccessor) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement ServiceAccessor");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Apply transparent background theme to the dialog
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);

        dialog.setOnShowListener(dialogInterface -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                // Set the bottom sheet to be full screen
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                behavior.setPeekHeight(0);
                bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.requestLayout();

                // Set the background to transparent
                bottomSheet.setBackground(new ColorDrawable(Color.TRANSPARENT));
            }
        });
        return dialog;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPeersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Set rounded corners background on your sheet content
        view.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_top_corners));

        binding.buttonClose.setOnClickListener(v -> dismiss());

        binding.btnLearnWhy.setOnClickListener(v -> {
            String url = "https://docs.netbird.io/how-to/manage-network-access";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });


        PeerInfoArray peers = serviceAccessor.getPeersList();
        /*
        if (peers != null && peers.size() > 0) {
            binding.textButtonLayout.setVisibility(View.GONE);
            binding.peersList.setVisibility(View.VISIBLE);
        } else {
            binding.textButtonLayout.setVisibility(View.VISIBLE);
            binding.peersList.setVisibility(View.GONE);
        }

         */
        binding.textButtonLayout.setVisibility(View.GONE);
        binding.peersList.setVisibility(View.VISIBLE);

        // Create sample data
        List<Peer> peerList = new ArrayList<>();
        peerList.add(new Peer(Status.DISCONNECTED, "192.168.1.1", "peer1.local"));
        peerList.add(new Peer(Status.CONNECTED, "192.168.1.2", "peer2.local"));
        peerList.add(new Peer(Status.CONNECTED, "192.168.1.3", "peer3.local"));
        peerList.add(new Peer(Status.DISCONNECTED, "192.168.1.4", "peer4.local"));
        peerList.add(new Peer(Status.DISCONNECTED, "192.168.1.5", "peer5.local"));
        peerList.add(new Peer(Status.CONNECTED, "192.168.1.6", "peer6.local"));
        peerList.add(new Peer(Status.DISCONNECTED, "192.168.1.7", "peer7.local"));
        peerList.add(new Peer(Status.CONNECTED, "192.168.1.8", "peer8.local"));
        peerList.add(new Peer(Status.DISCONNECTED, "192.168.1.9", "peer9.local"));
        peerList.add(new Peer(Status.CONNECTED, "192.168.1.10", "peer10.local"));
        peerList.add(new Peer(Status.DISCONNECTED, "192.168.1.11", "peer11.local"));
        peerList.add(new Peer(Status.DISCONNECTED, "192.168.1.12", "peer12.local"));
        peerList.add(new Peer(Status.CONNECTED, "192.168.1.13", "peer13.local"));
        peerList.add(new Peer(Status.DISCONNECTED, "192.168.1.14", "peer14.local"));
        peerList.add(new Peer(Status.CONNECTED, "192.168.1.15", "peer15.local"));
        peerList.add(new Peer(Status.DISCONNECTED, "192.168.1.16", "peer16.local"));
        peerList.add(new Peer(Status.CONNECTED, "192.168.1.17", "peer17.local"));
        peerList.add(new Peer(Status.DISCONNECTED, "192.168.1.18", "peer18.local"));
        peerList.add(new Peer(Status.CONNECTED, "192.168.1.19", "peer19.local"));
        peerList.add(new Peer(Status.DISCONNECTED, "192.168.1.20", "peer20.local"));

        peersListView = binding.peersRecyclerView;
        peersListView.setLayoutManager(new LinearLayoutManager(requireContext()));
        PeersAdapter adapter = new PeersAdapter(peerList);
        peersListView.setAdapter(adapter);

        binding.searchView.clearFocus();
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.filterBySearchQuery(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filterBySearchQuery(newText);
                return true;
            }
        });

        binding.filterIcon.setOnClickListener(v -> {
            androidx.appcompat.widget.PopupMenu popup = new PopupMenu(requireContext(), binding.filterIcon);
            popup.getMenuInflater().inflate(R.menu.peer_filter_menu, popup.getMenu());

            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.all) {
                    adapter.filterByStatus(PeersAdapter.FilterStatus.ALL);
                    return true;
                } else if (itemId == R.id.connected) {
                    adapter.filterByStatus(PeersAdapter.FilterStatus.CONNECTED);
                    return true;
                } else if (itemId == R.id.disconnected) {
                    adapter.filterByStatus(PeersAdapter.FilterStatus.DISCONNECTED);
                    return true;
                }

                return false;
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
}
