package io.netbird.client;

import android.animation.StateListAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.android.material.navigation.NavigationBarView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.appcompat.app.AppCompatActivity;

import io.netbird.client.databinding.ActivityMainBinding;
import io.netbird.client.tool.Profile;
import io.netbird.client.tool.ProfileManagerWrapper;
import io.netbird.client.tool.RouteChangeListener;
import io.netbird.client.tool.ServiceStateListener;
import io.netbird.client.tool.SessionEventListener;
import io.netbird.client.tool.VPNService;
import io.netbird.client.ui.PreferenceUI;
import io.netbird.client.ui.ssh.SshSessionManager;
import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.ConnectionListener;
import io.netbird.gomobile.android.ErrListener;
import io.netbird.gomobile.android.NetworkArray;
import io.netbird.gomobile.android.PeerInfoArray;
import io.netbird.gomobile.android.SSHClient;
import io.netbird.gomobile.android.URLOpener;


public class MainActivity extends AppCompatActivity implements ServiceAccessor, StateListenerRegistry {

    private StateListAnimator stateAnim;

    private enum ConnectionState {
        UNKNOWN,
        CONNECTED,
        CONNECTING,
        DISCONNECTING,
        DISCONNECTED,
        NO_NETWORK
    }
    private final static String LOGTAG = "NBMainActivity";
    private VPNService.MyLocalBinder mBinder;
    private SshSessionManager.ClientFactory sshClientFactory;

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private NavController navController;

    private ActivityResultLauncher<Intent> vpnActivityResultLauncher;
    private final List<StateListener> serviceStateListeners = new CopyOnWriteArrayList<>();
    // Route listeners are often registered before the service binding completes
    // (fragments come up first), so they are queued here and attached to the binder
    // in onServiceConnected.
    private final List<RouteChangeListener> routeChangeListeners = new CopyOnWriteArrayList<>();
    private URLOpener urlOpener;
    private URLOpener extendUrlOpener;
    private QrCodeDialog qrCodeDialog;

    private boolean isSSOFinishedWell = false;
    private boolean isRunningOnTV = false;
    private boolean useDeviceCodeFlow = false;

    // Set when the notification's "Extend session" action arrives before the
    // service binding is up; executed from onServiceConnected.
    private boolean pendingExtendRequest = false;
    // Guards the extend flow's cancel path: the SSO surface reports its
    // dismissal even after a successful login, which must not cancel.
    private volatile boolean extendInProgress = false;
    // Set when the user abandoned the extend browser while the service was
    // unbound; the cancel is issued from onServiceConnected.
    private boolean pendingExtendCancel = false;

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
            mBinder.addSessionEventListener(sessionEventListener);
            for (RouteChangeListener listener : routeChangeListeners) {
                mBinder.addRouteChangeListener(listener);
            }
            // The engine can stop while we are unbound — most notably when the
            // management server expires the session, which tears the engine
            // down on its own. No connection callback reaches us then, so
            // lastKnownState would keep replaying a stale "connected" to every
            // listener that registers after the rebind.
            if (!mBinder.isRunning() && lastKnownState != ConnectionState.DISCONNECTED) {
                connectionListener.onDisconnected();
            }

            // Fragments registered before this binding; replay the
            // login-required status to them now that it is readable.
            if (mBinder.isLoginRequired()) {
                for (StateListener listener : serviceStateListeners) {
                    listener.onLoginRequired();
                }
            }

            if (pendingExtendCancel) {
                pendingExtendCancel = false;
                mBinder.cancelExtendAuthSession();
            }
            if (pendingExtendRequest) {
                pendingExtendRequest = false;
                extendSession();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(LOGTAG, "VPN service disconnected");
            mBinder = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        applySystemBarIconContrast();
        applySystemBarInsets();

        SshSessionManager.get().init(this);
        syncSshSessionProfile();
        sshClientFactory = new SshSessionManager.ClientFactory() {
            @Override
            public SSHClient newClient() {
                return newSSHClient();
            }

            @Override
            public URLOpener urlOpener() {
                return getSSHURLOpener();
            }

            @Override
            public boolean canConnect() {
                return isEngineRunning();
            }
        };
        SshSessionManager.get().setClientFactory(sshClientFactory);

        isRunningOnTV = PlatformUtils.isAndroidTV(this);
        useDeviceCodeFlow = PlatformUtils.requiresDeviceCodeFlow(this);
        if (isRunningOnTV) {
            Log.i(LOGTAG, "Running on Android TV - optimizing for D-pad navigation");
        } else if (getResources().getBoolean(R.bool.lock_portrait)) {
            // Phone-sized screens (sw < 600dp) are portrait-only, like the iOS
            // app. Tablets, car head units and TV rotate freely: a portrait lock
            // on a landscape-only display letterboxes the app into a narrow
            // strip and clips the system keyboard with it.
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
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
        topLevelDestinations.add(R.id.nav_ssh_sessions);
        topLevelDestinations.add(R.id.nav_settings);
        mAppBarConfiguration = new AppBarConfiguration.Builder(topLevelDestinations).build();
        navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(bottomNav, navController);

        // Sub-screens (peer detail, and everything under Settings) are siblings of
        // the tabs rather than children, so tapping a tab navigates away without
        // unwinding them. NavigationUI derives the bottom-nav selection from the
        // current destination, so one left on the back stack both strands the bar
        // with nothing highlighted and reappears when its tab is tapped again.
        // Keyed on "not a tab" rather than a list of sub-screens, so destinations
        // added later are handled without touching this.
        bottomNav.setOnItemSelectedListener(item -> {
            NavDestination current = navController.getCurrentDestination();
            if (current != null && !topLevelDestinations.contains(current.getId())) {
                navController.popBackStack(current.getId(), true);
            }
            return NavigationUI.onNavDestinationSelected(item, navController);
        });

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();

            // First-launch onboarding takes the whole screen — hide both nav surfaces.
            // The SSH terminal does the same, so the keyboard and xterm grid get the
            // full height rather than competing with the toolbar and bottom nav.
            // Deferred a frame: this listener fires before the fragment swap, so
            // hiding immediately re-layouts the outgoing screen without its toolbar
            // (a visible flash) before the new one appears. By the next frame the
            // destination's view is in place; re-check in case navigation moved on.
            if (destId == R.id.firstInstallFragment || destId == R.id.nav_ssh_terminal) {
                binding.getRoot().post(() -> {
                    NavDestination current = navController.getCurrentDestination();
                    if (current == null || current.getId() != destId) {
                        return;
                    }
                    bottomNav.setVisibility(View.GONE);
                    setToolbarVisible(false);
                });
                return;
            }
            bottomNav.setVisibility(View.VISIBLE);

            // Home, Peers and Networks don't need a toolbar — bottom nav already
            // identifies the screen. Sub-screens keep the toolbar with title + Up
            // arrow. SSH sessions and Settings are the exceptions among the tabs:
            // both are lists that run to the top of the screen, and without a title
            // bar to anchor them the first row reads as cut off rather than as the
            // start of a list.
            boolean hideToolbar = topLevelDestinations.contains(destId)
                    && destId != R.id.nav_ssh_sessions
                    && destId != R.id.nav_settings;
            setToolbarVisible(!hideToolbar);

            if (destId == R.id.nav_home) {
                removeToolbarShadow();
            } else {
                resetToolbar();
                dismissBottomSheets();
            }
        });

        sshUrlOpener = new CustomTabURLOpener(this, () -> {
            // Custom Tab closed; SSH device-code polling will time out if not completed.
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
                    runOnUiThread(() -> {
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
                    });
                }

                @Override
                public void onLoginSuccess() {
                    Log.d(LOGTAG, "onLoginSuccess fired for device code flow.");
                    runOnUiThread(() -> {
                        if (qrCodeDialog != null && qrCodeDialog.isVisible()) {
                            qrCodeDialog.dismiss();
                            qrCodeDialog = null;
                        }
                    });
                }
            };
        }

        // CustomTabURLOpener registers an activity-result launcher, which is
        // only allowed before the activity is STARTED — so the extend flow's
        // opener must be built here, not lazily at tap time.
        extendUrlOpener = buildExtendURLOpener();

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

        // The fragment clears the first-launch flag itself once the user commits
        // to a server, so leaving the app on the onboarding screen shows it again.
        if (savedInstanceState == null && PreferenceUI.isFirstLaunch(this)) {
            showFirstInstallFragment();
        }

        handleSessionIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleSessionIntent(intent);
    }

    // The persistent notification's "Extend session" action lands here; the
    // activity is singleTask, so a running instance gets it via onNewIntent.
    private void handleSessionIntent(Intent intent) {
        if (intent == null || !VPNService.ACTION_EXTEND_SESSION.equals(intent.getAction())) {
            return;
        }
        if (mBinder != null) {
            extendSession();
        } else {
            pendingExtendRequest = true;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(LOGTAG, "onStart");
        startService();
    }

    /**
     * Targeting API 36 turns on edge-to-edge with no opt-out: the window draws under both
     * system bars. The root layout's fitsSystemWindows padding keeps content clear of them
     * and its nb_bg background paints the strip behind them, so the icons drawn on top have
     * to be darkened or lightened to match, or they vanish against it. Do not rely on the
     * window background alone: some devices keep FORCE_DRAW_STATUS_BAR_BACKGROUND set, and
     * with no android:statusBarColor the system fills the strip with its default black —
     * which is why the theme still sets that colour for the platforms where it applies.
     * The contrast is set here rather than in themes.xml because windowLightNavigationBar
     * is API 27+ while minSdk is 26; the compat controller handles that gate itself.
     */
    private void applySystemBarIconContrast() {
        boolean nightMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!nightMode);
        controller.setAppearanceLightNavigationBars(!nightMode);
    }

    /**
     * Splits the system bar insets between the two views that have to paint behind them.
     * A single fitsSystemWindows on the root cannot do this: it pads all four edges at
     * once, so the root's own background ends up in the navigation bar strip and the
     * bottom navigation stops short of the screen edge — visible as a mismatched band
     * under the tabs, since the root is nb_bg while the tabs are nb_bottom_nav_bg. Taking
     * the insets here instead lets the top padding hold the status bar off the toolbar
     * while the bottom navigation extends its own background down past the gesture or
     * 3-button bar.
     */
    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, 0);
            binding.bottomNav.setPadding(0, 0, 0, bars.bottom);
            return windowInsets;
        });
    }

    // A theme switch from the settings screen re-runs configuration, not onCreate,
    // so the bar icons have to be re-tinted for the new mode here as well.
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applySystemBarIconContrast();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Profiles are switched and deleted from a fragment, which reports
        // neither, so re-read the active one whenever we come back into view.
        syncSshSessionProfile();
    }

    private void syncSshSessionProfile() {
        try {
            ProfileManagerWrapper profileManager = new ProfileManagerWrapper(this);
            Profile active = profileManager.getActiveProfile();
            SshSessionManager.get().setProfile(active != null ? active.getID() : null);
        } catch (Exception e) {
            Log.w(LOGTAG, "Could not sync SSH sessions with the active profile", e);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(LOGTAG, "onStop");
        if (urlOpener instanceof CustomTabURLOpener && ((CustomTabURLOpener) urlOpener).isOpened()) {
            return; // Keep service alive for SSO custom tab
        }
        if (extendInProgress) {
            // Same reason, for the extend flow's own SSO surface: staying bound
            // lets its cancel reach the service the moment the user backs out.
            return;
        }

        if (mBinder != null) {
            mBinder.removeConnectionStateListener();
            mBinder.removeServiceStateListener(serviceStateListener);
            mBinder.removeSessionEventListener(sessionEventListener);
            unbindService(serviceIPC);
            mBinder = null;
        }
    }

    @Override
    protected  void onDestroy() {
        super.onDestroy();

        if (sshClientFactory != null) {
            SshSessionManager.get().clearClientFactory(sshClientFactory);
            sshClientFactory = null;
        }

        if (mBinder != null) {
            mBinder.removeConnectionStateListener();
            mBinder.removeServiceStateListener(serviceStateListener);
            mBinder.removeSessionEventListener(sessionEventListener);
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
            throw new Exception("VPN service not connected");
        }

        mBinder.selectRoute(route);
    }

    @Override
    public void deselectRoute(String route) throws Exception {
        if (mBinder == null) {
            throw new Exception("VPN service not connected");
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
    public SSHClient newSSHClient() {
        if (mBinder == null) {
            Log.w(LOGTAG, "VPN binder is null");
            return null;
        }
        return mBinder.newSSHClient();
    }

    private boolean isEngineRunning() {
        return mBinder != null && mBinder.isRunning();
    }

    private CustomTabURLOpener sshUrlOpener;

    @Override
    public io.netbird.gomobile.android.URLOpener getSSHURLOpener() {
        return sshUrlOpener;
    }

    private void dismissBottomSheets() {
        for (androidx.fragment.app.Fragment f : getSupportFragmentManager().getFragments()) {
            dismissBottomSheetsRecursive(f);
        }
    }

    private void dismissBottomSheetsRecursive(androidx.fragment.app.Fragment fragment) {
        if (fragment instanceof com.google.android.material.bottomsheet.BottomSheetDialogFragment) {
            try {
                ((com.google.android.material.bottomsheet.BottomSheetDialogFragment) fragment).dismissAllowingStateLoss();
            } catch (Exception ignore) {
                // Already detached/dismissed.
            }
            return;
        }
        for (androidx.fragment.app.Fragment child : fragment.getChildFragmentManager().getFragments()) {
            dismissBottomSheetsRecursive(child);
        }
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
            case NO_NETWORK:
                listener.onNoNetwork();
                break;
        }

        if (lastFqdn != null && lastIp != null) {
            listener.onAddressChanged(lastFqdn, lastIp);
        }

        listener.onPeersListChanged(lastPeersCount);

        // Fragments come up before the service binding, so a login-required
        // state that is already in effect (the session expired while no UI was
        // running) would otherwise never reach them: it is a status label, not
        // an event that repeats.
        if (mBinder != null && mBinder.isLoginRequired()) {
            listener.onLoginRequired();
        }
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
        // AUTO_CREATE keeps the service alive for as long as this binding
        // exists. Without it a theme-change relaunch kills the connection for
        // good: the old instance's unbind reaches the service after the new
        // instance has already bound, its stopSelf destroys the service under
        // the fresh binding, and onServiceDisconnected leaves mBinder null
        // with nothing left to bring the service back.
        bindService(bindIntent, serviceIPC, Context.BIND_AUTO_CREATE | Context.BIND_ABOVE_CLIENT);
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
        // In light mode the toolbar and content are nearly the same shade, so a 1dp
        // hairline under the toolbar carries the separation; at night the divider
        // color is transparent because the darker chrome against the lighter content
        // already separates them tonally. It follows the toolbar's visibility so
        // hidden-toolbar screens don't show a stray line.
        if (binding.toolbarDivider != null) {
            binding.toolbarDivider.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
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

    /** Maps a gomobile ClientState value to a readable name for logging. */
    private static String stateName(long state) {
        if (state == Android.ClientStateDisconnected) return "Disconnected";
        if (state == Android.ClientStateConnected) return "Connected";
        if (state == Android.ClientStateConnecting) return "Connecting";
        if (state == Android.ClientStateDisconnecting) return "Disconnecting";
        if (state == Android.ClientStateNoNetwork) return "NoNetwork";
        return "Unknown";
    }

    ConnectionListener connectionListener = new ConnectionListener() {
        @Override
        public void onStateChanged(long state) {
            Log.d(LOGTAG, "GO CALLBACK onStateChanged(" + state + " = " + stateName(state) + ")");
            // Legacy per-state callbacks drive the existing states; only the
            // states delivered exclusively through this callback are handled.
            if (state == Android.ClientStateNoNetwork) {
                lastKnownState = ConnectionState.NO_NETWORK;
                for (StateListener listener : serviceStateListeners) {
                    listener.onNoNetwork();
                }
            }
        }

        @Override
        public synchronized void onAddressChanged(String fqdn, String ip) {
            lastFqdn = fqdn;
            lastIp = ip;

            for (StateListener listener : serviceStateListeners) {
                listener.onAddressChanged(fqdn, ip);
            }
        }

        public void onConnected() {
            Log.d(LOGTAG, "GO CALLBACK onConnected()");
            lastKnownState = ConnectionState.CONNECTED;

            isSSOFinishedWell = true;
            for (StateListener listener : serviceStateListeners) {
                listener.onConnected();
            }
        }

        public void onConnecting() {
            Log.d(LOGTAG, "GO CALLBACK onConnecting()");
            lastKnownState = ConnectionState.CONNECTING;

            isSSOFinishedWell = true;
            for (StateListener listener : serviceStateListeners) {
                listener.onConnecting();
            }
        }

        public void onDisconnecting() {
            Log.d(LOGTAG, "GO CALLBACK onDisconnecting()");
            lastKnownState = ConnectionState.DISCONNECTING;

            for (StateListener listener : serviceStateListeners) {
                listener.onDisconnecting();
            }
        }

        public void onDisconnected() {
            Log.d(LOGTAG, "GO CALLBACK onDisconnected()");
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

    private final SessionEventListener sessionEventListener = new SessionEventListener() {
        @Override
        public void onSessionExpiring(long expiresAtUnixSeconds, long leadMinutes, boolean finalWarning) {
            // Nothing to do in the UI: the notification carries the warning
            // from the background, and the home screen's session row states the
            // deadline continuously with the same extend action. A dialog would
            // be a third copy of that, and an interruption an event known ten
            // minutes ahead does not warrant.
        }

        @Override
        public void onSessionExpired() {
            // No dialog: the home screen states it where the connection status
            // lives, and the connect toggle already runs the interactive login.
            runOnUiThread(() -> {
                for (StateListener listener : serviceStateListeners) {
                    listener.onLoginRequired();
                }
            });
        }

        @Override
        public void onSessionDeadlineChanged(long expiresAtUnixSeconds) {
            runOnUiThread(() -> {
                for (StateListener listener : serviceStateListeners) {
                    listener.onSessionDeadlineChanged(expiresAtUnixSeconds);
                }
            });
        }
    };

    @Override
    public long sessionExpiresAt() {
        return mBinder == null ? 0 : mBinder.sessionExpiresAt();
    }

    @Override
    public void extendSession() {
        if (mBinder == null) {
            return;
        }
        extendInProgress = true;
        mBinder.extendAuthSession(extendUrlOpener, useDeviceCodeFlow, new ErrListener() {
            @Override
            public void onSuccess() {
                extendInProgress = false;
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        R.string.session_extended, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(Exception e) {
                extendInProgress = false;
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        getString(R.string.session_extend_failed, e.getMessage()), Toast.LENGTH_LONG).show());
            }
        });
    }

    // The login urlOpener stops the engine when the SSO surface is dismissed
    // without success, which also kills its pending PKCE wait. An extend must
    // keep the tunnel up, so it cancels just the extend flow instead —
    // otherwise the abandoned wait holds its loopback port and every later
    // attempt fails to bind.
    private URLOpener buildExtendURLOpener() {
        if (!useDeviceCodeFlow) {
            return new CustomTabURLOpener(this, this::cancelExtendSession);
        }
        return new URLOpener() {
            @Override
            public void open(String url, String userCode) {
                runOnUiThread(() -> {
                    qrCodeDialog = QrCodeDialog.newInstance(url, userCode,
                            MainActivity.this::cancelExtendSession);
                    qrCodeDialog.show(getSupportFragmentManager(), "QrCodeDialog");

                    if (!isRunningOnTV) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        } catch (Exception e) {
                            Log.e(LOGTAG, "Failed to open browser for device code flow: " + e.getMessage());
                        }
                    }
                });
            }

            @Override
            public void onLoginSuccess() {
                extendInProgress = false;
                runOnUiThread(() -> {
                    if (qrCodeDialog != null && qrCodeDialog.isVisible()) {
                        qrCodeDialog.dismiss();
                        qrCodeDialog = null;
                    }
                });
            }
        };
    }

    private void cancelExtendSession() {
        if (!extendInProgress) {
            return;
        }
        extendInProgress = false;
        if (mBinder != null) {
            mBinder.cancelExtendAuthSession();
            return;
        }
        // The SSO round-trip can outlive the binding (the activity stops while
        // the browser is in front). Dropping the cancel here would leave the Go
        // flow holding its loopback port until it times out, and every later
        // attempt would fail with "already in progress" — so defer it to the
        // next bind instead.
        pendingExtendCancel = true;
    }
}
