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
 * DNS resolution test — the Android port of the Robot
 * {@code client-tests.robot} case "Should resolve the domain and hostname".
 *
 * <p>With the tunnel up, the {@code dnstest} peer's internal name must resolve
 * to its private address {@code 172.20.3.158} through the NetBird DNS. The
 * original runs {@code dig <name>}; here we run a real {@code nslookup} on the
 * device (via the shell, like the ping tests), so it exercises the device
 * resolver / VpnService DNS exactly as a user's traffic would.
 *
 * <p>Mirrors the two original assertions:
 * <ul>
 *   <li>FQDN resolves: {@code nslookup
 *       ip-172-20-3-158.eu-central-1.compute.internal} → 172.20.3.158;</li>
 *   <li>search-domain (unqualified) resolves: {@code nslookup ip-172-20-3-158}
 *       → 172.20.3.158.</li>
 * </ul>
 *
 * <p>The original additionally checks the main system resolver is a NetBird
 * {@code 100.x} address via {@code /etc/resolv.conf} / {@code resolvectl}. That
 * is Linux-specific and not portable to Android (no {@code resolv.conf} in the
 * usual sense; the VpnService owns DNS), so it is omitted — the resolution
 * results above already prove tunnel DNS is in effect.
 *
 * <p>Only the setup key is injected:
 * <pre>
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.setupKey=&lt;UUID&gt;
 * </pre>
 */
@RunWith(AndroidJUnit4.class)
public class DnsResolutionTest {

    private static final String TAG = "NBDnsTest";

    /** dnstest peer's internal hostname and its expected private address. */
    private static final String PEER_FQDN = "ip-172-20-3-158.eu-central-1.compute.internal";
    private static final String PEER_UNQUALIFIED = "ip-172-20-3-158";
    private static final String EXPECTED_IP = "172.20.3.158";

    /**
     * Window for the UI to report Connected. Generous against the observed
     * few seconds; the slow part is the data plane settling afterwards, which
     * the 90s probe windows below absorb (Robot allows ~3 min for the whole
     * peer-ready sequence).
     */
    private static final long CONNECT_TIMEOUT_SEC = 20;
    /** Time budget for DNS to start resolving once the engine is connected. */
    private static final long RESOLVE_TIMEOUT_SEC = 90;
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
    public void resolvesPeerNameThroughTunnel() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        String setupKey = args.getString("setupKey");

        assertNotNull("setupKey instrumentation argument is required", setupKey);
        assertTrue("setupKey must not be blank", !setupKey.trim().isEmpty());

        MainActivity activity = E2eAppRule.activity();
        assertNotNull("MainActivity must be available", activity);
        harness = new VpnTestHarness(activity);
        harness.enableTouchVisualization();

        harness.grantVpnConsent();

        profileName = LoginFlow.createProfileAndLogin(
                activity, harness.device(), "dns", setupKey);

        boolean connected = harness.connectAndAwait(CONNECT_TIMEOUT_SEC);
        if (!connected) {
            LoginFlow.dumpScreenshot(harness.device(), "vpn-connect-timeout");
        }
        assertTrue("VPN did not reach connected state within " + CONNECT_TIMEOUT_SEC + "s",
                connected);

        // FQDN resolves to the peer's private address through the tunnel DNS.
        boolean fqdnResolved = harness.waitForResolve(PEER_FQDN, EXPECTED_IP, RESOLVE_TIMEOUT_SEC);
        if (!fqdnResolved) {
            LoginFlow.dumpScreenshot(harness.device(), "dns-fqdn-unresolved");
        }
        assertTrue(PEER_FQDN + " did not resolve to " + EXPECTED_IP + " within "
                + RESOLVE_TIMEOUT_SEC + "s", fqdnResolved);

        // Search-domain: the unqualified name resolves to the same address.
        boolean searchResolved =
                harness.waitForResolve(PEER_UNQUALIFIED, EXPECTED_IP, RESOLVE_TIMEOUT_SEC);
        if (!searchResolved) {
            LoginFlow.dumpScreenshot(harness.device(), "dns-search-unresolved");
        }
        assertTrue(PEER_UNQUALIFIED + " (search domain) did not resolve to " + EXPECTED_IP
                + " within " + RESOLVE_TIMEOUT_SEC + "s", searchResolved);

        Log.i(TAG, "DNS resolution verified: " + PEER_FQDN + " and " + PEER_UNQUALIFIED
                + " -> " + EXPECTED_IP);
    }
}
