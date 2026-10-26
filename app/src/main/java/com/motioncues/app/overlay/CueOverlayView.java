package com.motioncues.app.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Choreographer;
import android.view.Display;
import android.view.View;

import com.motioncues.app.motion.CueState;
import com.motioncues.app.motion.MotionEngine;
import com.motioncues.app.settings.DotColor;
import com.motioncues.app.settings.ThemeMode;

/**
 * Точки-сигналы по левому и правому краю экрана.
 *
 * <p>На каждом кадре шагает физику движка и сдвигает все точки вместе.
 * Когда точки стоят на месте, кадр не перерисовывается — GPU простаивает.
 */
@SuppressLint("ViewConstructor") // создаётся только из кода, не из XML
final class CueOverlayView extends View implements Choreographer.FrameCallback {

    /** Видимость точек в покое относительно выбранной непрозрачности: заметны, но спокойнее. */
    private static final float IDLE_VISIBILITY = 0.8f;
    /** Порог, ниже которого кадр считается неизменным, px. */
    private static final float REDRAW_EPSILON_PX = 0.05f;

    private static final float RING_WIDTH_DP = 1.5f;
    /** Максимальный ход точек. */
    private static final float MAX_TRAVEL_DP = 18f;
    /** Отступ от края: ход + самый крупный радиус + обводка — точки не уходят за экран. */
    private static final float EDGE_MARGIN_DP = MAX_TRAVEL_DP + DotStyle.MAX_RADIUS_DP + RING_WIDTH_DP + 1f;
    private static final float COLUMN_GAP_DP = 18f;
    /** Точек во внешней колонке; во внутренней на одну меньше — всего 7 на сторону. */
    private static final int OUTER_ROWS = 4;
    /** Доля высоты экрана, которую занимают точки, — по центру. */
    private static final float VERTICAL_SPAN = 0.6f;

    private final MotionEngine engine;
    private final CueState state = new CueState();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final float windowAlpha;

    private float[] dotX = new float[0];
    private float[] dotY = new float[0];
    private ThemeMode themeMode;
    private DotColor dotColor;
    private CuePalette palette;
    private float radiusDp;
    private float opacity;

    private boolean running;
    private float drawnDx = Float.NaN, drawnDy = Float.NaN;
    private int drawnAlpha = -1;

    /**
     * @param windowAlpha прозрачность окна-оверлея: учитываем, чтобы итоговая
     *                    яркость точек совпадала с задуманной
     */
    CueOverlayView(Context context, MotionEngine engine, float windowAlpha,
                   ThemeMode themeMode, DotColor dotColor, int sizeLevel, int opacityLevel) {
        super(context);
        this.engine = engine;
        this.windowAlpha = windowAlpha;
        density = getResources().getDisplayMetrics().density;
        fillPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(RING_WIDTH_DP * density);
        setDotStyle(sizeLevel, opacityLevel);
        setColors(themeMode, dotColor);
    }

    /** Размер и непрозрачность с ползунков — применяются на лету. */
    void setDotStyle(int sizeLevel, int opacityLevel) {
        radiusDp = DotStyle.radiusDp(sizeLevel);
        opacity = DotStyle.opacity(opacityLevel);
        drawnAlpha = -1;
        invalidate();
    }

    /** Смена оформления или цвета на лету — точки перекрашиваются без перезапуска эффекта. */
    void setColors(ThemeMode mode, DotColor color) {
        themeMode = mode;
        dotColor = color;
        palette = CuePalette.resolve(getContext(), mode, color);
        drawnAlpha = -1;
        invalidate();
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

        fillPaint.setColor(palette.fill);
        fillPaint.setAlpha(drawnAlpha);
        ringPaint.setColor(palette.ring);
        ringPaint.setAlpha(Math.round(drawnAlpha * palette.ringStrength));
        float radius = radiusDp * density;
        // Обводка снаружи заливки: заливка остаётся того же размера и цвета
        float ringRadius = radius + ringPaint.getStrokeWidth() / 2f;
        for (int i = 0; i < dotX.length; i++) {
            float x = dotX[i] + drawnDx;
            float y = dotY[i] + drawnDy;
            canvas.drawCircle(x, y, radius, fillPaint);
            canvas.drawCircle(x, y, ringRadius, ringPaint);
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
        // Сменилась системная тема или обои — для Monet пересчитываем цвета
        setColors(themeMode, dotColor);
    }

    private void updateRotation() {
        Display display = getContext().getDisplay();
        if (display != null) {
            engine.setDisplayRotation(display.getRotation());
        }
    }

    /** Прозрачность заливки с учётом активности и прозрачности окна. */
    private int currentAlpha() {
        float visibility = IDLE_VISIBILITY + (1f - IDLE_VISIBILITY) * state.activity;
        return toAlpha(opacity * visibility / windowAlpha);
    }

    private static int toAlpha(float opacity) {
        return Math.round(Math.max(0f, Math.min(1f, opacity)) * 255f);
    }

    /** Две колонки у каждого края, ряды в шахматном порядке. */
    private void layoutDots(int width, int height) {
        float span = height * VERTICAL_SPAN;
        int rows = OUTER_ROWS;
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
