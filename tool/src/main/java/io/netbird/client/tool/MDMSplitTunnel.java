package io.netbird.client.tool;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The split tunnelling selection a policy imposes, if any.
 *
 * This is the one MDM key Android has and iOS does not: the platform lets a VPN
 * name the applications that stay out of the tunnel, or the only ones in it, so
 * an administrator can decide it instead of the user. The policy shape is the
 * desktop one — a mode discriminator plus a list of package names — mapped onto
 * the modes the Android client already has.
 *
 * Only the mapping lives here. Whether the keys are managed at all is read from
 * the Go snapshot ({@link MDMRestrictions.Fields#managesSplitTunnel()}), and how
 * the resolved packages reach the interface stays in {@link SplitTunnelConfig}.
 */
public final class MDMSplitTunnel {

    static final String KEY_MODE = "splitTunnelMode";
    static final String KEY_APPS = "splitTunnelApps";

    /** Only the named applications go through the tunnel. */
    static final String MODE_ALLOW = "allow";
    /** The named applications stay out of the tunnel; everything else goes in. */
    static final String MODE_DISALLOW = "disallow";

    private MDMSplitTunnel() {
    }

    /**
     * Reads the enforced selection out of the managed configuration — the same
     * JSON the Go loader is handed, so both sides act on one source.
     *
     * The values are read here rather than taken from the Go snapshot because
     * the snapshot reports only <em>that</em> the keys are managed, not which
     * applications they name: the desktop clients apply the list themselves and
     * have no interface to hand it across.
     *
     * @return null when the policy does not dictate the selection, in which case
     *         the user's own stored choice stands.
     */
    public static SplitTunnelConfig fromManagedConfiguration(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JSONObject policy = new JSONObject(json);
            return of(policy.optString(KEY_MODE, ""), packagesOf(policy.opt(KEY_APPS)));
        } catch (Exception e) {
            // Unreadable policy: the user's own selection stands, which is the
            // same answer as no policy at all.
            return null;
        }
    }

    /**
     * @return null for a mode this build does not know, so a value written for a
     *         newer client leaves the tunnel as the user configured it rather
     *         than silently taking some other mode's behaviour.
     */
    static SplitTunnelConfig of(String mode, List<String> packages) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (MODE_ALLOW.equals(normalized)) {
            return new SplitTunnelConfig(SplitTunnelConfig.Mode.INCLUDE, null, packages);
        }
        if (MODE_DISALLOW.equals(normalized)) {
            return new SplitTunnelConfig(SplitTunnelConfig.Mode.EXCLUDE, packages, null);
        }
        return null;
    }

    /**
     * Accepts both shapes an administrator can end up sending: the list of
     * strings an EMM console produces from a multi-select restriction, and the
     * comma-separated string the desktop registry and plist keys use.
     */
    static List<String> packagesOf(Object value) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<String> out = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                add(out, array.optString(i, ""));
            }
            return out;
        }
        if (value instanceof String) {
            List<String> out = new ArrayList<>();
            for (String item : ((String) value).split(",")) {
                add(out, item);
            }
            return out;
        }
        return Collections.emptyList();
    }

    private static void add(List<String> out, String packageName) {
        String trimmed = packageName == null ? "" : packageName.trim();
        if (!trimmed.isEmpty()) {
            out.add(trimmed);
        }
    }
}
