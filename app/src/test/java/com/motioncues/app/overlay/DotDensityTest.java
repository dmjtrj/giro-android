package com.motioncues.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.motioncues.app.settings.AppSettings;

import org.junit.Test;

public class DotDensityTest {

    @Test
    public void minimum_isSingleColumnLikeIphone() {
        assertEquals(2, DotDensity.columns(AppSettings.MIN_COUNT_LEVEL));
    }

    @Test
    public void maximum_isPreviousDensity() {
        assertEquals(3, DotDensity.columns(AppSettings.MAX_COUNT_LEVEL));
        assertEquals(96f, DotDensity.rowStepDp(AppSettings.MAX_COUNT_LEVEL), 0f);
    }

    @Test
    public void dotCount_growsWithLevel() {
        // Видимых точек на колонку высотой 900 dp: (колонки − 1) полных колонок × ряды
        float previous = 0f;
        for (int level = AppSettings.MIN_COUNT_LEVEL; level <= AppSettings.MAX_COUNT_LEVEL; level++) {
            float dots = (DotDensity.columns(level) - 1) * (900f / DotDensity.rowStepDp(level));
            assertTrue("уровень " + level, dots > previous);
            previous = dots;
        }
    }

    @Test
    public void outOfRange_isClamped() {
        assertEquals(DotDensity.columns(AppSettings.MIN_COUNT_LEVEL), DotDensity.columns(-1));
        assertEquals(DotDensity.rowStepDp(AppSettings.MAX_COUNT_LEVEL), DotDensity.rowStepDp(99), 0f);
    }
}
