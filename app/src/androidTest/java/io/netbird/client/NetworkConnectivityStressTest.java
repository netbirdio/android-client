package io.netbird.client;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Connectivity stress test for VPN resilience.
 *
 * Automatically detects whether it is running on a real device or emulator and
 * uses the appropriate network control strategy:
 *
 * <h3>Real device</h3>
 * Uses {@code svc wifi/data} and airplane mode shell commands to toggle
 * WiFi, mobile data, and airplane mode independently.
 *
 * <h3>Emulator</h3>
 * Uses the emulator console (telnet to {@code 10.0.2.2:5554}) to run
 * {@code network disconnect / network connect} and {@code network delay / speed}
 * commands that actually cut the virtual network at the host level.
 *
 * <h3>Prerequisites</h3>
 * <ul>
 *   <li>The app must be already logged in / authenticated</li>
 *   <li>A reachable VPN peer IP must be configured in {@link #PING_TARGET}</li>
 *   <li><b>Real device:</b> both WiFi and mobile data available;
 *       grant {@code adb shell pm grant io.netbird.client android.permission.WRITE_SECURE_SETTINGS}</li>
 *   <li><b>Emulator:</b> copy the auth token from {@code ~/.emulator_console_auth_token}
 *       into the {@link #EMULATOR_AUTH_TOKEN} constant, or clear the file to disable auth</li>
 * </ul>
 *
 * <h3>Run with</h3>
 * <pre>
 * ./gradlew connectedAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=io.netbird.client.NetworkConnectivityStressTest
 * </pre>
 */
@RunWith(AndroidJUnit4.class)
public class NetworkConnectivityStressTest {

    private static final String TAG = "NBConnStressTest";

    // --- Configuration -----------------------------------------------------------

    /** IP to ping through the VPN tunnel to verify connectivity. */
    private static final String PING_TARGET = "100.72.116.70";

    /** How many random disruption cycles to run. */
    private static final int NUM_CYCLES = 20;

    /** Maximum seconds to wait for VPN to recover after a disruption. */
    private static final int VPN_RECOVERY_TIMEOUT_SEC = 90;

    /** Seconds to wait after network state change before checking recovery. */
    private static final int SETTLE_DELAY_SEC = 5;

    /** Max random extra wait (seconds) to simulate variable real-world timing. */
    private static final int MAX_RANDOM_EXTRA_WAIT_SEC = 15;

    /** Ping timeout in seconds for a single ping attempt. */
    private static final int PING_TIMEOUT_SEC = 5;

    /**
     * Emulator console auth token. On the host machine run:
     * {@code cat ~/.emulator_console_auth_token}
     * and paste the value here. If the file is empty, leave this empty too.
     */
    private static final String EMULATOR_AUTH_TOKEN = "";

    /** Emulator console port (default 5554 for first emulator instance). */
    private static final int EMULATOR_CONSOLE_PORT = 5554;

    // --- End configuration -------------------------------------------------------

    private UiDevice device;
    private UiAutomation uiAutomation;
    private Random random;
    private boolean isEmulator;
    private final List<String> testLog = new ArrayList<>();

    @SuppressWarnings("deprecation")
    @Rule
    public ActivityTestRule<MainActivity> activityRule =
            new ActivityTestRule<>(MainActivity.class, true, true);

    @Before
    public void setUp() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        device = UiDevice.getInstance(instrumentation);
        uiAutomation = instrumentation.getUiAutomation();
        random = new Random();
        isEmulator = detectEmulator();

        log("=== NetworkConnectivityStressTest started ===");
        log("Platform: " + (isEmulator ? "EMULATOR" : "REAL DEVICE"));
        log("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
        log("Ping target: " + PING_TARGET);
        log("Cycles: " + NUM_CYCLES);
        log("Recovery timeout: " + VPN_RECOVERY_TIMEOUT_SEC + "s");

        // Ensure we start with a clean network state
        restoreAllNetworks();
        Thread.sleep(3000);

        // Wait for initial VPN connection
        log("Waiting for initial VPN connection...");
        connectVpnViaUI();
        assertTrue("VPN must be connected before stress test begins",
                waitForPingSuccess(VPN_RECOVERY_TIMEOUT_SEC));
        log("Initial VPN connection verified via ping");
    }

    @After
    public void tearDown() {
        log("=== Restoring network state ===");
        try {
            restoreAllNetworks();
        } catch (Exception e) {
            log("WARNING: Failed to restore network state: " + e.getMessage());
        }

        log("=== Test log summary ===");
        for (String entry : testLog) {
            Log.i(TAG, entry);
        }
    }

    @Test
    public void testVpnSurvivesRandomNetworkDisruptions() throws Exception {
        int passCount = 0;
        int failCount = 0;

        for (int cycle = 1; cycle <= NUM_CYCLES; cycle++) {
            DisruptionType disruption = pickRandomDisruption();
            log(String.format("--- Cycle %d/%d: %s ---", cycle, NUM_CYCLES, disruption.name));

            try {
                // Apply disruption
                disruption.apply.run();

                // Random wait to simulate real-world timing variability
                int extraWait = random.nextInt(MAX_RANDOM_EXTRA_WAIT_SEC + 1);
                int totalWait = SETTLE_DELAY_SEC + extraWait;
                log(String.format("  Disruption applied. Waiting %ds before recovery...", totalWait));
                Thread.sleep(totalWait * 1000L);

                // Restore connectivity
                disruption.restore.run();
                log("  Network restored. Waiting for VPN recovery...");
                Thread.sleep(SETTLE_DELAY_SEC * 1000L);

                // Verify VPN recovers
                boolean recovered = waitForPingSuccess(VPN_RECOVERY_TIMEOUT_SEC);
                if (recovered) {
                    passCount++;
                    log(String.format("  PASS - VPN recovered after %s", disruption.name));
                } else {
                    failCount++;
                    log(String.format("  FAIL - VPN did NOT recover after %s (timeout %ds)",
                            disruption.name, VPN_RECOVERY_TIMEOUT_SEC));
                }
            } catch (Exception e) {
                failCount++;
                log(String.format("  ERROR during cycle %d: %s", cycle, e.getMessage()));
            }

            // Brief pause between cycles
            Thread.sleep(2000);
        }

        log(String.format("=== Results: %d/%d passed, %d failed ===",
                passCount, NUM_CYCLES, failCount));

        if (failCount > 0) {
            fail(String.format("VPN failed to recover in %d out of %d disruption cycles. " +
                    "Check logcat tag '%s' for details.", failCount, NUM_CYCLES, TAG));
        }
    }

    // --- Disruption scenarios ----------------------------------------------------

    private DisruptionType pickRandomDisruption() {
        if (isEmulator) {
            return pickEmulatorDisruption();
        }
        return pickRealDeviceDisruption();
    }

    private DisruptionType pickRealDeviceDisruption() {
        DisruptionType[] types = {
                // Scenario 1: WiFi off -> mobile only (leaving office)
                new DisruptionType("WiFi->Mobile (leave office)",
                        this::disableWifi,
                        this::enableWifi),

                // Scenario 2: Mobile off -> WiFi only (common at home)
                new DisruptionType("Mobile->WiFi (at home)",
                        this::disableMobileData,
                        this::enableMobileData),

                // Scenario 3: All connectivity lost temporarily (elevator/tunnel)
                new DisruptionType("All networks lost (tunnel/elevator)",
                        this::enableAirplaneMode,
                        this::restoreAllNetworks),

                // Scenario 4: WiFi switch (disconnect from one, connect to another)
                new DisruptionType("WiFi reconnect (switch network)",
                        () -> { disableWifi(); sleep(2000); enableWifi(); },
                        () -> { /* already restored */ }),

                // Scenario 5: Rapid WiFi flapping (unstable connection)
                new DisruptionType("WiFi flapping (unstable)",
                        () -> {
                            for (int i = 0; i < 3; i++) {
                                disableWifi();
                                sleep(1500);
                                enableWifi();
                                sleep(1500);
                            }
                        },
                        this::enableWifi),

                // Scenario 6: Airplane mode toggle (quick on/off)
                new DisruptionType("Airplane mode toggle",
                        () -> { enableAirplaneMode(); sleep(5000); disableAirplaneMode(); },
                        () -> { enableWifi(); enableMobileData(); }),

                // Scenario 7: Mobile data flapping
                new DisruptionType("Mobile data flapping",
                        () -> {
                            for (int i = 0; i < 3; i++) {
                                disableMobileData();
                                sleep(1000);
                                enableMobileData();
                                sleep(1000);
                            }
                        },
                        this::enableMobileData),

                // Scenario 8: Long network outage (30+ seconds with no network)
                new DisruptionType("Long outage (30s no network)",
                        () -> { enableAirplaneMode(); sleep(30000); },
                        this::restoreAllNetworks),

                // Scenario 9: WiFi off then mobile off then both back
                new DisruptionType("Sequential network loss",
                        () -> { disableWifi(); sleep(3000); disableMobileData(); },
                        () -> { enableMobileData(); sleep(2000); enableWifi(); }),

                // Scenario 10: Rapid airplane mode flapping
                new DisruptionType("Airplane mode flapping",
                        () -> {
                            for (int i = 0; i < 2; i++) {
                                enableAirplaneMode();
                                sleep(3000);
                                disableAirplaneMode();
                                sleep(3000);
                            }
                        },
                        this::restoreAllNetworks),
        };

        return types[random.nextInt(types.length)];
    }

    private DisruptionType pickEmulatorDisruption() {
        DisruptionType[] types = {
                // Scenario 1: Full network disconnect (like losing all signal)
                new DisruptionType("EMU: Network disconnect",
                        this::emuNetworkDisconnect,
                        this::emuNetworkConnect),

                // Scenario 2: Network disconnect with long outage
                new DisruptionType("EMU: Long outage (30s)",
                        () -> { emuNetworkDisconnect(); sleep(30000); },
                        this::emuNetworkConnect),

                // Scenario 3: Rapid connect/disconnect flapping
                new DisruptionType("EMU: Network flapping (3x)",
                        () -> {
                            for (int i = 0; i < 3; i++) {
                                emuNetworkDisconnect();
                                sleep(1500);
                                emuNetworkConnect();
                                sleep(1500);
                            }
                        },
                        this::emuNetworkConnect),

                // Scenario 4: Extreme latency (simulates very poor connection)
                new DisruptionType("EMU: Extreme latency (5s delay)",
                        () -> emuNetworkDelay("10000"),
                        () -> emuNetworkDelay("0")),

                // Scenario 5: Very slow speed (GPRS-level)
                new DisruptionType("EMU: GPRS speed throttle",
                        () -> emuNetworkSpeed("gsm"),
                        () -> emuNetworkSpeed("full")),

                // Scenario 6: Disconnect then slow reconnect
                new DisruptionType("EMU: Disconnect then slow reconnect",
                        () -> {
                            emuNetworkDisconnect();
                            sleep(5000);
                            emuNetworkConnect();
                            emuNetworkSpeed("gsm");
                            sleep(5000);
                        },
                        () -> { emuNetworkSpeed("full"); }),

                // Scenario 7: Rapid flapping with long disconnect
                new DisruptionType("EMU: Flap then long disconnect (20s)",
                        () -> {
                            emuNetworkDisconnect();
                            sleep(1000);
                            emuNetworkConnect();
                            sleep(1000);
                            emuNetworkDisconnect();
                            sleep(20000);
                        },
                        this::emuNetworkConnect),

                // Scenario 8: High latency flapping
                new DisruptionType("EMU: Latency flapping",
                        () -> {
                            for (int i = 0; i < 3; i++) {
                                emuNetworkDelay("5000");
                                sleep(2000);
                                emuNetworkDelay("0");
                                sleep(2000);
                            }
                        },
                        () -> emuNetworkDelay("0")),

                // Scenario 9: Speed changes (LTE -> GPRS -> full)
                new DisruptionType("EMU: Speed degradation cycle",
                        () -> {
                            emuNetworkSpeed("hsdpa");
                            sleep(3000);
                            emuNetworkSpeed("edge");
                            sleep(3000);
                            emuNetworkSpeed("gsm");
                            sleep(5000);
                        },
                        () -> emuNetworkSpeed("full")),

                // Scenario 10: Combined disconnect + slow recovery
                new DisruptionType("EMU: Disconnect + degraded recovery",
                        () -> {
                            emuNetworkDisconnect();
                            sleep(10000);
                            emuNetworkConnect();
                            emuNetworkDelay("3000");
                            emuNetworkSpeed("edge");
                            sleep(5000);
                        },
                        () -> { emuNetworkDelay("0"); emuNetworkSpeed("full"); }),
        };

        return types[random.nextInt(types.length)];
    }

    // --- Real device network control ---------------------------------------------

    private void enableWifi() {
        executeShellCommand("svc wifi enable");
    }

    private void disableWifi() {
        executeShellCommand("svc wifi disable");
    }

    private void enableMobileData() {
        executeShellCommand("svc data enable");
    }

    private void disableMobileData() {
        executeShellCommand("svc data disable");
    }

    private void enableAirplaneMode() {
        // Explicitly disable WiFi and mobile data first — Android preserves
        // WiFi state across airplane mode toggles if the user had it enabled
        executeShellCommand("svc wifi disable");
        executeShellCommand("svc data disable");
        executeShellCommand("cmd connectivity airplane-mode enable");
    }

    private void disableAirplaneMode() {
        executeShellCommand("cmd connectivity airplane-mode disable");
    }

    private void restoreAllNetworks() {
        if (isEmulator) {
            emuNetworkConnect();
            emuNetworkDelay("0");
            emuNetworkSpeed("full");
        } else {
            disableAirplaneMode();
            enableWifi();
            enableMobileData();
        }
    }

    // --- Emulator console network control ----------------------------------------

    private void emuNetworkDisconnect() {
        sendEmulatorConsoleCommand("network disconnect");
    }

    private void emuNetworkConnect() {
        sendEmulatorConsoleCommand("network connect");
    }

    /**
     * Set network delay in milliseconds.
     * Values: "0" (none), "500", "1000", "5000", "10000", etc.
     */
    private void emuNetworkDelay(String delayMs) {
        sendEmulatorConsoleCommand("network delay " + delayMs);
    }

    /**
     * Set network speed.
     * Values: "gsm", "hscsd", "gprs", "edge", "umts", "hsdpa", "lte", "evdo", "full"
     */
    private void emuNetworkSpeed(String speed) {
        sendEmulatorConsoleCommand("network speed " + speed);
    }

    /**
     * Sends a command to the emulator console via telnet.
     * The emulator console listens on 10.0.2.2:{@link #EMULATOR_CONSOLE_PORT} from
     * inside the emulator (host loopback mapped address).
     */
    private void sendEmulatorConsoleCommand(String command) {
        try (
            // From inside the emulator, 10.0.2.2 is the host loopback
            Socket socket = new Socket("10.0.2.2", EMULATOR_CONSOLE_PORT);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            socket.setSoTimeout(5000);

            // Read the initial banner
            readUntilOK(reader);

            // Authenticate if token is set
            if (!EMULATOR_AUTH_TOKEN.isEmpty()) {
                writer.println("auth " + EMULATOR_AUTH_TOKEN);
                readUntilOK(reader);
            }

            // Send the actual command
            writer.println(command);
            String response = readUntilOK(reader);
            log("  EMU console: " + command + " -> " + response.trim());

            writer.println("quit");
        } catch (Exception e) {
            log("  EMU console command failed: " + command + " - " + e.getMessage());
            // Fallback: try via shell for basic disconnect/connect
            if (command.equals("network disconnect")) {
                executeShellCommand("iptables -A OUTPUT -j DROP");
                executeShellCommand("iptables -A INPUT -j DROP");
            } else if (command.equals("network connect")) {
                executeShellCommand("iptables -F OUTPUT");
                executeShellCommand("iptables -F INPUT");
            }
        }
    }

    private String readUntilOK(BufferedReader reader) {
        StringBuilder sb = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
                if (line.startsWith("OK") || line.contains("OK")) {
                    break;
                }
            }
        } catch (Exception e) {
            // timeout or read error, return what we have
        }
        return sb.toString();
    }

    // --- Emulator detection ------------------------------------------------------

    private boolean detectEmulator() {
        boolean emulator =
                Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MODEL.contains("sdk_gphone")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic")
                || Build.DEVICE.startsWith("generic")
                || "google_sdk".equals(Build.PRODUCT)
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("emulator")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu");
        return emulator;
    }

    // --- VPN UI interaction ------------------------------------------------------

    private void connectVpnViaUI() throws Exception {
        MainActivity activity = activityRule.getActivity();

        AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.UNKNOWN);
        CountDownLatch connectedLatch = new CountDownLatch(1);

        StateListener listener = new StateListenerAdapter() {
            @Override
            public void onConnected() {
                state.set(ConnectionState.CONNECTED);
                connectedLatch.countDown();
            }

            @Override
            public void onDisconnected() {
                state.set(ConnectionState.DISCONNECTED);
            }
        };

        activity.runOnUiThread(() -> activity.registerServiceStateListener(listener));

        // Check if already connected
        if (pingOnce()) {
            log("VPN already connected");
            activity.runOnUiThread(() -> activity.unregisterServiceStateListener(listener));
            return;
        }

        // Trigger connection
        activity.runOnUiThread(() -> activity.switchConnection(true));

        // Wait for connected state
        boolean connected = connectedLatch.await(VPN_RECOVERY_TIMEOUT_SEC, TimeUnit.SECONDS);
        activity.runOnUiThread(() -> activity.unregisterServiceStateListener(listener));

        if (!connected) {
            log("WARNING: VPN connect timed out via state listener, will verify via ping...");
        }
    }

    // --- Ping verification -------------------------------------------------------

    private boolean waitForPingSuccess(int timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        int attempt = 0;

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            if (pingOnce()) {
                log(String.format("  Ping succeeded on attempt %d", attempt));
                return true;
            }
            // Exponential backoff: 2s, 4s, 8s, capped at 10s
            long backoff = Math.min(2000L * (1L << Math.min(attempt - 1, 3)), 10000);
            Thread.sleep(backoff);
        }

        log(String.format("  Ping failed after %d attempts over %ds", attempt, timeoutSeconds));
        return false;
    }

    private boolean pingOnce() {
        try {
            String output = executeShellCommand(
                    String.format("ping -c 1 -W %d %s", PING_TIMEOUT_SEC, PING_TARGET));
            boolean success = output.contains("1 received") || output.contains("1 packets received");
            if (!success) {
                // Some devices use different ping output format
                success = output.contains("time=") && !output.contains("100% packet loss");
            }
            return success;
        } catch (Exception e) {
            return false;
        }
    }

    // --- Utilities ---------------------------------------------------------------

    private String executeShellCommand(String command) {
        try {
            ParcelFileDescriptor pfd = uiAutomation.executeShellCommand(command);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(pfd)))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                return output.toString();
            }
        } catch (Exception e) {
            log("Shell command failed: " + command + " - " + e.getMessage());
            return "";
        }
    }

    private void log(String message) {
        String entry = String.format("[%tT] %s", System.currentTimeMillis(), message);
        Log.d(TAG, entry);
        testLog.add(entry);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // --- Inner types -------------------------------------------------------------

    private enum ConnectionState {
        UNKNOWN, CONNECTED, DISCONNECTED
    }

    private static class DisruptionType {
        final String name;
        final Runnable apply;
        final Runnable restore;

        DisruptionType(String name, Runnable apply, Runnable restore) {
            this.name = name;
            this.apply = apply;
            this.restore = restore;
        }
    }

    private static class StateListenerAdapter implements StateListener {
        @Override public void onEngineStarted() {}
        @Override public void onEngineStopped() {}
        @Override public void onAddressChanged(String fqdn, String ip) {}
        @Override public void onConnected() {}
        @Override public void onConnecting() {}
        @Override public void onDisconnected() {}
        @Override public void onDisconnecting() {}
        @Override public void onPeersListChanged(long count) {}
    }
}
