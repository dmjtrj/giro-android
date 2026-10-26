package com.motioncues.app.haptics;

import android.os.VibrationEffect;

/**
 * Рецепты вибрации без зависимостей от железа — чтобы их можно было проверить тестами.
 *
 * <p>Основной путь — композиции из примитивов вибромотора. Подобраны под длительности
 * примитивов Pixel: CLICK 26 мс, TICK 5 мс, LOW_TICK 12 мс, QUICK_RISE 150 мс,
 * QUICK_FALL 100 мс. Длинные SLOW_RISE (500 мс) и THUD (300 мс) не используются —
 * отклик интерфейса должен укладываться в доли секунды.
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

    /** Контрольная точка огибающей: сила, «резкость» (частота) и длительность перехода. */
    static final class EnvelopePoint {
        final float intensity;
        final float sharpness;
        final long durationMs;

        EnvelopePoint(float intensity, float sharpness, long durationMs) {
            this.intensity = intensity;
            this.sharpness = sharpness;
            this.durationMs = durationMs;
        }
    }

    private HapticRecipes() {
    }

    static Step[] composition(HapticEffect effect) {
        switch (effect) {
            case CUES_ON:
                // Мотор разгоняется и защёлкивается — ощущение «включилось»
                return new Step[]{
                        step(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.5f, 0),
                        step(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 0)};
            case CUES_OFF:
                // Затухание и негромкая точка в конце — «выключилось»
                return new Step[]{
                        step(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.6f, 0),
                        step(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.6f, 50)};
            case TOGGLE_ON:
                return new Step[]{step(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f, 0)};
            case TOGGLE_OFF:
                return new Step[]{step(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f, 0)};
            case SELECT:
                return new Step[]{step(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 0)};
            case SLIDER_TICK:
                // Глубокий короткий «зубчик», как у колёсика с фиксацией
                return new Step[]{step(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.9f, 0)};
            case SLIDER_EDGE:
                return new Step[]{step(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.85f, 0)};
            default:
                throw new IllegalArgumentException(effect.name());
        }
    }

    /**
     * Огибающая с управлением частотой — для моторов, которые это умеют (Android 16+).
     * Только для включения и выключения: там плавное нарастание и затухание ощущаются
     * лучше всего. Последняя точка всегда с нулевой силой — мотор останавливается мягко.
     *
     * @return {@code null}, если для эффекта достаточно композиции
     */
    static EnvelopePoint[] envelope(HapticEffect effect) {
        switch (effect) {
            case CUES_ON:
                return new EnvelopePoint[]{
                        new EnvelopePoint(0.6f, 0.5f, 60),
                        new EnvelopePoint(1f, 0.8f, 40),
                        new EnvelopePoint(0f, 0.8f, 20)};
            case CUES_OFF:
                return new EnvelopePoint[]{
                        new EnvelopePoint(0.8f, 0.7f, 20),
                        new EnvelopePoint(0f, 0.2f, 120)};
            default:
                return null;
        }
    }

    /** Начальная «резкость» огибающей. */
    static float envelopeInitialSharpness(HapticEffect effect) {
        return effect == HapticEffect.CUES_OFF ? 0.8f : 0.3f;
    }

    /** Готовый эффект системы для устройств без примитивов. */
    static int predefined(HapticEffect effect) {
        switch (effect) {
            case CUES_ON:
            case SLIDER_EDGE:
                return VibrationEffect.EFFECT_HEAVY_CLICK;
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
