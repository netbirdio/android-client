package io.netbird.client.ui.home;

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
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.R;
import io.netbird.client.databinding.SheetProfilePickerBinding;
import io.netbird.client.tool.Profile;
import io.netbird.client.tool.ProfileManagerWrapper;

public class ProfilePickerSheet extends BottomSheetDialogFragment {

    public interface OnProfileSwitchedListener {
        void onProfileSwitched(String newActiveName);
    }

    private static final String TAG = "ProfilePickerSheet";

    private SheetProfilePickerBinding binding;
    private ProfileManagerWrapper profileManager;
    private final List<Profile> profiles = new ArrayList<>();
    private ProfilePickerAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetProfilePickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        profileManager = new ProfileManagerWrapper(requireContext());

        adapter = new ProfilePickerAdapter(profiles, this::handlePickProfile);
        RecyclerView list = binding.profilePickerList;
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        binding.profilePickerAdd.setOnClickListener(v -> showAddDialog());

        binding.profilePickerManage.setOnClickListener(v -> {
            dismiss();
            // Navigate the host activity's NavController to the manage-profiles destination.
            NavHostFragment.findNavController(requireParentFragment())
                    .navigate(R.id.nav_profiles);
        });

        loadProfiles();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void loadProfiles() {
        profiles.clear();
        profiles.addAll(profileManager.listProfiles());
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void handlePickProfile(Profile profile) {
        if (profile.isActive()) {
            dismiss();
            return;
        }
        try {
            profileManager.switchProfile(profile.getName());

            if (getParentFragment() instanceof OnProfileSwitchedListener) {
                ((OnProfileSwitchedListener) getParentFragment()).onProfileSwitched(profile.getName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to switch profile", e);
            Toast.makeText(requireContext(),
                    "Failed to switch profile: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
        dismiss();
    }

    private void showAddDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_simple_edit_text, null);

        TextView txtTitle = dialogView.findViewById(R.id.text_title_dialog);
        txtTitle.setText(R.string.profiles_dialog_add_title);

        TextView txtMessage = dialogView.findViewById(R.id.text_label_dialog);
        txtMessage.setText(R.string.profiles_dialog_add_message);

        EditText input = dialogView.findViewById(R.id.edit_text_dialog);
        input.setHint(R.string.profiles_dialog_add_hint);

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btn_cancel_dialog).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_ok_dialog).setOnClickListener(v -> {
            String profileName = input.getText().toString().trim();
            if (profileName.isEmpty()) {
                Toast.makeText(requireContext(), R.string.profiles_error_empty_name, Toast.LENGTH_SHORT).show();
                return;
            }
            String sanitized = sanitizeProfileName(profileName);
            if (sanitized.isEmpty()) {
                Toast.makeText(requireContext(),
                        "Profile name must contain at least one letter, digit, underscore or hyphen",
                        Toast.LENGTH_LONG).show();
                return;
            }
            try {
                profileManager.addProfile(sanitized);
                loadProfiles();
            } catch (Exception e) {
                Log.e(TAG, "Failed to add profile", e);
                String msg = e.getMessage();
                if (msg != null && msg.contains("already exists")) {
                    Toast.makeText(requireContext(),
                            getString(R.string.profiles_error_already_exists, sanitized),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(),
                            "Failed to add profile: " + msg,
                            Toast.LENGTH_SHORT).show();
                }
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private static String sanitizeProfileName(String name) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                result.append(c);
            }
        }
        return result.toString();
    }
}
