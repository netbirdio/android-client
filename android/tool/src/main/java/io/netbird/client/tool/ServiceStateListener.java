package io.netbird.client.tool;

public interface ServiceStateListener {
    void onStarted();
    void onStopped();
    void onError(String msg);

}
