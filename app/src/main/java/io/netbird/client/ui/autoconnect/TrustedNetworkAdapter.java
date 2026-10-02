package io.netbird.client.ui.autoconnect;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.databinding.ListItemTrustedNetworkBinding;
import io.netbird.client.tool.autoconnect.TrustedNetwork;

public class TrustedNetworkAdapter extends RecyclerView.Adapter<TrustedNetworkAdapter.NetworkViewHolder> {

    public interface OnRemoveListener {
        void onRemove(TrustedNetwork network);
    }

    private final List<TrustedNetwork> networks = new ArrayList<>();
    private final OnRemoveListener removeListener;

    public TrustedNetworkAdapter(List<TrustedNetwork> initial, OnRemoveListener removeListener) {
        this.removeListener = removeListener;
        networks.addAll(initial);
    }

    public void submitList(List<TrustedNetwork> updated) {
        networks.clear();
        networks.addAll(updated);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NetworkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ListItemTrustedNetworkBinding binding = ListItemTrustedNetworkBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new NetworkViewHolder(binding, removeListener);
    }

    @Override
    public void onBindViewHolder(@NonNull NetworkViewHolder holder, int position) {
        holder.bind(networks.get(position));
    }

    @Override
    public int getItemCount() {
        return networks.size();
    }

    static class NetworkViewHolder extends RecyclerView.ViewHolder {
        private final ListItemTrustedNetworkBinding binding;
        private final OnRemoveListener removeListener;

        NetworkViewHolder(ListItemTrustedNetworkBinding binding, OnRemoveListener removeListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.removeListener = removeListener;
        }

        void bind(TrustedNetwork network) {
            String primary = network.ssid != null ? network.ssid : network.bssid;
            binding.textNetworkPrimary.setText(primary);

            if (network.ssid != null && network.bssid != null) {
                binding.textNetworkSecondary.setText(network.bssid);
                binding.textNetworkSecondary.setVisibility(View.VISIBLE);
            } else {
                binding.textNetworkSecondary.setVisibility(View.GONE);
            }

            binding.btnRemoveNetwork.setOnClickListener(v -> removeListener.onRemove(network));
        }
    }
}
