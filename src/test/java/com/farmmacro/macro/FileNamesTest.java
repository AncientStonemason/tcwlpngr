package com.farmmacro.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FileNamesTest {
    @Test
    void ordinaryNamesKeepHistoricForm() {
        assertEquals("My_Macro", FileNames.sanitize("  My Macro "));
        assertEquals("a_b_c", FileNames.sanitize("a/b:c"));
        assertEquals("", FileNames.sanitize("..."));
    }

    @Test
    void reservedWindowsNamesAreEscaped() {
        for (String n : new String[] {"CON", "nul", "Aux", "COM1", "lpt9", "con.txt"}) {
            String s = FileNames.sanitize(n);
            assertTrue(s.startsWith("_"), s);
        }
        assertEquals("console", FileNames.sanitize("console"));
    }

    @Test
    void trailingDotsAreStripped() {
        assertEquals("name", FileNames.sanitize("name. . "));
        assertFalse(FileNames.sanitize("x...").endsWith("."));
    }

    @Test
    void longNamesAreBoundedAndStayDistinct() {
        String a = "a".repeat(200) + "1", b = "a".repeat(200) + "2";
        String sa = FileNames.sanitize(a), sb = FileNames.sanitize(b);
        assertTrue(sa.length() <= FileNames.MAX_LENGTH + 9);
        assertNotEquals(sa, sb);
    }
}
