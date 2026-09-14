package io.netbird.client.ui.apps;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.netbird.client.R;
import io.netbird.client.databinding.FragmentAppsBinding;
import io.netbird.client.ui.SegmentedSwitch;

/**
 * Hosts SSH and Files as two halves of one screen. Both are their own
 * fragments, unchanged and still reachable on their own; this only decides
 * which of the two is showing, so the bottom navigation spends a single slot
 * on them.
 */
public class AppsFragment extends Fragment {

    private static final String STATE_SHOWING_FILES = "showing_files";

    private FragmentAppsBinding binding;
    private boolean showingFiles;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAppsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SegmentedSwitch segments = new SegmentedSwitch(view, R.id.toggle_ssh_files,
                R.id.segment_thumb, R.id.btn_view_ssh, R.id.label_view_ssh,
                R.id.btn_view_files, R.id.label_view_files, this::showFiles);

        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_SHOWING_FILES)) {
            segments.selectSilently(true);
            showFiles(true);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SHOWING_FILES, showingFiles);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void showFiles(boolean files) {
        if (binding == null) {
            return;
        }
        showingFiles = files;
        binding.sshContainer.setVisibility(files ? View.GONE : View.VISIBLE);
        binding.filesContainer.setVisibility(files ? View.VISIBLE : View.GONE);
    }
}
