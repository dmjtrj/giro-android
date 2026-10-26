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

/**
 * Виброотклик приложения. Выбирает лучший способ, который умеет вибромотор:
 * <ol>
 *   <li>огибающая с управлением частотой (Android 16+, новые Pixel);</li>
 *   <li>композиция из примитивов (Pixel 6+ и другие моторы с COMPOSE_EFFECTS);</li>
 *   <li>готовые системные эффекты — на остальных устройствах.</li>
 * </ol>
 * Решение проверяется по возможностям мотора, а не по модели телефона.
 * Эффекты собираются один раз и кэшируются.
 */
public final class Haptics {

    private static final String TAG = "Haptics";

    private static Haptics instance;

    private final Vibrator vibrator;
    private final AppSettings settings;
    private final boolean envelopesSupported;
    private final EnumMap<HapticEffect, VibrationEffect> cache = new EnumMap<>(HapticEffect.class);

    public static synchronized Haptics get(Context context) {
        if (instance == null) {
            instance = new Haptics(context.getApplicationContext());
        }
        return instance;
    }

    private Haptics(Context context) {
        vibrator = context.getSystemService(VibratorManager.class).getDefaultVibrator();
        settings = new AppSettings(context);
        envelopesSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
                && vibrator.areEnvelopeEffectsSupported();
        Log.i(TAG, "envelopes=" + envelopesSupported
                + " primitives=" + vibrator.areAllPrimitivesSupported(allPrimitives()));
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Касание пользователя: система учитывает настройку «Отклик на касания»
            vibrator.vibrate(vibration, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH));
        } else {
            vibrator.vibrate(vibration);
        }
    }

    private VibrationEffect build(HapticEffect effect) {
        if (envelopesSupported) {
            HapticRecipes.EnvelopePoint[] points = HapticRecipes.envelope(effect);
            if (points != null) {
                return buildEnvelope(effect, points);
            }
        }
        if (vibrator.areAllPrimitivesSupported(HapticRecipes.primitives(effect))) {
            VibrationEffect.Composition composition = VibrationEffect.startComposition();
            for (HapticRecipes.Step step : HapticRecipes.composition(effect)) {
                composition.addPrimitive(step.primitive, step.scale, step.delayMs);
            }
            return composition.compose();
        }
        return VibrationEffect.createPredefined(HapticRecipes.predefined(effect));
    }

    private static VibrationEffect buildEnvelope(HapticEffect effect, HapticRecipes.EnvelopePoint[] points) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            throw new IllegalStateException("Envelopes need Android 16");
        }
        VibrationEffect.BasicEnvelopeBuilder builder = new VibrationEffect.BasicEnvelopeBuilder()
                .setInitialSharpness(HapticRecipes.envelopeInitialSharpness(effect));
        for (HapticRecipes.EnvelopePoint point : points) {
            builder.addControlPoint(point.intensity, point.sharpness, point.durationMs);
        }
        return builder.build();
    }

    private static int[] allPrimitives() {
        return new int[]{
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL};
    }
}
