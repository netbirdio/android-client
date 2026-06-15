package io.netbird.client.tool;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.netbird.gomobile.android.PolicyFetcher;

/**
 * MDMPolicyFetcher reads the current Android managed-config snapshot
 * from RestrictionsManager and returns it as a JSON-encoded string to
 * the Go layer. Registered on the goClient via setMDMPolicyFetcher
 * inside EngineRunner; the Go side invokes fetchJSON() on every
 * Loader.Load call so the response is always fresh.
 *
 * Returns an empty string when no managed config is set — the daemon
 * side treats that as the "no MDM source present" sentinel.
 *
 * Lives in the tool package so the network-extension target can
 * instantiate it without depending on the app package.
 */
public class MDMPolicyFetcher implements PolicyFetcher {
    private static final String TAG = "MDMPolicyFetcher";

    private final Context context;

    public MDMPolicyFetcher(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String fetchJSON() {
        RestrictionsManager rm = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        if (rm == null) {
            return "";
        }
        Bundle restrictions = rm.getApplicationRestrictions();
        if (restrictions == null || restrictions.isEmpty()) {
            return "";
        }
        try {
            return bundleToJSON(restrictions).toString();
        } catch (JSONException e) {
            Log.w(TAG, "Failed to serialize managed restrictions to JSON: " + e);
            return "";
        }
    }

    private static JSONObject bundleToJSON(Bundle bundle) throws JSONException {
        JSONObject obj = new JSONObject();
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if (value instanceof Bundle) {
                obj.put(key, bundleToJSON((Bundle) value));
            } else if (value instanceof Object[]) {
                JSONArray arr = new JSONArray();
                for (Object item : (Object[]) value) {
                    arr.put(item);
                }
                obj.put(key, arr);
            } else {
                obj.put(key, value);
            }
        }
        return obj;
    }
}
