package io.netbird.client.e2e;

import io.netbird.client.MainActivity;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

/**
 * End-to-end connectivity test — the Android port of the Robot
 * {@code client-tests.robot} "Should be able to connect to peer" cases.
 *
 * <p>Mirrors the Robot {@code Try Peer Connectivity} keyword. It activates the
 * suite's shared plain-key profile ({@link SharedProfiles} — enrolled against
 * the production management server via the UI flow in {@link LoginFlow}),
 * brings the VPN up, then verifies the data plane by pinging a remote peer's
 * <b>FQDN</b> through the tunnel. The NetBird tunnel DNS resolves the name to
 * the peer's overlay IP. The remote peers are live, externally-running NetBird
 * containers; this test does not create or tear them down. The shared profile
 * is removed once, after the whole suite.
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

    /**
     * Window for the UI to report Connected. Generous against the observed
     * few seconds; the slow part is the data plane settling afterwards, which
     * the 90s probe windows below absorb (Robot allows ~3 min for the whole
     * peer-ready sequence).
     */
    private static final long CONNECT_TIMEOUT_SEC = 20;
    /** How long to keep retrying the ping once the engine reports connected. */
    private static final long PING_TIMEOUT_SEC = 90;

    private VpnTestHarness harness;

    @Before
    public void skipIfPreviousFailed() {
        FailFast.skipIfAborted();
    }

    @Test
    public void connectsWithRelay() throws Exception {
        connectAndPing(PEER_FQDN_RELAY);
    }

    @Test
    public void connectsWithoutRelay() throws Exception {
        connectAndPing(PEER_FQDN_NO_RELAY);
    }

    /**
     * Shared body: shared profile active → connect → ping {@code peerFqdn}
     * through the tunnel. This is the Android equivalent of the Robot
     * {@code Try Peer Connectivity ${peer_name}} keyword. Force-relay is
     * disabled once for the whole suite (see {@link E2eSuite}).
     */
    private void connectAndPing(String peerFqdn) throws Exception {
        MainActivity activity = E2eAppRule.activity();
        harness = new VpnTestHarness(activity);
        harness.enableTouchVisualization();

        harness.grantVpnConsent();

        // 1. Make sure the suite's shared plain-key profile exists and is the
        // active one (created here if this class runs standalone).
        SharedProfiles.plain(activity, harness.device());

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
