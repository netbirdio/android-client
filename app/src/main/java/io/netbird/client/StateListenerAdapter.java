package io.netbird.client;

public abstract class StateListenerAdapter implements StateListener {

    @Override
    public void onEngineStarted() {

    }

    @Override
    public void onEngineStopped() {

    }

    @Override
    public void onAddressChanged(String fqdn, String ip) {

    }

    @Override
    public void onConnected() {

    }

    @Override
    public void onConnecting() {

    }

    @Override
    public void onDisconnected() {

    }

    @Override
    public void onDisconnecting() {

    }

    @Override
    public void onPeersListChanged(long totalPeers) {

    }
}
