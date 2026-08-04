package io.netbird.client.e2e;

import io.netbird.client.MainActivity;

import android.os.Bundle;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end connectivity test — the Android port of the Robot
 * {@code client-tests.robot} "Should be able to connect to peer" cases.
 *
 * <p>Mirrors the Robot {@code Try Peer Connectivity} keyword. It creates a
 * fresh, isolated profile (via the Profiles UI — the Android equivalent of
 * {@code netbird profile add test-<random>}), logs in to the production
 * management server with a setup key (shared UI flow in {@link LoginFlow}),
 * brings the VPN up, then verifies the data plane by pinging a remote peer's
 * <b>FQDN</b> through the tunnel. The NetBird tunnel DNS resolves the name to
 * the peer's overlay IP. The remote peers are live, externally-running NetBird
 * containers; this test does not create or tear them down. The profile is
 * removed in teardown.
 *
 * <p>Two cases, exactly as in the Robot suite (peer FQDNs hard-coded as in the
 * original):
 * <ul>
 *   <li>{@link #connectsWithRelay()} — {@code pingtest.netbird.cloud} (case
 *       "with relay support")</li>
 *   <li>{@link #connectsWithoutRelay()} — {@code pingtest-pre-relay.netbird.cloud}
 *       (case "without relay support")</li>
 * </ul>
 *
 * <p>Only the setup key is injected:
 * <ul>
 *   <li>{@code setupKey} — NetBird setup key for the client under test
 *       (CI injects the {@code INSTRUMENTATION_NB_SETUP_KEY} secret)</li>
 * </ul>
 *
 * <pre>
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.setupKey=&lt;UUID&gt;
 * </pre>
 *
 * <p>Timeouts follow the Robot {@code Wait For Peer Ready} keyword: up to
 * ~3 minutes for the peer to come up and the tunnel to carry traffic.
 */
@RunWith(AndroidJUnit4.class)
public class PeerConnectivityTest {

    private static final String TAG = "NBPeerConnTest";

    /** Peer reachable with relay support (Robot "with relay support" case). */
    private static final String PEER_FQDN_RELAY = "pingtest.netbird.cloud";
    /** Peer reachable without relay (Robot "without relay support" case). */
    private static final String PEER_FQDN_NO_RELAY = "pingtest-pre-relay.netbird.cloud";

    /** Matches the Robot suite's peer-connected window (3 min). */
    private static final long CONNECT_TIMEOUT_SEC = 20;
    /** How long to keep retrying the ping once the engine reports connected. */
    private static final long PING_TIMEOUT_SEC = 20;

    private VpnTestHarness harness;
    private String profileName;

    @Before
    public void skipIfPreviousFailed() {
        FailFast.skipIfAborted();
    }

    @After
    public void tearDown() throws Exception {
        if (profileName != null && harness != null) {
            harness.disableTouchVisualization();
            LoginFlow.removeProfile(E2eAppRule.activity(), harness.device(), profileName);
        }
    }

    @Test
    public void connectsWithRelay() throws Exception {
        connectAndPing(PEER_FQDN_RELAY, "relay");
    }

    @Test
    public void connectsWithoutRelay() throws Exception {
        connectAndPing(PEER_FQDN_NO_RELAY, "no-relay");
    }

    /**
     * Shared body: fresh profile → login → connect → ping {@code peerFqdn}
     * through the tunnel. This is the Android equivalent of the Robot
     * {@code Try Peer Connectivity ${peer_name}} keyword. Force-relay is
     * disabled once for the whole suite (see {@link E2eSuite}).
     */
    private void connectAndPing(String peerFqdn, String scenario) throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        String setupKey = args.getString("setupKey");

        assertNotNull("setupKey instrumentation argument is required", setupKey);
        assertTrue("setupKey must not be blank", !setupKey.trim().isEmpty());

        MainActivity activity = E2eAppRule.activity();
        harness = new VpnTestHarness(activity);
        harness.enableTouchVisualization();

        harness.grantVpnConsent();

        // 1. Create a fresh, isolated profile (Android equivalent of the Robot
        // suite's `netbird profile add test-<random>`), then log in into it.
        profileName = LoginFlow.createAndSwitchToFreshProfile(activity, harness.device(), scenario);
        LoginFlow.loginWithSetupKey(activity, harness.device(), setupKey);

        // 2. Bring the VPN up and wait for the engine to report connected.
        boolean connected = harness.connectAndAwait(CONNECT_TIMEOUT_SEC);
        if (!connected) {
            LoginFlow.dumpScreenshot(harness.device(), "vpn-connect-timeout");
        }
        assertTrue("VPN did not reach connected state within " + CONNECT_TIMEOUT_SEC + "s",
                connected);

        // 3. Verify the data plane: the remote peer must be reachable over the
        // tunnel by its FQDN (the tunnel DNS resolves it to the overlay IP).
        boolean reachable = harness.waitForPing(peerFqdn, PING_TIMEOUT_SEC);
        if (!reachable) {
            LoginFlow.dumpScreenshot(harness.device(), "peer-ping-failed");
        }
        assertTrue("Peer " + peerFqdn + " was not reachable over the tunnel within "
                + PING_TIMEOUT_SEC + "s", reachable);

        Log.i(TAG, "Peer " + peerFqdn + " reachable over the NetBird tunnel");
    }
}
