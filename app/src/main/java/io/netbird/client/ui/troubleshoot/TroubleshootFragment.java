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
import io.netbird.client.tool.ProfileManagerWrapper;

public class TroubleshootFragment extends Fragment implements AnonymizeLevelSheet.OnLevelChangedListener {

    private static final String LOGTAG = "TroubleshootFragment";

    private FragmentTroubleshootBinding binding;
    private Preferences preferences;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentTroubleshootBinding.inflate(inflater, container, false);

        preferences = new Preferences(inflater.getContext());
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

        updateAnonymizeValue();
        binding.anonymizeLayout.setOnClickListener(v ->
                AnonymizeLevelSheet.newInstance(preferences.getAnonymizeLevel())
                        .show(getChildFragmentManager(), "anonymize_level"));

        initializeRemoteJobsSwitch(inflater.getContext());

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

    @Override
    public void onAnonymizeLevelChanged(String level) {
        preferences.setAnonymizeLevel(level);
        if (binding != null) {
            updateAnonymizeValue();
        }
    }

    private void updateAnonymizeValue() {
        binding.anonymizeValue.setText(anonymizeLevelLabel(preferences.getAnonymizeLevel()));
    }

    private void initializeRemoteJobsSwitch(Context context) {
        try {
            String configFilePath = new ProfileManagerWrapper(context).getActiveConfigPath();
            io.netbird.gomobile.android.Preferences goPreferences = new io.netbird.gomobile.android.Preferences(configFilePath);
            binding.switchAllowRemoteJobs.setChecked(goPreferences.getRemoteJobsAllowed());
            binding.switchAllowRemoteJobs.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    goPreferences.setRemoteJobsAllowed(isChecked);
                    goPreferences.commit();
                } catch (Exception e) {
                    Log.e(LOGTAG, "Failed to set remote jobs allowed", e);
                }
            });
            binding.allowRemoteJobsLayout.setOnClickListener(v -> binding.switchAllowRemoteJobs.toggle());
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to initialize remote jobs switch", e);
        }
    }

    private void generateDebugBundle() {
        Activity activity = getActivity();
        if (activity == null || !(activity instanceof ServiceAccessor)) {
            return;
        }

        String level = preferences.getAnonymizeLevel();
        boolean anonymize = !Preferences.ANONYMIZE_LEVEL_NONE.equals(level);
        String anonymizeLevel = Preferences.ANONYMIZE_LEVEL_STRICT.equals(level)
                ? Preferences.ANONYMIZE_LEVEL_STRICT
                : Preferences.ANONYMIZE_LEVEL_DEFAULT;
        binding.buttonDebugBundle.setEnabled(false);
        new Thread(() -> {
            try {
                String key = ((ServiceAccessor) activity).debugBundle(anonymize, anonymizeLevel);
                activity.runOnUiThread(() -> {
                    if (binding == null || !isAdded()) return;
                    binding.buttonDebugBundle.setEnabled(true);
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Debug bundle key", key);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(activity, getString(R.string.troubleshoot_key_copied), Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(LOGTAG, "failed to create debug bundle", e);
                activity.runOnUiThread(() -> {
                    if (binding == null || !isAdded()) return;
                    binding.buttonDebugBundle.setEnabled(true);
                    Toast.makeText(activity, getString(R.string.troubleshoot_bundle_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static int anonymizeLevelLabel(String level) {
        switch (level) {
            case Preferences.ANONYMIZE_LEVEL_NONE:
                return R.string.troubleshoot_anonymize_none;
            case Preferences.ANONYMIZE_LEVEL_STRICT:
                return R.string.troubleshoot_anonymize_strict;
            default:
                return R.string.troubleshoot_anonymize_default;
        }
    }
}
