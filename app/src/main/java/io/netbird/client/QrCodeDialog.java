package io.netbird.client;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class QrCodeDialog extends DialogFragment {

    private static final String ARG_URL = "url";
    private static final String ARG_USER_CODE = "userCode";

    private static OnDialogDismissed dismissCallback;

    public interface OnDialogDismissed {
        void onDismissed();
    }

    public static QrCodeDialog newInstance(String url, String userCode, OnDialogDismissed callback) {
        QrCodeDialog fragment = new QrCodeDialog();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putString(ARG_USER_CODE, userCode);
        fragment.setArguments(args);
        dismissCallback = callback;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_qr_code, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView qrCodeImageView = view.findViewById(R.id.qr_code_image_view);
        TextView userCodeTextView = view.findViewById(R.id.user_code_text_view);
        Button closeButton = view.findViewById(R.id.close_button);

        String url = getArguments().getString(ARG_URL);
        String userCode = getArguments().getString(ARG_USER_CODE);

        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(url, BarcodeFormat.QR_CODE, 400, 400);
            qrCodeImageView.setImageBitmap(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Show user code if it exists. Needs testing on real device, emulator is not getting a Device Code
        if (userCode != null && !userCode.isEmpty()) {
            userCodeTextView.setText(getString(R.string.device_code, userCode));
            userCodeTextView.setVisibility(View.VISIBLE);
        } else {
            userCodeTextView.setVisibility(View.GONE);
        }

        closeButton.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (dismissCallback != null) {
            dismissCallback.onDismissed();
            dismissCallback = null;
        }
    }
}
