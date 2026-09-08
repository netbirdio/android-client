package io.netbird.client.ui.home;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import io.netbird.client.R;
import io.netbird.client.databinding.LayoutZeroPeerBinding;

class ZeroPeerView {
    private static final String LOGTAG = "ZeroPeerView";

    public static void setupLearnWhyClick(LayoutZeroPeerBinding binding, Context context) {
        binding.btnLearnWhy.setOnClickListener(v -> {
            String url = "https://docs.netbird.io/how-to/manage-network-access";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.w(LOGTAG, "No browser available to open learn-why URL", e);
                Toast.makeText(context,
                        context.getString(R.string.settings_no_browser, url),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    public static void updateVisibility(LayoutZeroPeerBinding zeroPeerBinding, View payloadView, Boolean show) {
        if (show) {
            zeroPeerBinding.getRoot().setVisibility(View.GONE);
            payloadView.setVisibility(View.VISIBLE);
        } else {
            zeroPeerBinding.getRoot().setVisibility(View.VISIBLE);
            payloadView.setVisibility(View.GONE);
        }
    }
}
