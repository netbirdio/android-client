package io.netbird.client.e2e;

import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Login smoke test: drives the "Change server" UI to authenticate against the
 * default (production) NetBird management server with a setup key — exactly the
 * flow a user would use, but automated.
 *
 * <p>This test stops at a successful login (the success dialog). For the
 * end-to-end "can actually reach a peer" check (doc test case 1) see
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

        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        LoginFlow.loginWithSetupKey(E2eAppRule.activity(), device, setupKey);
    }
}
