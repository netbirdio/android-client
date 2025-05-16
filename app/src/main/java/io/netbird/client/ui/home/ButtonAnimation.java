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

    public ButtonAnimation(LottieAnimationView buttonConnect, TextView textConnStatus) {
        this.btn = buttonConnect;
        this.textConnStatus = textConnStatus;
    }

    public void connecting() {
        if (currentState == AnimationState.CONNECTING || currentState == AnimationState.CONNECTING_LOOP) return;

        // avoid incorrect callback from engine
        if (currentState == AnimationState.DISCONNECTING) {
            return;
        }

        currentState = AnimationState.CONNECTING;

        textConnStatus.post(() -> textConnStatus.setText("Connecting"));

        btn.removeAllAnimatorListeners();
        btn.setAnimation("button_start_connecting.json");
        btn.setRepeatCount(0); // play once
        btn.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                    currentState = AnimationState.CONNECTING_LOOP;
                    btn.setAnimation("button_connecting_loop.json");
                    btn.setRepeatCount(LottieDrawable.INFINITE);
                    btn.removeAnimatorListener(this);
                    btn.playAnimation();
            }
        });
        btn.playAnimation();
    }

    public void connected() {
        if (currentState == AnimationState.CONNECTED) return;


        textConnStatus.post(() -> textConnStatus.setText("Connected"));

        btn.removeAllAnimatorListeners();
        if (!btn.isAnimating()) {
            currentState = AnimationState.CONNECTED;
            btn.setAnimation("button_connected.json");
            btn.setRepeatCount(LottieDrawable.INFINITE);
            btn.playAnimation();
        } else {
            // Wait for current animation to end, then start the connected animation
            btn.setRepeatCount(0);
            btn.addAnimatorListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    currentState = AnimationState.CONNECTED;
                    btn.setAnimation("button_connected.json");
                    btn.setRepeatCount(LottieDrawable.INFINITE);
                    btn.removeAnimatorListener(this);
                    btn.playAnimation();
                }
            });
        }
    }

    public void disconnecting() {
        if (currentState == AnimationState.DISCONNECTING) return;

        currentState = AnimationState.DISCONNECTING;

        textConnStatus.post(() -> textConnStatus.setText("Disconnecting"));

        btn.removeAllAnimatorListeners();
        btn.setRepeatCount(0); // let current animation finish
        btn.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                btn.removeAnimatorListener(this);
                btn.setAnimation("button_disconnecting.json");
                btn.setRepeatCount(LottieDrawable.INFINITE);
                btn.playAnimation();
            }
        });
    }

    public void disconnected() {
        if (currentState == AnimationState.DISCONNECTED) return;

        btn.removeAllAnimatorListeners();
        btn.setRepeatCount(0); // let current animation finish
        btn.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentState = AnimationState.DISCONNECTED;
                btn.removeAnimatorListener(this);
                btn.setAnimation("button_start_connecting.json");
                btn.setRepeatCount(0);
                btn.setProgress(0f);
                btn.pauseAnimation();

                textConnStatus.post(() -> textConnStatus.setText("Disconnected"));
            }
        });
    }
}
