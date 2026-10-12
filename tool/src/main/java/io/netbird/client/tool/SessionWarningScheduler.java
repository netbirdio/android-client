package io.netbird.client.tool;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Schedules the session-expiry warnings as WorkManager jobs anchored to the
 * wall clock, so they fire at the right moment even after the device slept
 * or the process was killed. One unique job per lead; a new deadline
 * replaces both.
 */
final class SessionWarningScheduler {
    static final long WARNING_LEAD_MINUTES = 10;
    static final long FINAL_WARNING_LEAD_MINUTES = 2;

    static final String INPUT_DEADLINE = "deadline";
    static final String INPUT_LEAD_MINUTES = "lead-minutes";
    static final String INPUT_PROFILE = "profile";

    private static final String WORK_NAME_PREFIX = "session-warning-";
    private static final String PREFS_NAME = "session-warning";
    private static final String PREF_DEADLINE = "deadline";
    private static final String PREF_PROFILE = "profile";
    private static final String PREF_FIRED_PREFIX = "fired-";

    private SessionWarningScheduler() {
    }

    static void schedule(Context context, long deadlineUnixSeconds, String profileId) {
        long nowSeconds = nowSeconds();
        if (deadlineUnixSeconds <= nowSeconds) {
            cancelWork(context);
            return;
        }
        if (isCurrent(context, deadlineUnixSeconds, profileId)) {
            return;
        }

        new SessionNotification(context).cancelExpiring();
        prefs(context).edit()
                .putLong(PREF_DEADLINE, deadlineUnixSeconds)
                .putString(PREF_PROFILE, profileId)
                .apply();

        WorkManager workManager = WorkManager.getInstance(context);
        enqueue(workManager, deadlineUnixSeconds, profileId, WARNING_LEAD_MINUTES, nowSeconds);
        enqueue(workManager, deadlineUnixSeconds, profileId, FINAL_WARNING_LEAD_MINUTES, nowSeconds);
    }

    static void cancelWork(Context context) {
        prefs(context).edit()
                .remove(PREF_DEADLINE)
                .remove(PREF_PROFILE)
                .apply();
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelUniqueWork(workName(WARNING_LEAD_MINUTES));
        workManager.cancelUniqueWork(workName(FINAL_WARNING_LEAD_MINUTES));
    }

    static void cancelAll(Context context) {
        cancelWork(context);
        new SessionNotification(context).cancelExpiring();
    }

    static boolean isCurrent(Context context, long deadlineUnixSeconds, String profileId) {
        SharedPreferences prefs = prefs(context);
        return prefs.getLong(PREF_DEADLINE, 0) == deadlineUnixSeconds
                && profileId != null
                && profileId.equals(prefs.getString(PREF_PROFILE, null));
    }

    static boolean wasFired(Context context, long leadMinutes, long deadlineUnixSeconds) {
        return prefs(context).getLong(PREF_FIRED_PREFIX + leadMinutes, 0) == deadlineUnixSeconds;
    }

    static void markFired(Context context, long leadMinutes, long deadlineUnixSeconds) {
        prefs(context).edit()
                .putLong(PREF_FIRED_PREFIX + leadMinutes, deadlineUnixSeconds)
                .apply();
    }

    static String workName(long leadMinutes) {
        return WORK_NAME_PREFIX + leadMinutes;
    }

    private static void enqueue(WorkManager workManager, long deadlineUnixSeconds, String profileId,
                                long leadMinutes, long nowSeconds) {
        long delaySeconds = Math.max(0, deadlineUnixSeconds - leadMinutes * 60 - nowSeconds);
        Data input = new Data.Builder()
                .putLong(INPUT_DEADLINE, deadlineUnixSeconds)
                .putLong(INPUT_LEAD_MINUTES, leadMinutes)
                .putString(INPUT_PROFILE, profileId)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SessionWarningWorker.class)
                .setInputData(input)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .build();
        workManager.enqueueUniqueWork(workName(leadMinutes), ExistingWorkPolicy.REPLACE, request);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }
}
