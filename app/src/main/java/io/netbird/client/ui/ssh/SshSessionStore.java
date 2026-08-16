package io.netbird.client.ui.ssh;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.SSHSessionArray;
import io.netbird.gomobile.android.SSHSessionEntry;
import io.netbird.gomobile.android.SSHSessionStore;

/**
 * Persists the connection details of SSH sessions so the list survives a
 * restart. A live connection cannot outlive the process, so restored entries
 * come back closed and are reconnected on demand.
 *
 * <p>Stored per profile in the Go profile manager's preferences: an overlay IP
 * means a different host under a different profile, so one list must not leak
 * into another, and deleting a profile deletes its list with it.
 *
 * <p>Passwords are never written here: one is only ever held in memory for the
 * session that asked for it, and a restored entry prompts again.
 */
final class SshSessionStore {

    private static final String LOGTAG = "SshSessionStore";

    private static final String PREFS = "ssh_sessions";
    /**
     * Not per profile: the login name belongs to whoever holds the phone, and
     * the same account is typically used whichever profile is active.
     */
    private static final String KEY_LAST_USER = "last_ssh_user";

    /** A stored session, without any credential. */
    static final class Entry {
        final String id;
        final String host;
        final int port;
        final String user;

        Entry(String id, String host, int port, String user) {
            this.id = id;
            this.host = host;
            this.port = port;
            this.user = user;
        }
    }

    private final String configDir;

    SshSessionStore(@NonNull Context context) {
        configDir = context.getApplicationContext().getFilesDir().getPath();
    }

    /**
     * The NetBird config directory the per-profile preferences live under; the
     * session list and the known-hosts entries are both stored there by the Go
     * profile manager, keyed by profile ID.
     */
    String configDir() {
        return configDir;
    }

    /**
     * The login name last connected with, or an empty string on a fresh install.
     * Static because the connect dialog needs it before any store instance
     * exists, and it is not tied to a profile.
     */
    static String lastUser(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_USER, "");
    }

    /** Remembers a login name so the next connect dialog can offer it. */
    static void setLastUser(@NonNull Context context, @NonNull String user) {
        if (user.isEmpty()) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST_USER, user).apply();
    }

    List<Entry> load(@NonNull String profileId) {
        List<Entry> entries = new ArrayList<>();
        try {
            SSHSessionStore goStore = Android.newSSHSessionStore(configDir, profileId);
            SSHSessionArray array = goStore.load();
            for (int i = 0; i < array.length(); i++) {
                SSHSessionEntry e = array.get(i);
                if (e == null) {
                    continue;
                }
                entries.add(new Entry(e.getID(), e.getHost(), (int) e.getPort(), e.getUser()));
            }
        } catch (Exception e) {
            // A corrupt store is not worth failing over; start clean instead.
            Log.w(LOGTAG, "could not load stored SSH sessions", e);
            return new ArrayList<>();
        }
        return entries;
    }

    void save(@NonNull String profileId, @NonNull List<SshSession> sessions) {
        SSHSessionArray array = Android.newSSHSessionArray();
        for (SshSession session : sessions) {
            array.add(session.getId(), session.getHost(), session.getPort(), session.getUser());
        }
        try {
            SSHSessionStore goStore = Android.newSSHSessionStore(configDir, profileId);
            goStore.save(array);
        } catch (Exception e) {
            Log.w(LOGTAG, "could not persist SSH sessions", e);
        }
    }
}
