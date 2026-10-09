package io.netbird.client.tool;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.netbird.gomobile.android.PolicyFetcher;

/**
 * Hands the Go MDM layer the configuration a device owner or EMM set for this
 * app.
 *
 * Android delivers managed configuration per app, into the app's own process,
 * so unlike iOS there is no second process to mirror it into: the service that
 * runs the engine reads the same bundle the UI does. No managed configuration
 * means an empty answer, and the client then behaves as if the feature did not
 * exist.
 *
 * One instance can serve every Go object that needs a policy source — the
 * bundle is re-read on each call, so a fetcher registered once keeps answering
 * with the current policy rather than the one that was live at registration.
 */
public class MDMPolicyFetcher implements PolicyFetcher {

    private static final String LOGTAG = "MDMPolicyFetcher";

    private final Context context;

    public MDMPolicyFetcher(Context context) {
        // The application context: this outlives any activity, and the Go side
        // keeps the fetcher for as long as the client lives.
        this.context = context.getApplicationContext();
    }

    @Override
    public String fetchJSON() {
        try {
            RestrictionsManager manager =
                    (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
            if (manager == null) {
                return "";
            }
            return ManagedConfiguration.encode(toMap(manager.getApplicationRestrictions()));
        } catch (RuntimeException e) {
            // Called from Go, sometimes on the engine's own goroutine: an
            // exception escaping here would take the process down over a policy
            // we could not read. An empty answer means the same as no MDM.
            Log.w(LOGTAG, "could not read the managed configuration", e);
            return "";
        }
    }

    /**
     * Flattens a restrictions bundle into a plain map, so the encoding rules can
     * be tested without an Android runtime.
     *
     * Nested bundles and bundle arrays are what the OS produces for the
     * {@code bundle} and {@code bundle_array} restriction types; they are
     * carried across as objects and arrays of objects even though no key uses
     * them yet, because dropping them silently would be the harder failure to
     * explain later.
     */
    @SuppressWarnings("deprecation") // Bundle.get: the typed getters cannot be
    // used without knowing each key's type, which is exactly what this does not
    // want to hardcode.
    static Map<String, Object> toMap(Bundle bundle) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (bundle == null) {
            return out;
        }
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if (value instanceof Bundle) {
                out.put(key, toMap((Bundle) value));
            } else if (value instanceof Parcelable[]) {
                out.put(key, toList((Parcelable[]) value));
            } else if (value != null) {
                out.put(key, value);
            }
        }
        return out;
    }

    private static List<Object> toList(Parcelable[] items) {
        List<Object> out = new ArrayList<>(items.length);
        for (Parcelable item : items) {
            if (item instanceof Bundle) {
                out.add(toMap((Bundle) item));
            }
        }
        return out;
    }
}
