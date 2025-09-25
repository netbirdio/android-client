package io.netbird.client.repository;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.List;

import io.netbird.client.tool.RouteChangeListener;
import io.netbird.client.tool.VPNService;
import io.netbird.client.ui.home.Resource;
import io.netbird.client.ui.home.Status;

public class VPNServiceRepository {
    private VPNService.MyLocalBinder binder;
    private final Context context;
    private VPNServiceBindListener serviceBindListener;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (VPNService.MyLocalBinder)service;
            if (serviceBindListener != null) {
                serviceBindListener.onServiceBind();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (binder != null) {
                binder = null;
            }

            serviceBindListener = null;
        }
    };

    public VPNServiceRepository(Context context) {
        this.context = context;
    }

    public void setServiceBindListener(VPNServiceBindListener listener) {
        this.serviceBindListener = listener;
    }

    public void bindService() {
        var intent = new Intent(context, VPNService.class);
        context.bindService(intent, serviceConnection, Context.BIND_ABOVE_CLIENT);
    }

    public void unbindService() {
        if (binder != null) {
            context.unbindService(serviceConnection);
            binder = null;
        }
    }

    public List<Resource> getNetworks() {
        if (binder == null) {
            return new ArrayList<>();
        }

        var resources = new ArrayList<Resource>();
        var networks = binder.networks();

        for (int i = 0; i < networks.size(); i++) {
            var network = networks.get(i);
            resources.add(new Resource(Status.fromString(network.getStatus()),
                    network.getName(),
                    network.getNetwork(),
                    network.getPeer()));
        }

        return resources;
    }

    public void addRouteChangeListener(RouteChangeListener listener) {
        if (binder != null) {
            binder.addRouteChangeListener(listener);
        }
    }

    public void removeRouteChangeListener(RouteChangeListener listener) {
        if (binder != null) {
            binder.removeRouteChangeListener(listener);
        }
    }
}
