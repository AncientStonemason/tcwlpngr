package com.farmmacro.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class AetherTurnTest {
    @Test
    void wrapStaysInHalfOpenRange() {
        assertEquals(-180, AetherTurn.wrap(180), 1e-9);
        assertEquals(-180, AetherTurn.wrap(-180), 1e-9);
        assertEquals(-180, AetherTurn.wrap(540), 1e-9);
        assertEquals(-170, AetherTurn.wrap(190), 1e-9);
        assertEquals(170, AetherTurn.wrap(-190), 1e-9);
        assertEquals(5, AetherTurn.wrap(725), 1e-9);
        assertEquals(5, AetherTurn.wrap(-355), 1e-9);
    }

    @Test
    void timedTurnTakesShortWayAcrossSeamAndArrives() {
        var s = new MotionSettings();
        var turn = new AetherTurn(-170, 0, 170, 10, false, s, new SplittableRandom(7));
        double yaw = -170, pitch = 0, maxStep = 0;
        for (int i = 0; i < 1200 && !turn.finished(); i++) {
            var d = turn.frame(yaw, pitch, 170, 10, .5, 720, 1 / 60.0);
            yaw += d.yaw();
            pitch += d.pitch();
            maxStep = Math.max(maxStep, Math.abs(d.yaw()));
            assertTrue(Math.abs(yaw) > 100 || Math.abs(yaw + 170) < 25 || Math.abs(yaw - 170) < 25, "ушёл длинным путём");
        }
        assertTrue(turn.finished());
        assertEquals(0, AetherTurn.wrap(170 - yaw), AetherTurn.tolerance(.5) + 1e-6);
        assertEquals(10, pitch, AetherTurn.tolerance(.5) + 1e-6);
        assertTrue(maxStep <= 720 * .06 + 1e-6, "шаг превысил бюджет скорости");
    }

    @Test
    void invalidInputFinishesWithoutMovementAndZeroDtWaits() {
        var s = new MotionSettings();
        var turn = new AetherTurn(0, 0, 90, 0, true, s, new SplittableRandom(1));
        var zero = turn.frame(0, 0, 90, 0, .5, 720, 0);
        assertEquals(0, zero.yaw(), 0);
        assertFalse(turn.finished());
        var bad = turn.frame(Double.NaN, 0, 90, 0, .5, 720, 1 / 60.0);
        assertEquals(0, bad.yaw(), 0);
        assertTrue(turn.finished());
    }

    @Test
    void trackingConvergesAndNeverLeavesPitchLimit() {
        var s = new MotionSettings();
        var turn = new AetherTurn(0, 0, 40, 85, true, s, new SplittableRandom(3));
        double yaw = 0, pitch = 0;
        for (int i = 0; i < 600; i++) {
            var d = turn.frame(yaw, pitch, 40, 120, .5, 720, 1 / 144.0);
            yaw += d.yaw();
            pitch += d.pitch();
            assertTrue(pitch <= 89 + 1e-6);
        }
        assertEquals(0, AetherTurn.wrap(40 - yaw), AetherTurn.tolerance(.5) + 1e-6);
        assertEquals(89, pitch, AetherTurn.tolerance(.5) + 1e-6);
    }
}
