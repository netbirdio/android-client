package io.netbird.client.ui.home;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import java.util.ArrayList;
import java.util.List;

import io.netbird.client.R;
import io.netbird.client.databinding.ListItemResourceBinding;

public class NetworksAdapter extends RecyclerView.Adapter<NetworksAdapter.ResourceViewHolder> {


    private final List<Resource> resourcesList;
    private final List<Resource> filteredResourcesList;


    private String filterQueryString = "";

    public NetworksAdapter(List<Resource> resourcesList) {
        this.resourcesList = resourcesList;
        filteredResourcesList = new ArrayList<>(resourcesList);
        Log.i("NetworksAdapter", "Initial resources size: " + resourcesList.size() + " / "+ filteredResourcesList.size());
        sort();
    }

    @NonNull
    @Override
    public ResourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Use ViewBinding to inflate the layout
        ListItemResourceBinding binding = ListItemResourceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ResourceViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ResourceViewHolder holder, int position) {
        holder.bind(filteredResourcesList.get(position));
    }

    @Override
    public int getItemCount() {
        return filteredResourcesList.size();
    }

    public void filterBySearchQuery(String query) {
        filterQueryString = query;
        applyFilters();
    }

    private void applyFilters() {
        filteredResourcesList.clear();
        doFilterBySearchQuery();
        sort();
        notifyDataSetChanged();
    }

    private void doFilterBySearchQuery() {
        if (filterQueryString.isEmpty()) {
            return;
        }

        ArrayList<Resource> temporaryList = new ArrayList<>(resourcesList);
        for (Resource res : temporaryList) {
            if (res.getName().toLowerCase().contains(filterQueryString.toLowerCase())){
                filteredResourcesList.add(res);
            }
        }
    }

    private void sort() {
        filteredResourcesList.sort((p1, p2) -> {
            return p1.getName().compareToIgnoreCase(p2.getName());
        });
    }

    public static class ResourceViewHolder extends RecyclerView.ViewHolder {
        ListItemResourceBinding binding;

        public ResourceViewHolder(ListItemResourceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Resource resource) {
            binding.address.setText(resource.getAddress());
            binding.name.setText(resource.getName());
            binding.peer.setText(resource.getPeer());

            if (resource.getStatus() == Status.CONNECTED) {
                binding.verticalLine.setBackgroundResource(R.drawable.peer_status_connected); // Green for connected
            } else {
                binding.verticalLine.setBackgroundResource(R.drawable.peer_status_disconnected); // Red for disconnected
            }

            if(resource.isExitNode()) {
                binding.exitNode.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.exitNode.setVisibility(android.view.View.GONE);
            }

        }
    }
}
