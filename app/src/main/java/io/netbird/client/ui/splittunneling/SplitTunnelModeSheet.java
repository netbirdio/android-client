package io.netbird.client.ui.splittunneling;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.netbird.client.databinding.SheetSplitTunnelModeBinding;
import io.netbird.client.tool.SplitTunnelConfig;

public class SplitTunnelModeSheet extends BottomSheetDialogFragment {

    public interface OnModeChangedListener {
        void onModeChanged(SplitTunnelConfig.Mode mode);
    }

    private static final String ARG_MODE = "mode";

    private SheetSplitTunnelModeBinding binding;

    public static SplitTunnelModeSheet newInstance(SplitTunnelConfig.Mode current) {
        SplitTunnelModeSheet sheet = new SplitTunnelModeSheet();
        Bundle args = new Bundle();
        args.putString(ARG_MODE, current.name());
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetSplitTunnelModeBinding.inflate(inflater, container, false);

        binding.modeRowOff.setOnClickListener(v -> pick(SplitTunnelConfig.Mode.OFF));
        binding.modeRowExclude.setOnClickListener(v -> pick(SplitTunnelConfig.Mode.EXCLUDE));
        binding.modeRowInclude.setOnClickListener(v -> pick(SplitTunnelConfig.Mode.INCLUDE));

        showCheckmarkFor(currentMode());
        return binding.getRoot();
    }

    private SplitTunnelConfig.Mode currentMode() {
        Bundle args = getArguments();
        if (args == null) {
            return SplitTunnelConfig.Mode.OFF;
        }
        try {
            return SplitTunnelConfig.Mode.valueOf(args.getString(ARG_MODE, SplitTunnelConfig.Mode.OFF.name()));
        } catch (IllegalArgumentException e) {
            return SplitTunnelConfig.Mode.OFF;
        }
    }

    private void showCheckmarkFor(SplitTunnelConfig.Mode mode) {
        binding.modeCheckOff.setVisibility(mode == SplitTunnelConfig.Mode.OFF ? View.VISIBLE : View.INVISIBLE);
        binding.modeCheckExclude.setVisibility(mode == SplitTunnelConfig.Mode.EXCLUDE ? View.VISIBLE : View.INVISIBLE);
        binding.modeCheckInclude.setVisibility(mode == SplitTunnelConfig.Mode.INCLUDE ? View.VISIBLE : View.INVISIBLE);
    }

    private void pick(SplitTunnelConfig.Mode mode) {
        if (getParentFragment() instanceof OnModeChangedListener) {
            ((OnModeChangedListener) getParentFragment()).onModeChanged(mode);
        }
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
