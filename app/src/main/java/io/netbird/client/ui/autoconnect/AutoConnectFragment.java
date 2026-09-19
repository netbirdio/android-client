package io.netbird.client.ui.autoconnect;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.function.Consumer;

import io.netbird.client.R;
import io.netbird.client.databinding.FragmentAutoConnectBinding;
import io.netbird.client.tool.autoconnect.AutoConnectArmer;
import io.netbird.client.tool.autoconnect.AutoConnectPreferences;

public class AutoConnectFragment extends Fragment {

    private FragmentAutoConnectBinding binding;
    private AutoConnectPreferences preferences;

    private final ActivityResultLauncher<String> requestNotificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                updateNotificationPermissionBanner();
                if (granted) {
                    // Any notification VPNService tried to post while this
                    // permission was missing was silently suppressed, not
                    // queued — re-arming re-posts it now that we actually can
                    // show it, instead of waiting for the next network event.
                    AutoConnectArmer.sync(requireContext());
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAutoConnectBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        preferences = new AutoConnectPreferences(requireContext());
        NavController navController = NavHostFragment.findNavController(this);

        binding.layoutConnectOnWifi.setOnClickListener(v ->
                navController.navigate(R.id.nav_auto_connect_wifi));
        binding.switchConnectOnWifi.setChecked(preferences.isWifiTriggerEnabled());
        binding.switchConnectOnWifi.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.setWifiTriggerEnabled(isChecked);
            AutoConnectArmer.sync(requireContext());
            updatePermissionBanners();
        });

        configureRow(binding.layoutConnectOnMobileData, binding.switchConnectOnMobileData,
                preferences.isMobileTriggerEnabled(), preferences::setMobileTriggerEnabled);
        configureRow(binding.layoutConnectOnEthernet, binding.switchConnectOnEthernet,
                preferences.isEthernetTriggerEnabled(), preferences::setEthernetTriggerEnabled);
        configureRow(binding.layoutDisconnectOnNoNetwork, binding.switchDisconnectOnNoNetwork,
                preferences.isDisconnectOnNoNetworkEnabled(), preferences::setDisconnectOnNoNetworkEnabled);

        binding.btnBannerIgnoreBatteryOptimizations.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(intent);
        });

        binding.btnBannerEnableNotifications.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // No runtime permission to request pre-13; notifications were
                // disabled some other way (e.g. the user turned them off in
                // system settings), so the only way to fix it is there.
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
                startActivity(intent);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // The Wi-Fi master switch can also change from the trusted-network
        // sub-screen's own back navigation state, so refresh on return.
        if (binding != null && preferences != null) {
            binding.switchConnectOnWifi.setChecked(preferences.isWifiTriggerEnabled());
            updatePermissionBanners();
        }
    }

    private void updatePermissionBanners() {
        updateBatteryOptimizationBanner();
        updateNotificationPermissionBanner();
    }

    /**
     * Only shown once auto-connect actually has something to keep alive for
     * (at least one trigger armed) — battery exemption is irrelevant, and
     * shouldn't be asked for, before then.
     */
    private void updateBatteryOptimizationBanner() {
        PowerManager powerManager = (PowerManager) requireContext().getSystemService(Context.POWER_SERVICE);
        boolean unrestricted = powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(requireContext().getPackageName());
        boolean shouldShow = preferences.isAnyTriggerArmed() && !unrestricted;
        binding.batteryOptimizationBanner.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
    }

    /**
     * Without this, the MONITORING foreground-service notification (and the
     * ordinary connected/disconnected one) silently fails to display at all
     * — the service still runs, the user just never sees it.
     */
    private void updateNotificationPermissionBanner() {
        boolean enabled = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled();
        boolean shouldShow = preferences.isAnyTriggerArmed() && !enabled;
        binding.notificationPermissionBanner.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
    }

    /**
     * Wires a row whose switch is visual only (clickable="false" in the
     * layout) — the whole row is the touch target, matching the rest of the
     * hub's rows, and the switch is updated programmatically to reflect it.
     */
    private void configureRow(LinearLayout row, SwitchMaterial switchView, boolean initiallyChecked,
                               Consumer<Boolean> onChanged) {
        switchView.setChecked(initiallyChecked);
        row.setOnClickListener(v -> {
            boolean newValue = !switchView.isChecked();
            switchView.setChecked(newValue);
            onChanged.accept(newValue);
            AutoConnectArmer.sync(requireContext());
            updatePermissionBanners();
        });
    }
}
