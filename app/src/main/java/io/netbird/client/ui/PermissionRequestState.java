package io.netbird.client.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

/**
 * Tracks which runtime permissions this app has already asked the user for
 * at least once. shouldShowRequestPermissionRationale() alone can't tell a
 * permanent denial (the system won't show its dialog again) apart from a
 * permission that's simply never been requested — both return false — so
 * this persisted history is what disambiguates the two, letting the grant
 * banners send a permanently-denied user to Settings instead of relaunching
 * a request that would silently no-op.
 */
public final class PermissionRequestState {
    private static final String PREFS_NAME = "netbird_permission_requests";

    private PermissionRequestState() {
    }

    public static boolean isPermanentlyDenied(Fragment fragment, String permission) {
        Context context = fragment.requireContext();
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        boolean askedBefore = prefs(context).getBoolean(permission, false);
        return askedBefore && !fragment.shouldShowRequestPermissionRationale(permission);
    }

    public static void markRequested(Context context, String permission) {
        prefs(context).edit().putBoolean(permission, true).apply();
    }

    /**
     * Launches the permission request, or opens the app's settings page
     * instead if it's already permanently denied (the request would
     * otherwise no-op with no visible dialog).
     */
    public static void performGrantAction(Fragment fragment, String permission, Runnable launchAction) {
        if (isPermanentlyDenied(fragment, permission)) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + fragment.requireContext().getPackageName()));
            fragment.startActivity(intent);
        } else {
            markRequested(fragment.requireContext(), permission);
            launchAction.run();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
