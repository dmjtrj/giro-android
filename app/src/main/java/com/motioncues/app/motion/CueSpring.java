package com.motioncues.app.motion;

/**
 * Пружина с демпфером, которая плавно ведёт точки к целевому смещению.
 *
 * <p>Шагается на каждом кадре отрисовки, поэтому движение идёт с частотой экрана
 * (60/120 Гц), а не с частотой датчиков. Чуть недодемпфирована — точки
 * приходят мягко, почти без перелёта, как в iOS.
 *
 * <p>Заодно считает «активность» — сглаженную силу движения для плавного
 * появления и скрытия точек.
 */
public final class CueSpring {

    /** Собственная частота пружины, рад/с. */
    static final float OMEGA = 9f;
    /** Коэффициент демпфирования: 1 — без перелёта, меньше — мягче и живее. */
    static final float DAMPING_RATIO = 0.85f;
    /** Как быстро точки проявляются, когда начинается движение. */
    static final float ACTIVITY_ATTACK_TAU_S = 0.2f;
    /** Как быстро точки гаснут в ровном ходе. */
    static final float ACTIVITY_RELEASE_TAU_S = 1.5f;
    /** Сила движения, при которой активность считается полной. */
    static final float ACTIVITY_FULL = 0.25f;

    private static final float MAX_STEP_S = 1f / 240f;
    private static final float MAX_FRAME_S = 0.1f;

    private float x, y;
    private float vx, vy;
    private float activity;

    /** Продвигает пружину на {@code dt} секунд к цели и пишет результат в {@code out}. */
    public void step(float dt, float targetX, float targetY, CueState out) {
        dt = Math.max(0f, Math.min(dt, MAX_FRAME_S));
        // Мелкие подшаги: устойчиво при любой частоте кадров и пропусках кадров
        int steps = Math.max(1, (int) Math.ceil(dt / MAX_STEP_S));
        float h = dt / steps;
        float k = OMEGA * OMEGA;
        float c = 2f * DAMPING_RATIO * OMEGA;
        for (int i = 0; i < steps; i++) {
            // Полунеявный Эйлер: сначала скорость, затем положение
            vx += (k * (targetX - x) - c * vx) * h;
            vy += (k * (targetY - y) - c * vy) * h;
            x += vx * h;
            y += vy * h;
        }

        float magnitude = Math.min(1f, (float) Math.hypot(targetX, targetY) / ACTIVITY_FULL);
        float tau = magnitude > activity ? ACTIVITY_ATTACK_TAU_S : ACTIVITY_RELEASE_TAU_S;
        if (dt > 0f) {
            activity += (magnitude - activity) * AccelerationEstimator.alpha(dt, tau);
        }

        out.offsetX = x;
        out.offsetY = y;
        out.activity = activity;
    }

    public void reset() {
        x = y = vx = vy = activity = 0f;
    }
}
