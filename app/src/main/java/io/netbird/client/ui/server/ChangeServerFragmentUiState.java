package io.netbird.client.ui.server;

public class ChangeServerFragmentUiState {
    public final boolean isUiEnabled;
    public final boolean isSetupKeyInvalid;
    public final boolean isOperationSuccessful;
    public final String errorMessage;
    public final boolean shouldDisplayWarningDialog;

    public ChangeServerFragmentUiState(Builder builder) {
        this.isSetupKeyInvalid = builder.isSetupKeyInvalid;
        this.isUiEnabled = builder.isUiEnabled;
        this.isOperationSuccessful = builder.isOperationSuccessful;
        this.shouldDisplayWarningDialog = builder.shouldDisplayWarningDialog;
        this.errorMessage = builder.errorMessage;
    }

    public static class Builder {
        private boolean isUiEnabled = true;
        private boolean isSetupKeyInvalid = false;
        private boolean isOperationSuccessful = false;
        private String errorMessage;
        private boolean shouldDisplayWarningDialog = false;

        public Builder isUiEnabled(boolean isUiEnabled) {
            this.isUiEnabled = isUiEnabled;
            return this;
        }

        public Builder isSetupKeyInvalid(boolean isSetupKeyInvalid) {
            this.isSetupKeyInvalid = isSetupKeyInvalid;
            return this;
        }

        public Builder isOperationSuccessful(boolean isOperationSuccessful) {
            this.isOperationSuccessful = isOperationSuccessful;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder shouldDisplayWarningDialog(boolean shouldDisplayWarningDialog) {
            this.shouldDisplayWarningDialog = shouldDisplayWarningDialog;
            return this;
        }

        public ChangeServerFragmentUiState build() {
            return new ChangeServerFragmentUiState(this);
        }
    }
}
