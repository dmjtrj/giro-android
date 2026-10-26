package com.motioncues.app.overlay;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Рисует текущее поле точек в двух окнах — у левого и правого края.
 * Общий для оверлея и превью. Буферы выделяются один раз — в кадре аллокаций нет.
 */
final class DotRenderer {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float ringWidth;

    private DotField field;
    private float[] xs = new float[0];
    private float[] ys = new float[0];
    private float[] scales = new float[0];

    private float leftWindowX;
    private float rightWindowX;
    private float windowY;

    DotRenderer(float ringWidthPx) {
        ringWidth = ringWidthPx;
        fillPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(ringWidthPx);
    }

    /**
     * Раскладывает окна: по одному у каждого края, по центру по высоте.
     *
     * @param edgePad отступ окна от края экрана, px
     */
    void layout(int width, int height, DotField field, float edgePad) {
        this.field = field;
        int capacity = field.capacity();
        if (xs.length < capacity) {
            xs = new float[capacity];
            ys = new float[capacity];
            scales = new float[capacity];
        }
        leftWindowX = edgePad;
        rightWindowX = width - edgePad - field.windowWidth;
        windowY = (height - field.windowHeight) / 2f;
    }

    boolean isLaidOut() {
        return field != null;
    }

    /**
     * @param ox     смещение поля по X, px (знак — куда текут точки)
     * @param oy     смещение поля по Y, px
     * @param radius радиус точки полного размера, px
     * @param alpha  непрозрачность заливки 0..255
     */
    void draw(Canvas canvas, float ox, float oy, float radius, int alpha, CuePalette palette) {
        if (field == null || alpha <= 0) {
            return;
        }
        fillPaint.setColor(palette.fill);
        fillPaint.setAlpha(alpha);
        ringPaint.setColor(palette.ring);
        ringPaint.setAlpha(Math.round(alpha * palette.ringStrength));

        // Оба окна смотрят в одно и то же поле — точки по краям текут синхронно
        int count = field.compute(ox, oy, xs, ys, scales);
        for (int i = 0; i < count; i++) {
            float r = radius * scales[i];
            drawDot(canvas, leftWindowX + xs[i], windowY + ys[i], r);
            drawDot(canvas, rightWindowX + xs[i], windowY + ys[i], r);
        }
    }

    private void drawDot(Canvas canvas, float x, float y, float r) {
        canvas.drawCircle(x, y, r, fillPaint);
        // Обводка снаружи заливки; у совсем маленьких точек её не рисуем — была бы кляксой
        if (r > ringWidth) {
            canvas.drawCircle(x, y, r + ringWidth / 2f, ringPaint);
        }
    }
}
