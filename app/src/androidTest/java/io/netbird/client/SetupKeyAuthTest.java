package io.netbird.client;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.io.File;

import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.netbird.client.ui.server.ChangeServerFragment;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Drives the "Change server" UI to authenticate against the default NetBird
 * management server with a setup key — exactly the flow a user would use, but
 * automated.
 *
 * <p>The setup key is read from an instrumentation runner argument so CI can
 * inject it as a secret without baking it into the APK:
 * <pre>
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.setupKey=&lt;UUID&gt;
 * </pre>
 *
 * <p>The test navigates straight to {@code nav_change_server} (skipping the
 * first-install teaser screen), fills the setup key and taps the
 * "Use NetBird" button, which uses the management URL hard-coded in the app
 * ({@code Preferences.defaultServer()}). Then it waits for the success dialog.
 */
@RunWith(AndroidJUnit4.class)
public class SetupKeyAuthTest {

    private static final String TAG = "NBSetupKeyAuthTest";
    private static final String PACKAGE = "io.netbird.client";
    private static final long UI_TIMEOUT_MS = 5_000;
    private static final long LOGIN_TIMEOUT_MS = 15_000;

    @SuppressWarnings("deprecation")
    @Rule
    public ActivityTestRule<MainActivity> activityRule =
            new ActivityTestRule<>(MainActivity.class, true, true);

    @Test
    public void loginWithSetupKeyViaUi() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        String setupKey = args.getString("setupKey");

        assertNotNull("setupKey instrumentation argument is required", setupKey);
        assertTrue("setupKey must not be blank", !setupKey.trim().isEmpty());

        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.waitForIdle();

        // Try the navigation up to 2 times — on first launch the MainActivity
        // pushes firstInstallFragment after onCreate, which can race with our
        // navigate() call from the test thread.
        UiObject2 setupKeyLabel = null;
        for (int attempt = 1; attempt <= 2 && setupKeyLabel == null; attempt++) {
            Log.i(TAG, "navigateToChangeServer attempt " + attempt);
            navigateToChangeServer();
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

        // Either the success dialog ("btn_close") shows up, or the form re-enables
        // itself with an error.
        long deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            UiObject2 closeBtn = device.findObject(By.res(PACKAGE, "btn_close"));
            if (closeBtn != null) {
                Log.i(TAG, "Setup-key login succeeded");
                closeBtn.click();
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
     * Skip the first-install teaser and jump straight to the "Change server" screen.
     * {@code hideAlert=true} suppresses the "are you sure?" warning dialog so this
     * is non-interactive.
     */
    private void navigateToChangeServer() throws InterruptedException {
        MainActivity activity = activityRule.getActivity();
        assertNotNull("MainActivity must be available", activity);

        activity.runOnUiThread(() -> {
            View host = activity.findViewById(R.id.nav_host_fragment_content_main);
            NavController nav = Navigation.findNavController(host);
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
     * {@code hideAlert=true}, because that arg is not currently honoured by
     * the fragment. Tap Yes to dismiss so we can interact with the form.
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
     * Take a screenshot via UiAutomator and write it into the test runner's
     * working dir (cwd is /data/local/tmp/io.netbird.client.test on most
     * devices, which `adb pull` can read).
     */
    private static void dumpScreenshot(UiDevice device, String name) {
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
