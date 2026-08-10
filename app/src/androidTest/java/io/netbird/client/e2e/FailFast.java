package io.netbird.client.e2e;

import androidx.test.internal.runner.listener.InstrumentationRunListener;

import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;

/**
 * Aborts the run after the first failure. The Gradle UTP layer ignores the
 * {@code failFast} runner argument, so we do it inside the test process: this
 * {@link org.junit.runner.notification.RunListener} (registered via the runner
 * {@code listener} argument) flips a flag on the first failure, and
 * {@link #skipIfAborted()} — called from each test's {@code @Before} — turns
 * every subsequent test into a skipped (assumption-failed) result.
 */
public final class FailFast extends InstrumentationRunListener {

    private static volatile boolean aborted = false;

    @Override
    public void testFailure(Failure failure) {
        aborted = true;
    }

    /** Skip (not fail) the current test if an earlier test already failed. */
    static void skipIfAborted() {
        if (aborted) {
            throw new AssumptionViolatedException("Skipped: a previous test already failed (fail-fast)");
        }
    }
}
