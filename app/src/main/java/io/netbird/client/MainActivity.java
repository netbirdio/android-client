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
import android.util.TypedValue;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.android.material.navigation.NavigationBarView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
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


public class MainActivity extends AppCompatActivity implements ServiceAccessor, StateListenerRegistry {

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
    // Route listeners are often registered before the service binding completes
    // (fragments come up first), so they are queued here and attached to the binder
    // in onServiceConnected.
    private final List<RouteChangeListener> routeChangeListeners = new ArrayList<>();
    private URLOpener urlOpener;
    private QrCodeDialog qrCodeDialog;

    private boolean isSSOFinishedWell = false;
    private boolean isRunningOnTV = false;
    private boolean useDeviceCodeFlow = false;

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
            for (RouteChangeListener listener : routeChangeListeners) {
                mBinder.addRouteChangeListener(listener);
            }
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
        setSupportActionBar(binding.toolbar);

        isRunningOnTV = PlatformUtils.isAndroidTV(this);
        useDeviceCodeFlow = PlatformUtils.requiresDeviceCodeFlow(this);
        if (isRunningOnTV) {
            Log.i(LOGTAG, "Running on Android TV - optimizing for D-pad navigation");
        }
        if (useDeviceCodeFlow && !isRunningOnTV) {
            Log.i(LOGTAG, "Running on ChromeOS - using device code flow for authentication");
        }

        NavigationBarView bottomNav = (NavigationBarView) binding.bottomNav;

        // All four bottom-nav destinations are top-level — no Up arrow on those.
        final Set<Integer> topLevelDestinations = new HashSet<>();
        topLevelDestinations.add(R.id.nav_home);
        topLevelDestinations.add(R.id.nav_peers);
        topLevelDestinations.add(R.id.nav_networks);
        topLevelDestinations.add(R.id.nav_settings);
        mAppBarConfiguration = new AppBarConfiguration.Builder(topLevelDestinations).build();
        navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(bottomNav, navController);

        // Peer detail is a sibling of the tabs rather than a child, so tapping a tab
        // navigates away without unwinding it. NavigationUI derives the bottom-nav
        // selection from the current destination, and peer detail matches no nav item,
        // which strands the bar with nothing highlighted. Clear it on the way out.
        bottomNav.setOnItemSelectedListener(item -> {
            NavDestination current = navController.getCurrentDestination();
            if (current != null && current.getId() == R.id.nav_peer_detail) {
                navController.popBackStack(R.id.nav_peer_detail, true);
            }
            return NavigationUI.onNavDestinationSelected(item, navController);
        });

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();

            // First-launch onboarding takes the whole screen — hide both nav surfaces.
            if (destId == R.id.firstInstallFragment) {
                bottomNav.setVisibility(View.GONE);
                setToolbarVisible(false);
                return;
            }
            bottomNav.setVisibility(View.VISIBLE);

            // Top-level destinations (Home, Peers, Networks, Settings) don't need a toolbar —
            // bottom nav already identifies the screen. Sub-screens keep the toolbar with title + Up arrow.
            boolean isTopLevel = topLevelDestinations.contains(destId);
            setToolbarVisible(!isTopLevel);

            if (destId == R.id.nav_home) {
                removeToolbarShadow();
            } else {
                resetToolbar();
            }
        });

        if (!useDeviceCodeFlow) {
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

                    if (!isRunningOnTV) {
                        try {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            startActivity(browserIntent);
                        } catch (Exception e) {
                            Log.e(LOGTAG, "Failed to open browser for device code flow: " + e.getMessage());
                        }
                    }
                }

                @Override
                public void onLoginSuccess() {
                    Log.d(LOGTAG, "onLoginSuccess fired for device code flow.");
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
                        Toast.makeText(this, getString(R.string.error_vpn_permission_required), Toast.LENGTH_LONG).show();
                        return;
                    }

                    Log.d(LOGTAG, "VPN permission granted");
                    // Always-on VPN check
                    if (VPNService.isUsingAlwaysOnVPN(this)) {
                        showAlwaysOnDialog(() -> {
                            if (mBinder != null) {
                                mBinder.runEngine(urlOpener, useDeviceCodeFlow);
                            }
                        });
                    } else {
                        if (mBinder != null) {
                            mBinder.runEngine(urlOpener, useDeviceCodeFlow);
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
            mBinder.runEngine(urlOpener, useDeviceCodeFlow);
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
            return null;
        }

        return mBinder.peersInfo();
    }

    @Override
    public NetworkArray getNetworks() {
        if (mBinder == null) {
            Log.w(LOGTAG, "VPN binder is null");
            return null;
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
    public String debugBundle(boolean anonymize) throws Exception {
        if (mBinder == null) {
            throw new Exception("VPN service not connected");
        }
        return mBinder.debugBundle(anonymize);
    }

    @Override
    public void addRouteChangeListener(RouteChangeListener listener) {
        if (!routeChangeListeners.contains(listener)) {
            routeChangeListeners.add(listener);
        }

        if (mBinder == null) {
            return; // queued; attached in onServiceConnected
        }

        mBinder.addRouteChangeListener(listener);
    }

    @Override
    public void removeRouteChangeListener(RouteChangeListener listener) {
        routeChangeListeners.remove(listener);

        if (mBinder == null) {
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

    private void setToolbarVisible(boolean visible) {
        ViewGroup.LayoutParams lp = binding.toolbar.getLayoutParams();
        int targetHeight;
        if (visible) {
            TypedValue tv = new TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                targetHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
            } else {
                targetHeight = (int) (56 * getResources().getDisplayMetrics().density);
            }
        } else {
            targetHeight = 0;
        }
        if (lp.height == targetHeight) {
            return;
        }
        lp.height = targetHeight;
        binding.toolbar.setLayoutParams(lp);
        // Ensure AppBarLayout re-measures itself so the content below shifts up correctly.
        binding.appbar.requestLayout();
    }

    private void removeToolbarShadow() {
        stateAnim = binding.appbar.getStateListAnimator();
        binding.appbar.setStateListAnimator(null);
        binding.appbar.setElevation(0f);
        binding.toolbar.setElevation(0);
        binding.toolbar.setBackground(new ColorDrawable(ContextCompat.getColor(this, R.color.nb_bg_home)));
    }

    private void resetToolbar() {
        if(stateAnim!=null) {
            binding.appbar.setStateListAnimator(stateAnim);
        }
        binding.appbar.setElevation(10f);
        binding.toolbar.setElevation(0);
        binding.toolbar.setBackground(new ColorDrawable(ContextCompat.getColor(this, R.color.nb_bg)));
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
