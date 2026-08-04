package io.netbird.client.e2e;

import io.netbird.client.MainActivity;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import java.util.Collection;

/**
 * Provides the single shared {@link MainActivity} for the e2e suite WITHOUT a
 * JUnit rule: {@link #activity()} returns the currently-resumed MainActivity,
 * launching one if none is up. This survives the test framework finishing
 * activities between test classes (which made a suite-level ActivityTestRule /
 * ActivityScenario hand back a destroyed activity). Because the launch reuses
 * the existing task, the app is not torn down and recreated between cases.
 */
final class E2eAppRule {

    private static final long LAUNCH_TIMEOUT_MS = 10_000;

    private E2eAppRule() {
    }

    /** The running MainActivity, launched on demand if not already resumed. */
    static MainActivity activity() {
        MainActivity existing = resumedMainActivity();
        if (existing != null) {
            return existing;
        }

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();

        long deadline = System.currentTimeMillis() + LAUNCH_TIMEOUT_MS;
        MainActivity activity = resumedMainActivity();
        while (activity == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            activity = resumedMainActivity();
        }
        if (activity == null) {
            throw new IllegalStateException("MainActivity did not reach RESUMED within "
                    + (LAUNCH_TIMEOUT_MS / 1000) + "s");
        }
        return activity;
    }

    private static MainActivity resumedMainActivity() {
        MainActivity[] found = new MainActivity[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Collection<Activity> resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED);
            for (Activity a : resumed) {
                if (a instanceof MainActivity) {
                    found[0] = (MainActivity) a;
                    break;
                }
            }
        });
        return found[0];
    }
}
