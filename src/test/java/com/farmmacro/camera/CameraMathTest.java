package com.farmmacro.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class CameraMathTest {
    @Test
    void freelookIgnoresNonFiniteMouseAndClampsPitch() {
        float[] a = FreelookMath.turn(10f, 80f, Double.NaN, Double.POSITIVE_INFINITY);
        assertEquals(10f, a[0]);
        assertEquals(80f, a[1]);
        a = FreelookMath.turn(0f, 89f, 0, 1000);
        assertEquals(90f, a[1]);
    }

    @Test
    void mouseTurnFinishesDespiteNanFrameTime() {
        MouseTurn t = new MouseTurn(0, 0, 90, 10, 0.5, 360, 0, new SplittableRandom(1));
        t.frame(Double.NaN);
        assertFalse(t.finished());
        long y = 0;
        for (int i = 0; i < 1000 && !t.finished(); i++) y += t.frame(0.02)[0];
        assertTrue(t.finished());
        assertEquals(t.totalYaw, y);
    }

    @Test
    void mouseTurnZeroDtMakesNoProgress() {
        MouseTurn t = new MouseTurn(0, 0, 90, 0, 0.5, 180, 0, new SplittableRandom(2));
        long[] d = t.frame(0);
        assertEquals(0, d[0]);
        assertFalse(t.finished());
    }

    @Test
    void freecamStaysInsideRadiusAndRejectsNan() {
        var anchor = new FreecamMath.Point(0, 64, 0);
        var p = FreecamMath.step(anchor, anchor, 0, 1, 0, 0, 40, 100, 8);
        double len = Math.sqrt(p.x() * p.x() + (p.y() - 64) * (p.y() - 64) + p.z() * p.z());
        assertTrue(len <= 8.0001);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> FreecamMath.step(anchor, anchor, 0, 1, 0, 0, Double.NaN, .01, 8));
    }

    @Test
    void trackingTurnWrapsYawAcrossSeam() {
        assertEquals(-170, TrackingTurn.wrap(190), 1e-9);
        assertEquals(170, TrackingTurn.wrap(-190), 1e-9);
        TrackingTurn t = new TrackingTurn();
        double yaw = 170;
        for (int i = 0; i < 200; i++) yaw += t.frame(yaw, 0, -170, 0, 0.5, 180, 0.016).yaw();
        assertTrue(yaw > 170, "поворот пошёл к шву ±180, а не на 340° назад: " + yaw);
        assertTrue(Math.abs(TrackingTurn.wrap(yaw + 170)) < 1.0, "дошёл до цели через шов: " + yaw);
        var zero = t.frame(0, 0, 10, 0, 0.5, 180, 0);
        assertEquals(0, zero.yaw());
    }
}
