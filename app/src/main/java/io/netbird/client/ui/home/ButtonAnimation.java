package io.netbird.client.ui.home;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.util.Log;
import android.widget.TextView;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;

class ButtonAnimation {
    private final LottieAnimationView btn;
    private final TextView textConnStatus;

    private enum AnimationState {
        DISCONNECTED,
        CONNECTING,
        CONNECTING_LOOP,
        CONNECTED,
        DISCONNECTING,
    }

    private AnimationState currentState = AnimationState.DISCONNECTED;

    // Frame ranges
    private final int CONNECTED = 142;
    private final int CONNECTING_START = 78;
    private final int CONNECTING_END = 120;

    private final int CONNECTING_FADE_OUT_START = 121;
    private final int CONNECTING_FADE_OUT_END = 142;

    private final int DISCONNECTING_LOOP_FADE_IN_START = 152;
    private final int DISCONNECTING_LOOP_FADE_IN_END = 214;

    private final int DISCONNECTING_LOOP_START = 215;
    private final int DISCONNECTING_LOOP_END = 258;

    private final int DISCONNECTING_FADE_OUT_START = 259;
    private final int DISCONNECTING_FADE_OUT_END = 339;

    public ButtonAnimation(LottieAnimationView buttonConnect, TextView textConnStatus) {
        btn = buttonConnect;
        this.textConnStatus = textConnStatus;
    }

    public void connecting() {
        if (currentState == AnimationState.CONNECTING || currentState == AnimationState.CONNECTING_LOOP)
            return;

        if (currentState == AnimationState.DISCONNECTING)
            return;

        currentState = AnimationState.CONNECTING;
        textConnStatus.post(() -> textConnStatus.setText("Connecting"));

        btn.removeAllAnimatorListeners();
        btn.setMinAndMaxFrame(CONNECTING_START, CONNECTING_END);
        btn.setRepeatCount(LottieDrawable.INFINITE);
        btn.playAnimation();
    }

    public void connected() {
        if (currentState == AnimationState.CONNECTED) return;

        textConnStatus.post(() -> textConnStatus.setText("Connected"));

        btn.removeAllAnimatorListeners();

        if (!btn.isAnimating()) {
            // when we switch the fragment and the animation is not running
            currentState = AnimationState.CONNECTED;
            btn.setRepeatCount(0);
            btn.setFrame(CONNECTED);
            btn.pauseAnimation();
        } else {
            // Wait for current animation to end, then start the connected animation
            btn.setRepeatCount(0);
            btn.addAnimatorListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    btn.removeAnimatorListener(this);
                    currentState = AnimationState.CONNECTED;
                    btn.setMinAndMaxFrame(CONNECTING_FADE_OUT_START, CONNECTING_FADE_OUT_END);
                    btn.setRepeatCount(0);
                    btn.playAnimation();
                }
            });
        }
    }

    public void disconnecting() {
        if (currentState == AnimationState.DISCONNECTING)
            return;

        currentState = AnimationState.DISCONNECTING;
        textConnStatus.post(() -> textConnStatus.setText("Disconnecting"));

        btn.removeAllAnimatorListeners();
        btn.setRepeatCount(0);
        btn.setMinAndMaxFrame(DISCONNECTING_LOOP_FADE_IN_START, DISCONNECTING_LOOP_FADE_IN_END);
        btn.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                btn.removeAnimatorListener(this);
                btn.setMinAndMaxFrame(DISCONNECTING_LOOP_START, DISCONNECTING_LOOP_END);
                btn.setRepeatCount(LottieDrawable.INFINITE);
                btn.playAnimation();
            }
        });

        btn.playAnimation();
    }

    public void disconnected() {
        if (currentState == AnimationState.DISCONNECTED)
            return;

        currentState = AnimationState.DISCONNECTED;
        textConnStatus.post(() -> textConnStatus.setText("Disconnected"));

        btn.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                btn.removeAllAnimatorListeners();
                btn.setRepeatCount(0);
                btn.setMinAndMaxFrame(DISCONNECTING_FADE_OUT_START, DISCONNECTING_FADE_OUT_END);
                btn.playAnimation();
            }
        });
    }
}
