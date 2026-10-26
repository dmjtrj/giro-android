package com.motioncues.app.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/** Режим «Гироскоп»: точки привязаны к миру и отвечают на поворот и наклон телефона. */
public class RotationEstimatorTest {

    private static final long STEP_NANOS = 20_000_000L; // 50 Гц

    private RotationEstimator estimator;
    private long now;

    @Before
    public void setUp() {
        estimator = new RotationEstimator();
        now = 1_000_000_000L;
        feed(0f, 0f, 0f); // опорная ориентация
    }

    @Test
    public void turningPhoneLeft_movesDotsRight() {
        // Поворот вокруг вертикали экрана (Y устройства) на +6°: телефон «смотрит» левее,
        // мир на экране уезжает вправо
        feed(0f, (float) Math.toRadians(6), 0f);
        assertTrue("точки вправо: " + estimator.targetX(), estimator.targetX() > 0.3f);
        assertEquals(0f, estimator.targetY(), 0.02f);
    }

    @Test
    public void tiltingPhoneUp_movesDotsDown() {
        feed((float) Math.toRadians(6), 0f, 0f);
        assertTrue("точки вниз: " + estimator.targetY(), estimator.targetY() > 0.3f);
        assertEquals(0f, estimator.targetX(), 0.02f);
    }

    @Test
    public void smallRotation_alreadyClearlyVisible() {
        // «Бегают от малейшего поворота»: 2° дают заметное смещение
        feed(0f, (float) Math.toRadians(2), 0f);
        assertTrue(estimator.targetX() > 0.15f);
    }

    @Test
    public void rotationInScreenPlane_isIgnored() {
        feed(0f, 0f, (float) Math.toRadians(20));
        assertEquals(0f, estimator.targetX(), 0.02f);
        assertEquals(0f, estimator.targetY(), 0.02f);
    }

    @Test
    public void heldStill_dotsReturnToCenter() {
        float angle = (float) Math.toRadians(8);
        feed(0f, angle, 0f);
        float right = estimator.targetX();
        for (int i = 0; i < 300; i++) { // 6 секунд неподвижно в новом положении
            feed(0f, angle, 0f);
        }
        assertTrue(right > 0.4f);
        assertEquals(0f, estimator.targetX(), 0.02f);
    }

    @Test
    public void landscape_mapsAxesToScreen() {
        // ROTATION_90: вертикаль экрана — это −X устройства
        estimator.setDisplayRotation(1);
        estimator.reset();
        feed(0f, 0f, 0f);
        feed(-(float) Math.toRadians(6), 0f, 0f);
        assertTrue(estimator.targetX() > 0.3f);
        assertEquals(0f, estimator.targetY(), 0.02f);
    }

    @Test
    public void sensitivity_scalesResponse() {
        feed(0f, (float) Math.toRadians(2), 0f);
        float normal = estimator.targetX();
        estimator.reset();
        estimator.setSensitivity(1.7f);
        feed(0f, 0f, 0f);
        feed(0f, (float) Math.toRadians(2), 0f);
        assertTrue(estimator.targetX() > normal * 1.4f);
    }

    /** Ориентация как поворот на углы вокруг осей X, Y, Z устройства (малые углы, последовательно). */
    private void feed(float ax, float ay, float az) {
        float angle = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        float s = angle > 0f ? (float) Math.sin(angle / 2) / angle : 0f;
        now += STEP_NANOS;
        estimator.onRotationVector(new float[]{ax * s, ay * s, az * s, (float) Math.cos(angle / 2)}, now);
    }
}
