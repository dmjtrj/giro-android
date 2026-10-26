package com.motioncues.app.overlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.Display;
import android.view.View;

import com.motioncues.app.motion.MotionEngine;
import com.motioncues.app.motion.Sensitivity;
import com.motioncues.app.settings.AppSettings;
import com.motioncues.app.settings.CueProfile;
import com.motioncues.app.settings.ThemeController;
import com.motioncues.app.settings.ThemeMode;

/**
 * Живое превью точек для экранов приложения. Работает ровно как эффект: те же датчики,
 * фильтры и физика ({@link CueFlow}), та же чувствительность, цвет, размер и непрозрачность.
 * Двигаете телефон — точки в карточке смещаются так же, как будут смещаться на экране.
 *
 * <p>Датчики и анимация включены, только пока превью видно на экране и точки показаны
 * ({@link #setDotsShown}): на главном экране при выключенном эффекте точки плавно гаснут.
 */
public final class CuePreviewView extends View implements Choreographer.FrameCallback {

    private static final float EDGE_PAD_DP = 16f;
    private static final float COLUMN_GAP_DP = 22f;
    /**
     * Превью — миниатюра экрана: ряды чаще, чтобы в небольшой карточке поместилось 4–5 рядов.
     * Масштаб один для всех уровней количества — разница между уровнями видна как в эффекте.
     * Шаг не меньше ~29 dp: даже самые крупные точки (Ø 15 dp) не наползают друг на друга.
     */
    private static final float ROW_SCALE = 0.3f;
    private static final float VERTICAL_PAD_DP = 12f;
    private static final float RING_WIDTH_DP = 1.5f;
    /** Смещение в том же масштабе, что и ряды: относительно решётки — как в эффекте (там 40 dp). */
    private static final float TRAVEL_DP = 40f * ROW_SCALE;
    private static final float REDRAW_EPSILON_PX = 0.05f;
    /** Плавность появления и исчезания точек. */
    private static final long SHOW_ANIMATION_MS = 220;

    private final DotRenderer renderer;
    private final float density;
    private final AppSettings settings;
    private final MotionEngine engine;
    private final CueFlow flow;

    private CuePalette palette;
    private float radiusDp;
    private int opacityLevel;
    private int countLevel = -1;

    private boolean animating;
    /** Показывать ли точки; {@link #shown} плавно идёт к 1 или 0. */
    private boolean dotsShown = true;
    private float shown = 1f;
    private ValueAnimator shownAnimator;
    private float drawnX = Float.NaN, drawnY = Float.NaN;
    private float drawnActivity = -1f;

    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener = (prefs, key) -> {
        readSettings();
        invalidate();
    };

    public CuePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        settings = new AppSettings(context);
        renderer = new DotRenderer(RING_WIDTH_DP * density);
        engine = new MotionEngine(context);
        flow = new CueFlow(engine);
        readSettings();
    }

    /**
     * Показывает или плавно гасит точки. Пока точки скрыты, датчики и анимация
     * превью остановлены.
     */
    public void setDotsShown(boolean show, boolean animate) {
        if (shownAnimator != null) {
            shownAnimator.cancel();
            shownAnimator = null;
        }
        dotsShown = show;
        float target = show ? 1f : 0f;
        if (!animate || !isAttachedToWindow()) {
            shown = target;
            updateAnimation();
            invalidate();
            return;
        }
        updateAnimation();
        shownAnimator = ValueAnimator.ofFloat(shown, target).setDuration(SHOW_ANIMATION_MS);
        shownAnimator.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        shownAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Погасли — датчики больше не нужны
                updateAnimation();
            }
        });
        shownAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        settings.registerListener(settingsListener);
        readSettings();
        updateAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        settings.unregisterListener(settingsListener);
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateAnimation();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateRotation();
        readSettings();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        layoutField(w, h);
    }

    private void layoutField(int width, int height) {
        if (width == 0 || height == 0) {
            return;
        }
        float gap = COLUMN_GAP_DP * density;
        float rowStep = DotDensity.rowStepDp(countLevel) * ROW_SCALE * density;
        int rows = Math.max(2, (int) ((height - 2f * VERTICAL_PAD_DP * density) / rowStep));
        renderer.layout(width, height, new DotField(gap, rowStep, DotDensity.columns(countLevel), rows),
                EDGE_PAD_DP * density);
        flow.configure(TRAVEL_DP * density, gap * 2f, rowStep);
        drawnX = Float.NaN;
        invalidate();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!animating) {
            return;
        }
        flow.step(frameTimeNanos);
        if (Math.abs(flow.fieldX() - drawnX) > REDRAW_EPSILON_PX
                || Math.abs(flow.fieldY() - drawnY) > REDRAW_EPSILON_PX
                || Math.abs(flow.activity() - drawnActivity) > 0.005f) {
            invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawnX = flow.fieldX();
        drawnY = flow.fieldY();
        drawnActivity = flow.activity();
        // Та же формула видимости, что у эффекта
        int alpha = Math.round(Math.min(1f, palette.opacity(opacityLevel, drawnActivity)) * shown * 255f);
        renderer.draw(canvas, drawnX, drawnY, radiusDp * density, alpha, palette);
    }

    /** Тот же итоговый профиль, что у эффекта, — включая режим «Как на iPhone». */
    private void readSettings() {
        ThemeMode theme = settings.theme();
        CueProfile profile = CueProfile.from(settings, ThemeController.isNight(getContext(), theme));
        palette = CuePalette.resolve(getContext(), theme, profile.dotColor);
        radiusDp = DotStyle.radiusDp(profile.dotSize);
        opacityLevel = profile.dotOpacity;
        engine.setSensitivity(Sensitivity.factor(profile.sensitivity));
        // Превью ведёт себя по выбранному режиму — ровно как эффект
        engine.setMode(profile.motionMode);
        if (profile.dotCount != countLevel) {
            countLevel = profile.dotCount;
            layoutField(getWidth(), getHeight());
        }
    }

    private void updateRotation() {
        Display display = getDisplay();
        if (display != null) {
            engine.setDisplayRotation(display.getRotation());
        }
    }

    private void updateAnimation() {
        boolean shouldAnimate = isAttachedToWindow() && getWindowVisibility() == VISIBLE
                && engine.isSupported() && (dotsShown || shown > 0f);
        if (shouldAnimate && !animating) {
            animating = true;
            updateRotation();
            flow.start();
            Choreographer.getInstance().postFrameCallback(this);
        } else if (!shouldAnimate) {
            stopAnimation();
        }
    }

    private void stopAnimation() {
        if (!animating) {
            return;
        }
        animating = false;
        Choreographer.getInstance().removeFrameCallback(this);
        flow.stop();
    }
}
