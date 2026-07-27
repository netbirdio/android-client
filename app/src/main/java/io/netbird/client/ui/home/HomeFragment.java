package io.netbird.client.ui.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

import io.netbird.client.PlatformUtils;
import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.StateListener;
import io.netbird.client.StateListenerRegistry;
import io.netbird.client.databinding.FragmentHomeBinding;
import io.netbird.client.tool.Profile;
import io.netbird.client.tool.ProfileManagerWrapper;

public class HomeFragment extends Fragment implements StateListener, ProfilePickerSheet.OnProfileSwitchedListener {

    private FragmentHomeBinding binding;
    private ServiceAccessor serviceAccessor;
    private StateListenerRegistry stateListenerRegistry;

    private TextView textHostname;
    private TextView textNetworkAddress;
    private TextView textIpAddress;
    private TextView textConnStatus;

    private SwitchMaterial buttonConnect;
    private boolean isConnected;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ServiceAccessor) {
            serviceAccessor = (ServiceAccessor) context;
        } else {
            throw new RuntimeException(context + " must implement ServiceAccessor");
        }
        if(context instanceof StateListenerRegistry) {
            stateListenerRegistry = (StateListenerRegistry) context;
        } else {
            throw new RuntimeException(context + " must implement StateListenerRegistry");
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        textHostname = binding.textHostname;
        textNetworkAddress = binding.textNetworkAddress;
        textIpAddress = binding.textIpAddress;
        textConnStatus = binding.textConnectionStatus;

        buttonConnect = binding.btnConnect;

        // Toggle taps drive the connection. We use a click listener rather than a
        // checked-change listener so that programmatic state updates coming from the
        // service (connected/disconnected callbacks) don't trigger a connection switch.
        buttonConnect.setOnClickListener(v -> {
            if (serviceAccessor == null) {
                return;
            }

            if (isConnected) {
                // We're currently connected, so disconnect
                buttonConnect.setEnabled(false);
                setStatusText(R.string.main_status_disconnecting);
                serviceAccessor.switchConnection(false);
            } else {
                // We're currently disconnected, so connect
                setStatusText(R.string.main_status_connecting);
                serviceAccessor.switchConnection(true);
            }
        });

        binding.btnCopyIp.setOnClickListener(v -> copyToClipboard(textIpAddress.getText()));
        binding.btnCopySecondary.setOnClickListener(v -> copyToClipboard(binding.textSecondaryValue.getText()));

        // Tapping the address summary expands/collapses the detailed info rows.
        binding.networkAddressSummary.setOnClickListener(v -> toggleInfoRows());

        binding.profileChip.setOnClickListener(v -> {
            ProfilePickerSheet sheet = new ProfilePickerSheet();
            sheet.show(getChildFragmentManager(), "ProfilePickerSheet");
        });

        updateProfileChip();

        if (PlatformUtils.isAndroidTV(requireContext())) {
            root.postDelayed(() -> {
                if (buttonConnect != null && buttonConnect.isEnabled()) {
                    buttonConnect.requestFocus();
                }
            }, 200);
        }

        // Seed the disconnected state for the case where the service hasn't reported one yet
        // (cold start). Registration below replays the real state synchronously when there is
        // one, so this never reaches the screen on a re-entry into an active connection.
        setToggle(false, true, R.string.main_status_disconnected);

        stateListenerRegistry.registerServiceStateListener(this);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateProfileChip();
    }

    @Override
    public void onProfileSwitched(String newActiveName) {
        updateProfileChip();
    }

    private void updateProfileChip() {
        if (binding == null) return;
        try {
            ProfileManagerWrapper profileManager = new ProfileManagerWrapper(requireContext());
            Profile activeProfile = profileManager.getActiveProfile();
            binding.profileChipText.setText(activeProfile != null ? activeProfile.getName() : "");
        } catch (Exception e) {
            Log.e("HomeFragment", "Failed to read active profile", e);
            binding.profileChipText.setText("");
        }
    }

    private void toggleInfoRows() {
        if (binding == null) return;
        boolean expand = binding.infoRows.getVisibility() != View.VISIBLE;
        binding.infoRows.setVisibility(expand ? View.VISIBLE : View.GONE);
        binding.infoRowsChevron.animate().rotation(expand ? 180f : 0f).setDuration(150).start();
    }

    private void copyToClipboard(CharSequence value) {
        Context ctx = getContext();
        if (ctx == null || value == null) return;
        String text = value.toString().trim();
        // Don't copy the empty-state placeholder.
        if (TextUtils.isEmpty(text) || getString(R.string.main_value_empty).equals(text)) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("NetBird", text));
        Toast.makeText(ctx, R.string.main_copied, Toast.LENGTH_SHORT).show();
    }

    private void setStatusText(int resId) {
        runOnUi(() -> {
            if (textConnStatus != null) {
                textConnStatus.setText(resId);
            }
        });
    }

    private void setToggle(boolean checked, boolean enabled, int statusResId) {
        runOnUi(() -> {
            if (buttonConnect != null) {
                buttonConnect.setChecked(checked);
                buttonConnect.setEnabled(enabled);
                // setChecked animates the thumb; on a freshly inflated view that reads as the
                // toggle sliding into place, so snap it to its final position instead.
                buttonConnect.jumpDrawablesToCurrentState();
            }
            if (textConnStatus != null) {
                textConnStatus.setText(statusResId);
            }
        });
    }

    /**
     * Applies a view update immediately when we're already on the main thread, and posts it
     * otherwise. State replayed at listener-registration time arrives on the main thread before
     * the first draw, so running it inline keeps the stale layout defaults from flashing.
     */
    private void runOnUi(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
            return;
        }
        View root = binding != null ? binding.getRoot() : null;
        if (root != null) {
            root.post(action);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stateListenerRegistry.unregisterServiceStateListener(this);
        binding = null;
        buttonConnect = null;
        textConnStatus = null;
        textHostname = null;
        textNetworkAddress = null;
        textIpAddress = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        serviceAccessor = null;
    }

    @Override
    public void onEngineStarted() {

    }

    @Override
    public void onEngineStopped() {
        isConnected = false;
        setToggle(false, true, R.string.main_status_disconnected);
    }

    @Override
    public void onAddressChanged(String fqdn, String ip) {
        if (binding == null) {
            return;
        }

        // The engine packs the addresses as "IPv4\nIPv6" when an IPv6 address is
        // available (see Status.UpdateLocalPeerState); otherwise it's just the IPv4.
        String ipv4 = "";
        String ipv6 = "";
        if (!TextUtils.isEmpty(ip)) {
            String[] parts = ip.split("\n", 2);
            ipv4 = parts[0].trim();
            if (parts.length > 1) {
                ipv6 = parts[1].trim();
            }
        }

        final String fIpv4 = ipv4;
        final String fIpv6 = ipv6;
        final boolean hasIpv4 = !TextUtils.isEmpty(fIpv4);
        runOnUi(() -> {
            if (binding == null) return;
            // Emphasized line shows the hostname (fqdn); muted summary shows the IPv4 address.
            binding.textHostname.setText(fqdn);
            binding.textNetworkAddress.setText(fIpv4);
            // Primary info row shows the IPv4 address.
            binding.textIpAddress.setText(fIpv4);
            // Secondary info row shows the IPv6 address only when one is available.
            boolean hasIpv6 = !TextUtils.isEmpty(fIpv6);
            binding.textSecondaryValue.setText(hasIpv6 ? fIpv6 : "");
            binding.secondaryValueRow.setVisibility(hasIpv6 ? View.VISIBLE : View.GONE);
            // Only show the muted address summary (with chevron) when we have an address.
            binding.networkAddressSummary.setVisibility(hasIpv4 ? View.VISIBLE : View.GONE);
            // Without an address there's nothing to expand: collapse the info rows and reset the chevron.
            if (!hasIpv4) {
                binding.infoRows.setVisibility(View.GONE);
                binding.infoRowsChevron.setRotation(0f);
            }
        });
    }

    @Override
    public void onConnected() {
        isConnected = true;
        setToggle(true, true, R.string.main_status_connected);
    }

    @Override
    public void onConnecting() {
        setToggle(true, false, R.string.main_status_connecting);
    }

    @Override
    public void onDisconnected() {
        isConnected = false;
        setToggle(false, true, R.string.main_status_disconnected);
    }

    @Override
    public void onDisconnecting() {
        setToggle(false, false, R.string.main_status_disconnecting);
    }

    @Override
    public void onPeersListChanged(long numberOfPeers) {
        // peer count badge moved to bottom navigation; intentionally noop here
    }
}
