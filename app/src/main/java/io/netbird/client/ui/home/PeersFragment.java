package io.netbird.client.ui.home;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.text.TextWatcher;
import android.text.Editable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
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
import io.netbird.gomobile.android.PeerInfo;
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

                // Remove gray background (dim)
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setDimAmount(0f);
                }
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


        PeerInfoArray peersInfo = serviceAccessor.getPeersList();
        if (peersInfo != null && peersInfo.size() > 0) {
            binding.textButtonLayout.setVisibility(View.GONE);
            binding.peersList.setVisibility(View.VISIBLE);
        } else {
            binding.textButtonLayout.setVisibility(View.VISIBLE);
            binding.peersList.setVisibility(View.GONE);
        }

        assert peersInfo != null;
        List<Peer> peerList = peersInfoToPeersList(peersInfo);
        updatePeerCount(peersInfo);
        peersListView = binding.peersRecyclerView;
        peersListView.setLayoutManager(new LinearLayoutManager(requireContext()));
        PeersAdapter adapter = new PeersAdapter(peerList);
        peersListView.setAdapter(adapter);

        binding.searchView.clearFocus();
        binding.searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filterBySearchQuery(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.searchView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // Hide the drawableStart icon when focused
                binding.searchView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            } else {
                // Show the drawableStart icon when not focused
                Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.search);
                binding.searchView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
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
            if(peer.getConnStatus().equalsIgnoreCase(Status.CONNECTED.toString())) {
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
