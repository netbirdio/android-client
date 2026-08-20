package io.netbird.client.ui.splittunneling;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.HashSet;
import java.util.Set;

import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.databinding.FragmentSplitTunnelingBinding;
import io.netbird.client.tool.Preferences;
import io.netbird.client.tool.SplitTunnelConfig;

/**
 * Lets the user say which applications the tunnel carries.
 *
 * Every change is written straight through and handed to the service, which
 * rebuilds the tunnel in place — there is no save button and no reconnection to
 * ask for.
 */
public class SplitTunnelingFragment extends Fragment
        implements SplitTunnelModeSheet.OnModeChangedListener, AppListAdapter.OnAppToggledListener {

    private FragmentSplitTunnelingBinding binding;
    private SplitTunnelingViewModel viewModel;
    private AppListAdapter adapter;
    private Preferences preferences;
    private ServiceAccessor serviceAccessor;

    private SplitTunnelConfig.Mode mode = SplitTunnelConfig.Mode.OFF;
    private final Set<String> excluded = new HashSet<>();
    private final Set<String> included = new HashSet<>();

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ServiceAccessor) {
            serviceAccessor = (ServiceAccessor) context;
        } else {
            throw new RuntimeException(context + " must implement ServiceAccessor");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSplitTunnelingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        preferences = new Preferences(requireContext());
        SplitTunnelConfig stored = preferences.getSplitTunnelConfig();
        mode = stored.getMode();
        excluded.addAll(stored.getExcluded());
        included.addAll(stored.getIncluded());

        adapter = new AppListAdapter(activeSelection(), this);
        binding.appsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.appsRecyclerView.setAdapter(adapter);

        binding.rowMode.setOnClickListener(v ->
                SplitTunnelModeSheet.newInstance(mode).show(getChildFragmentManager(), "split_tunnel_mode"));

        binding.searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filterBySearchQuery(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        viewModel = new ViewModelProvider(this).get(SplitTunnelingViewModel.class);
        viewModel.getApps().observe(getViewLifecycleOwner(), apps -> {
            pruneUninstalled(apps);
            adapter.submitApps(apps);
            binding.loadingIndicator.setVisibility(View.GONE);
        });
        viewModel.loadApps();

        renderMode();
    }

    @Override
    public void onModeChanged(SplitTunnelConfig.Mode newMode) {
        if (newMode == mode) {
            return;
        }
        mode = newMode;
        // Both selections are kept, so switching back and forth does not make the
        // user pick their apps again.
        save();
        adapter.setSelected(activeSelection());
        renderMode();
    }

    @Override
    public void onAppToggled(String packageName, boolean selected) {
        Set<String> selection = activeSelection();
        if (selected) {
            selection.add(packageName);
        } else {
            selection.remove(packageName);
        }
        save();
        renderWarning();
    }

    private Set<String> activeSelection() {
        return mode == SplitTunnelConfig.Mode.INCLUDE ? included : excluded;
    }

    private void save() {
        preferences.saveSplitTunnelConfig(new SplitTunnelConfig(mode, excluded, included));
        serviceAccessor.applySplitTunneling();
    }

    /**
     * Drops packages that were picked and later uninstalled. The tunnel already
     * ignores them, but leaving them in storage would silently re-apply them if
     * the app were installed again.
     */
    private void pruneUninstalled(java.util.List<AppEntry> apps) {
        Set<String> installed = new HashSet<>();
        for (AppEntry app : apps) {
            installed.add(app.getPackageName());
        }

        boolean changed = excluded.retainAll(installed);
        changed |= included.retainAll(installed);
        if (changed) {
            preferences.saveSplitTunnelConfig(new SplitTunnelConfig(mode, excluded, included));
        }
    }

    private void renderMode() {
        int label;
        if (mode == SplitTunnelConfig.Mode.EXCLUDE) {
            label = R.string.split_tunneling_mode_exclude;
        } else if (mode == SplitTunnelConfig.Mode.INCLUDE) {
            label = R.string.split_tunneling_mode_include;
        } else {
            label = R.string.split_tunneling_mode_off;
        }
        binding.currentModeName.setText(label);

        boolean listUsable = mode != SplitTunnelConfig.Mode.OFF;
        binding.searchView.setEnabled(listUsable);
        binding.appsRecyclerView.setVisibility(listUsable ? View.VISIBLE : View.GONE);
        binding.modeOffHint.setVisibility(listUsable ? View.GONE : View.VISIBLE);

        renderWarning();
    }

    private void renderWarning() {
        boolean emptyAllowlist = mode == SplitTunnelConfig.Mode.INCLUDE && included.isEmpty();
        binding.emptyIncludeWarning.setVisibility(emptyAllowlist ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
