package com.motioncues.app.overlay;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Рисует текущее поле точек у левого и правого края экрана.
 * Общий для оверлея и превью. Буферы выделяются один раз — в кадре аллокаций нет.
 */
final class DotRenderer {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float ringWidth;
    /**
     * Точки мельче не рисуем (~1 dp при обводке 1.5 dp): от шума датчиков в покое внутренняя
     * колонка иначе проступает пылью, а на iPhone самые мелкие видимые точки ~2.4 pt.
     */
    private final float minRadius;

    private DotField field;
    private float[] xs = new float[0];
    private float[] ys = new float[0];
    private float[] scales = new float[0];

    DotRenderer(float ringWidthPx) {
        ringWidth = ringWidthPx;
        minRadius = ringWidthPx * 2f / 3f;
        fillPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(ringWidthPx);
    }

    void layout(DotField field) {
        this.field = field;
        int capacity = field.capacity();
        if (xs.length < capacity) {
            xs = new float[capacity];
            ys = new float[capacity];
            scales = new float[capacity];
        }
    }

    boolean isLaidOut() {
        return field != null;
    }

    /**
     * @param ox     смещение поля по X, px (знак — куда текут точки)
     * @param oy     смещение поля по Y, px
     * @param reach  до какого расстояния от грани видны точки, px
     * @param radius радиус точки полного размера, px
     * @param alpha  непрозрачность заливки 0..255
     */
    void draw(Canvas canvas, float ox, float oy, float reach, float radius, int alpha, CuePalette palette) {
        if (field == null || alpha <= 0) {
            return;
        }
        fillPaint.setColor(palette.fill);
        fillPaint.setAlpha(alpha);
        ringPaint.setColor(palette.ring);
        ringPaint.setAlpha(Math.round(alpha * palette.ringStrength));

        // Запас — чтобы точки у грани рисовались частично, а не исчезали целиком
        int count = field.compute(ox, oy, reach, radius + ringWidth, xs, ys, scales);
        for (int i = 0; i < count; i++) {
            float r = radius * scales[i];
            if (r >= minRadius) {
                drawDot(canvas, xs[i], ys[i], r);
            }
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
