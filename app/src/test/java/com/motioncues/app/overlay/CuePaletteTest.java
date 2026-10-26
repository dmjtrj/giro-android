package com.motioncues.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import com.motioncues.app.settings.DotColor;
import com.motioncues.app.settings.ThemeMode;

import org.junit.Test;

public class CuePaletteTest {

    private static final int ACCENT_ON_LIGHT = 0xFF2E5E2E;
    private static final int ACCENT_ON_DARK = 0xFFB4E3A6;
    private static final int NEUTRAL_LIGHT = 0xFFF8FAF0;
    private static final int NEUTRAL_DARK = 0xFF1A1C18;
    private static final boolean[] SYSTEM_NIGHT = {false, true};

    @Test
    public void auto_light_isGraphiteWithWhiteRing_regardlessOfSystem() {
        for (boolean systemNight : SYSTEM_NIGHT) {
            CuePalette p = palette(ThemeMode.LIGHT, DotColor.AUTO, systemNight);
            assertEquals(CuePalette.LIGHT_FILL, p.fill);
            assertEquals(Color.WHITE, p.ring);
        }
    }

    @Test
    public void auto_dark_isLightWithBlackRing_regardlessOfSystem() {
        for (boolean systemNight : SYSTEM_NIGHT) {
            CuePalette p = palette(ThemeMode.DARK, DotColor.AUTO, systemNight);
            assertEquals(CuePalette.DARK_FILL, p.fill);
            assertEquals(Color.BLACK, p.ring);
        }
    }

    @Test
    public void auto_monet_followsSystemAndUsesWallpaperTones() {
        CuePalette day = palette(ThemeMode.MONET, DotColor.AUTO, false);
        assertEquals(ACCENT_ON_LIGHT, day.fill);
        assertEquals(NEUTRAL_LIGHT, day.ring);

        CuePalette night = palette(ThemeMode.MONET, DotColor.AUTO, true);
        assertEquals(ACCENT_ON_DARK, night.fill);
        assertEquals(NEUTRAL_DARK, night.ring);
    }

    @Test
    public void white_and_black_ignoreTheme_andHaveContrastingRing() {
        for (ThemeMode mode : ThemeMode.values()) {
            for (boolean systemNight : SYSTEM_NIGHT) {
                CuePalette white = palette(mode, DotColor.WHITE, systemNight);
                assertEquals(CuePalette.WHITE_FILL, white.fill);
                assertEquals(Color.BLACK, white.ring);

                CuePalette black = palette(mode, DotColor.BLACK, systemNight);
                assertEquals(CuePalette.BLACK_FILL, black.fill);
                assertEquals(Color.WHITE, black.ring);
            }
        }
    }

    @Test
    public void monetColor_inMonetTheme_followsSystem() {
        assertEquals(ACCENT_ON_LIGHT, palette(ThemeMode.MONET, DotColor.MONET, false).fill);
        assertEquals(ACCENT_ON_DARK, palette(ThemeMode.MONET, DotColor.MONET, true).fill);
    }

    @Test
    public void monetColor_outsideMonetTheme_behavesAsAuto() {
        for (ThemeMode mode : new ThemeMode[]{ThemeMode.LIGHT, ThemeMode.DARK}) {
            for (boolean systemNight : SYSTEM_NIGHT) {
                CuePalette monet = palette(mode, DotColor.MONET, systemNight);
                CuePalette auto = palette(mode, DotColor.AUTO, systemNight);
                assertEquals(auto.fill, monet.fill);
                assertEquals(auto.ring, monet.ring);
            }
        }
    }

    @Test
    public void whiteAndBlack_areSolid_soWallpaperDoesNotTintThem() {
        for (ThemeMode mode : ThemeMode.values()) {
            assertTrue(palette(mode, DotColor.WHITE, true).solid);
            assertTrue(palette(mode, DotColor.BLACK, false).solid);
            assertTrue(!palette(mode, DotColor.AUTO, false).solid);
        }
        CuePalette white = palette(ThemeMode.MONET, DotColor.WHITE, true);
        CuePalette auto = palette(ThemeMode.MONET, DotColor.AUTO, true);
        for (int level = 1; level <= 5; level++) {
            // Плотные точки не приглушаются в покое и заметно непрозрачнее обычных
            assertEquals(white.opacity(level, 0f), white.opacity(level, 1f), 0f);
            assertEquals("чистый белый без просвечивания", 1f, white.opacity(level, 0f), 0f);
            assertTrue(white.opacity(level, 0f) > auto.opacity(level, 0f));
        }
    }

    @Test
    public void ring_alwaysDiffersFromFill_andIsNoticeable() {
        for (ThemeMode mode : ThemeMode.values()) {
            for (DotColor color : DotColor.values()) {
                for (boolean systemNight : SYSTEM_NIGHT) {
                    CuePalette p = palette(mode, color, systemNight);
                    assertNotEquals(p.fill, p.ring);
                    assertTrue(p.ringStrength >= 0.5f && p.ringStrength <= 1f);
                }
            }
        }
    }

    private static CuePalette palette(ThemeMode mode, DotColor color, boolean systemNight) {
        return CuePalette.of(mode, color, systemNight, ACCENT_ON_LIGHT, ACCENT_ON_DARK, NEUTRAL_LIGHT, NEUTRAL_DARK);
    }
}
