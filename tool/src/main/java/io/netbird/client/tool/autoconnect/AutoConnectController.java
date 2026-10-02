package io.netbird.client.tool.autoconnect;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import io.netbird.client.tool.networks.Constants;
import io.netbird.client.tool.networks.NetworkAvailabilityListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the engine's connected/disconnected state in sync with the trigger
 * rules in {@link AutoConnectPreferences}: connects when the current network
 * satisfies an enabled trigger, and disconnects again if it later stops
 * satisfying any enabled trigger (not just on total network loss). Reacts to
 * both network changes (subscribed to {@code NetworkChangeDetector}
 * alongside the engine's own roaming listener) and to settings changes
 * ({@link #onSettingsChanged()}, called by {@code VPNService} whenever a
 * trigger or the trusted-network list is edited), so flipping a toggle takes
 * effect immediately rather than waiting for the next network event.
 *
 * <p>Deliberately keyed off {@code onNetworkAvailable}/{@code onNetworkLost}/
 * {@code onNetworkValidated} rather than {@code onDefaultNetworkTypeChanged}:
 * once our own tunnel is up it becomes the OS-reported "default" network, and
 * Android does not reliably keep notifying the default-network callback
 * about the transport changing underneath it from then on. The plain,
 * per-network callback these three methods come from tracks Wi-Fi/mobile/
 * ethernet by their own identity regardless of which one is "default", so it
 * keeps working correctly while the VPN is active — the same signal the
 * engine's own roaming feature ({@code ConcreteNetworkAvailabilityListener})
 * already relies on for this reason.
 *
 * <p>The Wi-Fi trusted-network list is an exemption list, not an allow-list:
 * a network on it is one the user doesn't need the VPN on (e.g. home Wi-Fi),
 * so auto-connect skips it and connects on any other Wi-Fi network instead.
 */
public class AutoConnectController implements NetworkAvailabilityListener {
    private static final String LOGTAG = "AutoConnect";
    private static final int UNKNOWN_TYPE = -1;

    // Collapses a rapid burst of network events (e.g. Wi-Fi and mobile both
    // toggling during a handover) into a single evaluation once things
    // settle, rather than connecting/disconnecting on every intermediate step.
    private static final long CONNECT_DEBOUNCE_MS = 2000;
    // Ignores brief all-networks gaps during a normal handover so
    // "disconnect on no network" doesn't fire mid-switch.
    private static final long DISCONNECT_GRACE_MS = 8000;

    private final AutoConnectPreferences preferences;
    private final WifiInfoProvider wifiInfoProvider;
    private final AutoConnectHost host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable reevaluateRunnable = this::reevaluateNow;

    private final Map<Integer, Boolean> availableTypes = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> validatedTypes = new ConcurrentHashMap<>();
    private volatile boolean hasInternet = true;

    public AutoConnectController(AutoConnectPreferences preferences, WifiInfoProvider wifiInfoProvider, AutoConnectHost host) {
        this.preferences = preferences;
        this.wifiInfoProvider = wifiInfoProvider;
        this.host = host;
    }

    /**
     * Called whenever a trigger toggle or the trusted-network list changes,
     * so the connection reacts right away instead of waiting for the next
     * network event — e.g. enabling "Connect on mobile data" while already
     * on mobile data connects immediately.
     */
    public void onSettingsChanged() {
        Log.d(LOGTAG, "settings changed, re-evaluating immediately");
        handler.removeCallbacks(reevaluateRunnable);
        reevaluateNow();
    }

    @Override
    public void onNetworkAvailable(@Constants.NetworkType int networkType) {
        Log.d(LOGTAG, "network available: type=" + networkType);
        availableTypes.put(networkType, true);
        scheduleReevaluate(CONNECT_DEBOUNCE_MS);
    }

    @Override
    public void onNetworkLost(@Constants.NetworkType int networkType) {
        Log.d(LOGTAG, "network lost: type=" + networkType);
        availableTypes.remove(networkType);
        validatedTypes.remove(networkType);
        scheduleReevaluate(CONNECT_DEBOUNCE_MS);
    }

    @Override
    public void onNetworkValidated(@Constants.NetworkType int networkType, boolean validated) {
        Log.d(LOGTAG, "network validated: type=" + networkType + " validated=" + validated);
        if (validated) {
            validatedTypes.put(networkType, true);
        } else {
            validatedTypes.remove(networkType);
        }
        scheduleReevaluate(CONNECT_DEBOUNCE_MS);
    }

    @Override
    public void onDefaultNetworkTypeChanged(@Constants.NetworkType int networkType, long networkHandle) {
        // Supplementary nudge only (e.g. a same-type AP-to-AP handover,
        // which doesn't change availableTypes/validatedTypes at all) — see
        // the class doc for why this signal alone isn't trusted once a VPN
        // is active.
        Log.d(LOGTAG, "default network changed: type=" + networkType + " handle=" + networkHandle);
        scheduleReevaluate(CONNECT_DEBOUNCE_MS);
    }

    @Override
    public void onInternetAvailabilityChanged(boolean available) {
        Log.d(LOGTAG, "internet availability changed: " + available);
        hasInternet = available;
        scheduleReevaluate(available ? CONNECT_DEBOUNCE_MS : DISCONNECT_GRACE_MS);
    }

    private void scheduleReevaluate(long delayMs) {
        handler.removeCallbacks(reevaluateRunnable);
        handler.postDelayed(reevaluateRunnable, delayMs);
    }

    private void reevaluateNow() {
        boolean running = host.isEngineRunning();

        if (!hasInternet) {
            boolean shouldDisconnect = preferences.isDisconnectOnNoNetworkEnabled() && running;
            Log.d(LOGTAG, "reevaluate: no internet; disconnectOnNoNetworkEnabled="
                    + preferences.isDisconnectOnNoNetworkEnabled() + " running=" + running
                    + " -> disconnect=" + shouldDisconnect);
            if (shouldDisconnect) {
                host.requestDisconnect();
            }
            return;
        }

        int currentType = currentActiveType();
        boolean matches = triggerMatches(currentType);
        Log.d(LOGTAG, "reevaluate: currentType=" + currentType + " matches=" + matches
                + " running=" + running + " available=" + availableTypes.keySet()
                + " validated=" + validatedTypes.keySet());
        if (matches && !running) {
            connectIfPossible();
        } else if (!matches && running) {
            Log.i(LOGTAG, "current network no longer matches an enabled trigger, requesting disconnect");
            host.requestDisconnect();
        }
    }

    /**
     * The transport auto-connect should act on right now: whichever is both
     * available and validated, preferring Ethernet, then Wi-Fi, then mobile
     * (matching Android's own general routing preference). Falls back to
     * merely "available" if nothing is validated yet, so evaluation doesn't
     * stall indefinitely on a network that never gets a validation signal.
     */
    private int currentActiveType() {
        int validated = bestOf(validatedTypes);
        if (validated != UNKNOWN_TYPE) {
            return validated;
        }
        return bestOf(availableTypes);
    }

    private int bestOf(Map<Integer, Boolean> types) {
        if (types.containsKey(Constants.NetworkType.ETHERNET)) return Constants.NetworkType.ETHERNET;
        if (types.containsKey(Constants.NetworkType.WIFI)) return Constants.NetworkType.WIFI;
        if (types.containsKey(Constants.NetworkType.MOBILE)) return Constants.NetworkType.MOBILE;
        return UNKNOWN_TYPE;
    }

    private boolean triggerMatches(int type) {
        switch (type) {
            case Constants.NetworkType.WIFI:
                boolean wifiEnabled = preferences.isWifiTriggerEnabled();
                boolean exempt = isCurrentWifiExempt();
                Log.d(LOGTAG, "triggerMatches(WIFI): wifiTriggerEnabled=" + wifiEnabled + " exempt=" + exempt);
                return wifiEnabled && !exempt;
            case Constants.NetworkType.MOBILE:
                return preferences.isMobileTriggerEnabled();
            case Constants.NetworkType.ETHERNET:
                return preferences.isEthernetTriggerEnabled();
            default:
                return false;
        }
    }

    private void connectIfPossible() {
        if (!host.hasVpnConsent()) {
            // Only an Activity can show the system VPN-consent dialog; a
            // background trigger cannot grant it itself.
            Log.w(LOGTAG, "trigger matched but VPN consent is missing; notifying instead of connecting");
            host.notifyConsentRequired();
            return;
        }
        Log.i(LOGTAG, "trigger matched, requesting connect");
        host.requestConnect();
    }

    /**
     * True when the current Wi-Fi network is on the trusted (exempt) list —
     * a network the user doesn't need the VPN on, so auto-connect should
     * skip it. An empty list exempts nothing, so plain "Connect on Wi-Fi"
     * still means any Wi-Fi network.
     */
    private boolean isCurrentWifiExempt() {
        List<TrustedNetwork> exemptList = preferences.getTrustedNetworks();
        if (exemptList.isEmpty()) {
            return false;
        }
        WifiInfoProvider.WifiSnapshot snapshot = wifiInfoProvider.currentWifi();
        if (snapshot == null) {
            // Can't verify which network this is (no location permission,
            // location services off, or a masked BSSID) — default to NOT
            // exempt, i.e. connect: protecting by default is the safer
            // failure mode than silently skipping the VPN on a network we
            // can't actually identify.
            Log.w(LOGTAG, "isCurrentWifiExempt(): could not read current SSID/BSSID "
                    + "(missing ACCESS_FINE_LOCATION, location services off, or masked BSSID) — treating as not exempt");
            return false;
        }
        Log.d(LOGTAG, "isCurrentWifiExempt(): current ssid=" + snapshot.ssid + " bssid=" + snapshot.bssid
                + " against " + exemptList.size() + " trusted entr" + (exemptList.size() == 1 ? "y" : "ies"));
        for (TrustedNetwork network : exemptList) {
            if (network.matches(snapshot.ssid, snapshot.bssid)) {
                Log.d(LOGTAG, "isCurrentWifiExempt(): matched trusted entry ssid=" + network.ssid
                        + " bssid=" + network.bssid + " — exempt, will not auto-connect");
                return true;
            }
        }
        return false;
    }
}
