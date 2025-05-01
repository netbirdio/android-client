package io.netbird.client.ui.about;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import io.netbird.client.databinding.FragmentAboutBinding;

public class AboutFragment extends Fragment {

    private FragmentAboutBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        AboutViewModel model =
                new ViewModelProvider(this).get(AboutViewModel.class);

        binding = FragmentAboutBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Set version info text
        try {
            String packageName = requireContext().getPackageName();
            String versionName = requireContext()
                    .getPackageManager()
                    .getPackageInfo(packageName, 0).versionName;

            binding.txtVersionString.setText(versionName);
        } catch (Exception e) {
            binding.txtVersionString.setText("unknown");
        }

        binding.txtLicense.setOnClickListener(v -> onLicenseClick(v));
        binding.textPrivacy.setOnClickListener(v -> onPrivacyClick(v));

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public void onLicenseClick(View view) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://netbird.io/terms"));
        startActivity(intent);
    }

    public void onPrivacyClick(View view) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://netbird.io/privacy"));
        startActivity(intent);
    }

}
