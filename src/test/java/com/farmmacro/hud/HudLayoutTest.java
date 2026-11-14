package com.farmmacro.hud;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.farmmacro.config.HudSlot;
import com.farmmacro.hud.HudLayout.Item;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HudLayoutTest {
    private static final int[][] SCREENS = {{427, 240}, {960, 540}};

    /** Все 19 блоков редактора: пустые — компактные заглушки, четыре «живых» — высокие. */
    private static List<Item> editorItems() {
        List<Item> items = new ArrayList<>();
        String[] right = {
            "status",
            "editor",
            "hold",
            "mouselock",
            "view",
            "human",
            "return",
            "delays",
            "susp",
            "panic",
            "server",
            "nav",
            "fishing"
        };
        for (String id : right)
            items.add(new Item(id, id.equals("status") ? 132 : 100, id.equals("status") ? 40 : 13, 1));
        items.add(new Item("activity", 152, 44, 0));
        items.add(new Item("profit", 152, 56, 0));
        items.add(new Item("skill", 60, 13, 0));
        items.add(new Item("pet", 80, 13, 0));
        items.add(new Item("combat", 50, 13, 0));
        items.add(new Item("inventory", 244, 66, 2));
        return items;
    }

    private static boolean overlap(int[] a, Item ia, int[] b, Item ib) {
        return a[0] < b[0] + ib.w() && b[0] < a[0] + ia.w() && a[1] < b[1] + ib.h() && b[1] < a[1] + ia.h();
    }

    @Test
    void defaultFlowHasNoOverlapAndStaysOnScreen() {
        for (int[] s : SCREENS) {
            var items = editorItems();
            Map<String, int[]> pos = HudLayout.flow(items, s[0], s[1]);
            assertEquals(items.size(), pos.size());
            for (int i = 0; i < items.size(); i++) {
                var a = items.get(i);
                int[] pa = pos.get(a.id());
                assertTrue(pa[0] >= 0 && pa[1] >= 0, a.id());
                assertTrue(pa[0] + a.w() <= s[0] && pa[1] + a.h() <= s[1], a.id() + " off-screen " + s[0]);
                for (int j = i + 1; j < items.size(); j++) {
                    var b = items.get(j);
                    assertFalse(overlap(pa, a, pos.get(b.id()), b), a.id() + " x " + b.id() + " at " + s[0]);
                }
            }
        }
    }

    @Test
    void defaultFlowIsDeterministicAndReservesBottom() {
        var items = editorItems();
        Map<String, int[]> one = HudLayout.flow(items, 427, 240), two = HudLayout.flow(items, 427, 240);
        for (var it : items) assertArrayEquals(one.get(it.id()), two.get(it.id()));
        int[] inv = one.get("inventory");
        assertTrue(inv[1] + 66 <= 240 - HudLayout.BOTTOM_RESERVE);
    }

    @Test
    void fewBlocksKeepRightAndLeftSides() {
        Map<String, int[]> pos =
                HudLayout.flow(List.of(new Item("status", 132, 40, 1), new Item("activity", 152, 44, 0)), 960, 540);
        assertEquals(HudLayout.MARGIN, pos.get("activity")[0]);
        assertEquals(960 - HudLayout.MARGIN - 132, pos.get("status")[0]);
        assertEquals(HudLayout.MARGIN, pos.get("status")[1]);
    }

    @Test
    void placeClampsAfterResizeSoBlockStaysReachable() {
        var s = new HudSlot();
        s.ax = 1;
        s.ox = 400;
        s.ay = 1;
        s.oy = 300;
        int[] p = HudLayout.place(s, 150, 60, 300, 200);
        assertTrue(p[0] >= 0 && p[0] + 150 <= 300 && p[1] >= 0 && p[1] + 60 <= 200);
    }

    @Test
    void storeClampsOutOfRangePositions() {
        var s = new HudSlot();
        HudLayout.store(s, -50, 900, 100, 40, 427, 240);
        int[] p = HudLayout.place(s, 100, 40, 427, 240);
        assertEquals(0, p[0]);
        assertEquals(200, p[1]);
    }

    @Test
    void snapAxisPicksNearestTargetAndReportsGuide() {
        double[] t = HudLayout.targets(400, new double[] {100});
        var snap = HudLayout.snapAxis(97, 50, 400, t, 5);
        assertEquals(100, snap.pos());
        assertEquals(100, snap.guide());
        var centre = HudLayout.snapAxis(176, 50, 400, t, 5);
        assertEquals(175, centre.pos());
        assertEquals(200, centre.guide());
        var none = HudLayout.snapAxis(60, 50, 400, t, 2);
        assertEquals(60, none.pos());
        assertTrue(Double.isNaN(none.guide()));
    }
}
