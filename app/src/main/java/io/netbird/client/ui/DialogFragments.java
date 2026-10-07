package io.netbird.client.ui;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

public final class DialogFragments {

    private DialogFragments() {
    }

    // A click listener runs as a posted message and the engine's URLOpener runs
    // from a background callback, so by the time either gets to show a dialog
    // the host may already be paused, finishing or past onSaveInstanceState.
    // None of those can host a window. Showing synchronously keeps a pending
    // show() from being flushed by the pause itself; the resumed check covers
    // the click landing behind the pause.
    public static boolean showNow(LifecycleOwner host, FragmentManager fm, DialogFragment dialog, String tag) {
        if (!host.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            return false;
        }
        dialog.showNow(fm, tag);
        return true;
    }
}
