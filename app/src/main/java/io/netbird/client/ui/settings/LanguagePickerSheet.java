package io.netbird.client.ui.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

import io.netbird.client.R;
import io.netbird.client.databinding.ListItemLanguageBinding;
import io.netbird.client.databinding.SheetLanguagePickerBinding;

public class LanguagePickerSheet extends BottomSheetDialogFragment {

    // Same languages as the desktop app (client/ui/i18n/locales/_index.json),
    // shown by their native names. An empty tag means "follow the OS language".
    private static final String[][] LANGUAGES = {
            {"en", "English (US)"},
            {"de", "Deutsch"},
            {"hu", "Magyar"},
            {"ru", "Русский"},
            {"es", "Español"},
            {"fr", "Français"},
            {"it", "Italiano"},
            {"pt", "Português"},
            {"zh-CN", "简体中文"},
            {"ja", "日本語"},
    };

    private SheetLanguagePickerBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetLanguagePickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LocaleListCompat current = AppCompatDelegate.getApplicationLocales();

        addRow("", getString(R.string.language_system_default), current.isEmpty());
        for (String[] language : LANGUAGES) {
            addDivider();
            addRow(language[0], language[1], isSelected(current, language[0]));
        }
    }

    private void addRow(String tag, String label, boolean selected) {
        ListItemLanguageBinding row = ListItemLanguageBinding.inflate(getLayoutInflater(), binding.languageContainer, false);
        row.languageName.setText(label);
        row.languageCheck.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        row.getRoot().setSelected(selected);
        row.getRoot().setContentDescription(label);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            row.getRoot().setStateDescription(selected ? getString(R.string.theme_picker_selected) : null);
        }
        row.getRoot().setOnClickListener(v -> pick(tag, label));
        binding.languageContainer.addView(row.getRoot());
    }

    private void addDivider() {
        getLayoutInflater().inflate(R.layout.list_item_setting_divider, binding.languageContainer, true);
    }

    private void pick(String tag, String label) {
        if (binding != null) {
            binding.getRoot().announceForAccessibility(getString(R.string.language_picker_announce, label));
        }
        LocaleListCompat locales = tag.isEmpty()
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(tag);
        // Persisted by AppCompat (autoStoreLocales) and recreates started activities.
        AppCompatDelegate.setApplicationLocales(locales);
        dismiss();
    }

    private static boolean isSelected(LocaleListCompat current, String tag) {
        if (current.isEmpty()) {
            return false;
        }
        Locale locale = current.get(0);
        if (locale == null) {
            return false;
        }
        Locale wanted = Locale.forLanguageTag(tag);
        if (!wanted.getLanguage().equals(locale.getLanguage())) {
            return false;
        }
        return wanted.getCountry().isEmpty() || wanted.getCountry().equals(locale.getCountry());
    }

    static String currentLanguageLabel(@NonNull Context context) {
        LocaleListCompat current = AppCompatDelegate.getApplicationLocales();
        if (current.isEmpty()) {
            return context.getString(R.string.language_system_default);
        }
        for (String[] language : LANGUAGES) {
            if (isSelected(current, language[0])) {
                return language[1];
            }
        }
        Locale locale = current.get(0);
        return locale == null ? context.getString(R.string.language_system_default) : locale.getDisplayName(locale);
    }
}
