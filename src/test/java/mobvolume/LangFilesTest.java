package mobvolume;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every language file has exactly the English keys, and every text is a valid Minecraft translation:
 * only %s, %1$s or %% (a lone % makes the game show the raw text), with as many arguments as in English.
 */
class LangFilesTest {
    private static final Path DIR = Path.of("src/main/resources/assets/" + MobVolumeMod.MOD_ID + "/lang");
    private static final Set<String> LANGUAGES = Set.of("en_us", "tr_tr", "az_az", "de_de", "fr_fr", "es_es",
        "es_mx", "es_ar", "it_it", "pt_br", "pt_pt", "nl_nl", "pl_pl", "ru_ru", "uk_ua", "zh_cn", "zh_tw", "ja_jp", "ko_kr");
    /** Same pattern as the game's TranslatableContents. */
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    private static Map<String, String> read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            // a Map type makes Gson reject duplicate keys
            return new Gson().fromJson(reader, new TypeToken<Map<String, String>>() { }.getType());
        }
    }

    /** Number of arguments the text uses, or -1 if the game cannot format it. */
    static int arguments(String text) {
        Matcher m = FORMAT.matcher(text);
        int count = 0;
        int current = 0;
        while (m.find(current)) {
            if (text.substring(current, m.start()).indexOf('%') >= 0) return -1;
            String type = m.group(2);
            if (type.equals("%") && m.group(0).equals("%%")) {
                // literal percent sign
            } else if (type.equals("s")) {
                count++;
            } else {
                return -1;
            }
            current = m.end();
        }
        return text.substring(current).indexOf('%') >= 0 ? -1 : count;
    }

    @Test
    void formatCheckMatchesTheGame() {
        assertEquals(1, arguments("%s%%"));
        assertEquals(1, arguments("%%%s"));
        assertEquals(2, arguments("%1$s – %2$s"));
        assertEquals(0, arguments("100%%"));
        assertEquals(-1, arguments("100%"));
        assertEquals(-1, arguments("50% off"));
        assertEquals(-1, arguments("%d chunks"));
    }

    @Test
    void allLanguagesHaveTheEnglishKeysAndValidTexts() throws IOException {
        Map<String, String> english = read(DIR.resolve("en_us.json"));
        Set<String> found = new TreeSet<>();
        List<String> errors = new ArrayList<>();
        try (Stream<Path> files = Files.list(DIR)) {
            for (Path file : files.toList()) {
                String code = file.getFileName().toString().replace(".json", "");
                found.add(code);
                Map<String, String> lang = read(file);
                Set<String> missing = new TreeSet<>(english.keySet());
                missing.removeAll(lang.keySet());
                Set<String> extra = new TreeSet<>(lang.keySet());
                extra.removeAll(english.keySet());
                if (!missing.isEmpty()) errors.add(code + ": missing " + missing);
                if (!extra.isEmpty()) errors.add(code + ": unknown " + extra);
                lang.forEach((key, text) -> {
                    int args = arguments(text);
                    if (args < 0) {
                        errors.add(code + ": " + key + " has a % that is not %s or %%: " + text);
                    } else if (english.containsKey(key) && args != arguments(english.get(key))) {
                        errors.add(code + ": " + key + " uses " + args + " arguments, English uses "
                            + arguments(english.get(key)));
                    }
                });
            }
        }
        assertEquals(new TreeSet<>(LANGUAGES), found, "language files");
        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }
}
