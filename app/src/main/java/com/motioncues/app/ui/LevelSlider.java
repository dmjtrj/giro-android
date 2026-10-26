package com.motioncues.app.ui;

import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;

import com.google.android.material.slider.Slider;

/**
 * Ползунок с делениями: ступенчатый, как раньше, с разметкой — но бегунок не телепортируется,
 * а плавно переезжает на новое деление.
 *
 * <p>Сам ползунок непрерывный, разметку делений рисует Material ({@code continuousModeTickCount}).
 * Палец двигает «невидимое» значение; как только оно переходит на другое деление, бегунок
 * за ~120 мс плавно доезжает до этого деления, срабатывает {@link Listener} (значение
 * применяется сразу, вибрация даёт почувствовать деление). Между делениями бегунок стоит —
 * отсюда ступенчатость.
 */
public final class LevelSlider {

    /** Вызывается, когда бегунок перешёл на новое деление. */
    public interface Listener {
        void onLevelChanged(int level);
    }

    private static final long STEP_ANIMATION_MS = 120;

    private final Slider slider;
    private final Listener listener;
    private int level;
    /** Где бегунок нарисован — анимируется от деления к делению. */
    private float shown;
    private ValueAnimator animator;
    private boolean applying;

    private LevelSlider(Slider slider, int level, Listener listener) {
        this.slider = slider;
        this.level = level;
        this.listener = listener;
        this.shown = level;
    }

    /** Подключает поведение к ползунку и выставляет начальное деление. */
    public static void attach(Slider slider, int level, Listener listener) {
        LevelSlider controller = new LevelSlider(slider, level, listener);
        int levels = Math.round(slider.getValueTo() - slider.getValueFrom()) + 1;
        slider.setContinuousModeTickCount(levels);
        slider.setTickVisible(true);
        slider.setValue(level);
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (fromUser && !controller.applying) {
                controller.onFingerMoved(value);
            }
        });
    }

    private void onFingerMoved(float value) {
        int nearest = Math.round(value);
        if (nearest != level) {
            level = nearest;
            listener.onLevelChanged(nearest);
            animateTo(nearest);
        }
        // Палец сдвинул бегунок за собой — возвращаем его туда, где он должен быть нарисован
        show(shown);
    }

    private void animateTo(int target) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(shown, target).setDuration(STEP_ANIMATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            show(shown);
        });
        animator.start();
    }

    private void show(float value) {
        applying = true;
        slider.setValue(value);
        applying = false;
    }
}
