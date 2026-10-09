package io.netbird.client.tool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs one fixed task on a single background thread. Requests made while a run
 * is already queued collapse into that run; a request made during a run queues
 * exactly one more, so the task always ends on the latest state. Meant for
 * refreshes triggered by Go callbacks: the callback thread only signals, the
 * JNI calls back into Go happen here.
 */
public final class CoalescingWorker {
    private final ExecutorService executor;
    private final Runnable task;
    private final AtomicBoolean queued = new AtomicBoolean(false);

    public CoalescingWorker(String threadName, Runnable task) {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
        this.task = task;
    }

    /** Schedules a run unless one is already waiting. Safe from any thread. */
    public void request() {
        if (queued.getAndSet(true)) {
            return;
        }
        try {
            executor.execute(this::run);
        } catch (RejectedExecutionException ignored) {
            queued.set(false);
        }
    }

    /** Runs a one-off action on the worker thread, after any queued run. */
    public void submit(Runnable action) {
        try {
            executor.execute(action);
        } catch (RejectedExecutionException ignored) {
            // shut down; the owner is going away and no longer wants the result
        }
    }

    /** Stops the worker. Queued runs are dropped; a run in progress finishes. */
    public void shutdown() {
        executor.shutdownNow();
    }

    private void run() {
        queued.set(false);
        task.run();
    }
}
