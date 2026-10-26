package com.motioncues.app.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CueProfileTest {

    @Test
    public void iphone_dotsAreOppositeToTheme() {
        assertEquals(DotColor.WHITE, CueProfile.iphone(true).dotColor);
        assertEquals(DotColor.BLACK, CueProfile.iphone(false).dotColor);
    }

    @Test
    public void iphone_usesInertiaDefaultsAndIphoneDensity() {
        CueProfile p = CueProfile.iphone(true);
        assertTrue(p.iphone);
        assertEquals(MotionMode.INERTIA, p.motionMode);
        assertEquals(AppSettings.DEFAULT_LEVEL, p.sensitivity);
        assertEquals(AppSettings.MIN_COUNT_LEVEL, p.dotCount);
        assertEquals(AppSettings.DEFAULT_LEVEL, p.dotSize);
    }
}
