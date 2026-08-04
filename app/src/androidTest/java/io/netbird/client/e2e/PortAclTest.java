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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ACL / allowed-ports test — the Android port of the Robot
 * {@code client-tests.robot} case "Should connect only to the allowed ports".
 *
 * <p>The {@code acltest.netbird.cloud} peer has an ACL that <b>blocks ICMP</b>
 * but <b>allows TCP port 80</b>. So, with the tunnel up, the original asserts:
 * <ul>
 *   <li>ping to the peer FAILS (ICMP dropped) — Robot {@code Should Not Be
 *       Equal As Numbers ${result.rc} 0} with a {@code 0 received} regex;</li>
 *   <li>a TCP connection to port 80 SUCCEEDS — Robot {@code Open Connection
 *       acltest.netbird.cloud port=80}.</li>
 * </ul>
 *
 * <p>This is the proof the ACL is actually enforced: the peer is reachable
 * (control plane connected, port 80 open) yet ICMP is filtered.
 *
 * <p>Reuses the same fresh-profile + login + connect flow as
 * {@link PeerConnectivityTest}. Only the setup key is injected:
 * <pre>
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.setupKey=&lt;UUID&gt;
 * </pre>
 */
@RunWith(AndroidJUnit4.class)
public class PortAclTest {

    private static final String TAG = "NBPortAclTest";

    /** Peer with an ICMP-blocking, port-80-allowing ACL. */
    private static final String PEER_FQDN = "acltest.netbird.cloud";
    private static final int ALLOWED_PORT = 80;

    /** Matches the Robot suite's peer-connected window (3 min). */
    private static final long CONNECT_TIMEOUT_SEC = 20;
    /**
     * Time budget for the allowed port to become reachable once the engine is
     * connected (peer + ACL need a moment to settle). Loosely mirrors the
     * Robot peer-ready/handshake windows.
     */
    private static final long PORT_TIMEOUT_SEC = 20;
    private static final int TCP_CONNECT_TIMEOUT_MS = 3000;
    /**
     * How long to keep trying ICMP before concluding it is blocked. The ACL
     * drops it, so this should always time out — kept short so a genuinely
     * blocked peer does not slow the test much, but long enough that a slow
     * first packet is not mistaken for a block (the port check above already
     * proved the peer is reachable, so a few seconds is plenty).
     */
    private static final long PING_BLOCKED_PROBE_SEC = 10;
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
    public void connectsOnlyToAllowedPorts() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        String setupKey = args.getString("setupKey");

        assertNotNull("setupKey instrumentation argument is required", setupKey);
        assertTrue("setupKey must not be blank", !setupKey.trim().isEmpty());

        MainActivity activity = E2eAppRule.activity();
        assertNotNull("MainActivity must be available", activity);
        harness = new VpnTestHarness(activity);
        harness.enableTouchVisualization();

        harness.grantVpnConsent();

        // Fresh profile + login, like the Robot suite's per-test InitNetBird.
        profileName = LoginFlow.createProfileAndLogin(
                activity, harness.device(), "port-acl", setupKey);

        boolean connected = harness.connectAndAwait(CONNECT_TIMEOUT_SEC);
        if (!connected) {
            LoginFlow.dumpScreenshot(harness.device(), "vpn-connect-timeout");
        }
        assertTrue("VPN did not reach connected state within " + CONNECT_TIMEOUT_SEC + "s",
                connected);

        // Allowed port: TCP 80 must be reachable. This also serves as the
        // "peer is ready" signal (the Robot suite uses ping for that, but here
        // ping is blocked, so we rely on the allowed port instead).
        boolean portOpen = waitForTcp(PEER_FQDN, ALLOWED_PORT, PORT_TIMEOUT_SEC);
        if (!portOpen) {
            LoginFlow.dumpScreenshot(harness.device(), "acl-port80-unreachable");
        }
        assertTrue("Allowed port " + ALLOWED_PORT + " on " + PEER_FQDN
                + " was not reachable within " + PORT_TIMEOUT_SEC + "s", portOpen);

        // Blocked protocol: ICMP must NOT get through (ACL drops it). The peer
        // is provably reachable (port 80 just connected), so any ping success
        // here would mean the ACL is not being enforced.
        boolean pingGotThrough = pingSucceedsWithin(PEER_FQDN, PING_BLOCKED_PROBE_SEC);
        if (pingGotThrough) {
            LoginFlow.dumpScreenshot(harness.device(), "acl-icmp-leaked");
        }
        assertFalse("ICMP to " + PEER_FQDN + " should be blocked by the ACL, but ping succeeded",
                pingGotThrough);

        Log.i(TAG, "ACL enforced: port " + ALLOWED_PORT + " open, ICMP blocked on " + PEER_FQDN);
    }

    /** Retry a TCP connect until it succeeds or the timeout elapses. */
    private boolean waitForTcp(String host, int port, long timeoutSec) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (timeoutSec * 1000L);
        while (System.currentTimeMillis() < deadline) {
            if (harness.tcpConnects(host, port, TCP_CONNECT_TIMEOUT_MS)) {
                return true;
            }
            Thread.sleep(3000);
        }
        return false;
    }

    /**
     * Probe ICMP for up to {@code timeoutSec}; returns true as soon as any ping
     * gets through. Used to assert ICMP is blocked, so a true result is a
     * failure for the caller.
     */
    private boolean pingSucceedsWithin(String host, long timeoutSec) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (timeoutSec * 1000L);
        while (System.currentTimeMillis() < deadline) {
            if (harness.pingOnce(host)) {
                return true;
            }
            Thread.sleep(2000);
        }
        return false;
    }
}
