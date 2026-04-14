package io.netbird.client.ui.troubleshoot;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.databinding.FragmentTroubleshootBinding;
import io.netbird.client.tool.Preferences;

public class TroubleshootFragment extends Fragment {

    private static final String LOGTAG = "TroubleshootFragment";
    private FragmentTroubleshootBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentTroubleshootBinding.inflate(inflater, container, false);

        Preferences preferences = new Preferences(inflater.getContext());
        binding.switchTraceLog.setChecked(preferences.isTraceLogEnabled());

        binding.switchTraceLog.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                preferences.enableTraceLog();
            } else {
                preferences.disableTraceLog();
            }
        });

        binding.traceLogLayout.setOnClickListener(v -> {
            binding.switchTraceLog.toggle();
        });

        binding.anonymizeLayout.setOnClickListener(v -> {
            binding.switchAnonymize.toggle();
        });

        binding.buttonDebugBundle.setOnClickListener(v -> {
            generateDebugBundle();
        });

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void generateDebugBundle() {
        Activity activity = getActivity();
        if (activity == null || !(activity instanceof ServiceAccessor)) {
            return;
        }

        boolean anonymize = binding.switchAnonymize.isChecked();
        binding.buttonDebugBundle.setEnabled(false);
        new Thread(() -> {
            try {
                String key = ((ServiceAccessor) activity).debugBundle(anonymize);
                activity.runOnUiThread(() -> {
                    binding.buttonDebugBundle.setEnabled(true);
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Debug bundle key", key);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(activity, "Debug bundle key copied to clipboard", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(LOGTAG, "failed to create debug bundle", e);
                activity.runOnUiThread(() -> {
                    binding.buttonDebugBundle.setEnabled(true);
                    Toast.makeText(activity, "Failed to create debug bundle: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
