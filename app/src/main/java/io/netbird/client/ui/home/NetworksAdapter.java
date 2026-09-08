package io.netbird.client.ui.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.databinding.ListItemResourceBinding;

public class NetworksAdapter extends RecyclerView.Adapter<NetworksAdapter.ResourceViewHolder> {
    public interface RouteSwitchToggleHandler {
        void handleSwitchToggle(String route, boolean isChecked) throws Exception;
    }

    private final List<Resource> resourcesList;
    private final List<RoutingPeer> peers;
    private final List<Resource> filteredResourcesList;
    private final RouteSwitchToggleHandler switchToggleHandler;
    private String filterQueryString = "";

    public NetworksAdapter(List<Resource> resourcesList, List<RoutingPeer> peers, RouteSwitchToggleHandler switchToggleHandler) {
        this.resourcesList = resourcesList;
        this.peers = peers;
        filteredResourcesList = new ArrayList<>(resourcesList);
        this.switchToggleHandler = switchToggleHandler;
        sort();
    }

    @NonNull
    @Override
    public ResourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Use ViewBinding to inflate the layout
        ListItemResourceBinding binding = ListItemResourceBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ResourceViewHolder(binding, switchToggleHandler);
    }

    @Override
    public void onBindViewHolder(@NonNull ResourceViewHolder holder, int position) {
        holder.bind(filteredResourcesList.get(position), peers);
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
            filteredResourcesList.addAll(resourcesList);
            return;
        }

        ArrayList<Resource> temporaryList = new ArrayList<>(resourcesList);
        for (Resource res : temporaryList) {
            if (res.getName().toLowerCase().contains(filterQueryString.toLowerCase())) {
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
        RouteSwitchToggleHandler switchToggleHandler;

        public ResourceViewHolder(ListItemResourceBinding binding, RouteSwitchToggleHandler switchToggleHandler) {
            super(binding.getRoot());
            this.binding = binding;
            this.switchToggleHandler = switchToggleHandler;
        }

        public void bind(Resource resource, List<RoutingPeer> peers) {
            binding.address.setText(resource.getAddress());
            binding.name.setText(resource.getName());
            binding.peer.setText(resource.getPeer());

            // Necessary when rebinding because onCheckedChangeListener is already set.
            binding.switchControl.setTag(true);

            binding.switchControl.setChecked(resource.isSelected());
            binding.switchControl.setTag(false);
            binding.switchControl.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    boolean tag = (boolean)binding.switchControl.getTag();
                    if (!tag) {
                        this.switchToggleHandler.handleSwitchToggle(resource.getName(), isChecked);
                    }
                } catch (Exception ignored) {
                    // This is done so that reversing the toggle action won't retrigger the toggle handler.
                    binding.switchControl.setTag(true);
                    binding.switchControl.setChecked(!isChecked);
                    binding.switchControl.setTag(false);
                }
            });

            if (resource.isExitNode()) {
                binding.exitNode.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.exitNode.setVisibility(android.view.View.GONE);
            }

        }
    }
}
