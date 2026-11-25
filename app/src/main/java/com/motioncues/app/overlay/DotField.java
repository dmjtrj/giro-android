package com.motioncues.app.overlay;

/**
 * Текущее поле точек, как в Vehicle Motion Cues в iOS (снято по записи экрана iPhone 15).
 *
 * <p>Одна решётка в шахматном порядке на всю ширину экрана течёт вместе со смещением
 * {@code (ox, oy)}. Видны только точки у боковых краёв: полного размера у грани и всё
 * мельче вглубь, до нуля на расстоянии {@code reach}. В покое {@code reach} маленький —
 * у каждого края одна колонка в ~24 dp от грани. В движении он растёт, и изнутри
 * вырастают ещё колонки. Решётка сдвигается целиком, поэтому левый и правый края
 * выглядят по-разному, а крайние точки уходят за грань экрана и обрезаются ею.
 *
 * <p>Шаг колонок подгоняется так, чтобы в покое крайние колонки стояли симметрично
 * и в одних рядах, как на iPhone.
 *
 * <p>Чистая Java без зависимостей от Android; без аллокаций при расчёте кадра.
 */
final class DotField {

    final float columnGap;
    final float rowStep;
    final float width;
    final float height;
    private final float inset;
    private final float fullReach;

    /**
     * @param targetGap желаемый шаг колонок, px
     * @param rowStep   шаг рядов в колонке, px; соседние колонки сдвинуты на полряда
     * @param width     ширина экрана, px
     * @param height    высота экрана, px
     * @param inset     где в покое стоят крайние колонки, px от грани
     * @param fullReach до какого расстояния от грани точки полного размера, px
     */
    DotField(float targetGap, float rowStep, float width, float height, float inset, float fullReach) {
        this.rowStep = rowStep;
        this.width = width;
        this.height = height;
        this.inset = inset;
        this.fullReach = fullReach;
        // Чётное число шагов между крайними колонками — в покое они в одних рядах
        float span = Math.max(targetGap, width - 2f * inset);
        int steps = Math.max(2, 2 * Math.round(span / targetGap / 2f));
        columnGap = span / steps;
    }

    /** Сколько точек максимум может оказаться на экране — размер буферов. */
    int capacity() {
        return ((int) (width / columnGap) + 4) * ((int) (height / rowStep) + 4);
    }

    /** Период решётки по X: две колонки (шахматный порядок повторяется через одну). */
    float periodX() {
        return 2f * columnGap;
    }

    float periodY() {
        return rowStep;
    }

    /**
     * Считает видимые точки для смещения поля {@code (ox, oy)}. Координаты — экранные;
     * точки у грани могут частично выходить за экран, их обрезает холст.
     *
     * @param reach до какого расстояния от грани видны точки, px
     * @param scale размер точки от 0 до 1
     * @return сколько точек записано
     */
    int compute(float ox, float oy, float reach, float margin, float[] x, float[] y, float[] scale) {
        float phaseX = wrap(ox, periodX());
        float phaseY = wrap(oy, periodY());
        float originX = inset + phaseX;
        // В покое ряд чётных колонок проходит через середину экрана
        float originY = height / 2f + phaseY;
        int firstColumn = (int) Math.floor((-margin - originX) / columnGap);
        int lastColumn = (int) Math.ceil((width + margin - originX) / columnGap);
        int firstRow = (int) Math.floor((-margin - originY) / rowStep) - 1;
        int lastRow = (int) Math.ceil((height + margin - originY) / rowStep);
        int count = 0;
        for (int c = firstColumn; c <= lastColumn; c++) {
            float u = originX + c * columnGap;
            if (u < -margin || u > width + margin) {
                continue;
            }
            float sx = reachScale(Math.min(u, width - u), fullReach, reach);
            if (sx <= 0f) {
                continue;
            }
            // Нечётные колонки сдвинуты на полряда — шахматный порядок
            float stagger = (c & 1) == 0 ? 0f : rowStep / 2f;
            for (int r = firstRow; r <= lastRow; r++) {
                float v = originY + r * rowStep + stagger;
                if (v < -margin || v > height + margin) {
                    continue;
                }
                x[count] = u;
                y[count] = v;
                scale[count] = sx;
                count++;
            }
        }
        return count;
    }

    /**
     * Размер точки по расстоянию от грани: 1 до {@code full}, плавно до 0 к {@code reach}.
     * За гранью (отрицательное расстояние) — тоже 1: точку обрезает край экрана.
     */
    static float reachScale(float distance, float full, float reach) {
        if (reach <= full) {
            return distance <= reach ? 1f : 0f;
        }
        return edgeScale(reach - distance, reach - full);
    }

    /** Плавный переход: 0 при {@code distance <= 0}, 1 при {@code distance >= fade}. */
    static float edgeScale(float distance, float fade) {
        float t = Math.max(0f, Math.min(1f, distance / fade));
        return t * t * (3f - 2f * t);
    }

    /** Остаток в [0, period) — смещение не растёт бесконечно и не теряет точность. */
    static float wrap(float value, float period) {
        float r = value % period;
        return r < 0f ? r + period : r;
    }
}
