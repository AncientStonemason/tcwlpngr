package com.farmmacro.gui;

import java.util.regex.Pattern;

/** Pure validation shared by settings and stress tests. */
public final class UiStyle {
    // Ui.sync вызывается каждый кадр: паттерны компилируем один раз.
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,40}");

    public static int color(String text, int fallback) {
        if (text == null || !HEX.matcher(text).matches()) return fallback;
        return (fallback & 0xFF000000) | Integer.parseInt(text.substring(1), 16);
    }

    public static boolean validColor(String text) {
        return text != null && (text.isEmpty() || HEX.matcher(text).matches());
    }

    public static boolean safeName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public static int opacity(int argb, int percent) {
        return ((int) Math.round(Math.clamp(percent, 0, 100) * 2.55) << 24) | (argb & 0xFFFFFF);
    }
}
