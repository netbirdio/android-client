package com.netbirdlib;

import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import io.netbird.client.tool.DeviceName;
import io.netbird.client.tool.Preferences;
import io.netbird.client.tool.ServiceStateListener;
import io.netbird.client.tool.VPNService;
import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.Auth;
import io.netbird.gomobile.android.ConnectionListener;
import io.netbird.gomobile.android.ErrListener;
import io.netbird.gomobile.android.PeerInfo;
import io.netbird.gomobile.android.PeerInfoArray;
import io.netbird.gomobile.android.SSOListener;
import io.netbird.gomobile.android.URLOpener;

@ReactModule(name = NetbirdLibModule.NAME)
public class NetbirdLibModule extends ReactContextBaseJavaModule {
  public static final String NAME = "NetbirdLib";
  private RNBasicService.MyLocalBinder mBinder;
  @SuppressLint("StaticFieldLeak")
  private static ReactApplicationContext reactContext;

  public NetbirdLibModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    getReactApplicationContext().addLifecycleEventListener(lifecycleEventListener);
  }

  public Boolean mCustomTabOpened = false;
  URLOpener urlOpener = url -> {
    sendEvent(reactContext, "onWebViewOpen", url);
  };

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  LifecycleEventListener lifecycleEventListener = new LifecycleEventListener() {
    @Override
    public void onHostResume() {
      Log.d(NAME, "onResume");
      if (mCustomTabOpened) {
        mCustomTabOpened = false;
        Log.d(NAME, "CUSTOM TAB CLOSED");
        sendEvent(reactContext, "onCustomTabClose", null);
      }
    }

    @Override
    public void onHostPause() {
      Log.d(NAME, "onPause");
    }

    @Override
    public void onHostDestroy() {
      Log.d(NAME, "unBind");
      unbindFromServiceOnDestroy();
    }
  };

  @ReactMethod
  public void prepare(final Promise promise) {
    Activity currentActivity = getCurrentActivity();
    if (currentActivity == null) {
      promise.reject("E_ACTIVITY_DOES_NOT_EXIST", "Activity doesn't exist");
      return;
    }
    reactContext.addActivityEventListener(new BaseActivityEventListener() {
      public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == VPNService.VPN_REQUEST_CODE) {
          if (VPNService.isUsingAlwaysOnVPN(reactContext)) {
            sendVpnAlwaysOnEvent();
          }
          if (resultCode == Activity.RESULT_OK) {
            promise.resolve(null);
            mBinder.runEngine(urlOpener);
          } else {
            unbindFromServiceAfterCancel();
            promise.reject("PrepareError", "Failed to prepare");
          }
        }
      }
    });
  }

  @ReactMethod
  public void startService() {
    try{
    Log.d(NAME, "try to start service");
    Intent intent = new Intent(this.getCurrentActivity(), VPNService.class);
    intent.setAction(VPNService.INTENT_ACTION_START);
    reactContext.startService(intent);
    bindToService();
    } catch (NullPointerException exception) {
      Log.e(NAME, "startService: exception ->" + exception.getMessage());
    }
  }

  private void sendVpnAlwaysOnEvent() {
    new Thread(() -> runOnUiThread(() -> {
      WritableMap params = Arguments.createMap();
      params.putString("message", "You have a VPN app that has Always On turned on. To enable the NetBird app, you first need to go to your Network & Internet settings -> Advanced -> Check the gear icon for all the apps listed here on this page. Disable Always On for the apps that are not NetBird.");
      sendEvent(reactContext, "onError", params);
    })).start();
  }

  private void bindToService() throws NullPointerException {
    Log.d(NAME, "try to bind the service");
    Intent intent = new Intent(this.getCurrentActivity(), VPNService.class);
    getReactApplicationContext().bindService(intent, serviceIPC, Context.BIND_ABOVE_CLIENT);
  }

  private void unbindFromServiceOnDestroy() {
    if (mBinder == null) {
      return;
    }
    unBindFromService();
    mBinder = null;
  }

  private void unBindFromService() {
    mBinder.removeConnectionStateListener();
    mBinder.removeServiceStateListener(serviceStateListener);
    getReactApplicationContext().unbindService(serviceIPC);
  }

  private void unbindFromServiceAfterCancel() {
    if (mBinder == null) {
      return;
    }
    unBindFromService();
    startService();
  }

  private final ServiceConnection serviceIPC = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder binder) {
      Log.d(NAME, "on service connected");
      mBinder = (VPNService.MyLocalBinder) binder;
      mBinder.setConnectionStateListener(connectionListener);
      mBinder.addServiceStateListener(serviceStateListener);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
  };
  ServiceStateListener serviceStateListener = new ServiceStateListener() {
    public void onStarted() {
      Log.d(NAME, "STATE: ServiceStateListener connected");
      Activity currentActivity = getCurrentActivity();
      if (currentActivity == null) {
        return;
      }

      currentActivity.runOnUiThread(() -> {
        Log.d("ServiceStateListener", "onStarted");
        sendEvent(reactContext, "onStarted", null);
      });
    }

    public void onStopped() {
      Log.d(NAME, "STATE: ServiceStateListener stopped");
      Activity currentActivity = getCurrentActivity();
      if (currentActivity == null) {
        return;
      }

      currentActivity.runOnUiThread(() -> {
        Log.d("ServiceStateListener", "onStopped");
        sendEvent(reactContext, "onStopped", null);
      });
    }

    public void onError(String msg) {
      Log.d(NAME, "STATE: ServiceStateListener error");
      Activity currentActivity = getCurrentActivity();
      if (currentActivity == null) {
        return;
      }
      currentActivity.runOnUiThread(() -> {
        WritableMap params = Arguments.createMap();
        params.putString("message", msg);
        sendEvent(reactContext, "onError", params);
      });
    }
  };
  ConnectionListener connectionListener = new ConnectionListener() {
    @Override
    public void onAddressChanged(String fqdn, String ip) {
      UiThreadUtil.runOnUiThread(() -> {
        WritableMap params = Arguments.createMap();
        params.putString("domainName", fqdn);
        params.putString("ip", ip);
        sendEvent(reactContext, "onAddressChanged", params);
      });
    }

    public void onConnected() {
      Log.d(NAME, "STATE: ConnectionListener connected");
      Activity currentActivity = getCurrentActivity();
      if (currentActivity == null) {
        return;
      }

      currentActivity.runOnUiThread(() -> {
        sendEvent(reactContext, "onConnected", null);
      });
    }

    public void onConnecting() {
      Activity currentActivity = getCurrentActivity();
      if (currentActivity == null) {
        return;
      }

      currentActivity.runOnUiThread(() -> {
        sendEvent(reactContext, "onConnecting", null);
      });
    }

    public void onDisconnecting() {
      Activity currentActivity = getCurrentActivity();
      if (currentActivity == null) {
        return;
      }

      currentActivity.runOnUiThread(() -> {
        Log.d(NAME, "disconnecting...");
        sendEvent(reactContext, "onDisconnecting", null);
      });
    }

    public void onDisconnected() {
      Activity currentActivity = getCurrentActivity();
      if (currentActivity == null) {
        return;
      }

      currentActivity.runOnUiThread(() -> {
        Log.d(NAME, "STATE: ConnectionListener disconnected");
        sendEvent(reactContext, "onDisconnected", null);
      });
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onPeersListChanged(long numberOfPeers) {
      Activity currentActivity = getCurrentActivity();
      if (currentActivity == null) {
        return;
      }

      currentActivity.runOnUiThread(() -> {
        Log.d(NAME, "PEERS: " + numberOfPeers);

        WritableMap params = Arguments.createMap();
        params.putInt("count", (int) numberOfPeers);
        sendEvent(reactContext, "onPeersUpdateCount", params);

        updatePeerInfo();
      });
    }

    private void updatePeerInfo() {
      if (mBinder == null) {
        return;
      }

      PeerInfoArray peersInfo = mBinder.peersInfo();
      WritableArray peers = Arguments.createArray();

      for (int i = 0; i < peersInfo.size(); i++) {
        PeerInfo p = peersInfo.get(i);
        WritableMap peer = Arguments.createMap();
        peer.putString("domainName", p.getFQDN());
        peer.putString("ip", p.getIP());
        peer.putString("connStatus", p.getConnStatus());
        peers.pushMap(peer);
      }
      Log.d(NAME, "All Peers: " + String.valueOf(peers.size()));
      sendEvent(reactContext, "onPeersUpdate", peers);
    }
  };

  // Example method
  // See https://reactnative.dev/docs/native-modules-android
  @ReactMethod
  public void multiply(double a, double b, Promise promise) {
    promise.resolve(a * b + 15);
  }

  @ReactMethod
  private void switchConnection(boolean status) {
    try{
    if (!status) {
      mBinder.stopEngine();
      return;
    }
    if (mBinder.hasVpnPermission(getCurrentActivity())) {
      mBinder.runEngine(urlOpener);
    }
    }catch(NullPointerException exception){
      Log.e(NAME, "switchConnection: exception -> " + exception.getMessage());
    }
  }

  @ReactMethod
  private void cancelLogin() {
  }

  private enum ChangeServerStatusCode {
    NONE,
    SSO_IS_SUPPORTED,
    SSO_IS_NOT_SUPPORTED,
    CHECK_ERROR,
    CHANGED,
    CHANGED_ERROR,
  }

  @ReactMethod
  private void checkServer(String mgmServerAddress, Promise promise) {
    Log.d(NAME, "Check Server: " + mgmServerAddress);
    Handler handler = new Handler(reactContext.getMainLooper());
    handler.post(() -> {
      String configFilePath = Preferences.configFile(getCurrentActivity());
      Auth auther = null;
      try {
        auther = Android.newAuth(configFilePath, mgmServerAddress);
        auther.saveConfigIfSSOSupported(new SSOListener() {
          @Override
          public void onError(Exception e) {
            Log.e("Authenticator", "on error", e);
            promise.resolve(ChangeServerStatusCode.CHECK_ERROR.ordinal());
          }

          @Override
          public void onSuccess(boolean sso) {
            Log.d("Authenticator", "on success: " + sso);
            ChangeServerStatusCode status = null;
            mBinder.stopEngine();
            if (sso) {
              status = ChangeServerStatusCode.SSO_IS_SUPPORTED;
            } else {
              status = ChangeServerStatusCode.SSO_IS_NOT_SUPPORTED;
            }
            promise.resolve(status.ordinal());
          }
        });
      } catch (Exception e) {
        promise.resolve(ChangeServerStatusCode.CHECK_ERROR.ordinal());
      }
    });
  }

  @ReactMethod
  private void changeServer(String mgmServerAddress, String setupKey, Promise promise) {
    Handler handler = new Handler(reactContext.getMainLooper());
    handler.post(() -> {
      String configFilePath = Preferences.configFile(reactContext);
      Auth auther = null;
      try {
        auther = Android.newAuth(configFilePath, mgmServerAddress);
        auther.loginWithSetupKeyAndSaveConfig(new ErrListener() {
          @Override
          public void onError(Exception e) {
            promise.resolve(ChangeServerStatusCode.CHANGED_ERROR.ordinal());
          }

          @Override
          public void onSuccess() {
            mBinder.stopEngine();
            promise.resolve(ChangeServerStatusCode.CHANGED.ordinal());
          }
        }, setupKey, DeviceName.getDeviceName());
      } catch (Exception e) {
        promise.resolve(ChangeServerStatusCode.CHANGED_ERROR.ordinal());
      }
    });
  }

  @ReactMethod
  private void setPreSharedKey(String key, Promise promise) {
    String configFilePath = Preferences.configFile(getCurrentActivity());
    io.netbird.gomobile.android.Preferences preferences = new io.netbird.gomobile.android.Preferences(configFilePath);
    preferences.setPreSharedKey(key);
    try {
      preferences.commit();
      promise.resolve(true);
    } catch (Exception e) {
      promise.reject(e);
    }
  }

  @ReactMethod
  private void inUsePreSharedKey(Promise promise) {
    Activity activity = getCurrentActivity();
    if (activity != null) {
      String configFilePath = Preferences.configFile(getCurrentActivity());
      io.netbird.gomobile.android.Preferences preferences = new io.netbird.gomobile.android.Preferences(configFilePath);
      try {
        String hashKey = preferences.getPreSharedKey();
        if (hashKey.trim().isEmpty()) {
          promise.resolve(false);
          return;
        }
        promise.resolve(true);
      } catch (Exception e) {
        promise.reject(e);
      }
    }
  }

  @ReactMethod
  private void isTraceLogEnabled(Promise promise) {
    Activity activity = getCurrentActivity();
    if (activity == null) {
      return;
    }

    Preferences pref = new Preferences(activity);
    promise.resolve(pref.isTraceLogEnabled());
  }

  @ReactMethod
  private void enableTraceLog() {
    Activity activity = getCurrentActivity();
    if (activity == null) {
      return;
    }

    Preferences pref = new Preferences(activity);
    pref.enableTraceLog();
  }

  @ReactMethod
  private void disableTraceLog() {
    Activity activity = getCurrentActivity();
    if (activity == null) {
      return;
    }

    Preferences pref = new Preferences(activity);
    pref.disableTraceLog();
  }

  private void sendEvent(ReactContext reactContext,
      String eventName,
      @Nullable Object params) {

    try {
      DeviceEventManagerModule.RCTDeviceEventEmitter jsm = reactContext
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);
      if (jsm != null) {
        jsm.emit(eventName, params);
      }
    } catch (Error e) {
      Log.d(NAME, "Error: " + e.getMessage());
    }
  }
}
