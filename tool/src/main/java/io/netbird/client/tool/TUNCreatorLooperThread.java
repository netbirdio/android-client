package io.netbird.client.tool;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import java.util.Objects;

public class TUNCreatorLooperThread extends Thread {
    private static final String TAG = TUNCreatorLooperThread.class.getSimpleName();
    private Handler handler;

    private final Consumer<String> tunCreator;

    public TUNCreatorLooperThread(Consumer<String> tunCreator) {
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
                        String routes = msg.obj.toString();
                        tunCreator.accept(routes);
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
