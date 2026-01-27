package io.netbird.client.ui.profile;

import static android.view.View.GONE;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.R;
import io.netbird.client.tool.Profile;
import io.netbird.client.tool.ProfileManagerWrapper;

public class ProfilesFragment extends Fragment {
    private static final String TAG = "ProfilesFragment";
    private RecyclerView recyclerView;
    private ProfilesAdapter adapter;
    private ProfileManagerWrapper profileManager;
    private final List<Profile> profiles = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profiles, container, false);

        // Initialize profile manager
        profileManager = new ProfileManagerWrapper(requireContext());

        recyclerView = view.findViewById(R.id.recycler_profiles);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new ProfilesAdapter(profiles, new ProfilesAdapter.ProfileActionListener() {
            @Override
            public void onSwitchProfile(Profile profile) {
                showSwitchDialog(profile);
            }

            @Override
            public void onLogoutProfile(Profile profile) {
                showLogoutDialog(profile);
            }

            @Override
            public void onRemoveProfile(Profile profile) {
                showRemoveDialog(profile);
            }
        });
        recyclerView.setAdapter(adapter);

        FloatingActionButton btnAdd = view.findViewById(R.id.btn_add_profile);
        btnAdd.setOnClickListener(v -> showAddDialog());

        loadProfiles();

        return view;
    }

    private void loadProfiles() {
        profiles.clear();
        List<Profile> loadedProfiles = profileManager.listProfiles();
        profiles.addAll(loadedProfiles);
        adapter.notifyDataSetChanged();
    }

    interface DialogCallback {
        // Return true to dismiss dialog.
        boolean onConfirm(@Nullable String inputText);
    }

    @SuppressLint("InflateParams")
    private AlertDialog createDialog(String title, String message, @Nullable String inputHint, DialogCallback callback) {
        boolean hasInput = inputHint != null;

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_simple_edit_text, null);

        TextView txtTitle = dialogView.findViewById(R.id.text_title_dialog);
        txtTitle.setText(title);

        TextView txtMessage = dialogView.findViewById(R.id.text_label_dialog);
        txtMessage.setText(message);

        EditText input = dialogView.findViewById(R.id.edit_text_dialog);
        if (hasInput) {
            input.setHint(inputHint);
        } else {
            input.setVisibility(GONE);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btn_cancel_dialog).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_ok_dialog).setOnClickListener(v -> {
            String inputText = hasInput ? input.getText().toString().trim() : null;
            if (callback.onConfirm(inputText)) {
                dialog.dismiss();
            }
        });

        return dialog;
    }

    private void showAddDialog() {
        createDialog(
                getString(R.string.profiles_dialog_add_title),
                getString(R.string.profiles_dialog_add_message),
                getString(R.string.profiles_dialog_add_hint),
                profileName -> {
                    if (profileName == null || profileName.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.profiles_error_empty_name, Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    // Validate profile name based on go client sanitization rules
                    String sanitizedName = sanitizeProfileName(profileName);
                    if (sanitizedName.isEmpty()) {
                        Toast.makeText(requireContext(),
                                "Profile name must contain at least one letter, digit, underscore or hyphen",
                                Toast.LENGTH_LONG).show();
                        return false;
                    }

                    addProfile(profileName);
                    return true;
                }).show();
    }

    /**
     * Sanitizes profile name using the same rules as the go client.
     * Only keeps letters, digits, underscores, and hyphens.
     * This matches the sanitization in netbird/client/internal/profilemanager/profilemanager.go
     */
    private String sanitizeProfileName(String name) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                result.append(c);
            }
        }
        return result.toString();
    }

    private void showSwitchDialog(Profile profile) {
        createDialog(
                getString(R.string.profiles_dialog_switch_title),
                getString(R.string.profiles_dialog_switch_message, profile.getName()),
                null,
                ignored -> {
                    switchProfile(profile);
                    return true;
                }
        ).show();
    }

    private void showLogoutDialog(Profile profile) {
        createDialog(
                getString(R.string.profiles_dialog_logout_title),
                getString(R.string.profiles_dialog_logout_message, profile.getName()),
                null,
                ignored -> {
                    logoutProfile(profile);
                    return true;
                }
        ).show();
    }

    private void showRemoveDialog(Profile profile) {
        createDialog(
                getString(R.string.profiles_dialog_remove_title),
                getString(R.string.profiles_dialog_remove_message, profile.getName()),
                null,
                ignored -> {
                    removeProfile(profile);
                    return true;
                }
        ).show();
    }

    private void addProfile(String profileName) {
        try {
            profileManager.addProfile(profileName);
            Toast.makeText(requireContext(),
                    getString(R.string.profiles_success_added, profileName),
                    Toast.LENGTH_SHORT).show();
            loadProfiles();
        } catch (Exception e) {
            Log.e(TAG, "Failed to add profile", e);
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("already exists")) {
                Toast.makeText(requireContext(),
                        getString(R.string.profiles_error_already_exists, profileName),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(),
                        "Failed to add profile: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void switchProfile(Profile profile) {
        try {
            // Switch profile (VPN service will be stopped automatically in ProfileManagerWrapper)
            profileManager.switchProfile(profile.getName());

            Toast.makeText(requireContext(),
                    getString(R.string.profiles_success_switched, profile.getName()),
                    Toast.LENGTH_SHORT).show();

            loadProfiles();

            // Navigate back to home
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        } catch (Exception e) {
            Log.e(TAG, "Failed to switch profile", e);
            Toast.makeText(requireContext(),
                    "Failed to switch profile: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void logoutProfile(Profile profile) {
        try {
            // Logout from profile (VPN service will be stopped automatically if it's the active profile)
            profileManager.logoutProfile(profile.getName());

            Toast.makeText(requireContext(),
                    getString(R.string.profiles_success_logged_out, profile.getName()),
                    Toast.LENGTH_SHORT).show();

            loadProfiles();
        } catch (Exception e) {
            Log.e(TAG, "Failed to logout from profile", e);
            Toast.makeText(requireContext(),
                    "Failed to logout: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void removeProfile(Profile profile) {
        try {
            if (profile.getName().equals("default")) {
                Toast.makeText(requireContext(),
                        R.string.profiles_error_cannot_remove_default,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (profile.isActive()) {
                Toast.makeText(requireContext(),
                        R.string.profiles_error_cannot_remove_active,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            profileManager.removeProfile(profile.getName());
            Toast.makeText(requireContext(),
                    getString(R.string.profiles_success_removed, profile.getName()),
                    Toast.LENGTH_SHORT).show();
            loadProfiles();
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove profile", e);
            Toast.makeText(requireContext(),
                    "Failed to remove profile: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
