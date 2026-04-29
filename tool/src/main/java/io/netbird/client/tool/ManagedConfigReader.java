package io.netbird.client.tool;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;
import android.util.Log;

import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.ManagedConfig;

/**
 * Reads MDM-managed configuration from Android Enterprise managed configurations
 * (app restrictions). Configuration is pushed by EMM/MDM solutions such as
 * Microsoft Intune, VMware Workspace ONE, or Google Admin Console.
 *
 * <p>The key names match those defined in res/xml/app_restrictions.xml and the
 * Go SDK's ManagedConfig key constants.</p>
 */
public class ManagedConfigReader {

    private static final String TAG = "ManagedConfigReader";

    private ManagedConfigReader() {
        // utility class
    }

    /**
     * Reads managed configuration from RestrictionsManager and returns a populated
     * ManagedConfig instance. Returns null if no managed configuration is available
     * or the RestrictionsManager service is unavailable.
     *
     * @param context Android context
     * @return populated ManagedConfig, or null if no MDM config is present
     */
    public static ManagedConfig read(Context context) {
        RestrictionsManager restrictionsManager =
                (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager == null) {
            Log.d(TAG, "RestrictionsManager not available");
            return null;
        }

        Bundle restrictions = restrictionsManager.getApplicationRestrictions();
        if (restrictions == null || restrictions.isEmpty()) {
            Log.d(TAG, "No managed configuration found");
            return null;
        }

        ManagedConfig config = Android.newManagedConfig();

        String managementUrl = restrictions.getString(
                Android.getManagedConfigKeyManagementURL(), "");
        if (!managementUrl.isEmpty()) {
            config.setManagementURL(managementUrl);
            Log.i(TAG, "MDM: management URL configured");
        }

        String setupKey = restrictions.getString(
                Android.getManagedConfigKeySetupKey(), "");
        if (!setupKey.isEmpty()) {
            config.setSetupKey(setupKey);
            // Do not log the setup key value for security
            Log.i(TAG, "MDM: setup key configured");
        }

        String adminUrl = restrictions.getString(
                Android.getManagedConfigKeyAdminURL(), "");
        if (!adminUrl.isEmpty()) {
            config.setAdminURL(adminUrl);
            Log.i(TAG, "MDM: admin URL configured");
        }

        String preSharedKey = restrictions.getString(
                Android.getManagedConfigKeyPreSharedKey(), "");
        if (!preSharedKey.isEmpty()) {
            config.setPreSharedKey(preSharedKey);
            Log.i(TAG, "MDM: pre-shared key configured");
        }

        if (restrictions.containsKey(Android.getManagedConfigKeyRosenpassEnabled())) {
            boolean rosenpassEnabled = restrictions.getBoolean(
                    Android.getManagedConfigKeyRosenpassEnabled(), false);
            config.setRosenpassEnabled(rosenpassEnabled);
            Log.i(TAG, "MDM: Rosenpass enabled=" + rosenpassEnabled);
        }

        if (restrictions.containsKey(Android.getManagedConfigKeyRosenpassPermissive())) {
            boolean rosenpassPermissive = restrictions.getBoolean(
                    Android.getManagedConfigKeyRosenpassPermissive(), false);
            config.setRosenpassPermissive(rosenpassPermissive);
            Log.i(TAG, "MDM: Rosenpass permissive=" + rosenpassPermissive);
        }

        if (restrictions.containsKey(Android.getManagedConfigKeyDisableAutoConnect())) {
            boolean disableAutoConnect = restrictions.getBoolean(
                    Android.getManagedConfigKeyDisableAutoConnect(), false);
            config.setDisableAutoConnect(disableAutoConnect);
            Log.i(TAG, "MDM: disable auto-connect=" + disableAutoConnect);
        }

        if (!config.hasConfig()) {
            Log.d(TAG, "MDM restrictions present but no NetBird keys configured");
            return null;
        }

        Log.i(TAG, "MDM managed configuration loaded successfully");
        return config;
    }

    /**
     * Returns true if any MDM-managed configuration is available for this app.
     *
     * @param context Android context
     * @return true if managed config has values
     */
    public static boolean hasManagedConfig(Context context) {
        ManagedConfig config = read(context);
        return config != null && config.hasConfig();
    }
}
