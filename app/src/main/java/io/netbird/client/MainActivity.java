package io.netbird.client;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import com.google.android.material.navigation.NavigationView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import io.netbird.client.databinding.ActivityMainBinding;
import io.netbird.client.tool.ServiceStateListener;
import io.netbird.client.tool.VPNService;
import io.netbird.gomobile.android.ConnectionListener;
import io.netbird.gomobile.android.URLOpener;


public class MainActivity extends AppCompatActivity implements ServiceAccessor, StateListenerRegistry {

    private final static String LOGTAG = "MainActivity";
    private VPNService.MyLocalBinder mBinder;


    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    private ActivityResultLauncher<Intent> vpnActivityResultLauncher;
    private final List<StateListener> serviceStateListeners = new ArrayList<>();



    URLOpener urlOpener = url -> {
        Log.d(LOGTAG, "URL: " + url);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(android.net.Uri.parse(url));
        startActivity(intent);
    };
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
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_advanced, R.id.nav_about)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        // VPN permission result launcher
        vpnActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
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

    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(LOGTAG, "onStart");
        startService();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
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
    public void registerServiceStateListener(StateListener listener) {
        if (serviceStateListeners.contains(listener)) {
            return;
        }
        serviceStateListeners.add(listener);
    }

    @Override
    public void unregisterServiceStateListener(StateListener listener) {
        serviceStateListeners.remove(listener);

    }


    private void startService() {
        Log.i(LOGTAG, "start VPN service");
        Intent intent = new Intent(this, VPNService.class);
        intent.setAction(VPNService.INTENT_ACTION_START);
        startService(intent);

        Intent bindIntent = new Intent(this, VPNService.class);
        bindService(bindIntent, serviceIPC, Context.BIND_ABOVE_CLIENT);
    }

    ConnectionListener connectionListener = new ConnectionListener() {
        @Override
        public void onAddressChanged(String fqdn, String ip) {
            for (StateListener listener : serviceStateListeners) {
                listener.onAddressChanged(fqdn, ip);
            }
        }

        public void onConnected() {
            for (StateListener listener : serviceStateListeners) {
                listener.onConnected();
            }
        }

        public void onConnecting() {
            for (StateListener listener : serviceStateListeners) {
                listener.onConnecting();
            }
        }

        public void onDisconnecting() {
            for (StateListener listener : serviceStateListeners) {
                listener.onDisconnecting();
            }
        }

        public void onDisconnected() {
            for (StateListener listener : serviceStateListeners) {
                listener.onDisconnected();
            }
        }

        @Override
        public void onPeersListChanged(long numberOfPeers) {
        }
    };

    ServiceStateListener serviceStateListener = new ServiceStateListener() {
        public void onStarted() {
            Log.d(LOGTAG, "on go service started");
            for (StateListener listener : serviceStateListeners) {
                listener.onEngineStarted();
            }
        }

        public void onStopped() {
            Log.d(LOGTAG, "on go service stopped");
            for (StateListener listener : serviceStateListeners) {
                listener.onEngineStopped();
            }
        }

        public void onError(String msg) {
            runOnUiThread(() -> {
                Toast toast = Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG);
                toast.show();
            });
        }
    };
}