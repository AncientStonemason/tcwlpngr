package com.farmmacro.panic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;

class PanicLogicTest {
    @Test
    void farmStopDelayFirstCauseWinsAndIsBounded() {
        var d = new FarmStopDelay();
        d.arm("chat", "первая", 0, 100_000);
        d.arm("staff", "вторая", 10, 0);
        assertEquals("chat", d.id());
        assertFalse(d.due(1_000_000_000L), "потолок задержки 1200 мс");
        assertTrue(d.due(1_200_000_000L));
        d.clear();
        assertFalse(d.active());
        assertFalse(FarmStopDelay.eligible("rotation", true, false, true), "критичные детекторы — без задержки");
        assertFalse(FarmStopDelay.eligible("chat", true, true, true), "Combat — без задержки");
        assertTrue(FarmStopDelay.eligible("chat", true, false, true));
    }

    @Test
    void banwaveCountsBansInWindow() {
        var s = List.of(
                new BanwaveWatch.Sample(0, 100, 10),
                new BanwaveWatch.Sample(60_000, 130, 12),
                new BanwaveWatch.Sample(120_000, 190, 20));
        assertEquals(-1, BanwaveWatch.bansIn(s.subList(0, 1), 0, 60_000, false));
        assertEquals(60, BanwaveWatch.bansIn(s, 120_000, 60_000, false));
        assertEquals(60 + 8, BanwaveWatch.bansIn(s, 120_000, 60_000, true));
        assertEquals(90, BanwaveWatch.bansIn(s, 120_000, 3_600_000, false), "окно не набрано — от старейшего ответа");
    }

    @Test
    void banwaveParseRejectsBadAnswers() {
        assertNotNull(BanwaveWatch.parse("{\"success\":true,\"watchdog_total\":5,\"staff_total\":2}", 1));
        assertNull(BanwaveWatch.parse("{\"success\":false}", 1));
        assertNull(BanwaveWatch.parse("{\"success\":true,\"watchdog_total\":5}", 1));
    }

    @Test
    void soundRenderClampsVolumeAndKeepsFrames() {
        var clip = new SoundDecoder.Clip(new short[] {1000, -1000, 30000, -30000}, 1, 8000);
        byte[] loud = SoundDecoder.render(clip, 5f, 1f); // громкость выше 1 обрезается до 1
        byte[] unit = SoundDecoder.render(clip, 1f, 1f);
        assertEquals(8, loud.length);
        assertEquals(java.util.Arrays.toString(unit), java.util.Arrays.toString(loud));
        assertEquals(0, SoundDecoder.render(clip, -3f, 1f)[0]);
        assertEquals(clip.frames() / 2, SoundDecoder.render(clip, 1f, 2f).length / 2, "тон ×2 — вдвое короче");
    }

    @Test
    void wavDecodesToMonoPcm() throws Exception {
        byte[] pcm = new byte[8000 * 2];
        for (int i = 0; i < 8000; i++) {
            short v = (short) (Math.sin(i / 10.0) * 12000);
            pcm[2 * i] = (byte) v;
            pcm[2 * i + 1] = (byte) (v >> 8);
        }
        var fmt = new AudioFormat(8000, 16, 1, true, false);
        var out = new ByteArrayOutputStream();
        AudioSystem.write(
                new AudioInputStream(new ByteArrayInputStream(pcm), fmt, 8000), AudioFileFormat.Type.WAVE, out);
        var clip = SoundDecoder.decode(out.toByteArray(), "x.WAV");
        assertEquals(8000, clip.sampleRate());
        assertEquals(1, clip.channels());
        assertEquals(8000, clip.frames());
    }

    @Test
    void correctionWindowCountsPathLengthAndBoundsPackets() {
        var w = new CorrectionWindow();
        long s = 1_000_000_000L;
        assertTrue(w.accept(0, 5, 3, 1.0, 0.4));
        assertTrue(w.accept(s, 5, 3, 1.0, 0.4)); // обратная поправка не гасит прошлую
        assertFalse(w.accept(2 * s, 5, 3, 1.0, 0.4), "суммарный путь 1.2 > бюджета 1.0");
        assertTrue(w.accept(10 * s, 5, 3, 1.0, 0.4), "старые пакеты вышли из окна");
    }

    @Test
    void dwellTimerResetsWhenKeyLeaves() {
        var t = new DwellTimer<Integer>();
        assertNull(t.update(0, java.util.Set.of(1), 1000));
        assertEquals(1, t.update(1000, java.util.Set.of(1), 1000));
        assertNull(t.update(1500, java.util.Set.of(), 1000));
        assertNull(t.update(2000, java.util.Set.of(1), 1000), "ушёл — отсчёт заново");
    }
}
