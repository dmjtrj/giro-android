package com.motioncues.app.motion;

import com.motioncues.app.settings.AppSettings;

/**
 * Градации ползунка «Чувствительность» → множитель реакции на ускорение.
 * Средний уровень — расчётная настройка движка; крайние заметно отличаются,
 * но остаются рабочими: на минимуме точки всё ещё отвечают на торможение,
 * на максимуме не упираются в край от каждой кочки.
 */
public final class Sensitivity {

    private static final float[] FACTOR = {0.6f, 0.8f, 1f, 1.3f, 1.7f};

    private Sensitivity() {
    }

    public static float factor(int level) {
        int clamped = Math.max(AppSettings.MIN_LEVEL, Math.min(AppSettings.MAX_LEVEL, level));
        return FACTOR[clamped - AppSettings.MIN_LEVEL];
    }
}
