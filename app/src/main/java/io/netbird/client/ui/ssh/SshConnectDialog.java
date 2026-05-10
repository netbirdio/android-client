package io.netbird.client.ui.ssh;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import io.netbird.client.R;

/**
 * Reusable dialog that prompts for SSH connection parameters and navigates to
 * {@link SSHTerminalFragment} which creates the underlying session. The Go
 * client auto-detects the server type (NetBird-SSH with/without JWT, or a
 * regular OpenSSH) so the dialog only asks for host/user/port and an optional
 * password used as a fallback for regular SSH servers.
 */
public final class SshConnectDialog {

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
            hostInput.setInputType(InputType.TYPE_CLASS_TEXT);
            hostInput.setHint(R.string.ssh_dialog_host);
            container.addView(hostInput, layoutWithMargin(marginV));
        }
        final EditText hostField = hostInput;

        EditText userInput = new EditText(context);
        userInput.setInputType(InputType.TYPE_CLASS_TEXT);
        userInput.setHint(R.string.ssh_dialog_username);
        userInput.setText(R.string.ssh_dialog_username_default);
        userInput.setSelection(userInput.getText().length());
        container.addView(userInput, layoutWithMargin(marginV));

        EditText portInput = new EditText(context);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setHint(R.string.ssh_dialog_port);
        portInput.setText(R.string.ssh_dialog_port_default);
        container.addView(portInput, layoutWithMargin(marginV));

        EditText passwordInput = new EditText(context);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setHint(R.string.ssh_dialog_password_optional);
        container.addView(passwordInput, layoutWithMargin(marginV));

        NavController navController = resolveNavController(context);

        String title = dialogTitle != null ? dialogTitle
                : context.getString(R.string.ssh_new_connection_title);

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(R.string.ssh_dialog_connect, (d, w) -> {
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
                        port = 22022;
                    }
                    Bundle args = new Bundle();
                    args.putString(SSHTerminalFragment.ARG_HOST, host);
                    args.putInt(SSHTerminalFragment.ARG_PORT, port);
                    args.putString(SSHTerminalFragment.ARG_USER, user);
                    args.putString(SSHTerminalFragment.ARG_PASSWORD, passwordInput.getText().toString());
                    navController.navigate(R.id.nav_ssh_terminal, args);
                })
                .setNegativeButton(R.string.ssh_dialog_cancel, null)
                .show();
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
