package com.motioncues.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.motioncues.app.settings.AppSettings;

import org.junit.Test;

public class DotDensityTest {

    @Test
    public void minimum_isLikeIphone() {
        // Запись экрана iPhone 15: в покое одна колонка у края, ряды через ~234 pt
        assertEquals(234f, DotDensity.rowStepDp(AppSettings.MIN_COUNT_LEVEL), 0f);
        float rest = DotDensity.reachDp(AppSettings.MIN_COUNT_LEVEL, 0f);
        assertTrue("видна только крайняя колонка", rest > DotDensity.EDGE_INSET_DP
                && rest < DotDensity.EDGE_INSET_DP + DotDensity.COLUMN_GAP_DP);
    }

    @Test
    public void reach_growsWithActivity() {
        float previous = DotDensity.reachDp(AppSettings.MIN_COUNT_LEVEL, 0f);
        for (float a = 0.25f; a <= 1f; a += 0.25f) {
            float reach = DotDensity.reachDp(AppSettings.MIN_COUNT_LEVEL, a);
            assertTrue(reach > previous);
            previous = reach;
        }
        assertEquals(DotDensity.MOVING_REACH_DP, DotDensity.reachDp(AppSettings.MIN_COUNT_LEVEL, 1f), 0f);
        assertEquals(DotDensity.MOVING_REACH_DP, DotDensity.reachDp(AppSettings.MIN_COUNT_LEVEL, 5f), 0f);
    }

    @Test
    public void dotCount_growsWithLevel() {
        // Видимых точек у края высотой 900 dp в покое: колонки в полосе × ряды
        float previous = 0f;
        for (int level = AppSettings.MIN_COUNT_LEVEL; level <= AppSettings.MAX_COUNT_LEVEL; level++) {
            float reach = DotDensity.reachDp(level, 0f);
            int columns = 1 + (int) ((reach - DotDensity.EDGE_INSET_DP) / DotDensity.COLUMN_GAP_DP);
            float dots = columns * (900f / DotDensity.rowStepDp(level));
            assertTrue("уровень " + level, dots > previous);
            previous = dots;
        }
    }

    @Test
    public void outOfRange_isClamped() {
        assertEquals(DotDensity.rowStepDp(AppSettings.MIN_COUNT_LEVEL), DotDensity.rowStepDp(-1), 0f);
        assertEquals(DotDensity.rowStepDp(AppSettings.MAX_COUNT_LEVEL), DotDensity.rowStepDp(99), 0f);
    }
}
