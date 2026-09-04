package io.netbird.client.ui.server;

import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.UUID;

import io.netbird.client.R;

/**
 * "Add this device with a setup key" section, shared by the profile editor and
 * the first-run screen. A Material switch reveals the key field.
 *
 * <p>The desktop UI offers no setup-key path at all, but mobile devices are
 * commonly provisioned with one, so Android keeps it — off by default, since
 * SSO is the recommended way in.
 */
public final class SetupKeySection {

    /** Setup keys are UUIDs; same check the Go core applies. */
    private static final int SETUP_KEY_LENGTH = 36;

    private final View row;
    private final SwitchMaterial toggle;
    private final EditText input;
    private final TextView warning;

    public SetupKeySection(View root) {
        row = root.findViewById(R.id.row_setup_key);
        toggle = root.findViewById(R.id.switch_setup_key);
        input = root.findViewById(R.id.edit_text_setup_key);
        warning = root.findViewById(R.id.text_setup_key_warning);

        // The whole row is the touch target; the switch itself is not
        // separately clickable, so state changes flow through one path.
        row.setOnClickListener(v -> setEnabledState(!toggle.isChecked()));
    }

    /** Hides the section entirely, for contexts where enrolment makes no sense. */
    public void hide() {
        row.setVisibility(View.GONE);
        input.setVisibility(View.GONE);
        warning.setVisibility(View.GONE);
    }

    public EditText input() {
        return input;
    }

    /** The entered key, or an empty string when switched off or blank. */
    public String key() {
        return toggle.isChecked() ? input.getText().toString().trim() : "";
    }

    public boolean isKeyInvalid(String key) {
        if (key.length() != SETUP_KEY_LENGTH) {
            return true;
        }
        try {
            UUID.fromString(key);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public void showError(String message) {
        input.setError(message);
        input.requestFocus();
    }

    public void clearError() {
        input.setError(null);
    }

    /** Greys out the section while a login is in flight. */
    public void setEnabled(boolean enabled) {
        row.setEnabled(enabled);
        toggle.setEnabled(enabled);
        input.setEnabled(enabled);
    }

    private void setEnabledState(boolean on) {
        toggle.setChecked(on);

        int visibility = on ? View.VISIBLE : View.GONE;
        input.setVisibility(visibility);
        warning.setVisibility(visibility);

        if (on) {
            input.requestFocus();
        } else {
            // Discard the key so a hidden field cannot silently enrol the
            // device with a value the user meant to abandon.
            input.setText("");
            input.setError(null);
        }
    }
}
