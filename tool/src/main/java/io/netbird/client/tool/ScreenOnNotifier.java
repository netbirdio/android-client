package io.netbird.client.tool;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.core.content.ContextCompat;

import io.netbird.gomobile.android.Android;

/**
 * Asks the Go core to check its heartbeats when the screen turns on, since
 * connections can go stale while the device sleeps. Screen on is the wake
 * signal every supported API level has. The core ignores the call while the
 * engine is not running, so the receiver can follow the service lifecycle.
 */
class ScreenOnNotifier {
    private static final String LOGTAG = ScreenOnNotifier.class.getSimpleName();

    private final Context context;
    private BroadcastReceiver receiver;

    ScreenOnNotifier(Context context) {
        this.context = context;
    }

    /** Registers the receiver. Idempotent. */
    void register() {
        if (receiver != null) {
            return;
        }
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    // Only arms a timer in the core and never waits on the
                    // engine, so it is safe on the main thread.
                    Android.checkHeartbeatsNow();
                }
            }
        };
        // A system broadcast, which a not-exported receiver still gets.
        ContextCompat.registerReceiver(context, receiver,
                new IntentFilter(Intent.ACTION_SCREEN_ON), Context.RECEIVER_NOT_EXPORTED);
    }

    /** Unregisters the receiver. Idempotent. */
    void unregister() {
        if (receiver == null) {
            return;
        }
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            Log.w(LOGTAG, "screen on receiver not registered", e);
        }
        receiver = null;
    }
}
