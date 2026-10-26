package com.motioncues.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.motioncues.app.settings.AppSettings;

import org.junit.Test;

public class DotStyleTest {

    /** Системный порог прозрачности окон поверх приложений. */
    private static final float OVERLAY_TOUCH_THRESHOLD = 0.8f;

    @Test
    public void everyLevel_isUsable() {
        for (int level = AppSettings.MIN_LEVEL; level <= AppSettings.MAX_LEVEL; level++) {
            assertTrue("точки видны на любом уровне", DotStyle.radiusDp(level) >= 4f);
            assertTrue("не выше системного порога", DotStyle.opacity(level) <= OVERLAY_TOUCH_THRESHOLD);
            assertTrue("заметны даже на минимуме", DotStyle.opacity(level) >= 0.5f);
        }
    }

    @Test
    public void levels_growStrictly() {
        for (int level = AppSettings.MIN_LEVEL + 1; level <= AppSettings.MAX_LEVEL; level++) {
            assertTrue(DotStyle.radiusDp(level) > DotStyle.radiusDp(level - 1));
            assertTrue(DotStyle.opacity(level) > DotStyle.opacity(level - 1));
        }
    }

    @Test
    public void maxRadius_matchesTopLevel() {
        assertEquals(DotStyle.MAX_RADIUS_DP, DotStyle.radiusDp(AppSettings.MAX_LEVEL), 0f);
    }

    @Test
    public void outOfRangeLevels_areClamped() {
        assertEquals(DotStyle.radiusDp(AppSettings.MIN_LEVEL), DotStyle.radiusDp(-3), 0f);
        assertEquals(DotStyle.opacity(AppSettings.MAX_LEVEL), DotStyle.opacity(42), 0f);
    }
}
