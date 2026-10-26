package com.motioncues.app.overlay;

import com.motioncues.app.motion.CueState;
import com.motioncues.app.motion.MotionEngine;

/**
 * Превращает сигнал движения в смещение поля точек — общий для эффекта и превью,
 * чтобы превью вело себя ровно как эффект.
 *
 * <p>Поле смещается против ускорения, как предметы по инерции, и плавно возвращается
 * на место пружиной движка, когда ускорение заканчивается. При резком разгоне,
 * торможении или повороте смещение больше полушага решётки — точки заходят за
 * границу окна, уменьшаются, а с другой стороны вырастают новые.
 */
final class CueFlow {

    private final MotionEngine engine;
    private final CueState state = new CueState();

    private float travelPx;
    private float periodX;
    private float periodY;

    private float fieldX, fieldY;

    CueFlow(MotionEngine engine) {
        this.engine = engine;
    }

    /**
     * @param travelPx смещение поля при максимальном сигнале, px
     * @param periodX  период поля по X, px — смещение держим в его пределах
     * @param periodY  период поля по Y, px
     */
    void configure(float travelPx, float periodX, float periodY) {
        this.travelPx = travelPx;
        this.periodX = periodX;
        this.periodY = periodY;
    }

    void start() {
        engine.start();
    }

    void stop() {
        engine.stop();
    }

    /** Шаг на кадр отрисовки. */
    void step(long frameTimeNanos) {
        engine.step(frameTimeNanos, state);
        apply(state.offsetX, state.offsetY);
    }

    /** Смещает поле по сглаженному сигналу {@code (signalX, signalY)} в [-1; 1]. */
    void apply(float signalX, float signalY) {
        fieldX = signalX * travelPx;
        fieldY = signalY * travelPx;
        // Поле периодично — приводим смещение к периоду; картинка от этого не меняется
        if (periodX > 0f && periodY > 0f) {
            fieldX = DotField.wrap(fieldX, periodX);
            fieldY = DotField.wrap(fieldY, periodY);
        }
    }

    float fieldX() {
        return fieldX;
    }

    float fieldY() {
        return fieldY;
    }

    /** Сила движения 0..1 — для яркости точек. */
    float activity() {
        return state.activity;
    }
}
