package io.netbird.client.ui.ssh;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.netbird.client.R;
import io.netbird.client.ServiceAccessor;
import io.netbird.client.databinding.FragmentSshTerminalBinding;
import io.netbird.gomobile.android.SSHClient;
import io.netbird.gomobile.android.URLOpener;

public class SSHTerminalFragment extends Fragment {

    private static final String LOGTAG = "SSHTerminal";

    public static final String ARG_SESSION_ID = "sessionId";

    // Args used when creating a session inline (e.g. first connect from peer dialog).
    public static final String ARG_HOST = "host";
    public static final String ARG_PORT = "port";
    public static final String ARG_USER = "user";
    public static final String ARG_PASSWORD = "password";

    private FragmentSshTerminalBinding binding;
    private ServiceAccessor serviceAccessor;
    private SshSession session;
    private SessionListener sessionListener;
    private AlertDialog passwordDialog;
    private AlertDialog hostKeyDialog;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean ctrlArmed = false;
    private boolean altArmed = false;
    private boolean terminalReady = false;
    private int previousSoftInputMode;
    private int previousOrientation;
    /** Outlives the view, so session callbacks can still resolve strings. */
    private Context appContext;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        appContext = context.getApplicationContext();
        if (context instanceof ServiceAccessor) {
            serviceAccessor = (ServiceAccessor) context;
        } else {
            throw new RuntimeException(context + " must implement ServiceAccessor");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSshTerminalBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        WebView webView = binding.terminalWebView;
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                if (cm.messageLevel() == ConsoleMessage.MessageLevel.ERROR
                        || cm.messageLevel() == ConsoleMessage.MessageLevel.WARNING) {
                    Log.w(LOGTAG, "WebView[" + cm.messageLevel() + "] " + cm.message()
                            + " @" + cm.sourceId() + ":" + cm.lineNumber());
                }
                return true;
            }
        });
        webView.addJavascriptInterface(new TerminalBridge(), "AndroidSSH");

        webView.loadUrl("file:///android_asset/terminal/index.html");

        applyImeInsets();
        wireKeyBar();
    }

    /**
     * Keeps the keyboard from covering the terminal, which needs two different
     * mechanisms depending on the platform the app is running on.
     *
     * The manifest asks for adjustPan, which slides the whole window up and
     * carries the key bar off screen. Up to API 34 that mode is honoured, so the
     * fragment asks for adjustResize instead while it is on screen and the
     * window shrinks around the keyboard. From API 35 edge-to-edge is enforced,
     * the soft input mode is ignored and the keyboard just draws over the
     * window; there the IME inset below is what frees the covered area.
     *
     * Either way the fragment root ends up shorter, so the weighted WebView
     * shrinks, xterm refits and reports the smaller row count to the SSH
     * session, and the key bar comes to rest above the keyboard.
     *
     * With the keyboard down the navigation bar takes over: edge-to-edge lets
     * the window extend under it, so without its inset a three-button bar sits
     * on top of the key bar.
     */
    private void applyImeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            // An open keyboard covers the navigation bar, and its inset already
            // spans that area, so the two never add up. With adjustResize the
            // window is short enough on its own, so the IME height would leave a
            // gap the size of the keyboard.
            int bottom = ime > 0
                    ? (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ? 0 : ime)
                    : navBar;
            v.setPadding(0, 0, 0, bottom);
            return insets;
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = requireActivity().getWindow();
        previousSoftInputMode = window.getAttributes().softInputMode;
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // The rest of the app is portrait-only, but a terminal earns landscape:
        // it roughly doubles the column count, which is what long command lines
        // and full-screen programs need. The session outlives the rotation
        // because it belongs to the manager, not to this fragment, and replays
        // its scrollback when the recreated view attaches.
        previousOrientation = requireActivity().getRequestedOrientation();
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
    }

    @Override
    public void onStop() {
        // adjustPan is what the rest of the app expects; leaving adjustResize on
        // would change how every other screen reacts to the keyboard.
        requireActivity().getWindow().setSoftInputMode(previousSoftInputMode);
        // Likewise the orientation lock: leaving it open would let every other
        // screen rotate into a layout that was never designed for it.
        requireActivity().setRequestedOrientation(previousOrientation);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (session != null && sessionListener != null) {
            session.detach(sessionListener);
        }
        if (passwordDialog != null) {
            passwordDialog.dismiss();
            passwordDialog = null;
        }
        if (hostKeyDialog != null) {
            hostKeyDialog.dismiss();
            hostKeyDialog = null;
        }
        sessionListener = null;
        session = null;
        terminalReady = false;
        if (binding != null) {
            binding.terminalWebView.removeJavascriptInterface("AndroidSSH");
        }
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        serviceAccessor = null;
        super.onDetach();
    }

    private void wireKeyBar() {
        binding.reconnectButton.setOnClickListener(v -> {
            if (session != null && !SshSessionManager.get().reconnect(session.getId())) {
                printStatus(getString(R.string.ssh_netbird_not_running));
            }
        });
        binding.keyEsc.setOnClickListener(v -> sendBytes(new byte[]{0x1b}));
        binding.keyTab.setOnClickListener(v -> sendBytes(new byte[]{0x09}));
        binding.keyUp.setOnClickListener(v -> sendCursorKey('A'));
        binding.keyDown.setOnClickListener(v -> sendCursorKey('B'));
        binding.keyRight.setOnClickListener(v -> sendCursorKey('C'));
        binding.keyLeft.setOnClickListener(v -> sendCursorKey('D'));

        // The three most common control codes get their own key: arming Ctrl and
        // then hitting a letter needs the soft keyboard to deliver that letter,
        // which it does not always do.
        binding.keyCtrlC.setOnClickListener(v -> sendBytes(new byte[]{0x03}));
        binding.keyCtrlD.setOnClickListener(v -> sendBytes(new byte[]{0x04}));
        binding.keyCtrlZ.setOnClickListener(v -> sendBytes(new byte[]{0x1a}));

        // Characters that a phone keyboard buries behind a symbol page.
        binding.keyPipe.setOnClickListener(v -> sendBytes(new byte[]{'|'}));
        binding.keyTilde.setOnClickListener(v -> sendBytes(new byte[]{'~'}));
        binding.keySlash.setOnClickListener(v -> sendBytes(new byte[]{'/'}));
        binding.keyDash.setOnClickListener(v -> sendBytes(new byte[]{'-'}));
        binding.keyUnderscore.setOnClickListener(v -> sendBytes(new byte[]{'_'}));

        binding.keyCtrl.setOnClickListener(v -> {
            ctrlArmed = !ctrlArmed;
            updateModifierStyle(binding.keyCtrl, ctrlArmed);
        });
        binding.keyAlt.setOnClickListener(v -> {
            altArmed = !altArmed;
            updateModifierStyle(binding.keyAlt, altArmed);
        });

        binding.keyCopy.setOnClickListener(v -> copySelection());
        binding.keyPaste.setOnClickListener(v -> pasteClipboard());
    }

    private void updateModifierStyle(Button btn, boolean armed) {
        btn.setAlpha(armed ? 1f : 0.6f);
    }

    /**
     * Copies the terminal selection to the clipboard. evaluateJavascript hands
     * back a JSON value rather than a bare string, so the result has to be
     * unquoted before use.
     */
    private void copySelection() {
        if (binding == null) {
            return;
        }
        binding.terminalWebView.evaluateJavascript(
                "window.getTerminalSelection ? window.getTerminalSelection() : ''", value -> {
                    // The result arrives asynchronously, so the screen may be gone
                    // by now and there is no context left to copy into.
                    if (!isAdded()) {
                        return;
                    }
                    String text = decodeJsString(value);
                    if (text.isEmpty()) {
                        toast(R.string.ssh_copy_no_selection);
                        return;
                    }
                    ClipboardManager cm = (ClipboardManager)
                            requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("netbird-ssh", text));
                        toast(R.string.ssh_copy_done);
                    }
                });
    }

    /**
     * Sends the clipboard text through xterm's paste path so bracketed paste is
     * honoured when the remote program asked for it.
     */
    private void pasteClipboard() {
        ClipboardManager cm = (ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null
                || cm.getPrimaryClip().getItemCount() == 0) {
            toast(R.string.ssh_paste_empty);
            return;
        }
        CharSequence text = cm.getPrimaryClip().getItemAt(0).coerceToText(requireContext());
        if (text == null || text.length() == 0) {
            toast(R.string.ssh_paste_empty);
            return;
        }
        postToTerminal("window.pasteText(" + JSONObject.quote(text.toString()) + ");");
    }

    private static String decodeJsString(String jsonValue) {
        if (jsonValue == null || jsonValue.isEmpty() || "null".equals(jsonValue)) {
            return "";
        }
        try {
            // A bare JSON string is not valid top-level JSON for JSONObject, so
            // wrap it in an array to reuse the platform parser.
            return new JSONArray("[" + jsonValue + "]").optString(0, "");
        } catch (JSONException e) {
            return "";
        }
    }

    private void toast(int resId) {
        if (isAdded()) {
            Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Resolves a string for session callbacks, which arrive on a background
     * thread and can outlive the view. Falls back to the application context so a
     * late status line still reads in the user's language rather than crashing.
     */
    private String string(int resId, Object... args) {
        Context context = getContext();
        if (context == null) {
            context = appContext;
        }
        if (context == null) {
            return "";
        }
        return args.length == 0
                ? context.getString(resId)
                : context.getString(resId, args);
    }

    private void sendBytes(byte[] data) {
        if (session == null) {
            return;
        }
        byte[] payload = data;
        if (altArmed && payload.length > 0) {
            byte[] withAlt = new byte[payload.length + 1];
            withAlt[0] = 0x1b;
            System.arraycopy(payload, 0, withAlt, 1, payload.length);
            payload = withAlt;
            altArmed = false;
            disarmModifier(ModifierKey.ALT);
        }
        session.write(payload);
    }

    /**
     * Arrow keys go through the page rather than straight to the session:
     * only xterm knows whether the application asked for application cursor
     * keys mode, and the sequence differs between the two modes. The page
     * routes the chosen sequence back through the input bridge, so armed
     * modifiers apply the same way as to typed characters.
     */
    private void sendCursorKey(char ch) {
        postToTerminal("window.sendCursorKey('" + ch + "');");
    }

    private enum ModifierKey { ALT, CTRL }

    /**
     * Clears a modifier key's armed styling. Input arrives on the WebView
     * JavaScript thread, so the view write has to be posted to the main thread,
     * and the binding can be gone by the time it runs.
     */
    private void disarmModifier(ModifierKey key) {
        mainHandler.post(() -> {
            if (binding == null) {
                return;
            }
            updateModifierStyle(key == ModifierKey.ALT ? binding.keyAlt : binding.keyCtrl, false);
        });
    }

    private void postToTerminal(String script) {
        if (binding == null) {
            return;
        }
        mainHandler.post(() -> {
            if (binding == null) {
                return;
            }
            binding.terminalWebView.evaluateJavascript(script, null);
        });
    }

    private void writeToTerminal(byte[] data) {
        String b64 = Base64.encodeToString(data, Base64.NO_WRAP);
        postToTerminal("window.writeFromHost('" + b64 + "');");
    }

    private void clearTerminal() {
        postToTerminal("window.resetTerminal && window.resetTerminal();");
    }

    private void printStatus(String text) {
        // Server errors arrive with newlines in them, which a hand-rolled escape
        // turns into a broken statement that drops the status line entirely.
        postToTerminal("window.printStatus(" + JSONObject.quote(text) + ");");
    }

    private String requireString(String key, String fallback) {
        Bundle args = getArguments();
        if (args == null) {
            return fallback;
        }
        String v = args.getString(key);
        return v != null ? v : fallback;
    }

    private int requireInt(String key, int fallback) {
        Bundle args = getArguments();
        if (args == null) {
            return fallback;
        }
        return args.getInt(key, fallback);
    }

    private final class TerminalBridge {

        @JavascriptInterface
        public void onReady(int cols, int rows) {
            mainHandler.post(() -> attachOrCreateSession(cols, rows));
        }

        @JavascriptInterface
        public void onInput(String data) {
            byte[] payload = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (ctrlArmed && payload.length == 1) {
                byte b = payload[0];
                if (b >= 'a' && b <= 'z') {
                    payload = new byte[]{(byte) (b - 'a' + 1)};
                } else if (b >= 'A' && b <= 'Z') {
                    payload = new byte[]{(byte) (b - 'A' + 1)};
                }
                ctrlArmed = false;
                disarmModifier(ModifierKey.CTRL);
            }
            sendBytes(payload);
        }

        @JavascriptInterface
        public void onResize(int cols, int rows) {
            if (session != null) {
                session.resize(cols, rows);
            }
        }
    }

    private void attachOrCreateSession(int cols, int rows) {
        terminalReady = true;
        SshSessionManager manager = SshSessionManager.get();

        // A rotation recreates the view with the arguments it was opened with, so
        // the id of a session created here is written back into them. Without
        // that, a fragment opened with host arguments would dial a second
        // session to the same target every time the screen turned.
        String existingId = requireString(ARG_SESSION_ID, "");
        if (!existingId.isEmpty()) {
            SshSession existing = manager.get(existingId);
            if (existing == null) {
                printStatus(getString(R.string.ssh_status_session_gone));
                return;
            }
            attachToSession(existing);
            existing.resize(cols, rows);
            return;
        }

        if (serviceAccessor == null) {
            printStatus(getString(R.string.ssh_status_service_not_connected));
            return;
        }
        SSHClient client = serviceAccessor.newSSHClient();
        if (client == null) {
            printStatus(getString(R.string.ssh_netbird_not_running));
            return;
        }

        String host = requireString(ARG_HOST, "");
        int port = requireInt(ARG_PORT, 22);
        String user = requireString(ARG_USER, "");
        String password = requireString(ARG_PASSWORD, "");

        if (host.isEmpty()) {
            printStatus(getString(R.string.ssh_status_missing_host));
            return;
        }
        // The connect dialog will not navigate without one, so an empty name
        // means the arguments were built elsewhere and are incomplete.
        if (user.isEmpty()) {
            printStatus(getString(R.string.ssh_status_missing_user));
            return;
        }

        URLOpener urlOpener = serviceAccessor.getSSHURLOpener();
        SshSession created = manager.create(client, host, port, user, password, urlOpener);
        // Recorded so a recreated view re-attaches to this session instead of
        // creating another one, and the password is dropped now that it has been
        // handed over.
        Bundle args = getArguments();
        if (args != null) {
            args.putString(ARG_SESSION_ID, created.getId());
            args.remove(ARG_PASSWORD);
        }
        attachToSession(created);
        created.connectAsync(cols, rows);
    }

    private void attachToSession(SshSession s) {
        // onReady can fire more than once for one fragment, and a listener left
        // attached would keep feeding a view that has moved on.
        if (session != null && sessionListener != null) {
            session.detach(sessionListener);
        }
        this.session = s;
        sessionListener = new SessionListener();
        s.attach(sessionListener);
    }

    /** Called from session callbacks on a background thread. */
    private void showReconnectBar(boolean visible, String message) {
        mainHandler.post(() -> {
            if (binding == null) {
                return;
            }
            binding.reconnectBar.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible && message != null) {
                binding.reconnectMessage.setText(message);
            }
        });
    }

    /**
     * Asks for a password once the server has told us the NetBird key is not
     * enough. Reached from a session callback on a background thread, so the
     * dialog is posted to the main thread; the fragment may also be detached by
     * then, hence the isAdded check.
     */
    private void promptForPassword(String message) {
        mainHandler.post(() -> {
            if (!isAdded() || session == null) {
                return;
            }
            // The state is replayed on attach, so avoid stacking dialogs.
            if (passwordDialog != null && passwordDialog.isShowing()) {
                return;
            }
            boolean rejected = message != null && !message.isEmpty();
            // Only a rejection is worth a terminal line, since it stays readable
            // in the scrollback afterwards. The plain request needs none: the
            // dialog on screen already says it.
            if (rejected) {
                printStatus(string(R.string.ssh_status_wrong_password));
            }

            SshSession target = session;

            // The shared dialog layout carries the rounded background the app's
            // AlertDialogTheme expects: that theme makes the window transparent,
            // so a stock AlertDialog built without a custom view has no visible
            // body at all.
            View dialogView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_simple_edit_text, null);

            TextView title = dialogView.findViewById(R.id.text_title_dialog);
            title.setText(R.string.ssh_password_prompt_title);

            // The title says what is being asked, so this line only has to name
            // the target: an explanatory sentence in front of it pushes
            // user@host:port onto a second line. A rejection replaces it,
            // because the reason matters more than repeating the target.
            TextView label = dialogView.findViewById(R.id.text_label_dialog);
            label.setText(rejected ? message : target.getDisplayLabel());

            EditText input = dialogView.findViewById(R.id.edit_text_dialog);
            // setSingleLine resets the input type, so it must come first or the
            // password would be shown in the clear.
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            // The stock transformation briefly shows each typed character when
            // the system "show passwords" setting is on; this one renders a
            // bullet unconditionally so the password never appears on screen.
            input.setTransformationMethod(new NoPeekPasswordTransformation());
            input.setHint(R.string.ssh_dialog_password);

            MaterialButton connect = dialogView.findViewById(R.id.btn_ok_dialog);
            MaterialButton cancel = dialogView.findViewById(R.id.btn_cancel_dialog);
            connect.setText(R.string.ssh_dialog_connect);
            cancel.setText(R.string.ssh_dialog_cancel);

            AlertDialog dialog = new AlertDialog.Builder(
                    requireContext(), R.style.AlertDialogTheme)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            connect.setOnClickListener(v -> {
                target.retryWithPassword(input.getText().toString());
                dialog.dismiss();
            });
            // Cancelling ends the session; otherwise it would be parked in
            // NEEDS_PASSWORD with no way forward.
            cancel.setOnClickListener(v -> {
                target.cancelPasswordPrompt();
                dialog.dismiss();
            });

            // Enter submits, as pressing Connect would.
            input.setImeOptions(EditorInfo.IME_ACTION_GO);
            input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId != EditorInfo.IME_ACTION_GO) {
                    return false;
                }
                target.retryWithPassword(input.getText().toString());
                dialog.dismiss();
                return true;
            });

            dialog.show();
            passwordDialog = dialog;
        });
    }

    /**
     * Shows the presented host-key fingerprint for a regular server and, on
     * confirmation, retries with it trusted. Reached from a session callback on
     * a background thread, so it is posted to the main thread and guarded with
     * isAdded like the password prompt.
     */
    private void promptForHostKey(String fingerprint) {
        mainHandler.post(() -> {
            if (!isAdded() || session == null) {
                return;
            }
            if (hostKeyDialog != null && hostKeyDialog.isShowing()) {
                return;
            }

            SshSession target = session;

            View dialogView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_simple_edit_text, null);

            TextView title = dialogView.findViewById(R.id.text_title_dialog);
            title.setText(R.string.ssh_hostkey_prompt_title);

            TextView label = dialogView.findViewById(R.id.text_label_dialog);
            label.setText(getString(R.string.ssh_hostkey_prompt_message, target.getDisplayLabel()));

            // The fingerprint sits in the input field, read-only: it keeps the
            // dialog's rounded style and lets the user select and copy the value
            // to compare it out of band, while the field's own focus is off so no
            // keyboard pops up.
            EditText input = dialogView.findViewById(R.id.edit_text_dialog);
            input.setText(fingerprint);
            input.setKeyListener(null);
            input.setFocusable(false);
            input.setFocusableInTouchMode(false);
            input.setTextIsSelectable(true);

            MaterialButton confirm = dialogView.findViewById(R.id.btn_ok_dialog);
            MaterialButton cancel = dialogView.findViewById(R.id.btn_cancel_dialog);
            confirm.setText(R.string.ssh_hostkey_trust);
            cancel.setText(R.string.ssh_dialog_cancel);

            AlertDialog dialog = new AlertDialog.Builder(
                    requireContext(), R.style.AlertDialogTheme)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            confirm.setOnClickListener(v -> {
                target.retryWithHostKeyTrust(fingerprint);
                dialog.dismiss();
            });
            cancel.setOnClickListener(v -> {
                target.cancelHostKeyPrompt();
                dialog.dismiss();
            });

            dialog.show();
            hostKeyDialog = dialog;
        });
    }

    private static final class NoPeekPasswordTransformation extends PasswordTransformationMethod {
        @Override
        public CharSequence getTransformation(CharSequence source, View view) {
            return new BulletSequence(source);
        }

        private static final class BulletSequence implements CharSequence {
            private final CharSequence source;

            BulletSequence(CharSequence source) {
                this.source = source;
            }

            @Override
            public int length() {
                return source.length();
            }

            @Override
            public char charAt(int index) {
                return '•';
            }

            @NonNull
            @Override
            public CharSequence subSequence(int start, int end) {
                return new BulletSequence(source.subSequence(start, end));
            }
        }
    }

    private final class SessionListener implements SshSession.Listener {
        @Override
        public void onScrollback(byte[] data) {
            clearTerminal();
            writeToTerminal(data);
        }

        @Override
        public void onData(byte[] data) {
            writeToTerminal(data);
        }

        @Override
        public void onStateChange(SshSession.State state, String message) {
            switch (state) {
                case CONNECTING:
                    // A reconnect starts here rather than in the create path,
                    // so this is the only notice the user gets for it.
                    if (session != null) {
                        printStatus(string(R.string.ssh_status_connecting,
                                session.getDisplayLabel()));
                    }
                    showReconnectBar(false, null);
                    break;
                case CONNECTED:
                    // Drop the connect chatter so the prompt starts clean, but
                    // only on the very first connect: a reconnect keeps the
                    // earlier output so it stays scrollable, and a re-attach
                    // would otherwise wipe the history it just restored.
                    if (terminalReady && session != null && !session.hasEverConnected()) {
                        clearTerminal();
                    }
                    showReconnectBar(false, null);
                    break;
                case NEEDS_PASSWORD:
                    promptForPassword(message);
                    break;
                case NEEDS_HOSTKEY_CONFIRM:
                    promptForHostKey(message);
                    break;
                case CLOSED: {
                    String text = message == null || message.isEmpty()
                            ? string(R.string.ssh_status_session_closed)
                            : string(R.string.ssh_status_session_closed_reason, message);
                    printStatus(text);
                    showReconnectBar(true, text);
                    break;
                }
                case ERROR: {
                    Log.w(LOGTAG, "session error: " + message);
                    String text = string(R.string.ssh_status_error, message);
                    printStatus(text);
                    showReconnectBar(true, text);
                    break;
                }
                default:
                    break;
            }
        }
    }
}
