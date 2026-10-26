package com.motioncues.app.settings;

/** Как точки отвечают на движение. */
public enum MotionMode {
    /**
     * «Инерция» — как в iOS: разгон, торможение и повороты машины; точки смещаются
     * против ускорения и возвращаются. Вращение телефона в руке игнорируется.
     */
    INERTIA,
    /**
     * «Гироскоп» — как в большинстве приложений для Android: точки привязаны к миру
     * и живо отвечают на любой поворот и наклон телефона.
     */
    GYROSCOPE;

    static MotionMode fromName(String name) {
        for (MotionMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return INERTIA;
    }
}
