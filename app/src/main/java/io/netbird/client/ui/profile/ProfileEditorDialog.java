package io.netbird.client.ui.profile;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.netbird.client.R;
import io.netbird.client.tool.Profile;
import io.netbird.client.tool.ProfileManagerWrapper;
import io.netbird.client.ui.server.ManagementServerSwitch;
import io.netbird.client.ui.server.ManagementUrl;
import io.netbird.client.ui.server.SetupKeySection;
import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.Auth;
import io.netbird.gomobile.android.ErrListener;
import io.netbird.gomobile.android.Preferences;

/**
 * Create/edit profile dialog mirroring the desktop app's profile modal: the
 * user names the profile and picks NetBird Cloud or a self-hosted management
 * server in one step. Cloud is the pre-selected default; the URL field only
 * appears for self-hosted.
 *
 * <p>The desktop reuses a single modal for both cases, seeding it with the
 * profile's current values when editing; {@link #showEdit} does the same.
 */
public final class ProfileEditorDialog {
    private static final String TAG = "ProfileEditorDialog";

    public interface OnProfileSavedListener {
        void onProfileSaved(Profile profile);
    }

    private final Context context;
    private final ProfileManagerWrapper profileManager;
    private final OnProfileSavedListener listener;
    /** Profile being edited, or null when creating a new one. */
    private final Profile editing;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Management URL the edited profile started with, for change detection. */
    private String initialUrl = "";

    private AlertDialog dialog;
    private EditText nameInput;
    private EditText urlInput;
    private TextView urlWarning;
    private ManagementServerSwitch serverSwitch;
    private SetupKeySection setupKeySection;
    private MaterialButton okButton;
    private MaterialButton cancelButton;

    // Set after a failed reachability check; the next submit skips the check so
    // the user can save anyway (soft warning, desktop parity).
    private boolean unreachable;

    private ProfileEditorDialog(Context context, ProfileManagerWrapper profileManager,
                                Profile editing, OnProfileSavedListener listener) {
        this.context = context;
        this.profileManager = profileManager;
        this.editing = editing;
        this.listener = listener;
    }

    /** Opens the dialog to create a new profile. */
    public static void showCreate(Context context, ProfileManagerWrapper profileManager,
                                  OnProfileSavedListener listener) {
        new ProfileEditorDialog(context, profileManager, null, listener).showDialog();
    }

    /** Opens the dialog seeded with an existing profile's name and server. */
    public static void showEdit(Context context, ProfileManagerWrapper profileManager,
                                Profile profile, OnProfileSavedListener listener) {
        new ProfileEditorDialog(context, profileManager, profile, listener).showDialog();
    }

    private boolean isEditing() {
        return editing != null;
    }

    @SuppressLint("InflateParams")
    private void showDialog() {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_profile_editor, null);

        nameInput = dialogView.findViewById(R.id.edit_text_profile_name);
        urlInput = dialogView.findViewById(R.id.edit_text_server_url);
        urlWarning = dialogView.findViewById(R.id.text_server_url_warning);
        okButton = dialogView.findViewById(R.id.btn_ok_dialog);
        cancelButton = dialogView.findViewById(R.id.btn_cancel_dialog);

        serverSwitch = new ManagementServerSwitch(dialogView, this::onModeChanged);
        setupKeySection = new SetupKeySection(dialogView);

        TextView title = dialogView.findViewById(R.id.text_title_dialog);
        title.setText(isEditing() ? R.string.profiles_dialog_edit_title : R.string.profiles_dialog_add_title);
        okButton.setText(submitLabel());

        // A profile being edited is already registered, so enrolling it with a
        // setup key would be meaningless — only offer the key when creating.
        if (isEditing()) {
            setupKeySection.hide();
            seedFromProfile();
        }

        nameInput.addTextChangedListener(new SimpleTextWatcher(() -> nameInput.setError(null)));
        urlInput.addTextChangedListener(new SimpleTextWatcher(this::resetUrlFeedback));
        setupKeySection.input().addTextChangedListener(
                new SimpleTextWatcher(setupKeySection::clearError));

        dialog = new AlertDialog.Builder(context, R.style.AlertDialogTheme)
                .setView(dialogView)
                .create();
        dialog.setOnDismissListener(d -> executor.shutdown());

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        okButton.setOnClickListener(v -> onSubmit());

        dialog.show();
        nameInput.requestFocus();
        // Editing usually means overwriting the name, so pre-select it.
        nameInput.selectAll();
    }

    private int submitLabel() {
        return isEditing() ? R.string.profiles_dialog_edit_submit : R.string.profiles_add;
    }

    private void onModeChanged(boolean selfHosted) {
        urlInput.setVisibility(selfHosted ? View.VISIBLE : View.GONE);
        resetUrlFeedback();
        if (selfHosted) {
            urlInput.requestFocus();
        }
    }

    /**
     * Fills the fields with the edited profile's current values. A cloud (or
     * blank) management URL keeps the Cloud segment selected and leaves the URL
     * field empty, matching how the desktop derives its initial mode.
     */
    private void seedFromProfile() {
        nameInput.setText(editing.getName());

        String url = readManagementUrl();
        initialUrl = url;
        if (ManagementUrl.isCloud(url)) {
            return;
        }

        serverSwitch.setSelfHostedSilently(true);
        urlInput.setText(url);
        urlInput.setVisibility(View.VISIBLE);
    }

    private String readManagementUrl() {
        try {
            String configPath = profileManager.getConfigPath(editing.getID());
            return Android.newPreferences(configPath).getManagementURL();
        } catch (Exception e) {
            // A profile that has never connected may not have a URL stored yet;
            // fall back to Cloud rather than blocking the edit.
            Log.w(TAG, "Failed to read management URL, defaulting to cloud", e);
            return "";
        }
    }

    private void resetUrlFeedback() {
        unreachable = false;
        urlInput.setError(null);
        urlWarning.setVisibility(View.GONE);
    }

    private void onSubmit() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameInput.setError(context.getString(R.string.profiles_error_empty_name));
            nameInput.requestFocus();
            return;
        }

        String key = setupKeySection.key();
        if (!key.isEmpty() && setupKeySection.isKeyInvalid(key)) {
            setupKeySection.showError(
                    context.getString(R.string.change_server_error_invalid_setup_key));
            return;
        }

        if (!serverSwitch.isSelfHosted()) {
            // Creating: leave the config untouched so the Go core applies its
            // own cloud default. Editing: the profile may be moving away from a
            // self-hosted server, so the cloud URL has to be written out.
            save(name, isEditing() ? ManagementUrl.CLOUD : null);
            return;
        }

        String rawUrl = urlInput.getText().toString().trim();
        if (!ManagementUrl.isValid(rawUrl)) {
            urlInput.setError(context.getString(R.string.profiles_dialog_url_invalid));
            urlInput.requestFocus();
            return;
        }
        String targetUrl = ManagementUrl.normalize(rawUrl);

        // Probe unless the URL is already known to work or the user chose to
        // proceed past the warning. A setup-key login is no substitute: it
        // reports an auth failure, not an unreachable host, and creates the
        // profile before it ever contacts the server.
        if (unreachable || targetUrl.equals(initialUrl)) {
            save(name, targetUrl);
            return;
        }

        setChecking(true);
        executor.execute(() -> {
            boolean reachable = ManagementUrl.isReachable(targetUrl);
            mainHandler.post(() -> {
                if (!dialog.isShowing()) {
                    return;
                }
                setChecking(false);
                if (!reachable) {
                    unreachable = true;
                    urlWarning.setVisibility(View.VISIBLE);
                    return;
                }
                save(name, targetUrl);
            });
        });
    }

    private void save(String name, String managementUrl) {
        if (isEditing()) {
            updateProfile(name, managementUrl);
        } else {
            createProfile(name, managementUrl);
        }
    }

    /** Applies name and/or server changes to the profile being edited. */
    private void updateProfile(String name, String managementUrl) {
        try {
            if (!name.equals(editing.getName())) {
                profileManager.renameProfile(editing.getID(), name);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to rename profile", e);
            nameInput.setError(e.getMessage());
            nameInput.requestFocus();
            return;
        }

        if (managementUrl != null && !managementUrl.equals(initialUrl)) {
            try {
                writeManagementUrl(editing.getID(), managementUrl);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update management URL", e);
                // The rename above may already have gone through; report the
                // server failure rather than silently keeping the old URL.
                urlInput.setError(context.getString(R.string.profiles_dialog_url_invalid));
                urlInput.requestFocus();
                return;
            }
        }

        dialog.dismiss();
        if (listener != null) {
            listener.onProfileSaved(new Profile(editing.getID(), name, editing.isActive()));
        }
    }

    private void writeManagementUrl(String profileId, String managementUrl) throws Exception {
        String configPath = profileManager.getConfigPath(profileId);
        Preferences preferences = Android.newPreferences(configPath);
        preferences.setManagementURL(managementUrl);
        preferences.commit();
    }

    private void createProfile(String name, String managementUrl) {
        Profile created;
        try {
            created = profileManager.addProfile(name);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add profile", e);
            showAddError(name, e);
            return;
        }

        if (managementUrl != null) {
            try {
                writeManagementUrl(created.getID(), managementUrl);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set management URL for new profile", e);
                // Roll back: don't leave behind a profile pointing at the
                // cloud server when the user asked for a self-hosted one.
                rollBack(created);
                urlInput.setError(context.getString(R.string.profiles_dialog_url_invalid));
                urlInput.requestFocus();
                return;
            }
        }

        String key = setupKeySection.key();
        if (!key.isEmpty()) {
            enrollWithSetupKey(created,
                    managementUrl == null ? ManagementUrl.CLOUD : managementUrl, key);
            return;
        }

        dialog.dismiss();
        if (listener != null) {
            listener.onProfileSaved(created);
        }
    }

    /**
     * Registers the freshly created profile with the management server using a
     * setup key. The gomobile listener fires on its own thread, so results are
     * posted back to the main thread before touching the UI.
     */
    private void enrollWithSetupKey(Profile created, String managementUrl, String key) {
        String configPath;
        try {
            configPath = profileManager.getConfigPath(created.getID());
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve config path for setup key login", e);
            rollBack(created);
            showAddError(created.getName(), e);
            return;
        }

        setChecking(true);
        Auth auth;
        try {
            auth = Android.newAuth(configPath, managementUrl);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create authenticator", e);
            setChecking(false);
            rollBack(created);
            showSetupKeyError(e.getMessage());
            return;
        }

        auth.loginWithSetupKeyAndSaveConfig(new ErrListener() {
            @Override
            public void onSuccess() {
                mainHandler.post(() -> {
                    if (!dialog.isShowing()) {
                        return;
                    }
                    setChecking(false);
                    dialog.dismiss();
                    if (listener != null) {
                        listener.onProfileSaved(created);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Setup key login failed", e);
                mainHandler.post(() -> {
                    // Keep the half-created profile out of the list: it was
                    // never registered, so it could not connect anyway.
                    rollBack(created);
                    if (!dialog.isShowing()) {
                        return;
                    }
                    setChecking(false);
                    showSetupKeyError(e.getMessage());
                });
            }
        }, key, Build.PRODUCT);
    }

    private void showSetupKeyError(String message) {
        setupKeySection.showError(message != null && !message.isEmpty()
                ? message
                : context.getString(R.string.change_server_error_invalid_setup_key));
    }

    private void rollBack(Profile created) {
        try {
            profileManager.removeProfile(created.getID());
        } catch (Exception e) {
            Log.e(TAG, "Failed to roll back profile", e);
        }
    }

    private void showAddError(String name, Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("already exists")) {
            Toast.makeText(context,
                    context.getString(R.string.profiles_error_already_exists, name),
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Failed to add profile: " + msg, Toast.LENGTH_SHORT).show();
        }
    }

    private void setChecking(boolean checking) {
        okButton.setEnabled(!checking);
        cancelButton.setEnabled(!checking);
        nameInput.setEnabled(!checking);
        urlInput.setEnabled(!checking);
        serverSwitch.setEnabled(!checking);
        setupKeySection.setEnabled(!checking);

        // Pin the width before swapping in the shorter "Checking…" label so the
        // filled button keeps its size instead of shrinking mid-check.
        if (checking) {
            okButton.setMinWidth(okButton.getWidth());
            okButton.setText(R.string.profiles_dialog_checking);
        } else {
            okButton.setText(submitLabel());
        }
    }

    private static class SimpleTextWatcher implements TextWatcher {
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
        public void afterTextChanged(Editable s) {
            onChanged.run();
        }
    }
}
