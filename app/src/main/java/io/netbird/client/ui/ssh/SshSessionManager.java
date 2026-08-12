package io.netbird.client.ui.ssh;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.SSHClient;
import io.netbird.gomobile.android.URLOpener;

/**
 * Application-scoped registry of {@link SshSession}s, outliving any single
 * fragment. Connection details are persisted, so the list survives a restart;
 * the connections themselves cannot, and come back closed for a reconnect.
 * Exposes {@link LiveData} of session snapshots for UI lists.
 */
public class SshSessionManager {

    private static final String LOGTAG = "SshSessionManager";

    private static final SshSessionManager INSTANCE = new SshSessionManager();

    public static SshSessionManager get() {
        return INSTANCE;
    }

    private final Map<String, SshSession> sessions = new LinkedHashMap<>();
    private final MutableLiveData<List<SshSession.Info>> sessionsLiveData =
            new MutableLiveData<>(Collections.emptyList());

    private SshSessionStore store;
    private ClientFactory clientFactory;
    private String activeProfileId;

    /** Supplies a fresh gomobile client, which only the bound service can create. */
    public interface ClientFactory {
        @Nullable SSHClient newClient();
        @Nullable URLOpener urlOpener();

        /** Whether the engine is up, so a client would have a tunnel to dial through. */
        boolean canConnect();
    }

    /**
     * Reports whether a connect attempt can get anywhere. An SSH session dials
     * through the tunnel, so without the engine every attempt fails, and the
     * caller can say so before opening a terminal that cannot work.
     */
    public synchronized boolean canConnect() {
        ClientFactory factory = clientFactory;
        return factory != null && factory.canConnect();
    }

    private SshSessionManager() {}

    /** Set while an activity is bound to the VPN service, cleared when it goes. */
    public synchronized void setClientFactory(@Nullable ClientFactory factory) {
        this.clientFactory = factory;
    }

    /**
     * Reconnects a session, attaching a client first if it lacks one, which is
     * the case for every entry restored from disk.
     */
    public synchronized boolean reconnect(@NonNull String id) {
        SshSession session = sessions.get(id);
        if (session == null) {
            return false;
        }
        // A session that already dialed keeps its client across an engine stop,
        // so the client alone says nothing about whether a redial can work.
        if (!canConnect()) {
            return false;
        }
        ClientFactory factory = clientFactory;
        if (factory == null) {
            return false;
        }
        if (!session.hasClient()) {
            SSHClient client = factory.newClient();
            if (client == null) {
                return false;
            }
            session.bindClient(client, factory.urlOpener());
        } else {
            // The opener belongs to the activity that created it, and a kept
            // client still points at the one from the previous connect. That
            // activity may be gone, leaving its result launcher unregistered, so
            // the JWT flow would report it is waiting for a browser that never
            // opens. Re-point the client at the current opener instead.
            session.setURLOpener(factory.urlOpener());
        }
        session.reconnect();
        return true;
    }

    /** Creates the store; the list itself arrives with {@link #setProfile}. */
    public synchronized void init(@NonNull Context context) {
        if (store == null) {
            store = new SshSessionStore(context);
        }
    }

    /**
     * Points the list at a profile, loading its stored sessions. Switching
     * closes whatever is live: the tunnel goes down with the old profile, and
     * an overlay IP means a different host under the new one.
     *
     * @param liveProfileIds every profile that still exists, so lists left by
     *                       deleted ones are discarded
     */
    public synchronized void setProfile(@Nullable String profileId,
                                        @NonNull Set<String> liveProfileIds) {
        if (store != null) {
            store.pruneDeletedProfiles(liveProfileIds);
        }
        if (profileId != null && profileId.equals(activeProfileId)) {
            return;
        }
        closeAllInternal();
        activeProfileId = profileId;
        if (store != null && profileId != null) {
            for (SshSessionStore.Entry entry : store.load(profileId)) {
                SshSession session = new SshSession(entry.id, entry.host, entry.port, entry.user);
                applyKnownHostsPath(session);
                sessions.put(entry.id, session);
                session.attach(stateChangeRefresher);
            }
        }
        publish();
    }

    /** Points a session's regular-server host-key checks at the active
     *  profile's store, so a trusted key never crosses profiles. */
    private void applyKnownHostsPath(@NonNull SshSession session) {
        if (store != null && activeProfileId != null) {
            session.setKnownHostsPath(store.knownHostsPath(activeProfileId));
        }
    }

    public synchronized SshSession create(@NonNull SSHClient client,
                                          @NonNull String host,
                                          int port,
                                          @NonNull String user,
                                          @NonNull String password,
                                          @Nullable URLOpener urlOpener) {
        String id = UUID.randomUUID().toString();
        SshSession session = new SshSession(id, host, port, user, password, client, urlOpener);
        applyKnownHostsPath(session);
        sessions.put(id, session);
        persist();
        publish();
        session.attach(stateChangeRefresher);
        return session;
    }

    /**
     * Opens a second session to the same target, connecting it right away.
     * The password is not carried over: it lives only in the session that was
     * asked for it, so a server wanting one prompts again.
     *
     * @return the new session, or null when NetBird is not running
     */
    @Nullable
    public synchronized SshSession duplicate(@NonNull String id) {
        SshSession source = sessions.get(id);
        ClientFactory factory = clientFactory;
        if (source == null || factory == null) {
            return null;
        }
        SSHClient client = factory.newClient();
        if (client == null) {
            return null;
        }
        SshSession copy = create(client, source.getHost(), source.getPort(), source.getUser(),
                "", factory.urlOpener());
        copy.connectAsync(source.getCols(), source.getRows());
        return copy;
    }

    /**
     * Changes where a stored session points. The details are final on a session
     * and a live connection belongs to the old target anyway, so the entry is
     * rebuilt: the old one is closed and replaced under the same id, which keeps
     * its place in the list and its stored entry rather than appending a second
     * one. The scrollback goes with it, since it came from a different host.
     *
     * <p>Left disconnected on purpose. Reconnecting here would dial before the
     * user has seen whether the new details are right, and the list already
     * offers a reconnect.
     *
     * @return false when the session is gone
     */
    public synchronized boolean edit(@NonNull String id, @NonNull String host, int port,
                                     @NonNull String user) {
        SshSession existing = sessions.get(id);
        if (existing == null) {
            return false;
        }
        existing.detach(stateChangeRefresher);
        existing.close();

        SshSession replacement = new SshSession(id, host, port, user);
        applyKnownHostsPath(replacement);
        sessions.put(id, replacement);
        replacement.attach(stateChangeRefresher);
        persist();
        publish();
        return true;
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
            forgetHostKeyIfUnused(session.getHost(), session.getPort());
        }
        persist();
        publish();
    }

    /**
     * Drops the host's trusted key once no session targets it anymore, so a
     * deleted host does not leave a trusted key behind. A host still used by
     * another session keeps its key, so that session is not re-prompted.
     */
    private void forgetHostKeyIfUnused(@NonNull String host, int port) {
        if (store == null || activeProfileId == null) {
            return;
        }
        for (SshSession other : sessions.values()) {
            if (other.getHost().equals(host) && other.getPort() == port) {
                return;
            }
        }
        try {
            Android.removeKnownHost(store.knownHostsPath(activeProfileId), host, port);
        } catch (Exception e) {
            Log.w(LOGTAG, "could not remove host key for " + host + ":" + port, e);
        }
    }

    public synchronized void closeAll() {
        closeAllInternal();
        persist();
        publish();
    }

    /** Tears the sessions down without persisting: callers decide what to store. */
    private void closeAllInternal() {
        for (SshSession session : sessions.values()) {
            session.detach(stateChangeRefresher);
            session.close();
        }
        sessions.clear();
    }

    public LiveData<List<SshSession.Info>> liveSessions() {
        return sessionsLiveData;
    }

    private void publish() {
        sessionsLiveData.postValue(SshSession.snapshot(new ArrayList<>(sessions.values())));
    }

    /** Called on add/remove only; a state change alters nothing that is stored. */
    private void persist() {
        if (store != null && activeProfileId != null) {
            store.save(activeProfileId, new ArrayList<>(sessions.values()));
        }
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
