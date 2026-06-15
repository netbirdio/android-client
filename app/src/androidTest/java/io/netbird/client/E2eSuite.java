package io.netbird.client;

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
 *   -Pandroid.testInstrumentationRunnerArguments.class=io.netbird.client.E2eSuite
 * </pre>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        SetupKeyAuthTest.class,
        PeerConnectivityTest.class,
        PortAclTest.class,
        DnsResolutionTest.class,
        ExitNodeRouteTest.class,
})
public class E2eSuite {
}
