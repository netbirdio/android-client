package io.netbird.client;

import android.app.UiModeManager;
import android.content.Context;
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
}

