package io.netbird.client.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.netbird.client.R;

class ExitNodePickerAdapter extends RecyclerView.Adapter<ExitNodePickerAdapter.VH> {

    interface OnPickListener {
        void onPick(Entry entry);
    }

    /** One pickable row: an exit node, or the "None" row when routeId is null. */
    static class Entry {
        @Nullable
        final String routeId;
        final String label;
        final boolean selected;

        Entry(@Nullable String routeId, String label, boolean selected) {
            this.routeId = routeId;
            this.label = label;
            this.selected = selected;
        }
    }

    private final List<Entry> entries;
    private final OnPickListener listener;

    ExitNodePickerAdapter(List<Entry> entries, OnPickListener listener) {
        this.entries = entries;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_exit_node_picker, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Entry entry = entries.get(position);
        holder.name.setText(entry.label);
        holder.check.setVisibility(entry.selected ? View.VISIBLE : View.INVISIBLE);
        holder.itemView.setOnClickListener(v -> listener.onPick(entry));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final ImageView check;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.exit_node_picker_name);
            check = itemView.findViewById(R.id.exit_node_picker_check);
        }
    }
}
