package com.farmmacro.panic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventLogWriterTest {
    @TempDir
    Path dir;

    @Test
    void rotatesWhenFileIsFullAndLosesNoLines() throws IOException {
        var w = new EventLogWriter(dir, 1024);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 100; i++) lines.add("line-" + i + "-" + "x".repeat(40));
        w.write(lines.subList(0, 50), LocalDate.of(2026, 1, 2));
        w.write(lines.subList(50, 100), LocalDate.of(2026, 1, 2));

        int total = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.toList()) {
                assertTrue(Files.size(f) <= 1024 + 100, f + " " + Files.size(f));
                total += Files.readAllLines(f).size();
            }
        }
        assertEquals(100, total);
        assertTrue(Files.exists(dir.resolve("events-2026-01-02.1.log")), "старый файл переименован при ротации");
    }

    @Test
    void oversizedSingleLineIsStillWritten() throws IOException {
        var w = new EventLogWriter(dir, 1024);
        w.write(List.of("y".repeat(5000)), LocalDate.of(2026, 1, 3));
        assertEquals(1, Files.readAllLines(w.fileFor(LocalDate.of(2026, 1, 3))).size());
    }
}
