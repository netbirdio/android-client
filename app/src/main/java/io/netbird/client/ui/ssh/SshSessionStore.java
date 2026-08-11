package io.netbird.client.ui.ssh;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Persists the connection details of SSH sessions so the list survives a
 * restart. A live connection cannot outlive the process, so restored entries
 * come back closed and are reconnected on demand.
 *
 * <p>Stored per profile: an overlay IP means a different host under a
 * different profile, so one list must not leak into another.
 *
 * <p>Passwords are never written here: one is only ever held in memory for the
 * session that asked for it, and a restored entry prompts again.
 */
final class SshSessionStore {

    private static final String LOGTAG = "SshSessionStore";

    private static final String PREFS = "ssh_sessions";
    /** Subdirectory under filesDir holding one known-hosts file per profile. */
    private static final String KNOWN_HOSTS_DIR = "ssh_known_hosts";
    /** Key is this plus the profile ID; the suffix is what pruning matches on. */
    private static final String KEY_PREFIX = "sessions_by_profile.";
    /**
     * Not per profile: the login name belongs to whoever holds the phone, and
     * the same account is typically used whichever profile is active.
     */
    private static final String KEY_LAST_USER = "last_ssh_user";
    private static final int MAX_ENTRIES = 50;

    private static final String FIELD_ID = "id";
    private static final String FIELD_HOST = "host";
    private static final String FIELD_PORT = "port";
    private static final String FIELD_USER = "user";

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

    private final SharedPreferences prefs;
    private final File knownHostsDir;

    SshSessionStore(@NonNull Context context) {
        Context app = context.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        knownHostsDir = new File(app.getFilesDir(), KNOWN_HOSTS_DIR);
    }

    private static String key(String profileId) {
        return KEY_PREFIX + profileId;
    }

    /**
     * Absolute path of the per-profile known-hosts file, creating the parent
     * directory on demand. The host keys a user trusts under one profile must
     * not verify a host reached through another, since an overlay IP is a
     * different machine per profile.
     */
    String knownHostsPath(@NonNull String profileId) {
        if (!knownHostsDir.exists() && !knownHostsDir.mkdirs()) {
            Log.w(LOGTAG, "could not create known-hosts dir: " + knownHostsDir);
        }
        return new File(knownHostsDir, profileId).getAbsolutePath();
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
        String raw = prefs.getString(key(profileId), null);
        if (raw == null || raw.isEmpty()) {
            return entries;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                String host = o.optString(FIELD_HOST, "");
                if (host.isEmpty()) {
                    continue;
                }
                entries.add(new Entry(
                        o.optString(FIELD_ID, ""),
                        host,
                        o.optInt(FIELD_PORT, 22),
                        o.optString(FIELD_USER, "")));
            }
        } catch (JSONException e) {
            // A corrupt store is not worth failing over; start clean instead.
            return new ArrayList<>();
        }
        return entries;
    }

    void save(@NonNull String profileId, @NonNull List<SshSession> sessions) {
        JSONArray array = new JSONArray();
        // Newest last, so the oldest are the ones dropped over the cap.
        int start = Math.max(0, sessions.size() - MAX_ENTRIES);
        for (int i = start; i < sessions.size(); i++) {
            SshSession session = sessions.get(i);
            try {
                JSONObject o = new JSONObject();
                o.put(FIELD_ID, session.getId());
                o.put(FIELD_HOST, session.getHost());
                o.put(FIELD_PORT, session.getPort());
                o.put(FIELD_USER, session.getUser());
                array.put(o);
            } catch (JSONException e) {
                // Skip the one entry rather than losing the whole list.
            }
        }
        prefs.edit().putString(key(profileId), array.toString()).apply();
    }

    /**
     * Drops the lists of profiles that no longer exist. Called with the live
     * profile IDs, so a deleted profile leaves nothing behind even though
     * deletion happens elsewhere and sends no notification. The known-hosts
     * file goes too: its trusted keys would otherwise outlive the profile and
     * silently apply to a future profile that reused the same ID.
     */
    void pruneDeletedProfiles(@NonNull Set<String> liveProfileIds) {
        SharedPreferences.Editor editor = null;
        for (String storedKey : prefs.getAll().keySet()) {
            if (!storedKey.startsWith(KEY_PREFIX)) {
                continue;
            }
            if (liveProfileIds.contains(storedKey.substring(KEY_PREFIX.length()))) {
                continue;
            }
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.remove(storedKey);
        }
        if (editor != null) {
            editor.apply();
        }

        File[] knownHostsFiles = knownHostsDir.listFiles();
        if (knownHostsFiles != null) {
            for (File file : knownHostsFiles) {
                if (!liveProfileIds.contains(file.getName()) && !file.delete()) {
                    Log.w(LOGTAG, "could not delete known-hosts file: " + file);
                }
            }
        }
    }
}
