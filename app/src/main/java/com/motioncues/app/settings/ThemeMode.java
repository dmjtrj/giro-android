package com.motioncues.app.settings;

/** Оформление приложения и точек. */
public enum ThemeMode {
    /** Монохромная светлая палитра, не зависит от системы. */
    LIGHT,
    /** Монохромная тёмная палитра, не зависит от системы. */
    DARK,
    /** Цвета из обоев; светлая или тёмная — как в системе. */
    MONET;

    static ThemeMode fromName(String name) {
        for (ThemeMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return MONET;
    }
}
