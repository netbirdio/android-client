package io.netbird.client.tool.files;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.FileDrop;
import io.netbird.gomobile.android.FileDropListener;
import io.netbird.gomobile.android.FileDropPayloads;
import io.netbird.gomobile.android.FileDropTransfer;
import io.netbird.gomobile.android.FileDropTransferArray;

/**
 * Application-scoped view of the file drop state, outliving any single screen.
 * Policy and history live in Go, keyed by profile; this only mirrors them out to
 * {@link TransfersListener}s and keeps every Go call off the UI thread.
 * <p>
 * Every Go call here can block for seconds during engine bootstrap or teardown,
 * the same hazard the peer list has, so all of them run on a single background
 * executor and callbacks arrive on that thread rather than the caller's.
 */
public class FileDropManager {

    private static final String LOGTAG = "FileDropManager";

    private static final FileDropManager INSTANCE = new FileDropManager();

    /** Supplies the file drop handle, which only the bound service can open. */
    public interface HandleFactory {
        @Nullable
        FileDrop fileDrop();
    }

    /** Notified for offers that need the user's consent. */
    public interface OfferListener {
        void onOffer(Transfer transfer);
    }

    /** Notified whenever the transfer list changes. Called off the UI thread. */
    public interface TransfersListener {
        void onTransfers(List<Transfer> transfers);
    }

    /** Immutable snapshot of one transfer, safe to hand to adapters. */
    public static final class Transfer {
        private final String id;
        private final boolean outgoing;
        private final String peerKey;
        private final String peerName;
        private final long state;
        private final long transferred;
        private final long totalSize;
        private final String error;
        private final long reason;
        private final long createdAtMillis;
        private final boolean isText;
        private final String text;
        private final List<String> fileNames;
        private final List<String> deliveredPaths;

        Transfer(FileDropTransfer t) {
            id = t.getID();
            outgoing = t.getOutgoing();
            peerKey = t.getPeerKey();
            peerName = t.getPeerName();
            state = t.getState();
            transferred = t.getTransferred();
            totalSize = t.getTotalSize();
            error = t.getError();
            reason = t.getReason();
            createdAtMillis = t.getCreatedAtMillis();
            isText = t.getIsText();
            text = isText && t.fileCount() > 0 ? t.getFile(0).getText() : "";

            List<String> names = new ArrayList<>();
            for (long i = 0; i < t.fileCount(); i++) {
                names.add(t.getFile(i).getName());
            }
            fileNames = Collections.unmodifiableList(names);

            String delivered = t.deliveredPaths();
            deliveredPaths = delivered.isEmpty()
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(List.of(delivered.split("\n"))));
        }

        public String id() { return id; }
        public boolean outgoing() { return outgoing; }
        public String peerKey() { return peerKey; }
        public String peerName() { return peerName; }
        public long state() { return state; }
        public long transferred() { return transferred; }
        public long totalSize() { return totalSize; }
        public String error() { return error; }
        public long createdAtMillis() { return createdAtMillis; }
        public boolean isText() { return isText; }
        public String text() { return text; }
        public List<String> fileNames() { return fileNames; }
        public List<String> deliveredPaths() { return deliveredPaths; }

        public boolean isPending() {
            return state == Android.FileDropStatePending;
        }

        public boolean isUnreachable() {
            return reason == Android.FileDropReasonUnreachable;
        }

        /** Whether the transfer ended in anything other than success. */
        public boolean isFailed() {
            return state == Android.FileDropStateFailed
                    || state == Android.FileDropStateDeclined
                    || state == Android.FileDropStateExpired;
        }

        /** Whether bytes are moving right now. */
        public boolean isRunning() {
            return state == Android.FileDropStateTransferring;
        }

        /** Whether the transfer has stopped for good, in any outcome. */
        public boolean isTerminal() {
            return state == Android.FileDropStateCompleted
                    || state == Android.FileDropStateDeclined
                    || state == Android.FileDropStateExpired
                    || state == Android.FileDropStateCancelled
                    || state == Android.FileDropStateFailed;
        }

        public String label() {
            if (fileNames.size() == 1) {
                return fileNames.get(0);
            }
            return fileNames.size() + " files";
        }
    }

    public static FileDropManager get() {
        return INSTANCE;
    }

    private final Set<TransfersListener> transfersListeners = ConcurrentHashMap.newKeySet();
    // Staged copies of outgoing files, keyed by transfer id; see send().
    private final Map<String, List<ContentFileSource>> staged = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile List<Transfer> transfers = Collections.emptyList();
    private HandleFactory handleFactory;
    private OfferListener offerListener;
    private String listeningProfileId;

    private final FileDropListener goListener = (kind, transfer) -> {
        Transfer snapshot = new Transfer(transfer);
        if (kind == Android.FileDropEventOffer) {
            OfferListener listener;
            synchronized (FileDropManager.this) {
                listener = offerListener;
            }
            if (listener != null) {
                listener.onOffer(snapshot);
            }
        }
        refresh();
    };

    private FileDropManager() {}

    /** Last published transfer list, newest first. */
    public List<Transfer> transfers() {
        return transfers;
    }

    /**
     * Registers a listener for transfer-list updates and replays the current
     * list to it, so a screen opening between two updates is not left blank.
     * Callbacks arrive on a background thread.
     */
    public void addTransfersListener(@NonNull TransfersListener listener) {
        transfersListeners.add(listener);
        listener.onTransfers(transfers);
    }

    public void removeTransfersListener(@NonNull TransfersListener listener) {
        transfersListeners.remove(listener);
    }

    /** Set while an activity or service is bound, cleared when it goes. */
    public synchronized void setHandleFactory(@Nullable HandleFactory factory) {
        handleFactory = factory;
        listeningProfileId = null;
        if (factory != null) {
            refresh();
        }
    }

    /**
     * Drops a factory on the way out, unless a newer one replaced it already.
     * A recreated activity starts before the old one is destroyed, so the old
     * instance would otherwise clear its successor's registration.
     */
    public synchronized void clearHandleFactory(@NonNull HandleFactory factory) {
        if (handleFactory == factory) {
            setHandleFactory(null);
        }
    }

    /** Set by whatever surfaces consent prompts, typically the foreground service. */
    public synchronized void setOfferListener(@Nullable OfferListener listener) {
        offerListener = listener;
    }

    /** Reloads the transfer list from Go and republishes it. */
    public void refresh() {
        submit(FileDropManager::refreshOn);
    }

    private static void refreshOn(FileDrop handle) {
        FileDropTransferArray array = handle.transfers();
        List<Transfer> list = new ArrayList<>();
        for (long i = 0; i < array.length(); i++) {
            list.add(new Transfer(array.get(i)));
        }
        INSTANCE.publish(Collections.unmodifiableList(list));
    }

    private void publish(List<Transfer> list) {
        transfers = list;
        releaseStaged(list);
        for (TransfersListener listener : transfersListeners) {
            listener.onTransfers(list);
        }
    }

    /**
     * Drops the staged copies of transfers that have finished. A transfer that
     * vanished from the log entirely counts as finished too, so a deleted entry
     * does not leave its bytes behind.
     */
    private void releaseStaged(List<Transfer> list) {
        if (staged.isEmpty()) {
            return;
        }

        Set<String> live = new HashSet<>();
        for (Transfer t : list) {
            if (!t.isTerminal()) {
                live.add(t.id());
            }
        }

        for (Map.Entry<String, List<ContentFileSource>> entry : staged.entrySet()) {
            if (live.contains(entry.getKey())) {
                continue;
            }
            for (ContentFileSource source : entry.getValue()) {
                source.release();
            }
            staged.remove(entry.getKey());
        }
    }

    /**
     * Sends the given content Uris to a peer. Reading their metadata can hit the
     * disk, so it happens on the executor rather than at the call site.
     */
    public void send(@NonNull List<ContentFileSource> sources, @NonNull String peerKey,
                     @NonNull String peerName, @NonNull String peerIp, @Nullable ResultCallback callback) {
        submitWithResult(handle -> {
            FileDropPayloads payloads = new FileDropPayloads();
            for (ContentFileSource source : sources) {
                payloads.addFile(source.name(), source.size(), source.contentType(), source);
            }
            String id;
            try {
                Log.i(LOGTAG, "sending " + sources.size() + " file(s) to " + peerName
                        + " (" + peerIp + ")");
                id = handle.send(peerKey, peerName, peerIp, payloads);
                Log.i(LOGTAG, "send started, transfer " + id);
            } catch (Exception e) {
                // Nothing will ever report this transfer as finished, so the
                // staged copies have to go here or they leak.
                for (ContentFileSource source : sources) {
                    source.release();
                }
                throw e;
            }
            // Sending is asynchronous, so the staged copies have to outlive this
            // call and are released when the transfer reaches a terminal state.
            staged.put(id, sources);
            refreshOn(handle);
            return id;
        }, callback);
    }

    /** Sends an inline text snippet to a peer. */
    public void sendText(@NonNull String text, @NonNull String peerKey, @NonNull String peerName,
                         @NonNull String peerIp, @Nullable ResultCallback callback) {
        submitWithResult(handle -> {
            FileDropPayloads payloads = new FileDropPayloads();
            payloads.addText("text", text);
            String id = handle.send(peerKey, peerName, peerIp, payloads);
            refreshOn(handle);
            return id;
        }, callback);
    }

    public void accept(@NonNull String transferId, @Nullable ResultCallback callback) {
        submitWithResult(handle -> {
            handle.accept(transferId);
            refreshOn(handle);
            return null;
        }, callback);
    }

    public void decline(@NonNull String transferId, @Nullable ResultCallback callback) {
        submitWithResult(handle -> {
            handle.decline(transferId);
            refreshOn(handle);
            return null;
        }, callback);
    }

    public void cancel(@NonNull String transferId) {
        submit(handle -> {
            handle.cancel(transferId);
            refreshOn(handle);
        });
    }

    public void delete(@NonNull String transferId) {
        submit(handle -> {
            handle.deleteTransfer(transferId);
            refreshOn(handle);
        });
    }

    /** Reads the receiving mode, falling back to "ask" when Go is unreachable. */
    public void mode(@NonNull ValueCallback<Long> callback) {
        submitWithResult(FileDrop::mode, callback);
    }

    public void setMode(long mode, @Nullable ResultCallback callback) {
        submitWithResult(handle -> {
            handle.setMode(mode);
            return null;
        }, callback);
    }

    public void peerRule(@NonNull String peerKey, @NonNull ValueCallback<Long> callback) {
        submitWithResult(handle -> handle.peerRule(peerKey), callback);
    }

    public void setPeerRule(@NonNull String peerKey, long rule, @Nullable ResultCallback callback) {
        submitWithResult(handle -> {
            handle.setPeerRule(peerKey, rule);
            return null;
        }, callback);
    }

    public void destinationDir(@NonNull ValueCallback<String> callback) {
        submitWithResult(FileDrop::destinationDir, callback);
    }

    /** Callback for an operation whose only outcome is success or a message. */
    public interface ResultCallback {
        void onResult(boolean ok, @Nullable String error);
    }

    /** Callback for an operation that reads a value out of Go. */
    public interface ValueCallback<T> extends ResultCallback {
        void onValue(T value);

        @Override
        default void onResult(boolean ok, @Nullable String error) {}
    }

    private interface Action {
        void run(FileDrop handle) throws Exception;
    }

    private interface Query<T> {
        T run(FileDrop handle) throws Exception;
    }

    private void submit(Action action) {
        submitWithResult(handle -> {
            action.run(handle);
            return null;
        }, null);
    }

    private <T> void submitWithResult(Query<T> query, @Nullable ResultCallback callback) {
        try {
            executor.execute(() -> {
                FileDrop handle = openHandle();
                if (handle == null) {
                    Log.w(LOGTAG, "no file drop handle; is the VPN service bound?");
                    report(callback, false, "NetBird is not running");
                    return;
                }
                try {
                    T value = query.run(handle);
                    if (callback instanceof ValueCallback) {
                        //noinspection unchecked
                        ((ValueCallback<T>) callback).onValue(value);
                    }
                    report(callback, true, null);
                } catch (Exception e) {
                    Log.e(LOGTAG, "file drop call failed", e);
                    report(callback, false, e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            Log.w(LOGTAG, "file drop executor rejected the call", e);
            report(callback, false, "file drop is shutting down");
        }
    }

    private static void report(@Nullable ResultCallback callback, boolean ok, @Nullable String error) {
        if (callback != null) {
            callback.onResult(ok, error);
        }
    }

    /**
     * Opens the handle and, on a profile switch, moves the event listener onto
     * the new one: Go swaps handles per profile, and the Java listener has to
     * follow so consent prompts keep arriving.
     */
    @Nullable
    private FileDrop openHandle() {
        HandleFactory factory;
        synchronized (this) {
            factory = handleFactory;
        }
        if (factory == null) {
            return null;
        }

        FileDrop handle = factory.fileDrop();
        if (handle == null) {
            return null;
        }

        synchronized (this) {
            String profileId = handle.profileID();
            if (!profileId.equals(listeningProfileId)) {
                handle.setListener(goListener);
                listeningProfileId = profileId;
            }
        }
        return handle;
    }
}
