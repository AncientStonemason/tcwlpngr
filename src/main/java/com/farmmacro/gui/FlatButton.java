package com.farmmacro.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Native focus/click/narration, own Kaelix drawing. */
public final class FlatButton extends Button {
    private static final int ICON = 12;
    private final int icon;

    public FlatButton(int x, int y, int w, int h, String text, OnPress press) {
        this(x, y, w, h, text, UiIcons.NONE, press);
    }

    public FlatButton(int x, int y, int w, int h, String text, int icon, OnPress press) {
        super(x, y, w, h, Component.literal(text), press, DEFAULT_NARRATION);
        this.icon = icon;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var f = Minecraft.getInstance().font;
        boolean lit = active && isHoveredOrFocused();
        Ui.roundBordered(
                g,
                getX(),
                getY(),
                getWidth(),
                getHeight(),
                5,
                lit ? Ui.CARD_HOVER : Ui.CARD,
                active && isFocused() ? Ui.ACCENT : Ui.BORDER);
        int color = active ? Ui.TEXT : Ui.SUB, iw = icon >= 0 ? ICON + 4 : 0;
        String label = Ui.ellipsize(f, getMessage().getString(), getWidth() - 12 - iw);
        int left = getX() + (getWidth() - iw - f.width(label)) / 2;
        if (icon >= 0) UiIcons.draw(g, icon, left, getY() + (getHeight() - ICON) / 2, ICON, color);
        Ui.text(g, f, label, left + iw, getY() + (getHeight() - 8) / 2, color);
    }
}
