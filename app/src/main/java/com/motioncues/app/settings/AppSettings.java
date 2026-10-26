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

    /** Градации ползунков размера и непрозрачности точек. */
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 5;
    public static final int DEFAULT_LEVEL = 3;

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
