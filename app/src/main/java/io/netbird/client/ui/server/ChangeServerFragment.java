package io.netbird.client.ui.server;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.MutableCreationExtras;

import java.util.UUID;

import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.databinding.FragmentServerBinding;
import io.netbird.client.tool.Preferences;
import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.Auth;
import io.netbird.gomobile.android.ErrListener;
import io.netbird.gomobile.android.SSOListener;


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

        if (uiState.isOperationSuccessful) {
            showSuccessDialog(requireContext());
        }
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MutableCreationExtras extras = new MutableCreationExtras();
        extras.set(ChangeServerFragmentViewModel.CONFIG_FILE_PATH_KEY, Preferences.configFile(requireContext()));
        extras.set(ChangeServerFragmentViewModel.DEVICE_NAME_KEY, deviceName());
        extras.set(ChangeServerFragmentViewModel.STOP_ENGINE_COMMAND_KEY,
                (ChangeServerFragmentViewModel.Operation) () -> serviceAccessor.stopEngine());

        viewModel = new ViewModelProvider(getViewModelStore(),
                ViewModelProvider.Factory.from(ChangeServerFragmentViewModel.initializer), extras)
                .get(ChangeServerFragmentViewModel.class);

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::mapStateToUi);

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

//        boolean hideAlert = false;
//        if (getArguments() != null) {
//            hideAlert = getArguments().getBoolean("hideAlert", false);
//        }
//
//        if (!hideAlert) {
//            showConfirmChangeServerDialog();
//        }
//
//        binding.btnUseNetbird.setOnClickListener(v -> {
//            disableUIElements();
//            binding.editTextServer.setText(Preferences.defaultServer());
//            updateServer(view.getContext(), Preferences.defaultServer());
//        });
//
//        binding.setupKeyGroup.setVisibility(View.VISIBLE);
//
//        binding.btnChangeServer.setOnClickListener(v -> {
//            if (binding.editTextServer.getText().toString().trim().isEmpty()) {
//                return;
//            }
//
//            disableUIElements();
//
//            if (binding.setupKeyGroup.getVisibility() == View.VISIBLE) {
//                String setupKey = binding.editTextSetupKey.getText().toString().trim();
//                if (setupKey.isEmpty()) {
//                    binding.editTextSetupKey.setError(v.getContext().getString(R.string.change_server_error_invalid_setup_key));
//                    binding.editTextSetupKey.requestFocus();
//                    enableUIElements();
//                    return;
//                }
//                if (!isValidSetupKey(setupKey)) {
//                    binding.editTextSetupKey.setError(v.getContext().getString(R.string.change_server_error_invalid_setup_key));
//                    binding.editTextSetupKey.requestFocus();
//                    enableUIElements();
//                    return;
//                }
//                String serverAddress = binding.editTextServer.getText().toString().trim();
//                loginWithSetupKey(v.getContext(), serverAddress, setupKey);
//            } else {
//                // Setup key is empty; update server instead
//                String serverAddress = binding.editTextServer.getText().toString().trim();
//                updateServer(v.getContext(), serverAddress);
//            }
//        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void showConfirmChangeServerDialog() {
        final View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_change_server, null);
        final AlertDialog alertDialog = new AlertDialog.Builder(requireContext())
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
            final AlertDialog alertDialog = new AlertDialog.Builder(context)
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

    private void loginWithSetupKey(Context context, String mgmServerAddress, String setupKey) {
        String configFilePath = Preferences.configFile(context);
        try {
            Auth auther = Android.newAuth(configFilePath, mgmServerAddress);
            auther.loginWithSetupKeyAndSaveConfig(new ErrListener() {
                @Override
                public void onError(Exception e) {
                    FragmentActivity activity = getActivity();
                    if (activity == null) return;
                    activity.runOnUiThread(() -> {
                        if (binding == null) return;
                        binding.editTextServer.setError(e.getMessage());
                        binding.editTextServer.requestFocus();
                    });
                    enableUIElements();
                }

                @Override
                public void onSuccess() {
                    enableUIElements();
                    showSuccessDialog(context);
                    serviceAccessor.stopEngine();
                }
            }, setupKey, deviceName());
        } catch (Exception e) {
            FragmentActivity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                if (binding == null) return;

                binding.editTextServer.setError(e.getMessage());
                binding.editTextServer.requestFocus();
            });
            enableUIElements();
        }
    }

    private String deviceName() {
        return Build.PRODUCT;
    }

    private void updateServer(Context context, String mgmServerAddress) {
        String configFilePath = Preferences.configFile(context);
        try {
            Auth auther = Android.newAuth(configFilePath, mgmServerAddress);
            auther.saveConfigIfSSOSupported(new SSOListener() {
                @Override
                public void onError(Exception e) {
                    Log.e("ChangeServerFragment", "Error updating server: " + e.getMessage());

                    FragmentActivity activity = getActivity();
                    if (activity == null) return;

                    activity.runOnUiThread(() -> {
                        if (binding == null) return;

                        binding.editTextServer.setError(e.getMessage());
                        binding.editTextServer.requestFocus();
                    });
                    enableUIElements();
                }

                @Override
                public void onSuccess(boolean sso) {
                    FragmentActivity activity = getActivity();
                    if (activity == null) return;

                    Log.i("ChangeServerFragment", "update server result, SSO supported: " + sso);
                    activity.runOnUiThread(() -> {
                        if (binding == null) return;

                        if (!sso) {
                            binding.setupKeyGroup.setVisibility(View.VISIBLE);
                        } else {
                            binding.setupKeyGroup.setVisibility(View.GONE);
                        }
                    });

                    enableUIElements();
                    showSuccessDialog(context);
                    serviceAccessor.stopEngine();
                }
            });
        } catch (Exception e) {
            Log.e("ChangeServerFragment", "Exception in updating server: " + e.getMessage());
            FragmentActivity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                if (binding == null) return;

                binding.editTextServer.setError(e.getMessage());
                binding.editTextServer.requestFocus();
            });
            enableUIElements();
        }
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

    private boolean isValidSetupKey(String setupKey) {
        try {
            UUID.fromString(setupKey);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}