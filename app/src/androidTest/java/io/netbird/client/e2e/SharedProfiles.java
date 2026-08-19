package io.netbird.client.e2e;

import io.netbird.client.MainActivity;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The suite's two shared profiles — one enrolled with the plain {@code
 * setupKey}, one with the {@code exitNodeSetupKey}. Each is created (via the
 * same UI flow a user drives, see {@link LoginFlow}) on first request and
 * reused by every later test; requesting one that exists but is not active
 * just switches to it. This replaces the old fresh-profile-per-test pattern,
 * which spent the create/enrol/remove UI round trip on every case and grew
 * the account's peer list with every run.
 *
 * <p>The active-profile bookkeeping lives here, not in the UI: only suite
 * code changes profiles, so a cached value is authoritative and the common
 * "already active" case costs no navigation at all.
 *
 * <p>{@link E2eSuite} removes both profiles once, after the whole suite. A
 * single test class run standalone leaves its shared profile behind — on the
 * throwaway CI emulator that is free, and a next standalone run creates a
 * fresh one.
 */
final class SharedProfiles {

    private static String plainProfile;
    private static String exitNodeProfile;
    private static String activeProfile;

    private SharedProfiles() {
    }

    /** The shared plain-key profile, created on first use, active on return. */
    static String plain(MainActivity activity, UiDevice device) throws Exception {
        String setupKey = requireArg("setupKey");
        if (plainProfile == null) {
            plainProfile = LoginFlow.createProfileAndLogin(activity, device, "shared", setupKey);
        } else if (!plainProfile.equals(activeProfile)) {
            LoginFlow.switchToProfile(activity, device, plainProfile);
        }
        activeProfile = plainProfile;
        return plainProfile;
    }

    /** The shared exit-node profile, created on first use, active on return. */
    static String exitNode(MainActivity activity, UiDevice device) throws Exception {
        String setupKey = requireArg("exitNodeSetupKey");
        if (exitNodeProfile == null) {
            exitNodeProfile = LoginFlow.createProfileAndLogin(
                    activity, device, "exit-node", setupKey);
        } else if (!exitNodeProfile.equals(activeProfile)) {
            LoginFlow.switchToProfile(activity, device, exitNodeProfile);
        }
        activeProfile = exitNodeProfile;
        return exitNodeProfile;
    }

    /** Remove whichever shared profiles were created. Called once from the suite. */
    static void removeAll(MainActivity activity, UiDevice device) throws Exception {
        if (plainProfile != null) {
            LoginFlow.removeProfile(activity, device, plainProfile);
            plainProfile = null;
        }
        if (exitNodeProfile != null) {
            LoginFlow.removeProfile(activity, device, exitNodeProfile);
            exitNodeProfile = null;
        }
        activeProfile = null;
    }

    private static String requireArg(String name) {
        String value = InstrumentationRegistry.getArguments().getString(name);
        assertNotNull(name + " instrumentation argument is required", value);
        assertTrue(name + " must not be blank", !value.trim().isEmpty());
        return value.trim();
    }
}
