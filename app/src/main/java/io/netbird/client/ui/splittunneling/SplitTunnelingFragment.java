package io.netbird.client.ui.splittunneling;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.netbird.client.R;
import io.netbird.client.databinding.FragmentSplitTunnelingBinding;
import io.netbird.client.tool.Preferences;
import io.netbird.client.tool.Preferences.SplitTunnelingMode;

public class SplitTunnelingFragment extends Fragment {

    private FragmentSplitTunnelingBinding binding;
    private SplitTunnelingAdapter adapter;
    private Preferences preferences;
    private List<AppInfo> allApps = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSplitTunnelingBinding.inflate(inflater, container, false);
        preferences = new Preferences(requireContext());
        setupUI();
        loadApps();
        return binding.getRoot();
    }

    private void setupUI() {
        adapter = new SplitTunnelingAdapter(app -> {
            app.isSelected = !app.isSelected;
            saveApps();
            adapter.notifyDataSetChanged();
        });

        binding.recyclerApps.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerApps.setAdapter(adapter);

        Preferences.SplitTunnelingMode mode = preferences.getSplitTunnelingMode();
        switch (mode) {
            case NONE:
                binding.radioGroupMode.check(R.id.radio_mode_none);
                break;
            case EXCLUDE:
                binding.radioGroupMode.check(R.id.radio_mode_exclude);
                break;
            case INCLUDE:
                binding.radioGroupMode.check(R.id.radio_mode_include);
                break;
        }

        binding.radioGroupMode.setOnCheckedChangeListener((group, checkedId) -> {
            Preferences.SplitTunnelingMode newMode;
            if (checkedId == R.id.radio_mode_none) {
                newMode = Preferences.SplitTunnelingMode.NONE;
            } else if (checkedId == R.id.radio_mode_exclude) {
                newMode = Preferences.SplitTunnelingMode.EXCLUDE;
            } else if (checkedId == R.id.radio_mode_include) {
                newMode = Preferences.SplitTunnelingMode.INCLUDE;
            } else {
                newMode = Preferences.SplitTunnelingMode.NONE;
            }
            preferences.setSplitTunnelingMode(newMode);
        });

        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadApps() {
        PackageManager pm = requireContext().getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        Set<String> selectedApps = preferences.getSplitTunnelingApps();
        String myPackageName = requireContext().getPackageName();

        allApps = apps.stream()
                .filter(app -> !app.packageName.equals(myPackageName))
                .map(app -> {
                    String name = pm.getApplicationLabel(app).toString();
                    Drawable icon = pm.getApplicationIcon(app);
                    return new AppInfo(name, app.packageName, icon, selectedApps.contains(app.packageName));
                })
                .sorted((a, b) -> a.name.compareToIgnoreCase(b.name))
                .collect(Collectors.toList());

        adapter.setApps(allApps);
    }

    private void saveApps() {
        Set<String> selectedApps = allApps.stream()
                .filter(app -> app.isSelected)
                .map(app -> app.packageName)
                .collect(Collectors.toSet());
        preferences.setSplitTunnelingApps(selectedApps);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
