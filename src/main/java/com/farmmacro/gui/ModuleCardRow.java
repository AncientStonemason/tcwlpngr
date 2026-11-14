package com.farmmacro.gui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A settings entry, deliberately without a controller toggle or start action. */
final class ModuleCardRow extends Rows.Row {
    private static final int TILE = 32, PAD = 8, CHEVRON_ROOM = 18;
    private final ModuleCatalog.Module module;
    private final Runnable open;
    private final BooleanSupplier focused;

    ModuleCardRow(ModuleCatalog.Module module, Runnable open, BooleanSupplier focused) {
        this.module = module;
        this.open = open;
        this.focused = focused;
    }

    @Override
    public String searchText() {
        return module.title() + " " + module.description();
    }

    @Override
    int height(Rows.Ctx c, int w) {
        return MenuLayout.CARD_HEIGHT;
    }

    @Override
    void render(Rows.Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
        boolean focus = focused.getAsBoolean(), lit = hover || focus;
        int h = MenuLayout.CARD_HEIGHT;
        Ui.roundBordered(
                g,
                x,
                y,
                w,
                h,
                7,
                hover ? Ui.CARD_HOVER : Ui.CARD,
                focus ? Ui.ACCENT : hover ? Ui.lerp(Ui.BORDER, Ui.ACCENT, .55f) : Ui.BORDER);
        if (lit) g.fill(x + 1, y + 7, x + 3, y + h - 7, Ui.ACCENT);
        int ty = y + (h - TILE) / 2;
        Ui.round(g, x + PAD + 2, ty, TILE, TILE, 6, Ui.lerp(Ui.CARD, Ui.ACCENT, lit ? .16f : .08f));
        ModuleGlyphs.draw(g, module.icon(), x + PAD + 6, ty + 4, 24);
        boolean chevron = w >= 190;
        int tx = x + PAD + 2 + TILE + 8, available = Math.max(8, x + w - tx - 6 - (chevron ? CHEVRON_ROOM : 0));
        String rest = module.description(), first = c.font().plainSubstrByWidth(rest, available);
        if (first.length() < rest.length()) {
            int space = first.lastIndexOf(' ');
            if (space > 0) first = first.substring(0, space);
        }
        rest = rest.substring(first.length()).strip();
        int top = y + (rest.isEmpty() ? h / 2 - 10 : 7);
        Ui.text(g, c.font(), Ui.ellipsize(c.font(), module.title(), available), tx, top, Ui.TEXT);
        Ui.text(g, c.font(), first, tx, top + 13, Ui.SUB);
        if (!rest.isEmpty()) Ui.text(g, c.font(), Ui.ellipsize(c.font(), rest, available), tx, top + 24, Ui.SUB);
        if (chevron)
            UiIcons.draw(g, UiIcons.CHEVRON_RIGHT, x + w - 20, y + (h - 12) / 2, 12, lit ? Ui.ACCENT_HI : Ui.DIM);
        if (hover) {
            c.hand();
            c.tooltip(module.title() + " — " + module.description() + " "
                    + Tr.t("farmmacro.module.open_hint", "Открытие не запускает модуль."));
        }
    }

    @Override
    boolean click(Rows.Ctx c, double mx, double my, int button) {
        if (button != 0) return false;
        c.clickSound();
        open.run();
        return true;
    }
}
