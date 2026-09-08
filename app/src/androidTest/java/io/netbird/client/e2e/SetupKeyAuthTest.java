package io.netbird.client.e2e;

import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Login smoke test: drives the profile editor to create a profile and
 * authenticate it against the default (NetBird Cloud) management server with a
 * setup key — exactly the flow a user would use, but automated.
 *
 * <p>This test stops at a successful login (the dialog dismissing itself). For
 * the end-to-end "can actually reach a peer" check (doc test case 1) see
 * {@link PeerConnectivityTest}. The shared login steps live in
 * {@link LoginFlow}.
 *
 * <p>The setup key is read from an instrumentation runner argument so CI can
 * inject it as a secret without baking it into the APK:
 * <pre>
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.setupKey=&lt;UUID&gt;
 * </pre>
 */
@RunWith(AndroidJUnit4.class)
public class SetupKeyAuthTest {

    private UiDevice device;
    private String profileName;

    @Before
    public void skipIfPreviousFailed() {
        FailFast.skipIfAborted();
    }

    @Test
    public void loginWithSetupKeyViaUi() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        String setupKey = args.getString("setupKey");

        assertNotNull("setupKey instrumentation argument is required", setupKey);
        assertTrue("setupKey must not be blank", !setupKey.trim().isEmpty());

        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        profileName = LoginFlow.createProfileAndLogin(
                E2eAppRule.activity(), device, "login", setupKey);
    }

    /**
     * Enrolling now creates a profile, so this test leaves one behind — remove
     * it like the connectivity tests do, to keep the account's peer list and the
     * device's profile list from growing with every run.
     */
    @After
    public void tearDown() throws Exception {
        if (profileName != null && device != null) {
            LoginFlow.removeProfile(E2eAppRule.activity(), device, profileName);
        }
    }
}
