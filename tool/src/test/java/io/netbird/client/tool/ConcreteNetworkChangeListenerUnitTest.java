package io.netbird.client.tool;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.netbird.client.tool.networks.ConcreteNetworkChangeListener;
import io.netbird.client.tool.networks.Constants;
import io.netbird.client.tool.networks.NetworkChangeListener;
import io.netbird.client.tool.networks.NetworkToggleListener;

public class ConcreteNetworkChangeListenerUnitTest {
    private static class MockNetworkChangeDetector {
        NetworkChangeListener listener;
        public MockNetworkChangeDetector(NetworkChangeListener listener) {
            this.listener = listener;
        }

        public void changeToWifi() {
            this.listener.onNetworkChanged(Constants.NetworkType.WIFI);
        }

        public void changeToMobile() {
            this.listener.onNetworkChanged(Constants.NetworkType.MOBILE);
        }
    }

    @Test
    public void shouldNotifyListenerOnce() {
        // Assemble:
        var networkToggleListener = new NetworkToggleListener() {
            private int totalTimesNetworkTypeChanged = 0;
            @Override
            public void onNetworkTypeChanged() {
                totalTimesNetworkTypeChanged++;
            }
        };

        var networkChangeListener = new ConcreteNetworkChangeListener();
        networkChangeListener.subscribe(networkToggleListener);

        var networkChangeDetector = new MockNetworkChangeDetector(networkChangeListener);

        // Act:
        networkChangeDetector.changeToWifi();
        networkChangeDetector.changeToMobile();

        // Assert:
        assertEquals(1, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotNotifyListenerNetworkOnlyChangedOnce() {
        // Assemble:
        var networkToggleListener = new NetworkToggleListener() {
            private int totalTimesNetworkTypeChanged = 0;
            @Override
            public void onNetworkTypeChanged() {
                totalTimesNetworkTypeChanged++;
            }
        };

        var networkChangeListener = new ConcreteNetworkChangeListener();
        networkChangeListener.subscribe(networkToggleListener);

        var networkChangeDetector = new MockNetworkChangeDetector(networkChangeListener);

        // Act:
        networkChangeDetector.changeToWifi();

        // Assert:
        assertEquals(0, networkToggleListener.totalTimesNetworkTypeChanged);
    }

    @Test
    public void shouldNotNotifyListenerNetworkDidNotChange() {
        // Assemble:
        var networkToggleListener = new NetworkToggleListener() {
            private int totalTimesNetworkTypeChanged = 0;
            @Override
            public void onNetworkTypeChanged() {
                totalTimesNetworkTypeChanged++;
            }
        };

        var networkChangeListener = new ConcreteNetworkChangeListener();
        networkChangeListener.subscribe(networkToggleListener);

        var networkChangeDetector = new MockNetworkChangeDetector(networkChangeListener);

        // Act:
        networkChangeDetector.changeToWifi();
        networkChangeDetector.changeToWifi();

        // Assert:
        assertEquals(0, networkToggleListener.totalTimesNetworkTypeChanged);
    }
}
