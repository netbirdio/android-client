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

    public static void show(Context context, @Nullable String prefillHost,
                            @Nullable String dialogTitle) {
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

        EditText hostInput = null;
        if (prefillHost == null) {
            hostInput = new EditText(context);
            hostInput.setSingleLine(true);
            hostInput.setInputType(InputType.TYPE_CLASS_TEXT);
            hostInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            hostInput.setHint(R.string.ssh_dialog_host);
            styleField(hostInput, fieldPad);
            container.addView(hostInput, layoutWithMargin(marginV));
        }
        final EditText hostField = hostInput;

        EditText userInput = new EditText(context);
        userInput.setSingleLine(true);
        userInput.setInputType(InputType.TYPE_CLASS_TEXT);
        userInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        userInput.setHint(R.string.ssh_dialog_username);
        userInput.setText(R.string.ssh_dialog_username_default);
        userInput.setSelection(userInput.getText().length());
        styleField(userInput, fieldPad);
        container.addView(userInput, layoutWithMargin(marginV));

        // A prefilled host is a NetBird peer, which runs its SSH server on
        // 22022; a host typed by hand is an ordinary server on 22.
        EditText portInput = new EditText(context);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setHint(R.string.ssh_dialog_port);
        portInput.setText(prefillHost != null
                ? R.string.ssh_dialog_port_default_peer
                : R.string.ssh_dialog_port_default);
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
        MaterialButton connectButton = textButton(context, R.string.ssh_dialog_connect);
        buttonRow.addView(cancelButton);
        buttonRow.addView(connectButton);
        container.addView(buttonRow, layoutWithMargin(marginV));

        AlertDialog dialog = new AlertDialog.Builder(context, R.style.AlertDialogTheme)
                .setView(container)
                .create();

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        connectButton.setOnClickListener(v -> {
            connect(navController, prefillHost, hostField, userInput, portInput);
            dialog.dismiss();
        });

        // Enter on the last field connects, as pressing the button would.
        portInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        portInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_GO) {
                return false;
            }
            connect(navController, prefillHost, hostField, userInput, portInput);
            dialog.dismiss();
            return true;
        });

        dialog.show();
    }

    private static void connect(NavController navController, @Nullable String prefillHost,
                                @Nullable EditText hostField, EditText userInput,
                                EditText portInput) {
        if (navController == null) {
            return;
        }
        String host = prefillHost != null
                ? prefillHost
                : (hostField != null ? hostField.getText().toString().trim() : "");
        if (host.isEmpty()) {
            return;
        }
        String user = userInput.getText().toString().trim();
        if (user.isEmpty()) {
            user = "pzoli";
        }
        int port;
        try {
            port = Integer.parseInt(portInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            port = prefillHost != null ? PEER_SSH_PORT : DEFAULT_SSH_PORT;
        }
        Bundle args = new Bundle();
        args.putString(SSHTerminalFragment.ARG_HOST, host);
        args.putInt(SSHTerminalFragment.ARG_PORT, port);
        args.putString(SSHTerminalFragment.ARG_USER, user);
        navController.navigate(R.id.nav_ssh_terminal, args);
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
