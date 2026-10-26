package com.motioncues.app.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.motioncues.app.settings.AppSettings;

import org.junit.Test;

public class SensitivityTest {

    @Test
    public void defaultLevel_keepsEngineTuning() {
        assertEquals(1f, Sensitivity.factor(AppSettings.DEFAULT_LEVEL), 0f);
    }

    @Test
    public void levels_growStrictly_andStayUsable() {
        for (int level = AppSettings.MIN_LEVEL + 1; level <= AppSettings.MAX_LEVEL; level++) {
            assertTrue(Sensitivity.factor(level) > Sensitivity.factor(level - 1));
        }
        assertTrue(Sensitivity.factor(AppSettings.MIN_LEVEL) >= 0.5f);
        assertTrue(Sensitivity.factor(AppSettings.MAX_LEVEL) <= 2f);
    }

    @Test
    public void outOfRange_isClamped() {
        assertEquals(Sensitivity.factor(AppSettings.MIN_LEVEL), Sensitivity.factor(0), 0f);
        assertEquals(Sensitivity.factor(AppSettings.MAX_LEVEL), Sensitivity.factor(9), 0f);
    }
}
