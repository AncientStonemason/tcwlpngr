package com.farmmacro.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Атласы иконок (tools/gui/gen_icons.py) совпадают с {@link Icon}: порядок, размеры, заполненность ячеек. */
class IconAtlasTest {
    private static final String DIR = "/assets/farmmacro/textures/gui/icons/";

    private static InputStream open(String name) throws IOException {
        InputStream in = IconAtlasTest.class.getResourceAsStream(DIR + name);
        if (in == null) throw new IOException("нет ресурса " + DIR + name);
        return in;
    }

    @Test
    void namesFollowEnumOrder() throws IOException {
        List<String> names;
        try (InputStream in = open("icons.txt")) {
            names = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
        assertEquals(
                Arrays.stream(Icon.values()).map(Icon::atlasName).toList(),
                names,
                "icons.txt и Icon расходятся: перегенерируй атлас (tools/gui/gen_icons.py) или поправь перечисление");
    }

    @Test
    void everyAtlasHasFilledPaddedCells() throws IOException {
        int count = Icon.values().length;
        for (int cell : Icon.cells()) {
            BufferedImage image;
            try (InputStream in = open("icons_" + cell + ".png")) {
                image = ImageIO.read(in);
            }
            assertNotNull(image, "icons_" + cell + ".png не читается");
            assertEquals(Icon.COLS * cell, image.getWidth(), "ширина icons_" + cell);
            assertEquals(Icon.rows() * cell, image.getHeight(), "высота icons_" + cell);
            for (int i = 0; i < Icon.COLS * Icon.rows(); i++) {
                int x0 = (i % Icon.COLS) * cell, y0 = (i / Icon.COLS) * cell;
                boolean any = false, edge = false;
                for (int y = 0; y < cell; y++)
                    for (int x = 0; x < cell; x++) {
                        if ((image.getRGB(x0 + x, y0 + y) >>> 24) == 0) continue;
                        any = true;
                        // Линейная фильтрация тянет соседнюю ячейку: рисунок не должен касаться края.
                        if (x == 0 || y == 0 || x == cell - 1 || y == cell - 1) edge = true;
                    }
                String what = "ячейка " + i + " в icons_" + cell;
                if (i < count) assertTrue(any, what + " пуста (" + Icon.values()[i] + ")");
                else assertFalse(any, what + " должна быть пустой");
                assertFalse(edge, what + " касается края");
            }
        }
    }

    @Test
    void picksSmallestCellThatCoversTheScreenSize() {
        assertEquals(24, Icon.cellFor(16));
        assertEquals(24, Icon.cellFor(24));
        assertEquals(48, Icon.cellFor(25));
        assertEquals(48, Icon.cellFor(48));
        assertEquals(96, Icon.cellFor(64));
        assertEquals(192, Icon.cellFor(97));
        assertEquals(192, Icon.cellFor(4096));
    }
}
