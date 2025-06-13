package io.netbird.client.ui.home;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import io.netbird.client.databinding.LayoutZeroPeerBinding;

class ZeroPeerView {
    public static void setupLearnWhyClick(LayoutZeroPeerBinding binding, Context context) {
        binding.btnLearnWhy.setOnClickListener(v -> {
            String url = "https://docs.netbird.io/how-to/manage-network-access";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(intent);
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
