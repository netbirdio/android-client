package io.netbird.client;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import com.google.android.material.navigation.NavigationView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import io.netbird.client.databinding.ActivityMainBinding;
import io.netbird.client.tool.ServiceStateListener;
import io.netbird.client.tool.VPNService;
import io.netbird.client.ui.PreferenceUI;
import io.netbird.gomobile.android.ConnectionListener;
import io.netbird.gomobile.android.PeerInfoArray;


public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, ServiceAccessor, StateListenerRegistry {

    private enum ConnectionState {
        UNKNOWN,
        CONNECTED,
        CONNECTING,
        DISCONNECTING,
        DISCONNECTED
    }
    private final static String LOGTAG = "MainActivity";
    private VPNService.MyLocalBinder mBinder;


    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private NavController navController;

    private ActivityResultLauncher<Intent> vpnActivityResultLauncher;
    private final List<StateListener> serviceStateListeners = new ArrayList<>();
    private CustomTabURLOpener urlOpener;

    private boolean isSSOFinishedWell = false;

    // Last known state for UI updates
    private ConnectionState lastKnownState = ConnectionState.UNKNOWN;
    private String lastFqdn = null;
    private String lastIp = null;
    private long lastPeersCount = 0;

    private final ServiceConnection serviceIPC = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            Log.d(LOGTAG, "on service connected");
            mBinder = (VPNService.MyLocalBinder) binder;
            mBinder.setConnectionStateListener(connectionListener);
            mBinder.addServiceStateListener(serviceStateListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBinder = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        // Set the listener for menu item selections
        navigationView.setNavigationItemSelectedListener(this);

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home)
                .setOpenableLayout(drawer)
                .build();
        navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);

        urlOpener = new CustomTabURLOpener(this, () -> {
            if(isSSOFinishedWell) {
                return;
            }

            if(mBinder == null) {
                return;
            }

            mBinder.stopEngine();
        });

        // VPN permission result launcher
        vpnActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(LOGTAG, "on activity resutl: "+ result.getResultCode());
                    if((result.getResultCode() != Activity.RESULT_OK)) {
                        Log.w(LOGTAG, "VPN permission denied");
                        Toast.makeText(this, "VPN permission required", Toast.LENGTH_LONG).show();
                        return;
                    }

                    Log.d(LOGTAG, "VPN permission granted");
                    // Always-on VPN check
                    if (VPNService.isUsingAlwaysOnVPN(this)) {
                        // todo throw always on message
                        //https://github.com/netbirdio/android-client/blob/7a02f88025e18ea1548c457b2c76de2bddd598af/react/netbird-lib/android/src/main/java/com/netbirdlib/NetbirdLibModule.java#L130
                    }

                    if (mBinder != null) {
                        mBinder.runEngine(urlOpener);
                    }
                });

        if (!PreferenceUI.isFirstLaunch(this)) {
            PreferenceUI.setFirstLaunchDone(this);
            showFirstInstallFragment();
        }

    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(LOGTAG, "onStart");
        startService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(LOGTAG, "onStop");
        if (mBinder != null) {
            mBinder.removeConnectionStateListener();
            mBinder.removeServiceStateListener(serviceStateListener);
            unbindService(serviceIPC);
            mBinder = null;
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (item.getItemId() == R.id.nav_docs) {
            openDocs();
            return true;
        }

        navController.navigate(id);
        binding.drawerLayout.closeDrawers();
        return false;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public void switchConnection(boolean status) {
        if (mBinder == null) {
            Log.w(LOGTAG, "VPN binder is null");
            return;
        }

        if (!status) {
            mBinder.stopEngine();
            return;
        }

        Intent prepareIntent = mBinder.prepareVpnIntent(this);
        if (prepareIntent != null) {
            vpnActivityResultLauncher.launch(prepareIntent);
        } else {
            mBinder.runEngine(urlOpener);
        }
    }

    @Override
    public void stopEngine() {
        if (mBinder == null) {
            return;
        }
        mBinder.stopEngine();

    }

    @Override
    public PeerInfoArray getPeersList() {
        if (mBinder == null) {
            Log.w(LOGTAG, "VPN binder is null");
            return new PeerInfoArray();
        }

        return mBinder.peersInfo();
    }


    @Override
    public void registerServiceStateListener(StateListener listener) {
        if (serviceStateListeners.contains(listener)) {
            return;
        }
        serviceStateListeners.add(listener);

        if(lastKnownState == ConnectionState.UNKNOWN) {
            return; // No state to notify yet
        }

        switch (lastKnownState) {
            case CONNECTED:
                listener.onConnected();
                break;
            case CONNECTING:
                listener.onConnecting();
                break;
            case DISCONNECTING:
                listener.onDisconnecting();
                break;
            case DISCONNECTED:
                listener.onDisconnected();
                break;
        }

        if (lastFqdn != null && lastIp != null) {
            listener.onAddressChanged(lastFqdn, lastIp);
        }

        listener.onPeersListChanged(lastPeersCount);
    }

    @Override
    public void unregisterServiceStateListener(StateListener listener) {
        serviceStateListeners.remove(listener);
    }

    private void openDocs() {
        String url = "https://docs.netbird.io";  // Replace with the desired URL
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void startService() {
        Log.i(LOGTAG, "start VPN service");
        Intent intent = new Intent(this, VPNService.class);
        intent.setAction(VPNService.INTENT_ACTION_START);
        startService(intent);

        Intent bindIntent = new Intent(this, VPNService.class);
        bindService(bindIntent, serviceIPC, Context.BIND_ABOVE_CLIENT);
    }

    private void showFirstInstallFragment() {
        if (navController != null) {
            navController.navigate(R.id.firstInstallFragment);
        } else {
            Log.w(LOGTAG, "NavController is null, can't navigate to FirstInstallFragment");
        }
    }

    ConnectionListener connectionListener = new ConnectionListener() {
        @Override
        public synchronized void onAddressChanged(String fqdn, String ip) {
            lastFqdn = fqdn;
            lastIp = ip;

            for (StateListener listener : serviceStateListeners) {
                listener.onAddressChanged(fqdn, ip);
            }
        }

        public void onConnected() {
            lastKnownState = ConnectionState.CONNECTED;

            isSSOFinishedWell = true;
            for (StateListener listener : serviceStateListeners) {
                listener.onConnected();
            }
        }

        public void onConnecting() {
            lastKnownState = ConnectionState.CONNECTING;

            isSSOFinishedWell = true;
            for (StateListener listener : serviceStateListeners) {
                listener.onConnecting();
            }
        }

        public void onDisconnecting() {
            lastKnownState = ConnectionState.DISCONNECTING;

            for (StateListener listener : serviceStateListeners) {
                listener.onDisconnecting();
            }
        }

        public void onDisconnected() {
            lastKnownState = ConnectionState.DISCONNECTED;

            isSSOFinishedWell = false;
            for (StateListener listener : serviceStateListeners) {
                listener.onDisconnected();
            }
        }

        @Override
        public void onPeersListChanged(long numberOfPeers) {
            lastPeersCount = numberOfPeers;
            for (StateListener listener : serviceStateListeners) {
                listener.onPeersListChanged(numberOfPeers);
            }
        }
    };

    ServiceStateListener serviceStateListener = new ServiceStateListener() {
        public void onStarted() {
            Log.d(LOGTAG, "on engine started");
            for (StateListener listener : serviceStateListeners) {
                listener.onEngineStarted();
            }
        }

        public void onStopped() {
            Log.d(LOGTAG, "on engine stopped");
            for (StateListener listener : serviceStateListeners) {
                listener.onEngineStopped();
            }
        }

        public void onError(String msg) {
            // in case of error the onStopped will be called all the time
            Log.e(LOGTAG, "on engine error: " + msg);
            runOnUiThread(() -> {
                Toast toast = Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG);
                toast.show();
            });
        }
    };
}