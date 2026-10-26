package com.motioncues.app.motion;

/**
 * Режим «Гироскоп»: точки как будто привязаны к миру вокруг. Повернули или наклонили
 * телефон — точки живо смещаются в обратную сторону; когда телефон замер,
 * опорная ориентация плавно догоняет текущую и точки возвращаются в центр.
 *
 * <p>Так работает большинство подобных приложений для Android. Чистая Java без
 * зависимостей от Android: вызывается из потока датчиков, покрывается unit-тестами.
 */
public final class RotationEstimator {

    /** Поворот (рад), при котором смещение достигает ~76% от максимума: ~11°. */
    static final float FULL_SCALE_RAD = 0.2f;
    /** Как быстро опорная ориентация догоняет текущую — время возврата точек в центр. */
    static final float RETURN_TAU_S = 1.2f;

    private static final float MAX_DT_S = 0.25f;

    /** Опорная ориентация (w, x, y, z): относительно неё считается поворот. */
    private final float[] reference = new float[4];
    private boolean hasReference;
    private long lastNanos;
    private float sensitivity = 1f;
    private int displayRotation;

    private volatile long packedTarget;

    /** Поворот дисплея: 0, 1, 2, 3 — как {@code Surface.ROTATION_*}. */
    public void setDisplayRotation(int rotation) {
        displayRotation = rotation & 3;
    }

    public void setSensitivity(float sensitivity) {
        this.sensitivity = Math.max(0f, sensitivity);
    }

    public void reset() {
        hasReference = false;
        packedTarget = 0L;
    }

    /** Ориентация из {@code TYPE_GAME_ROTATION_VECTOR}: x, y, z и, если есть, w. */
    public void onRotationVector(float[] values, long timestampNanos) {
        float x = values[0], y = values[1], z = values[2];
        float w;
        if (values.length >= 4) {
            w = values[3];
        } else {
            float ww = 1f - x * x - y * y - z * z;
            w = ww > 0f ? (float) Math.sqrt(ww) : 0f;
        }
        if (!hasReference) {
            set(reference, w, x, y, z);
            hasReference = true;
            lastNanos = timestampNanos;
            packedTarget = 0L;
            return;
        }
        float dt = Math.min(MAX_DT_S, Math.max(0f, (timestampNanos - lastNanos) * 1e-9f));
        lastNanos = timestampNanos;

        // Поворот от опорной ориентации к текущей в координатах устройства: q = ref⁻¹ · cur
        float rw = reference[0], rx = -reference[1], ry = -reference[2], rz = -reference[3];
        float qw = rw * w - rx * x - ry * y - rz * z;
        float qx = rw * x + rx * w + ry * z - rz * y;
        float qy = rw * y - rx * z + ry * w + rz * x;
        float qz = rw * z + rx * y - ry * x + rz * w;
        if (qw < 0f) {
            qw = -qw;
            qx = -qx;
            qy = -qy;
            qz = -qz;
        }
        // Вектор поворота (ось × угол), для малых углов ≈ 2·(x, y, z).
        // Вращение в плоскости экрана (вокруг Z) не используется — как и в аналогах.
        float angleX = 2f * qx, angleY = 2f * qy;

        // Оси экрана в координатах устройства с учётом поворота дисплея
        float upX, upY, rightX, rightY;
        switch (displayRotation) {
            case 1: upX = -1f; upY = 0f; rightX = 0f; rightY = 1f; break;   // ROTATION_90
            case 2: upX = 0f; upY = -1f; rightX = -1f; rightY = 0f; break;  // ROTATION_180
            case 3: upX = 1f; upY = 0f; rightX = 0f; rightY = -1f; break;   // ROTATION_270
            default: upX = 0f; upY = 1f; rightX = 1f; rightY = 0f; break;   // ROTATION_0
        }
        // Поворот вокруг вертикали экрана (влево/вправо) двигает точки по X,
        // вокруг горизонтали (наклон вверх/вниз) — по Y. Точки остаются «на месте в мире».
        float aroundUp = angleX * upX + angleY * upY;
        float aroundRight = angleX * rightX + angleY * rightY;
        float gain = sensitivity / FULL_SCALE_RAD;
        float targetX = (float) Math.tanh(aroundUp * gain);
        float targetY = (float) Math.tanh(aroundRight * gain);
        packedTarget = AccelerationEstimator.pack(targetX, targetY);

        // Опора плавно догоняет текущую ориентацию — точки возвращаются в центр
        float a = AccelerationEstimator.alpha(dt, RETURN_TAU_S);
        float dot = reference[0] * w + reference[1] * x + reference[2] * y + reference[3] * z;
        float sign = dot < 0f ? -1f : 1f;
        set(reference,
                reference[0] + (sign * w - reference[0]) * a,
                reference[1] + (sign * x - reference[1]) * a,
                reference[2] + (sign * y - reference[2]) * a,
                reference[3] + (sign * z - reference[3]) * a);
        normalize(reference);
    }

    public float targetX() {
        return Float.intBitsToFloat((int) (packedTarget >>> 32));
    }

    public float targetY() {
        return Float.intBitsToFloat((int) packedTarget);
    }

    private static void set(float[] q, float w, float x, float y, float z) {
        q[0] = w;
        q[1] = x;
        q[2] = y;
        q[3] = z;
    }

    private static void normalize(float[] q) {
        float n = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        if (n > 0f) {
            for (int i = 0; i < 4; i++) {
                q[i] /= n;
            }
        }
    }
}
