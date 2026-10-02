package io.netbird.client.ui.advanced;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import io.netbird.client.R;
import io.netbird.client.databinding.FragmentAdvancedBinding;
import io.netbird.client.tool.MDMBridge;
import io.netbird.client.tool.MDMRestrictions;
import io.netbird.client.tool.Preferences;
import io.netbird.client.tool.ProfileManagerWrapper;
import io.netbird.client.ui.MDMLock;


public class AdvancedFragment extends Fragment implements ThemePickerSheet.OnThemeChangedListener {

    private static final String hiddenKey = "********";
    private static final String LOGTAG = "AdvancedFragment";

    private FragmentAdvancedBinding binding;
    private io.netbird.gomobile.android.Preferences goPreferences;
    private String configFilePath;
    private MDMRestrictions mdm = MDMRestrictions.EMPTY;

    private void showReconnectionNeededWarningDialog() {
        final View dialogView = getLayoutInflater().inflate(R.layout.dialog_simple_alert_message, null);
        final AlertDialog alertDialog = new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                .setView(dialogView)
                .create();

        ((TextView)dialogView.findViewById(R.id.txt_dialog)).setText(R.string.reconnectionNeededWarningMessage);
        dialogView.findViewById(R.id.btn_ok_dialog).setOnClickListener(v -> alertDialog.dismiss());
        alertDialog.show();
    }

    private void configureForceRelayConnectionSwitch(@NonNull Preferences preferences) {
        binding.switchForceRelayConnection.setChecked(preferences.isConnectionForceRelayed());
        binding.switchForceRelayConnection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                preferences.enableForcedRelayConnection();
            } else {
                preferences.disableForcedRelayConnection();
            }

            showReconnectionNeededWarningDialog();
        });

        binding.layoutForceRelayConnection.setOnClickListener(v -> binding.switchForceRelayConnection.toggle());
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        // Get config path from ProfileManager instead of constructing it
        ProfileManagerWrapper profileManager = new ProfileManagerWrapper(inflater.getContext());
        try {
            configFilePath = profileManager.getActiveConfigPath();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get config path: " + e.getMessage(), e);
        }
        goPreferences = MDMBridge.openPreferences(inflater.getContext(), configFilePath);
        mdm = MDMBridge.restrictions(inflater.getContext());

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

        Preferences preferences = new Preferences(inflater.getContext());

        // Rosenpass settings
        try {
            binding.switchRosenpass.setChecked(goPreferences.getRosenpassEnabled());
            setPermissiveEnabled(binding.switchRosenpass.isChecked());
            if (binding.switchRosenpass.isChecked()) {
                binding.switchRosenpassPermissive.setChecked(goPreferences.getRosenpassPermissive());
            }

        } catch (Exception e) {
            Log.e(LOGTAG, "Error getting Rosenpass settings", e);
            Toast.makeText(inflater.getContext(), getString(R.string.error_generic, e.toString()), Toast.LENGTH_SHORT).show();
            binding.switchRosenpass.setChecked(false);
            setPermissiveEnabled(false);
        }

        binding.switchRosenpass.setOnCheckedChangeListener((buttonView, isChecked) -> {
            goPreferences.setRosenpassEnabled(isChecked);
            setPermissiveEnabled(isChecked);
            // Only when it is ours to drive: toggling a managed switch would fire
            // its listener and stage a write the policy is about to refuse.
            if (!isChecked && !permissiveManaged()) {
                binding.switchRosenpassPermissive.setChecked(false);
            }
            commit();
        });

        binding.layoutRosenpas.setOnClickListener(v -> binding.switchRosenpass.toggle());

        binding.switchRosenpassPermissive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            goPreferences.setRosenpassPermissive(isChecked);
            commit();
        });

        binding.layoutRosenpassPermissive.setOnClickListener(v -> binding.switchRosenpassPermissive.toggle());

        configureForceRelayConnectionSwitch(preferences);

        // Initialize engine config switches (your settings)
        initializeEngineConfigSwitches();

        // Theme picker row
        SharedPreferences sharedPreferences = inflater.getContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        int themeMode = sharedPreferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        binding.themeValue.setText(ThemePickerSheet.labelFor(requireContext(), themeMode));

        binding.rowTheme.setOnClickListener(v -> {
            ThemePickerSheet sheet = new ThemePickerSheet();
            sheet.show(getChildFragmentManager(), "ThemePickerSheet");
        });

        // Last: locking a row takes away the listeners installed above.
        applyMDMPolicy();

        return root;
    }

    /**
     * Locks what an administrator decided.
     *
     * The switches already show the enforced values — the Go preferences answer
     * with the policy's value for a managed key — so this only has to stop the
     * user changing them and say why. disableUpdateSettings is the blunt case:
     * the organisation allows no configuration changes at all, so every setting
     * that reaches the config goes read-only.
     *
     * The theme row is left alone. It is how the screen looks to this user on
     * this device, not configuration the organisation is managing.
     */
    private void applyMDMPolicy() {
        boolean everything = mdm.features.disableUpdateSettings;

        if (everything || mdm.mdm.preSharedKey) {
            MDMLock.lockControls(binding.presharedKey, binding.btnSave);
        }
        lockRow(everything || mdm.mdm.rosenpassEnabled,
                binding.layoutRosenpas, binding.switchRosenpass);
        lockRow(everything || mdm.mdm.rosenpassPermissive,
                binding.layoutRosenpassPermissive, binding.switchRosenpassPermissive);
        lockRow(everything || mdm.mdm.allowServerSSH != null,
                binding.layoutAllowSsh, binding.switchAllowSsh);
        lockRow(everything || mdm.mdm.disableClientRoutes,
                binding.layoutDisableClientRoutes, binding.switchDisableClientRoutes);
        lockRow(everything || mdm.mdm.disableServerRoutes,
                binding.layoutDisableServerRoutes, binding.switchDisableServerRoutes);
        lockRow(everything || mdm.mdm.blockInbound,
                binding.layoutBlockInbound, binding.switchBlockInbound);
        lockRow(everything, binding.layoutDisableDns, binding.switchDisableDns);
        lockRow(everything, binding.layoutDisableFirewall, binding.switchDisableFirewall);
        lockRow(everything, binding.layoutDisableIpv6, binding.switchDisableIpv6);
        lockRow(everything, binding.layoutForceRelayConnection, binding.switchForceRelayConnection);
    }

    private void lockRow(boolean managed, View row, View control) {
        if (managed) {
            MDMLock.lock(row, control);
        }
    }

    /**
     * Rosenpass permissive follows Rosenpass itself — off with it, on with it —
     * unless the policy holds it, in which case it stays where the policy put it.
     */
    private void setPermissiveEnabled(boolean enabled) {
        binding.switchRosenpassPermissive.setEnabled(enabled && !permissiveManaged());
    }

    private boolean permissiveManaged() {
        return mdm.features.disableUpdateSettings || mdm.mdm.rosenpassPermissive;
    }

    /** Writes the staged settings, reporting a policy refusal as one. */
    private void commit() {
        try {
            goPreferences.commit();
        } catch (Exception e) {
            // A refused write stays staged on the Go side, so every later commit
            // through this instance would be refused for the same reason, long
            // after the user moved on to another setting. Start again from what
            // is actually on disk.
            goPreferences = MDMBridge.openPreferences(requireContext(), configFilePath);
            reportWriteFailure(requireContext(), e);
        }
    }

    /**
     * A write the policy refused is not an error the user can do anything about,
     * so it is named as what it is. Go reports it with the offending keys, which
     * are worth repeating: the screen locks what it knows is managed, and a
     * refusal here means something managed that it did not know about.
     */
    private void reportWriteFailure(Context context, Exception e) {
        String keys = MDMRestrictions.rejectedKeys(e.getMessage());
        if (keys == null) {
            Log.e(LOGTAG, "Failed to save the setting", e);
            Toast.makeText(context, getString(R.string.error_generic, e.toString()),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(context, keys.isEmpty()
                        ? getString(R.string.mdm_managed_setting)
                        : getString(R.string.mdm_managed_setting_keys, keys),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onThemeChanged(int mode) {
        if (binding != null) {
            binding.themeValue.setText(ThemePickerSheet.labelFor(requireContext(), mode));
        }
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
            binding.switchDisableIpv6.setChecked(goPreferences.getDisableIPv6());

            // Set up change listeners
            binding.switchDisableClientRoutes.setOnCheckedChangeListener((buttonView, isChecked) -> {
                goPreferences.setDisableClientRoutes(isChecked);
                commit();
            });

            binding.switchDisableServerRoutes.setOnCheckedChangeListener((buttonView, isChecked) -> {
                goPreferences.setDisableServerRoutes(isChecked);
                commit();
            });

            binding.switchDisableDns.setOnCheckedChangeListener((buttonView, isChecked) -> {
                goPreferences.setDisableDNS(isChecked);
                commit();
            });

            binding.switchDisableFirewall.setOnCheckedChangeListener((buttonView, isChecked) -> {
                goPreferences.setDisableFirewall(isChecked);
                commit();
            });

            binding.switchAllowSsh.setOnCheckedChangeListener((buttonView, isChecked) -> {
                goPreferences.setServerSSHAllowed(isChecked);
                commit();
            });

            binding.switchBlockInbound.setOnCheckedChangeListener((buttonView, isChecked) -> {
                goPreferences.setBlockInbound(isChecked);
                commit();
            });

            binding.switchDisableIpv6.setOnCheckedChangeListener((buttonView, isChecked) -> {
                goPreferences.setDisableIPv6(isChecked);
                commit();
            });

            // Make parent rows clickable to toggle switches (for TV remote)
            binding.layoutAllowSsh.setOnClickListener(v -> binding.switchAllowSsh.toggle());
            binding.layoutBlockInbound.setOnClickListener(v -> binding.switchBlockInbound.toggle());
            binding.layoutDisableClientRoutes.setOnClickListener(v -> binding.switchDisableClientRoutes.toggle());
            binding.layoutDisableServerRoutes.setOnClickListener(v -> binding.switchDisableServerRoutes.toggle());
            binding.layoutDisableDns.setOnClickListener(v -> binding.switchDisableDns.toggle());
            binding.layoutDisableFirewall.setOnClickListener(v -> binding.switchDisableFirewall.toggle());

            binding.layoutDisableIpv6.setOnClickListener(v -> {
                binding.switchDisableIpv6.toggle();
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
        ProfileManagerWrapper profileManager = new ProfileManagerWrapper(context);
        String configFilePath;
        try {
            configFilePath = profileManager.getActiveConfigPath();
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.error_config_path, e.getMessage()), Toast.LENGTH_LONG).show();
            return;
        }
        io.netbird.gomobile.android.Preferences preferences = MDMBridge.openPreferences(context, configFilePath);
        try {
            preferences.setPreSharedKey(key);
            preferences.commit();
            Toast.makeText(context, R.string.advanced_presharedkey_saved_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            reportWriteFailure(context, e);
        }
    }

    private boolean hasPreSharedKey(Context context) {
        ProfileManagerWrapper profileManager = new ProfileManagerWrapper(context);
        String configFilePath;
        try {
            configFilePath = profileManager.getActiveConfigPath();
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to get config path", e);
            return false;
        }
        io.netbird.gomobile.android.Preferences preferences = MDMBridge.openPreferences(context, configFilePath);
        try {
            // Asks whether there is a key rather than for the key itself: a
            // policy-supplied one is never handed to the native layer, and the
            // field only ever shows the placeholder anyway.
            return preferences.hasPreSharedKey();
        } catch (Exception e) {
            return false;
        }
    }

}
