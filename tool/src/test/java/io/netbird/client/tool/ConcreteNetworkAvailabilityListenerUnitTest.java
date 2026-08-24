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
        public void networkValidated(int type) {
            this.listener.onNetworkValidated(type, true);
        }
        public void networkLostValidation(int type) {
            this.listener.onNetworkValidated(type, false);
        }
        public void networkAvailable(int type) {
            this.listener.onNetworkAvailable(type);
        }
        public void networkLost(int type) {
            this.listener.onNetworkLost(type);
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

    @Test
    public void shouldNotifyWhenNewTransportValidatesAlongsideExisting() {
        // Cellular is already up and validated; WiFi comes up and validates.
        // The active validated transport changes MOBILE -> WIFI, so the Go
        // core should be notified to reset peer connections onto WiFi.
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener(() -> true);
        networkAvailabilityListener.subscribe(networkToggleListener);

        var detector = new MockNetworkChangeDetector(networkAvailabilityListener);

        detector.networkAvailable(Constants.NetworkType.MOBILE);
        detector.networkValidated(Constants.NetworkType.MOBILE); // initial state, no notify
        detector.networkAvailable(Constants.NetworkType.WIFI);
        detector.networkValidated(Constants.NetworkType.WIFI);  // MOBILE -> WIFI

        assertEquals(1, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotNotifyWhenSecondaryTransportValidatesButActiveUnchanged() {
        // WiFi is already up and validated (active transport). Enabling
        // cellular data causes cellular to appear and validate, but the phone
        // is still using WiFi — the active validated transport does not
        // change, so no notification should fire.
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener(() -> true);
        networkAvailabilityListener.subscribe(networkToggleListener);

        var detector = new MockNetworkChangeDetector(networkAvailabilityListener);

        detector.networkAvailable(Constants.NetworkType.WIFI);
        detector.networkValidated(Constants.NetworkType.WIFI);  // initial state, no notify
        detector.networkAvailable(Constants.NetworkType.MOBILE);
        detector.networkValidated(Constants.NetworkType.MOBILE); // active stays WIFI

        assertEquals(0, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotNotifyOnNetworkAvailableWithoutValidation() {
        // onAvailable alone must not trigger a notification: the network has
        // appeared but Android has not yet confirmed it reaches the internet.
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener(() -> true);
        networkAvailabilityListener.subscribe(networkToggleListener);

        var detector = new MockNetworkChangeDetector(networkAvailabilityListener);

        detector.networkAvailable(Constants.NetworkType.MOBILE);
        detector.networkAvailable(Constants.NetworkType.WIFI);

        assertEquals(0, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotNotifyOnValidationLossOfNonActiveTransport() {
        // WiFi is the active validated transport. Cellular losing validation
        // does not change the active transport, so no notification.
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener(() -> true);
        networkAvailabilityListener.subscribe(networkToggleListener);

        var detector = new MockNetworkChangeDetector(networkAvailabilityListener);

        detector.networkAvailable(Constants.NetworkType.WIFI);
        detector.networkValidated(Constants.NetworkType.WIFI);  // initial state
        detector.networkAvailable(Constants.NetworkType.MOBILE);
        detector.networkValidated(Constants.NetworkType.MOBILE); // active stays WIFI
        detector.networkLostValidation(Constants.NetworkType.MOBILE); // still WIFI

        assertEquals(0, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotifyWhenActiveTransportLosesValidationAndFallback() {
        // WiFi is active, then loses validation. Cellular is validated, so
        // the active transport changes WIFI -> MOBILE — a real handover.
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener(() -> true);
        networkAvailabilityListener.subscribe(networkToggleListener);

        var detector = new MockNetworkChangeDetector(networkAvailabilityListener);

        detector.networkAvailable(Constants.NetworkType.WIFI);
        detector.networkValidated(Constants.NetworkType.WIFI);  // initial state
        detector.networkAvailable(Constants.NetworkType.MOBILE);
        detector.networkValidated(Constants.NetworkType.MOBILE); // active stays WIFI
        detector.networkLostValidation(Constants.NetworkType.WIFI); // WIFI -> MOBILE

        assertEquals(1, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotNotifyOnInitialValidation() {
        // The first transport to validate establishes the initial active
        // state, not a handover, so no notification should fire.
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener(() -> true);
        networkAvailabilityListener.subscribe(networkToggleListener);

        var detector = new MockNetworkChangeDetector(networkAvailabilityListener);

        detector.networkAvailable(Constants.NetworkType.WIFI);
        detector.networkValidated(Constants.NetworkType.WIFI);

        assertEquals(0, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotifyOnActiveTransportLostAndFallback() {
        // WiFi is active and is lost entirely. Cellular is validated, so the
        // active transport changes WIFI -> MOBILE.
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener(() -> true);
        networkAvailabilityListener.subscribe(networkToggleListener);

        var detector = new MockNetworkChangeDetector(networkAvailabilityListener);

        detector.networkAvailable(Constants.NetworkType.WIFI);
        detector.networkValidated(Constants.NetworkType.WIFI);  // initial state
        detector.networkAvailable(Constants.NetworkType.MOBILE);
        detector.networkValidated(Constants.NetworkType.MOBILE); // active stays WIFI
        detector.networkLost(Constants.NetworkType.WIFI);        // WIFI -> MOBILE

        assertEquals(1, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotifyOnEachActiveTransportChange() {
        // Multiple handovers: each time the active validated transport
        // actually changes, the Go core should be notified.
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener(() -> true);
        networkAvailabilityListener.subscribe(networkToggleListener);

        var detector = new MockNetworkChangeDetector(networkAvailabilityListener);

        detector.networkAvailable(Constants.NetworkType.MOBILE);
        detector.networkValidated(Constants.NetworkType.MOBILE); // initial state
        detector.networkAvailable(Constants.NetworkType.WIFI);
        detector.networkValidated(Constants.NetworkType.WIFI);   // MOBILE -> WIFI (+1)
        detector.networkLostValidation(Constants.NetworkType.WIFI); // WIFI -> MOBILE (+1)
        detector.networkValidated(Constants.NetworkType.WIFI);   // MOBILE -> WIFI (+1)

        assertEquals(3, networkToggleListener.totalTimesNetworkTypeChanged);
    }
}
