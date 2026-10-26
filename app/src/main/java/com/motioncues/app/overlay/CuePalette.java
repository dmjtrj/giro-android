package com.motioncues.app.overlay;

import android.content.Context;
import android.graphics.Color;

import com.motioncues.app.settings.DotColor;
import com.motioncues.app.settings.ThemeController;
import com.motioncues.app.settings.ThemeMode;

/**
 * Цвета точек: одноцветная заливка и тонкая контрастная обводка,
 * чтобы точки читались поверх любого содержимого экрана.
 * Непрозрачность задаёт пользователь ползунком — см. {@link DotStyle}.
 */
final class CuePalette {

    static final int LIGHT_FILL = 0xFF3C3C43;
    static final int DARK_FILL = 0xFFEBEBF5;
    static final int WHITE_FILL = 0xFFFFFFFF;
    static final int BLACK_FILL = 0xFF1C1C1E;

    final int fill;
    final int ring;
    /** Непрозрачность обводки относительно заливки. */
    final float ringStrength;

    CuePalette(int fill, int ring, float ringStrength) {
        this.fill = fill;
        this.ring = ring;
        this.ringStrength = ringStrength;
    }

    static CuePalette resolve(Context context, ThemeMode mode, DotColor color) {
        // Крайние тона акцента: точки в тон обоям, но не сливаются с ними
        return of(mode, color, ThemeController.isSystemNight(context),
                context.getColor(android.R.color.system_accent1_700),
                context.getColor(android.R.color.system_accent1_100),
                context.getColor(android.R.color.system_neutral1_10),
                context.getColor(android.R.color.system_neutral1_900));
    }

    /**
     * @param accentOnLight тон обоев для точек на светлой теме (тёмный)
     * @param accentOnDark  тон обоев для точек на тёмной теме (светлый)
     * @param neutralLight  светлый нейтральный тон из обоев — обводка тёмных точек
     * @param neutralDark   тёмный нейтральный тон из обоев — обводка светлых точек
     */
    static CuePalette of(ThemeMode mode, DotColor color, boolean systemNight,
                         int accentOnLight, int accentOnDark, int neutralLight, int neutralDark) {
        boolean night = mode == ThemeMode.DARK || (mode == ThemeMode.MONET && systemNight);
        switch (color) {
            case WHITE:
                return new CuePalette(WHITE_FILL, Color.BLACK, 0.8f);
            case BLACK:
                return new CuePalette(BLACK_FILL, Color.WHITE, 1f);
            case MONET:
                return monet(night, accentOnLight, accentOnDark, neutralLight, neutralDark);
            default:
                switch (mode) {
                    case LIGHT:
                        return new CuePalette(LIGHT_FILL, Color.WHITE, 1f);
                    case DARK:
                        return new CuePalette(DARK_FILL, Color.BLACK, 0.8f);
                    default:
                        return monet(night, accentOnLight, accentOnDark, neutralLight, neutralDark);
                }
        }
    }

    private static CuePalette monet(boolean night,
                                    int accentOnLight, int accentOnDark, int neutralLight, int neutralDark) {
        return night
                ? new CuePalette(accentOnDark, neutralDark, 0.8f)
                : new CuePalette(accentOnLight, neutralLight, 1f);
    }
}
