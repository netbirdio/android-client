package io.netbird.client.ui.advanced;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import io.netbird.client.databinding.FragmentAdvancedBinding;
import io.netbird.client.tool.Logcat;
import io.netbird.client.tool.Preferences;


public class AdvancedFragment extends Fragment {

    private static final String hiddenKey = "********";
    private static final String LOGTAG = "AdvancedFragment";

    private FragmentAdvancedBinding binding;
    private io.netbird.gomobile.android.Preferences goPreferences;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentAdvancedBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        String configFilePath = Preferences.configFile(inflater.getContext());
        goPreferences = new io.netbird.gomobile.android.Preferences(configFilePath);

        if (hasPreSharedKey(inflater.getContext())) {
            binding.presharedKey.setText(hiddenKey);
        } else {
            binding.presharedKey.setText("");
        }

        binding.btnSave.setOnClickListener(v -> {
            String presharedKey = binding.presharedKey.getText().toString().trim();

            if (presharedKey.equals(hiddenKey)) {
                return;
            }

            if (!isValidPresharedKey(presharedKey)) {
                binding.presharedKey.setError("Invalid key format");
                binding.presharedKey.requestFocus();
                return;
            }

            setPreSharedKey(presharedKey, inflater.getContext());
        });

        // Handle "Share Logs" button click
        binding.buttonShareLogs.setOnClickListener(v -> {
            shareLog();
        });

        Preferences preferences = new Preferences(inflater.getContext());
        binding.switchTraceLog.setChecked(preferences.isTraceLogEnabled());

        // Handle switch toggle
        binding.switchTraceLog.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                preferences.enableTraceLog();
            } else {
                preferences.disableTraceLog();
            }
        });

        // Initialize engine config switches
        initializeEngineConfigSwitches();

        return root;
    }

    private void initializeEngineConfigSwitches() {
        try {
            // Load current values from config
            binding.switchDisableClientRoutes.setChecked(goPreferences.getDisableClientRoutes());
            binding.switchDisableServerRoutes.setChecked(goPreferences.getDisableServerRoutes());
            binding.switchDisableDns.setChecked(goPreferences.getDisableDNS());
            binding.switchDisableFirewall.setChecked(goPreferences.getDisableFirewall());
            binding.switchAllowSsh.setChecked(goPreferences.getServerSSHAllowed());
            binding.switchBlockInbound.setChecked(goPreferences.getBlockInbound());

            // Set up change listeners
            binding.switchDisableClientRoutes.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    goPreferences.setDisableClientRoutes(isChecked);
                    goPreferences.commit();
                } catch (Exception e) {
                    Log.e(LOGTAG, "Failed to set disable client routes", e);
                }
            });

            binding.switchDisableServerRoutes.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    goPreferences.setDisableServerRoutes(isChecked);
                    goPreferences.commit();
                } catch (Exception e) {
                    Log.e(LOGTAG, "Failed to set disable server routes", e);
                }
            });

            binding.switchDisableDns.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    goPreferences.setDisableDNS(isChecked);
                    goPreferences.commit();
                } catch (Exception e) {
                    Log.e(LOGTAG, "Failed to set disable DNS", e);
                }
            });

            binding.switchDisableFirewall.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    goPreferences.setDisableFirewall(isChecked);
                    goPreferences.commit();
                } catch (Exception e) {
                    Log.e(LOGTAG, "Failed to set disable firewall", e);
                }
            });

            binding.switchAllowSsh.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    goPreferences.setServerSSHAllowed(isChecked);
                    goPreferences.commit();
                } catch (Exception e) {
                    Log.e(LOGTAG, "Failed to set server SSH allowed", e);
                }
            });

            binding.switchBlockInbound.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    goPreferences.setBlockInbound(isChecked);
                    goPreferences.commit();
                } catch (Exception e) {
                    Log.e(LOGTAG, "Failed to set block inbound", e);
                }
            });

        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to initialize engine config switches", e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private boolean isValidPresharedKey(String key) {
        if (key == null) {
            return false;
        }

        if (key.trim().isEmpty()) {
            return true;
        }

        String base64Pattern = "^[A-Za-z0-9+/=]{32,64}$";
        return key.matches(base64Pattern);
    }

    private void setPreSharedKey(String key, Context context) {
        String configFilePath = Preferences.configFile(context);
        io.netbird.gomobile.android.Preferences preferences = new io.netbird.gomobile.android.Preferences(configFilePath);
        preferences.setPreSharedKey(key);
    }

    private boolean hasPreSharedKey(Context context) {
        String configFilePath = Preferences.configFile(context);
        io.netbird.gomobile.android.Preferences preferences = new io.netbird.gomobile.android.Preferences(configFilePath);
        try {
            return !preferences.getPreSharedKey().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void shareLog() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        try {
            Logcat logcat = new Logcat(activity);
            logcat.dump();
        } catch (Exception e) {
            Log.e(LOGTAG, "failed to dump log", e);
        }
    }
}