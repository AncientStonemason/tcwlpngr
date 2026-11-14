package com.farmmacro.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class MenuLayoutTest {
    @Test
    void columnThresholds() {
        assertEquals(1, MenuLayout.columns(299));
        assertEquals(2, MenuLayout.columns(300));
        assertEquals(2, MenuLayout.columns(719));
        assertEquals(3, MenuLayout.columns(720));
    }

    @Test
    void cardsStayInsideAndDoNotOverlap() {
        for (int width : new int[] {120, 300, 333, 602, 720, 1000}) {
            int cols = MenuLayout.columns(width);
            for (int i = 0; i < 12; i++) {
                var a = MenuLayout.card(i, width, 4);
                assertTrue(a.x() >= 0 && a.x() + a.w() <= width, "card " + i + " at width " + width);
                for (int j = i + 1; j < 12; j++) {
                    var b = MenuLayout.card(j, width, 4);
                    boolean apart = a.x() + a.w() <= b.x()
                            || b.x() + b.w() <= a.x()
                            || a.y() + a.h() <= b.y()
                            || b.y() + b.h() <= a.y();
                    assertTrue(apart, "overlap " + i + "/" + j + " cols " + cols);
                }
            }
        }
    }

    @Test
    void compactCards() {
        assertTrue(MenuLayout.CARD_HEIGHT >= 48 && MenuLayout.CARD_HEIGHT <= 56);
    }

    @Test
    void selectionNavigation() {
        assertEquals(2, MenuLayout.nextSelection(0, 18, 2, GLFW.GLFW_KEY_DOWN));
        assertEquals(0, MenuLayout.nextSelection(0, 18, 2, GLFW.GLFW_KEY_UP));
        assertEquals(17, MenuLayout.nextSelection(17, 18, 2, GLFW.GLFW_KEY_DOWN));
        assertEquals(3, MenuLayout.nextSelection(4, 18, 2, GLFW.GLFW_KEY_LEFT));
        assertEquals(5, MenuLayout.nextSelection(4, 18, 2, GLFW.GLFW_KEY_RIGHT));
        assertEquals(0, MenuLayout.nextSelection(5, 0, 2, GLFW.GLFW_KEY_RIGHT));
    }

    @Test
    void everyModuleHasADistinctRealGlyph() {
        var seen = new java.util.HashSet<Integer>();
        for (var m : ModuleCatalog.ALL) {
            assertTrue(m.icon() >= 0 && m.icon() <= 19 && m.icon() != 17, m.id());
            assertTrue(seen.add(m.icon()), "duplicate glyph " + m.id());
        }
    }

    @Test
    void hexColourValidation() {
        assertTrue(UiStyle.validColor("") && UiStyle.validColor("#aBc012"));
        assertFalse(UiStyle.validColor("#12345") || UiStyle.validColor("123456") || UiStyle.validColor("#12345g"));
        assertFalse(UiStyle.validColor(null));
        assertEquals(0xFF112233, UiStyle.color("#112233", 0xFF000000));
        assertEquals(0x80000000, UiStyle.color("#zzzzzz", 0x80000000));
        assertTrue(UiStyle.safeName("abc_12") && !UiStyle.safeName("ABC") && !UiStyle.safeName(""));
    }
}
