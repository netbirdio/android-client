package io.netbird.client.ui.about;

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
import androidx.fragment.app.Fragment;

import io.netbird.client.R;
import io.netbird.client.databinding.FragmentAboutBinding;

public class AboutFragment extends Fragment {

    private static final String LOGTAG = "AboutFragment";

    private FragmentAboutBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

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
            binding.txtVersionString.setText(R.string.about_version_unknown);
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
        openUrl("https://netbird.io/terms");
    }

    public void onPrivacyClick(View view) {
        openUrl("https://netbird.io/privacy");
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(LOGTAG, "No browser available to open " + url, e);
            Toast.makeText(requireContext(),
                    getString(R.string.settings_no_browser, url),
                    Toast.LENGTH_LONG).show();
        }
    }

}
