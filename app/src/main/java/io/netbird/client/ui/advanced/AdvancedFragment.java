package io.netbird.client.ui.advanced;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import io.netbird.client.R;
import io.netbird.client.databinding.ComponentSwitchBinding;
import io.netbird.client.databinding.FragmentAdvancedBinding;
import io.netbird.client.tool.Logcat;
import io.netbird.client.tool.Preferences;


public class AdvancedFragment extends Fragment {

    private static final String hiddenKey = "********";
    private static final String LOGTAG = "AdvancedFragment";

    private FragmentAdvancedBinding binding;
    private io.netbird.gomobile.android.Preferences goPreferences;

    private void showReconnectionNeededWarningDialog() {
        final View dialogView = getLayoutInflater().inflate(R.layout.dialog_simple_alert_message, null);
        final AlertDialog alertDialog = new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                .setView(dialogView)
                .create();

        ((TextView)dialogView.findViewById(R.id.txt_dialog)).setText(R.string.reconnectionNeededWarningMessage);
        dialogView.findViewById(R.id.btn_ok_dialog).setOnClickListener(v -> alertDialog.dismiss());
        alertDialog.show();
    }

    private void configureForceRelayConnectionSwitch(@NonNull ComponentSwitchBinding binding, @NonNull Preferences preferences) {
        binding.switchTitle.setText(R.string.advanced_force_relay_conn);
        binding.switchDescription.setText(R.string.advanced_force_relay_conn_desc);

        binding.switchControl.setChecked(preferences.isConnectionForceRelayed());
        binding.switchControl.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                preferences.enableForcedRelayConnection();
            } else {
                preferences.disableForcedRelayConnection();
            }

            showReconnectionNeededWarningDialog();
        });
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        String configFilePath = Preferences.configFile(inflater.getContext());
        goPreferences = new io.netbird.gomobile.android.Preferences(configFilePath);

        binding = FragmentAdvancedBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

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

        // Enable trace logs
        Preferences preferences = new Preferences(inflater.getContext());
        binding.switchTraceLog.setChecked(preferences.isTraceLogEnabled());

        // Handle trace log switch toggle
        binding.switchTraceLog.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                preferences.enableTraceLog();
            } else {
                preferences.disableTraceLog();
            }
        });

        // Handle "Share Logs" button click
        binding.buttonShareLogs.setOnClickListener(v -> {
            shareLog();
        });

        // Rosenpass settings
        try {
            binding.switchRosenpass.setChecked(goPreferences.getRosenpassEnabled());
            if (!binding.switchRosenpass.isChecked()) {
                binding.switchRosenpassPermissive.setEnabled(false);
            } else {
                binding.switchRosenpassPermissive.setChecked(goPreferences.getRosenpassPermissive());
            }

        } catch (Exception e) {
            Log.e(LOGTAG, "Error getting Rosenpass settings", e);
            Toast.makeText(inflater.getContext(), "Error: " + e, Toast.LENGTH_SHORT).show();
            binding.switchRosenpass.setChecked(false);
            binding.switchRosenpassPermissive.setEnabled(false);
        }

        binding.switchRosenpass.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                goPreferences.setRosenpassEnabled(true);
                binding.switchRosenpassPermissive.setEnabled(true);

            } else {
                goPreferences.setRosenpassEnabled(false);
                binding.switchRosenpassPermissive.setEnabled(false);
                binding.switchRosenpassPermissive.setChecked(false);
            }

            try {
                goPreferences.commit();
            } catch (Exception e) {
                Log.e(LOGTAG, "Error committing Rosenpass settings", e);
                Toast.makeText(inflater.getContext(), "Error: " + e.toString(), Toast.LENGTH_SHORT).show();
            }
        });

        binding.switchRosenpassPermissive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            goPreferences.setRosenpassPermissive(isChecked);
            try {
                goPreferences.commit();
            } catch (Exception e) {
                Log.e(LOGTAG, "Error committing Rosenpass settings", e);
                Toast.makeText(inflater.getContext(), "Error: " + e, Toast.LENGTH_SHORT).show();
            }
        });

        configureForceRelayConnectionSwitch(binding.layoutForceRelayConnection, preferences);

        // Initialize engine config switches (your settings)
        initializeEngineConfigSwitches();

        // Theme-picker initialisieren
        SharedPreferences sharedPreferences = inflater.getContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        int themeMode = sharedPreferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        RadioGroup radioGroup = binding.radioGroupTheme;
        if (themeMode == AppCompatDelegate.MODE_NIGHT_NO) {
            radioGroup.check(binding.radioThemeLight.getId());
        } else if (themeMode == AppCompatDelegate.MODE_NIGHT_YES) {
            radioGroup.check(binding.radioThemeDark.getId());
        } else {
            radioGroup.check(binding.radioThemeSystem.getId());
        }
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (checkedId == binding.radioThemeLight.getId()) {
                mode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == binding.radioThemeDark.getId()) {
                mode = AppCompatDelegate.MODE_NIGHT_YES;
            }
            sharedPreferences.edit().putInt("theme_mode", mode).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
        });

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
        try {
            preferences.setPreSharedKey(key);
            preferences.commit();
            Toast.makeText(context, "Pre-shared key saved successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to save pre-shared key", e);
            Toast.makeText(context, "Error saving key: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
