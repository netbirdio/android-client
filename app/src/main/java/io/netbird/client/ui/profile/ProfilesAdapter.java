package io.netbird.client.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
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
        void onEditProfile(Profile profile);
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
        private final TextView textEmail;
        private final TextView badgeActive;
        private final ImageView btnMenu;

        public ProfileViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_profile_name);
            textEmail = itemView.findViewById(R.id.text_profile_email);
            badgeActive = itemView.findViewById(R.id.badge_active);
            btnMenu = itemView.findViewById(R.id.btn_profile_menu);
        }

        public void bind(Profile profile, ProfileActionListener listener) {
            textName.setText(profile.getName());

            String email = profile.getEmail();
            if (email == null || email.isEmpty()) {
                textEmail.setVisibility(View.GONE);
            } else {
                textEmail.setText(email);
                textEmail.setVisibility(View.VISIBLE);
            }

            badgeActive.setVisibility(profile.isActive() ? View.VISIBLE : View.GONE);

            itemView.setOnClickListener(v -> {
                if (!profile.isActive()) {
                    listener.onSwitchProfile(profile);
                }
            });

            btnMenu.setOnClickListener(v -> showActionsMenu(v, profile, listener));
            itemView.setOnLongClickListener(v -> {
                showActionsMenu(btnMenu, profile, listener);
                return true;
            });
        }

        private static void showActionsMenu(View anchor, Profile profile,
                                            ProfileActionListener listener) {
            PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
            popup.getMenuInflater().inflate(R.menu.profile_actions_menu, popup.getMenu());

            // Remove is hidden for the default profile. Keyed on ID, not name: the
            // name is user-editable and no longer identifies the default profile.
            popup.getMenu().findItem(R.id.profile_action_remove)
                    .setVisible(!DEFAULT_PROFILE_ID.equals(profile.getID()));

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.profile_action_edit) {
                    listener.onEditProfile(profile);
                    return true;
                } else if (id == R.id.profile_action_logout) {
                    listener.onLogoutProfile(profile);
                    return true;
                } else if (id == R.id.profile_action_remove) {
                    listener.onRemoveProfile(profile);
                    return true;
                }
                return false;
            });

            popup.show();
        }
    }
}
