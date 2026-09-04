package io.netbird.client.ui.fistinstall;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.netbird.client.PlatformUtils;
import io.netbird.client.R;
import io.netbird.client.databinding.FragmentFirstinstallBinding;
import io.netbird.client.tool.ProfileManagerWrapper;
import io.netbird.client.ui.PreferenceUI;
import io.netbird.client.ui.server.ManagementServerSwitch;
import io.netbird.client.ui.server.ManagementUrl;
import io.netbird.client.ui.server.SetupKeySection;
import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.Auth;
import io.netbird.gomobile.android.ErrListener;
import io.netbird.gomobile.android.Preferences;

/**
 * First-run screen. Mirrors the desktop's welcome management step: the user
 * either continues straight into NetBird Cloud or picks a self-hosted server
 * before the app is used for the first time.
 *
 * <p>Unlike the desktop it also offers setup-key enrolment, since mobile
 * devices are commonly provisioned that way.
 */
public class FirstInstallFragment extends Fragment {

    private static final String TAG = "FirstInstallFragment";

    private FragmentFirstinstallBinding binding;
    private ManagementServerSwitch serverSwitch;
    private SetupKeySection setupKeySection;
    private ProfileManagerWrapper profileManager;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Set after a failed reachability check so a second tap continues anyway.
    private boolean unreachable;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFirstinstallBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        hideAppBar();
        finishOnBack();

        profileManager = new ProfileManagerWrapper(requireContext());
        serverSwitch = new ManagementServerSwitch(view, this::onModeChanged);
        setupKeySection = new SetupKeySection(view);

        binding.editTextServerUrl.addTextChangedListener(
                new SimpleTextWatcher(this::resetUrlFeedback));
        binding.editTextSetupKey.addTextChangedListener(
                new SimpleTextWatcher(setupKeySection::clearError));

        binding.btnContinue.setOnClickListener(v -> onContinue());

        if (PlatformUtils.isAndroidTV(requireContext())) {
            binding.txtAndroidtvBeta.setVisibility(View.VISIBLE);
            binding.btnContinue.postDelayed(() -> binding.btnContinue.requestFocus(), 200);
        }
    }

    private void onModeChanged(boolean selfHosted) {
        binding.editTextServerUrl.setVisibility(selfHosted ? View.VISIBLE : View.GONE);
        resetUrlFeedback();
        if (selfHosted) {
            binding.editTextServerUrl.requestFocus();
        }
    }

    private void resetUrlFeedback() {
        unreachable = false;
        binding.editTextServerUrl.setError(null);
        binding.textServerUrlWarning.setVisibility(View.GONE);
        binding.btnContinue.setText(R.string.fragment_firstinstall_continue);
    }

    private void onContinue() {
        String key = setupKeySection.key();
        if (!key.isEmpty() && setupKeySection.isKeyInvalid(key)) {
            setupKeySection.showError(getString(R.string.change_server_error_invalid_setup_key));
            return;
        }

        if (!serverSwitch.isSelfHosted()) {
            // The Go core already defaults to the cloud endpoint, so nothing
            // needs writing unless a setup key has to log in against it.
            if (key.isEmpty()) {
                finish();
            } else {
                applyServer(ManagementUrl.CLOUD, key);
            }
            return;
        }

        String rawUrl = binding.editTextServerUrl.getText().toString().trim();
        if (!ManagementUrl.isValid(rawUrl)) {
            binding.editTextServerUrl.setError(getString(R.string.profiles_dialog_url_invalid));
            binding.editTextServerUrl.requestFocus();
            return;
        }
        String targetUrl = ManagementUrl.normalize(rawUrl);

        // Probe unless the user already chose to proceed past the warning. A
        // setup-key login is no substitute: it reports an auth failure, not an
        // unreachable host, so a typo in the URL would surface as a confusing
        // login error instead of "couldn't reach this server".
        if (unreachable) {
            applyServer(targetUrl, key);
            return;
        }

        setBusy(true);
        executor.execute(() -> {
            boolean reachable = ManagementUrl.isReachable(targetUrl);
            mainHandler.post(() -> {
                if (binding == null) {
                    return;
                }
                setBusy(false);
                if (!reachable) {
                    unreachable = true;
                    binding.textServerUrlWarning.setVisibility(View.VISIBLE);
                    // Make the confirm-on-second-press explicit: the next tap
                    // skips the probe and continues regardless.
                    binding.btnContinue.setText(R.string.fragment_firstinstall_continue_anyway);
                    return;
                }
                applyServer(targetUrl, key);
            });
        });
    }

    /** Writes the chosen server to the active profile, then logs in if asked. */
    private void applyServer(String managementUrl, String setupKey) {
        String configPath;
        try {
            configPath = profileManager.getActiveConfigPath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve active config path", e);
            binding.editTextServerUrl.setError(e.getMessage());
            return;
        }

        try {
            Preferences preferences = Android.newPreferences(configPath);
            preferences.setManagementURL(managementUrl);
            preferences.commit();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save management URL", e);
            binding.editTextServerUrl.setError(getString(R.string.profiles_dialog_url_invalid));
            binding.editTextServerUrl.requestFocus();
            return;
        }

        if (setupKey.isEmpty()) {
            finish();
            return;
        }

        setBusy(true);
        Auth auth;
        try {
            auth = Android.newAuth(configPath, managementUrl);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create authenticator", e);
            setBusy(false);
            setupKeySection.showError(e.getMessage());
            return;
        }

        auth.loginWithSetupKeyAndSaveConfig(new ErrListener() {
            @Override
            public void onSuccess() {
                mainHandler.post(() -> {
                    if (binding == null) {
                        return;
                    }
                    setBusy(false);
                    finish();
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Setup key login failed", e);
                mainHandler.post(() -> {
                    if (binding == null) {
                        return;
                    }
                    setBusy(false);
                    String msg = e.getMessage();
                    setupKeySection.showError(msg != null && !msg.isEmpty()
                            ? msg
                            : getString(R.string.change_server_error_invalid_setup_key));
                });
            }
        }, setupKey, Build.MODEL);
    }

    private void setBusy(boolean busy) {
        if (binding == null) {
            return;
        }
        binding.btnContinue.setEnabled(!busy);
        binding.btnContinue.setText(busy
                ? R.string.profiles_dialog_checking
                : R.string.fragment_firstinstall_continue);
        binding.editTextServerUrl.setEnabled(!busy);
        serverSwitch.setEnabled(!busy);
        setupKeySection.setEnabled(!busy);
    }

    private void finish() {
        PreferenceUI.setFirstLaunchDone(requireContext());
        NavController navController = Navigation.findNavController(
                requireActivity(), R.id.nav_host_fragment_content_main);
        navController.popBackStack();
    }

    /**
     * Back closes the app instead of dismissing the screen. Popping would drop
     * the user on the home screen with the management server never chosen, and
     * since the flag is only cleared on continue the onboarding would reappear
     * on every cold start.
     */
    private void finishOnBack() {
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        requireActivity().finish();
                    }
                });
    }

    private void hideAppBar() {
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().hide();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().show();
        }
        binding = null;
    }

    private static class SimpleTextWatcher implements android.text.TextWatcher {
        private final Runnable onChanged;

        SimpleTextWatcher(Runnable onChanged) {
            this.onChanged = onChanged;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
            onChanged.run();
        }
    }
}
