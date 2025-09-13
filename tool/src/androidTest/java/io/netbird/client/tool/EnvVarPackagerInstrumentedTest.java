package io.netbird.client.tool;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.netbird.gomobile.android.Android;

@RunWith(AndroidJUnit4.class)
public class EnvVarPackagerInstrumentedTest {
    @Test
    public void shouldReturnEnvironmentVariables() {
        var preferences = new Preferences(InstrumentationRegistry.getInstrumentation().getTargetContext());
        var environmentVariables = EnvVarPackager.getEnvironmentVariables(preferences);

        Assert.assertNotNull(environmentVariables);
        var forceRelay = environmentVariables.get(Android.getEnvKeyNBForceRelay());
        var variableNotPresentInList = environmentVariables.get("UNKNOWN_VAR");
        var emptyString = "";

        Assert.assertNotEquals(emptyString, forceRelay);
        Assert.assertEquals(emptyString, variableNotPresentInList);
    }
}
