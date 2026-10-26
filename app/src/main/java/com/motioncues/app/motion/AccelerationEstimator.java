package com.motioncues.app.motion;

/**
 * Переводит показания датчиков в целевое смещение точек на экране.
 *
 * <p>Чистая Java без зависимостей от Android: вызывается из потока датчиков,
 * покрывается unit-тестами.
 *
 * <p>Конвейер для каждого замера ускорения:
 * <ol>
 *   <li>ускорение из координат устройства переводится в мировые (по ориентации);</li>
 *   <li>берётся только горизонтальная часть и раскладывается на «вперёд» и «вправо»
 *       относительно экрана — так эффект верен при любом положении телефона;</li>
 *   <li>вычитается медленно плавающий ноль датчика (high-pass);</li>
 *   <li>шум сглаживается (low-pass), мелкая тряска мягко отсекается;</li>
 *   <li>пока телефон вращают в руке, вклад ускорения приглушается;</li>
 *   <li>результат через tanh сжимается в смещение [-1; 1] против ускорения — как инерция.</li>
 * </ol>
 *
 * <p>Ось Y смещения направлена вниз, как на экране: при разгоне точки уходят вниз,
 * при торможении — вверх, в левом повороте — вправо.
 */
public final class AccelerationEstimator {

    /** Сглаживание шума ускорения. */
    static final float SMOOTHING_TAU_S = 0.12f;
    /** Скорость, с которой отслеживается дрейф нуля датчика. */
    static final float BIAS_TAU_S = 12f;
    /** Ниже этого ускорения (м/с²) движение считается шумом. */
    static final float DEAD_ZONE = 0.15f;
    /** Ускорение (м/с²), при котором смещение достигает ~76% от максимума. */
    static final float FULL_SCALE = 3f;
    /** Скорость вращения (рад/с), с которой начинаем приглушать: повороты машины медленнее. */
    static final float HANDLING_START = 1.2f;
    /** Скорость вращения (рад/с), при которой ускорение игнорируется полностью. */
    static final float HANDLING_FULL = 3f;
    /** Скорость, с которой приглушение отпускает после того, как телефон перестали крутить. */
    static final float HANDLING_RELEASE_TAU_S = 0.6f;

    private static final float MAX_DT_S = 0.25f;
    private static final float EPSILON = 1e-4f;

    /** Матрица поворота «устройство → мир», построчно 3×3. */
    private final float[] rotation = new float[9];
    private final float[] lastQuat = new float[4];
    private boolean hasRotation;
    private long lastRotationNanos;

    private long lastAccelNanos;
    private boolean hasAccel;

    private float biasForward, biasRight;
    private float smoothForward, smoothRight;
    private float handlingAttenuation = 1f;
    private float sensitivity = 1f;
    private int displayRotation;

    private volatile long packedTarget;

    /** Поворот дисплея: 0, 1, 2, 3 — как {@code Surface.ROTATION_*}. */
    public void setDisplayRotation(int rotation) {
        displayRotation = rotation & 3;
    }

    /** Множитель чувствительности, 1 — по умолчанию. */
    public void setSensitivity(float sensitivity) {
        this.sensitivity = Math.max(0f, sensitivity);
    }

    public void reset() {
        hasRotation = false;
        hasAccel = false;
        biasForward = biasRight = 0f;
        smoothForward = smoothRight = 0f;
        handlingAttenuation = 1f;
        packedTarget = 0L;
    }

    /**
     * Ориентация устройства из {@code TYPE_GAME_ROTATION_VECTOR}.
     *
     * @param values значения датчика: x, y, z и, если есть, w
     */
    public void onRotationVector(float[] values, long timestampNanos) {
        float x = values[0], y = values[1], z = values[2];
        float w;
        if (values.length >= 4) {
            w = values[3];
        } else {
            float ww = 1f - x * x - y * y - z * z;
            w = ww > 0f ? (float) Math.sqrt(ww) : 0f;
        }

        if (hasRotation) {
            float dt = (timestampNanos - lastRotationNanos) * 1e-9f;
            if (dt > 0f && dt < MAX_DT_S) {
                updateHandling(angleBetween(lastQuat, w, x, y, z) / dt, dt);
            }
        }
        lastQuat[0] = w;
        lastQuat[1] = x;
        lastQuat[2] = y;
        lastQuat[3] = z;
        lastRotationNanos = timestampNanos;
        quaternionToMatrix(w, x, y, z, rotation);
        hasRotation = true;
    }

    /**
     * Ускорение в координатах устройства, м/с². Подходит как
     * {@code TYPE_LINEAR_ACCELERATION}, так и сырой {@code TYPE_ACCELEROMETER}:
     * используется только горизонтальная часть, а гравитация направлена вертикально.
     */
    public void onAcceleration(float ax, float ay, float az, long timestampNanos) {
        if (!hasRotation) {
            return;
        }
        float dt = hasAccel ? (timestampNanos - lastAccelNanos) * 1e-9f : 0f;
        lastAccelNanos = timestampNanos;
        hasAccel = true;
        if (dt <= 0f) {
            return;
        }
        dt = Math.min(dt, MAX_DT_S);

        final float[] r = rotation;
        // Ускорение в мировых координатах (Z — вверх), нужна только горизонталь
        float wx = r[0] * ax + r[1] * ay + r[2] * az;
        float wy = r[3] * ax + r[4] * ay + r[5] * az;

        // Оси экрана в координатах устройства с учётом поворота дисплея
        float upX, upY;
        switch (displayRotation) {
            case 1: upX = -1f; upY = 0f; break;  // ROTATION_90
            case 2: upX = 0f; upY = -1f; break;  // ROTATION_180
            case 3: upX = 1f; upY = 0f; break;   // ROTATION_270
            default: upX = 0f; upY = 1f; break;  // ROTATION_0
        }

        // «Вперёд» по горизонтали — сумма верха экрана и направления «в экран»:
        // у лежащего телефона работает первое, у стоящего — второе, под наклоном оба
        float fx = r[0] * upX + r[1] * upY - r[2];
        float fy = r[3] * upX + r[4] * upY - r[5];
        float len = (float) Math.sqrt(fx * fx + fy * fy);
        if (len < EPSILON) {
            return;
        }
        fx /= len;
        fy /= len;
        // «Вправо» — «вперёд», повёрнутое на 90° по часовой вокруг вертикали
        float rx = fy;
        float ry = -fx;

        float forward = wx * fx + wy * fy;
        float right = wx * rx + wy * ry;

        // Ноль датчика плавает — следим за ним медленно и не учимся, пока телефон крутят
        float biasAlpha = alpha(dt, BIAS_TAU_S) * handlingAttenuation;
        biasForward += (forward - biasForward) * biasAlpha;
        biasRight += (right - biasRight) * biasAlpha;

        float smoothAlpha = alpha(dt, SMOOTHING_TAU_S);
        smoothForward += (forward - biasForward - smoothForward) * smoothAlpha;
        smoothRight += (right - biasRight - smoothRight) * smoothAlpha;

        float gain = sensitivity * handlingAttenuation / FULL_SCALE;
        float targetX = -shape(softDeadZone(smoothRight) * gain);
        float targetY = shape(softDeadZone(smoothForward) * gain);
        packedTarget = pack(targetX, targetY);
    }

    /** Целевое смещение по X в [-1; 1]; можно читать из любого потока. */
    public float targetX() {
        return Float.intBitsToFloat((int) (packedTarget >>> 32));
    }

    /** Целевое смещение по Y в [-1; 1]; можно читать из любого потока. */
    public float targetY() {
        return Float.intBitsToFloat((int) packedTarget);
    }

    /** Текущее приглушение из-за вращения телефона в руке: 1 — нет, 0 — полное. */
    float handlingAttenuation() {
        return handlingAttenuation;
    }

    private void updateHandling(float angularSpeed, float dt) {
        float t = (angularSpeed - HANDLING_START) / (HANDLING_FULL - HANDLING_START);
        float instant = 1f - Math.max(0f, Math.min(1f, t));
        if (instant < handlingAttenuation) {
            // Приглушаем сразу, чтобы точки не дёрнулись
            handlingAttenuation = instant;
        } else {
            handlingAttenuation += (instant - handlingAttenuation) * alpha(dt, HANDLING_RELEASE_TAU_S);
        }
    }

    /** Мягкий порог: ниже DEAD_ZONE почти ноль, выше — линейно, без ступеньки. */
    static float softDeadZone(float a) {
        float abs = Math.abs(a);
        if (abs <= DEAD_ZONE) {
            // Квадратичное начало: значение и наклон непрерывны на границе
            float k = abs / DEAD_ZONE;
            return Math.copySign(k * k * DEAD_ZONE * 0.5f, a);
        }
        return Math.copySign(abs - DEAD_ZONE * 0.5f, a);
    }

    /** Мягкое насыщение: линейно в начале, плавно упирается в ±1. */
    static float shape(float v) {
        return (float) Math.tanh(v);
    }

    static float alpha(float dt, float tau) {
        return 1f - (float) Math.exp(-dt / tau);
    }

    static long pack(float x, float y) {
        return ((long) Float.floatToRawIntBits(x) << 32) | (Float.floatToRawIntBits(y) & 0xFFFFFFFFL);
    }

    private static float angleBetween(float[] q, float w, float x, float y, float z) {
        float dot = Math.abs(q[0] * w + q[1] * x + q[2] * y + q[3] * z);
        return 2f * (float) Math.acos(Math.min(1f, dot));
    }

    /** То же, что {@code SensorManager.getRotationMatrixFromVector}, для матрицы 3×3. */
    static void quaternionToMatrix(float w, float x, float y, float z, float[] out) {
        float xx = 2 * x * x, yy = 2 * y * y, zz = 2 * z * z;
        float xy = 2 * x * y, zw = 2 * z * w, xz = 2 * x * z;
        float yw = 2 * y * w, yz = 2 * y * z, xw = 2 * x * w;
        out[0] = 1 - yy - zz;
        out[1] = xy - zw;
        out[2] = xz + yw;
        out[3] = xy + zw;
        out[4] = 1 - xx - zz;
        out[5] = yz - xw;
        out[6] = xz - yw;
        out[7] = yz + xw;
        out[8] = 1 - xx - yy;
    }
}
