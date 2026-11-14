package com.farmmacro.gui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A settings entry, deliberately without a controller toggle or start action. */
final class ModuleCardRow extends Rows.Row {
    /** Плитка с иконкой слева и поле под стрелку справа. */
    private static final int TILE = 28, TEXT_X = 42, ARROW_W = 22;

    private final ModuleCatalog.Module module;
    private final Runnable open;
    private final BooleanSupplier focused;
    private final Runnable hovered;

    ModuleCardRow(ModuleCatalog.Module module, Runnable open, BooleanSupplier focused, Runnable hovered) {
        this.module = module;
        this.open = open;
        this.focused = focused;
        this.hovered = hovered;
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
        int yy = y + MenuLayout.CARD_GAP / 2, h = MenuLayout.CARD_HEIGHT - MenuLayout.CARD_GAP;
        Ui.roundBordered(
                g,
                x,
                yy,
                w,
                h,
                6,
                hover ? Ui.CARD_HOVER : Ui.CARD,
                focus ? Ui.ACCENT : hover ? Ui.lerp(Ui.BORDER, Ui.SUB, .35f) : Ui.BORDER);
        int tile = yy + (h - TILE) / 2;
        Ui.round(g, x + 6, tile, TILE, TILE, 6, Ui.lerp(Ui.CARD, Ui.ACCENT, lit ? .24f : .12f));
        ModuleCatalog.icon(module)
                .drawCentered(g, x + 6, tile, TILE, TILE, 18, lit ? Ui.ACCENT_HI : Ui.lerp(Ui.ACCENT_HI, Ui.TEXT, .3f));
        int available = Math.max(8, w - TEXT_X - ARROW_W);
        String title = Ui.ellipsize(c.font(), module.title(), available);
        String description = Ui.ellipsize(c.font(), module.description(), available);
        Ui.text(g, c.font(), title, x + TEXT_X, yy + h / 2 - 11, Ui.TEXT);
        Ui.text(g, c.font(), description, x + TEXT_X, yy + h / 2 + 2, Ui.SUB);
        Icon.CHEVRON_RIGHT.draw(g, x + w - 16, yy + (h - 10) / 2, 10, lit ? Ui.ACCENT_HI : Ui.DIM);
        if (hover) {
            hovered.run();
            c.hand();
            // Подсказка нужна только когда на карточке не поместился текст.
            if (!title.equals(module.title()) || !description.equals(module.description()))
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
