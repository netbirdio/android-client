package io.netbird.client.ui.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ManagementUrlTest {

    @Test
    public void isValid_acceptsDomainsLocalhostAndIpv4() {
        assertTrue(ManagementUrl.isValid("https://api.netbird.io:443"));
        assertTrue(ManagementUrl.isValid("api.netbird.io"));
        assertTrue(ManagementUrl.isValid("http://localhost:8080"));
        assertTrue(ManagementUrl.isValid("https://10.0.0.1:443/path"));
    }

    @Test
    public void isValid_acceptsIpv6Literals() {
        assertTrue(ManagementUrl.isValid("https://[2001:db8::1]:443"));
        // Without a scheme, since normalize adds it later anyway.
        assertTrue(ManagementUrl.isValid("[2001:db8::1]:443"));
        // Both spellings of the same address, plus the awkward middles.
        assertTrue(ManagementUrl.isValid("https://[2001:0db8:0000:0000:0000:0000:0000:0001]"));
        assertTrue(ManagementUrl.isValid("https://[::1]/path?q=1#f"));
        assertTrue(ManagementUrl.isValid("https://[::]"));
        assertTrue(ManagementUrl.isValid("https://[::ffff:192.0.2.1]:443"));
    }

    @Test
    public void isValid_rejectsMalformedIpv6Literals() {
        assertFalse(ManagementUrl.isValid("https://[2001:db8::1"));
        assertFalse(ManagementUrl.isValid("https://[::zz]"));
        assertFalse(ManagementUrl.isValid("https://[1:2:3:4:5:6:7:8:9]"));
        assertFalse(ManagementUrl.isValid("https://[]"));
        // An unbracketed IPv6 address has no port boundary, so it is not a URL.
        assertFalse(ManagementUrl.isValid("https://2001:db8::1:443"));
    }

    @Test
    public void isValid_rejectsEmptyAndGarbage() {
        assertFalse(ManagementUrl.isValid(null));
        assertFalse(ManagementUrl.isValid("   "));
        assertFalse(ManagementUrl.isValid("not a url"));
    }

    @Test
    public void normalize_keepsBracketedHost() {
        assertEquals("https://[2001:db8::1]:443", ManagementUrl.normalize("[2001:db8::1]:443"));
        assertEquals("http://[::1]:8080", ManagementUrl.normalize("http://[::1]:8080"));
    }
}
