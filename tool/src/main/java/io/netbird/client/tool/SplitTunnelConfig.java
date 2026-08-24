package io.netbird.client.tool;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which applications the tunnel carries.
 *
 * Android lets a VpnService name either the apps that stay out of the tunnel or
 * the apps that are the only ones in it, never both on the same builder, so the
 * two selections are kept apart and a mode says which one is live.
 *
 * Deliberately free of Android types: the rules below are the part worth testing
 * on the JVM, away from a device.
 */
public final class SplitTunnelConfig {

    public enum Mode {
        /** Everything but {@link #ALWAYS_EXCLUDED} goes through the tunnel. */
        OFF,
        /** The user's picks stay out of the tunnel; everything else goes in. */
        EXCLUDE,
        /** Only the user's picks go through the tunnel. */
        INCLUDE
    }

    /** How the resolved packages are meant to be handed to VpnService.Builder. */
    public enum Filter {
        DISALLOW,
        ALLOW
    }

    /**
     * Apps that misbehave when tunnelled, kept out in every mode that can express
     * an exclusion. They predate this feature and stay as the floor of EXCLUDE so
     * that turning split tunnelling on never silently pulls them into the tunnel.
     */
    public static final Set<String> ALWAYS_EXCLUDED = Collections.unmodifiableSet(
            new LinkedHashSet<>(java.util.Arrays.asList(
                    "com.google.android.projection.gearhead",
                    "com.google.android.apps.chromecast.app",
                    "com.google.android.apps.messaging",
                    "com.google.stadia.android")));

    private final Mode mode;
    private final Set<String> excluded;
    private final Set<String> included;

    public SplitTunnelConfig(Mode mode, Collection<String> excluded, Collection<String> included) {
        this.mode = mode == null ? Mode.OFF : mode;
        this.excluded = copyOf(excluded);
        this.included = copyOf(included);
    }

    public Mode getMode() {
        return mode;
    }

    public Set<String> getExcluded() {
        return excluded;
    }

    public Set<String> getIncluded() {
        return included;
    }

    /**
     * An INCLUDE selection that is empty would allow no app at all, leaving a
     * tunnel that carries nothing and looks broken rather than configured. Such a
     * config is reported as inactive and resolves like {@link Mode#OFF}, so the UI
     * can warn about it with the same answer the tunnel will act on.
     */
    public boolean isActive() {
        if (mode == Mode.EXCLUDE) {
            return !excluded.isEmpty();
        }
        if (mode == Mode.INCLUDE) {
            return !included.isEmpty();
        }
        return false;
    }

    /**
     * @param ownPackage this app's own package name, always allowed in INCLUDE
     *                   mode: the Go engine's own sockets bypass the tunnel via
     *                   protectSocket, but the built-in SSH client has to reach
     *                   peers through it.
     */
    public Resolution resolve(String ownPackage) {
        if (mode == Mode.INCLUDE && isActive()) {
            Set<String> allowed = new LinkedHashSet<>(included);
            if (ownPackage != null && !ownPackage.isEmpty()) {
                allowed.add(ownPackage);
            }
            return new Resolution(Filter.ALLOW, allowed);
        }

        Set<String> disallowed = new LinkedHashSet<>(ALWAYS_EXCLUDED);
        if (mode == Mode.EXCLUDE) {
            disallowed.addAll(excluded);
        }
        return new Resolution(Filter.DISALLOW, disallowed);
    }

    private static Set<String> copyOf(Collection<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        // SharedPreferences hands back a set that must not be touched, and the
        // caller may keep mutating its own; copy on the way in.
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }

    /** The packages to apply, and the builder method to apply them with. */
    public static final class Resolution {
        private final Filter filter;
        private final Set<String> packages;

        Resolution(Filter filter, Set<String> packages) {
            this.filter = filter;
            this.packages = Collections.unmodifiableSet(packages);
        }

        public Filter getFilter() {
            return filter;
        }

        public Set<String> getPackages() {
            return packages;
        }
    }
}
