package com.farmmacro.hud;

import com.farmmacro.config.HudSlot;
import com.farmmacro.config.ModConfig;
import com.farmmacro.gui.Ui;
import com.farmmacro.gui.UiIcons;
import com.farmmacro.util.Clock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * HUD editor: slim collapsible toolbar at the bottom centre, a one-line hint, snapping guides and a readout for the
 * selected block. H collapses the toolbar; nothing else reserves screen space.
 */
public class HudEditorScreen extends Screen {
    private static final int BTN = 16, PAD = 2, BTN_GAP = 2, GROUP_GAP = 5, BAR_MARGIN = 4;

    private final Screen parent;
    private List<FarmHud.Rect> rects = List.of();
    private final List<ToolButton> buttons = new ArrayList<>();
    private String selectedId = "inventory", dragId;
    private double grabDx,
            grabDy,
            dragX,
            dragY,
            initialX,
            initialY,
            initialW,
            initialH,
            initialScale,
            panelGrabX,
            panelGrabY;
    private double guideX = Double.NaN, guideY = Double.NaN;
    private boolean resizing, showList, chromeHidden, listDrag;
    private int listScroll, listX, listY, barX, barY, barW, barH;
    private long resetAllUntil;
    private ToolButton listBtn, snapBtn, gridBtn, mergeBtn, hideBtn, resetBtn, allBtn, alwaysBtn, collapseBtn;

    /** Icon-only toggle button with Kaelix styling. */
    private static final class ToolButton extends Button {
        private final int icon;
        boolean on;
        int tint;
        boolean primary;

        ToolButton(int icon, Runnable action) {
            super(0, 0, BTN, BTN, Component.empty(), button -> action.run(), DEFAULT_NARRATION);
            this.icon = icon;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
            boolean lit = active && isHoveredOrFocused();
            int fill = primary
                    ? Ui.alpha(Ui.ACCENT, lit ? .55f : .38f)
                    : on ? Ui.alpha(Ui.ACCENT, lit ? .38f : .26f) : lit ? Ui.CARD_HOVER : Ui.CARD;
            int border = active && (on || primary || isFocused()) ? Ui.ACCENT : Ui.BORDER;
            Ui.roundBordered(g, getX(), getY(), getWidth(), getHeight(), 4, fill, border);
            int size = Math.min(12, Math.min(getWidth(), getHeight()) - 4);
            int color = !active ? Ui.DIM : tint != 0 ? tint : on || primary ? Ui.ACCENT_HI : Ui.TEXT;
            UiIcons.draw(g, icon, getX() + (getWidth() - size) / 2, getY() + (getHeight() - size) / 2, size, color);
        }
    }

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Редактор HUD"));
        this.parent = parent;
    }

    private ModConfig cfg() {
        return ModConfig.INSTANCE;
    }

    private int listW() {
        return Math.min(198, Math.max(80, width - 16));
    }

    private int listH() {
        return Math.min(280, Math.max(60, height - 16));
    }

    private int boundX(int x, int w) {
        return Math.clamp(x, 0, Math.max(0, width - w));
    }

    private int boundY(int y, int h) {
        return Math.clamp(y, 0, Math.max(0, height - h));
    }

    private ToolButton add(int icon, Runnable action) {
        var b = new ToolButton(icon, action);
        buttons.add(b);
        addRenderableWidget(b);
        return b;
    }

    @Override
    protected void init() {
        buttons.clear();
        listX = boundX(cfg().hudEditorListX < 0 ? (width - listW()) / 2 : cfg().hudEditorListX, listW());
        listY = boundY(cfg().hudEditorListY < 0 ? height - 30 - listH() : cfg().hudEditorListY, listH());
        gridBtn = add(UiIcons.GRID, () -> cfg().hudEditorGrid = !cfg().hudEditorGrid);
        snapBtn = add(UiIcons.PIN, () -> cfg().hudEditorSnap = !cfg().hudEditorSnap);
        mergeBtn = add(UiIcons.COPY, () -> {
            cfg().hudMerged = !cfg().hudMerged;
            dragId = null;
        });
        listBtn = add(UiIcons.FOLDER, () -> {
            showList = !showList;
            dragId = null;
        });
        hideBtn = add(UiIcons.EYE, this::toggleHidden);
        alwaysBtn = add(UiIcons.STAR, () -> {
            if (selectedId.equals("all")) return;
            var s = slot(selectedId);
            s.always = !s.always;
            s.hidden = false;
            if (s.always && selectedId.equals("inventory")) cfg().hudInventory = true;
        });
        resetBtn = add(UiIcons.UNDO, () -> {
            String id = selectedScaleId();
            var old = slot(id);
            var fresh = new HudSlot();
            fresh.always = old.always;
            fresh.hidden = old.hidden;
            cfg().hudSlots.put(id, fresh);
            dragId = null;
        });
        allBtn = add(UiIcons.TRASH, () -> {
            long now = Clock.millis();
            if (now >= resetAllUntil) {
                resetAllUntil = now + 3_000L;
                return;
            }
            cfg().hudSlots.clear();
            resetAllUntil = 0;
            dragId = null;
        });
        var done = add(UiIcons.CHECK, this::onClose);
        done.primary = true;
        done.setTooltip(Tooltip.create(Component.literal("Готово — сохранить и вернуться (Esc)")));
        collapseBtn = add(UiIcons.CHEVRON_DOWN, this::toggleChrome);
        positionButtons();
    }

    /** Toolbar geometry: groups of 3 · 3 · 2 · 2 buttons; shrinks buttons on very narrow screens. */
    private void positionButtons() {
        int n = buttons.size(), size = BTN;
        int fixed = PAD * 2 + 6 * BTN_GAP + 3 * GROUP_GAP;
        if (fixed + n * size > width - 8) size = Math.max(10, (width - 8 - fixed) / n);
        barW = chromeHidden ? size + PAD * 2 : fixed + n * size;
        barH = size + PAD * 2;
        barX = (width - barW) / 2;
        barY = height - barH - BAR_MARGIN;
        int x = barX + PAD;
        int[] groupEnd = {3, 6, 8, 10};
        int group = 0;
        for (int i = 0; i < n; i++) {
            var b = buttons.get(i);
            boolean isCollapse = b == collapseBtn;
            b.visible = !chromeHidden || isCollapse;
            b.setWidth(size);
            b.setHeight(size);
            b.setY(barY + PAD);
            if (chromeHidden) {
                b.setX(isCollapse ? barX + PAD : barX);
                continue;
            }
            b.setX(x);
            x += size + BTN_GAP;
            if (i + 1 == groupEnd[group]) {
                x += GROUP_GAP - BTN_GAP;
                group++;
            }
        }
    }

    private void toggleChrome() {
        chromeHidden = !chromeHidden;
        dragId = null;
        listDrag = false;
        positionButtons();
        setFocused(null);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Ui.sync(cfg().uiTheme);
        g.fill(0, 0, width, height, 0x1a000000);
        if (cfg().hudEditorGrid) {
            for (int x = 0; x < width; x += 8) g.fill(x, 0, x + 1, height, 0x10ffffff);
            for (int y = 0; y < height; y += 8) g.fill(0, y, width, y + 1, 0x10ffffff);
        }
        g.fill(width / 2, 0, width / 2 + 1, height, 0x28ffffff);
        g.fill(0, height / 2, width, height / 2 + 1, 0x28ffffff);
        rects = List.of();
        if (minecraft != null && minecraft.player != null) {
            FarmHud.prepare(minecraft);
            rects = FarmHud.layout(g, minecraft, true);
        }
    }

    private static Tooltip tip(String text) {
        return Tooltip.create(Component.literal(text));
    }

    private static String state(boolean value) {
        return value ? " — вкл" : " — выкл";
    }

    private void refreshButtons() {
        boolean chosen = rect(selectedId) != null, single = chosen && !selectedId.equals("all");
        var sl = cfg().hudSlots.get(selectedId);
        gridBtn.on = cfg().hudEditorGrid;
        gridBtn.setTooltip(tip("Сетка 8 px" + state(gridBtn.on) + ". Отдельно от прилипания"));
        snapBtn.on = cfg().hudEditorSnap;
        snapBtn.setTooltip(tip("Прилипание" + state(snapBtn.on) + ": сетка 8 px, края, центр и соседние плашки"));
        mergeBtn.on = cfg().hudMerged;
        mergeBtn.setTooltip(tip("Общая плашка" + state(mergeBtn.on) + ". Индивидуальные места сохраняются"));
        listBtn.on = showList;
        listBtn.setTooltip(tip("Список плашек" + state(showList) + ": скрытые и пустые, перетащи за заголовок"));
        hideBtn.active = single;
        hideBtn.on = sl != null && sl.hidden;
        hideBtn.setTooltip(tip(
                selectedId.equals("inventory") && !cfg().hudInventory
                        ? "Включить инвентарь (ПКМ по плашке тоже)"
                        : "Скрыть / показать выбранную (ПКМ по плашке)"));
        alwaysBtn.active = single;
        alwaysBtn.on = FarmHud.pinned(selectedId);
        alwaysBtn.setTooltip(tip(
                alwaysBtn.on
                        ? "Показ: всегда. Плашка видна и без задачи"
                        : "Показ: по ситуации. Нажми, чтобы показывать всегда (модуль не запускается)"));
        resetBtn.active = chosen;
        resetBtn.setTooltip(tip("Сброс выбранной: место, размер и личные цвета"));
        boolean pending = Clock.millis() < resetAllUntil;
        allBtn.tint = pending ? Ui.DANGER : 0;
        allBtn.on = pending;
        allBtn.setTooltip(
                tip(pending ? "Нажми ещё раз — сбросить ВСЕ плашки" : "Сброс всех: нажми дважды за 3 секунды"));
        collapseBtn.setTooltip(tip(chromeHidden ? "Показать панель (H)" : "Свернуть панель (H)"));
        collapseBtn.tint = 0;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        refreshButtons();
        g.nextStratum();
        for (var r : rects) {
            if (r.id().equals(selectedId) || r.id().equals(dragId)) continue;
            if (r.contains(mx, my) && !overChrome(mx, my)) outline(g, r, 0x80ffffff);
        }
        drawGuides(g);
        var chosen = rect(selectedId);
        if (chosen != null) {
            outline(g, chosen, Ui.ACCENT_HI);
            g.fill(
                    chosen.x() - 2,
                    chosen.y() - 2,
                    chosen.x() + chosen.w() + 2,
                    chosen.y() - 1,
                    Ui.alpha(Ui.ACCENT, .4f));
            g.fill(
                    chosen.x() - 2,
                    chosen.y() + chosen.h() + 1,
                    chosen.x() + chosen.w() + 2,
                    chosen.y() + chosen.h() + 2,
                    Ui.alpha(Ui.ACCENT, .4f));
            drawReadout(g, chosen);
        }
        drawHint(g);
        if (showList && !chromeHidden) drawList(g);
        Ui.roundBordered(g, barX, barY, barW, barH, 5, Ui.alpha(Ui.TOOLTIP, .94f), Ui.BORDER);
        super.extractRenderState(g, mx, my, delta);
    }

    private void drawGuides(GuiGraphicsExtractor g) {
        int color = Ui.alpha(Ui.ACCENT_HI, .85f);
        if (!Double.isNaN(guideX)) g.fill((int) Math.round(guideX), 0, (int) Math.round(guideX) + 1, height, color);
        if (!Double.isNaN(guideY)) g.fill(0, (int) Math.round(guideY), width, (int) Math.round(guideY) + 1, color);
    }

    private void drawReadout(GuiGraphicsExtractor g, FarmHud.Rect chosen) {
        var whole = rect(selectedScaleId());
        var sl = cfg().hudSlots.get(selectedScaleId());
        int px = whole == null ? chosen.x() : whole.x(), py = whole == null ? chosen.y() : whole.y();
        String text = chosen.label() + " · " + px + "," + py + " · " + Math.round((sl == null ? 1 : sl.scale) * 100)
                + "%" + (chosen.hidden() ? " · скрыта" : "");
        if (selectedId.equals("inventory") && !cfg().hudInventory) text += " · предпросмотр";
        text = Ui.ellipsize(font, text, width - 16);
        int w = font.width(text) + 10, h = 13;
        int x = Math.clamp(chosen.x(), 2, Math.max(2, width - w - 2));
        int y = chosen.y() - h - 3 >= 0 ? chosen.y() - h - 3 : Math.min(height - h - 2, chosen.y() + chosen.h() + 3);
        Ui.roundBordered(g, x, y, w, h, 4, Ui.alpha(Ui.TOOLTIP, .96f), Ui.ACCENT);
        Ui.text(g, font, text, x + 5, y + 3, Ui.TEXT);
    }

    private void drawHint(GuiGraphicsExtractor g) {
        boolean pending = Clock.millis() < resetAllUntil;
        String[] variants = pending
                ? new String[] {"Нажми «Сброс всех» ещё раз за 3 с — иначе ничего не сбросится"}
                : new String[] {
                    "ЛКМ — перенос · Ctrl+drag / колесо — размер · стрелки — 1 px (Shift — 10) · C — центр · H — панель",
                    "ЛКМ — перенос · колесо — размер · стрелки — сдвиг · H — панель",
                    "ЛКМ — перенос · колесо — размер · H — панель",
                    "H — панель · Esc — готово"
                };
        String text = variants[variants.length - 1];
        for (String v : variants)
            if (font.width(v) <= width - 24) {
                text = v;
                break;
            }
        text = Ui.ellipsize(font, text, width - 16);
        int w = font.width(text) + 12, x = (width - w) / 2;
        Ui.roundBordered(g, x, 3, w, 13, 6, Ui.alpha(Ui.TOOLTIP, .85f), pending ? Ui.DANGER : Ui.BORDER);
        Ui.text(g, font, text, x + 6, 6, pending ? Ui.WARN : Ui.SUB);
    }

    private static void outline(GuiGraphicsExtractor g, FarmHud.Rect r, int color) {
        int x = r.x(), y = r.y(), w = r.w(), h = r.h();
        g.fill(x - 1, y - 1, x + w + 1, y, color);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
        g.fill(x - 1, y, x, y + h, color);
        g.fill(x + w, y, x + w + 1, y + h, color);
    }

    private boolean overList(double x, double y) {
        return !chromeHidden && showList && Ui.inside(x, y, listX, listY, listW(), listH());
    }

    private boolean overBar(double x, double y) {
        return Ui.inside(x, y, barX, barY, barW, barH);
    }

    private boolean overChrome(double x, double y) {
        return overBar(x, y) || overList(x, y);
    }

    private List<FarmHud.Rect> listRects() {
        return rects.stream().filter(r -> !r.id().equals("all")).toList();
    }

    private int visibleRows() {
        return Math.max(0, (listH() - 32) / 21);
    }

    private void clampList() {
        listScroll = Math.clamp(listScroll, 0, Math.max(0, listRects().size() - visibleRows()));
    }

    private void drawList(GuiGraphicsExtractor g) {
        clampList();
        Ui.roundBordered(g, listX, listY, listW(), listH(), 5, Ui.TOOLTIP, Ui.BORDER);
        Ui.text(g, font, "Плашки · тащи заголовок", listX + 8, listY + 6, Ui.TEXT);
        var list = listRects();
        int count = Math.min(visibleRows(), list.size() - listScroll);
        for (int i = 0; i < count; i++) {
            var r = list.get(i + listScroll);
            int y = listY + 20 + i * 21;
            if (r.id().equals(selectedId)) Ui.round(g, listX + 4, y, listW() - 8, 20, 3, Ui.alpha(Ui.ACCENT, .18f));
            boolean off = r.hidden() || r.id().equals("inventory") && !cfg().hudInventory;
            Ui.text(g, font, Ui.ellipsize(font, r.label(), listW() - 42), listX + 9, y + 6, off ? Ui.SUB : Ui.TEXT);
            UiIcons.draw(g, UiIcons.EYE, listX + listW() - 21, y + 4, 12, off ? Ui.WARN : Ui.ON);
        }
        if (count > 0 && list.size() > count)
            Ui.text(
                    g,
                    font,
                    (listScroll + 1) + "–" + (listScroll + count) + " / " + list.size(),
                    listX + 8,
                    listY + listH() - 11,
                    Ui.SUB);
    }

    private FarmHud.Rect hit(double x, double y) {
        if (overChrome(x, y)) return null;
        FarmHud.Rect best = null;
        for (var r : rects) if (r.contains(x, y) && (best == null || r.sub() || !best.sub())) best = r;
        return best;
    }

    private FarmHud.Rect rect(String id) {
        for (var r : rects) if (r.id().equals(id)) return r;
        return null;
    }

    private String selectedScaleId() {
        var r = rect(selectedId);
        return r != null && r.sub() ? "all" : selectedId;
    }

    private HudSlot slot(String id) {
        return cfg().hudSlots.computeIfAbsent(id, k -> new HudSlot());
    }

    private void toggleHidden() {
        if (selectedId.equals("all") || rect(selectedId) == null) return;
        var s = slot(selectedId);
        if (selectedId.equals("inventory") && !cfg().hudInventory) {
            cfg().hudInventory = true;
            s.hidden = false;
            s.always = true;
            return;
        }
        s.hidden = !s.hidden;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (overBar(e.x(), e.y())) {
            super.mouseClicked(e, doubled);
            return true;
        }
        if (overList(e.x(), e.y())) {
            if (e.y() < listY + 18 && e.button() == 0) {
                listDrag = true;
                panelGrabX = e.x() - listX;
                panelGrabY = e.y() - listY;
                dragId = null;
                setFocused(null);
                return true;
            }
            var list = listRects();
            int i = (int) (e.y() - listY - 20) / 21 + listScroll;
            if (e.y() >= listY + 20 && i >= listScroll && i < Math.min(list.size(), listScroll + visibleRows())) {
                selectedId = list.get(i).id();
                if (e.button() == 1 || e.x() > listX + listW() - 30) toggleHidden();
            }
            return true;
        }
        var r = hit(e.x(), e.y());
        if (r == null) return false;
        selectedId = r.id();
        resetAllUntil = 0;
        if (e.button() == 1) {
            toggleHidden();
            return true;
        }
        if (e.button() != 0) return false;
        dragId = selectedScaleId();
        var whole = rect(dragId);
        if (whole == null) {
            dragId = null;
            return true;
        }
        var s = slot(dragId);
        if (!s.placed) HudLayout.store(s, whole.x(), whole.y(), whole.w(), whole.h(), width, height);
        grabDx = e.x() - whole.x();
        grabDy = e.y() - whole.y();
        dragX = e.x();
        dragY = e.y();
        initialX = whole.x();
        initialY = whole.y();
        initialW = whole.w();
        initialH = whole.h();
        initialScale = s.scale;
        resizing = minecraft.hasControlDown();
        return true;
    }

    /** Edges and centres of the other blocks along one axis, as snapping targets. */
    private double[] neighbourTargets(String skipId, boolean horizontal) {
        List<Double> out = new ArrayList<>();
        for (var o : rects) {
            if (o.sub() || o.id().equals(skipId) || o.id().equals("all") && !cfg().hudMerged) continue;
            double a = horizontal ? o.x() : o.y(), size = horizontal ? o.w() : o.h();
            out.add(a);
            out.add(a + size / 2.0);
            out.add(a + size);
        }
        double[] arr = new double[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (listDrag) {
            listX = boundX((int) Math.round(e.x() - panelGrabX), listW());
            listY = boundY((int) Math.round(e.y() - panelGrabY), listH());
            return true;
        }
        if (dragId == null) return super.mouseDragged(e, dx, dy);
        var r = rect(dragId);
        if (r == null) return true;
        var s = slot(dragId);
        if (resizing) {
            guideX = Double.NaN;
            guideY = Double.NaN;
            double sx = (e.x() - dragX) / Math.max(1, initialW), sy = (e.y() - dragY) / Math.max(1, initialH);
            double amount = Math.abs(sx) >= Math.abs(sy) ? sx : sy;
            s.scale = Math.clamp(initialScale * (1 + amount), .5, 2);
            double ratio = s.scale / initialScale;
            HudLayout.store(s, initialX, initialY, initialW * ratio, initialH * ratio, width, height);
        } else {
            double x = e.x() - grabDx, y = e.y() - grabDy;
            boolean snapOn = cfg().hudEditorSnap;
            if (snapOn) {
                x = Math.round(x / 8) * 8;
                y = Math.round(y / 8) * 8;
            }
            double tol = snapOn ? HudLayout.SNAP : 0.5;
            var sx = HudLayout.snapAxis(x, r.w(), width, HudLayout.targets(width, neighbourTargets(dragId, true)), tol);
            var sy = HudLayout.snapAxis(
                    y, r.h(), height, HudLayout.targets(height, neighbourTargets(dragId, false)), tol);
            guideX = sx.guide();
            guideY = sy.guide();
            if (snapOn) {
                x = sx.pos();
                y = sy.pos();
            }
            HudLayout.store(s, x, y, r.w(), r.h(), width, height);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        guideX = Double.NaN;
        guideY = Double.NaN;
        if (e.button() == 0 && (dragId != null || listDrag)) {
            dragId = null;
            listDrag = false;
            super.mouseReleased(e);
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (overList(mx, my)) {
            listScroll -= (int) Math.signum(sy);
            clampList();
            return true;
        }
        if (overChrome(mx, my)) return true;
        var r = hit(mx, my);
        if (r == null || sy == 0) return super.mouseScrolled(mx, my, sx, sy);
        selectedId = r.id();
        String id = selectedScaleId();
        var whole = rect(id);
        if (whole == null) return true;
        var s = slot(id);
        double old = s.scale;
        s.scale = HudLayout.scaleStep(old, sy);
        double ratio = s.scale / old;
        double nw = whole.w() * ratio, nh = whole.h() * ratio;
        HudLayout.store(s, whole.x() + (whole.w() - nw) / 2, whole.y() + (whole.h() - nh) / 2, nw, nh, width, height);
        return true;
    }

    private void placeSelected(boolean center, int dx, int dy) {
        var r = rect(selectedScaleId());
        if (r == null) return;
        double x = center ? Math.max(0, (width - r.w()) / 2.0) : r.x() + dx;
        double y = center ? Math.max(0, (height - r.h()) / 2.0) : r.y() + dy;
        HudLayout.store(slot(selectedScaleId()), x, y, r.w(), r.h(), width, height);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == 72) {
            toggleChrome();
            return true;
        }
        if (e.key() == 67) {
            placeSelected(true, 0, 0);
            return true;
        }
        if (e.key() >= 262 && e.key() <= 265) {
            int step = e.hasShiftDown() ? 10 : 1;
            placeSelected(
                    false,
                    e.key() == 262 ? step : e.key() == 263 ? -step : 0,
                    e.key() == 264 ? step : e.key() == 265 ? -step : 0);
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        cfg().hudEditorListX = listX;
        cfg().hudEditorListY = listY;
        cfg().sanitize();
        ModConfig.save();
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
