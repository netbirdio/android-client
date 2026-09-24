package io.netbird.client.ui.files;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.netbird.client.R;
import io.netbird.client.databinding.FragmentFileSharingBinding;
import io.netbird.client.tool.files.FileDropManager;
import io.netbird.gomobile.android.Android;

/**
 * The receiving policy of the active profile: whether incoming offers are
 * refused, prompted for, or accepted outright, plus where files land. The
 * transfer log itself is the Files tab, not this screen.
 */
public class FileSharingFragment extends Fragment {

    private FragmentFileSharingBinding binding;

    // Set while the radio group is being populated from Go, so the resulting
    // check callbacks are not mistaken for the user's own choice.
    private boolean applyingMode;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFileSharingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (applyingMode) {
                return;
            }
            FileDropManager.get().setMode(modeOf(checkedId), this::report);
        });

        load();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void load() {
        FileDropManager.get().mode(mode -> onUiThread(() -> {
            if (binding == null) {
                return;
            }
            applyingMode = true;
            binding.modeGroup.check(checkIdOf(mode));
            applyingMode = false;
        }));

        FileDropManager.get().destinationDir(dir -> onUiThread(() -> {
            if (binding != null) {
                binding.destinationValue.setText(dir);
            }
        }));
    }

    private long modeOf(int checkedId) {
        if (checkedId == R.id.mode_off) {
            return Android.FileDropModeOff;
        }
        if (checkedId == R.id.mode_auto) {
            return Android.FileDropModeAutoAccept;
        }
        return Android.FileDropModeAsk;
    }

    private int checkIdOf(long mode) {
        if (mode == Android.FileDropModeOff) {
            return R.id.mode_off;
        }
        if (mode == Android.FileDropModeAutoAccept) {
            return R.id.mode_auto;
        }
        return R.id.mode_ask;
    }

    private void report(boolean ok, @Nullable String error) {
        if (ok) {
            return;
        }
        onUiThread(() -> {
            if (binding != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Manager callbacks come off its executor; view work has to go back. */
    private void onUiThread(Runnable action) {
        View root = getView();
        if (root != null) {
            root.post(action);
        }
    }
}
