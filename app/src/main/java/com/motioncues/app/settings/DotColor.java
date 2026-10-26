package com.motioncues.app.settings;

/** Цвет точек. */
public enum DotColor {
    /** Следует за оформлением приложения. */
    AUTO,
    /** Белые с тёмной обводкой. */
    WHITE,
    /** Чёрные со светлой обводкой. */
    BLACK,
    /** Тон из обоев при любом оформлении. */
    MONET;

    static DotColor fromName(String name) {
        for (DotColor color : values()) {
            if (color.name().equals(name)) {
                return color;
            }
        }
        return AUTO;
    }
}
