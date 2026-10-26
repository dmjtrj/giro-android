package com.motioncues.app.motion;

/**
 * Состояние точек на текущий кадр. Объект переиспользуется — без аллокаций в кадре.
 */
public final class CueState {
    /** Смещение по X в [-1; 1], вправо — плюс. */
    public float offsetX;
    /** Смещение по Y в [-1; 1], вниз — плюс. */
    public float offsetY;
    /** Сила движения в [0; 1] — для плавного появления точек. */
    public float activity;
}
