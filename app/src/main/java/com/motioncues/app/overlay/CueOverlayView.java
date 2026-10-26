package com.motioncues.app.overlay;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
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
 * Точки-сигналы по левому и правому краю экрана, почти на всю высоту.
 *
 * <p>Поле точек смещается против ускорения машины и плавно возвращается, когда ускорение
 * заканчивается. При резком движении точки заходят за границу окна и уменьшаются,
 * а с другой стороны вырастают новые. Когда поле стоит, кадр не перерисовывается.
 *
 * <p>Все параметры берутся из итогового профиля настроек ({@link CueProfile}) —
 * в том числе в режиме «Как на iPhone».
 */
@SuppressLint("ViewConstructor") // создаётся только из кода, не из XML
final class CueOverlayView extends View implements Choreographer.FrameCallback {

    /** Плавность скрытия и показа точек, пока открыто приложение. */
    private static final long HIDE_ANIMATION_MS = 200;
    /** Порог, ниже которого кадр считается неизменным, px. */
    private static final float REDRAW_EPSILON_PX = 0.05f;
    /**
     * Смещение поля при максимальном сигнале движения, dp. Больше полушага решётки —
     * при резком движении точки успевают зайти за границу окна и «прорасти» с другой стороны.
     */
    private static final float TRAVEL_DP = 40f;

    private static final float RING_WIDTH_DP = 1.5f;
    private static final float EDGE_PAD_DP = 12f;
    /** Отступ окон сверху и снизу — точки идут почти на всю высоту, как на iPhone. */
    private static final float VERTICAL_PAD_DP = 8f;
    private static final float COLUMN_GAP_DP = 22f;

    private final MotionEngine engine;
    private final AppSettings settings;
    private final CueFlow flow;
    private final DotRenderer renderer;
    private final float density;
    private final float windowAlpha;

    private CuePalette palette;
    private float radiusDp;
    private int opacityLevel;
    private int countLevel = -1;

    private boolean running;
    /** 1 — точки видны, 0 — скрыты; меняется плавно. */
    private float shown = 1f;
    private ValueAnimator shownAnimator;

    private float drawnX = Float.NaN, drawnY = Float.NaN;
    private int drawnAlpha = -1;

    /**
     * @param windowAlpha прозрачность окна-оверлея: учитываем, чтобы итоговая
     *                    яркость точек совпадала с задуманной
     */
    CueOverlayView(Context context, MotionEngine engine, float windowAlpha) {
        super(context);
        this.engine = engine;
        this.windowAlpha = windowAlpha;
        settings = new AppSettings(context);
        density = getResources().getDisplayMetrics().density;
        flow = new CueFlow(engine);
        renderer = new DotRenderer(RING_WIDTH_DP * density);
        applySettings();
    }

    /** Перечитывает настройки и применяет их на лету — без перезапуска эффекта. */
    void applySettings() {
        ThemeMode theme = settings.theme();
        CueProfile profile = CueProfile.from(settings, ThemeController.isNight(getContext(), theme));
        palette = CuePalette.resolve(getContext(), theme, profile.dotColor);
        radiusDp = DotStyle.radiusDp(profile.dotSize);
        opacityLevel = profile.dotOpacity;
        engine.setSensitivity(Sensitivity.factor(profile.sensitivity));
        engine.setMode(profile.motionMode);
        if (profile.dotCount != countLevel) {
            countLevel = profile.dotCount;
            layoutField(getWidth(), getHeight());
        }
        drawnAlpha = -1;
        invalidate();
    }

    /**
     * Скрывает точки, пока открыто приложение (там их показывает превью).
     * Датчики и физика продолжают работать — после показа точки сразу на верном месте.
     */
    void setHidden(boolean hidden) {
        float target = hidden ? 0f : 1f;
        if (shownAnimator != null) {
            shownAnimator.cancel();
        }
        if (!isAttachedToWindow()) {
            shown = target;
            return;
        }
        shownAnimator = ValueAnimator.ofFloat(shown, target).setDuration(HIDE_ANIMATION_MS);
        shownAnimator.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        shownAnimator.start();
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        updateRotation();
        flow.start();
        Choreographer.getInstance().postFrameCallback(this);
    }

    void stop() {
        if (!running) {
            return;
        }
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        flow.stop();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) {
            return;
        }
        flow.step(frameTimeNanos);
        if (Math.abs(flow.fieldX() - drawnX) > REDRAW_EPSILON_PX
                || Math.abs(flow.fieldY() - drawnY) > REDRAW_EPSILON_PX
                || currentAlpha() != drawnAlpha) {
            invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawnX = flow.fieldX();
        drawnY = flow.fieldY();
        drawnAlpha = currentAlpha();
        // Скрыты — запомнили состояние, чтобы не перерисовываться зря, и ничего не рисуем
        renderer.draw(canvas, drawnX, drawnY, radiusDp * density, drawnAlpha, palette);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        layoutField(w, h);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateRotation();
        // Сменилась системная тема или обои: для Monet и «Как на iPhone» пересчитываем цвета
        applySettings();
    }

    private void layoutField(int width, int height) {
        if (width == 0 || height == 0) {
            return;
        }
        float gap = COLUMN_GAP_DP * density;
        float rowStep = DotDensity.rowStepDp(countLevel) * density;
        int rows = Math.max(2, (int) ((height - 2f * VERTICAL_PAD_DP * density) / rowStep));
        renderer.layout(width, height, new DotField(gap, rowStep, DotDensity.columns(countLevel), rows),
                EDGE_PAD_DP * density);
        flow.configure(TRAVEL_DP * density, gap * 2f, rowStep);
        drawnX = Float.NaN;
        invalidate();
    }

    private void updateRotation() {
        Display display = getContext().getDisplay();
        if (display != null) {
            engine.setDisplayRotation(display.getRotation());
        }
    }

    /** Прозрачность заливки с учётом активности, скрытия и прозрачности окна. */
    private int currentAlpha() {
        return toAlpha(palette.opacity(opacityLevel, flow.activity()) * shown / windowAlpha);
    }

    private static int toAlpha(float opacity) {
        return Math.round(Math.max(0f, Math.min(1f, opacity)) * 255f);
    }
}
