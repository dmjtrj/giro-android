package com.motioncues.app.motion;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

/**
 * Связывает датчики Android с движком: показания читаются в отдельном потоке,
 * а точки шагаются в потоке отрисовки через {@link #step(long, CueState)}.
 *
 * <p>Использование: {@link #start()} → на каждом кадре {@link #step} → {@link #stop()}.
 */
public final class MotionEngine {

    /** ~50 Гц: достаточно для плавности, физика всё равно шагает с частотой экрана. */
    private static final int SAMPLING_PERIOD_US = 20_000;

    private final SensorManager sensorManager;
    private final Sensor accelerationSensor;
    private final Sensor rotationSensor;
    private final AccelerationEstimator estimator = new AccelerationEstimator();
    private final CueSpring spring = new CueSpring();

    private HandlerThread sensorThread;
    private long lastFrameNanos;

    private final SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            int type = event.sensor.getType();
            if (type == Sensor.TYPE_LINEAR_ACCELERATION || type == Sensor.TYPE_ACCELEROMETER) {
                estimator.onAcceleration(event.values[0], event.values[1], event.values[2], event.timestamp);
            } else {
                estimator.onRotationVector(event.values, event.timestamp);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

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
        return sensorThread != null;
    }

    public void start() {
        if (isRunning() || !isSupported()) {
            return;
        }
        estimator.reset();
        spring.reset();
        lastFrameNanos = 0L;

        sensorThread = new HandlerThread("MotionSensors", Process.THREAD_PRIORITY_DISPLAY);
        sensorThread.start();
        Handler handler = new Handler(sensorThread.getLooper());
        sensorManager.registerListener(listener, rotationSensor, SAMPLING_PERIOD_US, handler);
        sensorManager.registerListener(listener, accelerationSensor, SAMPLING_PERIOD_US, handler);
    }

    public void stop() {
        if (!isRunning()) {
            return;
        }
        sensorManager.unregisterListener(listener);
        sensorThread.quitSafely();
        sensorThread = null;
    }

    /** Поворот дисплея ({@code Surface.ROTATION_*}) — вызывать при смене ориентации. */
    public void setDisplayRotation(int rotation) {
        estimator.setDisplayRotation(rotation);
    }

    public void setSensitivity(float sensitivity) {
        estimator.setSensitivity(sensitivity);
    }

    /**
     * Шаг физики на кадр отрисовки.
     *
     * @param frameTimeNanos время кадра из {@code Choreographer.FrameCallback}
     */
    public void step(long frameTimeNanos, CueState out) {
        float dt = lastFrameNanos == 0L ? 0f : (frameTimeNanos - lastFrameNanos) * 1e-9f;
        lastFrameNanos = frameTimeNanos;
        spring.step(dt, estimator.targetX(), estimator.targetY(), out);
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
