package io.netbird.client.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ManagedConfigurationTest {

    @Test
    public void noConfigurationEncodesToNothing() {
        assertEquals("", ManagedConfiguration.encode(null));
        assertEquals("", ManagedConfiguration.encode(Collections.emptyMap()));
    }

    @Test
    public void keepsTheTypesTheConfigurationWasDeliveredIn() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("managementURL", "https://vpn.example.com");
        values.put("disableProfiles", true);
        values.put("wireguardPort", 51820);

        JSONObject encoded = new JSONObject(ManagedConfiguration.encode(values));

        assertEquals("https://vpn.example.com", encoded.getString("managementURL"));
        assertTrue(encoded.getBoolean("disableProfiles"));
        assertEquals(51820, encoded.getInt("wireguardPort"));
    }

    @Test
    public void carriesListsAcross() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("splitTunnelApps", new String[]{"com.example.one", "com.example.two"});

        JSONObject encoded = new JSONObject(ManagedConfiguration.encode(values));

        assertEquals(2, encoded.getJSONArray("splitTunnelApps").length());
        assertEquals("com.example.two", encoded.getJSONArray("splitTunnelApps").getString(1));
    }

    @Test
    public void carriesNestedBundlesAsObjects() throws Exception {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("inner", "value");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("outer", nested);

        JSONObject encoded = new JSONObject(ManagedConfiguration.encode(values));

        assertEquals("value", encoded.getJSONObject("outer").getString("inner"));
    }

    @Test
    public void dropsWhatJsonCannotHold() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("disableNetworks", true);
        values.put("mystery", new Object());
        values.put("notANumber", Double.NaN);

        JSONObject encoded = new JSONObject(ManagedConfiguration.encode(values));

        assertTrue(encoded.getBoolean("disableNetworks"));
        assertFalse(encoded.has("mystery"));
        assertFalse(encoded.has("notANumber"));
    }

    @Test
    public void aConfigurationOfNothingUsableIsNoConfiguration() {
        assertEquals("", ManagedConfiguration.encode(
                Collections.singletonMap("mystery", new Object())));
    }

    @Test
    public void ignoresEmptyKeys() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("", "orphan");
        values.put("disableNetworks", true);

        JSONObject encoded = new JSONObject(ManagedConfiguration.encode(values));

        assertEquals(1, encoded.length());
    }

    @Test
    public void listsDropTheirUnusableEntries() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("splitTunnelApps", Arrays.asList("com.example.one", new Object()));

        JSONObject encoded = new JSONObject(ManagedConfiguration.encode(values));

        assertEquals(1, encoded.getJSONArray("splitTunnelApps").length());
    }
}
