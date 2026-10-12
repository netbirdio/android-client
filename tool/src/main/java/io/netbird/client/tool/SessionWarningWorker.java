package io.netbird.client.tool;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Posts one session-expiry warning when its scheduled moment arrives, after
 * re-checking on the wall clock that the deadline it was armed for is still
 * the current one and still ahead.
 */
public class SessionWarningWorker extends Worker {

    public SessionWarningWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data input = getInputData();
        long deadline = input.getLong(SessionWarningScheduler.INPUT_DEADLINE, 0);
        long leadMinutes = input.getLong(SessionWarningScheduler.INPUT_LEAD_MINUTES, 0);
        String profileId = input.getString(SessionWarningScheduler.INPUT_PROFILE);
        Context context = getApplicationContext();

        if (deadline <= 0 || profileId == null) {
            return Result.success();
        }
        if (!SessionWarningScheduler.isCurrent(context, deadline, profileId)) {
            return Result.success();
        }
        long remainingSeconds = deadline - System.currentTimeMillis() / 1000L;
        if (remainingSeconds <= 0) {
            return Result.success();
        }
        // A delayed T-10 that lands inside the final window would only be
        // overwritten by the T-2 moments later.
        if (leadMinutes == SessionWarningScheduler.WARNING_LEAD_MINUTES
                && remainingSeconds <= SessionWarningScheduler.FINAL_WARNING_LEAD_MINUTES * 60) {
            return Result.success();
        }
        if (SessionWarningScheduler.wasFired(context, leadMinutes, deadline)) {
            return Result.success();
        }

        SessionWarningScheduler.markFired(context, leadMinutes, deadline);
        long minutesLeft = (remainingSeconds + 59) / 60;
        new SessionNotification(context).showExpiring(minutesLeft, deadline * 1000L);
        return Result.success();
    }
}
