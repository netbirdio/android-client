package io.netbird.client.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MDMRestrictionsTest {

    private static final String FULL = "{"
            + "\"mdm\":{"
            + "\"managementURL\":\"https://vpn.example.com:443\","
            + "\"preSharedKey\":true,"
            + "\"rosenpassEnabled\":true,"
            + "\"rosenpassPermissive\":false,"
            + "\"allowServerSSH\":false,"
            + "\"splitTunnelMode\":true,"
            + "\"splitTunnelApps\":true,"
            + "\"disableAdvancedView\":true},"
            + "\"features\":{\"disableProfiles\":true,\"disableNetworks\":false}}";

    @Test
    public void readsTheSnapshot() {
        MDMRestrictions restrictions = MDMRestrictions.decode(FULL);

        assertEquals("https://vpn.example.com:443", restrictions.mdm.managementURL);
        assertTrue(restrictions.mdm.managesManagementURL());
        assertTrue(restrictions.mdm.preSharedKey);
        assertTrue(restrictions.mdm.rosenpassEnabled);
        assertFalse(restrictions.mdm.rosenpassPermissive);
        assertTrue(restrictions.features.disableProfiles);
        assertFalse(restrictions.features.disableNetworks);
    }

    @Test
    public void triStatesTellManagedFromUnmanaged() {
        MDMRestrictions managedFalse = MDMRestrictions.decode(FULL);
        assertEquals(Boolean.FALSE, managedFalse.mdm.allowServerSSH);

        MDMRestrictions unmanaged = MDMRestrictions.decode("{\"mdm\":{}}");
        assertNull(unmanaged.mdm.allowServerSSH);
        assertNull(unmanaged.mdm.disableAdvancedView);

        MDMRestrictions explicitNull = MDMRestrictions.decode("{\"mdm\":{\"allowServerSSH\":null}}");
        assertNull(explicitNull.mdm.allowServerSSH);
    }

    @Test
    public void onlyAnExplicitTrueHidesTheAdvancedSection() {
        assertTrue(MDMRestrictions.decode(FULL).mdm.hidesAdvancedView());
        assertFalse(MDMRestrictions.decode("{\"mdm\":{\"disableAdvancedView\":false}}")
                .mdm.hidesAdvancedView());
        assertFalse(MDMRestrictions.EMPTY.mdm.hidesAdvancedView());
    }

    @Test
    public void eitherSplitTunnelKeyMeansTheSelectionIsManaged() {
        assertTrue(MDMRestrictions.decode(FULL).mdm.managesSplitTunnel());
        assertTrue(MDMRestrictions.decode("{\"mdm\":{\"splitTunnelApps\":true}}")
                .mdm.managesSplitTunnel());
        assertFalse(MDMRestrictions.EMPTY.mdm.managesSplitTunnel());
    }

    @Test
    public void missingAndUnknownKeysAreSurvivable() {
        MDMRestrictions restrictions = MDMRestrictions.decode(
                "{\"mdm\":{\"somethingNewer\":true},\"features\":{}}");

        assertFalse(restrictions.mdm.preSharedKey);
        assertFalse(restrictions.features.disableProfiles);
        assertEquals("", restrictions.mdm.managementURL);
    }

    @Test
    public void anUnreadableSnapshotManagesNothing() {
        assertFalse(MDMRestrictions.decode("not json").mdm.preSharedKey);
        assertFalse(MDMRestrictions.decode("").features.disableNetworks);
        assertFalse(MDMRestrictions.decode(null).features.disableNetworks);
    }

    @Test
    public void namesTheKeysAPolicyRefused() {
        assertEquals("rosenpassEnabled", MDMRestrictions.rejectedKeys(
                "fields managed by MDM cannot be modified: [rosenpassEnabled]"));
        assertEquals("preSharedKey, managementURL", MDMRestrictions.rejectedKeys(
                "fields managed by MDM cannot be modified: [preSharedKey, managementURL]"));
    }

    @Test
    public void aRefusalWithoutKeysIsStillARefusal() {
        assertEquals("", MDMRestrictions.rejectedKeys("fields managed by MDM cannot be modified"));
    }

    @Test
    public void anOrdinaryFailureIsNotARefusal() {
        assertNull(MDMRestrictions.rejectedKeys("permission denied: /data/config.json"));
        assertNull(MDMRestrictions.rejectedKeys(null));
    }
}
