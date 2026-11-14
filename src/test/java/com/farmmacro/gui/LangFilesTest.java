package com.farmmacro.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** en_us и ru_ru — одинаковые ключи; всё, что код спрашивает через {@link Tr}, есть в обоих. */
class LangFilesTest {
    private static final Pattern CYRILLIC = Pattern.compile("[\\u0400-\\u04FF]");

    private static Map<String, String> lang(String code) throws IOException {
        try (InputStream in = LangFilesTest.class.getResourceAsStream("/assets/farmmacro/lang/" + code + ".json")) {
            if (in == null) throw new IOException("нет " + code);
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, String> m = new TreeMap<>();
            o.entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsString()));
            return m;
        }
    }

    private static List<String> placeholders(String s) {
        List<String> found = new ArrayList<>(Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]")
                .matcher(s)
                .results()
                .map(r -> r.group())
                .toList());
        Collections.sort(found);
        return found;
    }

    @Test
    void sameKeysAndPlaceholders() throws IOException {
        Map<String, String> en = lang("en_us"), ru = lang("ru_ru");
        Set<String> onlyEn = new TreeSet<>(en.keySet()), onlyRu = new TreeSet<>(ru.keySet());
        onlyEn.removeAll(ru.keySet());
        onlyRu.removeAll(en.keySet());
        assertTrue(onlyEn.isEmpty() && onlyRu.isEmpty(), "только en_us: " + onlyEn + ", только ru_ru: " + onlyRu);
        for (String k : en.keySet()) {
            assertFalse(en.get(k).isBlank(), "пусто в en_us: " + k);
            assertFalse(ru.get(k).isBlank(), "пусто в ru_ru: " + k);
            assertEquals(placeholders(en.get(k)), placeholders(ru.get(k)), "разные подстановки %s/%d: " + k);
            assertFalse(CYRILLIC.matcher(en.get(k)).find(), "русский текст в en_us: " + k);
        }
    }

    @Test
    void everyCatalogKeyExists() throws IOException {
        Map<String, String> en = lang("en_us"), ru = lang("ru_ru");
        for (int i = 0; i < ModuleCatalog.CATEGORIES.length; i++) {
            assertTrue(
                    en.containsKey("farmmacro.category." + i) && ru.containsKey("farmmacro.category." + i),
                    "категория " + i);
            assertEquals(ModuleCatalog.CATEGORIES[i], ru.get("farmmacro.category." + i), "ru_ru = текст по умолчанию");
        }
        Set<String> ids = new TreeSet<>();
        for (ModuleCatalog.Module m : ModuleCatalog.ALL) {
            assertTrue(ids.add(m.id()), "повтор id " + m.id());
            for (String suffix : new String[] {".title", ".desc"}) {
                String key = "farmmacro.module." + m.id() + suffix;
                assertTrue(en.containsKey(key), "нет в en_us: " + key);
                assertTrue(ru.containsKey(key), "нет в ru_ru: " + key);
            }
        }
    }

    /** Tr.t("ключ", "текст") с литералами; допускает перенос строки после скобки и запятой. */
    private static final Pattern TR_CALL =
            Pattern.compile("Tr\\.t\\(\\s*\"([a-z0-9_.]+)\"\\s*,\\s*\"((?:[^\"\\\\\\n]|\\\\.)*)\"\\s*\\)");
    private static final Pattern TR_KEY = Pattern.compile("Tr\\.t\\(\\s*\"([a-z0-9_.]+)\"");

    private static List<Path> mainSources() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void everyLiteralTrKeyInSourcesExists() throws IOException {
        Map<String, String> en = lang("en_us"), ru = lang("ru_ru");
        Set<String> missing = new TreeSet<>();
        int seen = 0;
        for (Path f : mainSources()) {
            Matcher m = TR_KEY.matcher(Files.readString(f, StandardCharsets.UTF_8));
            while (m.find()) {
                seen++;
                String key = m.group(1);
                if (!en.containsKey(key) || !ru.containsKey(key)) missing.add(key + " (" + f.getFileName() + ")");
            }
        }
        assertTrue(seen > 0, "не найдено ни одного вызова Tr.t — проверка сломалась");
        assertTrue(missing.isEmpty(), "ключи без перевода: " + missing);
    }

    /** При русском языке игры текст из ru_ru обязан совпадать с русским текстом по умолчанию в коде. */
    @Test
    void ruLangMatchesCodeFallback() throws IOException {
        Map<String, String> ru = lang("ru_ru");
        Set<String> differs = new TreeSet<>();
        for (Path f : mainSources()) {
            Matcher m = TR_CALL.matcher(Files.readString(f, StandardCharsets.UTF_8));
            while (m.find()) {
                String fallback = m.group(2).replace("\\\"", "\"").replace("\\\\", "\\");
                if (ru.containsKey(m.group(1)) && !ru.get(m.group(1)).equals(fallback))
                    differs.add(m.group(1) + " (" + f.getFileName() + ")");
            }
        }
        assertTrue(differs.isEmpty(), "ru_ru расходится с текстом по умолчанию: " + differs);
    }

    @Test
    void moduleTextsMatchRuLang() throws Exception {
        Map<String, String> ru = lang("ru_ru");
        Field title = ModuleCatalog.Module.class.getDeclaredField("title");
        Field desc = ModuleCatalog.Module.class.getDeclaredField("description");
        title.setAccessible(true);
        desc.setAccessible(true);
        for (ModuleCatalog.Module m : ModuleCatalog.ALL) {
            assertEquals(title.get(m), ru.get("farmmacro.module." + m.id() + ".title"), "заголовок " + m.id());
            assertEquals(desc.get(m), ru.get("farmmacro.module." + m.id() + ".desc"), "описание " + m.id());
        }
    }

    /** Каждый ключ lang либо используется кодом, либо входит в известные семейства (клавиши, субтитры, каталог). */
    @Test
    void noOrphanKeys() throws IOException {
        Map<String, String> en = lang("en_us");
        StringBuilder all = new StringBuilder();
        for (Path f : mainSources()) all.append(Files.readString(f, StandardCharsets.UTF_8)).append('\n');
        String src = all.toString();
        Set<String> orphans = new TreeSet<>();
        for (String k : en.keySet()) {
            if (k.startsWith("farmmacro.module.") || k.startsWith("farmmacro.category.")) continue;
            if (k.equals("key.category.farmmacro.general") || k.startsWith("subtitles.farmmacro.")) continue;
            if (!src.contains("\"" + k + "\"")) orphans.add(k);
        }
        assertTrue(orphans.isEmpty(), "ключи lang, которые код не использует: " + orphans);
    }
}
