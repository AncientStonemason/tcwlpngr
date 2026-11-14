package com.farmmacro.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PathFinderKeyTest {
    @Test
    void keysDifferForDistinctOffsetsAndHeights() {
        assertNotEquals(PathFinder.packKey(1, 2, 64), PathFinder.packKey(2, 1, 64));
        assertNotEquals(PathFinder.packKey(1, 2, 64), PathFinder.packKey(1, 2, 65));
        assertNotEquals(PathFinder.packKey(-1, 0, 64), PathFinder.packKey(0, 0, 64));
        assertNotEquals(PathFinder.packKey(300_000, 0, 64), PathFinder.packKey(-300_000, 0, 64));
    }

    @Test
    void routeGoalsKeepSegmentEndBeforeDrop() {
        RoutePoint a = new RoutePoint(0, 64, 0), b = new RoutePoint(2, 64, 0), c = new RoutePoint(2, 60, 0);
        b.drop = true;
        List<PathFinder.Goal> goals = PathFinder.routeGoals(List.of(a, b, c), 64, -1, 0);
        assertTrue(goals.stream().anyMatch(g -> g.x == 2 && g.z == 0), "endpoint before drop must be kept");
    }

    @Test
    void routeGoalsSkipDuplicateJoin() {
        RoutePoint a = new RoutePoint(0, 64, 0), b = new RoutePoint(1, 64, 0), c = new RoutePoint(2, 64, 0);
        long atJoin = PathFinder.routeGoals(List.of(a, b, c), 64, -1, 0).stream()
                .filter(g -> g.x == 1)
                .count();
        assertEquals(1, atJoin);
    }
}
