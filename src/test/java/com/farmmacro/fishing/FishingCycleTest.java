package com.farmmacro.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class FishingCycleTest {
    private static final int HOOK = 42;

    private final FishingSettings c = new FishingSettings();
    private final FishingCycle cycle = new FishingCycle(new SplittableRandom(5));
    private long now;

    private FishingCycle.Action step(int hook, boolean bite) {
        now += 50;
        return cycle.tick(now, hook, bite, c);
    }

    @Test
    void biteReelAndRecastRunInOrder() {
        cycle.start(now);
        assertEquals(FishingCycle.Stage.WAIT_HOOK, cycle.stage());
        assertEquals(FishingCycle.Action.NONE, step(-1, false));
        assertEquals(FishingCycle.Action.NONE, step(HOOK, false));
        assertEquals(FishingCycle.Stage.WAIT_BITE, cycle.stage());
        assertEquals(FishingCycle.Action.NONE, step(HOOK, true));
        assertEquals(FishingCycle.Stage.REEL_DELAY, cycle.stage());
        FishingCycle.Action action = FishingCycle.Action.NONE;
        for (int i = 0; i < 100 && action == FishingCycle.Action.NONE; i++) action = step(HOOK, true);
        assertEquals(FishingCycle.Action.REEL, action);
        assertEquals(HOOK, cycle.expectedHook());
        assertEquals(1, cycle.reeled());
        assertEquals(FishingCycle.Action.NONE, step(-1, false));
        assertEquals(FishingCycle.Stage.RECAST_DELAY, cycle.stage());
        for (int i = 0; i < 200 && action != FishingCycle.Action.CAST; i++) action = step(-1, false);
        assertEquals(FishingCycle.Action.CAST, action);
        assertEquals(1, cycle.casts());
    }

    @Test
    void lostCastTimesOutInsteadOfLooping() {
        cycle.start(now);
        step(HOOK, false);
        step(HOOK, true);
        FishingCycle.Action action = FishingCycle.Action.NONE;
        for (int i = 0; i < 100 && action != FishingCycle.Action.REEL; i++) action = step(HOOK, true);
        for (int i = 0; i < 200 && action != FishingCycle.Action.CAST; i++) action = step(-1, false);
        assertEquals(FishingCycle.Action.CAST, action);
        for (int i = 0; i < 200 && cycle.active(); i++) assertEquals(FishingCycle.Action.NONE, step(-1, false));
        assertFalse(cycle.active());
        assertEquals(1, cycle.casts());
    }

    @Test
    void timeJumpStopsTheCycle() {
        cycle.start(now);
        now += 5000;
        assertEquals(FishingCycle.Action.NONE, cycle.tick(now, -1, false, c));
        assertFalse(cycle.active());
    }

    @Test
    void messagesStripFormattingAndMatchBundledList() {
        assertEquals("Hello", FishingMessages.clean("§aHello§r "));
        assertEquals("", FishingMessages.clean(null));
        FishingMessages.load();
        assertTrue(FishingMessages.size() > 0);
        assertTrue(FishingMessages.creature("§bYou caught a Sea Walker."));
        assertFalse(FishingMessages.creature("You caught a Cod."));
    }
}
