package com.motioncues.app.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

/**
 * Мир: X — восток, Y — север, Z — вверх. Машина едет на север.
 * Тождественный кватернион — телефон лежит экраном вверх, верх экрана смотрит вперёд.
 */
public class AccelerationEstimatorTest {

    private static final long STEP_NANOS = 20_000_000L; // 50 Гц
    private static final float[] FLAT = {0f, 0f, 0f, 1f};
    /** Телефон стоит вертикально лицом к водителю: поворот на 90° вокруг X. */
    private static final float[] UPRIGHT = {(float) Math.sqrt(0.5), 0f, 0f, (float) Math.sqrt(0.5)};

    private AccelerationEstimator estimator;
    private long now;

    @Before
    public void setUp() {
        estimator = new AccelerationEstimator();
        now = 1_000_000_000L;
    }

    @Test
    public void flatPhone_acceleration_movesDotsDown() {
        feed(FLAT, 0f, 2f, 0f, 1f);
        assertTrue("при разгоне точки уходят вниз: " + estimator.targetY(), estimator.targetY() > 0.3f);
        assertEquals(0f, estimator.targetX(), 0.01f);
    }

    @Test
    public void flatPhone_braking_movesDotsUp() {
        feed(FLAT, 0f, -2f, 0f, 1f);
        assertTrue(estimator.targetY() < -0.3f);
        assertEquals(0f, estimator.targetX(), 0.01f);
    }

    @Test
    public void flatPhone_leftTurn_movesDotsRight() {
        // В левом повороте центростремительное ускорение направлено влево (на запад)
        feed(FLAT, -2f, 0f, 0f, 1f);
        assertTrue("в левом повороте точки уходят вправо: " + estimator.targetX(), estimator.targetX() > 0.3f);
        assertEquals(0f, estimator.targetY(), 0.01f);
    }

    @Test
    public void uprightPhone_acceleration_movesDotsDown() {
        // У стоящего телефона «вперёд» (север) — это направление в экран, −Z устройства
        feed(UPRIGHT, 0f, 0f, -2f, 1f);
        assertTrue(estimator.targetY() > 0.3f);
        assertEquals(0f, estimator.targetX(), 0.01f);
    }

    @Test
    public void uprightPhone_verticalBumps_areIgnored() {
        // Кочки — вертикальное ускорение, у стоящего телефона это +Y устройства
        feed(UPRIGHT, 0f, 3f, 0f, 1f);
        assertEquals(0f, estimator.targetX(), 0.01f);
        assertEquals(0f, estimator.targetY(), 0.01f);
    }

    @Test
    public void tiltedPhone_acceleration_movesDotsDown() {
        // Наклон на 45° — как в держателе на торпедо
        float s = (float) Math.sin(Math.PI / 8), c = (float) Math.cos(Math.PI / 8);
        float[] tilted = {s, 0f, 0f, c};
        // Ускорение на север в координатах устройства: R^T · (0, 2, 0)
        float h = (float) Math.sqrt(0.5);
        feed(tilted, 0f, 2f * h, -2f * h, 1f);
        assertTrue(estimator.targetY() > 0.3f);
        assertEquals(0f, estimator.targetX(), 0.01f);
    }

    @Test
    public void landscape_axesFollowDisplayRotation() {
        // ROTATION_90: верх экрана — это −X устройства, право экрана — +Y
        estimator.setDisplayRotation(1);
        feed(FLAT, -2f, 0f, 0f, 1f);
        assertTrue("разгон вдоль верха экрана: " + estimator.targetY(), estimator.targetY() > 0.3f);
        assertEquals(0f, estimator.targetX(), 0.01f);

        estimator.reset();
        feed(FLAT, 0f, 2f, 0f, 1f);
        assertTrue("ускорение вправо по экрану уводит точки влево", estimator.targetX() < -0.3f);
        assertEquals(0f, estimator.targetY(), 0.01f);
    }

    @Test
    public void rawAccelerometerWithGravity_givesSameResult() {
        feed(FLAT, 0f, 2f, 0f, 1f);
        float linear = estimator.targetY();

        estimator.reset();
        feed(FLAT, 0f, 2f, 9.81f, 1f);
        assertEquals(linear, estimator.targetY(), 1e-4f);
    }

    @Test
    public void smallNoise_isSuppressed() {
        Random random = new Random(42);
        feedRotation(FLAT);
        for (int i = 0; i < 500; i++) {
            float nx = (random.nextFloat() - 0.5f) * 0.2f;
            float ny = (random.nextFloat() - 0.5f) * 0.2f;
            estimator.onAcceleration(nx, ny, 0f, tick());
            assertTrue(Math.abs(estimator.targetX()) < 0.02f);
            assertTrue(Math.abs(estimator.targetY()) < 0.02f);
        }
    }

    @Test
    public void constantSensorBias_decaysToZero() {
        feed(FLAT, 0.6f, 0.6f, 0f, 60f);
        assertEquals(0f, estimator.targetX(), 0.01f);
        assertEquals(0f, estimator.targetY(), 0.01f);
    }

    @Test
    public void handlingThePhone_suppressesCues() {
        // Телефон крутят в руке со скоростью 4 рад/с и одновременно трясут
        feedRotation(FLAT);
        double angle = 0;
        for (int i = 0; i < 50; i++) {
            angle += 4.0 * STEP_NANOS * 1e-9;
            float half = (float) (angle / 2);
            long t = tick();
            estimator.onRotationVector(new float[]{0f, 0f, (float) Math.sin(half), (float) Math.cos(half)}, t);
            estimator.onAcceleration(0f, 3f, 0f, t + 1);
        }
        assertEquals(0f, estimator.handlingAttenuation(), 1e-3f);
        assertEquals(0f, estimator.targetY(), 0.01f);
    }

    @Test
    public void carYawRate_doesNotSuppressCues() {
        // Машина в повороте вращается медленно (~0.5 рад/с) — эффект должен работать
        feedRotation(FLAT);
        double angle = 0;
        for (int i = 0; i < 50; i++) {
            angle += 0.5 * STEP_NANOS * 1e-9;
            float half = (float) (angle / 2);
            long t = tick();
            estimator.onRotationVector(new float[]{0f, 0f, (float) Math.sin(half), (float) Math.cos(half)}, t);
        }
        assertEquals(1f, estimator.handlingAttenuation(), 1e-3f);
    }

    @Test
    public void sensitivity_scalesResponse() {
        feed(FLAT, 0f, 1f, 0f, 1f);
        float normal = estimator.targetY();

        estimator.reset();
        estimator.setSensitivity(2f);
        feed(FLAT, 0f, 1f, 0f, 1f);
        assertTrue(estimator.targetY() > normal * 1.5f);
    }

    @Test
    public void strongAcceleration_staysWithinBounds() {
        feed(FLAT, 50f, -50f, 0f, 1f);
        assertTrue(estimator.targetX() >= -1f && estimator.targetX() < -0.99f);
        assertTrue(estimator.targetY() <= 1f && estimator.targetY() < -0.99f);
    }

    @Test
    public void softDeadZone_isContinuous() {
        float below = AccelerationEstimator.softDeadZone(AccelerationEstimator.DEAD_ZONE - 1e-4f);
        float above = AccelerationEstimator.softDeadZone(AccelerationEstimator.DEAD_ZONE + 1e-4f);
        assertEquals(below, above, 1e-3f);
        assertEquals(0f, AccelerationEstimator.softDeadZone(0f), 0f);
        assertEquals(-AccelerationEstimator.softDeadZone(1f), AccelerationEstimator.softDeadZone(-1f), 0f);
    }

    @Test
    public void quaternionToMatrix_rotatesAxes() {
        float[] m = new float[9];
        AccelerationEstimator.quaternionToMatrix(UPRIGHT[3], UPRIGHT[0], UPRIGHT[1], UPRIGHT[2], m);
        // Y устройства → вверх мира, Z устройства → юг
        assertEquals(1f, m[7], 1e-5f);
        assertEquals(-1f, m[5], 1e-5f);
        assertEquals(1f, m[0], 1e-5f);
    }

    private void feed(float[] rotation, float ax, float ay, float az, float seconds) {
        feedRotation(rotation);
        int samples = Math.round(seconds * 1e9f / STEP_NANOS);
        for (int i = 0; i < samples; i++) {
            estimator.onAcceleration(ax, ay, az, tick());
        }
    }

    private void feedRotation(float[] rotation) {
        estimator.onRotationVector(rotation, tick());
    }

    private long tick() {
        now += STEP_NANOS;
        return now;
    }
}
