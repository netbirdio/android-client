package io.netbird.client.ui.home;

import static io.netbird.client.ui.home.PeersAdapter.FilterStatus.ALL;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.R;
import io.netbird.client.databinding.ListItemPeerBinding;

public class PeersAdapter extends RecyclerView.Adapter<PeersAdapter.PeerViewHolder> {


    public enum FilterStatus {
        ALL,
        CONNECTED,
        DISCONNECTED
    }

    private final List<Peer> peerList;
    private final List<Peer> filteredPeerList;

    private FilterStatus filterStatus = ALL;
    private String filterQueryString = "";

    public PeersAdapter(List<Peer> peerList) {
        this.peerList = peerList;
        filteredPeerList = new ArrayList<>(peerList);
    }

    @NonNull
    @Override
    public PeerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Use ViewBinding to inflate the layout
        io.netbird.client.databinding.ListItemPeerBinding binding = ListItemPeerBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new PeerViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PeerViewHolder holder, int position) {
        Peer peer = filteredPeerList.get(position);
        holder.bind(peer);
    }

    @Override
    public int getItemCount() {
        return filteredPeerList.size();
    }

    public void filterByStatus(FilterStatus status) {
        filterStatus = status;
        applyFilters();
    }


    public void filterBySearchQuery(String query) {
        filterQueryString = query;
        applyFilters();
    }

    private void applyFilters() {
        doFilterByStatus();
        doFilterBySearchQuery();
        notifyDataSetChanged();
    }

    private void doFilterByStatus() {
        filteredPeerList.clear();
        if (filterStatus == FilterStatus.ALL) {
            filteredPeerList.addAll(peerList);
            return;
        }

        Status targetStatus = filterStatus == FilterStatus.CONNECTED ?
                Status.CONNECTED : Status.DISCONNECTED;

        for (Peer peer : peerList) {
            if (peer.getStatus() == targetStatus) {
                filteredPeerList.add(peer);
            }
        }
    }

    private void doFilterBySearchQuery() {
        if (filterQueryString.isEmpty()) {
            return;
        }

        ArrayList<Peer> temporaryList = new ArrayList<>(filteredPeerList);
        for (Peer peer : temporaryList) {
            if (!peer.getFqdn().toLowerCase().contains(filterQueryString.toLowerCase())){
                filteredPeerList.remove(peer);
            }
        }
    }

    public static class PeerViewHolder extends RecyclerView.ViewHolder {
        ListItemPeerBinding binding;

        public PeerViewHolder(ListItemPeerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Peer peer) {
            binding.status.setText(peer.getStatus().toString());
            binding.ip.setText(peer.getIp());
            binding.fqdn.setText(peer.getFqdn());

            if (peer.getStatus() == Status.CONNECTED) {
                binding.verticalLine.setBackgroundResource(R.drawable.peer_status_connected); // Green for connected
            } else {
                binding.verticalLine.setBackgroundResource(R.drawable.peer_status_disconnected); // Red for disconnected
            }
        }
    }
}
