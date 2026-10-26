package com.motioncues.app.haptics;

import android.content.Context;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import com.motioncues.app.settings.AppSettings;

import java.util.EnumMap;
import java.util.HashMap;

/**
 * Виброотклик приложения. Выбирает лучший способ, который умеет вибромотор:
 * композиция из примитивов (Pixel 6+ и другие моторы с COMPOSE_EFFECTS) или
 * готовые системные эффекты — на остальных устройствах.
 * Решение проверяется по возможностям мотора, а не по модели телефона.
 * Эффекты собираются один раз и кэшируются.
 */
public final class Haptics {

    private static final String TAG = "Haptics";

    private static Haptics instance;

    private final Vibrator vibrator;
    private final AppSettings settings;
    private final EnumMap<HapticEffect, VibrationEffect> cache = new EnumMap<>(HapticEffect.class);
    /** Вибрации делений ползунков: ключ — вид ползунка, деление и число делений. */
    private final HashMap<Integer, VibrationEffect> sliderCache = new HashMap<>();

    public static synchronized Haptics get(Context context) {
        if (instance == null) {
            instance = new Haptics(context.getApplicationContext());
        }
        return instance;
    }

    private Haptics(Context context) {
        vibrator = context.getSystemService(VibratorManager.class).getDefaultVibrator();
        settings = new AppSettings(context);
        Log.i(TAG, "primitives=" + vibrator.areAllPrimitivesSupported(allPrimitives()));
    }

    /** Проигрывает эффект, если виброотклик включён в настройках. */
    public void play(HapticEffect effect) {
        if (!settings.hapticsEnabled() || !vibrator.hasVibrator()) {
            return;
        }
        VibrationEffect vibration = cache.get(effect);
        if (vibration == null) {
            vibration = build(effect);
            cache.put(effect, vibration);
        }
        vibrate(vibration);
    }

    /**
     * Вибрация деления ползунка: у каждого ползунка свой характер, деление узнаётся на ощупь.
     *
     * @param levels сколько всего делений (деления считаются от 1)
     */
    public void playSlider(SliderHaptic kind, int level, int levels) {
        if (!settings.hapticsEnabled() || !vibrator.hasVibrator()) {
            return;
        }
        int key = (kind.ordinal() * 16 + levels) * 16 + level;
        VibrationEffect vibration = sliderCache.get(key);
        if (vibration == null) {
            if (vibrator.areAllPrimitivesSupported(HapticRecipes.sliderPrimitives(kind, level, levels))) {
                vibration = compose(HapticRecipes.slider(kind, level, levels));
            } else {
                vibration = VibrationEffect.createPredefined(HapticRecipes.sliderPredefined(kind));
            }
            sliderCache.put(key, vibration);
        }
        vibrate(vibration);
    }

    private void vibrate(VibrationEffect vibration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Касание пользователя: система учитывает настройку «Отклик на касания»
            vibrator.vibrate(vibration, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH));
        } else {
            vibrator.vibrate(vibration);
        }
    }

    private static VibrationEffect compose(HapticRecipes.Step[] steps) {
        VibrationEffect.Composition composition = VibrationEffect.startComposition();
        for (HapticRecipes.Step step : steps) {
            composition.addPrimitive(step.primitive, step.scale, step.delayMs);
        }
        return composition.compose();
    }

    private VibrationEffect build(HapticEffect effect) {
        if (vibrator.areAllPrimitivesSupported(HapticRecipes.primitives(effect))) {
            return compose(HapticRecipes.composition(effect));
        }
        return VibrationEffect.createPredefined(HapticRecipes.predefined(effect));
    }

    private static int[] allPrimitives() {
        return new int[]{
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                VibrationEffect.Composition.PRIMITIVE_SPIN,
                VibrationEffect.Composition.PRIMITIVE_THUD};
    }
}
