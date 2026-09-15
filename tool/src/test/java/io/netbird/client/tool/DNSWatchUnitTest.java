package io.netbird.client.tool;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DNSWatchUnitTest {

    @Test
    public void noHostnameKeepsTheTunnelResolver() {
        assertTrue(DNSWatch.shouldAddTunnelResolver(null));
    }

    @Test
    public void emptyHostnameKeepsTheTunnelResolver() {
        assertTrue(DNSWatch.shouldAddTunnelResolver(""));
    }

    @Test
    public void privateDnsHostnameLeavesTheTunnelResolverOut() {
        assertFalse(DNSWatch.shouldAddTunnelResolver("dns.example.com"));
    }
}
