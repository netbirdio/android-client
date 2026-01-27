package io.netbird.client.ui.server;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;
import androidx.lifecycle.viewmodel.MutableCreationExtras;

import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.databinding.FragmentServerBinding;
import io.netbird.client.tool.Preferences;
import io.netbird.client.tool.ProfileManagerWrapper;


public class ChangeServerFragment extends Fragment {

    public static final String HideAlertBundleArg = "hideAlert";
    private FragmentServerBinding binding;
    private ServiceAccessor serviceAccessor;
    private ChangeServerFragmentViewModel viewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentServerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof ServiceAccessor) {
            serviceAccessor = (ServiceAccessor) context;
        } else {
            throw new RuntimeException(context + " must implement ServiceAccessor");
        }
    }

    private void mapStateToUi(ChangeServerFragmentUiState uiState) {
        if (uiState.shouldDisplayWarningDialog) {
            showConfirmChangeServerDialog();
        }

        if (uiState.isUiEnabled) {
            enableUIElements();
        } else {
            // clearing error flags here because UI elements are only
            // disabled when managed to click the button and there are no errors.
            clearErrorFlags();
            disableUIElements();
        }

        if (uiState.errorMessage != null && !uiState.errorMessage.isEmpty()) {
            binding.editTextServer.setError(uiState.errorMessage);
            binding.editTextServer.requestFocus();
        }

        if (uiState.isSetupKeyInvalid) {
            binding.editTextSetupKey.setError(requireContext().getString(R.string.change_server_error_invalid_setup_key));
            binding.editTextSetupKey.requestFocus();
        }

        if (uiState.isUrlInvalid) {
            binding.editTextServer.setError(requireContext().getString(R.string.change_server_error_invalid_server_url));
            binding.editTextServer.requestFocus();
        }

        if (uiState.isOperationSuccessful) {
            showSuccessDialog(requireContext());
        }
    }

    private void setBounds(Drawable drawable) {
        if (drawable == null) return;

        var metrics = getResources().getDisplayMetrics();
        int dpValue = 12;
        int pixelValue = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpValue, metrics);

        drawable.setBounds(0, 0, pixelValue, pixelValue);
    }

    private void clearErrorFlags() {
        binding.editTextServer.setError(null);
        binding.editTextSetupKey.setError(null);
    }

    @NonNull
    @Override
    public CreationExtras getDefaultViewModelCreationExtras() {
        final var defaultExtras = super.getDefaultViewModelCreationExtras();

        // Get config path from ProfileManager instead of constructing it
        ProfileManagerWrapper profileManager = new ProfileManagerWrapper(requireContext());
        String configFilePath;
        try {
            configFilePath = profileManager.getActiveConfigPath();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get config path: " + e.getMessage(), e);
        }

        final var extras = new MutableCreationExtras(defaultExtras);
        extras.set(ChangeServerFragmentViewModel.CONFIG_FILE_PATH_KEY, configFilePath);
        extras.set(ChangeServerFragmentViewModel.DEVICE_NAME_KEY, deviceName());
        extras.set(ChangeServerFragmentViewModel.STOP_ENGINE_COMMAND_KEY,
                (ChangeServerFragmentViewModel.Operation) () -> serviceAccessor.stopEngine());
        return extras;
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this,
                ViewModelProvider.Factory.from(ChangeServerFragmentViewModel.initializer))
                .get(ChangeServerFragmentViewModel.class);

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::mapStateToUi);

        Drawable minusIcon = ContextCompat.getDrawable(requireContext(), R.drawable.remove_24px);
        Drawable plusIcon = ContextCompat.getDrawable(requireContext(), R.drawable.add_24px);
        setBounds(minusIcon);
        setBounds(plusIcon);

        // This is done to resize the first time the plus icon is shown on the label.
        binding.textSetupKeyLabel.setCompoundDrawables(plusIcon, null, null, null);

        binding.textSetupKeyLabel.setOnClickListener(v -> {
            if (binding.setupKeyGroup.getVisibility() == View.VISIBLE) {
                binding.textSetupKeyLabel.setCompoundDrawables(plusIcon, null, null, null);

                binding.editTextSetupKey.setText("");
                binding.editTextSetupKey.setError(null);
                binding.setupKeyGroup.setVisibility(View.GONE);
            } else {
                binding.textSetupKeyLabel.setCompoundDrawables(minusIcon, null, null, null);
                binding.setupKeyGroup.setVisibility(View.VISIBLE);
            }
        });

        binding.btnChangeServer.setOnClickListener(v -> {
            String managementServerUri = binding.editTextServer.getText().toString().trim();
            String setupKey = binding.editTextSetupKey.getText().toString().trim();

            if (managementServerUri.isEmpty() && setupKey.isEmpty()) {
                return;
            }

            if (!managementServerUri.isEmpty() && !setupKey.isEmpty()) {
                viewModel.loginWithSetupKey(managementServerUri, setupKey);
            } else if (!managementServerUri.isEmpty()) {
                viewModel.changeManagementServerAddress(managementServerUri);
            } else {
                managementServerUri = Preferences.defaultServer();

                binding.editTextServer.setText(managementServerUri);
                viewModel.loginWithSetupKey(managementServerUri, setupKey);
            }
        });

        binding.btnUseNetbird.setOnClickListener(v -> {
            String setupKey = binding.editTextSetupKey.getText().toString().trim();
            String managementServerUri = Preferences.defaultServer();

            binding.editTextServer.setText(managementServerUri);

            if (setupKey.isEmpty()) {
                viewModel.changeManagementServerAddress(managementServerUri);
            } else {
                viewModel.loginWithSetupKey(managementServerUri, setupKey);
            }
        });

        binding.editTextServer.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                hideKeyboard(v);
                v.clearFocus();
                return true;
            }
            return false;
        });

        binding.editTextSetupKey.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                hideKeyboard(v);
                v.clearFocus();
                return true;
            }
            return false;
        });
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void showConfirmChangeServerDialog() {
        final View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_change_server, null);
        final AlertDialog alertDialog = new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btn_yes).setOnClickListener(v -> alertDialog.dismiss());

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            alertDialog.dismiss();
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        alertDialog.show();
    }

    private void showSuccessDialog(Context context) {
        requireActivity().runOnUiThread(() -> {
            final View dialogView = getLayoutInflater().inflate(R.layout.dialog_change_server_success, null);
            final AlertDialog alertDialog = new AlertDialog.Builder(context, R.style.AlertDialogTheme)
                    .setView(dialogView)
                    .create();

            dialogView.findViewById(R.id.btn_close).setOnClickListener(v -> {
                alertDialog.dismiss();
                requireActivity().getSupportFragmentManager().popBackStack();
            });

            alertDialog.setOnDismissListener(dialog -> requireActivity().getSupportFragmentManager().popBackStack());
            alertDialog.show();
        });
    }

    private String deviceName() {
        return Build.PRODUCT;
    }

    private void disableUIElements() {
        if (binding == null) return;
        binding.editTextServer.setEnabled(false);
        binding.editTextSetupKey.setEnabled(false);
        binding.btnChangeServer.setText(R.string.change_server_verifying);
        binding.btnChangeServer.setEnabled(false);
        binding.btnUseNetbird.setVisibility(View.GONE);
    }

    private void enableUIElements() {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (binding == null) return;

            binding.editTextServer.setEnabled(true);
            binding.editTextSetupKey.setEnabled(true);
            binding.btnChangeServer.setText(R.string.change_server_btn);
            binding.btnChangeServer.setEnabled(true);
            binding.btnUseNetbird.setVisibility(View.VISIBLE);
        });
    }
}