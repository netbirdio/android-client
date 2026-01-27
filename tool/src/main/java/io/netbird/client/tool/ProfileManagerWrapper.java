package io.netbird.client.tool;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import io.netbird.gomobile.android.ProfileManager;
import io.netbird.gomobile.android.ProfileArray;

/**
 * Wrapper around the gomobile ProfileManager to provide a more Java-friendly API
 */
public class ProfileManagerWrapper {
    private static final String TAG = "ProfileManagerWrapper";
    private final ProfileManager profileManager;
    private final Context context;

    public ProfileManagerWrapper(Context context) {
        this.context = context;
        // Android always uses app's files directory for config
        String configDir = context.getFilesDir().getPath();
        this.profileManager = io.netbird.gomobile.android.Android.newProfileManager(configDir);
    }

    /**
     * Lists all available profiles
     */
    public List<Profile> listProfiles() {
        List<Profile> profiles = new ArrayList<>();
        try {
            ProfileArray array = profileManager.listProfiles();
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    io.netbird.gomobile.android.Profile p = array.get(i);
                    if (p != null) {
                        profiles.add(new Profile(p.getName(), p.getIsActive()));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to list profiles", e);
        }
        return profiles;
    }

    /**
     * Gets the currently active profile name
     */
    public String getActiveProfile() {
        try {
            return profileManager.getActiveProfile();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get active profile", e);
            return "default";
        }
    }

    /**
     * Switches to a different profile
     * Stops the VPN service before switching
     */
    public void switchProfile(String profileName) throws Exception {
        if (profileName == null || profileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Profile name cannot be empty");
        }

        // Stop VPN service before switching profile
        stopEngine();

        profileManager.switchProfile(profileName);
    }

    /**
     * Creates a new profile
     */
    public void addProfile(String profileName) throws Exception {
        if (profileName == null || profileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Profile name cannot be empty");
        }
        profileManager.addProfile(profileName);
    }

    /**
     * Logs out from a profile (clears authentication, requires re-login)
     * Stops the VPN service if logging out from the active profile
     */
    public void logoutProfile(String profileName) throws Exception {
        if (profileName == null || profileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Profile name cannot be empty");
        }

        // Check if logging out from active profile
        String activeProfile = getActiveProfile();
        if (activeProfile.equals(profileName)) {
            // Stop VPN service if logging out from active profile
            stopEngine();
        }

        profileManager.logoutProfile(profileName);
    }

    /**
     * Removes a profile
     */
    public void removeProfile(String profileName) throws Exception {
        if (profileName == null || profileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Profile name cannot be empty");
        }
        profileManager.removeProfile(profileName);
    }

    /**
     * Gets the config file path for the currently active profile
     * This should be used instead of Preferences.configFile()
     */
    public String getActiveConfigPath() throws Exception {
        return profileManager.getActiveConfigPath();
    }

    /**
     * Gets the state file path for the currently active profile
     * This should be used instead of Preferences.stateFile()
     */
    public String getActiveStateFilePath() throws Exception {
        return profileManager.getActiveStateFilePath();
    }

    /**
     * Stops the VPN engine (disconnects) without stopping the service
     */
    private void stopEngine() {
        try {
            Intent stopIntent = new Intent(VPNService.ACTION_STOP_ENGINE);
            stopIntent.setPackage(context.getPackageName());
            context.sendBroadcast(stopIntent);
            Log.d(TAG, "Sent stop engine broadcast for profile operation");
        } catch (Exception e) {
            Log.w(TAG, "Failed to send stop engine broadcast: " + e.getMessage());
            // Don't throw exception - profile operations should continue even if stop fails
        }
    }
}
