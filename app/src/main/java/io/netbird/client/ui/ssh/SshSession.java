package io.netbird.client.ui.ssh;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
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

    public enum State { CONNECTING, CONNECTED, CLOSED, ERROR }

    public interface Listener {
        void onScrollback(byte[] data);
        void onData(byte[] data);
        void onStateChange(State state, String message);
    }

    private final String id;
    private final String host;
    private final int port;
    private final String user;
    private final String password;

    private final SSHClient client;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private final Object bufferLock = new Object();
    private final byte[] buffer = new byte[MAX_SCROLLBACK];
    private int bufferLen = 0;
    private boolean bufferWrapped = false;

    private volatile State state = State.CONNECTING;
    private volatile String stateMessage = "";
    private volatile boolean sessionStarted = false;

    private int lastCols = 80;
    private int lastRows = 24;

    SshSession(String id, String host, int port, String user, String password,
               SSHClient client, URLOpener urlOpener) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.client = client;
        this.client.setListener(new BridgeListener());
        if (urlOpener != null) {
            this.client.setURLOpener(urlOpener);
        }
    }

    public String getId() { return id; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUser() { return user; }
    public State getState() { return state; }
    public String getStateMessage() { return stateMessage; }
    public boolean isSessionStarted() { return sessionStarted; }

    public String getDisplayLabel() {
        return user + "@" + host + ":" + port;
    }

    /** Kicks off connect + StartSession on a background thread. */
    void connectAsync(int cols, int rows) {
        this.lastCols = cols;
        this.lastRows = rows;
        new Thread(() -> {
            try {
                client.connect(host, port, user, password);
                client.startSession(cols, rows);
                sessionStarted = true;
            } catch (Exception e) {
                Log.w(LOGTAG, "ssh connect failed: " + e.getMessage());
                setState(State.ERROR, e.getMessage() != null ? e.getMessage() : "connect failed");
            }
        }, "ssh-session-" + id).start();
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
            setState(State.CONNECTED, "");
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

        Info(SshSession s) {
            this.id = s.id;
            this.host = s.host;
            this.port = s.port;
            this.user = s.user;
            this.state = s.state;
            this.stateMessage = s.stateMessage;
        }
    }

    public Info snapshot() {
        return new Info(this);
    }

    static List<Info> snapshot(List<SshSession> sessions) {
        List<Info> out = new ArrayList<>(sessions.size());
        for (SshSession s : sessions) {
            out.add(s.snapshot());
        }
        return out;
    }
}
