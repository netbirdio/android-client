package io.netbird.client.tool.networks;

public interface NetworkChangeListener {
    void onNetworkChanged(@Constants.NetworkType int networkType);
}
