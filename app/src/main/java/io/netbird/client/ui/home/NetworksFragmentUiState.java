package io.netbird.client.ui.home;

import java.util.List;

public class NetworksFragmentUiState {
    private final List<Resource> resources;

    public NetworksFragmentUiState(List<Resource> resources) {
        this.resources = resources;
    }

    public List<Resource> getResources() {
        return resources;
    }
}
