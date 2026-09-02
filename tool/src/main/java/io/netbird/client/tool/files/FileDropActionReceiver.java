package io.netbird.client.tool.files;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Answers a transfer offer straight from its notification. The decision goes to
 * the Go layer through {@link FileDropManager}, which is process-wide, so this
 * works whether or not an activity is alive.
 */
public class FileDropActionReceiver extends BroadcastReceiver {

    private static final String LOGTAG = "FileDropActionReceiver";

    public static final String ACTION_ACCEPT = "io.netbird.client.action.FILE_DROP_ACCEPT";
    public static final String ACTION_DECLINE = "io.netbird.client.action.FILE_DROP_DECLINE";
    public static final String EXTRA_TRANSFER_ID = "transferId";

    @Override
    public void onReceive(Context context, Intent intent) {
        String transferId = intent.getStringExtra(EXTRA_TRANSFER_ID);
        if (transferId == null || transferId.isEmpty()) {
            Log.w(LOGTAG, "no transfer id in " + intent.getAction());
            return;
        }

        new FileDropNotification(context).cancelOffer(transferId);

        String action = intent.getAction();
        if (ACTION_ACCEPT.equals(action)) {
            FileDropManager.get().accept(transferId, (ok, error) -> {
                if (!ok) {
                    Log.w(LOGTAG, "failed to accept " + transferId + ": " + error);
                }
            });
        } else if (ACTION_DECLINE.equals(action)) {
            FileDropManager.get().decline(transferId, (ok, error) -> {
                if (!ok) {
                    Log.w(LOGTAG, "failed to decline " + transferId + ": " + error);
                }
            });
        } else {
            Log.w(LOGTAG, "unexpected action " + action);
        }
    }
}
