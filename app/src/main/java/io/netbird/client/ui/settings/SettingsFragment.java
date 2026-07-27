package io.netbird.client.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import io.netbird.client.R;
import io.netbird.client.databinding.FragmentSettingsBinding;
import io.netbird.client.tool.Profile;
import io.netbird.client.tool.ProfileManagerWrapper;

public class SettingsFragment extends Fragment {

    private static final String LOGTAG = "NBSettingsFragment";
    private static final String DOCS_URL = "https://docs.netbird.io";

    private FragmentSettingsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        NavController navController = NavHostFragment.findNavController(this);

        binding.rowProfiles.setOnClickListener(v ->
                navController.navigate(R.id.nav_profiles));

        binding.rowChangeServer.setOnClickListener(v ->
                navController.navigate(R.id.nav_change_server));

        binding.rowAdvanced.setOnClickListener(v ->
                navController.navigate(R.id.nav_advanced));

        binding.rowLanguage.setOnClickListener(v ->
                new LanguagePickerSheet().show(getChildFragmentManager(), "language_picker"));

        binding.rowTroubleshoot.setOnClickListener(v ->
                navController.navigate(R.id.nav_troubleshoot));

        binding.rowAbout.setOnClickListener(v ->
                navController.navigate(R.id.nav_about));

        binding.rowDocumentation.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(DOCS_URL));
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.w(LOGTAG, "No browser available to open documentation URL", e);
                Toast.makeText(requireContext(),
                        getString(R.string.settings_no_browser, DOCS_URL),
                        Toast.LENGTH_LONG).show();
            }
        });

        setVersionText();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateActiveProfileName();
        binding.currentLanguageName.setText(LanguagePickerSheet.currentLanguageLabel(requireContext()));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void updateActiveProfileName() {
        if (binding == null) return;
        try {
            ProfileManagerWrapper profileManager = new ProfileManagerWrapper(requireContext());
            Profile activeProfile = profileManager.getActiveProfile();
            binding.activeProfileName.setText(activeProfile != null ? activeProfile.getName() : "");
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to read active profile", e);
            binding.activeProfileName.setText("");
        }
    }

    private void setVersionText() {
        if (binding == null) return;
        try {
            String packageName = requireContext().getPackageName();
            String versionName = requireContext().getPackageManager()
                    .getPackageInfo(packageName, 0).versionName;
            binding.versionText.setText(getString(R.string.about_version) + versionName);
        } catch (Exception e) {
            binding.versionText.setText("");
        }
    }
}
