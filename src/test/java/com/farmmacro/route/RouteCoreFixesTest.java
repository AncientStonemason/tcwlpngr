package com.farmmacro.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class RouteCoreFixesTest {
    private static PointRoute route(RoutePoint... pts) {
        PointRoute r = new PointRoute();
        r.points.addAll(List.of(pts));
        r.sanitize();
        return r;
    }

    @Test
    void expectedYAfterDropUsesLowerFloor() {
        RoutePoint p0 = new RoutePoint(0.5, 70, 0.5), p1 = new RoutePoint(5.5, 70, 0.5), p2 = new RoutePoint(10.5, 66, 0.5);
        p1.drop = true;
        p1.dropDir = "+x";
        WalkCore core = new WalkCore();
        WalkCore.Out out = new WalkCore.Out();
        core.start(route(p0, p1, p2), 0, 20);
        core.tick(0.5, 70, 0.5, true, 0f, 0.3, false, 20, false, 3, false, out); // точка 1
        core.tick(5.5, 70, 0.5, true, 0f, 0.3, false, 20, false, 3, false, out); // точка-спуск, фаза «край»
        core.tick(5.9, 69, 0.5, false, 0f, 0.3, false, 20, false, 3, false, out); // полёт
        core.tick(6.0, 66, 0.5, true, 0f, 0.3, false, 20, false, 3, false, out); // приземление
        assertEquals(WalkCore.D_NONE, core.dropPhase());
        assertEquals(2, core.target());
        assertEquals(66, core.expectedY(7.0, 0.5), 1e-9);
    }

    @Test
    void expectedYOnPlainSegmentInterpolates() {
        WalkCore core = new WalkCore();
        WalkCore.Out out = new WalkCore.Out();
        core.start(route(new RoutePoint(0.5, 64, 0.5), new RoutePoint(10.5, 65, 0.5)), 0, 20);
        core.tick(0.5, 64, 0.5, true, 0f, 0.3, false, 20, false, 3, false, out);
        assertEquals(64.5, core.expectedY(5.5, 0.5), 1e-9);
    }

    @Test
    void mirroringTwiceRestoresPointsAndYaw() {
        PointRoute r = route(new RoutePoint(0.5, 64, 0.5), new RoutePoint(10.5, 64, 3.5));
        r.points.get(1).dropDir = "+x";
        r.settings.farm.yaw = 90;
        PointRoute once = RouteCopies.copy(r, 0, 0, 0, true, false);
        assertEquals(-9.5, once.points.get(1).x, 1e-9);
        assertEquals("-x", once.points.get(1).dropDir);
        assertEquals(-90, once.settings.farm.yaw, 1e-9);
        PointRoute twice = RouteCopies.copy(once, 0, 0, 0, true, false);
        assertEquals(10.5, twice.points.get(1).x, 1e-9);
        assertEquals("+x", twice.points.get(1).dropDir);
        assertEquals(90, twice.settings.farm.yaw, 1e-9);
    }

    @Test
    void pointActionSanitizeFixesBrokenFileValues() {
        PointAction a = new PointAction();
        a.type = null;
        a.slot = 99;
        a.command = null;
        a.sanitize();
        assertFalse(a.active());
        assertEquals(9, a.slot);
        assertEquals("", a.command);
    }
}
