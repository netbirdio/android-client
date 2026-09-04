package io.netbird.client.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.R;
import io.netbird.client.databinding.SheetProfilePickerBinding;
import io.netbird.client.tool.Profile;
import io.netbird.client.tool.ProfileManagerWrapper;
import io.netbird.client.ui.profile.ProfileEditorDialog;
import io.netbird.client.ui.profile.ProfileUsageTracker;

public class ProfilePickerSheet extends BottomSheetDialogFragment {

    public interface OnProfileSwitchedListener {
        void onProfileSwitched(String newActiveName);
    }

    private static final String TAG = "ProfilePickerSheet";

    /** Beyond this many profiles the sheet shows only the most recent ones. */
    private static final int MAX_VISIBLE_PROFILES = 5;

    private SheetProfilePickerBinding binding;
    private ProfileManagerWrapper profileManager;
    private ProfileUsageTracker usageTracker;
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
        usageTracker = new ProfileUsageTracker(requireContext());

        adapter = new ProfilePickerAdapter(profiles, this::handlePickProfile);
        RecyclerView list = binding.profilePickerList;
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        binding.profilePickerAdd.setOnClickListener(v -> showAddDialog());

        binding.profilePickerShowAll.setOnClickListener(v -> openManageProfiles());
        binding.profilePickerManage.setOnClickListener(v -> openManageProfiles());

        loadProfiles();
    }

    @Override
    public void onStart() {
        super.onStart();
        // A long profile list would otherwise leave the sheet parked at peek height,
        // forcing the user to drag it up before they can pick anything.
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog == null) {
            return;
        }
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) {
            return;
        }
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void openManageProfiles() {
        dismiss();
        // Navigate the host activity's NavController to the manage-profiles destination.
        NavHostFragment.findNavController(requireParentFragment())
                .navigate(R.id.nav_profiles);
    }

    private void loadProfiles() {
        List<Profile> all = usageTracker.sortByRecency(profileManager.listProfiles());

        profiles.clear();
        profiles.addAll(all.size() > MAX_VISIBLE_PROFILES
                ? all.subList(0, MAX_VISIBLE_PROFILES)
                : all);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        if (binding == null) {
            return;
        }
        if (all.size() > MAX_VISIBLE_PROFILES) {
            binding.profilePickerShowAllText.setText(
                    getString(R.string.profile_picker_show_all, all.size()));
            binding.profilePickerShowAll.setVisibility(View.VISIBLE);
        } else {
            binding.profilePickerShowAll.setVisibility(View.GONE);
        }
    }

    private void handlePickProfile(Profile profile) {
        if (profile.isActive()) {
            dismiss();
            return;
        }
        try {
            profileManager.switchProfile(profile.getID());
            usageTracker.markUsed(profile.getID());

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
        ProfileEditorDialog.showCreate(requireContext(), profileManager, profile -> loadProfiles());
    }
}
