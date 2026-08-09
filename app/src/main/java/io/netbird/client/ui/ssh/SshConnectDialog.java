package io.netbird.client.ui.ssh;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

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
        float density = context.getResources().getDisplayMetrics().density;
        int padH = (int) (density * 24);
        int padV = (int) (density * 8);
        int marginV = (int) (density * 4);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padH, padV, padH, padV);

        EditText hostInput = null;
        if (prefillHost == null) {
            hostInput = new EditText(context);
            hostInput.setSingleLine(true);
            hostInput.setInputType(InputType.TYPE_CLASS_TEXT);
            hostInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            hostInput.setHint(R.string.ssh_dialog_host);
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
        container.addView(userInput, layoutWithMargin(marginV));

        // A prefilled host is a NetBird peer, which runs its SSH server on
        // 22022; a host typed by hand is an ordinary server on 22.
        EditText portInput = new EditText(context);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setHint(R.string.ssh_dialog_port);
        portInput.setText(prefillHost != null
                ? R.string.ssh_dialog_port_default_peer
                : R.string.ssh_dialog_port_default);
        container.addView(portInput, layoutWithMargin(marginV));

        NavController navController = resolveNavController(context);

        String title = dialogTitle != null ? dialogTitle
                : context.getString(R.string.ssh_new_connection_title);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(R.string.ssh_dialog_connect, (d, w) ->
                        connect(navController, prefillHost, hostField, userInput, portInput))
                .setNegativeButton(R.string.ssh_dialog_cancel, null)
                .create();

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
