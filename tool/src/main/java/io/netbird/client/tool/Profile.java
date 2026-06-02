package io.netbird.client.tool;

import java.util.Objects;

public class Profile {
    private final String id;
    private final String name;
    private final boolean isActive;

    public Profile(String id, String name, boolean isActive) {
        if (name == null) {
            throw new IllegalArgumentException("Profile name cannot be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        this.id = id;
        this.name = name;
        this.isActive = isActive;
    }

    public String getID() {return id;}

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return isActive;
    }

    @Override
    public String toString() {
        return id + name + (isActive ? " (Active)" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Profile profile = (Profile) o;
        return Objects.equals(id, profile.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
