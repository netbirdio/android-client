package io.netbird.client;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import io.netbird.gomobile.android.URLOpener;

public class CustomTabURLOpener implements URLOpener {
    private static final String TAG = "CustomTabURLOpener";
    private final AppCompatActivity context;
    private final ActivityResultLauncher<Intent> customTabLauncher;

    private boolean isOpened = false;

    public interface OnCustomTabResult {
        void onClosed();
    }

    public CustomTabURLOpener(AppCompatActivity activity,  OnCustomTabResult resultCallback) {
        this.context = activity;

        this.customTabLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult o) {
                        isOpened = false;
                        resultCallback.onClosed();
                    }
                }
        );
    }

    public boolean isOpened() {
        return isOpened;
    }

    @Override
    public void onLoginSuccess() {
        Log.d(TAG, "onLoginSuccess fired.");

        if (isOpened) {
            Intent i = new Intent(this.context, MainActivity.class);
            i.setAction(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            this.context.startActivity(i);
        }
    }

    @Override
    public void open(String url, String userCode) {
        isOpened = true;
        try {
            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
            Intent intent = customTabsIntent.intent;
            intent.setData(Uri.parse(url));
            customTabLauncher.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch CustomTab: " + e.getMessage());
            if (context instanceof OnCustomTabResult) {
                ((OnCustomTabResult) context).onClosed();
            }
        }
    }
}