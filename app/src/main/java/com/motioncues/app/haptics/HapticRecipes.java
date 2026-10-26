package com.motioncues.app.haptics;

import android.os.VibrationEffect;

/**
 * Рецепты вибрации без зависимостей от железа — чтобы их можно было проверить тестами.
 *
 * <p>Основной путь — композиции из примитивов вибромотора. Подобраны под длительности
 * примитивов Pixel: CLICK 26 мс, TICK 5 мс, LOW_TICK 12 мс, SPIN 133 мс,
 * THUD 300 мс (только на самом крупном размере точек — там нужен удар «всем корпусом»).
 * SLOW_RISE (500 мс) не используется.
 */
final class HapticRecipes {

    /** Шаг композиции: примитив, сила 0..1 и пауза перед ним. */
    static final class Step {
        final int primitive;
        final float scale;
        final int delayMs;

        Step(int primitive, float scale, int delayMs) {
            this.primitive = primitive;
            this.scale = scale;
            this.delayMs = delayMs;
        }
    }


    private HapticRecipes() {
    }

    static Step[] composition(HapticEffect effect) {
        switch (effect) {
            case CUES_ON:
                // Падающий металлический шарик: острые щелчки, каждый следующий отскок
                // слабее, а пауза до него короче — как у шарика, который подпрыгивает и замирает
                return new Step[]{
                        step(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 0),
                        step(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.75f, 140),
                        step(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.55f, 90),
                        step(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.4f, 55),
                        step(VibrationEffect.Composition.PRIMITIVE_TICK, 0.35f, 35),
                        step(VibrationEffect.Composition.PRIMITIVE_TICK, 0.25f, 20)};
            case CUES_OFF:
                // Пара к «шарику» при включении: шарик укатывается — низкие тики слабеют,
                // паузы между ними растут, как у замедляющегося качения, и тихий «клак» в конце
                return new Step[]{
                        step(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.9f, 0),
                        step(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.75f, 40),
                        step(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.6f, 60),
                        step(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.45f, 85),
                        step(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.3f, 115),
                        step(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.3f, 70)};
            case IPHONE_ON:
                // В духе Taptic Engine: «успех» — два коротких чистых удара, второй сильнее
                return new Step[]{
                        step(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 0),
                        step(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 80)};
            case TOGGLE_ON:
                return new Step[]{step(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f, 0)};
            case TOGGLE_OFF:
                return new Step[]{step(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f, 0)};
            case SELECT:
                return new Step[]{step(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 0)};
            default:
                throw new IllegalArgumentException(effect.name());
        }
    }

    /** Пауза между «шариками» на ползунке количества: раздельно, но одной серией. */
    static final int BALL_GAP_MS = 28;
    /** Пауза перед стуком на крайнем делении — чтобы он читался отдельно от деления. */
    static final int EDGE_KNOCK_GAP_MS = 40;

    /**
     * Вибрация деления ползунка — у каждого свой характер, по которому деление
     * узнаётся на ощупь. На крайних делениях после отклика — стук: на минимуме
     * негромкий, на максимуме отчётливый.
     *
     * @param level  выбранное деление
     * @param levels сколько всего делений (деления считаются от 1)
     */
    static Step[] slider(SliderHaptic kind, int level, int levels) {
        int clamped = Math.max(1, Math.min(levels, level));
        // Положение на ползунке от 0 (первое деление) до 1 (последнее)
        float t = levels > 1 ? (clamped - 1f) / (levels - 1f) : 1f;
        Step[] body;
        switch (kind) {
            case SENSITIVITY:
                body = sensitivitySteps(t);
                break;
            case OPACITY:
                // Чем плотнее точки, тем плотнее и «твёрже» щелчок
                body = new Step[]{step(VibrationEffect.Composition.PRIMITIVE_CLICK, lerp(0.3f, 1f, t), 0)};
                break;
            case COUNT:
                // Столько быстрых «шариков», сколько уровень: 1 точка — 1 тик, максимум — 4
                body = new Step[clamped];
                for (int i = 0; i < clamped; i++) {
                    body[i] = step(VibrationEffect.Composition.PRIMITIVE_TICK, 0.75f, i == 0 ? 0 : BALL_GAP_MS);
                }
                break;
            case SIZE:
            default:
                body = sizeSteps(t);
                break;
        }
        if (clamped == 1) {
            return append(body, step(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.4f, EDGE_KNOCK_GAP_MS));
        }
        if (clamped == levels) {
            return append(body,
                    step(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, EDGE_KNOCK_GAP_MS),
                    step(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 1f, 0));
        }
        return body;
    }

    /**
     * Сила растёт заметно на всём ползунке: внизу — тихие низкие тики, вверху — щелчки,
     * на максимуме — двойной слой щелчка и низкого тика в полную силу.
     * (Один низкий тик даже на 100% у Pixel слабый — поэтому верх строится на щелчках.)
     */
    private static Step[] sensitivitySteps(float t) {
        if (t < 0.3f) {
            return new Step[]{step(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, lerp(0.35f, 0.7f, t / 0.3f), 0)};
        }
        if (t < 0.9f) {
            return new Step[]{step(VibrationEffect.Composition.PRIMITIVE_CLICK, lerp(0.55f, 0.8f, (t - 0.3f) / 0.6f), 0)};
        }
        return new Step[]{
                step(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 0),
                step(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 1f, 0)};
    }

    /**
     * Мотор один, поэтому «где» вибрирует, задать нельзя — зато можно, как это ощущается.
     * Отличаемся длительностью и характером, а не только силой: маленькие точки — лёгкий
     * укол в одну точку; дальше щелчок, щелчок с басом, «вращение»; самые большие —
     * глубокий глухой удар, который чувствуется всем корпусом.
     */
    private static Step[] sizeSteps(float t) {
        int index = Math.round(t * 4f);
        switch (index) {
            case 0:
                return new Step[]{step(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f, 0)};
            case 1:
                return new Step[]{step(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, 0)};
            case 2:
                return new Step[]{
                        step(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 0),
                        step(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.8f, 0)};
            case 3:
                return new Step[]{
                        step(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.9f, 0),
                        step(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.4f, 0)};
            default:
                return new Step[]{step(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f, 0)};
        }
    }

    private static Step[] append(Step[] head, Step... tail) {
        Step[] result = new Step[head.length + tail.length];
        System.arraycopy(head, 0, result, 0, head.length);
        System.arraycopy(tail, 0, result, head.length, tail.length);
        return result;
    }

    static int[] sliderPrimitives(SliderHaptic kind, int level, int levels) {
        Step[] steps = slider(kind, level, levels);
        int[] result = new int[steps.length];
        for (int i = 0; i < steps.length; i++) {
            result[i] = steps[i].primitive;
        }
        return result;
    }

    /** Готовый эффект системы для ползунков на устройствах без примитивов. */
    static int sliderPredefined(SliderHaptic kind) {
        return kind == SliderHaptic.OPACITY ? VibrationEffect.EFFECT_CLICK : VibrationEffect.EFFECT_TICK;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Готовый эффект системы для устройств без примитивов. */
    static int predefined(HapticEffect effect) {
        switch (effect) {
            case CUES_ON:
                return VibrationEffect.EFFECT_HEAVY_CLICK;
            case IPHONE_ON:
                return VibrationEffect.EFFECT_DOUBLE_CLICK;
            case TOGGLE_ON:
            case SELECT:
                return VibrationEffect.EFFECT_CLICK;
            default:
                return VibrationEffect.EFFECT_TICK;
        }
    }

    static int[] primitives(HapticEffect effect) {
        Step[] steps = composition(effect);
        int[] result = new int[steps.length];
        for (int i = 0; i < steps.length; i++) {
            result[i] = steps[i].primitive;
        }
        return result;
    }

    private static Step step(int primitive, float scale, int delayMs) {
        return new Step(primitive, scale, delayMs);
    }
}
