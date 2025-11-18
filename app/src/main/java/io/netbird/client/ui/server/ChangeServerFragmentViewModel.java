package io.netbird.client.ui.server;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Optional;
import java.util.UUID;

import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.Auth;
import io.netbird.gomobile.android.ErrListener;
import io.netbird.gomobile.android.SSOListener;

public class ChangeServerFragmentViewModel extends ViewModel {
    public interface Operation {
        void execute();
    }

    private final MutableLiveData<ChangeServerFragmentUiState> uiState;
    private final String configFilePath;
    private final String defaultManagementServerAddress;
    private final String deviceName;
    private final Operation stopEngineCommand;

    public ChangeServerFragmentViewModel(String configFilePath, String defaultManagementServerAddress, String deviceName, Operation stopEngineCommand) {
        this.configFilePath = configFilePath;
        this.defaultManagementServerAddress = defaultManagementServerAddress;
        this.deviceName = deviceName;
        this.stopEngineCommand = stopEngineCommand;

        var state = new ChangeServerFragmentUiState.Builder()
                .isUiEnabled(true)
                .shouldDisplayWarningDialog(true)
                .build();
        this.uiState = new MutableLiveData<>(state);
    }

    private boolean isValidSetupKey(String setupKey) {
        try {
            UUID.fromString(setupKey);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Optional<Auth> getAuthenticator(String managementServerAddress) {
        Auth authenticator;

        try {
            authenticator = Android.newAuth(configFilePath, managementServerAddress);
        } catch (Exception e) {
            emitErrorState(e);
            return Optional.empty();
        }

        return Optional.ofNullable(authenticator);
    }

    private void emitSuccessState() {
        var state = new ChangeServerFragmentUiState.Builder()
                .isUiEnabled(false)
                .isOperationSuccessful(true)
                .build();

        uiState.postValue(state);
    }

    private void emitErrorState(Exception e) {
        var state = new ChangeServerFragmentUiState.Builder()
                .isUiEnabled(true)
                .errorMessage(e.getMessage())
                .build();

        uiState.postValue(state);
    }

    private void emitErrorState(ChangeServerFragmentUiState state) {
        uiState.postValue(state);
    }

    private void disableUi() {
        uiState.postValue(new ChangeServerFragmentUiState.Builder()
                .isUiEnabled(false)
                .build());
    }

    public LiveData<ChangeServerFragmentUiState> getUiState() {
        return uiState;
    }

    public void changeManagementServerAddress(String managementServerAddress) {
        disableUi();

        getAuthenticator(managementServerAddress).ifPresent((authenticator) -> authenticator.saveConfigIfSSOSupported(new SSOListener() {
            @Override
            public void onError(Exception e) {
                emitErrorState(e);
            }

            @Override
            public void onSuccess(boolean isSSOEnabled) {
                stopEngineCommand.execute();
                emitSuccessState();
            }
        }));
    }

    public void loginWithSetupKey(String managementServerAddress, String setupKey) {
        disableUi();

        if (!isValidSetupKey(setupKey)) {
            emitErrorState(new ChangeServerFragmentUiState.Builder()
                    .isSetupKeyInvalid(true)
                    .isUiEnabled(true)
                    .build());
            return;
        }

        getAuthenticator(managementServerAddress).ifPresent((authenticator) -> authenticator.loginWithSetupKeyAndSaveConfig(new ErrListener() {
            @Override
            public void onError(Exception e) {
                emitErrorState(e);
            }

            @Override
            public void onSuccess() {
                stopEngineCommand.execute();
                emitSuccessState();
            }
        }, setupKey, deviceName));
    }
}
