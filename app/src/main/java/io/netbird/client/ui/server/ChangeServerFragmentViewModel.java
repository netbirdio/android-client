package io.netbird.client.ui.server;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewmodel.CreationExtras;
import androidx.lifecycle.viewmodel.ViewModelInitializer;

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
    private final String deviceName;
    private final Operation stopEngineCommand;

    public ChangeServerFragmentViewModel(String configFilePath, String deviceName, Operation stopEngineCommand) {
        this.configFilePath = configFilePath;
        this.deviceName = deviceName;
        this.stopEngineCommand = stopEngineCommand;

        var state = new ChangeServerFragmentUiState.Builder()
                .isUiEnabled(true)
                .shouldDisplayWarningDialog(true)
                .build();
        this.uiState = new MutableLiveData<>(state);
    }

    public static final CreationExtras.Key<String> CONFIG_FILE_PATH_KEY = new CreationExtras.Key<>() {};
    public static final CreationExtras.Key<String> DEVICE_NAME_KEY = new CreationExtras.Key<>() {};
    public static final CreationExtras.Key<Operation> STOP_ENGINE_COMMAND_KEY = new CreationExtras.Key<>() {};

    static final ViewModelInitializer<ChangeServerFragmentViewModel> initializer = new ViewModelInitializer<>(
            ChangeServerFragmentViewModel.class,
            creationExtras -> {
                String configFilePath = creationExtras.get(CONFIG_FILE_PATH_KEY);
                String deviceName = creationExtras.get(DEVICE_NAME_KEY);
                Operation stopEngineOperation = creationExtras.get(STOP_ENGINE_COMMAND_KEY);

                return new ChangeServerFragmentViewModel(configFilePath, deviceName, stopEngineOperation);
            }
    );

    private boolean isSetupKeyInvalid(String setupKey) {
        if (setupKey == null || setupKey.length() != 36) {
            return true;
        }

        try {
            UUID.fromString(setupKey);
        } catch (IllegalArgumentException e) {
            return true;
        }

        return false;
    }

    private boolean isUrlInvalid(String url) {
        return !url.matches("^https://.*");
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

        if (isUrlInvalid(managementServerAddress)) {
            emitErrorState(new ChangeServerFragmentUiState.Builder()
                    .isUrlInvalid(true)
                    .isUiEnabled(true)
                    .build());

            return;
        }

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

        boolean isUrlInvalid = isUrlInvalid(managementServerAddress);
        boolean isSetupKeyInvalid = isSetupKeyInvalid(setupKey);

        if (isUrlInvalid || isSetupKeyInvalid) {
            emitErrorState(new ChangeServerFragmentUiState.Builder()
                    .isUrlInvalid(isUrlInvalid)
                    .isSetupKeyInvalid(isSetupKeyInvalid)
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
