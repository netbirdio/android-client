package io.netbird.client.ui.server;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.databinding.FragmentServerBinding;
import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.Auth;
import io.netbird.client.tool.Preferences;
import io.netbird.gomobile.android.ErrListener;
import io.netbird.gomobile.android.SSOListener;


public class ChangeServerFragment extends Fragment {

    public static final String HideAlertBundleArg="hideAlert";
    private FragmentServerBinding binding;
    private ServiceAccessor serviceAccessor;

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

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        boolean hideAlert = false;
        if (getArguments() != null) {
            hideAlert = getArguments().getBoolean("hideAlert", false);
        }

        if (!hideAlert) {
            showConfirmChangeServerDialog();
        }

        binding.btnUseNetbird.setOnClickListener(v -> {
            disableUIElements();
            binding.editTextServer.setText(Preferences.defaultServer());
            updateServer(view.getContext(), Preferences.defaultServer());
        });

        binding.btnChangeServer.setOnClickListener(v->{
            disableUIElements();

            String setupKey = binding.editTextSetupKey.getText().toString().trim();

            if (!setupKey.isEmpty()) {
                if (isValidSetupKey(setupKey)) {
                    String serverAddress = binding.editTextServer.getText().toString().trim();
                    loginWithSetupKey(v.getContext(), serverAddress, setupKey);
                } else {
                    binding.editTextSetupKey.setError(v.getContext().getString(R.string.change_server_error_invalid_setup_key));
                    enableUIElements();
                }
            } else {
                // Setup key is empty; update server instead
                String serverAddress = binding.editTextServer.getText().toString().trim();
                updateServer(v.getContext(), serverAddress);
            }
        });
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
                    activity.runOnUiThread(() -> binding.editTextServer.setError(e.getMessage()));
                    enableUIElements();
                }

                @Override
                public void onSuccess() {
                    enableUIElements();
                    showSuccessDialog(context);
                    serviceAccessor.stopEngine();
                }
            }, setupKey, "devicename");
        } catch (Exception e) {
            FragmentActivity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> binding.editTextServer.setError(e.getMessage()));
            enableUIElements();
        }
    }

    private void updateServer(Context context, String mgmServerAddress) {
        String configFilePath = Preferences.configFile(context);
        try {
            Auth auther = Android.newAuth(configFilePath, mgmServerAddress);
            auther.saveConfigIfSSOSupported(new SSOListener() {
                @Override
                public void onError(Exception e) {
                    FragmentActivity activity = getActivity();
                    if (activity == null) return;
                    activity.runOnUiThread(() -> binding.editTextServer.setError(e.getMessage()));
                    enableUIElements();
                }

                @Override
                public void onSuccess(boolean sso) {
                    enableUIElements();
                    showSuccessDialog(context);
                    serviceAccessor.stopEngine();
                }
            });
        } catch (Exception e) {
            FragmentActivity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> binding.editTextServer.setError(e.getMessage()));
            enableUIElements();
        }
    }

    private void disableUIElements() {
        if(binding == null) return;
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