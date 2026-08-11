package io.netbird.client.ui.ssh;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;

import io.netbird.client.R;

/**
 * Prompts for SSH connection parameters and navigates to
 * {@link SSHTerminalFragment}, which creates the session. No password is asked
 * for here: the Go client detects the server type and picks the auth, and only
 * a server that refuses everything else makes the terminal prompt for one.
 */
public final class SshConnectDialog {

    /** Port a NetBird peer's built-in SSH server listens on. */
    private static final int PEER_SSH_PORT = 22022;
    /** Port an ordinary SSH server listens on. */
    private static final int DEFAULT_SSH_PORT = 22;

    private SshConnectDialog() {}

    /**
     * Edits a stored session in place instead of opening a new one. The host is
     * editable here even for a peer session, which is the point: the saved entry
     * is what is being corrected.
     *
     * @param onSaved run with the edited details when the user confirms
     */
    public static void showEditor(Context context, @NonNull String host, int port,
                                  @NonNull String user, @NonNull OnEdited onSaved) {
        show(context, null, context.getString(R.string.ssh_session_edit),
                new Prefill(host, port, user), onSaved);
    }

    /** Details confirmed in the editor. */
    public interface OnEdited {
        void onEdited(String host, int port, String user);
    }

    /** Values an editor starts from; null when opening a fresh connection. */
    private static final class Prefill {
        final String host;
        final int port;
        final String user;

        Prefill(String host, int port, String user) {
            this.host = host;
            this.port = port;
            this.user = user;
        }
    }

    public static void show(Context context, @Nullable String prefillHost,
                            @Nullable String dialogTitle) {
        show(context, prefillHost, dialogTitle, null, null);
    }

    private static void show(Context context, @Nullable String prefillHost,
                             @Nullable String dialogTitle, @Nullable Prefill prefill,
                             @Nullable OnEdited onSaved) {
        // bg_rounded_nb_bg has a 28dp corner radius, so the body needs padding
        // wide enough that the first and last child clear the rounding. The
        // field padding goes with the bordered background set in styleField.
        float density = context.getResources().getDisplayMetrics().density;
        int pad = (int) (density * 24);
        int fieldPad = (int) (density * 12);
        int marginV = (int) (density * 8);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, pad);
        // AlertDialogTheme makes the dialog window transparent, expecting the
        // custom view to bring its own body, so without this the fields would
        // float over whatever is behind the dialog.
        container.setBackgroundResource(R.drawable.bg_rounded_nb_bg);

        // The host field is hidden only when connecting to a peer, whose address
        // is fixed. An editor always shows it: correcting the address is half of
        // what it is for.
        EditText hostInput = null;
        if (prefillHost == null) {
            hostInput = new EditText(context);
            hostInput.setSingleLine(true);
            hostInput.setInputType(InputType.TYPE_CLASS_TEXT);
            hostInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            hostInput.setHint(R.string.ssh_dialog_host);
            if (prefill != null) {
                hostInput.setText(prefill.host);
            }
            styleField(hostInput, fieldPad);
            container.addView(hostInput, layoutWithMargin(marginV));
        }
        final EditText hostField = hostInput;

        EditText userInput = new EditText(context);
        userInput.setSingleLine(true);
        userInput.setInputType(InputType.TYPE_CLASS_TEXT);
        userInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        userInput.setHint(R.string.ssh_dialog_username);
        // An editor starts from the session's own name; otherwise prefill with
        // whatever was used last, so a repeat connection is one tap. Left empty
        // on a fresh install rather than guessing a name.
        userInput.setText(prefill != null ? prefill.user : SshSessionStore.lastUser(context));
        userInput.setSelection(userInput.getText().length());
        styleField(userInput, fieldPad);
        container.addView(userInput, layoutWithMargin(marginV));

        // A prefilled host is a NetBird peer, which runs its SSH server on
        // 22022; a host typed by hand is an ordinary server on 22.
        EditText portInput = new EditText(context);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setHint(R.string.ssh_dialog_port);
        if (prefill != null) {
            portInput.setText(String.valueOf(prefill.port));
        } else {
            portInput.setText(prefillHost != null
                    ? R.string.ssh_dialog_port_default_peer
                    : R.string.ssh_dialog_port_default);
        }
        styleField(portInput, fieldPad);
        container.addView(portInput, layoutWithMargin(marginV));

        NavController navController = resolveNavController(context);

        String title = dialogTitle != null ? dialogTitle
                : context.getString(R.string.ssh_new_connection_title);

        // The title goes inside the custom view: the dialog's own title sits
        // outside the rounded background, which the transparent window would
        // leave hanging over the screen behind it.
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(24);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.nb_txt));
        container.addView(titleView, 0, layoutWithMargin(0));

        // The buttons live in the custom view for the same reason as the title.
        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);
        MaterialButton cancelButton = textButton(context, R.string.ssh_dialog_cancel);
        MaterialButton confirmButton = textButton(context, onSaved != null
                ? R.string.ssh_dialog_save
                : R.string.ssh_dialog_connect);
        buttonRow.addView(cancelButton);
        buttonRow.addView(confirmButton);
        container.addView(buttonRow, layoutWithMargin(marginV));

        AlertDialog dialog = new AlertDialog.Builder(context, R.style.AlertDialogTheme)
                .setView(container)
                .create();

        Runnable confirm = () -> {
            if (onSaved != null) {
                if (saveEdit(hostField, userInput, portInput, onSaved)) {
                    dialog.dismiss();
                }
                return;
            }
            if (connect(navController, prefillHost, hostField, userInput, portInput)) {
                dialog.dismiss();
            }
        };

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        confirmButton.setOnClickListener(v -> confirm.run());

        // Enter on the last field confirms, as pressing the button would.
        portInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        portInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_GO) {
                return false;
            }
            confirm.run();
            return true;
        });

        dialog.show();
    }

    /**
     * Hands the edited details back to the caller, which owns the stored
     * session. Applies the same validation as a fresh connection, so an editor
     * cannot save a session that could never be dialled.
     *
     * @return false when a required field is empty
     */
    private static boolean saveEdit(@Nullable EditText hostField, EditText userInput,
                                    EditText portInput, OnEdited onSaved) {
        String host = hostField != null ? hostField.getText().toString().trim() : "";
        if (host.isEmpty()) {
            if (hostField != null) {
                hostField.setError(hostField.getContext()
                        .getString(R.string.ssh_dialog_host_required));
            }
            return false;
        }
        String user = userInput.getText().toString().trim();
        if (user.isEmpty()) {
            userInput.setError(userInput.getContext()
                    .getString(R.string.ssh_dialog_username_required));
            return false;
        }
        int port;
        try {
            port = Integer.parseInt(portInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            port = DEFAULT_SSH_PORT;
        }
        SshSessionStore.setLastUser(userInput.getContext(), user);
        onSaved.onEdited(host, port, user);
        return true;
    }

    /**
     * @return false when a required field is empty, so the caller can leave the
     *         dialog open instead of dismissing it and losing what was typed.
     */
    private static boolean connect(NavController navController, @Nullable String prefillHost,
                                   @Nullable EditText hostField, EditText userInput,
                                   EditText portInput) {
        if (navController == null) {
            return false;
        }
        String host = prefillHost != null
                ? prefillHost
                : (hostField != null ? hostField.getText().toString().trim() : "");
        if (host.isEmpty()) {
            if (hostField != null) {
                hostField.setError(hostField.getContext()
                        .getString(R.string.ssh_dialog_host_required));
            }
            return false;
        }
        String user = userInput.getText().toString().trim();
        if (user.isEmpty()) {
            // No default to fall back on: the login name is the remote account,
            // and guessing one only produces a confusing auth failure.
            userInput.setError(userInput.getContext()
                    .getString(R.string.ssh_dialog_username_required));
            return false;
        }
        int port;
        try {
            port = Integer.parseInt(portInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            port = prefillHost != null ? PEER_SSH_PORT : DEFAULT_SSH_PORT;
        }
        SshSessionStore.setLastUser(userInput.getContext(), user);
        Bundle args = new Bundle();
        args.putString(SSHTerminalFragment.ARG_HOST, host);
        args.putInt(SSHTerminalFragment.ARG_PORT, port);
        args.putString(SSHTerminalFragment.ARG_USER, user);
        navController.navigate(R.id.nav_ssh_terminal, args);
        return true;
    }

    /** Matches the borderless orange buttons the XML dialog layouts use. */
    private static MaterialButton textButton(Context context, int textRes) {
        MaterialButton button = new MaterialButton(context, null,
                com.google.android.material.R.attr.borderlessButtonStyle);
        button.setText(textRes);
        button.setTextColor(ContextCompat.getColor(context, R.color.nb_orange));
        return button;
    }

    /**
     * Gives a programmatically created field the same look as the ones in the
     * XML dialog layouts: without this the text colour comes from the theme's
     * global android:textColor, which is near white at night and so invisible
     * against the field background.
     */
    private static void styleField(EditText field, int padding) {
        Context context = field.getContext();
        field.setBackgroundResource(R.drawable.edit_text_white_focusable);
        field.setPadding(padding, padding, padding, padding);
        field.setTextColor(ContextCompat.getColor(context, R.color.nb_txt));
        field.setHintTextColor(ContextCompat.getColor(context, R.color.nb_txt_light));
    }

    private static LinearLayout.LayoutParams layoutWithMargin(int marginV) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = marginV;
        return lp;
    }

    private static NavController resolveNavController(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof AppCompatActivity) {
                return Navigation.findNavController((AppCompatActivity) current,
                        R.id.nav_host_fragment_content_main);
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }
}
