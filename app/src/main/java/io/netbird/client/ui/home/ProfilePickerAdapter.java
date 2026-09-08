package io.netbird.client.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.netbird.client.R;
import io.netbird.client.tool.Profile;

class ProfilePickerAdapter extends RecyclerView.Adapter<ProfilePickerAdapter.VH> {

    interface OnPickListener {
        void onPick(Profile profile);
    }

    private final List<Profile> profiles;
    private final OnPickListener listener;

    ProfilePickerAdapter(List<Profile> profiles, OnPickListener listener) {
        this.profiles = profiles;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_profile_picker, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Profile profile = profiles.get(position);
        holder.name.setText(profile.getName());

        String email = profile.getEmail();
        if (email == null || email.isEmpty()) {
            holder.email.setVisibility(View.GONE);
        } else {
            holder.email.setText(email);
            holder.email.setVisibility(View.VISIBLE);
        }

        holder.check.setVisibility(profile.isActive() ? View.VISIBLE : View.INVISIBLE);
        holder.dot.setBackgroundResource(profile.isActive()
                ? R.drawable.peer_status_connected
                : R.drawable.peer_status_disconnected);
        holder.itemView.setOnClickListener(v -> listener.onPick(profile));
    }

    @Override
    public int getItemCount() {
        return profiles.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final View dot;
        final TextView name;
        final TextView email;
        final ImageView check;

        VH(@NonNull View itemView) {
            super(itemView);
            dot = itemView.findViewById(R.id.profile_picker_dot);
            name = itemView.findViewById(R.id.profile_picker_name);
            email = itemView.findViewById(R.id.profile_picker_email);
            check = itemView.findViewById(R.id.profile_picker_check);
        }
    }
}
