package io.netbird.client.ui.ssh;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import io.netbird.gomobile.android.SSHClient;
import io.netbird.gomobile.android.SSHTerminalListener;
import io.netbird.gomobile.android.URLOpener;

/**
 * A single SSH session wired to a gomobile {@link SSHClient}. The session
 * outlives the lifecycle of any individual {@link SSHTerminalFragment}: UI
 * fragments attach as listeners to receive live output and can detach
 * (e.g. when backgrounded) without affecting the underlying SSH connection.
 *
 * <p>Output bytes received from the remote side are appended to a fixed-size
 * ring buffer so a freshly attached listener can replay the scrollback before
 * resuming live updates.
 */
public class SshSession {

    private static final String LOGTAG = "SshSession";
    private static final int MAX_SCROLLBACK = 256 * 1024;

    /** NEEDS_PASSWORD and NEEDS_HOSTKEY_CONFIRM are pauses, not failures: each
     *  waits for the UI to call the matching retry. */
    public enum State { CONNECTING, CONNECTED, NEEDS_PASSWORD, NEEDS_HOSTKEY_CONFIRM, CLOSED, ERROR }

    /** Marker the Go binding puts in the error when a password would help. */
    private static final String PASSWORD_REQUIRED_MARKER = "netbird-ssh-password-required";
    /** Marker the Go binding puts in the error, followed by ":" and the
     *  presented SHA256 fingerprint, when a regular server's host key is not
     *  yet trusted. */
    private static final String HOSTKEY_UNKNOWN_MARKER = "netbird-ssh-hostkey-unknown";

    public interface Listener {
        void onScrollback(byte[] data);
        void onData(byte[] data);
        void onStateChange(State state, String message);
    }

    private final String id;
    private final String host;
    private final int port;
    private final String user;
    /** Not final: filled in later when the server turns out to want one. */
    private volatile String password;

    private volatile SSHClient client;
    /** Per-profile TOFU host-key store for regular servers; null until set. */
    private volatile String knownHostsPath;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private final Object bufferLock = new Object();
    private final byte[] buffer = new byte[MAX_SCROLLBACK];
    private int bufferLen = 0;
    private boolean bufferWrapped = false;

    private volatile State state = State.CONNECTING;
    private volatile String stateMessage = "";
    private volatile boolean sessionStarted = false;
    /** Sticky, unlike sessionStarted: a reconnect must not read as a first connect. */
    private volatile boolean everConnected = false;
    /** Passwords tried so far, to tell a first prompt from a rejected one. */
    private volatile int passwordAttempts = 0;

    private int lastCols = 80;
    private int lastRows = 24;

    SshSession(String id, String host, int port, String user, String password,
               SSHClient client, URLOpener urlOpener) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        if (client != null) {
            bindClient(client, urlOpener);
        }
    }

    /** Restored from disk: no client until a reconnect supplies one. */
    SshSession(String id, String host, int port, String user) {
        this(id, host, port, user, "", null, null);
        this.state = State.CLOSED;
    }

    void bindClient(@NonNull SSHClient client, URLOpener urlOpener) {
        this.client = client;
        client.setListener(new BridgeListener());
        if (urlOpener != null) {
            client.setURLOpener(urlOpener);
        }
        if (knownHostsPath != null) {
            client.setKnownHostsPath(knownHostsPath);
        }
    }

    /** Re-points a kept client at a live URL opener, since the one it holds may
     *  belong to an activity that is already gone. */
    void setURLOpener(URLOpener urlOpener) {
        SSHClient current = client;
        if (current != null && urlOpener != null) {
            current.setURLOpener(urlOpener);
        }
    }

    /** Points a regular server's host-key verification at the profile's store.
     *  Applied to every client this session binds, including reconnects. */
    void setKnownHostsPath(String path) {
        this.knownHostsPath = path;
        SSHClient current = client;
        if (current != null && path != null) {
            current.setKnownHostsPath(path);
        }
    }

    boolean hasClient() {
        return client != null;
    }

    public String getId() { return id; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUser() { return user; }
    public State getState() { return state; }
    public String getStateMessage() { return stateMessage; }
    public boolean isSessionStarted() { return sessionStarted; }
    public boolean hasEverConnected() { return everConnected; }

    public String getDisplayLabel() {
        return user + "@" + host + ":" + port;
    }

    /** Kicks off connect + StartSession on a background thread. */
    void connectAsync(int cols, int rows) {
        this.lastCols = cols;
        this.lastRows = rows;
        SSHClient target = client;
        if (target == null) {
            setState(State.ERROR, "NetBird is not running");
            return;
        }
        new Thread(() -> {
            try {
                target.connect(host, port, user, password);
                target.startSession(cols, rows);
                sessionStarted = true;
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : "connect failed";
                if (message.contains(PASSWORD_REQUIRED_MARKER)) {
                    // The marker on a retry means the password was wrong.
                    boolean afterAttempt = passwordAttempts > 0;
                    Log.d(LOGTAG, afterAttempt
                            ? "ssh: password rejected, asking again"
                            : "ssh: server wants a password");
                    setState(State.NEEDS_PASSWORD, afterAttempt ? "Wrong password" : "");
                    return;
                }
                String fingerprint = hostKeyFingerprint(message);
                if (fingerprint != null) {
                    Log.d(LOGTAG, "ssh: unknown host key, asking to confirm");
                    setState(State.NEEDS_HOSTKEY_CONFIRM, fingerprint);
                    return;
                }
                Log.w(LOGTAG, "ssh connect failed: " + message);
                setState(State.ERROR, message);
            }
        }, "ssh-session-" + id).start();
    }

    /**
     * Retries the connection with a password the user supplied after the
     * session landed in {@link State#NEEDS_PASSWORD}. Can be called repeatedly:
     * a rejected password puts the session back into that state, so the user
     * gets further attempts as with any ssh client.
     */
    void retryWithPassword(@NonNull String password) {
        this.password = password;
        passwordAttempts++;
        setState(State.CONNECTING, "");
        connectAsync(lastCols, lastRows);
    }

    /** Gives up on a session waiting for a password. */
    void cancelPasswordPrompt() {
        setState(State.CLOSED, "cancelled");
    }

    /**
     * Pulls the SHA256 fingerprint out of the host-key marker, or returns null
     * when the error is not that marker. The Go side formats it as
     * "{@value #HOSTKEY_UNKNOWN_MARKER}:SHA256:...".
     */
    private static String hostKeyFingerprint(String message) {
        int marker = message.indexOf(HOSTKEY_UNKNOWN_MARKER);
        if (marker < 0) {
            return null;
        }
        int colon = message.indexOf(':', marker);
        if (colon < 0) {
            return null;
        }
        String fingerprint = message.substring(colon + 1).trim();
        return fingerprint.isEmpty() ? null : fingerprint;
    }

    /**
     * Retries after the user confirmed the host-key fingerprint shown while the
     * session was in {@link State#NEEDS_HOSTKEY_CONFIRM}. The confirmed
     * fingerprint is handed to the client so it trusts exactly that key and
     * persists it; a key that changed since the prompt makes the retry fail.
     */
    void retryWithHostKeyTrust(@NonNull String fingerprint) {
        SSHClient target = client;
        if (target != null) {
            target.trustHostKey(fingerprint);
        }
        setState(State.CONNECTING, "");
        connectAsync(lastCols, lastRows);
    }

    /** Gives up on a session waiting for host-key confirmation. */
    void cancelHostKeyPrompt() {
        setState(State.CLOSED, "cancelled");
    }

    public void attach(@NonNull Listener listener) {
        listeners.add(listener);
        byte[] backlog = snapshotBuffer();
        if (backlog.length > 0) {
            listener.onScrollback(backlog);
        }
        listener.onStateChange(state, stateMessage);
    }

    public void detach(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    public void write(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        try {
            client.write(data);
        } catch (Exception e) {
            Log.d(LOGTAG, "write failed: " + e.getMessage());
        }
    }

    public void resize(int cols, int rows) {
        this.lastCols = cols;
        this.lastRows = rows;
        if (!sessionStarted) {
            return;
        }
        try {
            client.resize(cols, rows);
        } catch (Exception e) {
            Log.d(LOGTAG, "resize failed: " + e.getMessage());
        }
    }

    public int getCols() { return lastCols; }
    public int getRows() { return lastRows; }

    /** True while the session is finished but still reconnectable. */
    public boolean isReconnectable() {
        return state == State.CLOSED || state == State.ERROR;
    }

    /** Dials again, reusing this session so the scrollback survives. The
     *  password is kept: a server that wanted one before will want it again. */
    public void reconnect() {
        if (!isReconnectable()) {
            return;
        }
        if (client != null) {
            client.reset();
        }
        sessionStarted = false;
        passwordAttempts = 0;
        setState(State.CONNECTING, "");
        connectAsync(lastCols, lastRows);
    }

    /** Ends the connection but keeps the session listed for a reconnect. */
    public void disconnect() {
        if (isReconnectable()) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            Log.d(LOGTAG, "disconnect failed: " + e.getMessage());
        }
        sessionStarted = false;
        setState(State.CLOSED, "disconnected");
    }

    public void close() {
        try {
            client.close();
        } catch (Exception e) {
            Log.d(LOGTAG, "close failed: " + e.getMessage());
        }
        if (state != State.CLOSED && state != State.ERROR) {
            setState(State.CLOSED, "closed by user");
        }
    }

    private void setState(State newState, String msg) {
        state = newState;
        stateMessage = msg != null ? msg : "";
        for (Listener l : listeners) {
            try {
                l.onStateChange(newState, stateMessage);
            } catch (Exception e) {
                Log.w(LOGTAG, "listener.onStateChange threw: " + e.getMessage());
            }
        }
    }

    private void appendToBuffer(byte[] data) {
        synchronized (bufferLock) {
            int len = data.length;
            if (len >= buffer.length) {
                System.arraycopy(data, len - buffer.length, buffer, 0, buffer.length);
                bufferLen = buffer.length;
                bufferWrapped = true;
                return;
            }
            int remaining = buffer.length - bufferLen;
            if (len <= remaining) {
                System.arraycopy(data, 0, buffer, bufferLen, len);
                bufferLen += len;
            } else {
                int shift = len - remaining;
                System.arraycopy(buffer, shift, buffer, 0, bufferLen - shift);
                bufferLen -= shift;
                System.arraycopy(data, 0, buffer, bufferLen, len);
                bufferLen += len;
                bufferWrapped = true;
            }
        }
    }

    private byte[] snapshotBuffer() {
        synchronized (bufferLock) {
            byte[] copy = new byte[bufferLen];
            System.arraycopy(buffer, 0, copy, 0, bufferLen);
            return copy;
        }
    }

    public boolean isBufferTruncated() {
        synchronized (bufferLock) {
            return bufferWrapped;
        }
    }

    private final class BridgeListener implements SSHTerminalListener {
        @Override
        public void onConnected() {
            // Set after the listeners run, so they can still tell this apart
            // from a reconnect and leave the scrollback alone.
            setState(State.CONNECTED, "");
            everConnected = true;
        }

        @Override
        public void onData(byte[] data) {
            if (data == null || data.length == 0) {
                return;
            }
            appendToBuffer(data);
            for (Listener l : listeners) {
                try {
                    l.onData(data);
                } catch (Exception e) {
                    Log.w(LOGTAG, "listener.onData threw: " + e.getMessage());
                }
            }
        }

        @Override
        public void onClose(String reason) {
            sessionStarted = false;
            setState(State.CLOSED, reason != null ? reason : "");
        }

        @Override
        public void onError(String message) {
            sessionStarted = false;
            setState(State.ERROR, message != null ? message : "");
        }
    }

    /** Snapshot of session metadata for stable iteration in the UI. */
    public static final class Info {
        public final String id;
        public final String host;
        public final int port;
        public final String user;
        public final State state;
        public final String stateMessage;
        /** False when there is no output worth reading before reconnecting. */
        public final boolean hasScrollback;
        /** 1-based position among sessions to the same target; 0 when alone. */
        public final int ordinal;

        Info(SshSession s, int ordinal) {
            this.id = s.id;
            this.host = s.host;
            this.port = s.port;
            this.user = s.user;
            this.state = s.state;
            this.stateMessage = s.stateMessage;
            this.ordinal = ordinal;
            synchronized (s.bufferLock) {
                this.hasScrollback = s.bufferLen > 0;
            }
        }

        public String label() {
            String target = user + "@" + host + ":" + port;
            // Leading, like tmux: the target is long enough to get truncated on
            // a narrow row, which would drop the very part that disambiguates.
            return ordinal > 0 ? "#" + ordinal + "  " + target : target;
        }
    }

    /**
     * Numbers the sessions sharing a target, so parallel ones to the same host
     * can be told apart. A target with only one session gets no number, since
     * there is nothing to distinguish it from.
     */
    static List<Info> snapshot(List<SshSession> sessions) {
        Map<String, Integer> totals = new HashMap<>();
        for (SshSession s : sessions) {
            String target = s.getDisplayLabel();
            totals.put(target, totals.getOrDefault(target, 0) + 1);
        }

        Map<String, Integer> seen = new HashMap<>();
        List<Info> out = new ArrayList<>(sessions.size());
        for (SshSession s : sessions) {
            String target = s.getDisplayLabel();
            int ordinal = 0;
            if (totals.get(target) > 1) {
                ordinal = seen.getOrDefault(target, 0) + 1;
                seen.put(target, ordinal);
            }
            out.add(new Info(s, ordinal));
        }
        return out;
    }
}
