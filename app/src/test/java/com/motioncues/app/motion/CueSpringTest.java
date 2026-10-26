package com.motioncues.app.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class CueSpringTest {

    private static final float FRAME_120HZ = 1f / 120f;

    private CueSpring spring;
    private CueState state;

    @Before
    public void setUp() {
        spring = new CueSpring();
        state = new CueState();
    }

    @Test
    public void reachesTarget_withoutNoticeableOvershoot() {
        float peak = 0f;
        for (int i = 0; i < 240; i++) {
            spring.step(FRAME_120HZ, 0.5f, -0.5f, state);
            peak = Math.max(peak, state.offsetX);
        }
        assertEquals(0.5f, state.offsetX, 0.005f);
        assertEquals(-0.5f, state.offsetY, 0.005f);
        assertTrue("перелёт не больше 5%: " + peak, peak < 0.5f * 1.05f);
    }

    @Test
    public void movesSmoothly_noJumpOnFirstFrame() {
        spring.step(FRAME_120HZ, 1f, 0f, state);
        assertTrue("первый кадр сдвигается чуть-чуть: " + state.offsetX, state.offsetX < 0.01f);
    }

    @Test
    public void sameResult_at60And120Hz() {
        CueSpring other = new CueSpring();
        CueState otherState = new CueState();
        for (int i = 0; i < 60; i++) {
            spring.step(1f / 60f, 0.7f, 0f, state);
            other.step(FRAME_120HZ, 0.7f, 0f, otherState);
            other.step(FRAME_120HZ, 0.7f, 0f, otherState);
            assertEquals(state.offsetX, otherState.offsetX, 0.002f);
        }
    }

    @Test
    public void longFramePause_staysStable() {
        spring.step(5f, 1f, 1f, state);
        assertTrue(Math.abs(state.offsetX) <= 1.1f);
        for (int i = 0; i < 240; i++) {
            spring.step(FRAME_120HZ, 1f, 1f, state);
        }
        assertEquals(1f, state.offsetX, 0.01f);
    }

    @Test
    public void activity_risesQuickly_andFadesSlowly() {
        for (int i = 0; i < 60; i++) {
            spring.step(FRAME_120HZ, 0.5f, 0f, state);
        }
        assertTrue("за 0.5 с точки почти проявились: " + state.activity, state.activity > 0.9f);

        for (int i = 0; i < 60; i++) {
            spring.step(FRAME_120HZ, 0f, 0f, state);
        }
        assertTrue("через 0.5 с ровного хода ещё видны: " + state.activity, state.activity > 0.6f);

        for (int i = 0; i < 1200; i++) {
            spring.step(FRAME_120HZ, 0f, 0f, state);
        }
        assertEquals(0f, state.activity, 0.01f);
    }

    @Test
    public void zeroDt_changesNothing() {
        spring.step(0f, 1f, 1f, state);
        assertEquals(0f, state.offsetX, 0f);
        assertEquals(0f, state.activity, 0f);
    }
}
