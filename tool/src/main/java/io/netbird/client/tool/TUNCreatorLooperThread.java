package io.netbird.client.tool;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import java.util.Objects;

public class TUNCreatorLooperThread extends Thread {
    private static final String TAG = TUNCreatorLooperThread.class.getSimpleName();
    private Handler handler;

    private final Runnable tunCreator;

    public TUNCreatorLooperThread(Runnable tunCreator) {
        this.tunCreator = tunCreator;
    }

    public void run() {
        Looper.prepare();

        synchronized (this) {
            handler = new Handler(Objects.requireNonNull(Looper.myLooper())) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    if (msg.what == 1) {
                        Log.d(TAG, "handleMessage: renewing TUN!");
                        tunCreator.run();
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
