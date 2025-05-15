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
                if (currentState == AnimationState.CONNECTING) {
                    currentState = AnimationState.CONNECTING_LOOP;
                    btn.setAnimation("button_connecting_loop.json");
                    btn.setRepeatCount(LottieDrawable.INFINITE);
                    btn.removeAllAnimatorListeners();
                    btn.playAnimation();
                }
            }
        });
        btn.playAnimation();
    }

    public void connected() {
        if (currentState == AnimationState.CONNECTED) return;

        currentState = AnimationState.CONNECTED;

        textConnStatus.post(() -> textConnStatus.setText("Connected"));

        // Set to CONNECTED only after current animation ends and transition completes
        btn.removeAllAnimatorListeners();
        btn.setRepeatCount(0); // let current animation finish
        btn.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                btn.setAnimation("button_connected.json");
                btn.setRepeatCount(LottieDrawable.INFINITE);
                btn.removeAllAnimatorListeners();
                btn.playAnimation();
            }
        });
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
                btn.setAnimation("button_disconnecting.json");
                btn.setRepeatCount(LottieDrawable.INFINITE);
                btn.removeAllAnimatorListeners();
                btn.playAnimation();
            }
        });
    }

    public void disconnected() {
        if (currentState == AnimationState.DISCONNECTED) return;

        currentState = AnimationState.DISCONNECTED;

        btn.removeAllAnimatorListeners();
        btn.setRepeatCount(0); // let current animation finish
        btn.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentState = AnimationState.DISCONNECTED;
                btn.setAnimation("button_start_connecting.json");
                btn.setRepeatCount(0); // show stopped state
                btn.removeAllAnimatorListeners();
                btn.playAnimation();

                // Update UI after animation ends
                textConnStatus.post(() -> textConnStatus.setText("Disconnected"));
            }
        });
    }
}
