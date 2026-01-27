package io.netbird.client;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;

public final class PlatformUtils {

    private PlatformUtils() {
    }

    public static boolean isAndroidTV(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (uiModeManager != null) {
            return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        }
        return false;
    }

    public static boolean isChromeOS(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature("org.chromium.arc")
                || pm.hasSystemFeature("org.chromium.arc.device_management");
    }

    public static boolean requiresDeviceCodeFlow(Context context) {
        return isAndroidTV(context) || isChromeOS(context);
    }
}

