package io.netbird.client.tool.autoconnect;

/**
 * The engine-facing operations {@link AutoConnectController} needs from
 * whatever hosts it (in practice, {@code VPNService}). Kept as a small
 * interface so the trigger-evaluation logic doesn't depend on VpnService
 * internals directly.
 */
public interface AutoConnectHost {
    boolean isEngineRunning();

    /** True when the user has already granted VPN consent (VpnService.prepare() == null). */
    boolean hasVpnConsent();

    /** Connects the currently-active profile without an interactive login flow. */
    void requestConnect();

    void requestDisconnect();

    /** Consent is missing/revoked and can only be granted from an Activity; let the user know. */
    void notifyConsentRequired();
}
