package io.netbird.client.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class MDMSplitTunnelTest {

    @Test
    public void allowMeansOnlyTheseApplicationsUseTheTunnel() {
        SplitTunnelConfig config = MDMSplitTunnel.fromManagedConfiguration(
                "{\"splitTunnelMode\":\"allow\",\"splitTunnelApps\":[\"com.example.one\"]}");

        assertEquals(SplitTunnelConfig.Mode.INCLUDE, config.getMode());
        assertTrue(config.getIncluded().contains("com.example.one"));
        assertTrue(config.getExcluded().isEmpty());
    }

    @Test
    public void disallowMeansTheseApplicationsStayOut() {
        SplitTunnelConfig config = MDMSplitTunnel.fromManagedConfiguration(
                "{\"splitTunnelMode\":\"disallow\",\"splitTunnelApps\":\"com.example.one,com.example.two\"}");

        assertEquals(SplitTunnelConfig.Mode.EXCLUDE, config.getMode());
        assertEquals(2, config.getExcluded().size());
        assertTrue(config.getExcluded().contains("com.example.two"));
    }

    @Test
    public void theTunnelAppliesAnAllowListAsAnAllowFilter() {
        SplitTunnelConfig config = MDMSplitTunnel.fromManagedConfiguration(
                "{\"splitTunnelMode\":\"allow\",\"splitTunnelApps\":[\"com.example.one\"]}");

        SplitTunnelConfig.Resolution resolution = config.resolve("io.netbird.client");

        assertEquals(SplitTunnelConfig.Filter.ALLOW, resolution.getFilter());
        assertTrue(resolution.getPackages().contains("com.example.one"));
        // The app's own SSH client reaches peers through the tunnel.
        assertTrue(resolution.getPackages().contains("io.netbird.client"));
    }

    @Test
    public void noModeMeansTheUsersOwnSelectionStands() {
        assertNull(MDMSplitTunnel.fromManagedConfiguration(
                "{\"splitTunnelApps\":[\"com.example.one\"]}"));
        assertNull(MDMSplitTunnel.fromManagedConfiguration("{}"));
        assertNull(MDMSplitTunnel.fromManagedConfiguration(""));
        assertNull(MDMSplitTunnel.fromManagedConfiguration(null));
    }

    @Test
    public void aModeThisBuildDoesNotKnowChangesNothing() {
        assertNull(MDMSplitTunnel.fromManagedConfiguration(
                "{\"splitTunnelMode\":\"quarantine\",\"splitTunnelApps\":\"com.example.one\"}"));
    }

    @Test
    public void anUnreadablePolicyChangesNothing() {
        assertNull(MDMSplitTunnel.fromManagedConfiguration("not json"));
    }

    @Test
    public void aModeWithoutApplicationsIsStillTheModeThePolicySet() {
        SplitTunnelConfig config = MDMSplitTunnel.fromManagedConfiguration(
                "{\"splitTunnelMode\":\"disallow\"}");

        assertEquals(SplitTunnelConfig.Mode.EXCLUDE, config.getMode());
        assertTrue(config.getExcluded().isEmpty());
    }

    @Test
    public void readsTheModeCaseAndSpaceInsensitively() {
        assertEquals(SplitTunnelConfig.Mode.INCLUDE,
                MDMSplitTunnel.of(" Allow ", Arrays.asList("com.example.one")).getMode());
    }

    @Test
    public void dropsBlankPackageNames() {
        SplitTunnelConfig config = MDMSplitTunnel.fromManagedConfiguration(
                "{\"splitTunnelMode\":\"disallow\",\"splitTunnelApps\":\" com.example.one , ,\"}");

        assertEquals(1, config.getExcluded().size());
        assertTrue(config.getExcluded().contains("com.example.one"));
    }
}
