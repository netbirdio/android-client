package io.netbird.client.ui.ssh;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean ctrlArmed = false;
    private boolean altArmed = false;
    private boolean terminalReady = false;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
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

        wireKeyBar();
    }

    @Override
    public void onDestroyView() {
        if (session != null && sessionListener != null) {
            session.detach(sessionListener);
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
        binding.keyEsc.setOnClickListener(v -> sendBytes(new byte[]{0x1b}));
        binding.keyTab.setOnClickListener(v -> sendBytes(new byte[]{0x09}));
        binding.keyUp.setOnClickListener(v -> sendBytes(new byte[]{0x1b, '[', 'A'}));
        binding.keyDown.setOnClickListener(v -> sendBytes(new byte[]{0x1b, '[', 'B'}));
        binding.keyRight.setOnClickListener(v -> sendBytes(new byte[]{0x1b, '[', 'C'}));
        binding.keyLeft.setOnClickListener(v -> sendBytes(new byte[]{0x1b, '[', 'D'}));
        binding.keyCtrl.setOnClickListener(v -> {
            ctrlArmed = !ctrlArmed;
            updateModifierStyle(binding.keyCtrl, ctrlArmed);
        });
        binding.keyAlt.setOnClickListener(v -> {
            altArmed = !altArmed;
            updateModifierStyle(binding.keyAlt, altArmed);
        });
    }

    private void updateModifierStyle(Button btn, boolean armed) {
        btn.setAlpha(armed ? 1f : 0.6f);
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
            updateModifierStyle(binding.keyAlt, false);
        }
        session.write(payload);
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
        String escaped = text.replace("\\", "\\\\").replace("'", "\\'");
        postToTerminal("window.printStatus('" + escaped + "');");
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
                mainHandler.post(() -> updateModifierStyle(binding.keyCtrl, false));
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

        String existingId = requireString(ARG_SESSION_ID, "");
        if (!existingId.isEmpty()) {
            SshSession existing = manager.get(existingId);
            if (existing == null) {
                printStatus("Session not found (it may have been closed).");
                return;
            }
            attachToSession(existing);
            existing.resize(cols, rows);
            return;
        }

        if (serviceAccessor == null) {
            printStatus("VPN service not connected");
            return;
        }
        SSHClient client = serviceAccessor.newSSHClient();
        if (client == null) {
            printStatus("NetBird is not running");
            return;
        }

        String host = requireString(ARG_HOST, "");
        int port = requireInt(ARG_PORT, 22022);
        String user = requireString(ARG_USER, "pzoli");
        String password = requireString(ARG_PASSWORD, "");

        if (host.isEmpty()) {
            printStatus("Missing host");
            return;
        }

        URLOpener urlOpener = serviceAccessor.getSSHURLOpener();
        SshSession created = manager.create(client, host, port, user, password, urlOpener);
        attachToSession(created);
        printStatus("Connecting to " + user + "@" + host + ":" + port + " ...");
        created.connectAsync(cols, rows);
    }

    private void attachToSession(SshSession s) {
        this.session = s;
        sessionListener = new SessionListener();
        s.attach(sessionListener);
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
                case CONNECTED:
                    if (terminalReady) {
                        printStatus("Connected");
                    }
                    break;
                case CLOSED:
                    printStatus("Session closed" + (message == null || message.isEmpty() ? "" : ": " + message));
                    break;
                case ERROR:
                    Log.w(LOGTAG, "session error: " + message);
                    printStatus("Error: " + message);
                    break;
                default:
                    break;
            }
        }
    }
}
