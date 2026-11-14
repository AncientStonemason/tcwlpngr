package com.farmmacro.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class HoldAndGateTest {
    @Test
    void holdSanitizeRepairsBrokenValues() {
        HoldSettings h = new HoldSettings();
        h.button = "bogus";
        h.mode = "bogus";
        h.delayTicks = -5;
        h.ranges = new ArrayList<>();
        h.ranges.add(null);
        h.ranges.add(new HoldSettings.Range(9, 3));
        HoldSettings.sanitize(h);
        assertFalse(h.active());
        assertEquals(HoldSettings.WHOLE, h.mode);
        assertEquals(0, h.delayTicks);
        assertEquals(1, h.ranges.size());
        assertEquals(3, h.ranges.get(0).from);
        assertEquals(9, h.ranges.get(0).to);
    }

    @Test
    void holdTolerantOfNullRanges() {
        HoldSettings h = new HoldSettings();
        h.ranges = null;
        assertFalse(h.inRange(0));
        assertTrue(h.copy().ranges.isEmpty());
        h.sort();
        assertTrue(h.ranges.isEmpty());
    }

    @Test
    void holdWaitsForDelayThenFollowsRanges() {
        HoldSettings h = new HoldSettings();
        h.button = HoldSettings.ATTACK;
        h.mode = HoldSettings.POINTS;
        h.delayTicks = 2;
        h.ranges.add(new HoldSettings.Range(5, 7));
        assertFalse(h.wants(true, 2, false, 6, false));
        assertTrue(h.wants(true, 3, false, 6, false));
        assertFalse(h.wants(true, 3, false, 8, false));
        assertFalse(h.wants(false, 3, false, 6, false));
        assertTrue(h.wants(true, 3, true, 0, true));
    }

    @Test
    void buttonGateAppliesDelayAndCancelsOnRevert() {
        ActionGate.Button b = new ActionGate.Button();
        assertFalse(b.update(true, 1, () -> 2));
        assertFalse(b.update(true, 2, () -> 2));
        assertTrue(b.update(true, 3, () -> 2));
        // передумали до срока — отпускание не зависает
        assertTrue(b.update(false, 4, () -> 3));
        assertTrue(b.update(true, 5, () -> 3));
        assertFalse(b.update(false, 6, () -> 0));
    }

    @Test
    void buttonGateResetIsImmediate() {
        ActionGate.Button b = new ActionGate.Button();
        b.update(true, 1, () -> 50);
        b.reset(false);
        assertFalse(b.out());
        assertEquals(0, b.left());
    }

    @Test
    void sameTickCallsDoNotSpeedUpDelay() {
        ActionGate.Button b = new ActionGate.Button();
        b.update(true, 1, () -> 1);
        assertFalse(b.update(true, 1, () -> 1));
        assertTrue(b.update(true, 2, () -> 1));
    }
}
