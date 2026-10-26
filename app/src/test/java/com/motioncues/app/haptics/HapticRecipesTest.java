package com.motioncues.app.haptics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
                HapticEffect.SELECT}) {
            assertTrue(effect.name() + " " + durationOnPixel8(effect), durationOnPixel8(effect) <= 40);
        }
    }

    @Test
    public void onOff_staySnappy() {
        // «Шарик» при включении и «укатывание» при выключении — до полусекунды
        assertTrue(durationOnPixel8(HapticEffect.CUES_ON) <= 500);
        assertTrue(durationOnPixel8(HapticEffect.CUES_OFF) <= 500);
        assertTrue(durationOnPixel8(HapticEffect.IPHONE_ON) <= 150);
    }

    @Test
    public void cuesOn_isBouncingMetalBall() {
        // Острые щелчки: каждый отскок слабее, пауза до него короче
        HapticRecipes.Step[] ball = HapticRecipes.composition(HapticEffect.CUES_ON);
        assertTrue(ball.length >= 5);
        assertEquals(VibrationEffect.Composition.PRIMITIVE_CLICK, ball[0].primitive);
        assertEquals(1f, ball[0].scale, 0f);
        for (int i = 1; i < ball.length; i++) {
            assertTrue("отскок слабее: " + i, ball[i].scale < ball[i - 1].scale);
            if (i > 1) {
                assertTrue("пауза короче: " + i, ball[i].delayMs < ball[i - 1].delayMs);
            }
        }
    }

    @Test
    public void cuesOff_isBallRollingAway() {
        // Низкие тики слабеют, паузы растут — замедляющееся качение; в конце тихий «клак»
        HapticRecipes.Step[] roll = HapticRecipes.composition(HapticEffect.CUES_OFF);
        assertTrue(roll.length >= 5);
        int last = roll.length - 1;
        for (int i = 1; i < last; i++) {
            assertEquals(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, roll[i].primitive);
            assertTrue("тик слабее: " + i, roll[i].scale < roll[i - 1].scale);
            if (i > 1) {
                assertTrue("пауза длиннее: " + i, roll[i].delayMs > roll[i - 1].delayMs);
            }
        }
        assertEquals(VibrationEffect.Composition.PRIMITIVE_CLICK, roll[last].primitive);
        assertTrue(roll[last].scale <= 0.4f);
    }

    @Test
    public void iphoneOn_isTwoCleanTapsSecondStronger() {
        HapticRecipes.Step[] success = HapticRecipes.composition(HapticEffect.IPHONE_ON);
        assertEquals(2, success.length);
        assertEquals(VibrationEffect.Composition.PRIMITIVE_CLICK, success[0].primitive);
        assertEquals(VibrationEffect.Composition.PRIMITIVE_CLICK, success[1].primitive);
        assertTrue(success[1].scale > success[0].scale);
    }

    @Test
    public void slowRise_isNotUsed() {
        for (HapticEffect effect : HapticEffect.values()) {
            for (HapticRecipes.Step step : HapticRecipes.composition(effect)) {
                assertTrue(effect.name(), step.primitive != VibrationEffect.Composition.PRIMITIVE_SLOW_RISE);
            }
        }
    }

    @Test
    public void onAndOff_feelDifferent() {
        // Включение начинается с острого щелчка, выключение — с низкого тика
        assertEquals(VibrationEffect.Composition.PRIMITIVE_CLICK,
                HapticRecipes.composition(HapticEffect.CUES_ON)[0].primitive);
        assertEquals(VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                HapticRecipes.composition(HapticEffect.CUES_OFF)[0].primitive);
    }

    @Test
    public void sensitivity_strengthGrowsWithLevel_andMaxIsStrong() {
        float previous = 0f;
        for (int level = 1; level <= 5; level++) {
            float energy = energy(withoutKnock(HapticRecipes.slider(SliderHaptic.SENSITIVITY, level, 5), level, 5));
            assertTrue("уровень " + level + ": " + energy, energy > previous);
            previous = energy;
        }
        // Максимум — двойной слой щелчка и низкого тика в полную силу
        HapticRecipes.Step[] max = withoutKnock(HapticRecipes.slider(SliderHaptic.SENSITIVITY, 5, 5), 5, 5);
        assertEquals(2, max.length);
        assertEquals(1f, max[0].scale, 0f);
        assertEquals(1f, max[1].scale, 0f);
    }

    @Test
    public void count_playsOneBallPerLevel() {
        for (int level = 1; level <= 4; level++) {
            HapticRecipes.Step[] steps = withoutKnock(HapticRecipes.slider(SliderHaptic.COUNT, level, 4), level, 4);
            assertEquals(level, steps.length);
            for (int i = 1; i < steps.length; i++) {
                assertEquals(VibrationEffect.Composition.PRIMITIVE_TICK, steps[i].primitive);
                assertEquals(HapticRecipes.BALL_GAP_MS, steps[i].delayMs);
            }
        }
    }

    @Test
    public void size_levelsFeelDistinct() {
        // Каждое деление — свой набор примитивов, а не та же вибрация другой силы
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int level = 1; level <= 5; level++) {
            HapticRecipes.Step[] steps = withoutKnock(HapticRecipes.slider(SliderHaptic.SIZE, level, 5), level, 5);
            StringBuilder signature = new StringBuilder();
            for (HapticRecipes.Step step : steps) {
                signature.append(step.primitive).append(',');
            }
            assertTrue("уровень " + level + " повторяет другой", seen.add(signature.toString()));
        }
        // Маленькие — лёгкий укол, самые большие — глухой удар всем корпусом
        HapticRecipes.Step[] small = withoutKnock(HapticRecipes.slider(SliderHaptic.SIZE, 1, 5), 1, 5);
        assertEquals(VibrationEffect.Composition.PRIMITIVE_TICK, small[0].primitive);
        HapticRecipes.Step[] large = withoutKnock(HapticRecipes.slider(SliderHaptic.SIZE, 5, 5), 5, 5);
        assertEquals(VibrationEffect.Composition.PRIMITIVE_THUD, large[0].primitive);
    }

    @Test
    public void edges_knock_softAtMin_distinctAtMax() {
        for (SliderHaptic kind : SliderHaptic.values()) {
            HapticRecipes.Step[] min = HapticRecipes.slider(kind, 1, 5);
            HapticRecipes.Step knockMin = min[min.length - 1];
            assertEquals(VibrationEffect.Composition.PRIMITIVE_CLICK, knockMin.primitive);
            assertEquals(HapticRecipes.EDGE_KNOCK_GAP_MS, knockMin.delayMs);
            assertTrue(knockMin.scale <= 0.5f);

            HapticRecipes.Step[] max = HapticRecipes.slider(kind, 5, 5);
            HapticRecipes.Step knockClick = max[max.length - 2];
            assertEquals(VibrationEffect.Composition.PRIMITIVE_CLICK, knockClick.primitive);
            assertEquals(1f, knockClick.scale, 0f);
            assertEquals(1f, max[max.length - 1].scale, 0f);

            // На средних делениях стука нет
            HapticRecipes.Step[] mid = HapticRecipes.slider(kind, 3, 5);
            for (HapticRecipes.Step step : mid) {
                assertTrue(step.delayMs != HapticRecipes.EDGE_KNOCK_GAP_MS);
            }
        }
    }

    @Test
    public void sliderFeedback_fitsBeforeNextDetent() {
        // Средние деления — короче 150 мс; крайние со стуком и самый большой размер — до 450 мс
        for (SliderHaptic kind : SliderHaptic.values()) {
            for (int level = 1; level <= 5; level++) {
                int total = 0;
                for (HapticRecipes.Step step : HapticRecipes.slider(kind, level, 5)) {
                    assertTrue(step.scale > 0f && step.scale <= 1f);
                    total += step.delayMs + PIXEL_8_DURATIONS.get(step.primitive);
                }
                int limit = level == 1 || level == 5 ? 450 : 150;
                assertTrue(kind + " " + level + ": " + total, total < limit);
            }
        }
    }

    /** Отрезает стук на крайних делениях — чтобы сравнивать сами отклики делений. */
    private static HapticRecipes.Step[] withoutKnock(HapticRecipes.Step[] steps, int level, int levels) {
        int knock = level == 1 ? 1 : level == levels ? 2 : 0;
        return java.util.Arrays.copyOf(steps, steps.length - knock);
    }

    /** Грубая «сила» отклика: щелчок ощущается заметно сильнее низкого тика той же силы. */
    private static float energy(HapticRecipes.Step[] steps) {
        float total = 0f;
        for (HapticRecipes.Step step : steps) {
            float weight = step.primitive == VibrationEffect.Composition.PRIMITIVE_CLICK ? 1f
                    : step.primitive == VibrationEffect.Composition.PRIMITIVE_LOW_TICK ? 0.6f : 0.4f;
            total += step.scale * weight;
        }
        return total;
    }

    private static int durationOnPixel8(HapticEffect effect) {
        int total = 0;
        for (HapticRecipes.Step step : HapticRecipes.composition(effect)) {
            total += step.delayMs + PIXEL_8_DURATIONS.get(step.primitive);
        }
        return total;
    }
}
