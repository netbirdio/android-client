package io.netbird.client.ui.autoconnect;

import android.Manifest;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.regex.Pattern;

import io.netbird.client.R;
import io.netbird.client.databinding.SheetAddTrustedNetworkBinding;
import io.netbird.client.tool.autoconnect.WifiInfoProvider;

public class AddTrustedNetworkSheet extends BottomSheetDialogFragment {
    public static final String RESULT_KEY = "trusted_network_added";
    public static final String ARG_SSID = "ssid";
    public static final String ARG_BSSID = "bssid";

    private static final Pattern BSSID_PATTERN =
            Pattern.compile("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$");

    private SheetAddTrustedNetworkBinding binding;

    private final ActivityResultLauncher<String> requestLocationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> updateCurrentNetworkButton());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetAddTrustedNetworkBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        updateCurrentNetworkButton();
        binding.btnUseCurrentNetwork.setOnClickListener(v -> useCurrentNetwork());
        binding.btnGrantLocationPermission.setOnClickListener(v ->
                requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION));

        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnSave.setOnClickListener(v -> onSave());
    }

    private void updateCurrentNetworkButton() {
        boolean hasPermission = WifiInfoProvider.hasPermission(requireContext());
        binding.btnUseCurrentNetwork.setVisibility(hasPermission ? View.VISIBLE : View.GONE);
        binding.btnGrantLocationPermission.setVisibility(hasPermission ? View.GONE : View.VISIBLE);
    }

    private void useCurrentNetwork() {
        WifiInfoProvider.WifiSnapshot snapshot = new WifiInfoProvider(requireContext()).currentWifi();
        if (snapshot == null) {
            Toast.makeText(requireContext(), R.string.auto_connect_wifi_not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        if (snapshot.ssid != null) {
            binding.editSsid.setText(snapshot.ssid);
        }
        if (snapshot.bssid != null) {
            binding.editBssid.setText(snapshot.bssid);
        }
    }

    private void onSave() {
        String ssid = binding.editSsid.getText().toString().trim();
        String bssid = binding.editBssid.getText().toString().trim();

        if (ssid.isEmpty() && bssid.isEmpty()) {
            binding.editSsid.setError(getString(R.string.auto_connect_error_empty_entry));
            return;
        }
        if (!bssid.isEmpty() && !BSSID_PATTERN.matcher(bssid).matches()) {
            binding.editBssid.setError(getString(R.string.auto_connect_error_invalid_bssid));
            return;
        }

        Bundle result = new Bundle();
        result.putString(ARG_SSID, ssid.isEmpty() ? null : ssid);
        result.putString(ARG_BSSID, bssid.isEmpty() ? null : bssid);
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
        dismiss();
    }
}
