package io.netbird.client.tool;

import android.content.Context;
import android.util.Log;

import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.Auth;
import io.netbird.gomobile.android.Preferences;

/**
 * The single place the app reaches the MDM layer.
 *
 * Two jobs. It hands the policy source to every Go object that makes a decision
 * from it — a client, a profile manager, a preferences instance, a login — so a
 * new call site cannot quietly end up without one and read unmanaged values. And
 * it holds the enforcement snapshot the screens render from, re-read when the app
 * comes back into view and when the OS reports the policy changed, rather than on
 * every question a layout asks.
 */
public final class MDMBridge {

    private static final String LOGTAG = "MDMBridge";

    private static volatile MDMPolicyFetcher fetcher;
    private static volatile MDMRestrictions cached;
    private static volatile String cachedToken = "";

    private MDMBridge() {
    }

    /** The process-wide policy source; the bundle behind it is re-read per call. */
    public static synchronized MDMPolicyFetcher fetcher(Context context) {
        if (fetcher == null) {
            fetcher = new MDMPolicyFetcher(context);
        }
        return fetcher;
    }

    /**
     * Opens the Go preferences for a config file with the policy source attached.
     * Use this instead of {@code Android.newPreferences} — a bare instance reads
     * and writes as though no policy existed, which would both show the user
     * unmanaged values and let a write past a managed key.
     */
    public static Preferences openPreferences(Context context, String configPath) {
        Preferences preferences = Android.newPreferences(configPath);
        preferences.setMDMPolicyFetcher(fetcher(context));
        return preferences;
    }

    /**
     * Builds an authenticator under the active policy. A managed management URL
     * wins over the one passed in, which is decided on the Go side.
     */
    public static Auth newAuth(Context context, String configPath, String managementUrl) throws Exception {
        return Android.newAuth(configPath, managementUrl, fetcher(context));
    }

    /**
     * The enforcement snapshot the screens render from. Cheap to call: the
     * snapshot is kept until something says it may have changed.
     */
    public static MDMRestrictions restrictions(Context context) {
        MDMRestrictions snapshot = cached;
        return snapshot != null ? snapshot : refresh(context);
    }

    /**
     * Re-reads the snapshot through the Go bridge.
     *
     * A throwaway preferences instance is used rather than a shared one because
     * getRestrictionsJSON() consults only the policy loader — it never reads or
     * writes the config file — so this is free of side effects on the profile.
     */
    public static MDMRestrictions refresh(Context context) {
        String json = read(context);
        cachedToken = json;
        cached = MDMRestrictions.decode(json);
        return cached;
    }

    /**
     * A value that changes exactly when the snapshot does.
     *
     * The screens lock and hide their controls in code, which only goes one way:
     * a screen already built cannot tell that a setting became editable again.
     * Comparing this against the value a screen was built from says when it has
     * to be built afresh, and covers a policy being withdrawn as well as applied.
     */
    public static String snapshotToken(Context context) {
        restrictions(context);
        return cachedToken;
    }

    /**
     * The applications the policy says the tunnel carries, or null when it does
     * not say. Read straight from the managed configuration rather than from the
     * snapshot, which reports that the keys are managed but not which packages
     * they name.
     */
    public static SplitTunnelConfig managedSplitTunnel(Context context) {
        return MDMSplitTunnel.fromManagedConfiguration(fetcher(context).fetchJSON());
    }

    private static String read(Context context) {
        try {
            String configPath = new ProfileManagerWrapper(context).getActiveConfigPath();
            return openPreferences(context, configPath).getRestrictionsJSON();
        } catch (Exception e) {
            // No active profile yet, or a bridge that could not answer. Reporting
            // "nothing is managed" leaves the app usable; claiming the opposite
            // would lock a user out of settings over a transient failure.
            Log.w(LOGTAG, "could not read the MDM restrictions", e);
            return "";
        }
    }
}
