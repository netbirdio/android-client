package io.netbird.client.tool;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Java mirror of the UI enforcement snapshot returned by
 * {@code getRestrictionsJSON()} — the same JSON shape the desktop frontend and
 * the iOS client consume. Every MDM decision is made in Go; this type only
 * carries the rendered answer so a screen can hide or lock a control.
 *
 * Semantics:
 * <ul>
 *   <li>{@code mdm.managementURL} — the enforced value ("" = not managed)</li>
 *   <li>other {@code mdm.*} flags — true = the key is managed, lock the control</li>
 *   <li>{@code mdm.allowServerSSH}, {@code mdm.disableAdvancedView} — tri-state,
 *       null = not managed</li>
 *   <li>{@code features.*} — the enforced value of that gate</li>
 * </ul>
 *
 * The fields are read directly rather than through getters: this is a carrier
 * for a JSON shape defined elsewhere, and the names have to stay recognisable
 * against the Go struct and the Swift mirror.
 */
public final class MDMRestrictions {

    /**
     * The no-policy snapshot: nothing managed, nothing gated. Also the fallback
     * whenever the bridge cannot be read, so a failure leaves the app fully
     * usable instead of locking the user out of their own settings.
     */
    public static final MDMRestrictions EMPTY = new MDMRestrictions(null);

    public static final class Fields {
        public final String managementURL;
        public final boolean preSharedKey;
        public final boolean wireguardPort;
        public final boolean rosenpassEnabled;
        public final boolean rosenpassPermissive;
        public final boolean disableClientRoutes;
        public final boolean disableServerRoutes;
        /** Tri-state: null = not managed. */
        public final Boolean allowServerSSH;
        public final boolean disableAutoConnect;
        public final boolean disableAutostart;
        public final boolean blockInbound;
        public final boolean disableMetricsCollection;
        public final boolean splitTunnelMode;
        public final boolean splitTunnelApps;
        /**
         * Tri-state, like {@link #allowServerSSH}: null means the key is not
         * managed, and an explicit false means the section is allowed — only
         * true hides it.
         */
        public final Boolean disableAdvancedView;

        Fields(JSONObject json) {
            if (json == null) {
                json = new JSONObject();
            }
            managementURL = json.optString("managementURL", "");
            preSharedKey = json.optBoolean("preSharedKey", false);
            wireguardPort = json.optBoolean("wireguardPort", false);
            rosenpassEnabled = json.optBoolean("rosenpassEnabled", false);
            rosenpassPermissive = json.optBoolean("rosenpassPermissive", false);
            disableClientRoutes = json.optBoolean("disableClientRoutes", false);
            disableServerRoutes = json.optBoolean("disableServerRoutes", false);
            allowServerSSH = triState(json, "allowServerSSH");
            disableAutoConnect = json.optBoolean("disableAutoConnect", false);
            disableAutostart = json.optBoolean("disableAutostart", false);
            blockInbound = json.optBoolean("blockInbound", false);
            disableMetricsCollection = json.optBoolean("disableMetricsCollection", false);
            splitTunnelMode = json.optBoolean("splitTunnelMode", false);
            splitTunnelApps = json.optBoolean("splitTunnelApps", false);
            disableAdvancedView = triState(json, "disableAdvancedView");
        }

        /** True when a management URL is enforced by policy. */
        public boolean managesManagementURL() {
            return !managementURL.isEmpty();
        }

        /**
         * Whether the advanced section must be hidden. Folds the tri-state so
         * callers do not each have to decide what null means.
         */
        public boolean hidesAdvancedView() {
            return Boolean.TRUE.equals(disableAdvancedView);
        }

        /** True when the policy dictates which applications the tunnel carries. */
        public boolean managesSplitTunnel() {
            return splitTunnelMode || splitTunnelApps;
        }
    }

    public static final class Features {
        public final boolean disableProfiles;
        public final boolean disableNetworks;
        public final boolean disableUpdateSettings;

        Features(JSONObject json) {
            if (json == null) {
                json = new JSONObject();
            }
            disableProfiles = json.optBoolean("disableProfiles", false);
            disableNetworks = json.optBoolean("disableNetworks", false);
            disableUpdateSettings = json.optBoolean("disableUpdateSettings", false);
        }
    }

    public final Fields mdm;
    public final Features features;

    private MDMRestrictions(JSONObject json) {
        if (json == null) {
            json = new JSONObject();
        }
        mdm = new Fields(json.optJSONObject("mdm"));
        features = new Features(json.optJSONObject("features"));
    }

    /**
     * Parses a snapshot produced by {@code getRestrictionsJSON()}.
     *
     * Never throws, and reads key by key rather than requiring the whole shape:
     * a snapshot from a Go layer that has added or dropped a key still yields
     * the keys this build knows, and anything unreadable degrades to
     * {@link #EMPTY} so a malformed policy cannot brick the settings screens.
     */
    public static MDMRestrictions decode(String json) {
        if (json == null || json.isEmpty()) {
            return EMPTY;
        }
        try {
            return new MDMRestrictions(new JSONObject(json));
        } catch (Exception e) {
            return EMPTY;
        }
    }

    /**
     * Turns a rejected write into the keys the policy refused.
     *
     * Go wraps its MDM error with the offending keys — "fields managed by MDM
     * cannot be modified: [rosenpassEnabled]" — so name them instead of
     * discarding the half of the message that says which setting was refused.
     *
     * @return the keys, "" when the message names none, or null when the failure
     *         was not a policy refusal at all and the caller should report it as
     *         an ordinary error. The wording shown to the user lives in the
     *         string resources, not here.
     */
    public static String rejectedKeys(String reason) {
        if (reason == null || !reason.toLowerCase(Locale.ROOT).contains("managed by mdm")) {
            return null;
        }
        int separator = reason.lastIndexOf(": ");
        if (separator < 0) {
            return "";
        }
        String keys = reason.substring(separator + 2).trim();
        while (keys.startsWith("[")) {
            keys = keys.substring(1);
        }
        while (keys.endsWith("]")) {
            keys = keys.substring(0, keys.length() - 1);
        }
        return keys.trim();
    }

    private static Boolean triState(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        return json.optBoolean(key, false);
    }
}
