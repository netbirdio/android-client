package io.netbird.client.ui.profile;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.netbird.client.tool.Profile;

/**
 * Remembers when each profile was last switched to, so the picker sheet can show
 * the most recently used ones first. The gomobile Profile has no such field, so
 * the timestamps live in SharedPreferences keyed by profile ID.
 *
 * <p>The stored keys are self-cleaning: {@link #sortByRecency(List)} drops every
 * entry whose profile is no longer in the list it is handed, so deleting a profile
 * needs no notification from the caller and the store can never outgrow the number
 * of profiles that actually exist.
 */
public class ProfileUsageTracker {

    private static final String PREFS = "profile_usage";

    private final SharedPreferences prefs;

    public ProfileUsageTracker(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void markUsed(String profileId) {
        if (profileId == null) {
            return;
        }
        prefs.edit().putLong(profileId, System.currentTimeMillis()).apply();
    }

    private long lastUsed(Profile profile) {
        return prefs.getLong(profile.getID(), 0L);
    }

    /**
     * Returns the profiles ordered most-recently-used first, with the active one
     * always at the top. Profiles never switched to via this client sort last but
     * keep their original relative order.
     *
     * <p>Also discards timestamps belonging to profiles that no longer exist.
     */
    public List<Profile> sortByRecency(List<Profile> profiles) {
        Set<String> liveIds = new HashSet<>();
        for (Profile profile : profiles) {
            liveIds.add(profile.getID());
        }
        pruneStaleEntries(liveIds);

        List<Profile> sorted = new ArrayList<>(profiles);
        Collections.sort(sorted, (a, b) -> {
            if (a.isActive() != b.isActive()) {
                return a.isActive() ? -1 : 1;
            }
            return Long.compare(lastUsed(b), lastUsed(a));
        });
        return sorted;
    }

    private void pruneStaleEntries(Set<String> liveIds) {
        SharedPreferences.Editor editor = null;
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (liveIds.contains(entry.getKey())) {
                continue;
            }
            // Only touch the file when there is something to remove.
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.remove(entry.getKey());
        }
        if (editor != null) {
            editor.apply();
        }
    }
}
