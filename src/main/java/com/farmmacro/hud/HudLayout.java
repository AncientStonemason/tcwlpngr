package com.farmmacro.hud;

import com.farmmacro.config.HudSlot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v1.14: математика редактора HUD без Minecraft (проверка — PlayersCheck): из места на экране в якорь и обратно,
 * прилипание к краям и центру, масштаб. Все размеры — в пикселях GUI, w/h — уже с масштабом.
 */
public final class HudLayout {
    private HudLayout() {}

    public static final int MARGIN = 6, SNAP = 5;
    public static final double SCALE_MIN = 0.5, SCALE_MAX = 2.0, SCALE_STEP = 0.1;

    /** Левый верхний угол плашки w×h на экране sw×sh по слоту (с удержанием на экране). */
    public static int[] place(HudSlot s, double w, double h, int sw, int sh) {
        return new int[] {
            (int) Math.round(clamp(axis(s.ax, s.ox, w, sw), w, sw)),
            (int) Math.round(clamp(axis(s.ay, s.oy, h, sh), h, sh))
        };
    }

    private static double axis(double a, double off, double size, int screen) {
        if (a <= 0.25) return off;
        if (a >= 0.75) return screen - size - off;
        return screen / 2.0 + off - size / 2.0;
    }

    private static double clamp(double v, double size, int screen) {
        return Math.max(0, Math.min(Math.max(0, screen - size), v));
    }

    /** Записать в слот место (x, y) плашки w×h: якорь — ближайшая треть экрана. */
    public static void store(HudSlot s, double x, double y, double w, double h, int sw, int sh) {
        x = clamp(x, w, sw);
        y = clamp(y, h, sh);
        s.placed = true;
        s.ax = anchor(x + w / 2, sw);
        s.ay = anchor(y + h / 2, sh);
        s.ox = offset(s.ax, x, w, sw);
        s.oy = offset(s.ay, y, h, sh);
    }

    private static double anchor(double center, int screen) {
        return center < screen / 3.0 ? 0 : center > screen * 2 / 3.0 ? 1 : 0.5;
    }

    private static double offset(double a, double pos, double size, int screen) {
        if (a <= 0.25) return pos;
        if (a >= 0.75) return screen - size - pos;
        return pos + size / 2.0 - screen / 2.0;
    }

    /** Прилипание по одной оси: к отступу у краёв и к центру экрана. */
    public static double snap(double pos, double size, int screen) {
        double left = MARGIN, right = screen - size - MARGIN, center = screen / 2.0 - size / 2.0;
        if (Math.abs(pos - left) <= SNAP) return left;
        if (Math.abs(pos - right) <= SNAP) return right;
        if (Math.abs(pos - center) <= SNAP) return center;
        return clamp(pos, size, screen);
    }

    /** Колёсико: масштаб ± шаг, 0.5…2.0, округление до 0.1. */
    public static double scaleStep(double scale, double wheel) {
        double v = scale + Math.signum(wheel) * SCALE_STEP;
        v = Math.round(v * 10) / 10.0;
        return Math.max(SCALE_MIN, Math.min(SCALE_MAX, v));
    }

    // ── Раскладка по умолчанию ──────────────────────────────────────────────

    public static final int GAP = 4, BOTTOM_RESERVE = 20;
    public static final int GROUP_LEFT = 0, GROUP_RIGHT = 1, GROUP_BOTTOM_LEFT = 2;

    /** Плашка без сохранённого места: размеры уже с масштабом. */
    public record Item(String id, int w, int h, int group) {}

    /**
     * Аккуратные колонки: слева — панели-дашборд, справа — остальные, инвентарь — внизу слева. Колонка заполняется
     * сверху вниз, не влезло — следующая колонка ближе к центру. Если слева и справа не хватает ширины, плашки идут
     * одним потоком слева направо. Снизу оставлено BOTTOM_RESERVE под панель редактора и хотбар. Результат
     * детерминирован: порядок = порядок items. Ключ — id, значение — {x, y}.
     */
    public static Map<String, int[]> flow(List<Item> items, int sw, int sh) {
        int limit = sh - MARGIN - BOTTOM_RESERVE;
        List<Item> left = new ArrayList<>(), right = new ArrayList<>(), bottom = new ArrayList<>();
        for (Item it : items) {
            if (it.group() == GROUP_BOTTOM_LEFT) bottom.add(it);
            else if (it.group() == GROUP_LEFT) left.add(it);
            else right.add(it);
        }
        int bottomH = 0, bottomW = 0;
        for (Item it : bottom) {
            bottomH += it.h() + GAP;
            bottomW = Math.max(bottomW, it.w());
        }
        Map<String, int[]> out = new HashMap<>();
        List<List<Item>> lc = columns(left, limit - bottomH, limit);
        List<List<Item>> rc = columns(right, limit, limit);
        int leftW = columnsWidth(lc, bottomW), rightW = columnsWidth(rc, 0);
        if (MARGIN + leftW + (leftW > 0 && rightW > 0 ? GAP : 0) + rightW + MARGIN <= sw) {
            fill(out, lc, MARGIN, false, bottomW, sw, sh);
            fill(out, rc, sw - MARGIN, true, 0, sw, sh);
            int by = limit;
            for (int i = bottom.size() - 1; i >= 0; i--) {
                Item it = bottom.get(i);
                by -= it.h();
                out.put(it.id(), new int[] {MARGIN, Math.max(0, by)});
                by -= GAP;
            }
            return out;
        }
        List<Item> all = new ArrayList<>(right);
        all.addAll(left);
        all.addAll(bottom);
        List<List<Item>> best = null;
        int bestW = Integer.MAX_VALUE;
        List<java.util.Comparator<Item>> orders = List.of(
                (a, b) -> Integer.compare(b.w(), a.w()),
                (a, b) -> Integer.compare(b.h(), a.h()),
                (a, b) -> Integer.compare(b.w() * b.h(), a.w() * a.h()),
                (a, b) -> 0);
        for (var order : orders) {
            List<Item> sorted = new ArrayList<>(all);
            sorted.sort(order);
            List<List<Item>> cols = firstFit(sorted, limit);
            int w = columnsWidth(cols, 0);
            if (w < bestW) {
                bestW = w;
                best = cols;
            }
        }
        fill(out, best == null ? List.of() : best, MARGIN, false, 0, sw, sh);
        return out;
    }

    /** Каждая плашка — в первую колонку, где ещё есть место по высоте; иначе новая колонка. */
    private static List<List<Item>> firstFit(List<Item> items, int limit) {
        List<List<Item>> cols = new ArrayList<>();
        List<Integer> used = new ArrayList<>();
        for (Item it : items) {
            int target = -1;
            for (int i = 0; i < cols.size() && target < 0; i++) if (used.get(i) + it.h() <= limit) target = i;
            if (target < 0) {
                cols.add(new ArrayList<>());
                used.add(MARGIN);
                target = cols.size() - 1;
            }
            cols.get(target).add(it);
            used.set(target, used.get(target) + it.h() + GAP);
        }
        return cols;
    }

    private static List<List<Item>> columns(List<Item> items, int firstLimit, int limit) {
        List<List<Item>> cols = new ArrayList<>();
        List<Item> cur = new ArrayList<>();
        int y = MARGIN;
        for (Item it : items) {
            int lim = cols.isEmpty() ? firstLimit : limit;
            if (!cur.isEmpty() && y + it.h() > lim) {
                cols.add(cur);
                cur = new ArrayList<>();
                y = MARGIN;
            }
            cur.add(it);
            y += it.h() + GAP;
        }
        if (!cur.isEmpty()) cols.add(cur);
        return cols;
    }

    private static int columnWidth(List<Item> col) {
        int w = 0;
        for (Item it : col) w = Math.max(w, it.w());
        return w;
    }

    private static int columnsWidth(List<List<Item>> cols, int firstMin) {
        if (cols.isEmpty()) return firstMin;
        int total = 0;
        for (int i = 0; i < cols.size(); i++)
            total += Math.max(i == 0 ? firstMin : 0, columnWidth(cols.get(i))) + (i > 0 ? GAP : 0);
        return total;
    }

    private static void fill(
            Map<String, int[]> out, List<List<Item>> cols, int edge, boolean fromRight, int firstMin, int sw, int sh) {
        int cx = edge;
        for (int i = 0; i < cols.size(); i++) {
            List<Item> col = cols.get(i);
            int cw = columnWidth(col);
            if (i == 0) cw = Math.max(cw, firstMin);
            int y = MARGIN;
            for (Item it : col) {
                int x = fromRight ? cx - it.w() : cx;
                out.put(it.id(), new int[] {(int) clamp(x, it.w(), sw), (int) clamp(y, it.h(), sh)});
                y += it.h() + GAP;
            }
            cx += fromRight ? -(cw + GAP) : cw + GAP;
        }
    }

    // ── Направляющие при перетаскивании ────────────────────────────────────

    /** Результат прилипания по оси: новая позиция и координата направляющей (NaN — нет). */
    public record Snap(double pos, double guide) {}

    /**
     * Прилипание краёв/центра плашки size к целям (края экрана, отступы, центр, края соседей) в пределах tol.
     * Выбирается ближайшее совпадение; направляющая — координата цели.
     */
    public static Snap snapAxis(double pos, double size, int screen, double[] targets, double tol) {
        double best = Double.POSITIVE_INFINITY, bestPos = pos, guide = Double.NaN;
        double[] offs = {0, size / 2.0, size};
        for (double t : targets)
            for (double o : offs) {
                double d = t - o - pos;
                if (Math.abs(d) <= tol && Math.abs(d) < best) {
                    best = Math.abs(d);
                    bestPos = t - o;
                    guide = t;
                }
            }
        return new Snap(clamp(bestPos, size, screen), guide);
    }

    /** Цели для оси: края экрана и отступы, центр, затем extra (края соседей). */
    public static double[] targets(int screen, double[] extra) {
        double[] t = new double[5 + extra.length];
        t[0] = 0;
        t[1] = MARGIN;
        t[2] = screen / 2.0;
        t[3] = screen - MARGIN;
        t[4] = screen;
        System.arraycopy(extra, 0, t, 5, extra.length);
        return t;
    }
}
