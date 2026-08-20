package io.netbird.client.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SplitTunnelConfigUnitTest {

    private static final String OWN = "io.netbird.client";

    private static SplitTunnelConfig config(SplitTunnelConfig.Mode mode,
                                            Set<String> excluded,
                                            Set<String> included) {
        return new SplitTunnelConfig(mode, excluded, included);
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @Test
    public void offKeepsOnlyTheHistoricExclusions() {
        SplitTunnelConfig.Resolution r =
                config(SplitTunnelConfig.Mode.OFF, Collections.emptySet(), Collections.emptySet())
                        .resolve(OWN);

        assertEquals(SplitTunnelConfig.Filter.DISALLOW, r.getFilter());
        assertEquals(SplitTunnelConfig.ALWAYS_EXCLUDED, r.getPackages());
    }

    @Test
    public void offIgnoresSelectionsMadeInEitherMode() {
        SplitTunnelConfig.Resolution r =
                config(SplitTunnelConfig.Mode.OFF, setOf("com.example.a"), setOf("com.example.b"))
                        .resolve(OWN);

        assertEquals(SplitTunnelConfig.ALWAYS_EXCLUDED, r.getPackages());
    }

    @Test
    public void excludeAddsThePicksOnTopOfTheHistoricOnes() {
        SplitTunnelConfig.Resolution r =
                config(SplitTunnelConfig.Mode.EXCLUDE, setOf("com.example.a"), Collections.emptySet())
                        .resolve(OWN);

        assertEquals(SplitTunnelConfig.Filter.DISALLOW, r.getFilter());
        assertTrue(r.getPackages().containsAll(SplitTunnelConfig.ALWAYS_EXCLUDED));
        assertTrue(r.getPackages().contains("com.example.a"));
    }

    @Test
    public void includeAllowsOnlyThePicksPlusThisApp() {
        SplitTunnelConfig.Resolution r =
                config(SplitTunnelConfig.Mode.INCLUDE, Collections.emptySet(), setOf("com.example.a"))
                        .resolve(OWN);

        assertEquals(SplitTunnelConfig.Filter.ALLOW, r.getFilter());
        assertEquals(setOf("com.example.a", OWN), r.getPackages());
    }

    // The built-in SSH client has to reach peers through the tunnel, so the app
    // must never be able to lock itself out of it.
    @Test
    public void includeAlwaysCarriesThisApp() {
        SplitTunnelConfig.Resolution r =
                config(SplitTunnelConfig.Mode.INCLUDE, Collections.emptySet(), setOf("com.example.a"))
                        .resolve(OWN);

        assertTrue(r.getPackages().contains(OWN));
    }

    // An empty allowlist would leave a tunnel carrying nothing, which reads as a
    // broken VPN rather than a configured one.
    @Test
    public void emptyIncludeFallsBackToCarryingEverything() {
        SplitTunnelConfig cfg =
                config(SplitTunnelConfig.Mode.INCLUDE, Collections.emptySet(), Collections.emptySet());

        assertFalse(cfg.isActive());
        SplitTunnelConfig.Resolution r = cfg.resolve(OWN);
        assertEquals(SplitTunnelConfig.Filter.DISALLOW, r.getFilter());
        assertEquals(SplitTunnelConfig.ALWAYS_EXCLUDED, r.getPackages());
    }

    @Test
    public void emptyExcludeIsInactiveButStillDropsTheHistoricOnes() {
        SplitTunnelConfig cfg =
                config(SplitTunnelConfig.Mode.EXCLUDE, Collections.emptySet(), Collections.emptySet());

        assertFalse(cfg.isActive());
        assertEquals(SplitTunnelConfig.ALWAYS_EXCLUDED, cfg.resolve(OWN).getPackages());
    }

    @Test
    public void selectionsAreActiveWhenNotEmpty() {
        assertTrue(config(SplitTunnelConfig.Mode.EXCLUDE, setOf("com.example.a"), Collections.emptySet()).isActive());
        assertTrue(config(SplitTunnelConfig.Mode.INCLUDE, Collections.emptySet(), setOf("com.example.a")).isActive());
    }

    @Test
    public void nullModeAndNullSelectionsAreTreatedAsOff() {
        SplitTunnelConfig cfg = new SplitTunnelConfig(null, null, null);

        assertEquals(SplitTunnelConfig.Mode.OFF, cfg.getMode());
        assertTrue(cfg.getExcluded().isEmpty());
        assertTrue(cfg.getIncluded().isEmpty());
        assertEquals(SplitTunnelConfig.ALWAYS_EXCLUDED, cfg.resolve(OWN).getPackages());
    }

    // SharedPreferences hands back a set it keeps using, so the config must not
    // hold on to anything the caller can still change underneath it.
    @Test
    public void storedSelectionIsCopiedNotAliased() {
        Set<String> mutable = setOf("com.example.a");
        SplitTunnelConfig cfg = config(SplitTunnelConfig.Mode.EXCLUDE, mutable, Collections.emptySet());

        mutable.add("com.example.late");

        assertFalse(cfg.getExcluded().contains("com.example.late"));
        assertFalse(cfg.resolve(OWN).getPackages().contains("com.example.late"));
    }

    @Test
    public void resolutionWithoutOwnPackageStillWorks() {
        SplitTunnelConfig.Resolution r =
                config(SplitTunnelConfig.Mode.INCLUDE, Collections.emptySet(), setOf("com.example.a"))
                        .resolve(null);

        assertEquals(setOf("com.example.a"), r.getPackages());
    }
}
