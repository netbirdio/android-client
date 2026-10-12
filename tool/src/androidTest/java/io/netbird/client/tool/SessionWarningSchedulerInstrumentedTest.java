package io.netbird.client.tool;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class SessionWarningSchedulerInstrumentedTest {
    private static final String PROFILE = "profile-a";

    private final Executor executor = Executors.newSingleThreadExecutor();

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Before
    public void setUp() {
        Configuration config = new Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(getContext(), config);
        getContext().getSharedPreferences("session-warning", Context.MODE_PRIVATE).edit().clear().apply();
    }

    @After
    public void tearDown() {
        SessionWarningScheduler.cancelAll(getContext());
        getContext().getSharedPreferences("session-warning", Context.MODE_PRIVATE).edit().clear().apply();
    }

    @Test
    public void scheduleEnqueuesBothWarnings() throws Exception {
        long deadline = nowSeconds() + 3600;

        SessionWarningScheduler.schedule(getContext(), deadline, PROFILE);

        Assert.assertEquals(WorkInfo.State.ENQUEUED, stateOf(SessionWarningScheduler.WARNING_LEAD_MINUTES));
        Assert.assertEquals(WorkInfo.State.ENQUEUED, stateOf(SessionWarningScheduler.FINAL_WARNING_LEAD_MINUTES));
        Assert.assertTrue(SessionWarningScheduler.isCurrent(getContext(), deadline, PROFILE));
    }

    @Test
    public void zeroDeadlineCancelsWork() throws Exception {
        long deadline = nowSeconds() + 3600;
        SessionWarningScheduler.schedule(getContext(), deadline, PROFILE);

        SessionWarningScheduler.schedule(getContext(), 0, PROFILE);

        Assert.assertEquals(WorkInfo.State.CANCELLED, stateOf(SessionWarningScheduler.WARNING_LEAD_MINUTES));
        Assert.assertEquals(WorkInfo.State.CANCELLED, stateOf(SessionWarningScheduler.FINAL_WARNING_LEAD_MINUTES));
        Assert.assertFalse(SessionWarningScheduler.isCurrent(getContext(), deadline, PROFILE));
    }

    @Test
    public void newDeadlineReplacesCurrent() throws Exception {
        long first = nowSeconds() + 3600;
        long second = first + 1800;
        SessionWarningScheduler.schedule(getContext(), first, PROFILE);

        SessionWarningScheduler.schedule(getContext(), second, PROFILE);

        Assert.assertFalse(SessionWarningScheduler.isCurrent(getContext(), first, PROFILE));
        Assert.assertTrue(SessionWarningScheduler.isCurrent(getContext(), second, PROFILE));
        Assert.assertEquals(1, infosOf(SessionWarningScheduler.WARNING_LEAD_MINUTES).size());
        Assert.assertEquals(WorkInfo.State.ENQUEUED, stateOf(SessionWarningScheduler.WARNING_LEAD_MINUTES));
    }

    @Test
    public void workerFiresForCurrentDeadline() {
        long deadline = nowSeconds() + 3600;
        SessionWarningScheduler.schedule(getContext(), deadline, PROFILE);

        runWorker(deadline, SessionWarningScheduler.WARNING_LEAD_MINUTES, PROFILE);

        Assert.assertTrue(SessionWarningScheduler.wasFired(getContext(),
                SessionWarningScheduler.WARNING_LEAD_MINUTES, deadline));
    }

    @Test
    public void workerSkipsStaleDeadline() {
        long current = nowSeconds() + 3600;
        long stale = current - 600;
        SessionWarningScheduler.schedule(getContext(), current, PROFILE);

        runWorker(stale, SessionWarningScheduler.WARNING_LEAD_MINUTES, PROFILE);

        Assert.assertFalse(SessionWarningScheduler.wasFired(getContext(),
                SessionWarningScheduler.WARNING_LEAD_MINUTES, stale));
    }

    @Test
    public void workerSkipsOtherProfile() {
        long deadline = nowSeconds() + 3600;
        SessionWarningScheduler.schedule(getContext(), deadline, PROFILE);

        runWorker(deadline, SessionWarningScheduler.WARNING_LEAD_MINUTES, "profile-b");

        Assert.assertFalse(SessionWarningScheduler.wasFired(getContext(),
                SessionWarningScheduler.WARNING_LEAD_MINUTES, deadline));
    }

    @Test
    public void lateWarningYieldsToFinalWarning() {
        long deadline = nowSeconds() + 60;

        // Both leads are already past, so both jobs get a zero delay and the
        // SynchronousExecutor runs the real workers inside schedule().
        SessionWarningScheduler.schedule(getContext(), deadline, PROFILE);

        Assert.assertFalse(SessionWarningScheduler.wasFired(getContext(),
                SessionWarningScheduler.WARNING_LEAD_MINUTES, deadline));
        Assert.assertTrue(SessionWarningScheduler.wasFired(getContext(),
                SessionWarningScheduler.FINAL_WARNING_LEAD_MINUTES, deadline));
    }

    @Test
    public void cancelAllKeepsFiredMarks() throws Exception {
        long deadline = nowSeconds() + 3600;
        SessionWarningScheduler.schedule(getContext(), deadline, PROFILE);
        runWorker(deadline, SessionWarningScheduler.WARNING_LEAD_MINUTES, PROFILE);

        SessionWarningScheduler.cancelAll(getContext());

        Assert.assertEquals(WorkInfo.State.CANCELLED, stateOf(SessionWarningScheduler.WARNING_LEAD_MINUTES));
        Assert.assertFalse(SessionWarningScheduler.isCurrent(getContext(), deadline, PROFILE));
        Assert.assertTrue(SessionWarningScheduler.wasFired(getContext(),
                SessionWarningScheduler.WARNING_LEAD_MINUTES, deadline));
    }

    private void runWorker(long deadline, long leadMinutes, String profileId) {
        Data input = new Data.Builder()
                .putLong(SessionWarningScheduler.INPUT_DEADLINE, deadline)
                .putLong(SessionWarningScheduler.INPUT_LEAD_MINUTES, leadMinutes)
                .putString(SessionWarningScheduler.INPUT_PROFILE, profileId)
                .build();
        SessionWarningWorker worker = TestWorkerBuilder.from(getContext(), SessionWarningWorker.class, executor)
                .setInputData(input)
                .build();
        ListenableWorker.Result result = worker.doWork();
        Assert.assertEquals(ListenableWorker.Result.success(), result);
    }

    private static List<WorkInfo> infosOf(long leadMinutes) throws Exception {
        return WorkManager.getInstance(getContext())
                .getWorkInfosForUniqueWork(SessionWarningScheduler.workName(leadMinutes))
                .get();
    }

    private static WorkInfo.State stateOf(long leadMinutes) throws Exception {
        List<WorkInfo> infos = infosOf(leadMinutes);
        Assert.assertEquals(1, infos.size());
        return infos.get(0).getState();
    }

    private static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }
}
