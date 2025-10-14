package io.netbird.client.tool.networks;

public class ConcreteNetworkChangeListener implements NetworkChangeListener {
    private @Constants.NetworkType int currentNetworkType;

    private NetworkToggleListener listener;

    public ConcreteNetworkChangeListener() {
        this.currentNetworkType = Constants.NetworkType.NONE;
    }

    @Override
    public void onNetworkChanged(@Constants.NetworkType int networkType) {
        var oldNetworkType = currentNetworkType;
        currentNetworkType = networkType;

        if (oldNetworkType != Constants.NetworkType.NONE && oldNetworkType != currentNetworkType) {
            notifyListener();
        }
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onNetworkTypeChanged();
        }
    }

    public void subscribe(NetworkToggleListener listener) {
        this.listener = listener;
    }

    public void unsubscribe() {
        this.listener = null;
    }
}
