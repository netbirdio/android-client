package com.netbirdlib;

import android.app.Activity;
import android.content.Intent;

import com.facebook.react.bridge.ActivityEventListener;

/** An empty implementation of {@link ActivityEventListener} */
public class BaseActivityEventListener implements ActivityEventListener {

  /** @deprecated use {@link #onActivityResult(Activity, int, int, Intent)} instead. */
  @Deprecated
  public void onActivityResult(int requestCode, int resultCode, Intent data) {}

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {}

  @Override
  public void onNewIntent(Intent intent) {}


}
