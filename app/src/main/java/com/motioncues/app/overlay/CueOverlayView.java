package com.motioncues.app.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Choreographer;
import android.view.Display;
import android.view.View;

import com.motioncues.app.motion.CueState;
import com.motioncues.app.motion.MotionEngine;

/**
 * Точки-сигналы по левому и правому краю экрана.
 *
 * <p>На каждом кадре шагает физику движка и сдвигает все точки вместе.
 * Когда точки стоят на месте, кадр не перерисовывается — GPU простаивает.
 */
@SuppressLint("ViewConstructor") // создаётся только из кода, не из XML
final class CueOverlayView extends View implements Choreographer.FrameCallback {

    /** Видимость точек в покое: заметны, но не мешают. */
    private static final float IDLE_VISIBILITY = 0.4f;
    /** Порог, ниже которого кадр считается неизменным, px. */
    private static final float REDRAW_EPSILON_PX = 0.05f;

    private static final float DOT_RADIUS_DP = 4.5f;
    /** Максимальный ход точек; меньше отступа от края, чтобы точки не уходили за экран. */
    private static final float MAX_TRAVEL_DP = 18f;
    private static final float EDGE_MARGIN_DP = 22f;
    private static final float COLUMN_GAP_DP = 16f;
    private static final float ROW_SPACING_DP = 96f;
    /** Доля высоты экрана, которую занимают точки, — по центру. */
    private static final float VERTICAL_SPAN = 0.7f;

    private final MotionEngine engine;
    private final CueState state = new CueState();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final float windowAlpha;

    private float[] dotX = new float[0];
    private float[] dotY = new float[0];
    private int dotColor;
    private float dotOpacity;

    private boolean running;
    private float drawnDx = Float.NaN, drawnDy = Float.NaN;
    private int drawnAlpha = -1;

    /**
     * @param windowAlpha прозрачность окна-оверлея: учитываем, чтобы итоговая
     *                    яркость точек совпадала с задуманной
     */
    CueOverlayView(Context context, MotionEngine engine, float windowAlpha) {
        super(context);
        this.engine = engine;
        this.windowAlpha = windowAlpha;
        density = getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.FILL);
        applyPalette(getResources().getConfiguration());
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        updateRotation();
        engine.start();
        Choreographer.getInstance().postFrameCallback(this);
    }

    void stop() {
        if (!running) {
            return;
        }
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        engine.stop();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) {
            return;
        }
        engine.step(frameTimeNanos, state);
        float travel = MAX_TRAVEL_DP * density;
        float dx = state.offsetX * travel;
        float dy = state.offsetY * travel;
        if (Math.abs(dx - drawnDx) > REDRAW_EPSILON_PX
                || Math.abs(dy - drawnDy) > REDRAW_EPSILON_PX
                || currentAlpha() != drawnAlpha) {
            invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float travel = MAX_TRAVEL_DP * density;
        drawnDx = state.offsetX * travel;
        drawnDy = state.offsetY * travel;
        drawnAlpha = currentAlpha();

        paint.setColor(dotColor);
        paint.setAlpha(drawnAlpha);
        float radius = DOT_RADIUS_DP * density;
        for (int i = 0; i < dotX.length; i++) {
            canvas.drawCircle(dotX[i] + drawnDx, dotY[i] + drawnDy, radius, paint);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        layoutDots(w, h);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateRotation();
        applyPalette(newConfig);
        invalidate();
    }

    private void updateRotation() {
        Display display = getContext().getDisplay();
        if (display != null) {
            engine.setDisplayRotation(display.getRotation());
        }
    }

    private int currentAlpha() {
        float visibility = IDLE_VISIBILITY + (1f - IDLE_VISIBILITY) * state.activity;
        float alpha = Math.min(1f, dotOpacity * visibility / windowAlpha);
        return Math.round(alpha * 255f);
    }

    /** Одноцветные точки под тему системы. Выбор темы в настройках — в отдельной фиче. */
    private void applyPalette(Configuration config) {
        boolean night = (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            dotColor = Color.rgb(0xEB, 0xEB, 0xF5);
            dotOpacity = 0.6f;
        } else {
            dotColor = Color.rgb(0x3C, 0x3C, 0x43);
            dotOpacity = 0.55f;
        }
    }

    /** Две колонки у каждого края, ряды в шахматном порядке. */
    private void layoutDots(int width, int height) {
        float span = height * VERTICAL_SPAN;
        int rows = Math.max(3, Math.round(span / (ROW_SPACING_DP * density)) + 1);
        float rowStep = span / (rows - 1);
        float top = (height - span) / 2f;
        float margin = EDGE_MARGIN_DP * density;
        float gap = COLUMN_GAP_DP * density;

        // Внутренняя колонка смещена на полряда и короче на одну точку
        int perSide = rows + (rows - 1);
        dotX = new float[perSide * 2];
        dotY = new float[perSide * 2];
        int i = 0;
        for (int side = 0; side < 2; side++) {
            float outerX = side == 0 ? margin : width - margin;
            float innerX = side == 0 ? margin + gap : width - margin - gap;
            for (int r = 0; r < rows; r++) {
                dotX[i] = outerX;
                dotY[i++] = top + r * rowStep;
            }
            for (int r = 0; r < rows - 1; r++) {
                dotX[i] = innerX;
                dotY[i++] = top + (r + 0.5f) * rowStep;
            }
        }
        drawnDx = Float.NaN;
    }
}
