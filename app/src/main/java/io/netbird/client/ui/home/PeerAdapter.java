package io.netbird.client.ui.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.netbird.client.R;
import io.netbird.client.databinding.ListItemPeerBinding;

public class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.PeerViewHolder> {

    private List<Peer> peerList;
    private @NonNull ListItemPeerBinding binding;

    // Constructor
    public PeerAdapter(List<Peer> peerList) {
        this.peerList = peerList;
    }

    @NonNull
    @Override
    public PeerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Use ViewBinding to inflate the layout
        binding = ListItemPeerBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new PeerViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PeerViewHolder holder, int position) {
        Peer peer = peerList.get(position);

        holder.binding.status.setText(peer.getStatus().toString());
        holder.binding.ip.setText(peer.getIp());
        holder.binding.fqdn.setText(peer.getFqdn());

        if (peer.getStatus() == Status.CONNECTED) {
            holder.binding.verticalLine.setBackgroundResource(R.drawable.peer_status_connected); // Green for connected
        } else {
            holder.binding.verticalLine.setBackgroundResource(R.drawable.peer_status_disconnected); // Red for disconnected
        }
    }

    @Override
    public int getItemCount() {
        return peerList.size();
    }

    public static class PeerViewHolder extends RecyclerView.ViewHolder {
        ListItemPeerBinding binding;

        public PeerViewHolder(ListItemPeerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
