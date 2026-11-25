package com.motioncues.app.motion;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import com.motioncues.app.settings.MotionMode;

/**
 * Связывает датчики Android с движком: показания читаются в отдельном потоке,
 * а точки шагаются в потоке отрисовки через {@link #step(long, CueState)}.
 *
 * <p>Использование: {@link #start()} → на каждом кадре {@link #step} → {@link #stop()}.
 *
 * <p>Подписка на датчики и отписка — синхронные вызовы сенсорной службы, на Pixel 8
 * до 40 мс. Поэтому они идут в потоке датчиков, а {@link #start()} и {@link #stop()}
 * возвращаются сразу — кнопка и анимации в главном потоке не дёргаются.
 */
public final class MotionEngine {

    /** ~50 Гц: достаточно для плавности, физика всё равно шагает с частотой экрана. */
    private static final int SAMPLING_PERIOD_US = 20_000;

    private final SensorManager sensorManager;
    private final Sensor accelerationSensor;
    private final Sensor rotationSensor;
    private final CueSpring spring = new CueSpring();
    /** Выбранный режим; оба оценщика получают данные всегда — переключение без скачка. */
    private volatile MotionMode mode = MotionMode.INERTIA;
    private float sensitivity = 1f;
    private int displayRotation;

    /** Текущий запуск; {@code null}, пока движок остановлен. Только главный поток. */
    private Session session;
    private long lastFrameNanos;

    /**
     * Один запуск движка: свой поток датчиков и свои оценщики. Отписка старого запуска
     * может ещё идти, когда стартует новый, — общих данных у них нет, гонок тоже.
     */
    private final class Session implements SensorEventListener {
        final AccelerationEstimator estimator = new AccelerationEstimator();
        final RotationEstimator rotationEstimator = new RotationEstimator();
        final HandlerThread thread = new HandlerThread("MotionSensors", Process.THREAD_PRIORITY_DISPLAY);
        Handler handler;

        @Override
        public void onSensorChanged(SensorEvent event) {
            int type = event.sensor.getType();
            if (type == Sensor.TYPE_LINEAR_ACCELERATION || type == Sensor.TYPE_ACCELEROMETER) {
                estimator.onAcceleration(event.values[0], event.values[1], event.values[2], event.timestamp);
            } else {
                estimator.onRotationVector(event.values, event.timestamp);
                rotationEstimator.onRotationVector(event.values, event.timestamp);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }

    public MotionEngine(Context context) {
        sensorManager = context.getSystemService(SensorManager.class);
        accelerationSensor = firstAvailable(Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER);
        // Игровой вектор не использует магнитометр — не дёргается рядом с металлом машины
        rotationSensor = firstAvailable(Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR);
    }

    /** Есть ли на устройстве датчики, без которых эффект невозможен. */
    public boolean isSupported() {
        return accelerationSensor != null && rotationSensor != null;
    }

    public boolean isRunning() {
        return session != null;
    }

    public void start() {
        if (isRunning() || !isSupported()) {
            return;
        }
        spring.reset();
        lastFrameNanos = 0L;

        Session s = new Session();
        s.estimator.setSensitivity(sensitivity);
        s.rotationEstimator.setSensitivity(sensitivity);
        s.estimator.setDisplayRotation(displayRotation);
        s.rotationEstimator.setDisplayRotation(displayRotation);
        s.thread.start();
        s.handler = new Handler(s.thread.getLooper());
        s.handler.post(() -> {
            sensorManager.registerListener(s, rotationSensor, SAMPLING_PERIOD_US, s.handler);
            sensorManager.registerListener(s, accelerationSensor, SAMPLING_PERIOD_US, s.handler);
        });
        session = s;
    }

    public void stop() {
        Session s = session;
        if (s == null) {
            return;
        }
        session = null;
        // Отписка в том же потоке, после подписки; затем поток завершается сам
        s.handler.post(() -> {
            sensorManager.unregisterListener(s);
            s.thread.quitSafely();
        });
    }

    /** Поворот дисплея ({@code Surface.ROTATION_*}) — вызывать при смене ориентации. */
    public void setDisplayRotation(int rotation) {
        displayRotation = rotation;
        Session s = session;
        if (s != null) {
            s.estimator.setDisplayRotation(rotation);
            s.rotationEstimator.setDisplayRotation(rotation);
        }
    }

    public void setSensitivity(float sensitivity) {
        this.sensitivity = sensitivity;
        Session s = session;
        if (s != null) {
            s.estimator.setSensitivity(sensitivity);
            s.rotationEstimator.setSensitivity(sensitivity);
        }
    }

    /** «Инерция» (как в iOS) или «Гироскоп» (как в большинстве приложений для Android). */
    public void setMode(MotionMode mode) {
        this.mode = mode;
    }

    /**
     * Шаг физики на кадр отрисовки.
     *
     * @param frameTimeNanos время кадра из {@code Choreographer.FrameCallback}
     */
    public void step(long frameTimeNanos, CueState out) {
        float dt = lastFrameNanos == 0L ? 0f : (frameTimeNanos - lastFrameNanos) * 1e-9f;
        lastFrameNanos = frameTimeNanos;
        Session s = session;
        float targetX = 0f, targetY = 0f;
        if (s != null) {
            if (mode == MotionMode.GYROSCOPE) {
                targetX = s.rotationEstimator.targetX();
                targetY = s.rotationEstimator.targetY();
            } else {
                targetX = s.estimator.targetX();
                targetY = s.estimator.targetY();
            }
        }
        spring.step(dt, targetX, targetY, out);
    }

    private Sensor firstAvailable(int... types) {
        for (int type : types) {
            Sensor sensor = sensorManager.getDefaultSensor(type);
            if (sensor != null) {
                return sensor;
            }
        }
        return null;
    }
}
