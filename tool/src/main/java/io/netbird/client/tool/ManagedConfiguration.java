package io.netbird.client.tool;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/**
 * Encodes an OS-managed configuration as the JSON object string the Go MDM
 * loader reads.
 *
 * Deliberately free of Android types: the conversion rules are the part worth
 * testing on the JVM, away from a device. Reading the values out of
 * RestrictionsManager is {@link MDMPolicyFetcher}'s job.
 *
 * Every decision about what a key means stays in Go. This only carries the
 * values across, keeping the types the managed configuration was delivered in —
 * a boolean stays a boolean, a number stays a number — because the Go side
 * already accepts each of them for the keys it knows.
 */
public final class ManagedConfiguration {

    private ManagedConfiguration() {
    }

    /**
     * @return the values as a JSON object, or "" when there is nothing to carry.
     *         An empty answer is what the Go side reads as "no MDM source
     *         present", which is also the honest reading on Android: an app with
     *         no managed configuration and an app on an unmanaged device are
     *         handed the same empty bundle.
     */
    public static String encode(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        JSONObject encoded = objectOf(values);
        return encoded.length() == 0 ? "" : encoded.toString();
    }

    private static JSONObject objectOf(Map<?, ?> values) {
        JSONObject out = new JSONObject();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                continue;
            }
            String key = (String) entry.getKey();
            if (key.isEmpty()) {
                continue;
            }
            Object value = encodeValue(entry.getValue());
            if (value == null) {
                continue;
            }
            try {
                out.put(key, value);
            } catch (JSONException e) {
                // A value JSON cannot represent (a NaN, say). Dropping the one
                // key leaves the rest of the policy usable, which beats
                // rejecting the whole configuration over it.
            }
        }
        return out;
    }

    /**
     * @return the value in a form JSON can hold, or null for anything the
     *         managed configuration should not have contained in the first
     *         place. An unknown type is dropped rather than stringified: a
     *         "java.lang.Object@1f2e3d" reaching a policy key would read as a
     *         deliberate value on the Go side.
     */
    private static Object encodeValue(Object value) {
        if (value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte || value instanceof Double || value instanceof Float) {
            return value;
        }
        if (value instanceof Map) {
            return objectOf((Map<?, ?>) value);
        }
        if (value instanceof Iterable) {
            return arrayOf((Iterable<?>) value);
        }
        if (value instanceof Object[]) {
            return arrayOf(java.util.Arrays.asList((Object[]) value));
        }
        return null;
    }

    private static JSONArray arrayOf(Iterable<?> items) {
        JSONArray out = new JSONArray();
        for (Object item : items) {
            Object value = encodeValue(item);
            if (value != null) {
                out.put(value);
            }
        }
        return out;
    }
}
