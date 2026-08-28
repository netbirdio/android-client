package io.netbird.client.tool;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.PackageList;
import io.netbird.gomobile.android.SplitTunnelSettings;

/**
 * The split tunnelling selection of the active profile.
 *
 * The settings belong to a profile and are kept on the Go side with the rest of
 * a profile's preferences, so switching profile switches which applications the
 * tunnel carries. This class is only the translation layer: which packages end
 * up on the interface, and how, stays in {@link SplitTunnelConfig}.
 */
public class SplitTunnelStore {

    private static final String LOGTAG = "SplitTunnelStore";

    private final String configDir;
    private final ProfileManagerWrapper profileManager;

    public SplitTunnelStore(Context context) {
        this.configDir = context.getFilesDir().getPath();
        this.profileManager = new ProfileManagerWrapper(context);
    }

    /**
     * Reads the active profile's selection. A profile that has never stored one,
     * an unreadable store, or no active profile all mean the same thing to the
     * caller: carry every application.
     */
    public SplitTunnelConfig load() {
        try {
            SplitTunnelSettings settings = openStore().load();
            return new SplitTunnelConfig(
                    toMode(settings.getMode()),
                    toList(settings.getExcluded()),
                    toList(settings.getIncluded()));
        } catch (Exception e) {
            Log.w(LOGTAG, "could not read the split tunnelling settings", e);
            return new SplitTunnelConfig(SplitTunnelConfig.Mode.OFF, null, null);
        }
    }

    public void save(SplitTunnelConfig config) throws Exception {
        SplitTunnelSettings settings = Android.newSplitTunnelSettings();
        settings.setMode(toGoMode(config.getMode()));
        fill(settings.getExcluded(), config.getExcluded());
        fill(settings.getIncluded(), config.getIncluded());
        openStore().save(settings);
    }

    private io.netbird.gomobile.android.SplitTunnelStore openStore() throws Exception {
        Profile active = profileManager.getActiveProfile();
        if (active == null) {
            throw new IllegalStateException("no active profile");
        }
        return Android.newSplitTunnelStore(configDir, active.getID());
    }

    private static void fill(PackageList target, Iterable<String> packages) {
        for (String packageName : packages) {
            target.add(packageName);
        }
    }

    private static List<String> toList(PackageList list) {
        List<String> out = new ArrayList<>();
        if (list == null) {
            return out;
        }
        for (int i = 0; i < list.size(); i++) {
            out.add(list.get(i));
        }
        return out;
    }

    private static SplitTunnelConfig.Mode toMode(String goMode) {
        if (Android.SplitTunnelModeExclude.equals(goMode)) {
            return SplitTunnelConfig.Mode.EXCLUDE;
        }
        if (Android.SplitTunnelModeInclude.equals(goMode)) {
            return SplitTunnelConfig.Mode.INCLUDE;
        }
        return SplitTunnelConfig.Mode.OFF;
    }

    private static String toGoMode(SplitTunnelConfig.Mode mode) {
        switch (mode) {
            case EXCLUDE:
                return Android.SplitTunnelModeExclude;
            case INCLUDE:
                return Android.SplitTunnelModeInclude;
            default:
                return Android.SplitTunnelModeOff;
        }
    }
}
