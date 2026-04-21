package io.netbird.client.tool;

import io.netbird.gomobile.android.PlatformFiles;

public class AndroidPlatformFiles implements PlatformFiles {
    private final String configurationFilePath;
    private final String stateFilePath;
    private final String cacheDir;

    public AndroidPlatformFiles(String configurationFilePath, String stateFilePath, String cacheDir) {
        this.configurationFilePath = configurationFilePath;
        this.stateFilePath = stateFilePath;
        this.cacheDir = cacheDir;
    }

    @Override
    public String configurationFilePath() {
        return configurationFilePath;
    }

    @Override
    public String stateFilePath() {
        return stateFilePath;
    }

    @Override
    public String cacheDir() {
        return cacheDir;
    }
}
