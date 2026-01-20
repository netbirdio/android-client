package io.netbird.client;

import android.animation.StateListAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import com.google.android.material.navigation.NavigationView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import io.netbird.client.databinding.ActivityMainBinding;
import io.netbird.client.tool.RouteChangeListener;
import io.netbird.client.tool.ServiceStateListener;
import io.netbird.client.tool.VPNService;
import io.netbird.client.ui.PreferenceUI;
import io.netbird.gomobile.android.ConnectionListener;
import io.netbird.gomobile.android.NetworkArray;
import io.netbird.gomobile.android.PeerInfoArray;
import io.netbird.gomobile.android.URLOpener;


public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, ServiceAccessor, StateListenerRegistry {

    private StateListAnimator stateAnim;

    private enum ConnectionState {
        UNKNOWN,
        CONNECTED,
        CONNECTING,
        DISCONNECTING,
        DISCONNECTED
    }
    private final static String LOGTAG = "NBMainActivity";
    private VPNService.MyLocalBinder mBinder;

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private NavController navController;

    private ActivityResultLauncher<Intent> vpnActivityResultLauncher;
    private final List<StateListener> serviceStateListeners = new ArrayList<>();
    private URLOpener urlOpener;
    private QrCodeDialog qrCodeDialog;

    private boolean isSSOFinishedWell = false;
    private boolean isRunningOnTV = false;

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

        isRunningOnTV = PlatformUtils.isAndroidTV(this);
        if (isRunningOnTV) {
            Log.i(LOGTAG, "Running on Android TV - optimizing for D-pad navigation");
        }

        setVersionText();

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        // Set the listener for menu item selections
        navigationView.setNavigationItemSelectedListener(this);

        // Update profile menu item with active profile name
        updateProfileMenuItem(navigationView);
        
        // On TV, request focus when drawer opens so D-pad navigation works
        if (isRunningOnTV) {
            drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
                @Override
                public void onDrawerOpened(View drawerView) {
                    // Request focus on the drawer when it opens
                    navigationView.postDelayed(() -> {
                        navigationView.setFocusable(true);
                        navigationView.setFocusableInTouchMode(false);
                        
                        if (!navigationView.requestFocus()) {
                            Log.d(LOGTAG, "NavigationView couldn't get focus, trying menu items");
                        }
                        
                        // Try to find and focus the first visible menu item
                        View menuView = navigationView.getChildAt(0);
                        if (menuView != null) {
                            View firstFocusable = menuView.findFocus();
                            if (firstFocusable == null) {
                                menuView.requestFocus();
                            }
                        }
                    }, 100); // Delay to let drawer animation finish
                }
                
                @Override
                public void onDrawerClosed(View drawerView) {
                    // Return focus to main content when drawer closed
                    View mainContent = findViewById(R.id.nav_host_fragment_content_main);
                    if (mainContent != null) {
                        mainContent.requestFocus();
                    }
                }
            });
        }

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home)
                .setOpenableLayout(drawer)
                .build();
        navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (destination.getId() == R.id.nav_home) {
                removeToolbarShadow();
                // Update profile menu item when returning to home (e.g., after profile switch)
                if (binding != null && binding.navView != null) {
                    updateProfileMenuItem(binding.navView);
                }
            } else {
                resetToolbar();
            }
        });

        if (!isRunningOnTV) {
            urlOpener = new CustomTabURLOpener(this, () -> {
                if (isSSOFinishedWell) {
                    return;
                }
                if (mBinder == null) {
                    return;
                }

                mBinder.stopEngine();
            });
        } else {
            urlOpener = new URLOpener() {
                @Override
                public void open(String url, String userCode) {
                    qrCodeDialog = QrCodeDialog.newInstance(url, userCode, () -> {
                        if (isSSOFinishedWell) {
                            return;
                        }
                        if (mBinder == null) {
                            return;
                        }
                        mBinder.stopEngine();
                    });
                    qrCodeDialog.show(getSupportFragmentManager(), "QrCodeDialog");
                }

                @Override
                public void onLoginSuccess() {
                    Log.d(LOGTAG, "onLoginSuccess fired for TV.");
                    if (qrCodeDialog != null && qrCodeDialog.isVisible()) {
                        qrCodeDialog.dismiss();
                        qrCodeDialog = null;
                    }
                }
            };
        }

        // VPN permission result launcher
        vpnActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if((result.getResultCode() != Activity.RESULT_OK)) {
                        Log.w(LOGTAG, "VPN permission denied");
                        for (StateListener listener : serviceStateListeners) {
                            listener.onEngineStopped();
                        }
                        Toast.makeText(this, "VPN permission required", Toast.LENGTH_LONG).show();
                        return;
                    }

                    Log.d(LOGTAG, "VPN permission granted");
                    // Always-on VPN check
                    if (VPNService.isUsingAlwaysOnVPN(this)) {
                        showAlwaysOnDialog(() -> {
                            if (mBinder != null) {
                                mBinder.runEngine(urlOpener, isRunningOnTV);
                            }
                        });
                    } else {
                        if (mBinder != null) {
                            mBinder.runEngine(urlOpener, isRunningOnTV);
                        }
                    }
                });

        if (PreferenceUI.isFirstLaunch(this)) {
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
    protected void onResume() {
        super.onResume();
        // Update profile menu item when returning to MainActivity
        if (binding != null && binding.navView != null) {
            updateProfileMenuItem(binding.navView);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(LOGTAG, "onStop");
        if (urlOpener instanceof CustomTabURLOpener && ((CustomTabURLOpener) urlOpener).isOpened()) {
            return; // Keep service alive for SSO custom tab
        }

        if (mBinder != null) {
            mBinder.removeConnectionStateListener();
            mBinder.removeServiceStateListener(serviceStateListener);
            unbindService(serviceIPC);
            mBinder = null;
        }
    }

    @Override
    protected  void onDestroy() {
        super.onDestroy();

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
        if (id == R.id.nav_docs) {
            item.setCheckable(false);
            binding.drawerLayout.closeDrawers();
            openDocs();
            return true;
        }

        // Use NavigationUI which handles launchSingleTop and saveState/restoreState
        // This prevents fragment recreation and preserves state when alternating between destinations
        boolean isHandled = NavigationUI.onNavDestinationSelected(item, navController);
        binding.drawerLayout.closeDrawers();
        return isHandled;
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
            mBinder.runEngine(urlOpener, isRunningOnTV);
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
    public NetworkArray getNetworks() {
        if (mBinder == null) {
            Log.w(LOGTAG, "VPN binder is null");
            return new NetworkArray();
        }

        return mBinder.networks();
    }

    @Override
    public void selectRoute(String route) throws Exception {
        if (mBinder == null) {
            Log.w(LOGTAG, "VPN binder is null");
            return;
        }

        mBinder.selectRoute(route);
    }

    @Override
    public void deselectRoute(String route) throws Exception {
        if (mBinder == null) {
            Log.w(LOGTAG, "VPN binder is null");
            return;
        }

        mBinder.deselectRoute(route);
    }

    @Override
    public void addRouteChangeListener(RouteChangeListener listener) {
        if (mBinder == null) {
            Log.w(LOGTAG, "VPN binder is null");
            return;
        }

        mBinder.addRouteChangeListener(listener);
    }

    @Override
    public void removeRouteChangeListener(RouteChangeListener listener) {
        if (mBinder == null) {
            Log.w(LOGTAG, "VPN binder is null");
            return;
        }

        mBinder.removeRouteChangeListener(listener);
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

    private void removeToolbarShadow() {
        stateAnim = binding.appBarMain.appbar.getStateListAnimator();
        binding.appBarMain.appbar.setStateListAnimator(null);
        binding.appBarMain.appbar.setElevation(0f);
        binding.appBarMain.toolbar.setElevation(0);
        binding.appBarMain.toolbar.setBackground(new ColorDrawable(ContextCompat.getColor(this, R.color.nb_bg_home)));
    }

    private void resetToolbar() {
        if(stateAnim!=null) {
            binding.appBarMain.appbar.setStateListAnimator(stateAnim);
        }
        binding.appBarMain.appbar.setElevation(10f);
        binding.appBarMain.toolbar.setElevation(0);
        binding.appBarMain.toolbar.setBackground(new ColorDrawable(ContextCompat.getColor(this, R.color.nb_bg)));
    }

    private void showAlwaysOnDialog(Runnable onDismissAction) {
        final View dialogView = getLayoutInflater().inflate(R.layout.dialog_always_on, null);
        final AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        // Set bold-formatted text using Html.fromHtml
        TextView descriptionText = dialogView.findViewById(R.id.text_description);
        descriptionText.setText(Html.fromHtml(getString(R.string.dialog_always_on_desc), Html.FROM_HTML_MODE_LEGACY));

        dialogView.findViewById(R.id.btn_close).setOnClickListener(v -> alertDialog.dismiss());

        alertDialog.setOnDismissListener(dialog -> {
            if (onDismissAction != null) {
                onDismissAction.run();
            }
        });

        alertDialog.show();
    }

    private void setVersionText() {
        try {
            try {
                String packageName = getPackageName();
                String versionName = getPackageManager().getPackageInfo(packageName, 0).versionName;
                binding.navVersion.setText(versionName);
            } catch (Exception e) {
                binding.navVersion.setText("");
            }
        } catch (Exception e) {
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

    private void updateProfileMenuItem(NavigationView navigationView) {
        try {
            // Get active profile from ProfileManager instead of reading file
            io.netbird.client.tool.ProfileManagerWrapper profileManager =
                new io.netbird.client.tool.ProfileManagerWrapper(this);
            String activeProfile = profileManager.getActiveProfile();
            Menu menu = navigationView.getMenu();
            MenuItem profileItem = menu.findItem(R.id.nav_profiles);
            if (profileItem != null && activeProfile != null) {
                profileItem.setTitle(activeProfile);
            }
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to update profile menu item", e);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isRunningOnTV) {
            return super.onKeyDown(keyCode, event);
        }
        else {
            Log.d(LOGTAG, "Key pressed: " + keyCode + " (" + KeyEvent.keyCodeToString(keyCode) + "), repeat: " + event.getRepeatCount());

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                boolean isOnHomeScreen = navController != null &&
                    navController.getCurrentDestination() != null &&
                    navController.getCurrentDestination().getId() == R.id.nav_home;

                if (event.getRepeatCount() == 0 && isOnHomeScreen && !binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    Toast.makeText(this, R.string.tv_menu_hint, Toast.LENGTH_SHORT).show();
                }

                // drawer is not selectable on Android 16+, so we open via a long press of the left d-pad button instead
                if (event.getRepeatCount() > 0 && !binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    Log.d(LOGTAG, "Long press LEFT detected - opening drawer");
                    binding.drawerLayout.openDrawer(GravityCompat.START);
                    binding.navView.requestFocus();
                    return true;
                }
            }

            if (keyCode == KeyEvent.KEYCODE_BACK && binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                Log.d(LOGTAG, "Closing drawer with BACK");
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }
}