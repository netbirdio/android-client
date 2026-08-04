package io.netbird.client.e2e;

import io.netbird.client.MainActivity;
import io.netbird.client.R;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.util.List;
import java.util.Random;

import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import io.netbird.client.ui.server.ChangeServerFragment;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Shared "log in with a setup key via the UI" flow, extracted so both
 * {@link SetupKeyAuthTest} (login smoke test) and {@link PeerConnectivityTest}
 * (doc test case 1 — connectivity to a peer) drive the exact same screens.
 *
 * <p>This mirrors what a user does by hand: open "Change server", type the
 * setup key, submit, and dismiss the success dialog. It targets the management
 * URL hard-coded in the app ({@code Preferences.defaultServer()}, the
 * production server) via the "Use NetBird" button.
 *
 * <p>The setup key comes from the {@code setupKey} instrumentation argument so
 * CI can inject it as a secret:
 * <pre>
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.setupKey=&lt;UUID&gt;
 * </pre>
 */
final class LoginFlow {

    private static final String TAG = "NBLoginFlow";
    static final String PACKAGE = "io.netbird.client";
    private static final String DEFAULT_PROFILE = "default";

    private static final long UI_TIMEOUT_MS = 5_000;
    private static final long LOGIN_TIMEOUT_MS = 15_000;

    private LoginFlow() {
    }

    /**
     * Drives the "Change server" UI to log in with {@code setupKey} against the
     * app's default (production) management server, then dismisses the success
     * dialog so the caller lands back on the Home screen.
     *
     * @param activity the running MainActivity (from the test's ActivityTestRule)
     * @param device   the shared UiDevice
     * @param setupKey the NetBird setup key (already trimmed/validated by caller)
     * @throws Exception if any expected view never appears or login does not
     *                   complete within {@link #LOGIN_TIMEOUT_MS}
     */
    static void loginWithSetupKey(MainActivity activity, UiDevice device, String setupKey)
            throws Exception {
        device.waitForIdle();

        // Try the navigation up to 2 times — on first launch the MainActivity
        // pushes firstInstallFragment after onCreate, which can race with our
        // navigate() call from the test thread.
        UiObject2 setupKeyLabel = null;
        for (int attempt = 1; attempt <= 2 && setupKeyLabel == null; attempt++) {
            Log.i(TAG, "navigateToChangeServer attempt " + attempt);
            navigateToChangeServer(activity);
            dismissConfirmChangeServerDialog(device);
            setupKeyLabel = device.wait(
                    Until.findObject(By.res(PACKAGE, "text_setup_key_label")), UI_TIMEOUT_MS);
        }
        if (setupKeyLabel == null) {
            dumpScreenshot(device, "navigation-failed");
            fail("text_setup_key_label not found after 2 navigation attempts");
        }
        setupKeyLabel.click();

        UiObject2 setupKeyField = device.wait(
                Until.findObject(By.res(PACKAGE, "edit_text_setup_key")), UI_TIMEOUT_MS);
        assertNotNull("edit_text_setup_key must be present", setupKeyField);
        setupKeyField.setText(setupKey.trim());

        // "Use NetBird" submits with the app's default management URL.
        UiObject2 submit = device.wait(
                Until.findObject(By.res(PACKAGE, "btn_use_netbird")), UI_TIMEOUT_MS);
        assertNotNull("btn_use_netbird must be present", submit);
        submit.click();

        // Either the success dialog ("btn_close") shows up, or the form
        // re-enables itself with an error.
        long deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            UiObject2 closeBtn = device.findObject(By.res(PACKAGE, "btn_close"));
            if (closeBtn != null) {
                Log.i(TAG, "Setup-key login succeeded");
                closeBtn.click();
                device.waitForIdle();
                return;
            }
            // If the "Change server" button is enabled again, the request came
            // back with an error.
            UiObject2 submitAgain = device.findObject(By.res(PACKAGE, "btn_change_server"));
            if (submitAgain != null && submitAgain.isEnabled()) {
                fail("Login failed: submit button re-enabled without success dialog");
            }
            Thread.sleep(200);
        }
        dumpScreenshot(device, "login-timeout");
        fail("Login did not complete within " + (LOGIN_TIMEOUT_MS / 1000) + "s");
    }

    /**
     * Skip the first-install teaser and jump straight to the "Change server"
     * screen. {@code hideAlert=true} suppresses the "are you sure?" warning
     * dialog so this is non-interactive.
     */
    private static void navigateToChangeServer(MainActivity activity) throws InterruptedException {
        assertNotNull("MainActivity must be available", activity);

        activity.runOnUiThread(() -> {
            NavController nav = Navigation.findNavController(activity, R.id.nav_host_fragment_content_main);
            Bundle bundle = new Bundle();
            bundle.putBoolean(ChangeServerFragment.HideAlertBundleArg, true);
            // Same nav options the FirstInstallFragment uses when the user taps
            // its "change_server" link, so we land in the same place.
            NavOptions opts = new NavOptions.Builder()
                    .setPopUpTo(R.id.firstInstallFragment, true)
                    .build();
            nav.navigate(R.id.nav_change_server, bundle, opts);
        });
        // Let the fragment transaction commit before UiAutomator looks for views.
        Thread.sleep(1500);
    }

    /**
     * The Change Server fragment shows a "this will erase the local config"
     * confirmation dialog whenever it opens — even when the caller passed
     * {@code hideAlert=true}, because that arg is not currently honoured by the
     * fragment. Tap Yes to dismiss so we can interact with the form.
     */
    private static void dismissConfirmChangeServerDialog(UiDevice device) {
        UiObject2 yes = device.wait(
                Until.findObject(By.res(PACKAGE, "btn_yes")), UI_TIMEOUT_MS);
        if (yes != null) {
            Log.i(TAG, "Dismissing change-server confirmation dialog");
            yes.click();
            device.waitForIdle();
        }
    }

    /**
     * Create a fresh, isolated profile through the Profiles UI and switch to
     * it, mirroring the Robot suite's {@code netbird profile add test-<random>}
     * step (which it runs from the CLI). Driving it through the UI keeps the
     * test on the same user-facing path the rest of the flow uses.
     *
     * @param scenario short label for the calling test, woven into the profile
     *                 name so it is identifiable in the UI / on the recording
     * @return the generated profile name (pass it to {@link #removeProfile} to
     *         clean up afterwards)
     */
    static String createAndSwitchToFreshProfile(MainActivity activity, UiDevice device, String scenario)
            throws Exception {
        String profileName = "e2e-" + sanitizeScenario(scenario) + "-" + randomLowercase(4);

        navigateTo(activity, R.id.nav_profiles);

        UiObject2 addBtn = device.wait(
                Until.findObject(By.res(PACKAGE, "btn_add_profile")), UI_TIMEOUT_MS);
        assertNotNull("btn_add_profile must be present", addBtn);
        addBtn.click();

        UiObject2 nameField = device.wait(
                Until.findObject(By.res(PACKAGE, "edit_text_dialog")), UI_TIMEOUT_MS);
        assertNotNull("edit_text_dialog must be present", nameField);
        nameField.setText(profileName);
        confirmDialog(device);

        // The new profile is added but not active yet — switch to it so the
        // subsequent login writes into this profile.
        UiObject2 switchBtn = rowAction(device, profileName, "btn_switch");
        if (switchBtn == null) {
            dumpScreenshot(device, "profile-switch-missing");
            fail("btn_switch not found for profile " + profileName);
        }
        switchBtn.click();
        confirmDialog(device);
        device.waitForIdle();

        Log.i(TAG, "Created and switched to profile " + profileName);
        return profileName;
    }

    /**
     * Remove a profile created by {@link #createAndSwitchToFreshProfile}. A
     * profile can't be removed while active, so we switch to the built-in
     * "default" profile first, then remove the test profile. We assert on the
     * profile UI: a missing switch/remove button fails the test — it's a real
     * UI defect, not something to swallow.
     */
    static void removeProfile(MainActivity activity, UiDevice device, String profileName)
            throws InterruptedException {
        navigateTo(activity, R.id.nav_profiles);

        UiObject2 switchBtn = rowAction(device, DEFAULT_PROFILE, "btn_switch");
        if (switchBtn == null) {
            dumpScreenshot(device, "default-switch-missing");
            fail("btn_switch not found for the " + DEFAULT_PROFILE + " profile");
        }
        switchBtn.click();
        confirmDialog(device);
        device.waitForIdle();

        // Switching profiles bounces back to Home, so return to the profiles
        // screen before looking for the test profile's remove button.
        navigateTo(activity, R.id.nav_profiles);

        UiObject2 removeBtn = rowAction(device, profileName, "btn_remove");
        if (removeBtn == null) {
            dumpScreenshot(device, "remove-button-missing");
            fail("btn_remove not found for profile " + profileName);
        }
        removeBtn.click();
        confirmDialog(device);
        device.waitForIdle();
        Log.i(TAG, "Removed profile " + profileName);
    }

    /**
     * Set the "Force relay connection" toggle (a global setting) to {@code
     * enabled} via the Advanced screen. Each test sets the value it needs rather
     * than relying on the previous test's state, so the toggle is reset between
     * tests. Only clicks when the current state differs.
     */
    static void setForceRelay(MainActivity activity, UiDevice device, boolean enabled)
            throws InterruptedException {
        navigateTo(activity, R.id.nav_advanced);

        // The switch is far down the scrollable Advanced screen — scroll to it.
        scrollTo(device, "Force relay connection");

        UiObject2 toggle = rowControl(device, "Force relay connection", "switch_control");
        if (toggle == null) {
            dumpScreenshot(device, "force-relay-switch-missing");
            fail("Force relay connection switch not found in Advanced screen");
        }
        if (toggle.isChecked() != enabled) {
            toggle.click();
            // Toggling pops a "reconnection needed" warning; dismiss it.
            confirmDialog(device);
            Log.i(TAG, "Set force relay connection to " + enabled);
        } else {
            Log.i(TAG, "Force relay connection already " + enabled);
        }
    }

    /**
     * Find the action button with {@code resId} inside the profile row whose
     * {@code text_profile_name} matches {@code profileName}. Returns null if no
     * such row/button is visible.
     */
    private static UiObject2 rowAction(UiDevice device, String profileName, String resId) {
        return rowControl(device, "text_profile_name", profileName, resId, true);
    }

    /** Like {@link #rowAction} but the row is matched by visible text on any label. */
    private static UiObject2 rowControl(UiDevice device, String rowText, String resId) {
        return rowControl(device, null, rowText, resId, false);
    }

    private static UiObject2 rowControl(UiDevice device, String labelResId, String text,
                                        String resId, boolean labelByRes) {
        BySelector labelSel = labelByRes
                ? By.res(PACKAGE, labelResId).text(text)
                : By.text(text);
        UiObject2 label = device.wait(Until.findObject(labelSel), UI_TIMEOUT_MS);
        if (label == null) {
            return null;
        }
        // The control lives in the same row; walk up and search within it.
        UiObject2 row = label.getParent();
        BySelector sel = By.res(PACKAGE, resId);
        for (int i = 0; i < 5 && row != null; i++) {
            UiObject2 ctrl = row.findObject(sel);
            if (ctrl != null) {
                return ctrl;
            }
            row = row.getParent();
        }
        return null;
    }

    /** Scroll a scrollable screen until an element with {@code text} is visible. */
    private static void scrollTo(UiDevice device, String text) {
        try {
            UiScrollable scrollable = new UiScrollable(new UiSelector().scrollable(true));
            scrollable.scrollTextIntoView(text);
        } catch (Exception e) {
            Log.w(TAG, "scrollTo(" + text + ") failed: " + e.getMessage());
        }
        device.waitForIdle();
    }

    private static void confirmDialog(UiDevice device) {
        UiObject2 ok = device.wait(
                Until.findObject(By.res(PACKAGE, "btn_ok_dialog")), UI_TIMEOUT_MS);
        assertNotNull("btn_ok_dialog must be present", ok);
        ok.click();
        device.waitForIdle();
    }

    private static void navigateTo(MainActivity activity, int destId) throws InterruptedException {
        assertNotNull("MainActivity must be available", activity);
        activity.runOnUiThread(() -> {
            NavController nav = Navigation.findNavController(activity, R.id.nav_host_fragment_content_main);
            nav.navigate(destId);
        });
        Thread.sleep(1000);
    }

    private static String sanitizeScenario(String scenario) {
        String s = scenario.toLowerCase().replaceAll("[^a-z0-9-]+", "-").replaceAll("(^-+|-+$)", "");
        return s.isEmpty() ? "test" : s;
    }

    private static String randomLowercase(int len) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }

    /**
     * Take a screenshot via UiAutomator and write it under /sdcard/Pictures so
     * the CI workflow can {@code adb pull} it alongside the screen recording.
     */
    static void dumpScreenshot(UiDevice device, String name) {
        try {
            File png = new File("/sdcard/Pictures/" + name + ".png");
            //noinspection ResultOfMethodCallIgnored
            png.getParentFile().mkdirs();
            boolean ok = device.takeScreenshot(png);
            Log.i(TAG, "Screenshot " + (ok ? "saved to " : "FAILED for ") + png);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to dump screenshot: " + t.getMessage());
        }
    }
}
