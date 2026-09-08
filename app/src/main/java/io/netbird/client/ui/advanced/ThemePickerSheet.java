package io.netbird.client.ui.advanced;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.netbird.client.databinding.SheetThemePickerBinding;

public class ThemePickerSheet extends BottomSheetDialogFragment {

    public interface OnThemeChangedListener {
        void onThemeChanged(int mode);
    }

    private SheetThemePickerBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetThemePickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedPreferences prefs = requireContext().getSharedPreferences("settings", 0);
        int currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        renderChecks(currentMode);

        binding.themeRowSystem.setOnClickListener(v -> pick(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        binding.themeRowLight.setOnClickListener(v -> pick(AppCompatDelegate.MODE_NIGHT_NO));
        binding.themeRowDark.setOnClickListener(v -> pick(AppCompatDelegate.MODE_NIGHT_YES));
    }

    private void renderChecks(int mode) {
        binding.themeCheckSystem.setVisibility(mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM ? View.VISIBLE : View.INVISIBLE);
        binding.themeCheckLight.setVisibility(mode == AppCompatDelegate.MODE_NIGHT_NO ? View.VISIBLE : View.INVISIBLE);
        binding.themeCheckDark.setVisibility(mode == AppCompatDelegate.MODE_NIGHT_YES ? View.VISIBLE : View.INVISIBLE);

        applyRowAccessibility(binding.themeRowSystem, mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                getString(io.netbird.client.R.string.advanced_theme_system));
        applyRowAccessibility(binding.themeRowLight, mode == AppCompatDelegate.MODE_NIGHT_NO,
                getString(io.netbird.client.R.string.advanced_theme_light));
        applyRowAccessibility(binding.themeRowDark, mode == AppCompatDelegate.MODE_NIGHT_YES,
                getString(io.netbird.client.R.string.advanced_theme_dark));
    }

    private void applyRowAccessibility(View row, boolean selected, String label) {
        row.setContentDescription(label);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            row.setStateDescription(selected ? getString(io.netbird.client.R.string.theme_picker_selected) : null);
        }
        row.setSelected(selected);
    }

    private void pick(int mode) {
        SharedPreferences prefs = requireContext().getSharedPreferences("settings", 0);
        prefs.edit().putInt("theme_mode", mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
        if (binding != null) {
            binding.getRoot().announceForAccessibility(
                    getString(io.netbird.client.R.string.theme_picker_announce, labelFor(requireContext(), mode)));
        }
        if (getParentFragment() instanceof OnThemeChangedListener) {
            ((OnThemeChangedListener) getParentFragment()).onThemeChanged(mode);
        }
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    static String labelFor(@NonNull android.content.Context ctx, int mode) {
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) {
            return ctx.getString(io.netbird.client.R.string.advanced_theme_light);
        }
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) {
            return ctx.getString(io.netbird.client.R.string.advanced_theme_dark);
        }
        return ctx.getString(io.netbird.client.R.string.advanced_theme_system);
    }
}
