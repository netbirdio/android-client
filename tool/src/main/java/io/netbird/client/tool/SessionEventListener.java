package io.netbird.client.tool;

/**
 * Callbacks for the SSO auth-session lifecycle, produced by
 * {@link SessionMonitor}. All callbacks arrive on the main thread.
 */
public interface SessionEventListener {

    /**
     * The management server started rejecting the peer: the session has
     * expired and an interactive re-login is required.
     */
    void onSessionExpired();

    /**
     * The session deadline changed: first published after login, pushed out
     * by an extend, or cleared (0) when expiry is disabled or the engine is
     * torn down. Drives persistent surfaces (foreground notification,
     * home-screen row) and the warning scheduler.
     */
    void onSessionDeadlineChanged(long expiresAtUnixSeconds);
}
