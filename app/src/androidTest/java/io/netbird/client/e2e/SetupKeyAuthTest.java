package io.netbird.client.e2e;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

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
 * <p>The profile this creates is the suite's shared plain-key profile
 * ({@link SharedProfiles}): every later plain-key test reuses it instead of
 * enrolling its own, and {@link E2eSuite} removes it after the whole run.
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

    @Before
    public void skipIfPreviousFailed() {
        FailFast.skipIfAborted();
    }

    @Test
    public void loginWithSetupKeyViaUi() throws Exception {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String profileName = SharedProfiles.plain(E2eAppRule.activity(), device);
        assertNotNull("shared profile must exist after login", profileName);
    }
}
