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

        public void activateWifi() {
            this.listener.onNetworkAvailable(Constants.NetworkType.WIFI);
        }
        public void deactivateWifi() {
            this.listener.onNetworkLost(Constants.NetworkType.WIFI);
        }
        public void activateMobile() {
            this.listener.onNetworkAvailable(Constants.NetworkType.MOBILE);
        }
        public void deactivateMobile() {
            this.listener.onNetworkLost(Constants.NetworkType.MOBILE);
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
    public void shouldNotifyListenerNetworkUpgraded() {
        // Assemble:
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener();
        networkAvailabilityListener.subscribe(networkToggleListener);

        var networkChangeDetector = new MockNetworkChangeDetector(networkAvailabilityListener);

        // Act:
        networkChangeDetector.activateMobile();
        networkChangeDetector.activateWifi();

        // Assert:
        assertEquals(1, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotifyListenerNetworkDowngraded() {
        // Assemble:
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener();
        networkAvailabilityListener.subscribe(networkToggleListener);

        var networkChangeDetector = new MockNetworkChangeDetector(networkAvailabilityListener);

        // Act:
        networkChangeDetector.activateMobile();
        networkChangeDetector.activateWifi();   // upgraded, network changes.
        networkChangeDetector.deactivateWifi(); // downgraded, network changes.

        // Assert:
        assertEquals(2, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotNotifyListenerNetworkDidNotUpgrade() {
        // Assemble:
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener();
        networkAvailabilityListener.subscribe(networkToggleListener);

        var networkChangeDetector = new MockNetworkChangeDetector(networkAvailabilityListener);

        // Act:
        networkChangeDetector.activateWifi();

        networkToggleListener.resetCounter();

        networkChangeDetector.activateMobile();
        networkChangeDetector.deactivateMobile();

        // Assert:
        assertEquals(0, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotNotifyListenerNoNetworksAvailable() {
        // Assemble:
        var networkToggleListener = new MockNetworkToggleListener();
        var networkAvailabilityListener = new ConcreteNetworkAvailabilityListener();
        networkAvailabilityListener.subscribe(networkToggleListener);

        var networkChangeDetector = new MockNetworkChangeDetector(networkAvailabilityListener);

        // Act:
        networkChangeDetector.activateWifi();

        networkToggleListener.resetCounter();

        networkChangeDetector.deactivateWifi();

        // Assert:
        assertEquals(0, networkToggleListener.totalTimesNetworkTypeChanged);
    }
}
