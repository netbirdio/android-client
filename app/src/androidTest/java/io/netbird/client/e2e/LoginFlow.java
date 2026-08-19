package io.netbird.client.e2e;

import io.netbird.client.MainActivity;
import io.netbird.client.R;

import android.util.Log;

import java.io.File;
import java.util.List;
import java.util.Random;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Shared "create a profile and log in with a setup key via the UI" flow, so
 * both {@link SetupKeyAuthTest} (login smoke test) and the connectivity tests
 * drive the exact same screens.
 *
 * <p>The iOS-style redesign folded what used to be three separate steps —
 * add profile, switch to it, then log in on a "Change server" screen — into a
 * single {@code ProfileEditorDialog}: the user names the profile, picks
 * NetBird Cloud (the default) or self-hosted, optionally flips on "add this
 * device with a setup key", and submits once. Creating the profile and
 * enrolling it happen in that one submit, so this class mirrors that: see
 * {@link #createProfileAndLogin}.
 *
 * <p>Two details of the new dialog matter for automation:
 * <ul>
 *   <li>The setup-key field starts hidden behind a switch (SSO is the
 *       recommended path), so it has to be revealed before it can be typed
 *       into.</li>
 *   <li>The whole {@code row_setup_key} is the touch target — the switch
 *       itself is not separately clickable — so the row is what we click.</li>
 * </ul>
 *
 * <p>Success is the dialog dismissing itself; failure surfaces as an error on
 * the setup-key field with the dialog still up. There is no longer a success
 * dialog to close.
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
    private static final long LOGIN_TIMEOUT_MS = 30_000;

    private LoginFlow() {
    }

    /**
     * Create a fresh, isolated profile and enrol it with {@code setupKey} in a
     * single pass through the profile editor, mirroring the Robot suite's
     * {@code netbird profile add test-<random>} plus setup-key login. The new
     * profile is created against NetBird Cloud (the dialog's default), which is
     * the production management server the suite targets.
     *
     * <p>Creating a profile makes it active, so there is no separate "switch to
     * it" step any more — on return the engine is configured to use it.
     *
     * @param scenario short label for the calling test, woven into the profile
     *                 name so it is identifiable in the UI / on the recording
     * @return the generated profile name (pass it to {@link #removeProfile} to
     *         clean up afterwards)
     */
    static String createProfileAndLogin(MainActivity activity, UiDevice device,
                                        String scenario, String setupKey) throws Exception {
        String profileName = "e2e-" + sanitizeScenario(scenario) + "-" + randomLowercase(4);

        openProfileEditor(activity, device);

        typeInto(device, "edit_text_profile_name", profileName);

        // Reveal the setup-key field: it is hidden behind a switch, and the row
        // (not the switch) carries the click listener. Typing the name opens the
        // soft keyboard, which shifts the dialog up, so the row is resolved
        // fresh here rather than reusing a handle from before the shift.
        UiObject2 setupKeyRow = device.wait(
                Until.findObject(By.res(PACKAGE, "row_setup_key")), UI_TIMEOUT_MS);
        assertNotNull("row_setup_key must be present", setupKeyRow);
        setupKeyRow.click();

        if (device.wait(Until.findObject(By.res(PACKAGE, "edit_text_setup_key")),
                UI_TIMEOUT_MS) == null) {
            dumpScreenshot(device, "setup-key-field-hidden");
            fail("edit_text_setup_key did not appear after toggling row_setup_key");
        }
        // Autofill can pre-populate this field with a stale UUID, so clear it
        // before typing rather than trusting it to be empty.
        typeInto(device, "edit_text_setup_key", setupKey.trim());

        // Submitting creates the profile and enrols it with the key in one go.
        UiObject2 submit = device.wait(
                Until.findObject(By.res(PACKAGE, "btn_ok_dialog")), UI_TIMEOUT_MS);
        assertNotNull("btn_ok_dialog must be present", submit);
        submit.click();

        awaitLoginResult(activity, device, profileName);

        // Creating a profile does not make it active: the editor dialog never
        // calls switchProfile, so the engine would still start from the legacy
        // netbird.cfg and try an interactive SSO login instead of using the
        // config the setup key just enrolled. Switch explicitly, the way the
        // Switch button does.
        switchToProfile(activity, device, profileName);

        Log.i(TAG, "Created and logged in profile " + profileName);
        return profileName;
    }

    /** Make {@code profileName} the active profile via its row's Switch button. */
    static void switchToProfile(MainActivity activity, UiDevice device, String profileName)
            throws InterruptedException {
        openProfiles(device);

        UiObject2 switchBtn = rowAction(device, profileName, "btn_switch");
        if (switchBtn == null) {
            dumpScreenshot(device, "switch-button-missing");
            dumpVisibleProfiles(device);
            fail("btn_switch not found for profile " + profileName);
        }
        if (!switchBtn.isEnabled()) {
            // Already the active profile — the button reads "Active" and is
            // disabled, so there is nothing to switch.
            Log.i(TAG, "Profile " + profileName + " is already active");
            return;
        }
        switchBtn.click();
        confirmDialog(device);
        device.waitForIdle();
        Log.i(TAG, "Switched to profile " + profileName);
    }

    /**
     * Set {@code text} on the field with {@code resId}, clearing whatever was
     * there first, and assert it stuck.
     *
     * <p>UiObject2 caches the bounds it was found at, so a node located before
     * the soft keyboard opened is clicked at stale coordinates afterwards — the
     * tap lands on a neighbouring view and the write silently goes nowhere.
     * Resolving the field immediately before use and reading it back turns that
     * class of failure into an explicit one.
     */
    private static void typeInto(UiDevice device, String resId, String text) {
        UiObject2 field = device.wait(
                Until.findObject(By.res(PACKAGE, resId)), UI_TIMEOUT_MS);
        if (field == null) {
            dumpScreenshot(device, resId + "-missing");
            fail(resId + " not found");
        }
        field.setText("");
        field.setText(text);
        device.waitForIdle();

        // Re-resolve: setText may itself have moved things around.
        UiObject2 check = device.findObject(By.res(PACKAGE, resId));
        String actual = check == null ? null : check.getText();
        if (actual == null || !actual.equals(text)) {
            dumpScreenshot(device, resId + "-not-set");
            fail("Failed to set " + resId + " — reads back as " + actual);
        }
    }

    /**
     * Wait for a successful enrolment. The dialog dismisses itself on success,
     * but "dialog gone" alone is too weak a signal — it also disappears on
     * paths that never registered the peer — so this additionally confirms the
     * profile is actually in the list afterwards.
     */
    private static void awaitLoginResult(MainActivity activity, UiDevice device,
                                        String profileName) throws InterruptedException {
        long deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS;
        boolean dismissed = false;
        while (System.currentTimeMillis() < deadline) {
            if (device.findObject(By.res(PACKAGE, "btn_ok_dialog")) == null) {
                dismissed = true;
                break;
            }
            Thread.sleep(200);
        }
        if (!dismissed) {
            dumpScreenshot(device, "login-timeout");
            dumpVisibleProfiles(device);
            fail("Login did not complete within " + (LOGIN_TIMEOUT_MS / 1000)
                    + "s — profile editor still open (setup key rejected?)");
        }
        device.waitForIdle();

        // A rolled-back profile leaves no row behind, so its presence is the
        // proof that creation plus enrolment both went through.
        openProfiles(device);
        if (rowAction(device, profileName, "btn_remove") == null) {
            dumpScreenshot(device, "login-profile-missing");
            dumpVisibleProfiles(device);
            fail("Profile " + profileName + " is not in the list after login —"
                    + " enrolment failed and the profile was rolled back");
        }
        Log.i(TAG, "Setup-key login succeeded (profile " + profileName + " present)");
    }

    /**
     * Open the "add profile" dialog by tapping through Settings → Profiles.
     */
    private static void openProfileEditor(MainActivity activity, UiDevice device)
            throws InterruptedException {
        dismissFirstInstall(activity, device);

        // Retry: the first-install fragment is torn down asynchronously and can
        // still be settling over the tabs. The editor's own name field — which no
        // other screen has — is what confirms we are really in the dialog.
        for (int attempt = 1; attempt <= 3; attempt++) {
            Log.i(TAG, "open profile editor attempt " + attempt);
            openProfiles(device);

            UiObject2 addBtn = device.wait(
                    Until.findObject(By.res(PACKAGE, "btn_add_profile")), UI_TIMEOUT_MS);
            if (addBtn == null) {
                continue;
            }
            addBtn.click();

            if (device.wait(Until.findObject(By.res(PACKAGE, "edit_text_profile_name")),
                    UI_TIMEOUT_MS) != null) {
                return;
            }
        }
        dumpScreenshot(device, "profile-editor-not-open");
        fail("profile editor (edit_text_profile_name) did not open after 3 attempts");
    }

    /**
     * Get past the first-run screen if it is up. It shares {@code row_setup_key}
     * and {@code switch_setup_key} with the profile editor but enrols the
     * already-active profile instead of creating a named one, so leaving it on
     * screen means the test types its setup key into the wrong form — the
     * profile it expects is never created.
     */
    private static void dismissFirstInstall(MainActivity activity, UiDevice device)
            throws InterruptedException {
        UiObject2 continueBtn = device.wait(
                Until.findObject(By.res(PACKAGE, "btn_continue")), UI_TIMEOUT_MS);
        if (continueBtn == null) {
            return;
        }
        Log.i(TAG, "first-install screen is up — continuing past it");
        continueBtn.click();

        // Continue tears the fragment down asynchronously, so wait for it to
        // actually go rather than assuming a fixed delay is enough.
        if (!device.wait(Until.gone(By.res(PACKAGE, "btn_continue")), UI_TIMEOUT_MS)) {
            dumpScreenshot(device, "first-install-stuck");
            fail("first-install screen did not close after tapping Continue");
        }
        device.waitForIdle();
        Log.i(TAG, "first-install screen dismissed");
    }

    /**
     * Remove a profile created by {@link #createProfileAndLogin}. A profile
     * can't be removed while active, so we switch to the built-in "default"
     * profile first, then remove the test profile. We assert on the profile UI:
     * a missing switch/remove button fails the test — it's a real UI defect,
     * not something to swallow.
     */
    static void removeProfile(MainActivity activity, UiDevice device, String profileName)
            throws InterruptedException {
        openProfiles(device);

        // The active profile shows a disabled "Active" chip where the inactive
        // ones show "Switch", so a missing button here means default is already
        // active — nothing to switch away from.
        UiObject2 switchBtn = rowAction(device, DEFAULT_PROFILE, "btn_switch");
        if (switchBtn != null && switchBtn.isEnabled()) {
            switchBtn.click();
            confirmDialog(device);
            device.waitForIdle();

            // Switching profiles bounces back to Home, so return to the
            // profiles screen before looking for the remove button.
            openProfiles(device);
        }

        UiObject2 removeBtn = rowAction(device, profileName, "btn_remove");
        if (removeBtn == null) {
            dumpScreenshot(device, "remove-button-missing");
            dumpVisibleProfiles(device);
            fail("btn_remove not found for profile " + profileName);
        }
        removeBtn.click();
        confirmDialog(device);
        device.waitForIdle();
        Log.i(TAG, "Removed profile " + profileName);
    }

    /**
     * Set the "Force relay connection" toggle (a global setting) to {@code
     * enabled} via the settings screen. Each test sets the value it needs rather
     * than relying on the previous test's state, so the toggle is reset between
     * tests. Only clicks when the current state differs.
     */
    static void setForceRelay(MainActivity activity, UiDevice device, boolean enabled)
            throws InterruptedException {
        // This runs from @BeforeClass, before any test builds a VpnTestHarness, so
        // touch feedback has to be turned on here too — otherwise the setup step's
        // taps are invisible on the recording, which is where debugging starts.
        new VpnTestHarness(activity).enableTouchVisualization();

        // This runs first in the suite, so on a fresh install the first-run
        // screen is still in front of the settings and would swallow the taps.
        dismissFirstInstall(activity, device);

        openAdvanced(device);

        // On a clean install the stored value differs from the switch's default,
        // so binding it in onCreateView fires the change listener and the
        // "reconnection needed" warning is already up before we touch anything —
        // it would swallow the row tap. On a re-run the values agree and no
        // dialog appears. Clear it either way.
        dismissDialogIfPresent(device);

        // The row sits below the fold, so it has to be scrolled in — and that is
        // also why a single click was flaky: scrollForward() flings, and a tap
        // injected while the list is still settling is consumed by the ScrollView
        // as "stop the fling" instead of being delivered as a click. (Clicks on
        // every other, non-scrolled screen worked; a manual tap on this exact
        // point seconds later worked too.) So: scroll, let the list settle,
        // click the row (it owns the click listener; the switch itself is
        // clickable="false" by design), and retry the whole unit until the
        // switch actually reports the requested state.
        boolean applied = false;
        for (int attempt = 1; attempt <= 3 && !applied; attempt++) {
            // Scroll by resource id rather than by label: the label is localised
            // into nine languages and would tie the suite to the device locale.
            scrollToRes(device, "switch_force_relay_connection");
            Thread.sleep(700); // let any residual fling settle before tapping

            UiObject2 toggle = device.wait(
                    Until.findObject(By.res(PACKAGE, "switch_force_relay_connection")),
                    UI_TIMEOUT_MS);
            if (toggle == null) {
                dumpScreenshot(device, "force-relay-switch-missing");
                fail("Force relay connection switch not found in settings screen");
            }
            if (toggle.isChecked() == enabled) {
                Log.i(TAG, "Force relay connection is " + enabled
                        + " (attempt " + attempt + ")");
                applied = true;
                break;
            }

            UiObject2 row = device.findObject(By.res(PACKAGE, "layout_force_relay_connection"));
            if (row == null) {
                dumpScreenshot(device, "force-relay-row-missing");
                fail("layout_force_relay_connection not found in settings screen");
            }
            Log.i(TAG, "clicking force relay row " + row.getVisibleBounds()
                    + " (attempt " + attempt + ")");
            row.click();
            device.waitForIdle();

            // Toggling pops a "reconnection needed" warning; acknowledge-only, and
            // absent when the tap was swallowed, so tolerate either.
            dismissDialogIfPresent(device);

            UiObject2 after = device.findObject(By.res(PACKAGE, "switch_force_relay_connection"));
            applied = after != null && after.isChecked() == enabled;
        }
        if (!applied) {
            dumpScreenshot(device, "force-relay-not-applied");
            fail("Force relay connection did not become " + enabled);
        }
        Log.i(TAG, "Set force relay connection to " + enabled);
    }

    /**
     * Tap a bottom-navigation tab and wait for its screen to come up.
     *
     * <p>Navigating by tapping rather than through NavController keeps the app in
     * the state a user would put it in: a programmatic navigate() can land on a
     * destination without the tab selection, back stack and fragment lifecycle
     * that the real path produces, which is exactly where UI automation starts
     * seeing views it cannot interact with.
     */
    private static void tapTab(UiDevice device, String tabResId, String expectedResId)
            throws InterruptedException {
        UiObject2 tab = device.wait(Until.findObject(By.res(PACKAGE, tabResId)), UI_TIMEOUT_MS);
        if (tab == null) {
            dumpScreenshot(device, tabResId + "-missing");
            fail(tabResId + " tab not found in bottom navigation");
        }
        tab.click();
        if (device.wait(Until.findObject(By.res(PACKAGE, expectedResId)), UI_TIMEOUT_MS) == null) {
            dumpScreenshot(device, expectedResId + "-not-shown");
            fail(expectedResId + " did not appear after tapping " + tabResId);
        }
        device.waitForIdle();
    }

    /** Tap a row on the Settings screen and wait for {@code expectedResId}. */
    private static void tapSettingsRow(UiDevice device, String rowResId, String expectedResId)
            throws InterruptedException {
        scrollToRes(device, rowResId);
        UiObject2 row = device.wait(Until.findObject(By.res(PACKAGE, rowResId)), UI_TIMEOUT_MS);
        if (row == null) {
            dumpScreenshot(device, rowResId + "-missing");
            fail(rowResId + " not found on the settings screen");
        }
        row.click();
        if (device.wait(Until.findObject(By.res(PACKAGE, expectedResId)), UI_TIMEOUT_MS) == null) {
            dumpScreenshot(device, expectedResId + "-not-shown");
            fail(expectedResId + " did not appear after tapping " + rowResId);
        }
        device.waitForIdle();
    }

    /** Open Settings → Advanced by tapping, the way a user would. */
    private static void openAdvanced(UiDevice device) throws InterruptedException {
        tapTab(device, "nav_settings", "row_advanced");
        tapSettingsRow(device, "row_advanced", "scr_vw_advanced");
    }

    /** Open Settings → Profiles by tapping, the way a user would. */
    private static void openProfiles(UiDevice device) throws InterruptedException {
        tapTab(device, "nav_settings", "row_profiles");
        tapSettingsRow(device, "row_profiles", "btn_add_profile");
    }

    /**
     * Find the action button with {@code resId} inside the profile row whose
     * {@code text_profile_name} matches {@code profileName}. Returns null if no
     * such row/button is visible.
     */
    private static UiObject2 rowAction(UiDevice device, String profileName, String resId) {
        BySelector labelSel = By.res(PACKAGE, "text_profile_name").text(profileName);
        UiObject2 label = device.wait(Until.findObject(labelSel), UI_TIMEOUT_MS);
        if (label == null) {
            // An account can accumulate enough profiles to push the target off
            // screen, and UiAutomator only sees what is rendered — scroll it in.
            scrollTo(device, profileName);
            label = device.wait(Until.findObject(labelSel), UI_TIMEOUT_MS);
        }
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

    /**
     * Log which profile rows UiAutomator can actually see, to tell "the row is
     * off screen" apart from "we are not on the profiles screen at all" when a
     * lookup fails.
     */
    private static void dumpVisibleProfiles(UiDevice device) {
        List<UiObject2> names = device.findObjects(By.res(PACKAGE, "text_profile_name"));
        Log.w(TAG, "visible profile rows: " + names.size());
        for (UiObject2 name : names) {
            Log.w(TAG, "  profile: " + name.getText());
        }
        Log.w(TAG, "scrollable containers: "
                + device.findObjects(By.scrollable(true)).size());
    }

    /**
     * Dismiss a confirmation dialog if one is up. Unlike {@link #confirmDialog}
     * this tolerates its absence, for the acknowledge-only warnings that carry no
     * decision: the "reconnection needed" notice appears when a stored setting
     * differs from the switch default (a clean install) and not when they agree
     * (a re-run), so both outcomes are normal.
     */
    private static void dismissDialogIfPresent(UiDevice device) {
        // Short wait: absence is an expected outcome here, so this must not cost
        // the full UI timeout on every call.
        UiObject2 ok = device.wait(
                Until.findObject(By.res(PACKAGE, "btn_ok_dialog")), 1_500);
        if (ok == null) {
            Log.i(TAG, "no confirmation dialog to dismiss");
            return;
        }
        ok.click();
        device.waitForIdle();
        Log.i(TAG, "dismissed a confirmation dialog");
    }

    /** Confirm a dialog that must be present, e.g. a switch/remove confirmation. */
    private static void confirmDialog(UiDevice device) {
        UiObject2 ok = device.wait(
                Until.findObject(By.res(PACKAGE, "btn_ok_dialog")), UI_TIMEOUT_MS);
        if (ok == null) {
            dumpScreenshot(device, "confirm-dialog-missing");
        }
        assertNotNull("btn_ok_dialog must be present", ok);
        ok.click();
        device.waitForIdle();
    }

    /**
     * Scroll until the view with {@code resId} is on screen. Locale-independent,
     * unlike scrolling to a translated label.
     */
    private static void scrollToRes(UiDevice device, String resId) {
        BySelector target = By.res(PACKAGE, resId);
        try {
            UiScrollable scrollable = new UiScrollable(new UiSelector().scrollable(true));
            if (!scrollable.exists()) {
                return;
            }
            for (int i = 0; i < 8 && device.findObject(target) == null; i++) {
                if (!scrollable.scrollForward()) {
                    break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "scrollToRes(" + resId + ") failed: " + e.getMessage());
        }
        device.waitForIdle();
    }

    private static void scrollTo(UiDevice device, String text) {
        try {
            UiScrollable scrollable = new UiScrollable(new UiSelector().scrollable(true));
            if (scrollable.exists()) {
                scrollable.scrollTextIntoView(text);
            }
        } catch (Exception e) {
            Log.w(TAG, "scrollTo(" + text + ") failed: " + e.getMessage());
        }
        device.waitForIdle();
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
     * Take a screenshot via UiAutomator so failures can be inspected after the
     * fact — the CI workflow pulls these alongside the screen recording.
     *
     * <p>Written into the test app's own external files dir, not
     * {@code /sdcard/Pictures}: since scoped storage the instrumentation process
     * cannot create files there and every capture failed with EACCES, which is
     * exactly when a screenshot is most needed.
     */
    static void dumpScreenshot(UiDevice device, String name) {
        try {
            File dir = InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getExternalFilesDir("screenshots");
            if (dir == null) {
                Log.w(TAG, "No external files dir for screenshots");
                return;
            }
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File png = new File(dir, name + ".png");
            boolean ok = device.takeScreenshot(png);
            Log.i(TAG, "Screenshot " + (ok ? "saved to " : "FAILED for ") + png);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to dump screenshot: " + t.getMessage());
        }
    }
}
