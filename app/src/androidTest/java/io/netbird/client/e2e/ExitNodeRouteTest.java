package io.netbird.client.e2e;

import io.netbird.client.MainActivity;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Exit-node route test — the Android port of the Robot
 * {@code client-tests.robot} case "Should use exit node route".
 *
 * <p>The client logs in with a setup key whose profile routes all traffic
 * through an exit node (the Robot suite's {@code EXIT_NODE_TEST_SETUP_KEY}).
 * With the tunnel up, a request to {@code https://api.ipify.org} must report
 * the exit node's public IP ({@code 3.121.38.77}) rather than the device's own
 * — proving egress goes through the exit node. This is a real HTTPS request
 * over the tunnel, the equivalent of the Robot {@code GET https://api.ipify.org}.
 *
 * <p>Unlike the other cases this needs a <b>separate</b> setup key (the
 * exit-node profile), injected as its own argument — mirroring the original's
 * distinct {@code EXIT_NODE_TEST_SETUP_KEY}:
 * <pre>
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.exitNodeSetupKey=&lt;UUID&gt;
 * </pre>
 *
 * <p>If {@code exitNodeSetupKey} is not provided the test fails fast on its own
 * assertion, so the rest of the suite is unaffected.
 */
@RunWith(AndroidJUnit4.class)
public class ExitNodeRouteTest {

    private static final String TAG = "NBExitNodeTest";

    /** Where to ask for our public egress IP. */
    private static final String EGRESS_CHECK_URL = "https://api.ipify.org";
    /** The exit node's public IP — egress must appear to come from here. */
    private static final String EXIT_NODE_PUBLIC_IP = "3.121.38.77";

    /**
     * Window for the UI to report Connected. Generous against the observed
     * few seconds; the slow part is the data plane settling afterwards, which
     * the 90s probe windows below absorb (Robot allows ~3 min for the whole
     * peer-ready sequence).
     */
    private static final long CONNECT_TIMEOUT_SEC = 20;
    /** Time budget for the exit-node route to take effect after connecting. */
    private static final long EGRESS_TIMEOUT_SEC = 90;
    private VpnTestHarness harness;

    @Before
    public void skipIfPreviousFailed() {
        FailFast.skipIfAborted();
    }

    @Test
    public void egressGoesThroughExitNode() throws Exception {
        MainActivity activity = E2eAppRule.activity();
        assertNotNull("MainActivity must be available", activity);
        harness = new VpnTestHarness(activity);
        harness.enableTouchVisualization();

        harness.grantVpnConsent();

        // Separate key from the basic tests, like the Robot EXIT_NODE_TEST_SETUP_KEY.
        SharedProfiles.exitNode(activity, harness.device());

        boolean connected = harness.connectAndAwait(CONNECT_TIMEOUT_SEC);
        if (!connected) {
            LoginFlow.dumpScreenshot(harness.device(), "vpn-connect-timeout");
        }
        assertTrue("VPN did not reach connected state within " + CONNECT_TIMEOUT_SEC + "s",
                connected);

        // Best-effort: log the route table for debugging (the egress IP below
        // is the real assertion). Mirrors the Robot route-table dump.
        Log.i(TAG, "ip route:\n" + harness.shell("ip route show table all"));

        // The real check: our public egress IP must be the exit node's.
        boolean viaExitNode = harness.waitForHttpBodyContains(
                EGRESS_CHECK_URL, EXIT_NODE_PUBLIC_IP, EGRESS_TIMEOUT_SEC);
        if (!viaExitNode) {
            LoginFlow.dumpScreenshot(harness.device(), "exit-node-egress-mismatch");
        }
        assertTrue("Egress IP from " + EGRESS_CHECK_URL + " was not the exit node's "
                + EXIT_NODE_PUBLIC_IP + " within " + EGRESS_TIMEOUT_SEC + "s", viaExitNode);

        Log.i(TAG, "Egress verified through exit node " + EXIT_NODE_PUBLIC_IP);
    }
}
