package io.netbird.client.tool;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

@RunWith(AndroidJUnit4.class)
public class PreferencesInstrumentedTest {
    private static Preferences preferences;

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @BeforeClass
    public static void setUp() {
        preferences = new Preferences(getContext());
    }

    @After
    public void tearDown() {
        getContext().getSharedPreferences("netbird", Context.MODE_PRIVATE).edit().clear().apply();
    }

    @Test
    public void shouldCreatePreferencesWithoutThrownException() {
        Preferences preferences = null;
        Exception thrown = null;

        try {
            preferences = new Preferences(getContext());
        } catch (Exception e) {
            thrown = e;
        }

        Assert.assertNull(thrown);
        Assert.assertNotNull(preferences);
    }

    @Test
    public void shouldReturnTrueWhenConnectionForceRelayedIsNotSet() {
        Assert.assertTrue(preferences.isConnectionForceRelayed());
    }

    @Test
    public void shouldReturnTrueAfterEnablingForcedRelayConnection() {
        preferences.enableForcedRelayConnection();

        Assert.assertTrue(preferences.isConnectionForceRelayed());
    }

    @Test
    public void shouldReturnFalseAfterDisablingForcedRelayConnection() {
        preferences.enableForcedRelayConnection();
        preferences.disableForcedRelayConnection();

        Assert.assertFalse(preferences.isConnectionForceRelayed());
    }

    @Test
    public void shouldReturnFalseWhenTraceLogIsNotSet() {
        Assert.assertFalse(preferences.isTraceLogEnabled());
    }

    @Test
    public void shouldReturnTrueAfterEnablingTraceLog() {
        preferences.enableTraceLog();

        Assert.assertTrue(preferences.isTraceLogEnabled());
    }

    @Test
    public void shouldReturnFalseAfterDisablingTraceLog() {
        preferences.enableTraceLog();
        preferences.disableTraceLog();

        Assert.assertFalse(preferences.isTraceLogEnabled());
    }

    @Test
    public void shouldReturnCorrectDefaultServer() {
        final var defaultServer = "https://api.netbird.io";

        Assert.assertEquals(defaultServer, Preferences.defaultServer());
    }

    @Test
    public void shouldDefaultToSplitTunnellingOff() {
        SplitTunnelConfig config = preferences.getSplitTunnelConfig();

        Assert.assertEquals(SplitTunnelConfig.Mode.OFF, config.getMode());
        Assert.assertTrue(config.getExcluded().isEmpty());
        Assert.assertTrue(config.getIncluded().isEmpty());
    }

    @Test
    public void shouldRoundTripBothSelectionsAndTheMode() {
        preferences.saveSplitTunnelConfig(new SplitTunnelConfig(
                SplitTunnelConfig.Mode.EXCLUDE,
                new HashSet<>(Arrays.asList("com.example.a", "com.example.b")),
                new HashSet<>(Collections.singletonList("com.example.c"))));

        SplitTunnelConfig config = preferences.getSplitTunnelConfig();

        Assert.assertEquals(SplitTunnelConfig.Mode.EXCLUDE, config.getMode());
        Assert.assertEquals(new HashSet<>(Arrays.asList("com.example.a", "com.example.b")),
                config.getExcluded());
        Assert.assertEquals(new HashSet<>(Collections.singletonList("com.example.c")),
                config.getIncluded());
    }

    // Switching mode must not throw the other list away: the user gets their
    // previous picks back when they switch back.
    @Test
    public void shouldKeepTheInactiveSelectionAcrossAModeChange() {
        preferences.saveSplitTunnelConfig(new SplitTunnelConfig(
                SplitTunnelConfig.Mode.EXCLUDE,
                new HashSet<>(Collections.singletonList("com.example.a")),
                new HashSet<>(Collections.singletonList("com.example.b"))));

        SplitTunnelConfig stored = preferences.getSplitTunnelConfig();
        preferences.saveSplitTunnelConfig(new SplitTunnelConfig(
                SplitTunnelConfig.Mode.INCLUDE, stored.getExcluded(), stored.getIncluded()));

        SplitTunnelConfig config = preferences.getSplitTunnelConfig();
        Assert.assertEquals(SplitTunnelConfig.Mode.INCLUDE, config.getMode());
        Assert.assertEquals(new HashSet<>(Collections.singletonList("com.example.a")),
                config.getExcluded());
    }

    // A set handed to SharedPreferences is kept by reference and handed back on
    // read, so a later change to the caller's copy must not leak into storage.
    @Test
    public void shouldNotAliasTheCallersSet() {
        HashSet<String> picks = new HashSet<>(Collections.singletonList("com.example.a"));
        preferences.saveSplitTunnelConfig(new SplitTunnelConfig(
                SplitTunnelConfig.Mode.EXCLUDE, picks, Collections.emptySet()));

        picks.add("com.example.late");

        Assert.assertEquals(new HashSet<>(Collections.singletonList("com.example.a")),
                preferences.getSplitTunnelConfig().getExcluded());
    }
}
