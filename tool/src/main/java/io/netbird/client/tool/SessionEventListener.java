package io.netbird.client.tool;

/**
 * Callbacks for the SSO auth-session lifecycle, produced by
 * {@link SessionMonitor}. All callbacks arrive on the main thread.
 */
public interface SessionEventListener {

    /**
     * The session deadline is approaching. Fired at the T-10 warning
     * (finalWarning false) and again at the T-2 fallback (finalWarning true,
     * unless the user dismissed the first warning).
     *
     * @param expiresAtUnixSeconds absolute deadline as unix seconds (UTC)
     * @param leadMinutes          the warning lead in minutes (10 or 2)
     * @param finalWarning         true for the T-2 fallback warning
     */
    void onSessionExpiring(long expiresAtUnixSeconds, long leadMinutes, boolean finalWarning);

    /**
     * The management server started rejecting the peer: the session has
     * expired and an interactive re-login is required.
     */
    void onSessionExpired();

    /**
     * The session deadline changed: first published after login, pushed out
     * by an extend, or cleared (0) when expiry is disabled or the engine is
     * torn down. Drives persistent surfaces (foreground notification,
     * home-screen row).
     */
    void onSessionDeadlineChanged(long expiresAtUnixSeconds);
}
