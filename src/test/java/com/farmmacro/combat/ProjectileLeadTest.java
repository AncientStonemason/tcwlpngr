package com.farmmacro.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProjectileLeadTest {
    private static final double EPS = 1e-6;

    @Test
    void stationaryTargetNeedsNoLead() {
        var o = ProjectileLead.solve(10, 0, 0, 0, 0, 0, 40, 0, 1, 6);
        assertEquals(0, o.x(), EPS);
        assertEquals(0, o.y(), EPS);
        assertEquals(0, o.z(), EPS);
    }

    @Test
    void perpendicularMotionLeadsByFlightTime() {
        var o = ProjectileLead.solve(10, 0, 0, 0, 0, 5, 40, 0, 1, 6);
        double t = 10 / Math.sqrt(40 * 40 - 5 * 5);
        assertEquals(t, o.seconds(), 1e-4);
        assertEquals(5 * t, o.z(), 1e-4);
    }

    @Test
    void leadIsCappedByTimeAndDistance() {
        var timeCapped = ProjectileLead.solve(30, 0, 0, 0, 0, 8, 40, 0, .3, 6);
        assertEquals(.3, timeCapped.seconds(), EPS);
        assertEquals(2.4, timeCapped.z(), EPS);
        var distanceCapped = ProjectileLead.solve(30, 0, 0, 0, 0, 8, 40, 0, 1, 1);
        assertEquals(1, Math.hypot(distanceCapped.x(), distanceCapped.z()), 1e-4);
    }

    @Test
    void unsolvableOrInvalidInputGivesZeroOffset() {
        // Цель убегает быстрее снаряда: корни отрицательные.
        assertEquals(0, ProjectileLead.solve(10, 0, 0, 20, 0, 0, 10, 0, 1, 6).seconds(), EPS);
        assertEquals(0, ProjectileLead.solve(10, 0, 0, 0, 0, 5, 0, 0, 1, 6).seconds(), EPS);
        assertEquals(0, ProjectileLead.solve(10, 0, 0, 0, 0, Double.NaN, 40, 0, 1, 6).seconds(), EPS);
        assertEquals(0, ProjectileLead.solve(10, 0, 0, 0, 0, 30, 40, 0, 1, 6).seconds(), EPS);
        assertEquals(0, ProjectileLead.solve(0, 0, 0, 0, 0, 5, 40, 0, 1, 6).z(), EPS);
    }

    @Test
    void plausibleStepRejectsTeleportsAndBadTime() {
        assertTrue(ProjectileLead.plausibleStep(.5, 0, 0, .05));
        assertFalse(ProjectileLead.plausibleStep(5, 0, 0, .05));
        assertFalse(ProjectileLead.plausibleStep(0, 0, 0, 0));
        assertFalse(ProjectileLead.plausibleStep(0, 0, 0, Double.NaN));
        assertFalse(ProjectileLead.plausibleStep(0, 0, 0, 1));
    }
}
