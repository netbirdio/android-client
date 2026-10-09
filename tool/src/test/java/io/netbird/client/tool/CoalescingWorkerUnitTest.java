package io.netbird.client.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CoalescingWorkerUnitTest {

    @Test
    public void requestsDuringRunCollapseIntoOneMoreRun() throws Exception {
        CountDownLatch firstRunStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRun = new CountDownLatch(1);
        CountDownLatch twoRunsDone = new CountDownLatch(2);
        AtomicInteger runs = new AtomicInteger();

        CoalescingWorker worker = new CoalescingWorker("test-worker", () -> {
            if (runs.incrementAndGet() == 1) {
                firstRunStarted.countDown();
                await(releaseFirstRun);
            }
            twoRunsDone.countDown();
        });

        worker.request();
        assertTrue(firstRunStarted.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < 100; i++) {
            worker.request();
        }
        releaseFirstRun.countDown();

        assertTrue(twoRunsDone.await(5, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertEquals(2, runs.get());
        worker.shutdown();
    }

    @Test
    public void taskRunsOnTheWorkerThread() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();

        CoalescingWorker worker = new CoalescingWorker("nb-test", () -> {
            threadName.set(Thread.currentThread().getName());
            done.countDown();
        });

        worker.request();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals("nb-test", threadName.get());
        worker.shutdown();
    }

    @Test
    public void requestsAfterShutdownAreDropped() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        CoalescingWorker worker = new CoalescingWorker("test-worker", runs::incrementAndGet);

        worker.shutdown();
        worker.request();
        worker.submit(runs::incrementAndGet);
        Thread.sleep(100);

        assertEquals(0, runs.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
