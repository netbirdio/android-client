package io.netbird.client.ui.server;

import android.security.NetworkSecurityPolicy;
import android.util.Log;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Management-URL helpers shared by the profile editor and the first-run screen,
 * mirroring the desktop's useManagementUrl hook.
 */
public final class ManagementUrl {

    private static final String LOGTAG = "ManagementUrl";

    /** Management URL of NetBird's hosted service. */
    public static final String CLOUD = "https://api.netbird.io:443";

    // The Go core rewrites the legacy endpoint, but existing configs may still
    // carry it; both must read as "Cloud" so an old profile is not mistaken for
    // a self-hosted one.
    private static final String LEGACY_CLOUD = "https://api.wiretrustee.com:443";

    // Same syntactic check as the desktop UI: host is a domain, localhost or
    // IPv4, with optional scheme, port, path, query and fragment.
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://)?"
                    + "((([a-z\\d]([a-z\\d-]*[a-z\\d])?)\\.)+[a-z]{2,}|localhost|"
                    + "((\\d{1,3}\\.){3}\\d{1,3}))"
                    + "(:\\d+)?(/[-a-z\\d%_.~+]*)*"
                    + "(\\?[;&a-z\\d%_.~+=-]*)?"
                    + "(#[-a-z\\d_]*)?$",
            Pattern.CASE_INSENSITIVE);

    private static final int REACHABILITY_TIMEOUT_MS = 5000;

    private ManagementUrl() {
    }

    public static boolean isCloud(String url) {
        if (url == null || url.trim().isEmpty()) {
            return true;
        }
        String trimmed = url.trim();
        return CLOUD.equals(trimmed) || LEGACY_CLOUD.equals(trimmed);
    }

    public static boolean isValid(String url) {
        String trimmed = url == null ? "" : url.trim();
        return !trimmed.isEmpty() && URL_PATTERN.matcher(trimmed).matches();
    }

    /** Adds the https:// scheme when the user omitted it. */
    public static String normalize(String url) {
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    /**
     * Soft reachability probe: any HTTP response counts as reachable, only a
     * connection failure or timeout does not. Can false-negative for servers
     * behind internal DNS or with self-signed certificates, which is why an
     * unreachable result is surfaced as a warning rather than a hard block.
     *
     * <p>Blocking call — run it off the main thread.
     */
    public static boolean isReachable(String url) {
        URL parsed;
        try {
            parsed = new URL(url);
        } catch (MalformedURLException e) {
            // Nothing to probe, and nothing the engine could dial either, so
            // this is a genuine negative rather than a question the probe
            // cannot answer.
            Log.d(LOGTAG, "management server " + url + " is not a URL", e);
            return false;
        }

        if (!cleartextPermitted(parsed)) {
            // Not an answer this can give, so it does not pretend to. The
            // engine reaches the server with Go's own networking, which is not
            // bound by the platform's cleartext policy the way this probe is,
            // so a plain-HTTP server that the probe may not touch is still one
            // the client can use. Reporting it unreachable would warn about a
            // URL that works.
            return true;
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) parsed.openConnection();
            connection.setConnectTimeout(REACHABILITY_TIMEOUT_MS);
            connection.setReadTimeout(REACHABILITY_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.getResponseCode();
            return true;
        } catch (Exception e) {
            // Logged because the reason is the whole diagnostic value: no
            // route, a name that does not resolve, and a rejected certificate
            // all arrive here and all show the user the same sentence.
            Log.d(LOGTAG, "management server " + url + " did not answer the probe", e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Whether the platform will carry this request. An app targeting API 28 or
     * later has cleartext denied unless it opts in, and this one does not, so
     * an http:// URL fails on policy before any packet is sent. Anything else
     * is permitted as far as this policy is concerned.
     */
    private static boolean cleartextPermitted(URL url) {
        if (!"http".equalsIgnoreCase(url.getProtocol())) {
            return true;
        }
        return NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(url.getHost());
    }
}
