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
import androidx.core.content.ContextCompat;
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
import io.netbird.client.tool.RouteChangeListener;
import io.netbird.gomobile.android.NetworkArray;

public class HomeFragment extends Fragment implements StateListener, RouteChangeListener, ProfilePickerSheet.OnProfileSwitchedListener {

    private FragmentHomeBinding binding;
    private ServiceAccessor serviceAccessor;
    private StateListenerRegistry stateListenerRegistry;

    private TextView textHostname;
    private TextView textNetworkAddress;
    private TextView textIpAddress;
    private TextView textConnStatus;

    private SwitchMaterial buttonConnect;
    private boolean isConnected;

    private enum EngineState { CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTED }

    private static final long PENDING_ACTION_TIMEOUT_MS = 7_000;

    // Action latch, mirroring the desktop MainConnectionStatusSwitch: while a tap
    // is in flight, engine reports that contradict its target (e.g. the transient
    // Connecting emitted during teardown before the engine-side Disconnecting
    // latch is set) don't repaint the toggle, so it can't flicker.
    private volatile EngineState lastEngineState = EngineState.DISCONNECTED;
    private volatile EngineState pendingTarget;
    private final Runnable pendingTimeout = this::expirePendingAction;

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
        //
        // A tap has already flipped the switch by the time this runs: SwitchMaterial is a
        // CompoundButton, and CompoundButton.performClick() toggles isChecked before
        // dispatching the click. So every branch must paint the toggle through setToggle()
        // rather than only the label, otherwise the switch keeps whatever position the tap
        // gave it while the text says something else.
        buttonConnect.setOnClickListener(v -> {
            if (serviceAccessor == null) {
                // Nothing will drive the connection, so undo the tap's flip.
                setToggle(isConnected, true, isConnected
                        ? R.string.main_status_connected
                        : R.string.main_status_disconnected);
                return;
            }

            if (isConnected) {
                // We're currently connected, so disconnect
                beginPendingAction(EngineState.DISCONNECTED);
                setToggle(false, false, R.string.main_status_disconnecting);
                serviceAccessor.switchConnection(false);
            } else {
                // We're currently disconnected, so connect
                beginPendingAction(EngineState.CONNECTED);
                setToggle(true, true, R.string.main_status_connecting);
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

        binding.exitNodeRow.setOnClickListener(v -> {
            ExitNodePickerSheet sheet = new ExitNodePickerSheet();
            sheet.show(getChildFragmentManager(), "ExitNodePickerSheet");
        });
        serviceAccessor.addRouteChangeListener(this);

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

    private void setToggle(boolean checked, boolean enabled, int statusResId) {
        runOnUi(() -> {
            if (buttonConnect != null) {
                // setChecked animates the thumb; on a freshly inflated view that reads as the
                // toggle sliding into place, so snap it to its final position instead. Only do
                // so when this call actually changes the state: SwitchCompat's jump ends the
                // in-flight position animator, and the tap handler runs while the tap's own
                // slide (which already put the switch in the requested state) is still playing.
                boolean changed = buttonConnect.isChecked() != checked;
                buttonConnect.setChecked(checked);
                buttonConnect.setEnabled(enabled);
                if (changed) {
                    buttonConnect.jumpDrawablesToCurrentState();
                }
            }
            if (textConnStatus != null) {
                textConnStatus.setText(statusResId);
            }
        });
    }

    private void onEngineState(EngineState state) {
        lastEngineState = state;
        isConnected = state == EngineState.CONNECTED;
        if (shouldSuppressPaint(state)) {
            return;
        }
        applyEngineState(state);
    }

    /**
     * Decides whether an engine state report may repaint the toggle while a user
     * action is pending, and releases the latch once the action completes or
     * demonstrably fails.
     */
    private boolean shouldSuppressPaint(EngineState state) {
        EngineState target = pendingTarget;
        if (target == null) {
            return false;
        }
        if (state == target) {
            clearPendingAction();
            return false;
        }
        if (target == EngineState.DISCONNECTED) {
            // Only same-direction progress may paint while disconnecting.
            return state != EngineState.DISCONNECTING;
        }
        // target == CONNECTED
        if (state == EngineState.CONNECTING) {
            // Same-direction progress: let it paint.
            return false;
        }
        if (state == EngineState.DISCONNECTED) {
            // The connect ended disconnected, so it failed. This covers the attempt that
            // reported Connecting first as well as the one that never got that far (the
            // engine refused up front, e.g. the VPN permission was declined); in both
            // cases latching on would strand the toggle in the position the tap gave it.
            clearPendingAction();
            return false;
        }
        return true;
    }

    private void applyEngineState(EngineState state) {
        switch (state) {
            case CONNECTING:
                setToggle(true, false, R.string.main_status_connecting);
                break;
            case CONNECTED:
                setToggle(true, true, R.string.main_status_connected);
                break;
            case DISCONNECTING:
                setToggle(false, false, R.string.main_status_disconnecting);
                break;
            case DISCONNECTED:
                setToggle(false, true, R.string.main_status_disconnected);
                break;
        }
    }

    private void beginPendingAction(EngineState target) {
        pendingTarget = target;
        View root = binding != null ? binding.getRoot() : null;
        if (root != null) {
            root.removeCallbacks(pendingTimeout);
            root.postDelayed(pendingTimeout, PENDING_ACTION_TIMEOUT_MS);
        }
    }

    private void clearPendingAction() {
        pendingTarget = null;
        View root = binding != null ? binding.getRoot() : null;
        if (root != null) {
            root.removeCallbacks(pendingTimeout);
        }
    }

    private void expirePendingAction() {
        if (pendingTarget == null) {
            return;
        }
        // The engine never reached the target (e.g. the action hung or failed
        // silently); fall back to painting whatever it last reported instead of
        // staying latched forever.
        pendingTarget = null;
        applyEngineState(lastEngineState);
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
        if (serviceAccessor != null) {
            serviceAccessor.removeRouteChangeListener(this);
        }
        if (binding != null) {
            binding.getRoot().removeCallbacks(pendingTimeout);
        }
        pendingTarget = null;
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
        onEngineState(EngineState.DISCONNECTED);
        updateExitNodeRow();
    }

    @Override
    public void onRouteChanged(String routes) {
        updateExitNodeRow();
    }

    /**
     * The exit node row is always on screen; it is enabled only while connected
     * with at least one exit node shared with this peer, and dimmed otherwise.
     * The subtitle names the active exit node, if any.
     */
    private void updateExitNodeRow() {
        ServiceAccessor accessor = serviceAccessor;
        if (accessor == null || binding == null) {
            return;
        }

        String activeName = null;
        boolean hasAny = false;
        if (isConnected) {
            NetworkArray networks = accessor.getNetworks();
            if (networks != null) {
                for (int i = 0; i < networks.size(); i++) {
                    var network = networks.get(i);
                    if (!Resource.isExitNodeAddress(network.getNetwork())) {
                        continue;
                    }
                    hasAny = true;
                    if (network.getIsSelected()) {
                        activeName = network.getName();
                    }
                }
            }
        }

        final boolean enabled = isConnected && hasAny;
        final String name = activeName;
        runOnUi(() -> {
            Context ctx = getContext();
            if (binding == null || ctx == null) {
                return;
            }
            binding.exitNodeRow.setEnabled(enabled);
            binding.exitNodeRow.setAlpha(enabled ? 1f : 0.5f);
            binding.exitNodeStatus.setText(!enabled
                    ? getString(R.string.exit_node_unavailable)
                    : name != null ? name : getString(R.string.exit_node_none));
            binding.exitNodeIcon.setColorFilter(ContextCompat.getColor(ctx,
                    name != null ? R.color.nb_orange : R.color.nb_txt_light));
        });
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
        onEngineState(EngineState.CONNECTED);
        updateExitNodeRow();
    }

    @Override
    public void onConnecting() {
        onEngineState(EngineState.CONNECTING);
    }

    @Override
    public void onDisconnected() {
        onEngineState(EngineState.DISCONNECTED);
        updateExitNodeRow();
    }

    @Override
    public void onDisconnecting() {
        onEngineState(EngineState.DISCONNECTING);
    }

    @Override
    public void onPeersListChanged(long numberOfPeers) {
        // Peer count badge moved to bottom navigation. This event also fires right
        // after the network map is applied, unlike onConnected (too early: routes not
        // yet present) and onRouteChanged (silent for the initial route set, which is
        // the notifier's baseline) — so it's what makes the exit node row appear on a
        // fresh connect. Same trigger pair the Networks tab relies on.
        updateExitNodeRow();
    }
}
