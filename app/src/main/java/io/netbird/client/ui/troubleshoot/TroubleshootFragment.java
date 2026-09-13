package io.netbird.client.ui.troubleshoot;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.databinding.FragmentTroubleshootBinding;
import io.netbird.client.tool.Preferences;
import io.netbird.client.tool.ProfileManagerWrapper;

public class TroubleshootFragment extends Fragment {

    private static final String LOGTAG = "TroubleshootFragment";
    private FragmentTroubleshootBinding binding;

    // The system file picker behind "save to file". Registered at construction
    // because the contract has to exist before the fragment is created.
    private final ActivityResultLauncher<String> saveBundleLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"), this::saveDebugBundleTo);

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

        initializeRemoteJobsSwitch(inflater.getContext());

        binding.buttonDebugBundle.setOnClickListener(v -> {
            generateDebugBundle();
        });

        binding.buttonDebugBundleFile.setOnClickListener(v -> {
            saveBundleLauncher.launch(suggestedBundleName());
        });

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
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

        boolean anonymize = binding.switchAnonymize.isChecked();
        setBundleButtonsEnabled(false);
        new Thread(() -> {
            try {
                String key = ((ServiceAccessor) activity).debugBundle(anonymize);
                activity.runOnUiThread(() -> {
                    if (binding == null || !isAdded()) return;
                    setBundleButtonsEnabled(true);
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Debug bundle key", key);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(activity, getString(R.string.troubleshoot_key_copied), Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(LOGTAG, "failed to create debug bundle", e);
                activity.runOnUiThread(() -> {
                    if (binding == null || !isAdded()) return;
                    setBundleButtonsEnabled(true);
                    Toast.makeText(activity, getString(R.string.troubleshoot_bundle_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static String suggestedBundleName() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return "netbird-debug-" + stamp + ".zip";
    }

    /**
     * Generates the bundle and copies it into the document the user picked.
     * The engine writes the zip into the app cache; it is removed once copied.
     * On failure the picked document is deleted so no empty file is left behind.
     */
    private void saveDebugBundleTo(@Nullable Uri target) {
        if (target == null) {
            // picker dismissed
            return;
        }
        Activity activity = getActivity();
        if (binding == null || !(activity instanceof ServiceAccessor)) {
            return;
        }

        boolean anonymize = binding.switchAnonymize.isChecked();
        setBundleButtonsEnabled(false);
        new Thread(() -> {
            try {
                String path = ((ServiceAccessor) activity).debugBundleFile(anonymize);
                copyAndDelete(new File(path), activity.getContentResolver(), target);
                activity.runOnUiThread(() -> {
                    if (binding == null || !isAdded()) return;
                    setBundleButtonsEnabled(true);
                    Toast.makeText(activity, getString(R.string.troubleshoot_bundle_saved), Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(LOGTAG, "failed to save debug bundle", e);
                deleteDocument(activity.getContentResolver(), target);
                activity.runOnUiThread(() -> {
                    if (binding == null || !isAdded()) return;
                    setBundleButtonsEnabled(true);
                    Toast.makeText(activity, getString(R.string.troubleshoot_bundle_save_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static void copyAndDelete(File source, ContentResolver resolver, Uri target) throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = resolver.openOutputStream(target, "w")) {
            if (out == null) {
                throw new IOException("cannot open " + target);
            }
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        } finally {
            if (!source.delete()) {
                Log.w(LOGTAG, "could not remove temporary bundle " + source);
            }
        }
    }

    private static void deleteDocument(ContentResolver resolver, Uri target) {
        try {
            DocumentsContract.deleteDocument(resolver, target);
        } catch (Exception e) {
            Log.w(LOGTAG, "could not remove empty bundle document " + target, e);
        }
    }

    // Both actions run the same generator, so neither may start while the
    // other is busy.
    private void setBundleButtonsEnabled(boolean enabled) {
        if (binding == null) {
            return;
        }
        binding.buttonDebugBundle.setEnabled(enabled);
        binding.buttonDebugBundleFile.setEnabled(enabled);
    }
}
