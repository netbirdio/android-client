package io.netbird.client.ui.splittunneling;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.netbird.client.databinding.ListItemAppBinding;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {

    public interface OnAppToggledListener {
        void onAppToggled(String packageName, boolean selected);
    }

    private final List<AppEntry> apps = new ArrayList<>();
    private final List<AppEntry> filteredApps = new ArrayList<>();
    private final OnAppToggledListener toggleListener;

    private Set<String> selected;
    private String filterQueryString = "";

    public AppListAdapter(Set<String> selected, OnAppToggledListener toggleListener) {
        this.selected = selected;
        this.toggleListener = toggleListener;
    }

    public void submitApps(List<AppEntry> newApps) {
        apps.clear();
        apps.addAll(newApps);
        applyFilter();
    }

    /** Called when the mode changes, which swaps which of the two lists is shown. */
    public void setSelected(Set<String> selected) {
        this.selected = selected;
        notifyDataSetChanged();
    }

    public void filterBySearchQuery(String query) {
        filterQueryString = query == null ? "" : query;
        applyFilter();
    }

    private void applyFilter() {
        filteredApps.clear();
        if (filterQueryString.isEmpty()) {
            filteredApps.addAll(apps);
        } else {
            String needle = filterQueryString.toLowerCase(Locale.getDefault());
            for (AppEntry app : apps) {
                if (app.getLabel().toLowerCase(Locale.getDefault()).contains(needle)) {
                    filteredApps.add(app);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ListItemAppBinding binding = ListItemAppBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new AppViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        holder.bind(filteredApps.get(position));
    }

    @Override
    public int getItemCount() {
        return filteredApps.size();
    }

    class AppViewHolder extends RecyclerView.ViewHolder {

        private final ListItemAppBinding binding;

        AppViewHolder(ListItemAppBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AppEntry app) {
            binding.appName.setText(app.getLabel());
            binding.appPackage.setText(app.getPackageName());
            binding.appIcon.setImageDrawable(app.getIcon());

            // Cleared before setChecked so recycling a row into a different app
            // cannot fire a toggle the user never made.
            binding.switchControl.setOnCheckedChangeListener(null);
            binding.switchControl.setChecked(selected.contains(app.getPackageName()));
            binding.switchControl.setOnCheckedChangeListener((buttonView, isChecked) ->
                    toggleListener.onAppToggled(app.getPackageName(), isChecked));

            binding.getRoot().setOnClickListener(v -> binding.switchControl.toggle());
        }
    }
}
