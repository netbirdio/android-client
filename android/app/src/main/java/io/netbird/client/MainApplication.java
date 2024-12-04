package io.netbird.client;

import android.app.Application;
import android.content.IntentFilter;

import com.facebook.react.PackageList;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactPackage;
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint;
import com.facebook.react.defaults.DefaultReactNativeHost;
import com.facebook.soloader.SoLoader;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.tool.NetworkChangeNotifier;

public class MainApplication extends Application implements ReactApplication {
    private final NotificationReceiver notificationReceiver = new NotificationReceiver();

    private ArrayList<Class> runningActivities = new ArrayList<>();

    public void addActivityToStack (Class cls) {
        if (!runningActivities.contains(cls)) runningActivities.add(cls);
    }

    public void removeActivityFromStack (Class cls) {
        if (runningActivities.contains(cls)) runningActivities.remove(cls);
    }

    public boolean isActivityInBackStack (Class cls) {
        return runningActivities.contains(cls);
    }

    private final ReactNativeHost mReactNativeHost =
            new DefaultReactNativeHost(this) {
                @Override
                public boolean getUseDeveloperSupport() {
                    return BuildConfig.DEBUG;
                }

                @Override
                protected List<ReactPackage> getPackages() {
                    @SuppressWarnings("UnnecessaryLocalVariable")
                    List<ReactPackage> packages = new PackageList(this).getPackages();
                    // Packages that cannot be autolinked yet can be added manually here, for example:
                    // packages.add(new MyReactNativePackage());
                    return packages;
                }

                @Override
                protected String getJSMainModuleName() {
                    return "index";
                }

                @Override
                protected boolean isNewArchEnabled() {
                    return BuildConfig.IS_NEW_ARCHITECTURE_ENABLED;
                }

                @Override
                protected Boolean isHermesEnabled() {
                    return BuildConfig.IS_HERMES_ENABLED;
                }
            };

    @Override
    public ReactNativeHost getReactNativeHost() {
        return mReactNativeHost;
    }

    private void registerNotificationReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(NetworkChangeNotifier.action);
        registerReceiver(notificationReceiver, filter, RECEIVER_EXPORTED);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerNotificationReceiver();
        SoLoader.init(this, /* native exopackage */ false);
        if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
            // If you opted-in for the New Architecture, we load the native entry point for this app.
            DefaultNewArchitectureEntryPoint.load();
        }
        ReactNativeFlipper.initializeFlipper(this, getReactNativeHost().getReactInstanceManager());
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        unregisterReceiver(notificationReceiver);
    }
}
