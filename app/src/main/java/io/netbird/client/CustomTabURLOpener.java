package io.netbird.client;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import io.netbird.gomobile.android.URLOpener;

public class CustomTabURLOpener implements URLOpener {
    private final AppCompatActivity context;
    private final ActivityResultLauncher<Intent> customTabLauncher;


    public interface OnCustomTabResult {
        void onSuccess();
    }

    public CustomTabURLOpener(AppCompatActivity activity,  OnCustomTabResult resultCallback) {
        this.context = activity;

        this.customTabLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),

                result -> {
                    resultCallback.onSuccess();

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
                ((OnCustomTabResult) context).onSuccess();
            }
        }
    }
}