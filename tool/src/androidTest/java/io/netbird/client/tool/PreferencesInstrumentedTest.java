package io.netbird.client.tool;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;

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
    public void shouldReturnFalseWhenConnectionForceRelayedIsNotSet() {
        Assert.assertFalse(preferences.isConnectionForceRelayed());
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
}
