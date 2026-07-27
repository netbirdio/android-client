package io.netbird.client.ui.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.StateListenerRegistry;
import io.netbird.client.databinding.FragmentPeerDetailBinding;

/**
 * Full-screen view of everything the client knows about one peer. Mirrors the
 * desktop app's peer detail panel: same rows, same ordering, and the same rule
 * that a row is hidden rather than shown with a meaningless value.
 */
public class PeerDetailFragment extends Fragment {

    public static final String ARG_PUB_KEY = "pubKey";

    private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final long LATENCY_GOOD_MS = 100;

    /**
     * Transfer counters and latency change without the peer list firing a change
     * event, so poll while this screen is in the foreground. Matches the desktop
     * panel, which ticks once a second for the same reason.
     */
    private static final long POLL_INTERVAL_MS = 1000;

    private FragmentPeerDetailBinding binding;
    private ServiceAccessor serviceAccessor;
    private StateListenerRegistry stateListenerRegistry;
    private PeersFragmentViewModel model;

    private String pubKey;

    /** Last peer state the rows were built from; guards redundant re-renders. */
    @Nullable
    private Peer rendered;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (model != null) {
                model.refreshPeers();
            }
            pollHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public static Bundle argsFor(Peer peer) {
        Bundle args = new Bundle();
        args.putString(ARG_PUB_KEY, peer.getPubKey());
        return args;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ServiceAccessor) {
            serviceAccessor = (ServiceAccessor) context;
        } else {
            throw new RuntimeException(context + " must implement ServiceAccessor");
        }

        if (context instanceof StateListenerRegistry) {
            stateListenerRegistry = (StateListenerRegistry) context;
        } else {
            throw new RuntimeException(context + " must implement StateListenerRegistry");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPeerDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        pubKey = getArguments() != null ? getArguments().getString(ARG_PUB_KEY, "") : "";

        // Activity-scoped and therefore NOT the instance PeersFragment drives (that one
        // is fragment-scoped), so this model needs its own event registration and an
        // eager first load — nothing else ever fills it.
        model = new ViewModelProvider(requireActivity(), PeersFragmentViewModel.getFactory(serviceAccessor))
                .get(PeersFragmentViewModel.class);
        stateListenerRegistry.registerServiceStateListener(model.getStateListener());
        model.refreshPeers();

        binding.peerDetailRefresh.setOnClickListener(v -> {
            v.animate().rotationBy(360f).setDuration(600).start();
            model.refreshPeers();
        });

        // The peer list keeps streaming while this screen is open, so re-render from
        // each snapshot rather than from a copy taken when the row was tapped.
        model.getUiState().observe(getViewLifecycleOwner(), uiState -> {
            // An empty list is "not loaded yet" (this model starts blank and fills
            // asynchronously), not "the peer is gone" — leave only when a non-empty
            // snapshot no longer contains the peer.
            if (uiState.getPeers().isEmpty()) {
                return;
            }
            Peer peer = findPeer(uiState.getPeers());
            if (peer == null) {
                // The peer dropped out of the list; nothing left to show.
                NavHostFragment.findNavController(this).popBackStack();
                return;
            }
            // Polling hands us an identical peer most ticks. Rebuilding the rows
            // then would restart ripples and drop any in-progress text selection.
            if (peer.equals(rendered)) {
                return;
            }
            rendered = peer;
            render(peer);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        pollHandler.postDelayed(pollTask, POLL_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        pollHandler.removeCallbacks(pollTask);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        pollHandler.removeCallbacks(pollTask);
        rendered = null;
        // Safe to unregister: this is the activity-scoped model's listener, a different
        // object from the one the fragment-scoped PeersFragment model registered.
        if (model != null && stateListenerRegistry != null) {
            stateListenerRegistry.unregisterServiceStateListener(model.getStateListener());
        }
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        stateListenerRegistry = null;
        serviceAccessor = null;
        super.onDetach();
    }

    @Nullable
    private Peer findPeer(List<Peer> peers) {
        for (Peer peer : peers) {
            if (pubKey.equals(peer.getPubKey())) {
                return peer;
            }
        }
        return null;
    }

    private void render(Peer peer) {
        binding.peerDetailFqdn.setText(peer.getFqdn());
        binding.peerDetailStatusDot.setBackgroundResource(statusDot(peer.getStatus()));

        LinearLayout rows = binding.peerDetailRows;
        rows.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        boolean isConnected = peer.getStatus() == Status.CONNECTED;

        addRow(inflater, rows, R.drawable.ic_pin_drop, getString(R.string.peer_detail_netbird_ip),
                orDash(peer.getIp()), peer.getIp());

        if (notEmpty(peer.getIpv6())) {
            addRow(inflater, rows, R.drawable.ic_pin_drop, getString(R.string.peer_detail_netbird_ipv6),
                    peer.getIpv6(), peer.getIpv6());
        }

        // "Relayed" is a plain bool with no "not connected" state, so it only means
        // something once the peer is actually connected.
        if (isConnected) {
            addRow(inflater, rows, R.drawable.ic_swap_horiz, getString(R.string.peer_detail_connection),
                    getString(peer.isRelayed()
                            ? R.string.peer_detail_connection_relayed
                            : R.string.peer_detail_connection_p2p), null);
        }

        if (peer.getLatencyMs() > 0) {
            View row = addRow(inflater, rows, R.drawable.ic_speed, getString(R.string.peer_detail_latency),
                    getString(R.string.peer_detail_latency_value, peer.getLatencyMs()), null);
            TextView value = row.findViewById(R.id.peer_detail_row_value);
            value.setTextColor(ContextCompat.getColor(requireContext(), latencyColor(peer.getLatencyMs())));
        }

        if (peer.getBytesRx() > 0 || peer.getBytesTx() > 0) {
            addTransferRow(inflater, rows, peer);
        }

        addRow(inflater, rows, R.drawable.ic_schedule, getString(R.string.peer_detail_status_since),
                relativeOrDash(peer.getConnStatusUpdate()), null);

        addRow(inflater, rows, R.drawable.ic_key, getString(R.string.peer_detail_rosenpass),
                getString(peer.isRosenpassEnabled() ? R.string.peer_detail_yes : R.string.peer_detail_no), null);

        // Listed as plain rows under a section header, one route per line, matching
        // the iOS client's Routes section.
        List<String> routes = peer.getRoutes();
        if (routes != null && !routes.isEmpty()) {
            addHeader(inflater, rows, R.string.peer_detail_networks);
            for (String route : routes) {
                TextView routeView = (TextView) inflater.inflate(
                        R.layout.list_item_peer_detail_route, rows, false);
                routeView.setText(route);
                rows.addView(routeView);
            }
        }

        addIceRow(inflater, rows, R.drawable.ic_computer, R.string.peer_detail_local_ice,
                peer.getLocalIceCandidateType(), peer.getLocalIceCandidateEndpoint());
        addIceRow(inflater, rows, R.drawable.ic_rss, R.string.peer_detail_remote_ice,
                peer.getRemoteIceCandidateType(), peer.getRemoteIceCandidateEndpoint());

        // Keys are read from the front when eyeballing them, so clip the tail.
        addRow(inflater, rows, R.drawable.ic_key, getString(R.string.peer_detail_public_key),
                orDash(peer.getPubKey()), peer.getPubKey(), TextUtils.TruncateAt.END);
    }

    private void addHeader(LayoutInflater inflater, LinearLayout rows, @StringRes int label) {
        TextView header = (TextView) inflater.inflate(
                R.layout.list_item_peer_detail_header, rows, false);
        header.setText(label);
        rows.addView(header);
    }

    /** Skipped entirely when neither the type nor the endpoint is known, as on desktop. */
    private void addIceRow(LayoutInflater inflater, LinearLayout rows, @DrawableRes int icon,
                           @StringRes int baseLabel, String type, String endpoint) {
        if (!notEmpty(type) && !notEmpty(endpoint)) {
            return;
        }

        String label = notEmpty(type)
                ? getString(R.string.peer_detail_ice_with_type, getString(baseLabel), capitalize(type))
                : getString(baseLabel);
        String value = notEmpty(endpoint) ? endpoint : capitalize(type);

        addRow(inflater, rows, icon, label, value, notEmpty(endpoint) ? endpoint : null);
    }

    /**
     * @param copyText text to put on the clipboard, or null for no copy affordance
     */
    private View addRow(LayoutInflater inflater, LinearLayout rows, @DrawableRes int icon,
                        String label, String value, @Nullable String copyText) {
        return addRow(inflater, rows, icon, label, value, copyText, TextUtils.TruncateAt.MIDDLE);
    }

    /**
     * @param copyText text to put on the clipboard, or null for no copy affordance
     * @param truncateAt where to clip a value too long for one line
     */
    private View addRow(LayoutInflater inflater, LinearLayout rows, @DrawableRes int icon,
                        String label, String value, @Nullable String copyText,
                        TextUtils.TruncateAt truncateAt) {
        View row = inflater.inflate(R.layout.list_item_peer_detail_row, rows, false);

        ((ImageView) row.findViewById(R.id.peer_detail_row_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.peer_detail_row_label)).setText(label);

        TextView valueView = row.findViewById(R.id.peer_detail_row_value);
        valueView.setEllipsize(truncateAt);
        valueView.setText(value);

        ImageView copy = row.findViewById(R.id.peer_detail_row_copy);
        if (notEmpty(copyText)) {
            copy.setVisibility(View.VISIBLE);
            copy.setOnClickListener(v -> copyToClipboard(label, copyText));
            row.setOnClickListener(v -> copyToClipboard(label, copyText));
        } else {
            copy.setVisibility(View.GONE);
            row.setClickable(false);
        }

        rows.addView(row);
        return row;
    }

    private void addTransferRow(LayoutInflater inflater, LinearLayout rows, Peer peer) {
        View row = inflater.inflate(R.layout.list_item_peer_detail_transfer, rows, false);
        Context context = requireContext();

        ((TextView) row.findViewById(R.id.peer_detail_bytes_rx))
                .setText(Formatter.formatFileSize(context, peer.getBytesRx()));
        ((TextView) row.findViewById(R.id.peer_detail_bytes_tx))
                .setText(Formatter.formatFileSize(context, peer.getBytesTx()));

        rows.addView(row);
    }

    @ColorRes
    private int latencyColor(long latencyMs) {
        return latencyMs < LATENCY_GOOD_MS ? R.color.nb_latency_good : R.color.nb_latency_high;
    }

    @DrawableRes
    private int statusDot(Status status) {
        return status == Status.CONNECTED
                ? R.drawable.peer_status_connected
                : R.drawable.peer_status_disconnected;
    }

    private static boolean notEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    private String orDash(String value) {
        return notEmpty(value) ? value : getString(R.string.peer_detail_unknown);
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * Renders a Go-formatted UTC timestamp as relative time. Falls back to the raw
     * string when it cannot be parsed, so an unexpected format still shows something.
     */
    private String relativeOrDash(String timestamp) {
        if (Peer.isNever(timestamp)) {
            return getString(R.string.peer_detail_unknown);
        }

        SimpleDateFormat parser = new SimpleDateFormat(TIMESTAMP_FORMAT, Locale.US);
        parser.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date parsed;
        try {
            parsed = parser.parse(timestamp);
        } catch (ParseException e) {
            return timestamp;
        }
        if (parsed == null) {
            return timestamp;
        }

        long seconds = Math.max(0, (System.currentTimeMillis() - parsed.getTime()) / 1000);
        if (seconds < 1) {
            return getString(R.string.peer_detail_just_now);
        }
        if (seconds < 60) {
            return seconds + "s ago";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m ago";
        }
        if (seconds < 86400) {
            return (seconds / 3600) + "h ago";
        }
        return (seconds / 86400) + "d ago";
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(requireContext(), R.string.peer_detail_copied, Toast.LENGTH_SHORT).show();
    }
}
