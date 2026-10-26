package com.motioncues.app.overlay;

import com.motioncues.app.settings.AppSettings;

/**
 * Градации ползунков «Размер» и «Непрозрачность» → параметры отрисовки точек.
 * Уровни от {@link AppSettings#MIN_LEVEL} до {@link AppSettings#MAX_LEVEL}.
 */
public final class DotStyle {

    /**
     * Радиус точки по уровням, dp. Каждый уровень рабочий: даже самые мелкие
     * точки хорошо видны, самые крупные — заметны, но не навязчивы.
     */
    private static final float[] RADIUS_DP = {4f, 4.75f, 5.5f, 6.5f, 7.5f};
    /** Самый крупный радиус — по нему считается отступ от края экрана. */
    public static final float MAX_RADIUS_DP = 7.5f;
    /**
     * Непрозрачность заливки при движении по уровням. Даже нижний уровень
     * заметен на любом фоне; верхний 0.8 — системный порог для окон поверх
     * приложений, выше Android блокирует касания.
     */
    private static final float[] OPACITY = {0.5f, 0.58f, 0.66f, 0.73f, 0.8f};

    private DotStyle() {
    }

    public static float radiusDp(int level) {
        return RADIUS_DP[index(level)];
    }

    public static float opacity(int level) {
        return OPACITY[index(level)];
    }

    /**
     * Белые и чёрные точки всегда полностью непрозрачные — чистый цвет без просвечивания
     * фона, как на iPhone. Ползунок непрозрачности на них не действует.
     * В обычном оверлее система всё равно ограничит окно до 80%; полные 100% —
     * в режиме «Поверх шторки» (окно службы специальных возможностей).
     */
    public static float solidOpacity() {
        return 1f;
    }

    private static int index(int level) {
        int clamped = Math.max(AppSettings.MIN_LEVEL, Math.min(AppSettings.MAX_LEVEL, level));
        return clamped - AppSettings.MIN_LEVEL;
    }
}
