package com.farmmacro.macro;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Safe file names for user-given macro/route names (Windows-safe, bounded length). */
public final class FileNames {
    private FileNames() {}

    static final int MAX_LENGTH = 64;
    private static final Pattern RESERVED = Pattern.compile("(?i)(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(\\..*)?");

    /** Empty string when nothing usable is left. Ordinary names keep their historic form. */
    public static String sanitize(String name) {
        String s = name.trim()
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("^\\.+", "")
                .replaceAll("\\.+$", "");
        if (s.isEmpty()) return s;
        if (RESERVED.matcher(s).matches()) s = "_" + s;
        if (s.length() > MAX_LENGTH) {
            int cut = MAX_LENGTH;
            if (Character.isHighSurrogate(s.charAt(cut - 1))) cut--;
            s = s.substring(0, cut).replaceAll("\\.+$", "") + "-" + hash(name.trim());
        }
        return s;
    }

    private static String hash(String s) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
