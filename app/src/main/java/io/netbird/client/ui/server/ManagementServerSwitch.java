package io.netbird.client.ui.server;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import io.netbird.client.R;

/**
 * Cloud / Self-hosted segmented control, the Android counterpart of the
 * desktop's ManagementServerSwitch component. Binds to a track laid out by
 * {@code bg_segmented_track} holding a thumb and two labels, and slides the
 * thumb between them.
 *
 * <p>Shared by the profile editor dialog and the first-run screen so the choice
 * looks identical wherever it is offered.
 */
public final class ManagementServerSwitch {

    public interface OnModeChangedListener {
        void onModeChanged(boolean selfHosted);
    }

    private static final long SLIDE_DURATION_MS = 180;

    private final Context context;
    private final FrameLayout track;
    private final View thumb;
    private final View cloudButton;
    private final View selfHostedButton;
    private final TextView cloudLabel;
    private final TextView selfHostedLabel;
    private final OnModeChangedListener listener;

    private boolean selfHosted;
    // True while the slide animation owns translationX. A layout pass caused by
    // the URL field appearing must not reposition the thumb mid-slide.
    private boolean sliding;

    public ManagementServerSwitch(View root, OnModeChangedListener listener) {
        this.context = root.getContext();
        this.listener = listener;

        track = root.findViewById(R.id.toggle_server_mode);
        thumb = root.findViewById(R.id.segment_thumb);
        cloudButton = root.findViewById(R.id.btn_server_cloud);
        selfHostedButton = root.findViewById(R.id.btn_server_self_hosted);
        cloudLabel = root.findViewById(R.id.label_server_cloud);
        selfHostedLabel = root.findViewById(R.id.label_server_self_hosted);

        applyCloudLogo();

        track.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int half = halfWidth();
            if (half <= 0 || sliding) {
                return;
            }
            ViewGroup.LayoutParams lp = thumb.getLayoutParams();
            if (lp.width != half) {
                lp.width = half;
                thumb.setLayoutParams(lp);
            }
            thumb.setTranslationX(restingOffset(half));
        });

        cloudButton.setOnClickListener(v -> setSelfHosted(false, true));
        selfHostedButton.setOnClickListener(v -> setSelfHosted(true, true));
        applyLabelColors();
    }

    public boolean isSelfHosted() {
        return selfHosted;
    }

    /** Selects a mode without animating or notifying, for initial state. */
    public void setSelfHostedSilently(boolean selfHosted) {
        setSelfHosted(selfHosted, false);
    }

    public void setEnabled(boolean enabled) {
        cloudButton.setEnabled(enabled);
        selfHostedButton.setEnabled(enabled);
    }

    private void setSelfHosted(boolean selfHosted, boolean notify) {
        if (this.selfHosted == selfHosted) {
            return;
        }
        this.selfHosted = selfHosted;

        if (notify) {
            sliding = true;
            thumb.animate()
                    .translationX(restingOffset(halfWidth()))
                    .setDuration(SLIDE_DURATION_MS)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                    .withEndAction(() -> sliding = false)
                    .start();
        } else {
            // No measured width yet at seed time; the layout listener places
            // the thumb once the track is laid out.
            thumb.setTranslationX(restingOffset(halfWidth()));
        }

        applyLabelColors();
        if (notify && listener != null) {
            listener.onModeChanged(selfHosted);
        }
    }

    private void applyCloudLogo() {
        Drawable logo = ContextCompat.getDrawable(context, R.drawable.ic_netbird_btn);
        if (logo == null) {
            return;
        }
        // The PNG's intrinsic size dwarfs the label, so scale it to match.
        int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16,
                context.getResources().getDisplayMetrics());
        logo.setBounds(0, 0, size, size);
        cloudLabel.setCompoundDrawablesRelative(logo, null, null, null);
        cloudLabel.setCompoundDrawablePadding(size / 3);
    }

    private void applyLabelColors() {
        ColorStateList active = ContextCompat.getColorStateList(context, R.color.nb_txt);
        ColorStateList inactive = ContextCompat.getColorStateList(context, R.color.nb_txt_light);
        cloudLabel.setTextColor(selfHosted ? inactive : active);
        selfHostedLabel.setTextColor(selfHosted ? active : inactive);
    }

    private int halfWidth() {
        return (track.getWidth() - track.getPaddingLeft() - track.getPaddingRight()) / 2;
    }

    /** Resting X offset of the thumb for the current mode, in pixels. */
    private float restingOffset(int width) {
        if (!selfHosted) {
            return 0f;
        }
        boolean rtl = track.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        return rtl ? -width : width;
    }
}
