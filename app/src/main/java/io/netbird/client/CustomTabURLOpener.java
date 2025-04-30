package io.netbird.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import io.netbird.gomobile.android.URLOpener;

public class CustomTabURLOpener implements URLOpener {
    private final Context context;
    private final ActivityResultLauncher<Intent> customTabLauncher;

    public interface OnCustomTabResult {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public CustomTabURLOpener(AppCompatActivity activity, OnCustomTabResult resultCallback) {
        this.context = activity;

        // Register the activity result launcher
        this.customTabLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // This will be called when the CustomTab is closed
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // Success case
                        resultCallback.onSuccess();
                    } else {
                        // Failure or cancellation case
                        resultCallback.onFailure("CustomTab was closed without completion");
                    }
                }
        );
    }

    @Override
    public void open(String url) {
        try {
            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
            Intent intent = customTabsIntent.intent;
            intent.setData(Uri.parse(url));
            customTabLauncher.launch(intent);
        } catch (Exception e) {
            Log.e("CustomTabURLOpener", "Failed to launch CustomTab: " + e.getMessage());
            if (context instanceof OnCustomTabResult) {
                ((OnCustomTabResult) context).onFailure("Failed to launch: " + e.getMessage());
            }
        }
    }
}