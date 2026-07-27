package io.netbird.client.ui.server;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Management-URL helpers shared by the profile editor and the first-run screen,
 * mirroring the desktop's useManagementUrl hook.
 */
public final class ManagementUrl {

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
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(REACHABILITY_TIMEOUT_MS);
            connection.setReadTimeout(REACHABILITY_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.getResponseCode();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
