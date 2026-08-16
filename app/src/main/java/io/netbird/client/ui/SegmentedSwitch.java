package io.netbird.client.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import io.netbird.client.R;

/**
 * Two-way segmented control: a track laid out by {@code bg_segmented_track}
 * holding a thumb and two labels, with the thumb sliding between them.
 * <p>
 * The view ids are passed in rather than fixed, so one implementation serves
 * every segmented control in the app. {@link io.netbird.client.ui.server.ManagementServerSwitch}
 * predates this and keeps its own copy along with the Cloud logo it draws.
 */
public final class SegmentedSwitch {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(boolean second);
    }

    private static final long SLIDE_DURATION_MS = 180;

    private final Context context;
    private final FrameLayout track;
    private final View thumb;
    private final TextView firstLabel;
    private final TextView secondLabel;
    private final OnSelectionChangedListener listener;

    private boolean second;
    // True while the slide animation owns translationX, so a layout pass
    // triggered mid-slide cannot snap the thumb back.
    private boolean sliding;

    public SegmentedSwitch(View root, int trackId, int thumbId, int firstButtonId,
                           int firstLabelId, int secondButtonId, int secondLabelId,
                           OnSelectionChangedListener listener) {
        this.context = root.getContext();
        this.listener = listener;

        track = root.findViewById(trackId);
        thumb = root.findViewById(thumbId);
        firstLabel = root.findViewById(firstLabelId);
        secondLabel = root.findViewById(secondLabelId);

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

        root.findViewById(firstButtonId).setOnClickListener(v -> select(false, true));
        root.findViewById(secondButtonId).setOnClickListener(v -> select(true, true));
        applyLabelColors();
    }

    public boolean isSecondSelected() {
        return second;
    }

    /** Selects a segment without animating or notifying, for initial state. */
    public void selectSilently(boolean second) {
        select(second, false);
    }

    private void select(boolean second, boolean notify) {
        if (this.second == second) {
            return;
        }
        this.second = second;

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
            listener.onSelectionChanged(second);
        }
    }

    private void applyLabelColors() {
        ColorStateList active = ContextCompat.getColorStateList(context, R.color.nb_txt);
        ColorStateList inactive = ContextCompat.getColorStateList(context, R.color.nb_txt_light);
        firstLabel.setTextColor(second ? inactive : active);
        secondLabel.setTextColor(second ? active : inactive);
    }

    private int halfWidth() {
        return (track.getWidth() - track.getPaddingLeft() - track.getPaddingRight()) / 2;
    }

    /** Resting X offset of the thumb for the current selection, in pixels. */
    private float restingOffset(int width) {
        if (!second) {
            return 0f;
        }
        boolean rtl = track.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        return rtl ? -width : width;
    }
}
