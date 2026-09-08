package io.netbird.client.e2e;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Focused group: only the network transition scenarios (the matrix in
 * {@link NetworkTransitionTest} plus the exit-node variant), for CI runs that
 * iterate on connection switching without paying for the full {@link E2eSuite}.
 * The mobile-e2e workflow selects it via its suite dropdown, which maps to
 * <pre>
 *   -Pandroid.testInstrumentationRunnerArguments.class=io.netbird.client.e2e.NetworkTransitionSuite
 * </pre>
 *
 * <p>No suite-level setup is needed: both classes configure what they depend
 * on themselves (force relay ON, transports restored), so the group runs the
 * same standalone as inside the full suite. Order matters and mirrors
 * {@link E2eSuite}: {@link NetworkTransitionTest} goes last because its final
 * case is expected to fail until PR #243 merges, and the FailFast listener
 * would skip everything scheduled after that failure.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        ExitNodeNetworkTransitionTest.class,
        NetworkTransitionTest.class,
})
public class NetworkTransitionSuite {
}
