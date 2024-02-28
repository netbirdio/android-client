package io.netbird.client;

import static io.netbird.client.NotificationReceiver.sendEvent;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.ReactActivity;
import com.facebook.react.ReactActivityDelegate;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint;
import com.facebook.react.defaults.DefaultReactActivityDelegate;

import org.devio.rn.splashscreen.SplashScreen;

import java.util.Objects;

public class MainActivity extends ReactActivity {
    public final static String SEND_EVENT_ACTION = "send_event_action";
    public final static String NOTIFICATION_KEY = "notification";
    public final static String NOTIFICATION_IN_APP_KEY = "notificationInApp";
    public final static String NEW_ROUTE_MESSAGE = "NewRouteSetting";
    public final static String MESSAGE_KEY = "message_key";
    String sendEventToReactMessageKey = "";
    SharedPreferences sendEventActionSharedPref;


    /**
     * Returns the name of the main component registered from JavaScript. This is used to schedule
     * rendering of the component.
     */
    @Override
    protected String getMainComponentName() {
        return "NetBird";
    }

    /**
     * Returns the instance of the {@link ReactActivityDelegate}. Here we use a util class {@link
     * DefaultReactActivityDelegate} which allows you to easily enable Fabric and Concurrent React
     * (aka React 18) with two boolean flags.
     */
    @Override
    protected ReactActivityDelegate createReactActivityDelegate() {
        return new DefaultReactActivityDelegate(
                this,
                getMainComponentName(),
                // If you opted-in for the New Architecture, we enable the Fabric Renderer.
                DefaultNewArchitectureEntryPoint.getFabricEnabled(), // fabricEnabled
                // If you opted-in for the New Architecture, we enable Concurrent React (i.e. React 18).
                DefaultNewArchitectureEntryPoint.getConcurrentReactEnabled() // concurrentRootEnabled
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);
        SplashScreen.show(this);
        MainApplication application = (MainApplication) getApplication();

        sendEventActionSharedPref = getSharedPreferences(SEND_EVENT_ACTION, Context.MODE_PRIVATE);
        sendEventToReactMessageKey = sendEventActionSharedPref.getString(MESSAGE_KEY, "");

        application.getReactNativeHost().getReactInstanceManager().addReactInstanceEventListener(reactContext -> {
            if (!sendEventToReactMessageKey.isEmpty()) {
                new Thread(() -> runOnUiThread(() -> {
                    WritableMap params = Arguments.createMap();
                    params.putString(sendEventToReactMessageKey, NEW_ROUTE_MESSAGE);
                    sendEvent(Objects.requireNonNull(application.getReactNativeHost().getReactInstanceManager().getCurrentReactContext()), "onError", params);
                })).start();
                //clear shared pref
                sendEventActionSharedPref.edit().remove(MESSAGE_KEY).apply();
            }
        });
    }
}
