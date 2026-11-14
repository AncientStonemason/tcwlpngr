package com.farmmacro.hud;

import com.farmmacro.gui.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/** Общие приёмы отрисовки плашек HUD: отступ, заголовок с акцентной полоской, строки «подпись — значение». */
final class HudUi {
    static final int PAD = 9;

    private HudUi() {}

    /** Подпись и значение строки «Подпись: значение». */
    record Split(String label, String value) {}

    /** null — строка не делится на подпись и значение. */
    static @Nullable Split split(String text) {
        int i = text.indexOf(": ");
        if (i <= 0 || i > 22 || i + 2 >= text.length()) return null;
        String label = text.substring(0, i);
        if (label.chars().filter(ch -> ch == ' ').count() > 2) return null;
        return new Split(label, text.substring(i + 2));
    }

    /** Заголовок: акцентная полоска и название, ужатое по измеренной ширине. */
    static void header(GuiGraphicsExtractor g, Font f, int x, int y, int w, String title, int accent, int color) {
        g.fill(x + PAD, y + 5, x + PAD + 2, y + 14, accent);
        Ui.text(g, f, Ui.ellipsize(f, title, w - PAD * 2 - 6), x + PAD + 6, y + 6, color);
    }

    /** Строка: подпись слева, значение выровнено вправо; не помещается — одна строка с «…». */
    static void row(GuiGraphicsExtractor g, Font f, int x, int y, int w, String text, int labelColor, int valueColor) {
        int inner = w - PAD * 2;
        Split s = split(text);
        if (s != null) {
            int vw = f.width(s.value()), lw = f.width(s.label());
            if (lw + vw + 8 <= inner) {
                Ui.text(g, f, s.label(), x + PAD, y, labelColor);
                Ui.text(g, f, s.value(), x + w - PAD - vw, y, valueColor);
                return;
            }
            if (vw + 8 <= inner * 2 / 3 && lw >= 16) {
                int room = inner - vw - 8;
                Ui.text(g, f, Ui.ellipsize(f, s.label(), room), x + PAD, y, labelColor);
                Ui.text(g, f, s.value(), x + w - PAD - vw, y, valueColor);
                return;
            }
        }
        Ui.text(g, f, Ui.ellipsize(f, text, inner), x + PAD, y, labelColor);
    }
}
