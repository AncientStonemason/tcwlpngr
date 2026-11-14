package com.farmmacro.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Чистые помощники для нормы настроек и списков модулей меню (без Minecraft). */
public final class ConfigDefaults {
    private ConfigDefaults() {}

    public static final int FAVOURITES_MAX = 32, RECENT_MAX = 4;

    /** Идентификаторы модулей каталога меню; сверяется тестом с {@code gui.ModuleCatalog}. */
    public static final Set<String> MODULE_IDS = Set.of(
            "farms",
            "recording",
            "launch",
            "motion",
            "fishing",
            "combat",
            "zealots",
            "combat_panic",
            "camera",
            "hud",
            "economy",
            "theme",
            "route_display",
            "freecam",
            "cosmetics",
            "panic_garden",
            "keys",
            "configs",
            "performance");

    /** Убрать мусор, повторы и неизвестные id, оставить не больше {@code max} (порядок сохраняется). */
    public static List<String> cleanModuleIds(@Nullable List<String> ids, int max) {
        List<String> out = new ArrayList<>();
        if (ids == null) return out;
        for (String id : ids) {
            if (out.size() >= max) break;
            if (id != null && MODULE_IDS.contains(id) && !out.contains(id)) out.add(id);
        }
        return out;
    }

    /** Новый список «недавних»: {@code id} первым, без повторов, не больше {@link #RECENT_MAX}. */
    public static List<String> pushRecent(@Nullable List<String> recent, String id) {
        List<String> out = new ArrayList<>();
        out.add(id);
        if (recent != null) for (String r : recent) if (!r.equals(id) && out.size() < RECENT_MAX) out.add(r);
        return out;
    }

    /** Совпадают ли два значения настройки (числа — с допуском на хвосты float). */
    public static boolean sameValue(@Nullable Object a, @Nullable Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            double p = x.doubleValue(), q = y.doubleValue();
            return Math.abs(p - q) <= 1e-6 * Math.max(1, Math.max(Math.abs(p), Math.abs(q)));
        }
        return java.util.Objects.equals(a, b);
    }
}
