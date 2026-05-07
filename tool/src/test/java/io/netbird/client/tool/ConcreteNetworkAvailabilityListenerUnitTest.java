package io.netbird.client.tool;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.netbird.client.tool.networks.ConcreteNetworkAvailabilityListener;
import io.netbird.client.tool.networks.Constants;
import io.netbird.client.tool.networks.NetworkAvailabilityListener;
import io.netbird.client.tool.networks.NetworkToggleListener;

public class ConcreteNetworkAvailabilityListenerUnitTest {
    private static class MockNetworkChangeDetector {
        private final NetworkAvailabilityListener listener;
        public MockNetworkChangeDetector(NetworkAvailabilityListener listener) {
            this.listener = listener;
        }

        public void defaultBecameWifi() {
            this.listener.onDefaultNetworkTypeChanged(Constants.NetworkType.WIFI);
        }
        public void defaultBecameMobile() {
            this.listener.onDefaultNetworkTypeChanged(Constants.NetworkType.MOBILE);
        }
    }

    private static class MockNetworkToggleListener implements NetworkToggleListener {
        private int totalTimesNetworkTypeChanged = 0;

        @Override
        public void onNetworkTypeChanged() {
            totalTimesNetworkTypeChanged++;
        }

        public void resetCounter() {
            totalTimesNetworkTypeChanged = 0;
        }
    }

    @Test
    public void shouldNotifyOnMobileToWifiTransition() {
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener(() -> true);
        networkAvailabilityListener.subscribe(networkToggleListener);

        var detector = new MockNetworkChangeDetector(networkAvailabilityListener);

        detector.defaultBecameMobile(); // first observation, not a transition
        detector.defaultBecameWifi();   // mobile -> wifi

        assertEquals(1, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotifyOnWifiToMobileTransition() {
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener(() -> true);
        networkAvailabilityListener.subscribe(networkToggleListener);

        var detector = new MockNetworkChangeDetector(networkAvailabilityListener);

        detector.defaultBecameWifi();   // first observation
        detector.defaultBecameMobile(); // wifi -> mobile

        assertEquals(1, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotifyOnEachTypeFlip() {
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener(() -> true);
        networkAvailabilityListener.subscribe(networkToggleListener);

        var detector = new MockNetworkChangeDetector(networkAvailabilityListener);

        detector.defaultBecameMobile(); // first observation
        detector.defaultBecameWifi();   // +1
        detector.defaultBecameMobile(); // +1
        detector.defaultBecameWifi();   // +1

        assertEquals(3, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotNotifyOnDuplicateSameType() {
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener(() -> true);
        networkAvailabilityListener.subscribe(networkToggleListener);

        var detector = new MockNetworkChangeDetector(networkAvailabilityListener);

        detector.defaultBecameWifi();
        detector.defaultBecameWifi();
        detector.defaultBecameWifi();

        assertEquals(0, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotNotifyOnInitialObservation() {
        // The first onDefaultNetworkTypeChanged after subscribe is the
        // current state, not a transition.
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener(() -> true);
        networkAvailabilityListener.subscribe(networkToggleListener);

        var detector = new MockNetworkChangeDetector(networkAvailabilityListener);

        detector.defaultBecameWifi();

        assertEquals(0, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotNotifyWhenShouldNotifyReturnsFalse() {
        // shouldNotify gates notifications, e.g. while engine is not running.
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener(() -> false);
        networkAvailabilityListener.subscribe(networkToggleListener);

        var detector = new MockNetworkChangeDetector(networkAvailabilityListener);

        detector.defaultBecameMobile();
        detector.defaultBecameWifi();
        detector.defaultBecameMobile();

        assertEquals(0, networkToggleListener.totalTimesNetworkTypeChanged);
    }
}
