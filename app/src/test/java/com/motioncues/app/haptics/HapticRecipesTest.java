package com.motioncues.app.haptics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.VibrationEffect;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class HapticRecipesTest {

    /** Длительности примитивов на Pixel 8 из {@code dumpsys vibrator_manager}, мс. */
    private static final Map<Integer, Integer> PIXEL_8_DURATIONS = new HashMap<>();

    static {
        PIXEL_8_DURATIONS.put(VibrationEffect.Composition.PRIMITIVE_CLICK, 26);
        PIXEL_8_DURATIONS.put(VibrationEffect.Composition.PRIMITIVE_TICK, 5);
        PIXEL_8_DURATIONS.put(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 12);
        PIXEL_8_DURATIONS.put(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 150);
        PIXEL_8_DURATIONS.put(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 100);
        PIXEL_8_DURATIONS.put(VibrationEffect.Composition.PRIMITIVE_SPIN, 133);
        PIXEL_8_DURATIONS.put(VibrationEffect.Composition.PRIMITIVE_THUD, 300);
        PIXEL_8_DURATIONS.put(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 500);
    }

    @Test
    public void everyEffect_hasValidComposition() {
        for (HapticEffect effect : HapticEffect.values()) {
            HapticRecipes.Step[] steps = HapticRecipes.composition(effect);
            assertTrue(effect.name(), steps.length > 0);
            for (HapticRecipes.Step step : steps) {
                assertTrue(effect.name(), step.scale > 0f && step.scale <= 1f);
                assertTrue(effect.name(), step.delayMs >= 0);
                assertNotNull(effect.name(), PIXEL_8_DURATIONS.get(step.primitive));
            }
        }
    }

    @Test
    public void uiFeedback_isShortAndCrisp() {
        // Отклик элементов интерфейса не длиннее 40 мс — иначе ощущается как «жужжание»
        for (HapticEffect effect : new HapticEffect[]{HapticEffect.TOGGLE_ON, HapticEffect.TOGGLE_OFF,
                HapticEffect.SELECT, HapticEffect.SLIDER_TICK, HapticEffect.SLIDER_EDGE}) {
            assertTrue(effect.name() + " " + durationOnPixel8(effect), durationOnPixel8(effect) <= 40);
        }
    }

    @Test
    public void onOff_fitInQuarterSecond() {
        assertTrue(durationOnPixel8(HapticEffect.CUES_ON) <= 250);
        assertTrue(durationOnPixel8(HapticEffect.CUES_OFF) <= 250);
    }

    @Test
    public void longPrimitives_areNotUsed() {
        for (HapticEffect effect : HapticEffect.values()) {
            for (HapticRecipes.Step step : HapticRecipes.composition(effect)) {
                assertTrue(effect.name(), step.primitive != VibrationEffect.Composition.PRIMITIVE_SLOW_RISE);
                assertTrue(effect.name(), step.primitive != VibrationEffect.Composition.PRIMITIVE_THUD);
            }
        }
    }

    @Test
    public void envelopes_onlyForOnOff_andEndSilently() {
        for (HapticEffect effect : HapticEffect.values()) {
            HapticRecipes.EnvelopePoint[] points = HapticRecipes.envelope(effect);
            if (effect != HapticEffect.CUES_ON && effect != HapticEffect.CUES_OFF) {
                assertNull(effect.name(), points);
                continue;
            }
            assertNotNull(effect.name(), points);
            assertEquals(0f, points[points.length - 1].intensity, 0f);
            long total = 0;
            for (HapticRecipes.EnvelopePoint point : points) {
                assertTrue(point.intensity >= 0f && point.intensity <= 1f);
                assertTrue(point.sharpness >= 0f && point.sharpness <= 1f);
                assertTrue(point.durationMs > 0);
                total += point.durationMs;
            }
            assertTrue(effect.name() + " " + total, total <= 250);
        }
    }

    @Test
    public void onAndOff_feelDifferent() {
        // Включение начинается с нарастания, выключение — с затухания
        assertEquals(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                HapticRecipes.composition(HapticEffect.CUES_ON)[0].primitive);
        assertEquals(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                HapticRecipes.composition(HapticEffect.CUES_OFF)[0].primitive);
    }

    private static int durationOnPixel8(HapticEffect effect) {
        int total = 0;
        for (HapticRecipes.Step step : HapticRecipes.composition(effect)) {
            total += step.delayMs + PIXEL_8_DURATIONS.get(step.primitive);
        }
        return total;
    }
}
