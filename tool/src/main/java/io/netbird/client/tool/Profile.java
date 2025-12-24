package io.netbird.client.tool;

import java.util.Objects;

public class Profile {
    private final String name;
    private final boolean isActive;

    public Profile(String name, boolean isActive) {
        if (name == null) {
            throw new IllegalArgumentException("Profile name cannot be null");
        }
        this.name = name;
        this.isActive = isActive;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return isActive;
    }

    @Override
    public String toString() {
        return name + (isActive ? " (Active)" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Profile profile = (Profile) o;
        return Objects.equals(name, profile.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
