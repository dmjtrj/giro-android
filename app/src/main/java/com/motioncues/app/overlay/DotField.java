package com.motioncues.app.overlay;

/**
 * Текущее поле точек, как в Vehicle Motion Cues в iOS.
 *
 * <p>Бесконечная решётка точек в шахматном порядке течёт вместе со смещением
 * {@code (ox, oy)}. Каждая сторона экрана — окно в эту решётку. Точка, подходящая
 * к границе окна, плавно уменьшается до нуля, а с противоположной стороны в это
 * же время вырастает новая — ровно настолько, насколько уменьшилась первая.
 * Поэтому при долгом разгоне или повороте точки непрерывно «текут», а не упираются в край.
 *
 * <p>Зона уменьшения — один шаг решётки, а окно — целое число шагов. Тогда размеры
 * уходящей и входящей точек в сумме всегда дают одну полную точку, и общая «масса»
 * точек в окне не меняется при любом движении.
 *
 * <p>Раскладка симметрична относительно центра окна: в покое по центру стоит полная
 * колонка (и полный ряд), к краям точки мягко уменьшаются. При окне в 2 колонки это
 * одна полная колонка точно по центру, при окне в 3 — полная в середине и половинки по краям.
 *
 * <p>Чистая Java без зависимостей от Android; без аллокаций при расчёте кадра.
 */
final class DotField {

    final float columnGap;
    final float rowStep;
    final float windowWidth;
    final float windowHeight;
    private final int columns;
    private final int rows;

    /**
     * @param columnGap расстояние между колонками, px
     * @param rowStep   расстояние между рядами в колонке, px
     * @param columns   ширина окна в колонках
     * @param rows      высота окна в рядах
     */
    DotField(float columnGap, float rowStep, int columns, int rows) {
        this.columnGap = columnGap;
        this.rowStep = rowStep;
        this.columns = columns;
        this.rows = rows;
        windowWidth = columns * columnGap;
        windowHeight = rows * rowStep;
    }

    /** Сколько точек максимум может оказаться в окне — размер буферов. */
    int capacity() {
        return (columns + 4) * (rows + 4);
    }

    /**
     * Общая «масса» точек (сумма размеров) — одна и та же при любом смещении поля.
     * Две крайние частичные колонки вместе дают одну полную, так же и ряды.
     */
    float mass() {
        return (columns - 1) * (rows - 1);
    }

    /** Период решётки по X: две колонки (шахматный порядок повторяется через одну). */
    float periodX() {
        return 2f * columnGap;
    }

    float periodY() {
        return rowStep;
    }

    /**
     * Считает точки, видимые в окне, для смещения поля {@code (ox, oy)}.
     * Координаты — относительно левого верхнего угла окна.
     *
     * @param scale размер точки от 0 (у границы) до 1
     * @return сколько точек записано
     */
    int compute(float ox, float oy, float[] x, float[] y, float[] scale) {
        float phaseX = wrap(ox, periodX());
        float phaseY = wrap(oy, periodY());
        // Зона уменьшения — ровно шаг: уходящая и входящая точки дополняют друг друга
        float fadeX = columnGap;
        float fadeY = rowStep;
        int count = 0;
        // Отсчёт от центра окна: в покое колонка и ряд по центру — полного размера.
        // Идём с запасом в чётное число шагов влево/вверх (чётное — чтобы центральная
        // колонка не была сдвинута на полряда), фаза сдвигает поле в пределах периода.
        int marginX = 2 * ((int) Math.ceil(windowWidth / columnGap / 2f) + 1);
        int marginY = (int) Math.ceil(windowHeight / rowStep / 2f) + 2;
        float baseX = windowWidth / 2f - marginX * columnGap + phaseX;
        float baseY = windowHeight / 2f - marginY * rowStep + phaseY;
        for (int c = 0; c <= 2 * marginX + 2; c++) {
            float u = baseX + c * columnGap;
            if (u < 0f || u > windowWidth) {
                continue;
            }
            float sx = edgeScale(Math.min(u, windowWidth - u), fadeX);
            // Нечётные колонки сдвинуты на полряда — шахматный порядок
            float stagger = (c & 1) == 0 ? 0f : rowStep / 2f;
            for (int r = 0; r <= 2 * marginY + 2; r++) {
                float v = baseY + r * rowStep + stagger;
                if (v < 0f || v > windowHeight) {
                    continue;
                }
                float s = sx * edgeScale(Math.min(v, windowHeight - v), fadeY);
                if (s <= 0f) {
                    continue;
                }
                x[count] = u;
                y[count] = v;
                scale[count] = s;
                count++;
            }
        }
        return count;
    }

    /**
     * Плавное уменьшение у границы: 0 на границе, 1 через шаг и дальше.
     * Симметрично: {@code edgeScale(d) + edgeScale(fade - d) == 1}.
     */
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
