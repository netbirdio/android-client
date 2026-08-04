package io.netbird.client.e2e;

import io.netbird.client.MainActivity;
import io.netbird.client.StateListener;
import io.netbird.client.StateListenerAdapter;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared machinery for the on-device client e2e tests (the Android port of the
 * Robot {@code client-tests.robot} suite). Each test creates one of these from
 * its {@link MainActivity}, then composes the building blocks it needs:
 *
 * <ul>
 *   <li>{@link #grantVpnConsent()} — pre-approve the VPN so the engine starts
 *       without the system consent dialog (headless-friendly)</li>
 *   <li>{@link #connectAndAwait(long)} — start the engine via the app API and
 *       wait for {@code onConnected}, like the Robot {@code Wait For Peer
 *       Ready}</li>
 *   <li>{@link #waitForPing(String, long)} / {@link #pingOnce(String)} — the
 *       Android equivalent of the Robot {@code Get Ping Command And Regex}
 *       check (ICMP through the tunnel)</li>
 *   <li>{@link #tcpConnects(String, int, int)} — the equivalent of the Robot
 *       {@code Open Connection ... port=N} Telnet check (TCP reachability)</li>
 * </ul>
 *
 * <p>Connecting goes through {@link MainActivity#switchConnection(boolean)} and
 * a {@link StateListener}, not the Lottie connect button, so it is robust to UI
 * timing. Network probes run as shell commands / plain sockets so they observe
 * the tunnel exactly as a user's traffic would.
 */
final class VpnTestHarness {

    private static final String TAG = "NBVpnHarness";

    private final MainActivity activity;
    private final UiDevice device;
    private final UiAutomation uiAutomation;

    VpnTestHarness(MainActivity activity) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        this.activity = activity;
        this.device = UiDevice.getInstance(instrumentation);
        this.uiAutomation = instrumentation.getUiAutomation();
    }

    UiDevice device() {
        return device;
    }

    /** Show touch feedback on screen so recordings reveal what the test taps. */
    void enableTouchVisualization() {
        shell("settings put system show_touches 1");
        shell("settings put system pointer_location 1");
    }

    void disableTouchVisualization() {
        shell("settings put system show_touches 0");
        shell("settings put system pointer_location 0");
    }

    /**
     * Pre-grant the VPN consent (ACTIVATE_VPN appop) for this package so the
     * VpnService starts without a system dialog. Best-effort: if the command is
     * unavailable the test still works as long as consent was granted before.
     */
    void grantVpnConsent() {
        String out = shell("appops set " + LoginFlow.PACKAGE + " ACTIVATE_VPN allow");
        Log.i(TAG, "appops ACTIVATE_VPN -> " + out.trim());
    }

    /**
     * Start the engine via the app's own API and block until a state listener
     * reports {@code onConnected}, or the timeout elapses.
     *
     * @return true if the engine reported connected within the timeout
     */
    boolean connectAndAwait(long timeoutSec) throws InterruptedException {
        CountDownLatch connectedLatch = new CountDownLatch(1);

        // StateListenerAdapter stubs out the callbacks we don't care about, so
        // this keeps compiling as the interface grows.
        StateListener listener = new StateListenerAdapter() {
            @Override public void onConnected() {
                Log.i(TAG, "Engine reported connected");
                connectedLatch.countDown();
            }
            @Override public void onPeersListChanged(long count) {
                Log.i(TAG, "Peers list changed: " + count);
            }
        };

        activity.runOnUiThread(() -> activity.registerServiceStateListener(listener));
        try {
            activity.runOnUiThread(() -> activity.switchConnection(true));
            return connectedLatch.await(timeoutSec, TimeUnit.SECONDS);
        } finally {
            activity.runOnUiThread(() -> activity.unregisterServiceStateListener(listener));
        }
    }

    /** Retry {@link #pingOnce(String)} until it succeeds or the timeout elapses. */
    boolean waitForPing(String target, long timeoutSec) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (timeoutSec * 1000L);
        int attempt = 0;
        while (System.currentTimeMillis() < deadline) {
            attempt++;
            if (pingOnce(target)) {
                Log.i(TAG, "Ping to " + target + " succeeded on attempt " + attempt);
                return true;
            }
            Thread.sleep(3000);
        }
        Log.w(TAG, "Ping to " + target + " failed after " + attempt + " attempts");
        return false;
    }

    /** Ping a host (FQDN or IP) once through the tunnel. */
    boolean pingOnce(String target) {
        String output = shell(String.format("ping -c 1 -W %d %s", PING_W_SEC, target));
        if (output.contains("1 received") || output.contains("1 packets received")) {
            return true;
        }
        // Some ROMs print a different summary; fall back to a positive RTT line.
        return output.contains("time=") && !output.contains("100% packet loss");
    }

    /**
     * Try to open a TCP connection to {@code host:port}, the equivalent of the
     * Robot {@code Open Connection ... port=N connection_timeout=1} Telnet
     * check. Returns true if the socket connects within {@code timeoutMs}.
     */
    boolean tcpConnects(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            Log.i(TAG, "TCP connect to " + host + ":" + port + " succeeded");
            return true;
        } catch (IOException e) {
            Log.i(TAG, "TCP connect to " + host + ":" + port + " failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Resolve {@code host} via {@code ping}, which is the only resolver tool
     * present on these devices (no nslookup/getent on Android 11). ping prints
     * the resolved address in its first line — {@code PING host (1.2.3.4) ...} —
     * and does the lookup through the device resolver / NetBird VpnService DNS,
     * like a user's traffic. Returns that address, or null if it didn't resolve.
     */
    String resolve(String host) {
        String out = shell("ping -c 1 -W 2 " + host);
        Matcher m = PING_RESOLVED_IP.matcher(out);
        return m.find() ? m.group(1) : null;
    }

    private static final Pattern PING_RESOLVED_IP =
            Pattern.compile("\\(([0-9]{1,3}(?:\\.[0-9]{1,3}){3})\\)");

    /** Retry {@link #resolve(String)} until it returns {@code expectedIp} or the timeout elapses. */
    boolean waitForResolve(String host, String expectedIp, long timeoutSec) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (timeoutSec * 1000L);
        String last = null;
        while (System.currentTimeMillis() < deadline) {
            last = resolve(host);
            if (expectedIp.equals(last)) {
                Log.i(TAG, "Resolved " + host + " -> " + last);
                return true;
            }
            Thread.sleep(3000);
        }
        Log.w(TAG, "Resolve " + host + " did not yield " + expectedIp + " (last: " + last + ")");
        return false;
    }

    /**
     * Issue a real HTTPS GET and return the response body, or null on failure.
     * Used by the exit-node test to ask {@code https://api.ipify.org} what the
     * public egress IP is — the Android equivalent of the Robot
     * {@code GET https://api.ipify.org}. With the tunnel routing through an
     * exit node, the returned IP is the exit node's, not the device's.
     */
    String httpGet(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.i(TAG, "GET " + urlString + " -> HTTP " + code);
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
                return body.toString();
            }
        } catch (IOException e) {
            Log.i(TAG, "GET " + urlString + " failed: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Poll {@link #httpGet(String)} until the response contains {@code
     * expectedSubstring} (word-boundaried for dotted-quad IPs) or the timeout
     * elapses.
     */
    boolean waitForHttpBodyContains(String urlString, String expectedSubstring, long timeoutSec)
            throws InterruptedException {
        Pattern p = Pattern.compile("(^|[^0-9.])" + Pattern.quote(expectedSubstring) + "([^0-9.]|$)");
        long deadline = System.currentTimeMillis() + (timeoutSec * 1000L);
        String last = null;
        while (System.currentTimeMillis() < deadline) {
            last = httpGet(urlString);
            if (last != null && p.matcher(last).find()) {
                Log.i(TAG, "GET " + urlString + " body matched " + expectedSubstring);
                return true;
            }
            Thread.sleep(3000);
        }
        Log.w(TAG, "GET " + urlString + " never matched " + expectedSubstring + " (last: " + last + ")");
        return false;
    }

    /** Run a shell command via the instrumentation UiAutomation and return stdout. */
    String shell(String command) {
        try {
            ParcelFileDescriptor pfd = uiAutomation.executeShellCommand(command);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(pfd)))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
                return output.toString();
            }
        } catch (Exception e) {
            Log.w(TAG, "Shell command failed: " + command + " - " + e.getMessage());
            return "";
        }
    }

    /** Per-attempt ping timeout, in seconds. */
    private static final int PING_W_SEC = 5;
}
