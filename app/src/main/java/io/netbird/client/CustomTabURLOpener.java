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

    /** Written from a Go thread, read from the main thread and vice versa. */
    private volatile boolean isOpened = false;

    public interface OnCustomTabResult {
        void onClosed();
    }

    public CustomTabURLOpener(AppCompatActivity activity,  OnCustomTabResult resultCallback) {
        this.context = activity;

        this.customTabLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), o -> {
                    isOpened = false;
                    resultCallback.onClosed();
                }
        );
    }

    public boolean isOpened() {
        return isOpened;
    }

    @Override
    public void onLoginSuccess() {
        Log.d(TAG, "onLoginSuccess fired, isOpened=" + isOpened);

        if (!isOpened) {
            return;
        }
        // isOpened stays set: MainActivity.onStop uses it to keep the service
        // bound while the SSO surface is in front, and the launcher callback
        // clears it when the tab actually goes away. The Custom Tab is a
        // separate task, so it is dismissed by bringing this activity forward
        // rather than by closing it directly.
        context.runOnUiThread(() -> {
            Intent i = new Intent(this.context, MainActivity.class);
            i.setAction(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            this.context.startActivity(i);
        });
    }

    @Override
    public void open(String url, String userCode) {
        // Set before posting, not inside the post: callers invoke this from a Go
        // thread and may report success straight after, and onLoginSuccess does
        // nothing unless the surface is already marked as opened.
        isOpened = true;
        // launch() drives an activity result contract, which has to run on the
        // main thread; a Go thread would otherwise raise a wrong-thread error.
        context.runOnUiThread(() -> {
            try {
                CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                Intent intent = customTabsIntent.intent;
                intent.setData(Uri.parse(url));
                customTabLauncher.launch(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch CustomTab: " + e.getMessage());
                isOpened = false;
                if (context instanceof OnCustomTabResult) {
                    ((OnCustomTabResult) context).onClosed();
                }
            }
        });
    }
}