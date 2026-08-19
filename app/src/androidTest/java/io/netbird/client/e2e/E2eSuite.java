package io.netbird.client.e2e;

import io.netbird.client.MainActivity;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Runs the on-device client e2e tests (the Android port of the Robot
 * {@code client-tests.robot} suite). Tests obtain the shared {@link MainActivity}
 * via {@link E2eAppRule#activity()}, which reuses the running activity (launching
 * one only if none is up), so the app is not restarted between cases.
 *
 * <p>Run with:
 * <pre>
 *   -Pandroid.testInstrumentationRunnerArguments.class=io.netbird.client.e2e.E2eSuite
 * </pre>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        SetupKeyAuthTest.class,
        PeerConnectivityTest.class,
        PortAclTest.class,
        DnsResolutionTest.class,
        ExitNodeRouteTest.class,
        ExitNodeNetworkTransitionTest.class,
        // Last on purpose: its final case (B2, cellular->WiFi handover speed)
        // is expected to fail until PR #243 merges, and the FailFast listener
        // would skip everything scheduled after that failure.
        NetworkTransitionTest.class,
})
public class E2eSuite {

    /**
     * Force relay is a global setting that defaults ON and would stop the
     * relay-less peer connecting. Turn it off ONCE, before any test runs.
     */
    @BeforeClass
    public static void disableForceRelay() throws Exception {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        LoginFlow.setForceRelay(E2eAppRule.activity(), device, false);
    }

    /**
     * The tests share two profiles for the whole run (one per setup key, see
     * {@link SharedProfiles}), so cleanup happens once here instead of per test.
     */
    @AfterClass
    public static void removeSharedProfiles() throws Exception {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        SharedProfiles.removeAll(E2eAppRule.activity(), device);
        new VpnTestHarness(E2eAppRule.activity()).disableTouchVisualization();
    }
}
