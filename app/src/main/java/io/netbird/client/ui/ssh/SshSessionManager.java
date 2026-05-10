package io.netbird.client.ui.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.netbird.gomobile.android.SSHClient;
import io.netbird.gomobile.android.URLOpener;

/**
 * Application-scoped registry of active {@link SshSession}s. Surviving the
 * lifecycle of any single fragment, sessions are kept alive as long as the
 * netbird process is running. The manager exposes {@link LiveData} of session
 * snapshots for UI lists.
 */
public class SshSessionManager {

    private static final SshSessionManager INSTANCE = new SshSessionManager();

    public static SshSessionManager get() {
        return INSTANCE;
    }

    private final Map<String, SshSession> sessions = new LinkedHashMap<>();
    private final MutableLiveData<List<SshSession.Info>> sessionsLiveData =
            new MutableLiveData<>(Collections.emptyList());

    private SshSessionManager() {}

    public synchronized SshSession create(@NonNull SSHClient client,
                                          @NonNull String host,
                                          int port,
                                          @NonNull String user,
                                          @NonNull String password,
                                          @Nullable URLOpener urlOpener) {
        String id = UUID.randomUUID().toString();
        SshSession session = new SshSession(id, host, port, user, password, client, urlOpener);
        sessions.put(id, session);
        publish();
        session.attach(stateChangeRefresher);
        return session;
    }

    @Nullable
    public synchronized SshSession get(@NonNull String id) {
        return sessions.get(id);
    }

    public synchronized List<SshSession> all() {
        return new ArrayList<>(sessions.values());
    }

    public synchronized void close(@NonNull String id) {
        SshSession session = sessions.remove(id);
        if (session != null) {
            session.detach(stateChangeRefresher);
            session.close();
        }
        publish();
    }

    public synchronized void closeAll() {
        for (SshSession session : sessions.values()) {
            session.detach(stateChangeRefresher);
            session.close();
        }
        sessions.clear();
        publish();
    }

    public LiveData<List<SshSession.Info>> liveSessions() {
        return sessionsLiveData;
    }

    private void publish() {
        sessionsLiveData.postValue(SshSession.snapshot(new ArrayList<>(sessions.values())));
    }

    /** Listener attached to every session so state changes refresh the list. */
    private final SshSession.Listener stateChangeRefresher = new SshSession.Listener() {
        @Override public void onScrollback(byte[] data) {}
        @Override public void onData(byte[] data) {}
        @Override public void onStateChange(SshSession.State state, String message) {
            publish();
        }
    };
}
