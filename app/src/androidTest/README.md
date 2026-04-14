# Network Connectivity Stress Test

Automated stress test that verifies the VPN recovers after real-world network disruptions.
The test randomly picks disruption scenarios, applies them, waits a random amount of time,
restores connectivity, then verifies VPN recovery by pinging a peer through the tunnel.

## Prerequisites

- The app must be **already logged in / authenticated**
- A reachable VPN peer IP configured in `PING_TARGET` (default: `100.72.116.70`)
- **Real device**: WiFi and mobile data (SIM card) available
- **Emulator**: Copy auth token from `~/.emulator_console_auth_token` into `EMULATOR_AUTH_TOKEN` constant, or clear the file to disable auth

## How to run

```bash
# 1. Build both APKs
./gradlew assembleDebug assembleDebugAndroidTest

# 2. Install them
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# 3. Run the test directly via adb
adb shell am instrument -w -e class io.netbird.client.NetworkConnectivityStressTest \
  io.netbird.client.test/androidx.test.runner.AndroidJUnitRunner
```

> **Note**: Do not use `./gradlew connectedAndroidTest` — Gradle's Unified Test Platform
> may reinstall the APK mid-test, killing the process during long-running disruption cycles.

## Watching live progress

In a separate terminal:

```bash
adb logcat -s NBConnStressTest:D
```

## Useful helper commands

```bash
# Simulate battery mode (realistic power management: Doze, app standby)
adb shell dumpsys battery unplug

# Restore charging state after test
adb shell dumpsys battery reset

# Manually test airplane mode
adb shell cmd connectivity airplane-mode enable
adb shell cmd connectivity airplane-mode disable

# Manually test WiFi/mobile data
adb shell svc wifi disable
adb shell svc wifi enable
adb shell svc data disable
adb shell svc data enable
```

## Configuration

Constants at the top of `NetworkConnectivityStressTest.java`:

| Constant | Default | Description |
|----------|---------|-------------|
| `PING_TARGET` | `100.72.116.70` | VPN peer IP to ping for connectivity verification |
| `NUM_CYCLES` | `20` | Number of random disruption cycles |
| `VPN_RECOVERY_TIMEOUT_SEC` | `90` | Max seconds to wait for VPN to recover per cycle |
| `SETTLE_DELAY_SEC` | `5` | Seconds to wait after network change before checking |
| `MAX_RANDOM_EXTRA_WAIT_SEC` | `15` | Max random extra wait to simulate real-world timing |
| `PING_TIMEOUT_SEC` | `5` | Timeout for a single ping attempt |
| `EMULATOR_AUTH_TOKEN` | `""` | Emulator console auth token (emulator only) |
| `EMULATOR_CONSOLE_PORT` | `5554` | Emulator console telnet port (emulator only) |

## Real Device Scenarios

The test auto-detects real device vs emulator and picks from the appropriate scenario set.

| # | Scenario | Disruption | Restore |
|---|----------|-----------|---------|
| 1 | WiFi→Mobile (leave office) | Disable WiFi | Enable WiFi |
| 2 | Mobile→WiFi (at home) | Disable mobile data | Enable mobile data |
| 3 | All networks lost (tunnel/elevator) | WiFi off + mobile off + airplane on | Airplane off + WiFi on + mobile on |
| 4 | WiFi reconnect (switch network) | WiFi off → 2s → WiFi on | (already restored) |
| 5 | WiFi flapping (unstable) | 3x: WiFi off 1.5s → WiFi on 1.5s | Enable WiFi |
| 6 | Airplane mode toggle | Airplane on → 5s → airplane off | WiFi on + mobile on |
| 7 | Mobile data flapping | 3x: mobile off 1s → mobile on 1s | Enable mobile |
| 8 | Long outage (30s no network) | WiFi off + mobile off + airplane on → 30s | Airplane off + WiFi on + mobile on |
| 9 | Sequential network loss | WiFi off → 3s → mobile off | Mobile on → 2s → WiFi on |
| 10 | Airplane mode flapping | 2x: airplane on 3s → airplane off 3s | Airplane off + WiFi on + mobile on |

## Emulator Scenarios

Uses the emulator console (`telnet 10.0.2.2:5554`) to control the virtual network.
Falls back to `iptables` rules if the console is not reachable.

| # | Scenario | Disruption | Restore |
|---|----------|-----------|---------|
| 1 | Network disconnect | Full network cut | Network connect |
| 2 | Long outage (30s) | Network cut → 30s wait | Network connect |
| 3 | Network flapping (3x) | 3x: disconnect 1.5s → connect 1.5s | Network connect |
| 4 | Extreme latency (5s delay) | 10s network delay | Delay 0 |
| 5 | GPRS speed throttle | Speed → GSM | Speed → full |
| 6 | Disconnect then slow reconnect | Disconnect → 5s → connect at GSM → 5s | Speed → full |
| 7 | Flap then long disconnect (20s) | Disconnect → connect → disconnect → 20s | Network connect |
| 8 | Latency flapping | 3x: 5s delay 2s → 0 delay 2s | Delay 0 |
| 9 | Speed degradation cycle | HSDPA → 3s → EDGE → 3s → GSM → 5s | Speed → full |
| 10 | Disconnect + degraded recovery | Disconnect → 10s → connect with 3s delay + EDGE → 5s | Delay 0 + speed full |

## Test output

The test prints a summary at the end:

```
=== Results: 18/20 passed, 2 failed ===
```

If any cycle fails, the test fails with a message indicating how many cycles did not recover.
Full details are in logcat under tag `NBConnStressTest`.
