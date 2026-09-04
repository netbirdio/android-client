package io.netbird.client.tool;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Fans the Go client's session notifications out to the app, mirroring what
 * the desktop daemon feeds its tray. The engine owns the expiry timers and
 * publishes the warnings; this class only forwards them and edge-detects the
 * NeedsLogin status label, which the run loop sets outside the event stream.
 *
 * <p>All state lives on the main thread; {@link #onStateChanged()} and
 * {@link #onSessionExpiring} may be called from any thread.
 */
public class SessionMonitor {

    /** Run-loop status label meaning an interactive login is required. */
    private static final String STATUS_NEEDS_LOGIN = "NeedsLogin";

    private static final String LOGTAG = "SessionMonitor";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Supplier<String> status;
    private final LongSupplier deadlineUnixSeconds;
    private final Set<SessionEventListener> listeners = ConcurrentHashMap.newKeySet();

    private long lastDeadline;
    private boolean wasNeedsLogin;

    public SessionMonitor(Supplier<String> status, LongSupplier deadlineUnixSeconds) {
        this.status = status;
        this.deadlineUnixSeconds = deadlineUnixSeconds;
    }

    /** Wake-up from the Go state-change signal. Safe to call from any thread. */
    public void onStateChanged() {
        handler.post(this::refresh);
    }

    /** Expiry warning from the engine's session watcher. Any thread. */
    public void onSessionExpiring(long expiresAtUnixSeconds, long leadMinutes, boolean finalWarning) {
        handler.post(() -> {
            for (SessionEventListener l : listeners) {
                notifySafely(() -> l.onSessionExpiring(expiresAtUnixSeconds, leadMinutes, finalWarning));
            }
        });
    }

    public void addListener(SessionEventListener listener) {
        listeners.add(listener);
        // Replay the current state so a late-binding UI doesn't miss what
        // happened while it was detached. Both cases are real: the activity
        // unbinds for the SSO browser round-trip (which is when an extend
        // moves the deadline), and a session can expire with no UI running at
        // all — the expiry is an edge the listener would never see otherwise.
        // Same pattern as the Go notifier's setListener.
        handler.post(() -> {
            refresh();
            notifySafely(() -> listener.onSessionDeadlineChanged(lastDeadline));
            if (isLoginRequired()) {
                notifySafely(listener::onSessionExpired);
            }
        });
    }

    /** True while the run loop reports that an interactive login is needed. */
    public boolean isLoginRequired() {
        return STATUS_NEEDS_LOGIN.equals(status.get());
    }

    public void removeListener(SessionEventListener listener) {
        listeners.remove(listener);
    }

    private void refresh() {
        long deadline = deadlineUnixSeconds.getAsLong();
        if (deadline != lastDeadline) {
            lastDeadline = deadline;
            for (SessionEventListener l : listeners) {
                notifySafely(() -> l.onSessionDeadlineChanged(deadline));
            }
        }

        boolean needsLogin = STATUS_NEEDS_LOGIN.equals(status.get());
        if (needsLogin && !wasNeedsLogin) {
            for (SessionEventListener l : listeners) {
                notifySafely(l::onSessionExpired);
            }
        }
        wasNeedsLogin = needsLogin;
    }

    private void notifySafely(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            Log.w(LOGTAG, "session listener failed", e);
        }
    }
}
