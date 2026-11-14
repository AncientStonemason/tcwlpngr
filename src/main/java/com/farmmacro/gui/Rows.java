package com.farmmacro.gui;

import com.farmmacro.config.ModConfig;
import com.farmmacro.util.Clock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Строки содержимого вкладок (немедленный режим: значения читаются из конфига при каждой отрисовке,
 * поэтому переключатели всегда показывают актуальное состояние без пересоздания экрана).
 */
public final class Rows {
    private Rows() {}

    /** Что строкам нужно от экрана. */
    public interface Ctx {
        Font font();

        void clickSound();

        void tooltip(String text);

        void hand();

        boolean shift();
    }

    /**
     * Строка, которой разрешено менять значение колёсиком. Колёсико действует только после клика
     * по полю: иначе прокрутка списка, проходя курсором над числом, незаметно меняла пороги (v1.2.1).
     */
    static Row wheelFocus;

    /** Сбросить фокус колёсика (клик мимо поля, смена вкладки, закрытие меню). */
    public static void clearWheelFocus() {
        wheelFocus = null;
    }

    // ── v1.11: что показывать (простой/расширенный режим, свёрнутые разделы, поиск) ──

    /** Расширенный режим меню: видны строки, помеченные {@link Row#adv()}. */
    public static BooleanSupplier advanced = () -> ModConfig.INSTANCE.uiAdvanced;
    /** Идёт поиск: видно всё найденное, без учёта режима и свёрнутых разделов. */
    public static boolean searching;
    /** Фильтр «только изменённые»: видны лишь строки, отличающиеся от нормы; разделы раскрыты, тонкие настройки видны. */
    public static boolean changedOnly;

    /** Привязать строки к разделам (строка → ближайший Section выше). Вызывать после сборки списка. */
    public static void link(List<Row> rows) {
        Section cur = null;
        for (Row r : rows) {
            if (r instanceof Section s) {
                cur = s;
                s.children.clear();
                continue;
            }
            if (r instanceof End) {
                cur = null;
                continue;
            }
            r.section = cur;
            if (cur != null) cur.children.add(r);
        }
    }

    /** Сколько строк скрыто только потому, что выключен расширенный режим. */
    public static int hiddenAdvanced(List<Row> rows) {
        if (advanced.getAsBoolean()) return 0;
        int n = 0;
        for (Row r : rows)
            if (r.adv
                    && !(r instanceof Section)
                    && !(r instanceof Note)
                    && r.visible.getAsBoolean()
                    && (r.section == null || r.section.visible.getAsBoolean())) n++;
        return n;
    }

    public abstract static class Row {
        /** Позиция с последней отрисовки (для кликов). */
        int lastX, lastY, lastW, lastH;
        /** v1.16.0: место в раскладке (колонки): сдвиг x, y от начала списка, ширина. */
        int lx, ly, lw;

        BooleanSupplier enabled = () -> true;
        BooleanSupplier visible = () -> true;
        boolean adv;
        Section section;

        public Row enabledIf(BooleanSupplier s) {
            this.enabled = s;
            return this;
        }
        /** Показывать только при условии (например, пока включён «родительский» переключатель). */
        public Row showIf(BooleanSupplier s) {
            this.visible = s;
            return this;
        }
        /** Тонкая настройка: только в расширенном режиме (и в поиске). */
        public Row adv() {
            adv = true;
            return this;
        }
        /** enabledIf + showIf одним условием. */
        public Row under(BooleanSupplier s) {
            enabled = s;
            visible = s;
            return this;
        }

        boolean on() {
            return enabled.getAsBoolean();
        }

        public boolean isAdv() {
            return adv;
        }

        /** Условия самой строки без раздела: видимость, расширенный режим, фильтр «только изменённые». */
        boolean ownShown() {
            return visible.getAsBoolean()
                    && (!adv || advanced.getAsBoolean() || changedOnly)
                    && (!changedOnly || passesChangedFilter());
        }

        /** Остаётся ли строка при фильтре «только изменённые» (по умолчанию — нет, кроме строк с изменённым значением). */
        boolean passesChangedFilter() {
            return false;
        }

        /** Видна ли сама строка (без учёта свёрнутого раздела). */
        boolean selfShown() {
            return ownShown() && (section == null || section.selfShown());
        }

        public boolean shown() {
            if (searching) return true;
            return selfShown() && (section == null || section.open());
        }
        /** Высота с учётом видимости — её используют экраны при раскладке. */
        public int h(Ctx c, int w) {
            return shown() ? height(c, w) : 0;
        }
        /** Текст для поиска (подпись и подсказка). */
        public String searchText() {
            return "";
        }

        abstract int height(Ctx c, int w);

        abstract void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover);

        boolean click(Ctx c, double mx, double my, int button) {
            return false;
        }

        boolean scroll(Ctx c, double mx, double my, double amount) {
            return false;
        }
    }

    // ── Заголовок раздела ────────────────────────────────────────────────────

    public static final class Section extends Row {
        final String title;
        final List<Row> children = new ArrayList<>();
        private String key;
        private boolean defaultOpen = true;
        private Boolean localExpansion;
        private Runnable onClick;

        public Section(String title) {
            this.title = title;
            this.key = title;
        }

        public String title() {
            return title;
        }
        /** Ключ состояния «свёрнут» (по умолчанию — заголовок). */
        public Section key(String k) {
            key = k;
            return this;
        }

        public String key() {
            return key;
        }
        /** Свёрнут, пока пользователь не развернёт. */
        public Section closed() {
            defaultOpen = false;
            return this;
        }
        /** Клик по заголовку вместо сворачивания (результаты поиска — перейти к разделу). */
        public Section onClick(Runnable r) {
            onClick = r;
            return this;
        }

        boolean collapsible() {
            return onClick == null && !children.isEmpty() && !searching;
        }

        public Section expandedLocally() {
            localExpansion = true;
            return this;
        }

        public boolean open() {
            if (changedOnly) return true;
            if (localExpansion != null) return localExpansion;
            if (!collapsible()) return true;
            Boolean v = ModConfig.INSTANCE.uiSections.get(key);
            return v != null ? v : defaultOpen;
        }

        public void setOpen(boolean v) {
            if (localExpansion != null) {
                localExpansion = v;
                return;
            }
            ModConfig.INSTANCE.uiSections.put(key, v);
            ModConfig.save();
        }

        @Override
        boolean passesChangedFilter() {
            return true;
        }

        @Override
        boolean selfShown() {
            if (!ownShown()) return false;
            if (children.isEmpty()) return !changedOnly;
            for (Row r : children) if (r.ownShown() && !(r instanceof Note n && n.empty())) return true;
            return false;
        }

        /** Строки раздела, которые сейчас видны и отличаются от нормы (их сбрасывает кнопка раздела). */
        List<Labeled> changedChildren() {
            List<Labeled> out = new ArrayList<>();
            for (Row r : children) if (r instanceof Labeled l && l.changed() && l.ownShown()) out.add(l);
            return out;
        }

        private static Section armed;
        private static long armedUntil;
        private static final int ARM_MS = 3000;

        private boolean armed() {
            return armed == this && Clock.millis() < armedUntil;
        }

        private boolean overReset(double mx, double my) {
            return my >= lastY + 4 && my < lastY + 20 && mx >= lastX + lastW - 16 && mx < lastX + lastW;
        }

        @Override
        public String searchText() {
            return title;
        }

        @Override
        int height(Ctx c, int w) {
            return 24;
        }

        @Override
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            boolean col = collapsible(), op = open();
            boolean hot = hover && (col || onClick != null);
            renderFlat(c, g, x, y, w, hot, col, op);
        }

        /** v1.16.0 минималистичный заголовок: мелко, вразрядку, серым, без линии. */
        private void renderFlat(
                Ctx c, GuiGraphicsExtractor g, int x, int y, int w, boolean hot, boolean col, boolean op) {
            Font f = c.font();
            int tx = x + 2, ty = y + 10;
            if (col || onClick != null) {
                UiIcons.draw(
                        g,
                        col ? (op ? UiIcons.CHEVRON_DOWN : UiIcons.CHEVRON_RIGHT) : UiIcons.CHEVRON_RIGHT,
                        tx - 2,
                        ty - 2,
                        12,
                        hot ? Ui.SUB : Ui.DIM);
                tx += 12;
            }
            String right = null;
            if (col) {
                int on = 0, all = 0, rows = 0;
                for (Row r : children) {
                    if (!r.visible.getAsBoolean() || (r.adv && !advanced.getAsBoolean()) || r instanceof Note) continue;
                    rows++;
                    if (r instanceof Toggle tg) {
                        all++;
                        if (tg.value()) on++;
                    }
                }
                if (all > 0) right = on + "/" + all;
                if (!op) right = (right != null ? right + " · " : "") + rows + " " + plural(rows);
            } else if (onClick != null) right = "перейти";
            int reserve = right == null ? 0 : f.width(right) + 12;
            String label = Ui.ellipsize(f, title, Math.max(8, x + w - tx - reserve));
            Ui.text(g, f, label, tx, ty, hot ? Ui.TEXT : Ui.SUB);
            if (right != null) Ui.textRight(g, f, right, x + w - 2, ty, Ui.DIM);
            if (hot) {
                c.hand();
                String action = onClick != null
                        ? "Открыть вкладку с этим разделом"
                        : op ? "Свернуть раздел" : "Развернуть раздел";
                c.tooltip(label.equals(title) ? action : title + " — " + action);
            }
        }

        @Override
        boolean click(Ctx c, double mx, double my, int button) {
            if (button != 0) return false;
            if (onClick != null) {
                c.clickSound();
                onClick.run();
                return true;
            }
            if (!collapsible()) return false;
            c.clickSound();
            setOpen(!open());
            return true;
        }

        private static String plural(int n) {
            int m10 = n % 10, m100 = n % 100;
            if (m10 == 1 && m100 != 11) return "настройка";
            if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return "настройки";
            return "настроек";
        }
    }

    /**
     * Переключатель «показать ещё N тонких настроек / скрыть» (простой/расширенный режим) в конце вкладки или окна.
     */
    public static final class MoreToggle extends Row {
        private final IntSupplier hidden;
        private final Runnable toggle;

        public MoreToggle(IntSupplier hidden, Runnable toggle) {
            this.hidden = hidden;
            this.toggle = toggle;
        }

        private String text() {
            if (advanced.getAsBoolean()) return "Скрыть тонкие настройки (простой режим)";
            int n = hidden.getAsInt();
            return n <= 0 ? "" : "Ещё " + n + " " + Section.plural(n) + " — показать все (расширенный режим)";
        }

        @Override
        public boolean shown() {
            return !searching && !text().isEmpty();
        }

        @Override
        int height(Ctx c, int w) {
            return 20;
        }

        @Override
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            String t = text();
            int color = hover ? Ui.TEXT : Ui.DIM;
            String s = Ui.ellipsize(c.font(), t, w - 12 - 14);
            int left = x + (w - 14 - c.font().width(s)) / 2;
            UiIcons.draw(
                    g, advanced.getAsBoolean() ? UiIcons.CHEVRON_DOWN : UiIcons.CHEVRON_RIGHT, left, y + 4, 12, color);
            Ui.text(g, c.font(), s, left + 14, y + 6, color);
            if (hover) {
                c.hand();
                c.tooltip("Пороги, веса, цвета и служебные шаги. Тот же переключатель — вверху окна");
            }
        }

        @Override
        boolean click(Ctx c, double mx, double my, int button) {
            if (button != 0) return false;
            c.clickSound();
            toggle.run();
            return true;
        }
    }

    /** Конец раздела: строки ниже не принадлежат предыдущему Section (не сворачиваются с ним). Высота 0. */
    public static final class End extends Row {
        @Override
        public boolean shown() {
            return false;
        }

        @Override
        int height(Ctx c, int w) {
            return 0;
        }

        @Override
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {}
    }

    // ── Базовая «карточка» с подписью и подсказкой ───────────────────────────

    abstract static class Labeled extends Row {
        final String label, hint;

        Labeled(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }

        @Override
        public String searchText() {
            return label + (hint != null ? " " + hint : "");
        }

        @Override
        int height(Ctx c, int w) {
            return hintShown() ? 30 : 22;
        }

        /** v1.16.0: в минималистичных темах подсказка — во всплывашке, под подписью только ошибка (или если включены подписи). */
        boolean hintShown() {
            String hn = hintNow();
            if (hn == null) return false;
            return showHints.getAsBoolean() || !Objects.equals(hn, hint);
        }

        /** Подсказка под подписью сейчас (у полей ввода при ошибке — текст ошибки). */
        String hintNow() {
            return hint;
        }

        int hintColor() {
            return on() ? Ui.SUB : Ui.DIM;
        }

        /** Ширина элемента управления справа. */
        abstract int controlWidth(Ctx c);

        void drawCard(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover, boolean clickable) {
            drawFlat(c, g, x, y, w, h, hover, clickable);
        }

        /** Минималистичная строка: без карточки, тонкая линия снизу, лёгкая подсветка под мышью. */
        void drawFlat(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover, boolean clickable) {
            if (hover && on()) g.fill(x - 2, y, x + w + 2, y + h - 1, Ui.alpha(Ui.CARD_HOVER, 0.55f));
            g.fill(x, y + h - 1, x + w, y + h, Ui.BORDER);
            int textW = w - controlWidth(c) - 14;
            int col = on() ? Ui.TEXT : Ui.DIM;
            Font f = c.font();
            String lab = Ui.ellipsize(f, label, textW);
            if (hintShown()) {
                String hn = hintNow();
                Ui.text(g, f, lab, x + 2, y + 6, col);
                String hs = Ui.ellipsize(f, hn, textW);
                Ui.text(g, f, hs, x + 2, y + 17, hintColor());
                if (hover && !hs.equals(hn)) c.tooltip(hn);
            } else {
                Ui.text(g, f, lab, x + 2, y + (h - 8) / 2, col);
                if (hover && (hint != null || !lab.equals(label)))
                    c.tooltip(!lab.equals(label) ? label + (hint != null ? " — " + hint : "") : hint);
            }
        }
    }

    /** v1.16.0: показывать подсказки под подписями в минималистичных темах (ModConfig.uiHints). */
    public static BooleanSupplier showHints = () -> ModConfig.INSTANCE.uiHints;

    // ── Переключатель ────────────────────────────────────────────────────────

    public static final class Toggle extends Labeled {
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;
        private float anim = -1;
        private long lastNs;

        public Toggle(String label, String hint, BooleanSupplier get, Consumer<Boolean> set) {
            super(label, hint);
            this.get = get;
            this.set = set;
        }

        @Override
        int controlWidth(Ctx c) {
            return 24;
        }

        public boolean value() {
            return get.getAsBoolean();
        }

        @Override
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            int h = height(c, w);
            drawCard(c, g, x, y, w, h, hover, true);
            boolean v = get.getAsBoolean();
            long now = System.nanoTime();
            float target = v ? 1 : 0;
            if (anim < 0) anim = target;
            float dt = lastNs == 0 ? 0 : (now - lastNs) / 1e9f;
            lastNs = now;
            anim += (target - anim) * Math.min(1, dt * 14);
            if (Math.abs(target - anim) < 0.01f) anim = target;

            int tw = 18, th = 10;
            int tx = x + w - tw - 2, ty = y + (h - 1 - th) / 2;
            int track = Ui.lerp(Ui.TRACK_OFF, Ui.TOGGLE_ON, anim);
            if (!on()) track = Ui.alpha(track, 0.45f);
            Ui.pill(g, tx, ty, tw, th, track);
            int kr = 3;
            int kx = tx + th / 2 + Math.round(anim * (tw - th));
            Ui.circle(g, kx, ty + th / 2, kr, !on() ? Ui.KNOB_OFF : Ui.lerp(Ui.KNOB_OFF, Ui.KNOB_ON, anim));
            if (hover && on()) c.hand();
        }

        @Override
        boolean click(Ctx c, double mx, double my, int button) {
            if (!on() || button != 0) return false;
            set.accept(!get.getAsBoolean());
            c.clickSound();
            return true;
        }
    }

    // ── Числовое поле со стрелками (клик, колёсико; Shift — крупный шаг) ─────

    /** Короткая запись числа без хвостов float: 0.001 → «0.001», 2.0 → «2». До 6 знаков после точки. */
    public static String num(double v) {
        if (!Double.isFinite(v)) return String.valueOf(v);
        String t = new BigDecimal(v)
                .setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
        return t.equals("-0") ? "0" : t;
    }

    /**
     * Разбор числа из поля ввода. @return текст ошибки или null (значение — в out[0]).
     * Точка или запятая, пробелы игнорируются; только конечные числа в [min, max]; integer — только целые.
     */
    public static String parseNumber(String s, double min, double max, boolean integer, double[] out) {
        String t = s == null ? "" : s.trim().replace(" ", "").replace(',', '.');
        if (t.startsWith("+")) t = t.substring(1);
        if (t.isEmpty()) return "Пусто — впиши число";
        double v;
        try {
            v = Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return "«" + s.trim() + "» — не число";
        }
        if (!Double.isFinite(v)) return "Слишком большое число";
        if (t.matches(".*[eEdDfFxX].*")) return "«" + s.trim() + "» — не число";
        if (integer && v != Math.rint(v)) return "Нужно целое число";
        if (v < min) return "Не меньше " + num(min) + (min == 0 ? " (отрицательное нельзя)" : "");
        if (v > max) return "Не больше " + num(max);
        out[0] = v;
        return null;
    }

    public static final class Number extends Input {
        private final DoubleSupplier get;
        private final DoubleConsumer set;
        private final double min, max, step, bigStep;
        private final DoubleFunction<String> fmt;
        private boolean integer;

        public Number(
                String label,
                String hint,
                DoubleSupplier get,
                DoubleConsumer set,
                double min,
                double max,
                double step,
                double bigStep,
                DoubleFunction<String> fmt) {
            super(label, hint);
            this.get = get;
            this.set = set;
            this.min = min;
            this.max = max;
            this.step = step;
            this.bigStep = bigStep;
            this.fmt = fmt;
        }

        /** Только целые (поле в настройках — int). */
        public Number integer() {
            integer = true;
            return this;
        }

        /** Встроен в {@link Range}: поле рисуется в заданном месте, без своей карточки. */
        int ex = Integer.MIN_VALUE, ey, ew;

        boolean embedded() {
            return ex != Integer.MIN_VALUE;
        }

        @Override
        int controlWidth(Ctx c) {
            return embedded() ? ew : slider() ? sliderW() + 4 + valueW(c) : 92;
        }

        /** v1.16.0: в минималистичных темах — тонкий слайдер и значение текстом (клик по значению — ввод числом). */
        boolean slider() {
            return !embedded() && Double.isFinite(max - min) && max > min;
        }

        int sliderW() {
            return Math.max(40, Math.min(110, lastW / 4));
        }

        int valueW(Ctx c) {
            return Math.max(34, c.font().width(fmt.apply(max)) + 4);
        }
        /** {x трека, y линии, ширина трека, x значения}. */
        private int[] sl(Ctx c) {
            int h = height(c, lastW), vw = valueW(c), sw = sliderW();
            int vx = lastX + lastW - 2 - vw, tx = vx - 6 - sw;
            int ly = hintShown() ? lastY + 10 : lastY + (h - 1) / 2;
            return new int[] {tx, ly, sw, vx};
        }

        double frac() {
            double v = get.getAsDouble();
            return Math.max(0, Math.min(1, (v - min) / (max - min)));
        }

        void setFrac(Ctx c, double mx) {
            int[] s = sl(c);
            double t = Math.max(0, Math.min(1, (mx - s[0]) / Math.max(1, s[2])));
            double v = min + t * (max - min);
            v = min + Math.round((v - min) / step) * step;
            v = Math.round(v * 1e6) / 1e6;
            if (integer) v = Math.rint(v);
            v = Math.max(min, Math.min(max, v));
            if (v != get.getAsDouble()) set.accept(v);
        }

        /** {x, y, ширина} поля. */
        private int[] box(Ctx c) {
            if (embedded()) return new int[] {ex, ey, ew};
            if (slider()) {
                int[] s = sl(c);
                return new int[] {s[3] - 2, s[1] - 7, valueW(c) + 4};
            }
            int h = height(c, lastW), cw = controlWidth(c);
            return new int[] {lastX + lastW - cw - 6, lastY + (h - 3 - 14) / 2, cw};
        }

        @Override
        int maxLen() {
            return 16;
        }

        @Override
        boolean allowed(String ch) {
            return ch.matches("[0-9.,+\\- ]");
        }

        @Override
        String initialText() {
            return num(get.getAsDouble());
        }

        @Override
        String commitText(String text) {
            double[] out = new double[1];
            String err = parseNumber(text, min, max, integer, out);
            if (err != null) return err;
            if (out[0] != get.getAsDouble()) set.accept(out[0]);
            return null;
        }

        @Override
        String editTip() {
            return "Enter — принять, Esc — отменить · от " + num(min) + " до " + num(max) + (integer ? ", целое" : "");
        }

        @Override
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            if (!embedded()) drawCard(c, g, x, y, w, height(c, w), hover, false);
            int[] bx = box(c);
            int cw = bx[2], cx = bx[0], cy = bx[1];
            boolean en = on();
            if (!en && focused()) textFocus = null;
            if (focused()) {
                drawEditing(c, g, cx, cy, cw, hover);
                return;
            }
            double v = get.getAsDouble();
            if (slider()) {
                int[] s = sl(c);
                int tx = s[0], ly = s[1], tw = s[2];
                int kx = tx + (int) Math.round(frac() * tw);
                boolean ht = en && Ui.inside(mx, my, tx - 3, ly - 5, tw + 6, 10);
                g.fill(tx, ly, tx + tw, ly + 1, Ui.TRACK_OFF);
                g.fill(tx, ly, kx, ly + 1, en ? Ui.ACCENT : Ui.DIM);
                Ui.circle(
                        g,
                        kx,
                        ly,
                        ht || dragging == this ? 4 : 3,
                        en ? (ht || dragging == this ? Ui.ACCENT_HI : Ui.ACCENT) : Ui.DIM);
                boolean hv = en && Ui.inside(mx, my, s[3] - 2, ly - 7, valueW(c) + 4, 14);
                if (en && wheelFocus == this) g.fill(s[3], ly + 6, s[3] + valueW(c), ly + 7, Ui.ACCENT);
                String vs = Ui.ellipsize(c.font(), fmt.apply(v), valueW(c));
                Ui.textRight(g, c.font(), vs, s[3] + valueW(c), ly - 3, hv ? Ui.TEXT : en ? Ui.SUB : Ui.DIM);
                if (ht || hv) c.hand();
                if (hv)
                    c.tooltip("Клик — ввести число (" + num(min) + "…" + num(max) + "), потом колёсико тоже меняет");
                else if (ht)
                    c.tooltip("Тяни или кликни · колёсико после клика — точнее (Shift — шаг ×" + trim(bigStep / step)
                            + ")");
                return;
            }
            // поле (рамка — колёсико сейчас меняет это значение)
            if (en && wheelFocus == this) Ui.round(g, cx - 1, cy - 1, cw + 2, 16, 5, Ui.ACCENT);
            Ui.round(g, cx, cy, cw, 14, 4, Ui.FIELD);
            boolean hm = en && Ui.inside(mx, my, cx, cy, 16, 14) && v > min;
            boolean hp = en && Ui.inside(mx, my, cx + cw - 16, cy, 16, 14) && v < max;
            Ui.round(g, cx, cy, 16, 14, 4, hm ? Ui.ACCENT : Ui.BORDER);
            Ui.round(g, cx + cw - 16, cy, 16, 14, 4, hp ? Ui.ACCENT : Ui.BORDER);
            int arrowCol = en ? Ui.TEXT : Ui.DIM;
            Ui.textCentered(g, c.font(), "−", cx + 8, cy + 3, v > min ? arrowCol : Ui.DIM);
            Ui.textCentered(g, c.font(), "+", cx + cw - 8, cy + 3, v < max ? arrowCol : Ui.DIM);
            String s = Ui.ellipsize(c.font(), fmt.apply(v), cw - 36);
            Ui.textCentered(g, c.font(), s, cx + cw / 2, cy + 3, en ? Ui.TEXT : Ui.DIM);
            if (hm || hp) c.hand();
            boolean mid = hover && en && Ui.inside(mx, my, cx + 16, cy, cw - 32, 14);
            if (mid) c.hand();
            if (mid)
                c.tooltip(
                        wheelFocus == this
                                ? "Клик — ввести число, колёсико — изменить (Shift — шаг ×" + trim(bigStep / step) + ")"
                                : "Клик — ввести число вручную (" + num(min) + "…" + num(max)
                                        + "), потом колёсико тоже меняет");
        }

        private static String trim(double d) {
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }

        private void change(Ctx c, int dir) {
            double s = c.shift() ? bigStep : step;
            double v = get.getAsDouble() + dir * s;
            v = Math.round(v * 1e6) / 1e6; // убираем хвосты float, ручной ввод не округляем к шагу
            if (integer) v = Math.rint(v);
            v = Math.max(min, Math.min(max, v));
            set.accept(v);
        }

        @Override
        boolean click(Ctx c, double mx, double my, int button) {
            if (!on() || button != 0) return false;
            if (slider() && !focused()) {
                int[] s = sl(c);
                if (Ui.inside(mx, my, s[0] - 3, s[1] - 6, s[2] + 6, 12)) {
                    wheelFocus = this;
                    dragging = this;
                    setFrac(c, mx);
                    c.clickSound();
                    return true;
                }
                if (Ui.inside(mx, my, s[3] - 2, s[1] - 7, valueW(c) + 4, 14)) {
                    wheelFocus = this;
                    focus(c);
                    return true;
                }
                return false;
            }
            int[] bx = box(c);
            int cx = bx[0], cy = bx[1], cw = bx[2];
            if (!Ui.inside(mx, my, cx, cy, cw, 14)) return false;
            if (focused()) {
                placeCursor(c, mx, cx);
                return true;
            }
            wheelFocus = this;
            if (Ui.inside(mx, my, cx, cy, 16, 14)) {
                change(c, -1);
                c.clickSound();
                return true;
            }
            if (Ui.inside(mx, my, cx + cw - 16, cy, 16, 14)) {
                change(c, +1);
                c.clickSound();
                return true;
            }
            focus(c); // клик по середине — ввод числом
            return true;
        }

        @Override
        boolean scroll(Ctx c, double mx, double my, double amount) {
            if (!on() || wheelFocus != this) return false;
            if (slider()) {
                int[] s = sl(c);
                if (!Ui.inside(mx, my, s[0] - 3, s[1] - 7, s[3] + valueW(c) - s[0] + 5, 14)) return false;
                if (focused()) return true;
                change(c, amount > 0 ? 1 : -1);
                return true;
            }
            int[] bx = box(c);
            if (!Ui.inside(mx, my, bx[0], bx[1], bx[2], 14)) return false;
            if (focused()) return true; // пока печатаем, колёсико значение не трогает
            change(c, amount > 0 ? 1 : -1);
            return true;
        }
    }

    /** v1.16.0: слайдер, который сейчас тянут мышью (экран зовёт {@link #drag}/{@link #dragEnd}). */
    static Number dragging;

    public static boolean drag(Ctx c, double mx) {
        if (dragging == null) return false;
        if (!dragging.on() || !dragging.slider()) {
            dragging = null;
            return false;
        }
        dragging.setFrac(c, mx);
        return true;
    }

    public static void dragEnd() {
        dragging = null;
    }

    /** v1.11: «от – до» в одной строке (два числовых поля). Вместо двух строк «…: от» и «…: до». */
    public static final class Range extends Labeled {
        private final Number lo, hi;

        public Range(String label, String hint, Number lo, Number hi) {
            super(label, hint);
            this.lo = lo;
            this.hi = hi;
        }

        private int fieldW() {
            return Math.max(60, Math.min(84, (lastW / 2 - 22) / 2));
        }

        @Override
        int controlWidth(Ctx c) {
            return fieldW() * 2 + 12;
        }

        private void place(Ctx c) {
            int h = height(c, lastW), fw = fieldW();
            int cy = hintShown() ? lastY + 3 : lastY + (h - 1 - 14) / 2;
            int right = lastX + lastW - 2;
            hi.ex = right - fw;
            hi.ey = cy;
            hi.ew = fw;
            lo.ex = right - fw * 2 - 12;
            lo.ey = cy;
            lo.ew = fw;
            for (Number n : new Number[] {lo, hi}) {
                n.enabled = enabled;
                n.lastX = lastX;
                n.lastY = lastY;
                n.lastW = lastW;
                n.lastH = lastH;
            }
        }

        @Override
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            drawCard(c, g, x, y, w, height(c, w), hover, false);
            place(c);
            lo.render(c, g, x, y, w, mx, my, hover);
            hi.render(c, g, x, y, w, mx, my, hover);
            Ui.textCentered(g, c.font(), "–", lo.ex + lo.ew + 6, lo.ey + 3, on() ? Ui.SUB : Ui.DIM);
        }

        @Override
        boolean click(Ctx c, double mx, double my, int button) {
            place(c);
            return lo.click(c, mx, my, button) || hi.click(c, mx, my, button);
        }

        @Override
        boolean scroll(Ctx c, double mx, double my, double amount) {
            place(c);
            return lo.scroll(c, mx, my, amount) || hi.scroll(c, mx, my, amount);
        }
    }

    /** v1.11: поиск по всем настройкам (фильтр на лету, пока печатаешь). */
    public static final class Search extends Input {
        private final Consumer<String> onChange;
        private String query = "";

        public Search(Consumer<String> onChange) {
            super("Поиск", null);
            this.onChange = onChange;
        }

        public String query() {
            return query;
        }

        public void clear() {
            if (textFocus == this) textFocus = null;
            set("");
        }

        /** Ctrl+F: поставить фокус в поле поиска. */
        void focusField(Ctx c) {
            if (!focused()) focus(c);
        }

        @Override
        int leftPad() {
            return 18;
        }

        @Override
        int rightPad() {
            return 18;
        }

        private boolean hasText() {
            return focused() ? !buf.isEmpty() : !query.isEmpty();
        }

        private boolean overClear(double mx, double my, int cx, int cy, int cw) {
            return hasText() && Ui.inside(mx, my, cx + cw - 16, cy, 16, 15);
        }

        private void drawIcons(Ctx c, GuiGraphicsExtractor g, int cx, int cy, int cw, int mx, int my) {
            UiIcons.draw(g, UiIcons.SEARCH, cx + 4, cy + 1, 12, focused() ? Ui.ACCENT_HI : Ui.DIM);
            if (hasText()) {
                boolean hx = overClear(mx, my, cx, cy, cw);
                UiIcons.draw(g, UiIcons.CLOSE, cx + cw - 15, cy + 1, 12, hx ? Ui.DANGER : Ui.SUB);
                if (hx) {
                    c.hand();
                    c.tooltip("Очистить поиск");
                }
            }
        }

        private void set(String q) {
            if (!q.equals(query)) {
                query = q;
                onChange.accept(q);
            }
        }

        @Override
        public boolean shown() {
            return true;
        }

        @Override
        int height(Ctx c, int w) {
            return 22;
        }

        @Override
        int controlWidth(Ctx c) {
            return lastW - 12;
        }

        @Override
        int maxLen() {
            return 40;
        }

        @Override
        boolean allowed(String ch) {
            return true;
        }

        @Override
        String initialText() {
            return query;
        }

        @Override
        String commitText(String text) {
            set(text.strip());
            return null;
        }

        @Override
        String editTip() {
            return "На главной — поиск модулей; в настройках — параметров · Esc — выйти из поля, × — очистить";
        }

        @Override
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            int cx = x + 2, cy = y + 3, cw = w - 4;
            if (focused()) {
                if (!buf.strip().equals(query)) set(buf.strip());
                drawEditing(c, g, cx, cy, cw, hover);
                drawIcons(c, g, cx, cy, cw, mx, my);
                return;
            }
            boolean hf = Ui.inside(mx, my, cx, cy, cw, 14);
            Ui.roundBordered(g, cx, cy, cw, 15, 4, Ui.FIELD, hf ? Ui.SUB : Ui.BORDER);
            int avail = cw - leftPad() - rightPad();
            if (query.isEmpty()) Ui.text(g, c.font(), Ui.ellipsize(c.font(), "Поиск…", avail), cx + 18, cy + 3, Ui.DIM);
            else Ui.text(g, c.font(), Ui.ellipsize(c.font(), query, avail), cx + 18, cy + 3, Ui.TEXT);
            drawIcons(c, g, cx, cy, cw, mx, my);
            if (hf) c.hand();
        }

        @Override
        boolean click(Ctx c, double mx, double my, int button) {
            if (button != 0) return false;
            int cx = lastX + 2, cy = lastY + 3, cw = lastW - 4;
            if (!Ui.inside(mx, my, cx, cy, cw, 14)) return false;
            if (overClear(mx, my, cx, cy, cw)) {
                c.clickSound();
                if (focused()) {
                    buf = "";
                    cursor = 0;
                    set("");
                } else clear();
                return true;
            }
            if (!focused()) focus(c);
            else placeCursor(c, mx, cx);
            return true;
        }
    }

    // ── Выбор из списка «‹ значение ›» ───────────────────────────────────────

    public record Choice(String id, String label) {}

    public static final class Selector extends Labeled {
        private final Supplier<List<Choice>> options;
        private final Supplier<String> get;
        private final Consumer<String> set;

        public Selector(
                String label, String hint, Supplier<List<Choice>> options, Supplier<String> get, Consumer<String> set) {
            super(label, hint);
            this.options = options;
            this.get = get;
            this.set = set;
        }

        @Override
        int controlWidth(Ctx c) {
            return Math.min(150, Math.max(110, lastW / 2 - 10));
        }

        private String currentLabel() {
            String id = get.get();
            for (Choice ch : options.get()) if (ch.id().equals(id)) return ch.label();
            return id;
        }

        @Override
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            int h = height(c, w);
            drawCard(c, g, x, y, w, h, hover, false);
            int cw = controlWidth(c);
            int cx = ctrlX(x, w, cw), cy = ctrlY(y, h);
            boolean en = on();
            boolean hl = en && Ui.inside(mx, my, cx, cy, cw / 2, 14);
            boolean hr = en && Ui.inside(mx, my, cx + cw / 2, cy, cw - cw / 2, 14);
            if (en && wheelFocus == this) g.fill(cx, cy + 13, cx + cw, cy + 14, Ui.ACCENT);
            String s = Ui.ellipsize(c.font(), currentLabel(), cw - 22);
            int sw = c.font().width(s);
            Ui.textRight(g, c.font(), "›", cx + cw, cy + 3, hr ? Ui.ACCENT_HI : Ui.DIM);
            Ui.text(g, c.font(), s, cx + cw - 10 - sw, cy + 3, en ? (hl || hr ? Ui.TEXT : Ui.SUB) : Ui.DIM);
            Ui.text(g, c.font(), "‹", cx + cw - 18 - sw, cy + 3, hl ? Ui.ACCENT_HI : Ui.DIM);
            if (hl || hr) c.hand();
        }

        /** Один прямоугольник управления и для отрисовки, и для попаданий мыши. */
        private int ctrlX(int x, int w, int cw) {
            return x + w - cw - 2;
        }

        private int ctrlY(int y, int h) {
            return hintShown() ? y + 3 : y + (h - 1 - 14) / 2;
        }

        private void cycle(int dir) {
            List<Choice> list = options.get();
            if (list.isEmpty()) return;
            int idx = 0;
            for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(get.get())) idx = i;
            idx = Math.floorMod(idx + dir, list.size());
            set.accept(list.get(idx).id());
        }

        @Override
        boolean click(Ctx c, double mx, double my, int button) {
            if (!on()) return false;
            int h = height(c, lastW), cw = controlWidth(c);
            int cx = ctrlX(lastX, lastW, cw), cy = ctrlY(lastY, h);
            if (!Ui.inside(mx, my, cx, cy, cw, 14)) return false;
            wheelFocus = this;
            int dir = button == 1 ? -1 : (mx < cx + cw / 2.0 ? -1 : 1);
            cycle(dir);
            c.clickSound();
            return true;
        }

        @Override
        boolean scroll(Ctx c, double mx, double my, double amount) {
            if (!on() || wheelFocus != this) return false;
            int h = height(c, lastW), cw = controlWidth(c);
            int cx = ctrlX(lastX, lastW, cw), cy = ctrlY(lastY, h);
            if (!Ui.inside(mx, my, cx, cy, cw, 14)) return false;
            cycle(amount > 0 ? -1 : 1);
            return true;
        }
    }

    /** Explicit click-to-open list. Wheel never silently changes the selected value. */
    public static final class ListSelector extends Labeled {
        private final Supplier<List<Choice>> options;
        private final Supplier<String> get;
        private final Consumer<String> set;

        public ListSelector(
                String label, String hint, Supplier<List<Choice>> options, Supplier<String> get, Consumer<String> set) {
            super(label, hint);
            this.options = options;
            this.get = get;
            this.set = set;
        }

        @Override
        int controlWidth(Ctx c) {
            return Math.min(220, Math.max(110, lastW / 2 - 10));
        }

        @Override
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            int h = height(c, w);
            drawCard(c, g, x, y, w, h, hover, true);
            String id = get.get(), name = id;
            for (var ch : options.get())
                if (ch.id().equals(id)) {
                    name = ch.label();
                    break;
                }
            int cw = controlWidth(c), cx = x + w - cw - 2, cy = y + (h - 14) / 2;
            boolean hot = on() && Ui.inside(mx, my, x, y, w, h);
            Ui.textRight(
                    g, c.font(), Ui.ellipsize(c.font(), name, cw - 22), cx + cw - 18, cy + 3, on() ? Ui.SUB : Ui.DIM);
            Ui.textRight(g, c.font(), "▾", cx + cw - 2, cy + 3, hot ? Ui.ACCENT_HI : Ui.DIM);
            if (hot) c.hand();
        }

        @Override
        boolean click(Ctx c, double mx, double my, int button) {
            if (button != 0 || !on() || !Ui.inside(mx, my, lastX, lastY, lastW, height(c, lastW))) return false;
            var mc = Minecraft.getInstance();
            clearWheelFocus();
            c.clickSound();
            mc.setScreen(new ChoicePickerScreen(mc.screen, label, options.get(), get.get(), set));
            return true;
        }
    }

    // ── Ряд кнопок ───────────────────────────────────────────────────────────

    public enum Style {
        PRIMARY,
        SECONDARY,
        DANGER,
        SUCCESS
    }

    public static final class Btn {
        final Supplier<String> label;
        final Supplier<Style> style;
        final Runnable action;
        BooleanSupplier enabled = () -> true;
        String tip;
        int icon = UiIcons.NONE;

        public Btn(String label, Style style, Runnable action) {
            this(() -> label, () -> style, action);
        }

        public Btn(Supplier<String> label, Supplier<Style> style, Runnable action) {
            this.label = label;
            this.style = style;
            this.action = action;
        }

        public Btn(int icon, String label, Style style, Runnable action) {
            this(label, style, action);
            this.icon = icon;
        }

        public Btn(int icon, Supplier<String> label, Supplier<Style> style, Runnable action) {
            this(label, style, action);
            this.icon = icon;
        }

        public Btn enabledIf(BooleanSupplier s) {
            enabled = s;
            return this;
        }

        public Btn tip(String t) {
            tip = t;
            return this;
        }

        /** Иконка слева от подписи ({@link UiIcons}). */
        public Btn icon(int i) {
            icon = i;
            return this;
        }
    }

    private static final int BTN_ICON = 12;

    /** Подпись по центру; иконка (если есть) слева от неё, вся группа центрируется. */
    private static void buttonLabel(
            Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int icon, int color) {
        int iw = icon >= 0 ? BTN_ICON + (label.isEmpty() ? 0 : 4) : 0;
        String s = Ui.ellipsize(c.font(), label, w - 6 - iw);
        int total = iw + c.font().width(s), left = x + (w - total) / 2;
        if (icon >= 0) UiIcons.draw(g, icon, left, y + (h - BTN_ICON) / 2, BTN_ICON, color);
        Ui.text(g, c.font(), s, left + iw, y + (h - 8) / 2, color);
    }

    /** Рисует одну кнопку; возвращает true, если под мышью. Используется и вне строк. */
    public static boolean drawButton(
            Ctx c,
            GuiGraphicsExtractor g,
            int x,
            int y,
            int w,
            int h,
            String label,
            Style style,
            boolean enabled,
            int mx,
            int my) {
        return drawButton(c, g, x, y, w, h, label, UiIcons.NONE, style, enabled, mx, my);
    }

    public static boolean drawButton(
            Ctx c,
            GuiGraphicsExtractor g,
            int x,
            int y,
            int w,
            int h,
            String label,
            int icon,
            Style style,
            boolean enabled,
            int mx,
            int my) {
        boolean hover = enabled && Ui.inside(mx, my, x, y, w, h);
        int base = switch (style) {
            case PRIMARY -> Ui.ACCENT;
            case DANGER -> Ui.DANGER;
            case SUCCESS -> Ui.ON;
            case SECONDARY -> Ui.BORDER;
        };
        int col = !enabled ? Ui.alpha(Ui.BORDER, 0.5f) : hover ? Ui.lerp(base, 0xFFFFFFFF, 0.15f) : base;
        if (style == Style.DANGER || style == Style.SUCCESS) {
            Ui.roundBordered(
                    g,
                    x,
                    y,
                    w,
                    h,
                    4,
                    hover && enabled ? Ui.alpha(base, 0.14f) : Ui.WINDOW,
                    enabled ? Ui.alpha(base, hover ? 1f : 0.7f) : Ui.alpha(Ui.BORDER, 0.5f));
            buttonLabel(c, g, x, y, w, h, label, icon, !enabled ? Ui.DIM : base);
            if (hover) c.hand();
            return hover;
        }
        if (style == Style.SECONDARY) {
            Ui.roundBordered(
                    g,
                    x,
                    y,
                    w,
                    h,
                    4,
                    hover && enabled ? Ui.CARD_HOVER : Ui.WINDOW,
                    enabled ? Ui.lerp(Ui.BORDER, Ui.SUB, hover ? 0.5f : 0.2f) : Ui.alpha(Ui.BORDER, 0.5f));
            buttonLabel(c, g, x, y, w, h, label, icon, !enabled ? Ui.DIM : hover ? Ui.TEXT : Ui.SUB);
            if (hover) c.hand();
            return hover;
        }
        Ui.round(g, x, y, w, h, 4, col);
        int tc = !enabled ? Ui.DIM : Ui.ON_ACCENT; // SECONDARY уже нарисована выше
        buttonLabel(c, g, x, y, w, h, label, icon, tc);
        if (hover) c.hand();
        return hover;
    }

    public static final class Buttons extends Row {
        private final Btn[] buttons;

        public Buttons(Btn... buttons) {
            this.buttons = buttons;
        }

        @Override
        public String searchText() {
            StringBuilder b = new StringBuilder();
            for (Btn x : buttons) {
                b.append(x.label.get()).append(' ');
                if (x.tip != null) b.append(x.tip).append(' ');
            }
            return b.toString();
        }

        @Override
        int height(Ctx c, int w) {
            return 22;
        }

        private int bw(int w) {
            return (w - (buttons.length - 1) * 4) / buttons.length;
        }

        @Override
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            int bw = bw(w);
            for (int i = 0; i < buttons.length; i++) {
                Btn b = buttons[i];
                int bx = x + i * (bw + 4);
                boolean en = on() && b.enabled.getAsBoolean();
                if (drawButton(c, g, bx, y, bw, 18, b.label.get(), b.icon, b.style.get(), en, mx, my) && b.tip != null)
                    c.tooltip(b.tip);
            }
        }

        @Override
        boolean click(Ctx c, double mx, double my, int button) {
            if (!on() || button != 0) return false;
            int bw = bw(lastW);
            for (int i = 0; i < buttons.length; i++) {
                Btn b = buttons[i];
                int bx = lastX + i * (bw + 4);
                if (b.enabled.getAsBoolean() && Ui.inside(mx, my, bx, lastY, bw, 18)) {
                    c.clickSound();
                    b.action.run();
                    return true;
                }
            }
            return false;
        }
    }

    // ── Текстовая заметка (переносится по ширине) ────────────────────────────

    public static final class Note extends Row {
        private final Supplier<String> text;
        private final int color;

        public Note(String text, int color) {
            this(() -> text, color);
        }

        public Note(Supplier<String> text, int color) {
            this.text = text;
            this.color = color;
        }

        boolean empty() {
            String t = text.get();
            return t == null || t.isEmpty();
        }

        @Override
        public String searchText() {
            String t = text.get();
            return t == null ? "" : t;
        }

        @Override
        int height(Ctx c, int w) {
            String t = text.get();
            if (t == null || t.isEmpty()) return 0;
            return c.font().split(Component.literal(t), w - 16).size() * 10 + 8;
        }

        @Override
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            String t = text.get();
            if (t == null || t.isEmpty()) return;
            int h = height(c, w);
            g.fill(x, y + 2, x + 1, y + h - 5, Ui.alpha(color, 0.8f));
            int ly = y + 3;
            for (var line : c.font().split(Component.literal(t), w - 16)) {
                g.text(c.font(), line, x + 8, ly, Ui.lerp(color, Ui.SUB, 0.7f), false);
                ly += 10;
            }
        }
    }

    /** v1.16.2: своя отрисовка (превью и т. п.): высота и рисование — лямбдами. */
    public static final class Custom extends Row {
        public interface Painter {
            void paint(GuiGraphicsExtractor g, int x, int y, int w);
        }

        private final int h;
        private final Painter p;

        public Custom(int h, Painter p) {
            this.h = h;
            this.p = p;
        }

        @Override
        int height(Ctx c, int w) {
            return h;
        }

        @Override
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            p.paint(g, x, y, w);
        }
    }

    /** Пустой отступ. */
    public static final class Spacer extends Row {
        private final int h;

        public Spacer(int h) {
            this.h = h;
        }

        @Override
        int height(Ctx c, int w) {
            return h;
        }

        @Override
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {}
    }

    // ── Поле ввода текста/числа (клик — редактировать, Enter — принять, Esc — отменить) ──

    /** Поле, в котором сейчас печатают (TextField или число в Number), или null. */
    static Input textFocus;

    public static boolean textFocused() {
        return textFocus != null;
    }

    /** Строка с полем в фокусе (для «клик мимо — принять»). */
    public static Row focusedRow() {
        return textFocus;
    }

    /** Принять ввод (клик мимо, прокрутка, смена вкладки, закрытие). Неверное значение — откат. */
    public static void blurText() {
        Input f = textFocus;
        textFocus = null;
        if (f != null) {
            f.commitText(f.buf);
            f.error = null;
        }
    }

    /** Клавиша для поля в фокусе. @return true — съедена */
    public static boolean textKey(KeyEvent e) {
        Input f = textFocus;
        return f != null && f.key(e);
    }

    public static boolean textChar(CharacterEvent e) {
        Input f = textFocus;
        if (f == null || !e.isAllowedChatCharacter()) return false;
        f.insert(e.codepointAsString());
        return true;
    }

    /** Общая часть полей ввода: буфер, курсор, Enter/Esc/стрелки/вставка, ошибка под подписью. */
    abstract static class Input extends Labeled {
        String buf = "";
        int cursor;
        /** Текст ошибки последнего Enter или null. */
        String error;

        Input(String label, String hint) {
            super(label, hint);
        }

        abstract int maxLen();

        abstract boolean allowed(String ch);

        abstract String initialText();
        /** Применить; @return null — принято, иначе понятный текст ошибки. */
        abstract String commitText(String text);

        String editTip() {
            return "Enter — принять, Esc — отменить";
        }

        boolean focused() {
            return textFocus == this;
        }

        @Override
        String hintNow() {
            return focused() && error != null ? "⚠ " + error : hint;
        }

        @Override
        int hintColor() {
            return focused() && error != null ? Ui.DANGER : super.hintColor();
        }

        void focus(Ctx c) {
            if (textFocus != null && textFocus != this) blurText();
            textFocus = this;
            buf = initialText();
            cursor = buf.length();
            error = null;
            c.clickSound();
        }

        int leftPad() {
            return 4;
        }

        int rightPad() {
            return 4;
        }

        void placeCursor(Ctx c, double mx, int boxX) {
            int rel = (int) mx - (boxX + leftPad()), best = buf.length();
            for (int i = 0; i <= buf.length(); i++)
                if (c.font().width(buf.substring(0, i)) >= rel) {
                    best = i;
                    break;
                }
            cursor = best;
        }

        void drawEditing(Ctx c, GuiGraphicsExtractor g, int cx, int cy, int cw, boolean hover) {
            Font f = c.font();
            Ui.round(g, cx - 1, cy - 1, cw + 2, 16, 5, error != null ? Ui.DANGER : Ui.ACCENT);
            Ui.round(g, cx, cy, cw, 14, 4, Ui.FIELD);
            int lp = leftPad(), avail = cw - lp - rightPad();
            String vis = buf;
            int off = 0;
            while (f.width(vis) > avail - 2 && off < cursor) {
                off++;
                vis = buf.substring(off);
            }
            vis = f.plainSubstrByWidth(vis, avail);
            Ui.text(g, f, vis, cx + lp, cy + 3, Ui.TEXT);
            if ((Clock.millis() / 500) % 2 == 0) {
                int cur = Math.max(off, Math.min(cursor, off + vis.length()));
                int px = cx + lp + f.width(buf.substring(off, cur));
                g.fill(px, cy + 2, px + 1, cy + 12, Ui.ACCENT_HI);
            }
            if (hover) c.tooltip(error != null ? error + " · Esc — вернуть как было" : editTip());
        }

        void insert(String add) {
            StringBuilder ok = new StringBuilder();
            add.codePoints().forEach(cp -> {
                if (cp < 0x20 || cp == 0x7F || cp == '§') return;
                String ch = new String(Character.toChars(cp));
                if (allowed(ch)) ok.append(ch);
            });
            int room = maxLen() - buf.length();
            if (room <= 0 || ok.isEmpty()) return;
            String a = ok.length() > room ? ok.substring(0, room) : ok.toString();
            buf = buf.substring(0, cursor) + a + buf.substring(cursor);
            cursor += a.length();
            error = null;
        }

        boolean key(KeyEvent e) {
            switch (e.key()) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { // Enter
                    String err = commitText(buf);
                    if (err == null) {
                        textFocus = null;
                        error = null;
                    } else error = err;
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    textFocus = null;
                    error = null;
                    return true;
                } // Esc — отменить
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (cursor > 0) {
                        buf = buf.substring(0, cursor - 1) + buf.substring(cursor);
                        cursor--;
                        error = null;
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (cursor < buf.length()) {
                        buf = buf.substring(0, cursor) + buf.substring(cursor + 1);
                        error = null;
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    cursor = Math.max(0, cursor - 1);
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    cursor = Math.min(buf.length(), cursor + 1);
                    return true;
                }
                case GLFW.GLFW_KEY_HOME -> {
                    cursor = 0;
                    return true;
                }
                case GLFW.GLFW_KEY_END -> {
                    cursor = buf.length();
                    return true;
                }
                default -> {}
            }
            if (e.isPaste()) {
                insert(Minecraft.getInstance()
                        .keyboardHandler
                        .getClipboard()
                        .replace("\r\n", " ")
                        .replace('\n', ' '));
                return true;
            }
            if (e.hasControlDown() && e.key() == GLFW.GLFW_KEY_A) {
                cursor = buf.length();
                return true;
            }
            return true; // остальные клавиши в поле не уходят дальше (Tab, клавиша меню и т. п.)
        }
    }

    public static final class TextField extends Input {
        private final Supplier<String> get;
        /** Принять строку; false — значение не подходит (поле подсвечивается красным и остаётся в фокусе). */
        private final Predicate<String> commit;

        private final Predicate<String> allowedChar;
        private final int maxLen, width;
        private String errorText = "Не подходит — проверь значение и диапазон";

        /**
         * @param width ширина поля, 0 — половина строки
         * @param allowedChar какие символы можно печатать (null — любые допустимые в чате)
         */
        public TextField(
                String label,
                String hint,
                Supplier<String> get,
                Predicate<String> commit,
                Predicate<String> allowedChar,
                int maxLen,
                int width) {
            super(label, hint);
            this.get = get;
            this.commit = commit;
            this.allowedChar = allowedChar;
            this.maxLen = maxLen;
            this.width = width;
        }

        /** Числа: цифры, знак, точка/запятая. */
        public static Predicate<String> numeric() {
            return ch -> ch.matches("[0-9.,+\\-]");
        }

        /** Текст ошибки, если commit вернул false. */
        public TextField errorText(String t) {
            errorText = t;
            return this;
        }

        @Override
        int maxLen() {
            return maxLen;
        }

        @Override
        boolean allowed(String ch) {
            return allowedChar == null || allowedChar.test(ch);
        }

        @Override
        String initialText() {
            return get.get();
        }

        @Override
        String commitText(String text) {
            return commit.test(text) ? null : errorText;
        }

        @Override
        int controlWidth(Ctx c) {
            return width > 0 ? width : Math.min(160, Math.max(92, lastW / 2 - 10));
        }

        private int[] box(Ctx c) {
            int h = height(c, lastW), cw = controlWidth(c);
            return new int[] {lastX + lastW - cw - 6, lastY + (h - 3 - 14) / 2, cw};
        }

        @Override
        void render(Ctx c, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, boolean hover) {
            int h = height(c, w);
            drawCard(c, g, x, y, w, h, hover, false);
            int cw = controlWidth(c);
            int cx = x + w - cw - 6, cy = y + (h - 3 - 14) / 2;
            boolean en = on();
            if (!en && focused()) textFocus = null;
            Font f = c.font();
            if (focused()) {
                drawEditing(c, g, cx, cy, cw, hover);
                return;
            }
            boolean hf = en && Ui.inside(mx, my, cx, cy, cw, 14);
            Ui.round(g, cx, cy, cw, 14, 4, hf ? Ui.CARD_HOVER : Ui.FIELD);
            String s = Ui.ellipsize(f, get.get(), cw - 8);
            Ui.textCentered(g, f, s, cx + cw / 2, cy + 3, en ? Ui.TEXT : Ui.DIM);
            if (hf) {
                c.hand();
                c.tooltip("Кликни, чтобы ввести значение");
            }
        }

        @Override
        boolean click(Ctx c, double mx, double my, int button) {
            if (!on() || button != 0) return false;
            int[] b = box(c);
            if (!Ui.inside(mx, my, b[0], b[1], b[2], 14)) return false;
            if (!focused()) focus(c);
            else placeCursor(c, mx, b[0]);
            return true;
        }
    }
}
