package com.farmmacro.gui;

import java.util.regex.Pattern;

/** Pure validation shared by settings and stress tests. */
public final class UiStyle {
    private static final Pattern SAFE_NAME = Pattern.compile("[a-z0-9_]{1,40}");

    public static int color(String text, int fallback) {
        if (text == null || !isHex(text)) return fallback;
        return (fallback & 0xFF000000) | Integer.parseInt(text.substring(1), 16);
    }

    public static boolean validColor(String text) {
        return text != null && (text.isEmpty() || isHex(text));
    }

    /** {@code #RRGGBB}, ASCII hex digits only; called per colour per frame, so no regex. */
    private static boolean isHex(String text) {
        if (text.length() != 7 || text.charAt(0) != '#') return false;
        for (int i = 1; i < 7; i++) {
            char ch = text.charAt(i);
            if (ch > 'f' || Character.digit(ch, 16) < 0) return false;
        }
        return true;
    }

    public static boolean safeName(String name) {
        return name != null && SAFE_NAME.matcher(name).matches();
    }

    public static int opacity(int argb, int percent) {
        return ((int) Math.round(Math.clamp(percent, 0, 100) * 2.55) << 24) | (argb & 0xFFFFFF);
    }
}
