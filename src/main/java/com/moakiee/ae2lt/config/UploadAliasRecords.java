package com.moakiee.ae2lt.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Lossless edits of the legacy source=alias list; unrelated malformed entries are retained. */
public final class UploadAliasRecords {
    private UploadAliasRecords() { }

    public record Entry(String source, String alias, int occurrences) { }

    public static String normalize(String source) {
        return source == null ? "" : source.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean valid(String source, String alias) {
        String key = normalize(source);
        String value = alias == null ? "" : alias.strip();
        return !key.isEmpty() && !value.isEmpty() && !key.contains("=")
                && key.length() <= 256 && value.length() <= 256
                && key.length() + 1 + value.length() <= 512;
    }

    private static String key(String raw) {
        int separator = raw.indexOf('=');
        return separator > 0 ? normalize(raw.substring(0, separator)) : "";
    }

    public static List<Entry> snapshot(List<? extends String> raw) {
        var entries = new LinkedHashMap<String, Entry>();
        for (String line : raw) {
            String source = key(line);
            if (source.isEmpty()) continue;
            Entry previous = entries.get(source);
            entries.put(source, previous == null
                    ? new Entry(source, line.substring(line.indexOf('=') + 1).strip(), 1)
                    : new Entry(source, previous.alias(), previous.occurrences() + 1));
        }
        return entries.values().stream()
                .sorted(java.util.Comparator.comparing(Entry::source, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public static List<String> replace(List<? extends String> raw, String original, String source, String alias) {
        if (!valid(source, alias)) throw new IllegalArgumentException("Invalid upload alias");
        String oldKey = normalize(original);
        String newKey = normalize(source);
        var result = new ArrayList<String>();
        for (String line : raw) {
            String key = key(line);
            if (key.equals(newKey) || (!oldKey.isEmpty() && key.equals(oldKey))) continue;
            result.add(line);
        }
        result.add(newKey + "=" + alias.strip());
        return List.copyOf(result);
    }

    public static List<String> remove(List<? extends String> raw, String source) {
        String target = normalize(source);
        return raw.stream().filter(line -> target.isEmpty() || !key(line).equals(target)).map(String::toString).toList();
    }
}
