package com.motioncues.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class DotFieldTest {

    private static final float GAP = 58f;
    private static final float ROW = 252f;
    private static final int COLUMNS = 3;
    private static final int ROWS = 5;

    private final DotField field = new DotField(GAP, ROW, COLUMNS, ROWS);
    private final float[] x = new float[field.capacity()];
    private final float[] y = new float[field.capacity()];
    private final float[] s = new float[field.capacity()];

    @Test
    public void atRest_middleIsFull_andColumnsTaperAtEnds() {
        int n = field.compute(0f, 0f, x, y, s);
        float max = 0f, min = 1f;
        for (int i = 0; i < n; i++) {
            max = Math.max(max, s[i]);
            min = Math.min(min, s[i]);
        }
        assertEquals(1f, max, 1e-4f);
        assertTrue("крайние точки меньше: " + min, min < 0.6f);
    }

    @Test
    public void singleColumnWindow_atRest_hasOneFullCenteredColumn() {
        DotField single = new DotField(GAP, ROW, 2, ROWS);
        int n = single.compute(0f, 0f, x, y, s);
        assertTrue(n > 0);
        for (int i = 0; i < n; i++) {
            assertEquals("ровно по центру окна", single.windowWidth / 2f, x[i], 1e-3f);
        }
        assertEquals(single.mass(), sum(n), 1e-3f);
    }

    @Test
    public void totalMass_isConstant_whileFlowing() {
        // Сколько точка потеряла у одной границы, столько выросло у другой
        for (float ox = -300f; ox <= 300f; ox += 7.3f) {
            for (float oy = -600f; oy <= 600f; oy += 31.7f) {
                int n = field.compute(ox, oy, x, y, s);
                float sum = 0f;
                for (int i = 0; i < n; i++) {
                    sum += s[i];
                }
                assertEquals("ox=" + ox + " oy=" + oy, field.mass(), sum, 1e-3f);
            }
        }
    }

    @Test
    public void flowingDown_dotShrinksAtBottom_whileNewOneGrowsAtTop() {
        float before = edgeRowsScale(0f, ROW / 8f);
        float after = edgeRowsScale(0f, ROW / 4f);
        // Сдвиг вниз: верхняя (новая) растёт, нижняя уменьшается, в сумме — одна точка
        float[] top0 = topBottom(ROW / 8f);
        float[] top1 = topBottom(ROW / 4f);
        assertTrue("сверху растёт: " + top0[0] + " → " + top1[0], top1[0] > top0[0]);
        assertTrue("снизу уменьшается: " + top0[1] + " → " + top1[1], top1[1] < top0[1]);
        assertEquals(1f, before, 1e-3f);
        assertEquals(1f, after, 1e-3f);
    }

    @Test
    public void dotsStayInsideWindow() {
        for (float ox = -300f; ox <= 300f; ox += 13.1f) {
            for (float oy = -600f; oy <= 600f; oy += 41.3f) {
                int n = field.compute(ox, oy, x, y, s);
                assertTrue(n <= field.capacity());
                for (int i = 0; i < n; i++) {
                    assertTrue(x[i] >= 0f && x[i] <= field.windowWidth);
                    assertTrue(y[i] >= 0f && y[i] <= field.windowHeight);
                    assertTrue(s[i] > 0f && s[i] <= 1f);
                }
            }
        }
    }

    @Test
    public void field_isPeriodic() {
        float ox = 13f, oy = 77f;
        String a = snapshot(ox, oy);
        assertEquals(a, snapshot(ox + field.periodX(), oy));
        assertEquals(a, snapshot(ox, oy - field.periodY()));
    }

    @Test
    public void edgeScale_isSmoothAndSymmetric() {
        assertEquals(0f, DotField.edgeScale(0f, 10f), 0f);
        assertEquals(1f, DotField.edgeScale(10f, 10f), 0f);
        assertEquals(1f, DotField.edgeScale(50f, 10f), 0f);
        for (float d = 0f; d <= 10f; d += 0.5f) {
            assertEquals(1f, DotField.edgeScale(d, 10f) + DotField.edgeScale(10f - d, 10f), 1e-5f);
        }
    }

    @Test
    public void wrap_handlesNegative() {
        assertEquals(3f, DotField.wrap(-7f, 10f), 1e-6f);
        assertEquals(3f, DotField.wrap(13f, 10f), 1e-6f);
    }

    private float sum(int n) {
        float total = 0f;
        for (int i = 0; i < n; i++) {
            total += s[i];
        }
        return total;
    }

    /** Размеры верхней и нижней точек средней (полной) колонки. */
    private float[] topBottom(float oy) {
        int n = field.compute(0f, oy, x, y, s);
        float middle = 1.5f * GAP;
        float top = Float.MAX_VALUE, topScale = 0f, bottom = -Float.MAX_VALUE, bottomScale = 0f;
        for (int i = 0; i < n; i++) {
            if (Math.abs(x[i] - middle) > 1e-3f) {
                continue;
            }
            if (y[i] < top) {
                top = y[i];
                topScale = s[i];
            }
            if (y[i] > bottom) {
                bottom = y[i];
                bottomScale = s[i];
            }
        }
        return new float[]{topScale, bottomScale};
    }

    private float edgeRowsScale(float ox, float oy) {
        float[] tb = topBottom(oy);
        return tb[0] + tb[1];
    }

    private String snapshot(float ox, float oy) {
        int n = field.compute(ox, oy, x, y, s);
        float[][] dots = new float[n][];
        for (int i = 0; i < n; i++) {
            dots[i] = new float[]{Math.round(x[i] * 10f) / 10f, Math.round(y[i] * 10f) / 10f,
                    Math.round(s[i] * 1000f) / 1000f};
        }
        Arrays.sort(dots, (p, q) -> p[0] != q[0] ? Float.compare(p[0], q[0]) : Float.compare(p[1], q[1]));
        return Arrays.deepToString(dots);
    }
}
