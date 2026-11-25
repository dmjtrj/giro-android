package com.motioncues.app.overlay;

import com.motioncues.app.settings.AppSettings;

/**
 * Геометрия поля точек и уровни ползунка «Количество точек».
 *
 * <p>Числа сняты с записи экрана iPhone 15 (в pt, на Android берём столько же dp):
 * шаг колонок ~43, шаг рядов в колонке ~234, в покое у каждого края одна колонка
 * в ~24 от грани. В движении видимая полоса растёт до ~170 от грани.
 * Уровень 1 — как на iPhone; дальше ряды чаще, а в покое видна и вторая колонка.
 */
public final class DotDensity {

    /** Шаг колонок, dp. */
    public static final float COLUMN_GAP_DP = 43f;
    /** Где в покое стоят крайние колонки, dp от грани. */
    public static final float EDGE_INSET_DP = 24f;
    /** До какого расстояния от грани точки полного размера, dp. */
    public static final float FULL_REACH_DP = 30f;
    /** Видимая полоса при полном движении, dp от грани. */
    public static final float MOVING_REACH_DP = 170f;

    /** Шаг рядов в колонке по уровням, dp. */
    private static final float[] ROW_STEP_DP = {234f, 180f, 150f, 120f};
    /** Видимая полоса в покое по уровням, dp: 50 — одна колонка, 95 — две. */
    private static final float[] REST_REACH_DP = {50f, 50f, 95f, 95f};

    private DotDensity() {
    }

    public static float rowStepDp(int level) {
        return ROW_STEP_DP[index(level)];
    }

    /**
     * Видимая полоса точек от грани, dp.
     *
     * @param activity сила движения 0..1: в покое полоса узкая, в движении изнутри вырастают колонки
     */
    public static float reachDp(int level, float activity) {
        float rest = REST_REACH_DP[index(level)];
        float a = Math.max(0f, Math.min(1f, activity));
        return rest + (MOVING_REACH_DP - rest) * a;
    }

    private static int index(int level) {
        int clamped = Math.max(AppSettings.MIN_COUNT_LEVEL, Math.min(AppSettings.MAX_COUNT_LEVEL, level));
        return clamped - AppSettings.MIN_COUNT_LEVEL;
    }
}
