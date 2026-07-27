package io.netbird.client.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.netbird.client.R;
import io.netbird.client.tool.Profile;

public class ProfilesAdapter extends RecyclerView.Adapter<ProfilesAdapter.ProfileViewHolder> {

    /** ID of the built-in profile, which cannot be removed. Must match the Go core. */
    static final String DEFAULT_PROFILE_ID = "default";

    private final List<Profile> profiles;
    private final ProfileActionListener listener;

    public interface ProfileActionListener {
        void onSwitchProfile(Profile profile);
        void onRenameProfile(Profile profile);
        void onLogoutProfile(Profile profile);
        void onRemoveProfile(Profile profile);
    }

    public ProfilesAdapter(List<Profile> profiles, ProfileActionListener listener) {
        this.profiles = profiles;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_profile, parent, false);
        return new ProfileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
        Profile profile = profiles.get(position);
        holder.bind(profile, listener);
    }

    @Override
    public int getItemCount() {
        return profiles.size();
    }

    static class ProfileViewHolder extends RecyclerView.ViewHolder {
        private final TextView textName;
        private final TextView badgeActive;
        private final ImageView btnRename;
        private final Button btnSwitch;
        private final Button btnLogout;
        private final Button btnRemove;

        public ProfileViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_profile_name);
            badgeActive = itemView.findViewById(R.id.badge_active);
            btnRename = itemView.findViewById(R.id.btn_rename);
            btnSwitch = itemView.findViewById(R.id.btn_switch);
            btnLogout = itemView.findViewById(R.id.btn_logout);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }

        public void bind(Profile profile, ProfileActionListener listener) {
            textName.setText(profile.getName());

            if (profile.isActive()) {
                badgeActive.setVisibility(View.VISIBLE);
                btnSwitch.setEnabled(false);
                btnSwitch.setText(R.string.profiles_active);
            } else {
                badgeActive.setVisibility(View.GONE);
                btnSwitch.setEnabled(true);
                btnSwitch.setText(R.string.profiles_switch);
            }

            // Disable remove for the default profile. Keyed on ID, not name: the
            // name is user-editable and no longer identifies the default profile.
            boolean isDefault = DEFAULT_PROFILE_ID.equals(profile.getID());
            btnRemove.setEnabled(!isDefault);
            btnRemove.setAlpha(isDefault ? 0.5f : 1.0f);

            btnRename.setOnClickListener(v -> listener.onRenameProfile(profile));

            btnSwitch.setOnClickListener(v -> {
                if (!profile.isActive()) {
                    listener.onSwitchProfile(profile);
                }
            });

            btnLogout.setOnClickListener(v -> {
                listener.onLogoutProfile(profile);
            });

            btnRemove.setOnClickListener(v -> {
                if (!DEFAULT_PROFILE_ID.equals(profile.getID())) {
                    listener.onRemoveProfile(profile);
                }
            });
        }
    }
}
