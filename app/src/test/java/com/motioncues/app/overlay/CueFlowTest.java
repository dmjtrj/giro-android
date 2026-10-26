package com.motioncues.app.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class CueFlowTest {

    private static final float TRAVEL = 105f;
    private static final float PERIOD_X = 116f;
    private static final float PERIOD_Y = 252f;

    private CueFlow flow;

    @Before
    public void setUp() {
        // Движок нужен только для шага по датчикам; здесь проверяем само смещение поля
        flow = new CueFlow(null);
        flow.configure(TRAVEL, PERIOD_X, PERIOD_Y);
    }

    @Test
    public void noSignal_fieldAtRest() {
        flow.apply(0f, 0f);
        assertEquals(0f, flow.fieldX(), 0f);
        assertEquals(0f, flow.fieldY(), 0f);
    }

    @Test
    public void displacement_isProportionalToSignal() {
        flow.apply(0f, 0.4f);
        assertEquals(TRAVEL * 0.4f, flow.fieldY(), 1e-3f);
    }

    @Test
    public void fieldReturns_whenSignalGoesBack() {
        flow.apply(0.8f, -0.6f);
        flow.apply(0f, 0f);
        assertEquals(0f, flow.fieldX(), 0f);
        assertEquals(0f, flow.fieldY(), 0f);
    }

    @Test
    public void negativeDisplacement_wrapsIntoPeriod() {
        // Сдвиг вверх на 0.5·105 эквивалентен сдвигу вниз на период минус это значение
        flow.apply(0f, -0.5f);
        assertEquals(PERIOD_Y - TRAVEL * 0.5f, flow.fieldY(), 1e-3f);
    }

    @Test
    public void strongSignal_movesMoreThanHalfStep() {
        // Иначе точки никогда не дойдут до границы окна и не «прорастут» с другой стороны
        flow.apply(1f, 1f);
        assertEquals(TRAVEL, flow.fieldY(), 1e-3f);
        assertTrue(TRAVEL > PERIOD_X / 4f);
    }
}
