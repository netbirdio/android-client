package io.netbird.client.ui.files;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.google.android.material.button.MaterialButton;

import io.netbird.client.R;
import io.netbird.client.databinding.ActivityShareTargetBinding;
import io.netbird.client.tool.VPNService;
import io.netbird.client.tool.files.ContentFileSource;
import io.netbird.client.tool.files.FileDropManager;
import io.netbird.client.ui.home.Status;
import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.PeerInfo;
import io.netbird.gomobile.android.PeerInfoArray;

/**
 * Receives the system share sheet's files and sends them to a peer the user
 * picks. A full screen rather than a dialog: an account can hold hundreds of
 * peers, which needs a search field and room to scroll.
 * <p>
 * Deliberately separate from MainActivity: that one is singleTask, so a share
 * would land inside the running task and fight its back stack. Binds to the VPN
 * service for the duration, both to read the peer list and because
 * {@link FileDropManager}'s handle only exists while the service does.
 */
public class ShareTargetActivity extends AppCompatActivity {

    public static final String EXTRA_PRESET_SEARCH = "io.netbird.client.PRESET_SEARCH";

    private static final String LOGTAG = "ShareTargetActivity";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Uri> shared = new ArrayList<>();
    private final List<PeerTarget> allTargets = new ArrayList<>();
    private final PeerAdapter adapter = new PeerAdapter(this::send);

    private ActivityShareTargetBinding binding;
    private VPNService.MyLocalBinder binder;
    private boolean bound;
    private String sharedText;

    // The manager notifies from its own executor, so updates are posted back.
    private final FileDropManager.TransfersListener transfersListener =
            transfers -> runOnUiThread(() -> onTransfers(transfers));

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (VPNService.MyLocalBinder) service;
            loadPeers();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            binder = null;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!readSharedContent(getIntent())) {
            toastAndFinish(getString(R.string.file_drop_share_nothing));
            return;
        }

        binding = ActivityShareTargetBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets();

        describeShared();
        binding.peerList.setLayoutManager(new LinearLayoutManager(this));
        binding.peerList.setAdapter(adapter);
        binding.btnCancelDialog.setOnClickListener(v -> finish());

        binding.searchView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                showMatching(s.toString().trim().toLowerCase(Locale.getDefault()));
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // A send started from a peer's own screen lands here with that peer
        // already named; prefilling the search narrows the list to it while
        // still leaving the choice visible and editable.
        String preset = getIntent().getStringExtra(EXTRA_PRESET_SEARCH);
        if (preset != null && !preset.isEmpty()) {
            binding.searchView.setText(preset);
        }

        // Binds without BIND_AUTO_CREATE on purpose: starting the VPN service
        // from a share would ask for the VPN permission out of nowhere. If the
        // tunnel is down there is nothing to send over anyway.
        Intent bindIntent = new Intent(this, VPNService.class);
        bindIntent.setAction(VPNService.INTENT_ACTION_START);
        bound = bindService(bindIntent, connection, 0);
        if (!bound) {
            toastAndFinish(getString(R.string.file_drop_share_not_running));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        FileDropManager.get().removeTransfersListener(transfersListener);
        // shutdown, never shutdownNow: an in-flight copy has to run to
        // completion or the transfer it feeds would start on a truncated file.
        executor.shutdown();
        binding = null;
    }

    /**
     * Keeps the header clear of the status bar and the last row clear of the
     * navigation bar. Mirrors MainActivity: the root takes the top inset, the
     * bottom one becomes list padding so the rows can still scroll under the
     * navigation bar instead of stopping short of it.
     */
    private void applySystemBarInsets() {
        int listBottom = getResources().getDimensionPixelSize(R.dimen.share_list_bottom_padding);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, 0);
            binding.peerList.setPadding(0, 0, 0, listBottom + bars.bottom);
            return windowInsets;
        });
    }

    private boolean readSharedContent(Intent intent) {
        String action = intent.getAction();

        if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                shared.add(uri);
            } else {
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris != null) {
                shared.addAll(uris);
            }
        }

        return !shared.isEmpty() || (sharedText != null && !sharedText.isEmpty());
    }

    /**
     * Names what is about to be sent, so the screen is not just a peer list.
     * Reading the metadata queries the content provider, so it happens off the
     * UI thread and fills in the header when it lands.
     */
    private void describeShared() {
        if (shared.isEmpty()) {
            binding.shareTitle.setText(R.string.file_drop_share_text_title);
            binding.shareSubtitle.setText(sharedText);
            return;
        }

        // A count stands in until the providers answer; the name and type
        // replace it once the metadata lands.
        binding.shareTitle.setText(getResources().getQuantityString(
                R.plurals.file_drop_share_items, shared.size(), shared.size()));
        binding.shareSubtitle.setText("");

        Context app = getApplicationContext();
        executor.execute(() -> {
            List<ContentFileSource.Details> details = new ArrayList<>();
            for (Uri uri : shared) {
                ContentFileSource.Details d = ContentFileSource.describe(app, uri);
                if (d != null) {
                    details.add(d);
                }
            }

            runOnUiThread(() -> {
                if (binding == null || details.isEmpty()) {
                    return;
                }
                binding.shareTitle.setText(titleFor(details));
                binding.shareSubtitle.setText(describeAll(details));
            });
        });
    }

    /** One file reads as its name; several read as a count. */
    private String titleFor(List<ContentFileSource.Details> details) {
        if (details.size() == 1) {
            return details.get(0).name();
        }
        return getResources().getQuantityString(R.plurals.file_drop_share_items,
                details.size(), details.size());
    }

    /**
     * Size and kind for a single file; for several, the names on one line and
     * the combined size after them.
     */
    private String describeAll(List<ContentFileSource.Details> details) {
        if (details.size() == 1) {
            return describeMeta(details.get(0));
        }

        StringBuilder names = new StringBuilder();
        long total = 0;
        boolean sizeKnown = true;

        for (ContentFileSource.Details d : details) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(d.name());

            if (d.size() >= 0) {
                total += d.size();
            } else {
                sizeKnown = false;
            }
        }

        if (sizeKnown) {
            names.append(" · ").append(formatSize(total));
        }
        return names.toString();
    }

    private String describeMeta(ContentFileSource.Details d) {
        String kind = kindLabel(d.contentType());
        if (d.size() < 0) {
            return kind;
        }
        String size = formatSize(d.size());
        return kind.isEmpty() ? size : size + " · " + kind;
    }

    /**
     * Turns a MIME type into something readable: the subtype uppercased, with
     * the general class after it, so "image/jpeg" reads as "JPEG image".
     */
    private String kindLabel(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return "";
        }

        int slash = contentType.indexOf('/');
        if (slash < 0) {
            return contentType;
        }

        String general = contentType.substring(0, slash);
        String specific = contentType.substring(slash + 1);
        if (specific.isEmpty() || "*".equals(specific)) {
            return general;
        }
        // "svg+xml" reads better as "SVG".
        int plus = specific.indexOf('+');
        if (plus > 0) {
            specific = specific.substring(0, plus);
        }

        // Vendor subtypes ("vnd.android.package-archive") carry no meaning for
        // the reader; the file extension in the name already says what it is.
        if (specific.startsWith("vnd.") || specific.startsWith("x-")) {
            return "application".equals(general) ? "" : general;
        }

        String label = specific.toUpperCase(Locale.ROOT);
        if ("application".equals(general)) {
            return label;
        }
        return label + " " + general;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double size = bytes;
        int unit = -1;
        do {
            size /= 1024;
            unit++;
        } while (size >= 1024 && unit < units.length - 1);

        if (size >= 10) {
            return String.format(Locale.getDefault(), "%.0f %s", size, units[unit]);
        }
        return String.format(Locale.getDefault(), "%.1f %s", size, units[unit]);
    }

    /**
     * Reads the peer list off the UI thread: it is a JNI call that can take
     * seconds while the engine is starting up or tearing down.
     */
    private void loadPeers() {
        executor.execute(() -> {
            PeerInfoArray peers = binder == null ? null : binder.peersInfo();

            List<PeerTarget> targets = new ArrayList<>();
            if (peers != null) {
                for (int i = 0; i < peers.size(); i++) {
                    PeerInfo peer = peers.get(i);
                    // Deliberately not filtered on connection status: an idle
                    // peer is the normal resting state under lazy connections,
                    // and the transfer's own packets are what wake it. Only a
                    // peer without an overlay address has nothing to dial.
                    if (peer == null || peer.getIP().isEmpty()) {
                        continue;
                    }
                    targets.add(new PeerTarget(peer.getPubKey(), peer.getFQDN(), peer.getIP(),
                            Status.fromLong(peer.getConnStatus()) == Status.CONNECTED));
                }
            }

            // Same order as the main peer list: connected peers first, then by
            // name, so the same peer sits in the same place on both screens.
            targets.sort((a, b) -> {
                int byStatus = Boolean.compare(b.connected, a.connected);
                return byStatus != 0 ? byStatus : a.name.compareToIgnoreCase(b.name);
            });

            runOnUiThread(() -> {
                if (binding == null) {
                    return;
                }
                allTargets.clear();
                allTargets.addAll(targets);
                showMatching(binding.searchView.getText().toString().trim()
                        .toLowerCase(Locale.getDefault()));
            });
        });
    }

    private void showMatching(String query) {
        if (binding == null) {
            return;
        }

        List<PeerTarget> shown = new ArrayList<>();
        for (PeerTarget target : allTargets) {
            if (target.matches(query)) {
                shown.add(target);
            }
        }

        adapter.submit(shown);
        binding.emptyView.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        binding.emptyView.setText(allTargets.isEmpty()
                ? R.string.file_drop_share_no_peers
                : R.string.file_drop_share_no_matches);
    }

    /**
     * Tap is the send: no confirm step, and the row itself carries the whole
     * lifecycle. The screen stays put so the same file can be fanned out to
     * several peers, each row tracking its own transfer.
     */
    private void send(PeerTarget target) {
        if (target.state != SendState.IDLE) {
            return;
        }

        target.state = SendState.WAITING;
        adapter.refresh(target);
        FileDropManager.get().addTransfersListener(transfersListener);

        // Keeping the id is what ties this row to its own transfer: matching on
        // the peer alone would latch onto an older, already finished send to the
        // same peer and report it as this one's outcome.
        FileDropManager.ValueCallback<String> callback = new FileDropManager.ValueCallback<>() {
            @Override
            public void onValue(String transferId) {
                runOnUiThread(() -> {
                    if (binding == null) {
                        return;
                    }
                    target.transferId = transferId;
                });
            }

            @Override
            public void onResult(boolean ok, String error) {
                if (ok) {
                    return;
                }
                runOnUiThread(() -> {
                    if (binding == null) {
                        return;
                    }
                    target.state = SendState.FAILED;
                    target.detail = error;
                    adapter.refresh(target);
                });
            }
        };

        if (shared.isEmpty()) {
            FileDropManager.get().sendText(sharedText, target.pubKey, target.name, target.ip,
                    callback);
            return;
        }

        // Copying happens here, not lazily during the upload: the share grants
        // read access only while this activity lives, and the upload outlives
        // it. Uses the application context so a finished activity cannot take
        // the copy down with it.
        Context app = getApplicationContext();
        executor.execute(() -> {
            List<ContentFileSource> sources = new ArrayList<>();
            for (Uri uri : shared) {
                ContentFileSource source = ContentFileSource.of(app, uri);
                if (source == null) {
                    Log.w(LOGTAG, "skipping unreadable " + uri);
                    continue;
                }
                sources.add(source);
            }

            if (sources.isEmpty()) {
                runOnUiThread(() -> {
                    if (binding == null) {
                        return;
                    }
                    target.state = SendState.FAILED;
                    target.detail = getString(R.string.file_drop_share_unreadable);
                    adapter.refresh(target);
                });
                return;
            }
            FileDropManager.get().send(sources, target.pubKey, target.name, target.ip, callback);
        });
    }

    /**
     * Maps live transfers onto the rows that started them. Matching is by peer
     * key, so a row keeps following its own transfer while other rows run
     * theirs.
     */
    private void onTransfers(List<FileDropManager.Transfer> transfers) {
        if (binding == null) {
            return;
        }

        for (PeerTarget target : allTargets) {
            if (target.state == SendState.IDLE || target.settled()) {
                continue;
            }

            FileDropManager.Transfer mine = find(transfers, target.transferId);

            SendState before = target.state;
            String detailBefore = target.detail;

            // A cancel can also come from elsewhere, the Files tab included.
            // Either way the row goes back to being pickable rather than
            // reporting a failure it did not have.
            if (mine != null && mine.state() == Android.FileDropStateCancelled) {
                reset(target);
                continue;
            }

            if (mine != null && mine.isTerminal()) {
                target.state = terminalStateOf(mine);
                target.detail = null;
            } else if (mine != null && mine.isRunning() && mine.totalSize() > 0) {
                target.state = SendState.SENDING;
                target.progress = (int) (mine.transferred() * 100 / mine.totalSize());
            }

            // A row in flight is redrawn on every update even when nothing about
            // it changed: the item animator's cross-fade is what makes it pulse,
            // which is the only sign of life a row has while it waits for the
            // peer to answer and nothing is moving yet.
            if (target.state != before || !java.util.Objects.equals(target.detail, detailBefore)
                    || target.state == SendState.SENDING || target.state == SendState.WAITING) {
                adapter.refresh(target);
            }
        }
    }

    /** The transfer this row started, or null while its id is still unknown. */
    @Nullable
    private static FileDropManager.Transfer find(List<FileDropManager.Transfer> transfers,
                                                 @Nullable String transferId) {
        if (transferId == null) {
            return null;
        }
        for (FileDropManager.Transfer transfer : transfers) {
            if (transfer.id().equals(transferId)) {
                return transfer;
            }
        }
        return null;
    }

    /**
     * Asks before killing a send in flight. Long press is easy enough to hit by
     * accident on a list whose rows are meant to be tapped, and the bytes are
     * already on their way, so the prompt is the safeguard.
     */
    @SuppressLint("InflateParams")
    private void confirmStop(PeerTarget target) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_simple_edit_text, null);

        ((TextView) view.findViewById(R.id.text_title_dialog)).setText(target.name);
        ((TextView) view.findViewById(R.id.text_label_dialog))
                .setText(R.string.file_drop_share_stop_confirm);
        view.findViewById(R.id.edit_text_dialog).setVisibility(View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setView(view)
                .create();

        MaterialButton confirm = view.findViewById(R.id.btn_ok_dialog);
        confirm.setText(R.string.file_drop_share_stop);
        confirm.setOnClickListener(v -> {
            stop(target);
            dialog.dismiss();
        });
        view.findViewById(R.id.btn_cancel_dialog).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Aborts this row's transfer and hands the row back untouched, so the same
     * files can be sent again to the same peer. Anything the transfer staged is
     * released by the manager once Go reports it cancelled.
     */
    private void stop(PeerTarget target) {
        if (target.transferId != null) {
            FileDropManager.get().cancel(target.transferId);
        }
        reset(target);
    }

    /** Returns a row to its untouched, pickable state. */
    private void reset(PeerTarget target) {
        target.transferId = null;
        target.state = SendState.IDLE;
        target.progress = 0;
        target.detail = null;
        adapter.refresh(target);
    }

    private void toastAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    /** Renders the pickable peers; each row shows its own send lifecycle. */
    private final class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.Holder> {

        private final List<PeerTarget> targets = new ArrayList<>();
        private final Consumer<PeerTarget> onPick;

        PeerAdapter(Consumer<PeerTarget> onPick) {
            this.onPick = onPick;
        }

        void submit(List<PeerTarget> next) {
            targets.clear();
            targets.addAll(next);
            notifyDataSetChanged();
        }

        /** Redraws one row in place, leaving the rest of the list untouched. */
        void refresh(PeerTarget target) {
            int index = targets.indexOf(target);
            if (index >= 0) {
                notifyItemChanged(index);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item_peer_picker, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            PeerTarget target = targets.get(position);

            holder.name.setText(target.name);
            holder.subtitle.setText(subtitleFor(target));
            holder.progress.setVisibility(View.GONE);

            switch (target.state) {
                case WAITING:
                    holder.state.setText(R.string.file_drop_share_state_waiting);
                    holder.state.setTextColor(color(R.color.nb_txt_light));
                    break;
                case SENDING:
                    holder.state.setText(getString(R.string.file_drop_state_progress,
                            target.progress));
                    holder.state.setTextColor(color(R.color.nb_orange));
                    holder.progress.setVisibility(View.VISIBLE);
                    holder.progress.setProgress(target.progress);
                    break;
                case SENT:
                    holder.state.setText(R.string.file_drop_share_state_sent);
                    holder.state.setTextColor(color(R.color.nb_latency_good));
                    break;
                case DECLINED:
                    holder.state.setText(R.string.file_drop_state_declined);
                    holder.state.setTextColor(color(R.color.nb_danger));
                    break;
                case EXPIRED:
                    holder.state.setText(R.string.file_drop_state_expired);
                    holder.state.setTextColor(color(R.color.nb_txt_light));
                    break;
                case FAILED:
                    holder.state.setText(R.string.file_drop_state_failed);
                    holder.state.setTextColor(color(R.color.nb_danger));
                    break;
                default:
                    // An untouched row says nothing: tapping to send is the
                    // only thing a row does, so a label would be noise.
                    holder.state.setText("");
                    break;
            }

            // Only an untouched row is tappable: a second tap would start a
            // duplicate transfer to the same peer.
            boolean tappable = target.state == SendState.IDLE;
            holder.itemView.setOnClickListener(tappable ? v -> onPick.accept(target) : null);
            holder.itemView.setClickable(tappable);

            // Stopping is a long press rather than a button: tap already means
            // send here, and a second meaning for it would put a 500 MB upload
            // one stray finger away from being killed.
            boolean stoppable = target.state == SendState.WAITING
                    || target.state == SendState.SENDING;
            holder.itemView.setOnLongClickListener(stoppable ? v -> {
                confirmStop(target);
                return true;
            } : null);
            holder.itemView.setLongClickable(stoppable);
        }

        /** IP, plus the reason a row cannot be sent to, or why it stopped. */
        private String subtitleFor(PeerTarget target) {
            if (target.state == SendState.FAILED && target.detail != null) {
                return target.detail;
            }
            // Idle is what a row looks like before it is picked. Once a send is
            // under way the transfer's own packets have woken the peer, so the
            // label would be stale, and the send state to the right says more.
            if (target.connected || target.state != SendState.IDLE) {
                return target.ip;
            }
            return target.ip + " · " + getString(R.string.peer_status_idle);
        }

        private int color(int res) {
            return ContextCompat.getColor(ShareTargetActivity.this, res);
        }

        @Override
        public int getItemCount() {
            return targets.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView subtitle;
            final TextView state;
            final ProgressBar progress;

            Holder(View view) {
                super(view);
                name = view.findViewById(R.id.peer_name);
                subtitle = view.findViewById(R.id.peer_ip);
                state = view.findViewById(R.id.peer_state);
                progress = view.findViewById(R.id.peer_progress);
            }
        }
    }

    /**
     * Maps a finished transfer onto the row state. Declined and expired are
     * kept apart from a genuine failure: the first two are answers, the third
     * means the transfer never got one.
     */
    private static SendState terminalStateOf(FileDropManager.Transfer transfer) {
        long state = transfer.state();
        if (state == Android.FileDropStateCompleted) {
            return SendState.SENT;
        }
        if (state == Android.FileDropStateDeclined) {
            return SendState.DECLINED;
        }
        if (state == Android.FileDropStateExpired) {
            return SendState.EXPIRED;
        }
        return SendState.FAILED;
    }

    /** Where a row is in the send lifecycle; see send(). */
    private enum SendState { IDLE, WAITING, SENDING, SENT, DECLINED, EXPIRED, FAILED }

    private static final class PeerTarget {
        final String pubKey;
        final String name;
        final String ip;
        final boolean connected;

        SendState state = SendState.IDLE;
        int progress;
        String detail;
        // Set once the send call reports which transfer it started; see send().
        String transferId;

        /** Whether this row's transfer has reached an outcome. */
        boolean settled() {
            return state == SendState.SENT || state == SendState.DECLINED
                    || state == SendState.EXPIRED || state == SendState.FAILED;
        }

        PeerTarget(String pubKey, String name, String ip, boolean connected) {
            this.pubKey = pubKey;
            this.name = name;
            this.ip = ip;
            this.connected = connected;
        }

        boolean matches(String query) {
            if (query.isEmpty()) {
                return true;
            }
            Locale locale = Locale.getDefault();
            return name.toLowerCase(locale).contains(query) || ip.contains(query);
        }
    }
}
