package io.netbird.client.e2e;

import io.netbird.client.MainActivity;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.net.DnsResolver;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertNotNull;

/**
 * Shared machinery for the on-device client e2e tests (the Android port of the
 * Robot {@code client-tests.robot} suite). Each test creates one of these from
 * its {@link MainActivity}, then composes the building blocks it needs:
 *
 * <ul>
 *   <li>{@link #grantVpnConsent()} — pre-approve the VPN so the engine starts
 *       without the system consent dialog (headless-friendly)</li>
 *   <li>{@link #connectAndAwait(long)} — flip the Home screen's connect
 *       toggle and wait for the status text to read Connected, like the Robot
 *       {@code Wait For Peer Ready}</li>
 *   <li>{@link #waitForPing(String, long)} / {@link #pingOnce(String)} — the
 *       Android equivalent of the Robot {@code Get Ping Command And Regex}
 *       check (ICMP through the tunnel)</li>
 *   <li>{@link #tcpConnects(String, int, int)} — the equivalent of the Robot
 *       {@code Open Connection ... port=N} Telnet check (TCP reachability)</li>
 * </ul>
 *
 * <p>Connecting is driven from the Home screen's toggle and the wait watches
 * the on-screen status text — trigger and feedback both go through the same UI
 * a user sees. Network probes run as shell commands / plain sockets so they
 * observe the tunnel exactly as a user's traffic would.
 */
final class VpnTestHarness {

    private static final long UI_TIMEOUT_MS = 5_000;

    private static final String TAG = "NBVpnHarness";

    private final UiDevice device;
    private final UiAutomation uiAutomation;

    VpnTestHarness(MainActivity activity) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
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
     * Connect by tapping the Home screen's connect toggle — the same control a
     * user flips — and wait for the on-screen status to read "Connected", the
     * same feedback a user watches. No app internals are touched; the suite
     * runs on an English locale, so the literal status text is matched.
     *
     * @return true if the status showed Connected within the timeout
     */
    boolean connectAndAwait(long timeoutSec) throws InterruptedException {
        UiObject2 homeTab = device.wait(
                Until.findObject(By.res(LoginFlow.PACKAGE, "nav_home")), UI_TIMEOUT_MS);
        assertNotNull("nav_home tab must be present", homeTab);
        homeTab.click();

        UiObject2 toggle = device.wait(
                Until.findObject(By.res(LoginFlow.PACKAGE, "btn_connect")), UI_TIMEOUT_MS);
        assertNotNull("btn_connect must be present on the Home screen", toggle);
        if (!toggle.isChecked()) {
            Log.i(TAG, "Tapping the connect toggle");
            toggle.click();
        } else {
            Log.i(TAG, "Connect toggle is already on");
        }

        boolean connected = device.wait(
                Until.hasObject(By.res(LoginFlow.PACKAGE, "text_connection_status")
                        .text("Connected")),
                timeoutSec * 1000L);
        Log.i(TAG, connected ? "Status shows Connected"
                : "Status did not reach Connected within " + timeoutSec + "s");
        return connected;
    }

    /**
     * Toggle the emulator's virtual WiFi transport. {@code svc wifi} works on
     * the API 30 image the e2e workflow runs on (removed in API 31+, where
     * {@code cmd wifi set-wifi-enabled} replaces it).
     */
    void setWifi(boolean enabled) {
        String out = shell("svc wifi " + (enabled ? "enable" : "disable"));
        Log.i(TAG, "svc wifi " + (enabled ? "enable" : "disable") + " -> " + out.trim());
    }

    /** Toggle the emulator's virtual cellular data transport. */
    void setMobileData(boolean enabled) {
        String out = shell("svc data " + (enabled ? "enable" : "disable"));
        Log.i(TAG, "svc data " + (enabled ? "enable" : "disable") + " -> " + out.trim());
    }

    /**
     * Wait until the Home screen's status text shows exactly {@code expected}
     * (English locale, like {@link #connectAndAwait(long)}).
     */
    boolean awaitStatusText(String expected, long timeoutSec) {
        boolean shown = device.wait(
                Until.hasObject(By.res(LoginFlow.PACKAGE, "text_connection_status")
                        .text(expected)),
                timeoutSec * 1000L);
        Log.i(TAG, "Status '" + expected + "' " + (shown ? "shown" : "NOT shown")
                + " within " + timeoutSec + "s");
        return shown;
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
            logJavaResolution(target);
            Thread.sleep(3000);
        }
        Log.w(TAG, "Ping to " + target + " failed after " + attempt + " attempts");
        return false;
    }

    /**
     * Log what the app's own resolver says about {@code target}. The shell ping
     * resolves as the shell uid; this resolves in-process, which is guaranteed
     * to route through the VPN. Comparing the two in the logcat artifact tells a
     * DNS-zone problem apart from a which-network-resolved-it problem — and
     * unlike ping, an exception here carries the resolver's actual error.
     */
    private void logJavaResolution(String target) {
        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName(target);
            Log.i(TAG, "InetAddress.getByName(" + target + ") = " + addr.getHostAddress());
        } catch (Exception e) {
            Log.i(TAG, "InetAddress.getByName(" + target + ") failed: " + e);
        }
    }

    /** Ping a host (FQDN or IP) once through the tunnel. */
    boolean pingOnce(String target) {
        return pingOnce(target, PING_W_SEC);
    }

    /**
     * Ping a host once with a caller-chosen per-attempt timeout. The network
     * transition tests probe on a ~1s cadence to measure outage windows, so
     * they need a tighter timeout than the default {@link #PING_W_SEC}.
     */
    boolean pingOnce(String target, int timeoutSec) {
        String output = shell(String.format("ping -c 1 -W %d %s", timeoutSec, target));
        // The full output goes to logcat — the CI artifact — so a failure shows
        // exactly what happened: the resolved address in the "PING x (a.b.c.d)"
        // header, an unknown-host error, or 100% loss to a resolved peer.
        Log.i(TAG, "ping " + target + " output:\n" + output.trim());
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
        Log.i(TAG, "resolve " + host + " output:\n" + out.trim());
        Matcher m = PING_RESOLVED_IP.matcher(out);
        return m.find() ? m.group(1) : null;
    }

    private static final Pattern PING_RESOLVED_IP =
            Pattern.compile("\\(([0-9]{1,3}(?:\\.[0-9]{1,3}){3})\\)");

    /**
     * Resolve {@code host} to its NetBird address, retrying until one shows up
     * or {@code timeoutSec} elapses. Returns null if it never resolved.
     *
     * <p>The lookup deliberately bypasses the device resolver cache. The
     * NetBird DNS zone is registered a fraction of a second after the status
     * reads Connected, so a query issued in that window reaches the upstream
     * forwarder and comes back NXDOMAIN — which Android then negative-caches
     * for the record's SOA TTL. Every later lookup of the same name is served
     * from that cache and skips straight to the search-domain suffixes, so a
     * plain retry loop stays poisoned for the whole test. Each attempt here is
     * a real query instead.
     *
     * <p>An answer only counts when it lands in the NetBird CGNAT range: the
     * upstream forwarder may hand back an unrelated address for a name that
     * only exists inside the tunnel.
     */
    String waitForPeerIp(String host, long timeoutSec) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (timeoutSec * 1000L);
        String last = null;
        while (System.currentTimeMillis() < deadline) {
            last = queryUncached(host, DNS_QUERY_TIMEOUT_MS);
            if (last != null && isNetBirdAddress(last)) {
                Log.i(TAG, "Resolved " + host + " -> " + last);
                return last;
            }
            Thread.sleep(1000);
        }
        Log.w(TAG, "Resolve " + host + " yielded no NetBird address within " + timeoutSec
                + "s (last: " + last + ")");
        return null;
    }

    /**
     * One cache-bypassing A lookup. Falls back to the ping-based
     * {@link #resolve(String)} below API 29, where the flag does not exist —
     * the CI emulator is API 30, so the fallback is for local runs on old
     * devices only.
     */
    private String queryUncached(String host, int timeoutMs) throws InterruptedException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return resolve(host);
        }
        ArrayBlockingQueue<List<InetAddress>> answers = new ArrayBlockingQueue<>(1);
        CancellationSignal cancellation = new CancellationSignal();
        DnsResolver.getInstance().query(null, host, DnsResolver.FLAG_NO_CACHE_LOOKUP,
                Runnable::run, cancellation,
                new DnsResolver.Callback<List<InetAddress>>() {
                    @Override
                    public void onAnswer(List<InetAddress> answer, int rcode) {
                        answers.offer(answer);
                    }

                    @Override
                    public void onError(DnsResolver.DnsException e) {
                        Log.d(TAG, "uncached lookup of " + host + " failed: " + e.getMessage());
                        answers.offer(Collections.emptyList());
                    }
                });
        List<InetAddress> answer = answers.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (answer == null) {
            cancellation.cancel();
            return null;
        }
        for (InetAddress address : answer) {
            if (address instanceof Inet4Address) {
                return address.getHostAddress();
            }
        }
        return null;
    }

    /** True for addresses in the NetBird CGNAT range, 100.64.0.0/10. */
    private static boolean isNetBirdAddress(String ip) {
        String[] octets = ip.split("\\.");
        if (octets.length != 4) {
            return false;
        }
        try {
            return Integer.parseInt(octets[0]) == 100
                    && Integer.parseInt(octets[1]) >= 64
                    && Integer.parseInt(octets[1]) <= 127;
        } catch (NumberFormatException e) {
            return false;
        }
    }

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
        return httpGet(urlString, 10_000);
    }

    /**
     * {@link #httpGet(String)} with a caller-chosen timeout. The network
     * transition tests probe with short timeouts so a request hung on a dead
     * route cannot blur the recovery-time measurement.
     */
    String httpGet(String urlString, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
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
        return waitForHttpBodyContains(urlString, expectedSubstring, timeoutSec, 10_000, 3000);
    }

    /**
     * {@link #waitForHttpBodyContains(String, String, long)} with caller-chosen
     * per-probe timeout and poll interval, for recovery-time measurements that
     * need finer granularity than the 10s/3s defaults.
     */
    boolean waitForHttpBodyContains(String urlString, String expectedSubstring, long timeoutSec,
                                    int probeTimeoutMs, long pollMs) throws InterruptedException {
        Pattern p = Pattern.compile("(^|[^0-9.])" + Pattern.quote(expectedSubstring) + "([^0-9.]|$)");
        long deadline = System.currentTimeMillis() + (timeoutSec * 1000L);
        String last = null;
        while (System.currentTimeMillis() < deadline) {
            last = httpGet(urlString, probeTimeoutMs);
            if (last != null && p.matcher(last).find()) {
                Log.i(TAG, "GET " + urlString + " body matched " + expectedSubstring);
                return true;
            }
            Thread.sleep(pollMs);
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

    /** Per-attempt timeout for a cache-bypassing DNS query. */
    private static final int DNS_QUERY_TIMEOUT_MS = 5_000;

    /** Per-attempt ping timeout, in seconds. */
    private static final int PING_W_SEC = 5;
}
