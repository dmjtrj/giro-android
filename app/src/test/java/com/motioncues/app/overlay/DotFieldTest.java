package com.motioncues.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class DotFieldTest {

    // Экран 412 × 915 dp при плотности 1 — Pixel 8
    private static final float W = 412f;
    private static final float H = 915f;
    private static final float GAP = 43f;
    private static final float ROW = 234f;
    private static final float INSET = 24f;
    private static final float FULL = 30f;
    private static final float REST_REACH = 50f;
    private static final float MOVING_REACH = 170f;
    private static final float MARGIN = 8f;

    private final DotField field = new DotField(GAP, ROW, W, H, INSET, FULL);
    private final float[] x = new float[field.capacity()];
    private final float[] y = new float[field.capacity()];
    private final float[] s = new float[field.capacity()];

    @Test
    public void atRest_oneColumnAtEachEdge_symmetricAndInSameRows() {
        int n = field.compute(0f, 0f, REST_REACH, MARGIN, x, y, s);
        float[] left = new float[n], right = new float[n];
        int l = 0, r = 0;
        for (int i = 0; i < n; i++) {
            if (x[i] < W / 2f) {
                assertEquals("левая колонка в 24 от грани", INSET, x[i], 1e-3f);
                left[l++] = y[i];
            } else {
                assertEquals("правая колонка в 24 от грани", W - INSET, x[i], 1e-3f);
                right[r++] = y[i];
            }
            assertEquals("в покое точки полного размера", 1f, s[i], 1e-5f);
        }
        assertTrue("по 3–4 точки у края, как на iPhone: " + l, l >= 3 && l <= 4);
        assertEquals(l, r);
        Arrays.sort(left, 0, l);
        Arrays.sort(right, 0, r);
        for (int i = 0; i < l; i++) {
            assertEquals("ряды слева и справа совпадают", left[i], right[i], 1e-3f);
        }
    }

    @Test
    public void columnGap_fitsEvenNumberOfStepsBetweenEdgeColumns() {
        float steps = (W - 2f * INSET) / field.columnGap;
        assertEquals(Math.round(steps), steps, 1e-4f);
        assertEquals(0, Math.round(steps) % 2);
        assertEquals("шаг близок к заданному", GAP, field.columnGap, GAP * 0.1f);
    }

    @Test
    public void moving_innerColumnsGrow_andShrinkInward() {
        int n = field.compute(0f, 0f, MOVING_REACH, MARGIN, x, y, s);
        int columns = 0;
        float previousScale = 2f;
        for (float u = INSET; u < W / 2f; u += field.columnGap) {
            float scale = scaleAt(n, u);
            if (scale > 0f) {
                columns++;
                assertTrue("вглубь точки мельче: " + scale, scale <= previousScale + 1e-5f);
                previousScale = scale;
            }
        }
        assertTrue("в движении у края 3–4 колонки: " + columns, columns >= 3 && columns <= 4);
    }

    @Test
    public void sideways_fieldShifts_edgeDotsLeaveScreen_andSidesDiffer() {
        // Решётка сдвигается целиком: слева колонка уходит за грань, справа — вглубь
        float shift = -30f;
        int n = field.compute(shift, 0f, REST_REACH, MARGIN, x, y, s);
        boolean clippedLeft = false;
        float nearestRight = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (x[i] < 0f) {
                clippedLeft = true;
            }
            if (x[i] > W / 2f) {
                nearestRight = Math.min(nearestRight, W - x[i]);
            }
        }
        assertTrue("слева точки у самой грани, частично за ней", clippedLeft);
        assertNotEquals("края несимметричны", INSET + shift, -nearestRight, 1f);
    }

    @Test
    public void reachScale_fullAtEdge_zeroBeyondReach() {
        assertEquals(1f, DotField.reachScale(-5f, FULL, MOVING_REACH), 0f);
        assertEquals(1f, DotField.reachScale(FULL, FULL, MOVING_REACH), 0f);
        assertEquals(0f, DotField.reachScale(MOVING_REACH, FULL, MOVING_REACH), 0f);
        float mid = DotField.reachScale(100f, FULL, MOVING_REACH);
        assertTrue("на 100 dp точка около половины, как на iPhone: " + mid, mid > 0.35f && mid < 0.65f);
    }

    @Test
    public void dotsStayNearScreen() {
        for (float ox = -300f; ox <= 300f; ox += 13.1f) {
            for (float oy = -600f; oy <= 600f; oy += 41.3f) {
                int n = field.compute(ox, oy, MOVING_REACH, MARGIN, x, y, s);
                assertTrue(n <= field.capacity());
                for (int i = 0; i < n; i++) {
                    assertTrue(x[i] >= -MARGIN && x[i] <= W + MARGIN);
                    assertTrue(y[i] >= -MARGIN && y[i] <= H + MARGIN);
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

    /** Размер точек колонки с центром в {@code u}; 0 — колонки не видно. */
    private float scaleAt(int n, float u) {
        for (int i = 0; i < n; i++) {
            if (Math.abs(x[i] - u) < 1e-2f) {
                return s[i];
            }
        }
        return 0f;
    }

    private String snapshot(float ox, float oy) {
        int n = field.compute(ox, oy, MOVING_REACH, MARGIN, x, y, s);
        float[][] dots = new float[n][];
        for (int i = 0; i < n; i++) {
            dots[i] = new float[]{Math.round(x[i] * 10f) / 10f, Math.round(y[i] * 10f) / 10f,
                    Math.round(s[i] * 1000f) / 1000f};
        }
        Arrays.sort(dots, (p, q) -> p[0] != q[0] ? Float.compare(p[0], q[0]) : Float.compare(p[1], q[1]));
        return Arrays.deepToString(dots);
    }
}
