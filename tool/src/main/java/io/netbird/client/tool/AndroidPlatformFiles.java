package io.netbird.client.tool;

import io.netbird.gomobile.android.PlatformFiles;

public class AndroidPlatformFiles implements PlatformFiles {
    private final String configurationFilePath;
    private final String stateFilePath;

    public AndroidPlatformFiles(String configurationFilePath, String stateFilePath) {
        this.configurationFilePath = configurationFilePath;
        this.stateFilePath = stateFilePath;
    }

    @Override
    public String configurationFilePath() {
        return configurationFilePath;
    }

    @Override
    public String stateFilePath() {
        return stateFilePath;
    }
}
