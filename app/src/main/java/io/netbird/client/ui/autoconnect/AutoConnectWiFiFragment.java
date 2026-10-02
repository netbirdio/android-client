package io.netbird.client.ui.autoconnect;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;

import io.netbird.client.R;
import io.netbird.client.databinding.FragmentAutoConnectWifiBinding;
import io.netbird.client.tool.autoconnect.AutoConnectArmer;
import io.netbird.client.tool.autoconnect.AutoConnectPreferences;
import io.netbird.client.tool.autoconnect.TrustedNetwork;
import io.netbird.client.tool.autoconnect.WifiInfoProvider;
import io.netbird.client.ui.PermissionRequestState;

public class AutoConnectWiFiFragment extends Fragment {

    private FragmentAutoConnectWifiBinding binding;
    private AutoConnectPreferences preferences;
    private TrustedNetworkAdapter adapter;

    private final ActivityResultLauncher<String[]> requestLocationPermissions =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grantResults -> {
                updateLocationPermissionBanner();
                AutoConnectArmer.sync(requireContext());
            });

    private final ActivityResultLauncher<String> requestBackgroundLocationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                updateLocationPermissionBanner();
                AutoConnectArmer.sync(requireContext());
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAutoConnectWifiBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        preferences = new AutoConnectPreferences(requireContext());
        adapter = new TrustedNetworkAdapter(preferences.getTrustedNetworks(), this::onRemoveNetwork);
        binding.recyclerTrustedNetworks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerTrustedNetworks.setAdapter(adapter);
        refreshList();

        binding.btnAddTrustedNetwork.setOnClickListener(v ->
                new AddTrustedNetworkSheet().show(getChildFragmentManager(), "add_trusted_network"));

        getChildFragmentManager().setFragmentResultListener(
                AddTrustedNetworkSheet.RESULT_KEY, this, (requestKey, bundle) -> {
                    TrustedNetwork network = new TrustedNetwork(
                            bundle.getString(AddTrustedNetworkSheet.ARG_SSID),
                            bundle.getString(AddTrustedNetworkSheet.ARG_BSSID));
                    preferences.addTrustedNetwork(network);
                    refreshList();
                    AutoConnectArmer.sync(requireContext());
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (preferences != null) {
            refreshList();
            updateLocationPermissionBanner();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * Two-step flow: first ACCESS_FINE_LOCATION (a normal one-shot grant),
     * then — only once that's granted, and only on Android 10+ where the
     * distinction exists — ACCESS_BACKGROUND_LOCATION, which Android
     * requires as a separate, later request and which is what actually
     * shows "Allow all the time" as an option. Auto-connect evaluates
     * trusted networks from VPNService in the background, so foreground-only
     * ("while using the app") access gets revoked exactly when it's needed.
     */
    private void updateLocationPermissionBanner() {
        if (!WifiInfoProvider.hasPermission(requireContext())) {
            showLocationBanner(R.string.auto_connect_location_permission_warning,
                    R.string.auto_connect_grant_location_permission,
                    () -> PermissionRequestState.performGrantAction(this, Manifest.permission.ACCESS_FINE_LOCATION,
                            () -> requestLocationPermissions.launch(new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION})));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && !WifiInfoProvider.hasBackgroundCapablePermission(requireContext())) {
            showLocationBanner(R.string.auto_connect_background_location_warning,
                    R.string.auto_connect_allow_always,
                    () -> PermissionRequestState.performGrantAction(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                            () -> requestBackgroundLocationPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)));
        } else {
            binding.locationPermissionBanner.setVisibility(View.GONE);
        }
    }

    private void showLocationBanner(int textRes, int buttonRes, Runnable onGrantClicked) {
        binding.locationPermissionBanner.setVisibility(View.VISIBLE);
        binding.locationPermissionBannerText.setText(textRes);
        binding.btnBannerGrantLocation.setText(buttonRes);
        binding.btnBannerGrantLocation.setOnClickListener(v -> onGrantClicked.run());
    }

    private void onRemoveNetwork(TrustedNetwork network) {
        preferences.removeTrustedNetwork(network);
        refreshList();
        AutoConnectArmer.sync(requireContext());
    }

    private void refreshList() {
        List<TrustedNetwork> networks = preferences.getTrustedNetworks();
        adapter.submitList(networks);
        binding.emptyTrustedNetworks.setVisibility(networks.isEmpty() ? View.VISIBLE : View.GONE);
        binding.recyclerTrustedNetworks.setVisibility(networks.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
