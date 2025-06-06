package io.netbird.client.ui.home;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.util.Log;
import android.widget.TextView;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;

class ButtonAnimation {
    private LottieAnimationView btn;
    private TextView textConnStatus;

    private enum AnimationState {
        DISCONNECTED("Disconnected"),
        CONNECTING("Connecting"),
        CONNECTED("Connected"),
        DISCONNECTING("Disconnecting");

        private final String text;
        AnimationState(String text) { this.text = text; }
    }

    private AnimationState currentState = AnimationState.DISCONNECTED;

    // Frame ranges
    private static final int CONNECTING_START = 78, CONNECTING_END = 120;
    private static final int CONNECTING_FADE_OUT_START = 121, CONNECTING_FADE_OUT_END = 142;
    private static final int DISCONNECTING_FADE_IN_START = 152, DISCONNECTING_FADE_IN_END = 214;
    private static final int DISCONNECTING_LOOP_START = 215, DISCONNECTING_LOOP_END = 258;
    private static final int DISCONNECTING_FADE_OUT_START = 259, DISCONNECTING_FADE_OUT_END = 339;


    public ButtonAnimation() {
    }

    public void refresh(LottieAnimationView buttonConnect, TextView textConnStatus) {
        Log.i("ButtonAnimation", "refresh: "+currentState);
        btn = buttonConnect;
        this.textConnStatus = textConnStatus;
        updateText(currentState.text);

        switch (currentState) {
            case DISCONNECTED:
                btn.setRepeatCount(0);
                btn.setMinAndMaxFrame(DISCONNECTING_FADE_OUT_END, DISCONNECTING_FADE_OUT_END);
                btn.playAnimation();
                break;

            case CONNECTING:
                btn.setRepeatCount(LottieDrawable.INFINITE);
                btn.setMinAndMaxFrame(CONNECTING_START, CONNECTING_END);
                btn.playAnimation();
                break;

            case CONNECTED:
                btn.setRepeatCount(0);
                btn.setMinAndMaxFrame(CONNECTING_FADE_OUT_END, CONNECTING_FADE_OUT_END);
                btn.playAnimation();
                break;

            case DISCONNECTING:
                btn.setRepeatCount(LottieDrawable.INFINITE);
                btn.setMinAndMaxFrame(DISCONNECTING_LOOP_START, DISCONNECTING_LOOP_END);
                btn.playAnimation();
                break;
        }
    }

    public void destroy() {
        Log.d("ButtonAnimation", currentState+" -> destroy ");
        btn.cancelAnimation();
        btn.removeAllAnimatorListeners();
        btn.setImageDrawable(null);
    }

    public void connecting() {
        Log.d("ButtonAnimation", currentState+" -> connecting ");
        if (currentState == AnimationState.CONNECTING)
            return;

        // Go send invalid connecting state for a moment
        if (currentState == AnimationState.DISCONNECTING)
            return;

        currentState = AnimationState.CONNECTING;
        updateText(AnimationState.CONNECTING.text);

        btn.removeAllAnimatorListeners();
        btn.setMinAndMaxFrame(CONNECTING_START, CONNECTING_END);
        btn.setRepeatCount(LottieDrawable.INFINITE);
        btn.playAnimation();
    }

    public void connected() {
        Log.d("ButtonAnimation", currentState+" -> connected ");
        if (currentState == AnimationState.CONNECTED) return;

        updateText(AnimationState.CONNECTED.text);

        btn.removeAllAnimatorListeners();

        if (!btn.isAnimating()) {
            // when we switch the fragment and the animation is not running
            currentState = AnimationState.CONNECTED;
            btn.setMinAndMaxFrame(CONNECTING_FADE_OUT_END, CONNECTING_FADE_OUT_END);
            btn.setRepeatCount(0);
            btn.playAnimation();
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
        Log.d("ButtonAnimation", currentState+" -> disconnecting ");
        if (currentState == AnimationState.DISCONNECTING) return;

        currentState = AnimationState.DISCONNECTING;
        updateText(AnimationState.DISCONNECTING.text);

        btn.removeAllAnimatorListeners();
        btn.setRepeatCount(0);
        btn.setMinAndMaxFrame(DISCONNECTING_FADE_IN_START, DISCONNECTING_FADE_IN_END);
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
        Log.d("ButtonAnimation", currentState+" -> disconnected ");
        if (currentState == AnimationState.DISCONNECTED)
            return;

        updateText(AnimationState.DISCONNECTED.text);

        btn.removeAllAnimatorListeners();
        if(currentState == AnimationState.CONNECTING) {
            currentState = AnimationState.DISCONNECTED;
            btn.setRepeatCount(0);
            btn.setMinAndMaxFrame(DISCONNECTING_FADE_OUT_END, DISCONNECTING_FADE_OUT_END);
            btn.playAnimation();
            return;
        }

        currentState = AnimationState.DISCONNECTED;
        btn.setRepeatCount(0);
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

    private void updateText(String text) {
        Log.i("ButtonAnimation", "set text: "+text);
        textConnStatus.post(() -> textConnStatus.setText(text));
    }
}
