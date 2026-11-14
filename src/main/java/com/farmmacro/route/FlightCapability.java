package com.farmmacro.route;

import com.farmmacro.util.Clock;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Read-only capability. Survival is valid; configuration never grants permission or changes abilities. */
public final class FlightCapability {
    // Слабые ссылки: после выхода из мира статическое поле не должно держать игрока и весь ClientLevel.
    private static WeakReference<LocalPlayer> player = new WeakReference<>(null);
    private static WeakReference<Object> world = new WeakReference<>(null);
    private static Boolean granted, serverFlying;
    private static long observed;

    public static boolean allowed(LocalPlayer p) {
        return p != null && (p.getAbilities().mayfly || p.getAbilities().flying);
    }

    public static void packet(Minecraft mc, boolean permission, boolean flying) {
        player = new WeakReference<>(mc.player);
        world = new WeakReference<>(mc.level);
        granted = permission;
        serverFlying = flying;
        observed = Clock.millis();
    }

    public static String describe(Minecraft mc) {
        if (mc.player == null || mc.level == null) return "Полёт: нет игрока/мира";
        var a = mc.player.getAbilities();
        String packet = player.get() == mc.player && world.get() == mc.level && Clock.millis() - observed < 30_000L
                ? "; пакет сервера: разрешение=" + granted + ", flying=" + serverFlying
                : "; свежий пакет способностей не наблюдался";
        return "Полёт: mayfly=" + a.mayfly + ", flying=" + a.flying + packet
                + (!allowed(mc.player)
                        ? ". Сервер не разрешил полёт; настройка не выдаёт его."
                        : ". Доступен также в Survival.");
    }

    private FlightCapability() {}
}
