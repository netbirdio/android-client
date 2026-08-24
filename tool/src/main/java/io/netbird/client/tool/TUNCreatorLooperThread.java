package io.netbird.client.tool;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import java.util.Objects;
import java.util.function.Consumer;

public class TUNCreatorLooperThread extends Thread {
    private static final String TAG = TUNCreatorLooperThread.class.getSimpleName();

    /** what value of the message asking for the TUN to be rebuilt. */
    public static final int MSG_RENEW_TUN = 1;

    /**
     * arg1 value asking for the rebuild to happen even when the engine reports
     * the same routes and search domains as before.
     */
    public static final int ARG_FORCE = 1;

    private Handler handler;

    private final Consumer<Boolean> tunCreator;

    public TUNCreatorLooperThread(Consumer<Boolean> tunCreator) {
        this.tunCreator = tunCreator;
    }

    public void run() {
        Looper.prepare();

        synchronized (this) {
            handler = new Handler(Objects.requireNonNull(Looper.myLooper())) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    if (msg.what == MSG_RENEW_TUN) {
                        boolean force = msg.arg1 == ARG_FORCE;
                        Log.d(TAG, "handleMessage: renewing TUN!" + (force ? " (forced)" : ""));
                        tunCreator.accept(force);
                    }
                }
            };
            notifyAll();
        }

        Looper.loop();
    }

    public synchronized Handler getHandler() {
        while (handler == null) {
            try {
                wait();
            } catch (InterruptedException e) {
                Log.d(TAG, "getHandler: ", e);
            }
        }

        return handler;
    }
}
