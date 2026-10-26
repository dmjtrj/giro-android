package com.motioncues.app.settings;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

/**
 * Применяет выбранное оформление: светлую/тёмную тему через AppCompat
 * и цвета из обоев (Monet) только в режиме {@link ThemeMode#MONET}.
 */
public final class ThemeController {

    private ThemeController() {
    }

    /** Вызывается один раз из {@code Application.onCreate}. */
    public static void install(Application app) {
        AppSettings settings = new AppSettings(app);
        AppCompatDelegate.setDefaultNightMode(nightModeFor(settings.theme()));
        DynamicColors.applyToActivitiesIfAvailable(app, new DynamicColorsOptions.Builder()
                .setPrecondition((activity, theme) -> settings.theme() == ThemeMode.MONET)
                .build());
    }

    /**
     * Сохраняет и применяет тему. AppCompat сам пересоздаёт Activity, если сменилась
     * светлая/тёмная; если нет (например, «Светлая» → Monet днём) — пересоздаём сами,
     * чтобы включить или выключить цвета из обоев.
     */
    public static void change(Activity activity, ThemeMode mode) {
        AppSettings settings = new AppSettings(activity);
        ThemeMode old = settings.theme();
        if (old == mode) {
            return;
        }
        boolean wasNight = isNight(activity, old);
        // Цвет точек «Monet» есть только в теме Monet — в остальных возвращаемся к «Авто»
        if (mode != ThemeMode.MONET && settings.dotColor() == DotColor.MONET) {
            settings.setDotColor(DotColor.AUTO);
        }
        settings.setTheme(mode);
        AppCompatDelegate.setDefaultNightMode(nightModeFor(mode));
        if (isNight(activity, mode) == wasNight) {
            activity.recreate();
        }
    }

    /** Будет ли тема тёмной при текущей системной настройке. */
    public static boolean isNight(Context context, ThemeMode mode) {
        switch (mode) {
            case LIGHT:
                return false;
            case DARK:
                return true;
            default:
                return isSystemNight(context);
        }
    }

    public static boolean isSystemNight(Context context) {
        int uiMode = context.getApplicationContext().getResources().getConfiguration().uiMode;
        return (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int nightModeFor(ThemeMode mode) {
        switch (mode) {
            case LIGHT:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case DARK:
                return AppCompatDelegate.MODE_NIGHT_YES;
            default:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }
}
