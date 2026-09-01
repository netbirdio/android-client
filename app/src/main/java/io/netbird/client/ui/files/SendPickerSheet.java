package io.netbird.client.ui.files;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.netbird.client.R;
import io.netbird.client.databinding.SheetSendPickerBinding;

/**
 * Asks what to send: a file from the document picker, or whatever text sits on
 * the clipboard. The clipboard row previews its content up front, so nothing
 * is ever sent blind, and the peer picker shows the full text once more before
 * the actual send.
 */
public class SendPickerSheet extends BottomSheetDialogFragment {

    interface Listener {
        void onPickFile();
    }

    private SheetSendPickerBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetSendPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.rowSendFile.setOnClickListener(v -> {
            Listener listener = (Listener) getParentFragment();
            dismiss();
            if (listener != null) {
                listener.onPickFile();
            }
        });

        String text = clipboardText();
        if (text == null) {
            binding.clipboardPreview.setText(R.string.file_drop_clipboard_empty);
            binding.rowSendClipboard.setEnabled(false);
            binding.rowSendClipboard.setAlpha(0.4f);
            return;
        }

        binding.clipboardPreview.setText(previewOf(text));
        binding.rowSendClipboard.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), ShareTargetActivity.class);
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(intent);
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Nullable
    private String clipboardText() {
        ClipboardManager clipboard =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return null;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(requireContext());
        if (text == null || text.toString().trim().isEmpty()) {
            return null;
        }
        return text.toString();
    }

    /** The first non-empty line stands in for the whole snippet. */
    private static String previewOf(String text) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return text.trim();
    }
}
