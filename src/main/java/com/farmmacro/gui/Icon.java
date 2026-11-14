package com.farmmacro.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Иконки интерфейса Kaelix: оригинальная векторная графика (tools/gui/gen_icons.py), одноцветная маска. Цвет
 * задаётся при отрисовке, поэтому иконки следуют теме. Порядок констант = порядок ячеек атласа (icons.txt,
 * проверяет IconAtlasTest).
 */
public enum Icon {
    GRID,
    SPROUT,
    SWORD,
    PALETTE,
    SLIDERS,
    GEAR,
    PLAY,
    STOP,
    PAUSE,
    RECORD,
    PENCIL,
    SAVE,
    TRASH,
    ARROW_LEFT,
    CHEVRON_RIGHT,
    CHEVRON_DOWN,
    CHEVRON_UP,
    CLOSE,
    SEARCH,
    PLUS,
    MINUS,
    CHECK,
    COPY,
    IMPORT,
    EXPORT,
    UNDO,
    REDO,
    REFRESH,
    WARNING,
    INFO,
    HELP,
    EYE,
    FOLDER,
    FILE,
    KEYBOARD,
    MOUSE,
    CLOCK,
    TARGET,
    FISH,
    SHIELD,
    SIREN,
    BELL,
    WAYPOINT,
    ROUTE,
    CAMERA,
    VIDEO,
    LAYOUT,
    COINS,
    SPARKLES,
    GAUGE,
    MOTION,
    LAUNCH,
    MACRO,
    STAR,
    FLIP,
    LAYERS,
    SOUND,
    SEND,
    HEART,
    FLAG,
    DICE,
    LOCK,
    WINDOW,
    USER,
    BOLT;

    /** Колонок в атласе и размеры ячеек в пикселях — как в генераторе. */
    static final int COLS = 8;

    private static final int[] CELLS = {24, 48, 96, 192};

    static int[] cells() {
        return CELLS.clone();
    }

    static int rows() {
        return (values().length + COLS - 1) / COLS;
    }

    /** Наименьшая ячейка не меньше нужного размера на экране: уменьшение при отрисовке всегда меньше чем вдвое. */
    static int cellFor(int screenPixels) {
        for (int cell : CELLS) if (cell >= screenPixels) return cell;
        return CELLS[CELLS.length - 1];
    }

    /** Имя в icons.txt и в генераторе. */
    String atlasName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Квадрат size×size (единицы GUI) с левым верхним углом (x, y), окрашенный в argb. */
    public void draw(GuiGraphicsExtractor g, int x, int y, int size, int argb) {
        if (size <= 0 || (argb >>> 24) == 0) return;
        int scale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        int slot = Atlas.slot(size * scale), cell = CELLS[slot], index = ordinal();
        g.blit(
                RenderPipelines.GUI_TEXTURED,
                Atlas.IDS[slot],
                x,
                y,
                (index % COLS) * cell,
                (index / COLS) * cell,
                size,
                size,
                cell,
                cell,
                COLS * cell,
                rows() * cell,
                argb);
    }

    /** То же по центру прямоугольника (x, y, w, h). */
    public void drawCentered(GuiGraphicsExtractor g, int x, int y, int w, int h, int size, int argb) {
        draw(g, x + (w - size) / 2, y + (h - size) / 2, size, argb);
    }

    /** Текстуры отдельно: перечисление и расчёт ячеек остаются доступными тестам без классов игры. */
    private static final class Atlas {
        static final Identifier[] IDS = new Identifier[CELLS.length];

        static {
            for (int i = 0; i < CELLS.length; i++)
                IDS[i] = Identifier.fromNamespaceAndPath("farmmacro", "textures/gui/icons/icons_" + CELLS[i] + ".png");
        }

        static int slot(int screenPixels) {
            for (int i = 0; i < CELLS.length; i++) if (CELLS[i] >= screenPixels) return i;
            return CELLS.length - 1;
        }
    }
}
