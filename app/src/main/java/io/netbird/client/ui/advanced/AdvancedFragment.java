package io.netbird.client.ui.advanced;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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
import io.netbird.client.tool.Preferences;
import io.netbird.client.tool.ProfileManagerWrapper;


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
        
        // Make parent layout clickable to toggle switch (for TV remote)
        binding.getRoot().setOnClickListener(v -> {
            binding.switchControl.toggle();
        });
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        // Get config path from ProfileManager instead of constructing it
        ProfileManagerWrapper profileManager = new ProfileManagerWrapper(inflater.getContext());
        String configFilePath;
        try {
            configFilePath = profileManager.getActiveConfigPath();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get config path: " + e.getMessage(), e);
        }
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

        Preferences preferences = new Preferences(inflater.getContext());

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
        
        // Make parent layout clickable to toggle switch (for TV remote)
        binding.layoutRosenpas.setOnClickListener(v -> {
            binding.switchRosenpass.toggle();
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
        
        // Make parent layout clickable to toggle switch (for TV remote)
        binding.layoutRosenpassPermissive.setOnClickListener(v -> {
            binding.switchRosenpassPermissive.toggle();
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

            // Connection mode + timeouts (Phase 3.7h Android UI). Default
            // selection is "Follow server" (index 0 in connection_mode_entries),
            // which clears any local override. Selecting an explicit mode
            // unhides the timeout fields below.
            initializeConnectionModeUI();

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

            // Make parent layouts clickable to toggle switches (for TV remote)
            binding.layoutAllowSsh.setOnClickListener(v -> {
                binding.switchAllowSsh.toggle();
            });

            binding.layoutBlockInbound.setOnClickListener(v -> {
                binding.switchBlockInbound.toggle();
            });
            
            binding.layoutDisableClientRoutes.setOnClickListener(v -> {
                binding.switchDisableClientRoutes.toggle();
            });
            
            binding.layoutDisableServerRoutes.setOnClickListener(v -> {
                binding.switchDisableServerRoutes.toggle();
            });
            
            binding.layoutDisableDns.setOnClickListener(v -> {
                binding.switchDisableDns.toggle();
            });
            
            binding.layoutDisableFirewall.setOnClickListener(v -> {
                binding.switchDisableFirewall.toggle();
            });

        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to initialize engine config switches", e);
        }
    }

    /**
     * Mapping from spinner position to canonical connection-mode string.
     * Index 0 = "Follow server" -> empty string clears the local override
     * so the daemon uses the server-pushed value. Other entries set an
     * explicit local override that wins over the server value.
     *
     * Order matches connection_mode_entries (res/values/connection_mode_array.xml):
     * Follow server, relay-forced, p2p, p2p-lazy, p2p-dynamic.
     */
    private static final String[] CONNECTION_MODE_VALUES = new String[] {
            "",             // 0: Follow server
            "relay-forced", // 1
            "p2p",          // 2
            "p2p-lazy",     // 3
            "p2p-dynamic"   // 4
    };

    private void initializeConnectionModeUI() {
        try {
            // Build a theme-aware adapter so the dropdown uses our nb_txt color
            // and the popup picks up nb_bg in dark mode.
            // The "Follow server" entry gets a "(currently: <mode>)" suffix
            // that surfaces the value the management server most recently
            // pushed -- refreshed on every spinner-touch in case the engine
            // was not connected yet when the fragment first opened.
            refreshConnectionModeAdapter();

            // Hydrate spinner from current persisted local override.
            String currentMode = goPreferences.getConnectionMode();
            int selectedIdx = 0;
            for (int i = 0; i < CONNECTION_MODE_VALUES.length; i++) {
                if (CONNECTION_MODE_VALUES[i].equals(currentMode)) {
                    selectedIdx = i;
                    break;
                }
            }
            binding.spinnerConnectionMode.setSelection(selectedIdx);
            updateTimeoutsVisibility(selectedIdx);

            // Hydrate timeout fields with the locally-stored override (if any).
            // Empty when no override is set so the user sees the field is
            // currently inactive; the hint text shows the server-pushed
            // default for that field as guidance.
            long relay = goPreferences.getRelayTimeoutSeconds();
            long p2p = goPreferences.getP2pTimeoutSeconds();
            long retry = goPreferences.getP2pRetryMaxSeconds();
            binding.editRelayTimeout.setText(relay == 0 ? "" : String.valueOf(relay));
            binding.editP2pTimeout.setText(p2p == 0 ? "" : String.valueOf(p2p));
            binding.editP2pRetryMax.setText(retry == 0 ? "" : String.valueOf(retry));

            // Refresh the "(currently: ...)" suffix every time the spinner is
            // touched. Cheap (just a getter call to the engine), and covers
            // the case where the user opens this fragment before the daemon
            // received its first PeerConfig.
            binding.spinnerConnectionMode.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    refreshConnectionModeAdapter();
                }
                return false;
            });

            binding.spinnerConnectionMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    try {
                        goPreferences.setConnectionMode(CONNECTION_MODE_VALUES[position]);
                        goPreferences.commit();
                    } catch (Exception e) {
                        Log.e(LOGTAG, "Failed to set connection mode", e);
                    }
                    updateTimeoutsVisibility(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) { }
            });

            wireTimeoutEditOnBlur(binding.editRelayTimeout, "relay", v -> {
                try { goPreferences.setRelayTimeoutSeconds(v); goPreferences.commit(); }
                catch (Exception e) { Log.e(LOGTAG, "Failed to set relay timeout", e); }
            });
            wireTimeoutEditOnBlur(binding.editP2pTimeout, "p2p", v -> {
                try { goPreferences.setP2pTimeoutSeconds(v); goPreferences.commit(); }
                catch (Exception e) { Log.e(LOGTAG, "Failed to set p2p timeout", e); }
            });
            wireTimeoutEditOnBlur(binding.editP2pRetryMax, "p2pRetryMax", v -> {
                try { goPreferences.setP2pRetryMaxSeconds(v); goPreferences.commit(); }
                catch (Exception e) { Log.e(LOGTAG, "Failed to set p2p retry max", e); }
            });
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to initialize connection mode UI", e);
        }
    }

    private void refreshConnectionModeAdapter() {
        if (binding == null) return;
        String[] base = getResources().getStringArray(R.array.connection_mode_entries);
        String[] entries = base.clone();
        String pushed = "";
        try {
            if (requireActivity() instanceof io.netbird.client.ServiceAccessor) {
                pushed = ((io.netbird.client.ServiceAccessor) requireActivity())
                        .getServerPushedConnectionMode();
            }
        } catch (Throwable t) {
            Log.d(LOGTAG, "no server-pushed mode available yet: " + t.getMessage());
        }
        if (pushed != null && !pushed.isEmpty()) {
            entries[0] = base[0] + " (currently: " + pushed + ")";
        } else {
            entries[0] = base[0] + " (engine not yet connected)";
        }
        int currentSelection = binding.spinnerConnectionMode.getSelectedItemPosition();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), R.layout.spinner_item_themed, entries);
        adapter.setDropDownViewResource(R.layout.spinner_item_themed);
        binding.spinnerConnectionMode.setAdapter(adapter);
        if (currentSelection >= 0 && currentSelection < entries.length) {
            binding.spinnerConnectionMode.setSelection(currentSelection);
        }
        // Server-pushed timeout values may have changed too; refresh hints.
        refreshTimeoutHints();
    }

    private void updateTimeoutsVisibility(int spinnerPosition) {
        // Inactivity timeouts only apply when the lazy/dynamic connection
        // manager is active. Mapping (CONNECTION_MODE_VALUES indices):
        //   0 follow-server : hide all (server may push any mode; default off)
        //   1 relay-forced  : hide all (relay tunnel always up, no teardown)
        //   2 p2p           : hide all (no inactivity manager runs)
        //   3 p2p-lazy      : show relay_timeout (whole-peer teardown)
        //   4 p2p-dynamic   : show all three (ICE-only + relay + retry-cap)
        boolean lazyActive = (spinnerPosition == 3 || spinnerPosition == 4);
        boolean dynamicActive = (spinnerPosition == 4);

        binding.layoutTimeoutsContainer.setVisibility(lazyActive ? View.VISIBLE : View.GONE);
        if (!lazyActive) return;

        // relay timeout shown for both p2p-lazy and p2p-dynamic.
        binding.labelP2pTimeout.setVisibility(dynamicActive ? View.VISIBLE : View.GONE);
        binding.editP2pTimeout.setVisibility(dynamicActive ? View.VISIBLE : View.GONE);
        binding.labelP2pRetryMax.setVisibility(dynamicActive ? View.VISIBLE : View.GONE);
        binding.editP2pRetryMax.setVisibility(dynamicActive ? View.VISIBLE : View.GONE);

        // Refresh hint text from the latest server-pushed values so users
        // see what they would inherit if they leave a field blank.
        refreshTimeoutHints();
    }

    private void refreshTimeoutHints() {
        long relayServer = 0, p2pServer = 0, retryServer = 0;
        try {
            if (requireActivity() instanceof io.netbird.client.ServiceAccessor) {
                io.netbird.client.ServiceAccessor sa =
                        (io.netbird.client.ServiceAccessor) requireActivity();
                relayServer = sa.getServerPushedRelayTimeoutSecs();
                p2pServer = sa.getServerPushedP2pTimeoutSecs();
                retryServer = sa.getServerPushedP2pRetryMaxSecs();
            }
        } catch (Throwable t) {
            Log.d(LOGTAG, "server-pushed timeouts unavailable: " + t.getMessage());
        }
        binding.editRelayTimeout.setHint(formatHint(relayServer));
        binding.editP2pTimeout.setHint(formatHint(p2pServer));
        binding.editP2pRetryMax.setHint(formatHint(retryServer));
    }

    private static String formatHint(long secs) {
        if (secs <= 0) {
            return "use server default";
        }
        return "use server default (" + secs + "s)";
    }

    private interface LongConsumer { void accept(long v); }

    private void wireTimeoutEditOnBlur(EditText edit, String label, LongConsumer onCommit) {
        edit.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) return;
            String s = edit.getText().toString().trim();
            long val = 0;
            if (!s.isEmpty()) {
                try { val = Long.parseLong(s); }
                catch (NumberFormatException nfe) {
                    Log.w(LOGTAG, "Invalid " + label + " timeout: " + s);
                    edit.setText("");
                    return;
                }
                if (val < 0) val = 0;
            }
            onCommit.accept(val);
        });
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
            Toast.makeText(context, "Failed to get config path: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        io.netbird.gomobile.android.Preferences preferences = new io.netbird.gomobile.android.Preferences(configFilePath);
        try {
            preferences.setPreSharedKey(key);
            preferences.commit();
            Toast.makeText(context, R.string.advanced_presharedkey_saved_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to save pre-shared key", e);
            Toast.makeText(context, R.string.advanced_presharedkey_save_error + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        io.netbird.gomobile.android.Preferences preferences = new io.netbird.gomobile.android.Preferences(configFilePath);
        try {
            return !preferences.getPreSharedKey().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

}
