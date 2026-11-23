package io.netbird.client.ui;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.sidesheet.SideSheetDialog;

import io.netbird.client.R;

/**
 * Bottom sheet in portrait, side sheet in landscape. On a 16:9 landscape screen a bottom
 * sheet's automatic peek height falls back to its 64dp minimum, so on a TV only the title
 * is visible and the options sit below the screen edge.
 */
public class AdaptiveSheetDialogFragment extends BottomSheetDialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (getResources().getBoolean(R.bool.sheet_on_side)) {
            return new SideSheetDialog(requireContext(), getTheme());
        }
        return super.onCreateDialog(savedInstanceState);
    }
}
