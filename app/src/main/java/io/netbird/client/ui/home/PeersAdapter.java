package io.netbird.client.ui.home;

import static io.netbird.client.ui.home.PeersAdapter.FilterStatus.ALL;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.R;
import io.netbird.client.databinding.ListItemPeerBinding;

public class PeersAdapter extends RecyclerView.Adapter<PeersAdapter.PeerViewHolder> {


    public enum FilterStatus {
        ALL,
        IDLE,
        CONNECTING,
        CONNECTED,
    }

    private final List<Peer> peerList;
    private final List<Peer> filteredPeerList;

    private FilterStatus filterStatus = ALL;
    private String filterQueryString = "";

    public PeersAdapter(List<Peer> peerList) {
        this.peerList = peerList;
        filteredPeerList = new ArrayList<>(peerList);
        sortPeers();
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
        sortPeers();
        notifyDataSetChanged();
    }

    private void doFilterByStatus() {
        Status targetStatus;
        switch (filterStatus) {
            case IDLE:
                targetStatus = Status.IDLE;
                break;
            case CONNECTING:
                targetStatus = Status.CONNECTING;
                break;
            case CONNECTED:
                targetStatus = Status.CONNECTED;
                break;
            default:
                filteredPeerList.addAll(peerList);
                return;
        }

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

    private static void showPopup(View view, Peer peer) {
        PopupMenu popup = new PopupMenu(view.getContext(), view);
        popup.getMenuInflater().inflate(R.menu.peer_clipboard_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.copy_fqdn) {
                copyToClipboard(view.getContext(), "FQDN", peer.getFqdn());
                return true;
            } else if (id == R.id.copy_ip) {
                copyToClipboard(view.getContext(), "IP Address", peer.getIp());
                return true;
            }
            return false;
        });

        popup.show();
    }

    private static void copyToClipboard(Context context, String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
    }

    private void sortPeers() {
        filteredPeerList.sort((p1, p2) -> {
            int statusCompare = Boolean.compare(
                    p2.getStatus() == Status.CONNECTED,
                    p1.getStatus() == Status.CONNECTED
            );
            if (statusCompare != 0) {
                return statusCompare;
            }
            // Then sort alphabetically by fqdn
            return p1.getFqdn().compareToIgnoreCase(p2.getFqdn());
        });
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

            // Long press listener
            binding.getRoot().setOnLongClickListener(v -> {
                showPopup(v, peer);
                return true;
            });
        }
    }
}
