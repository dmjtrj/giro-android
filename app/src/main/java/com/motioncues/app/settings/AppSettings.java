package com.motioncues.app.settings;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Настройки приложения поверх {@link SharedPreferences}. Приложение однопроцессное,
 * поэтому служба оверлея узнаёт об изменениях через обычный listener.
 */
public final class AppSettings {

    public static final String KEY_THEME = "theme";
    public static final String KEY_DOT_SIZE = "dot_size";
    public static final String KEY_DOT_OPACITY = "dot_opacity";
    public static final String KEY_DOT_COLOR = "dot_color";
    public static final String KEY_HAPTICS = "haptics";
    public static final String KEY_SENSITIVITY = "sensitivity";
    public static final String KEY_MOTION_MODE = "motion_mode";
    public static final String KEY_ABOVE_SYSTEM = "above_system";
    public static final String KEY_DOT_COUNT = "dot_count";
    public static final String KEY_IPHONE_MODE = "iphone_mode";

    /** Градации ползунков: размер и непрозрачность точек, чувствительность. */
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 5;
    public static final int DEFAULT_LEVEL = 3;

    /** Градации ползунка «Количество точек»: 1 — как на iPhone, 4 — максимум. */
    public static final int MIN_COUNT_LEVEL = 1;
    public static final int MAX_COUNT_LEVEL = 4;

    private static final String FILE = "settings";

    private final SharedPreferences prefs;

    public AppSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public ThemeMode theme() {
        return ThemeMode.fromName(prefs.getString(KEY_THEME, null));
    }

    public void setTheme(ThemeMode mode) {
        prefs.edit().putString(KEY_THEME, mode.name()).apply();
    }

    public int dotCount() {
        int level = prefs.getInt(KEY_DOT_COUNT, MAX_COUNT_LEVEL);
        return Math.max(MIN_COUNT_LEVEL, Math.min(MAX_COUNT_LEVEL, level));
    }

    public void setDotCount(int level) {
        prefs.edit().putInt(KEY_DOT_COUNT, Math.max(MIN_COUNT_LEVEL, Math.min(MAX_COUNT_LEVEL, level))).apply();
    }

    /** «Как на iPhone»: настройки точек и движения заменяются набором, близким к iOS. */
    public boolean iphoneMode() {
        return prefs.getBoolean(KEY_IPHONE_MODE, false);
    }

    public void setIphoneMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_IPHONE_MODE, enabled).apply();
    }

    /** Показывать точки поверх шторки и системных окон (через службу специальных возможностей). */
    public boolean aboveSystem() {
        return prefs.getBoolean(KEY_ABOVE_SYSTEM, false);
    }

    public void setAboveSystem(boolean enabled) {
        prefs.edit().putBoolean(KEY_ABOVE_SYSTEM, enabled).apply();
    }

    /** Виброотклик включён по умолчанию. */
    public boolean hapticsEnabled() {
        return prefs.getBoolean(KEY_HAPTICS, true);
    }

    public void setHapticsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply();
    }

    public DotColor dotColor() {
        return DotColor.fromName(prefs.getString(KEY_DOT_COLOR, null));
    }

    public void setDotColor(DotColor color) {
        prefs.edit().putString(KEY_DOT_COLOR, color.name()).apply();
    }

    public MotionMode motionMode() {
        return MotionMode.fromName(prefs.getString(KEY_MOTION_MODE, null));
    }

    public void setMotionMode(MotionMode mode) {
        prefs.edit().putString(KEY_MOTION_MODE, mode.name()).apply();
    }

    public int sensitivity() {
        return level(KEY_SENSITIVITY);
    }

    public void setSensitivity(int level) {
        setLevel(KEY_SENSITIVITY, level);
    }

    public int dotSize() {
        return level(KEY_DOT_SIZE);
    }

    public void setDotSize(int level) {
        setLevel(KEY_DOT_SIZE, level);
    }

    public int dotOpacity() {
        return level(KEY_DOT_OPACITY);
    }

    public void setDotOpacity(int level) {
        setLevel(KEY_DOT_OPACITY, level);
    }

    /** SharedPreferences хранит listener слабой ссылкой — держите ссылку на него сами. */
    public void registerListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }

    private int level(String key) {
        return clamp(prefs.getInt(key, DEFAULT_LEVEL));
    }

    private void setLevel(String key, int level) {
        prefs.edit().putInt(key, clamp(level)).apply();
    }

    private static int clamp(int level) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }
}
