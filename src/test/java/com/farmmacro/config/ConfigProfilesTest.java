package com.farmmacro.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class ConfigProfilesTest {
    @Test
    void cleanRejectsReservedAndTrailingDots() {
        assertNull(ConfigProfiles.clean("..."));
        assertNull(ConfigProfiles.clean("  "));
        assertEquals("_CON", ConfigProfiles.clean("CON"));
        assertEquals("_nul", ConfigProfiles.clean("nul"));
        assertEquals("_COM1.txt", ConfigProfiles.clean("COM1.txt"));
        assertEquals("name", ConfigProfiles.clean("name. . "));
        assertEquals("my profile", ConfigProfiles.clean("my profile"));
        assertEquals("CONSOLE", ConfigProfiles.clean("CONSOLE"));
        assertEquals("a_b", ConfigProfiles.clean("a/b"));
    }

    @Test
    void settingsCodeOmitsOtherPlayersNames() {
        ModConfig c = new ModConfig();
        c.friendNames = "Alice, Bob";
        c.chatKeywords = "secretword";
        c.aimManualName = "Carol";
        c.combatPanic.friends = "Dave";
        JsonObject o = ConfigProfiles.codeTree(c);
        for (String k : new String[] {"friendNames", "chatKeywords", "aimManualName"}) assertFalse(o.has(k), k);
        assertFalse(o.getAsJsonObject("combatPanic").has("friends"));
        assertFalse(o.toString().contains("Alice"));
        assertTrue(o.has("schemaVersion"));
    }

    @Test
    void hudSlotsOverLimitKeepFirst64() {
        ModConfig c = new ModConfig();
        for (int i = 0; i < 70; i++) c.hudSlots.put("s" + i, new ModConfig.HudSlot());
        c.sanitize();
        assertEquals(64, c.hudSlots.size());
        assertTrue(c.hudSlots.containsKey("s0"));
        assertFalse(c.hudSlots.containsKey("s69"));
    }
}
