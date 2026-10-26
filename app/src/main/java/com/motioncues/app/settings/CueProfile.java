package com.motioncues.app.settings;

/**
 * Итоговые параметры точек и движения, которые реально применяются к эффекту и превью.
 *
 * <p>В обычном режиме — то, что выбрал пользователь. В режиме «Как на iPhone» настройки
 * точек и движения заменяются набором, максимально близким к Vehicle Motion Cues в iOS:
 * одноцветные точки противоположного теме цвета, средний размер, плотность как на iPhone,
 * режим «Инерция» и средняя чувствительность.
 */
public final class CueProfile {

    /** Размер точек в режиме «Как на iPhone» — средний уровень. */
    static final int IPHONE_DOT_SIZE = AppSettings.DEFAULT_LEVEL;
    /** Плотность точек в режиме «Как на iPhone» — минимальная, одна колонка у края. */
    static final int IPHONE_DOT_COUNT = AppSettings.MIN_COUNT_LEVEL;

    public final boolean iphone;
    public final DotColor dotColor;
    public final int dotSize;
    public final int dotOpacity;
    public final int dotCount;
    public final MotionMode motionMode;
    public final int sensitivity;

    private CueProfile(boolean iphone, DotColor dotColor, int dotSize, int dotOpacity, int dotCount,
                       MotionMode motionMode, int sensitivity) {
        this.iphone = iphone;
        this.dotColor = dotColor;
        this.dotSize = dotSize;
        this.dotOpacity = dotOpacity;
        this.dotCount = dotCount;
        this.motionMode = motionMode;
        this.sensitivity = sensitivity;
    }

    /**
     * @param night тёмная ли сейчас тема приложения — в режиме «Как на iPhone» точки
     *              противоположного цвета: белые на тёмной, чёрные на светлой
     */
    public static CueProfile from(AppSettings settings, boolean night) {
        if (settings.iphoneMode()) {
            return iphone(night);
        }
        return new CueProfile(false, settings.dotColor(), settings.dotSize(), settings.dotOpacity(),
                settings.dotCount(), settings.motionMode(), settings.sensitivity());
    }

    static CueProfile iphone(boolean night) {
        // Белые и чёрные точки всегда полностью непрозрачные — ползунок непрозрачности не нужен
        return new CueProfile(true, night ? DotColor.WHITE : DotColor.BLACK, IPHONE_DOT_SIZE,
                AppSettings.MAX_LEVEL, IPHONE_DOT_COUNT, MotionMode.INERTIA, AppSettings.DEFAULT_LEVEL);
    }
}
