package com.motioncues.app.overlay;

import com.motioncues.app.settings.AppSettings;

/**
 * Уровни ползунка «Количество точек» → ширина окна у каждого края (в колонках)
 * и шаг рядов. Окно в 2 колонки — одна полная колонка по центру, как на iPhone;
 * в 3 колонки — полная колонка и половинки по краям, которые перетекают при поворотах.
 */
public final class DotDensity {

    private static final int[] COLUMNS = {2, 2, 3, 3};
    /** Шаг рядов на экране по уровням, dp. Уровень 1 — плотность как на iPhone. */
    private static final float[] ROW_STEP_DP = {120f, 90f, 120f, 96f};

    private DotDensity() {
    }

    public static int columns(int level) {
        return COLUMNS[index(level)];
    }

    public static float rowStepDp(int level) {
        return ROW_STEP_DP[index(level)];
    }

    private static int index(int level) {
        int clamped = Math.max(AppSettings.MIN_COUNT_LEVEL, Math.min(AppSettings.MAX_COUNT_LEVEL, level));
        return clamped - AppSettings.MIN_COUNT_LEVEL;
    }
}
