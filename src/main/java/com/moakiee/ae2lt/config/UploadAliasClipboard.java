package com.moakiee.ae2lt.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Versioned clipboard interchange. Parsing never changes configuration. */
public final class UploadAliasClipboard {
    public static final int MAX_CHARACTERS = 1_048_576;
    public static final int MAX_ENTRIES = 1024;
    private static final String FORMAT = "ae2lt-upload-aliases";

    private UploadAliasClipboard() { }

    public static String exportAliases(List<UploadAliasRecords.Entry> records) {
        if (records.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many aliases");
        var root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("version", 1);
        var aliases = new JsonArray();
        for (var record : records) {
            if (!UploadAliasRecords.valid(record.source(), record.alias()))
                throw new IllegalArgumentException("Invalid alias record");
            var entry = new JsonObject();
            entry.addProperty("source", record.source());
            entry.addProperty("alias", record.alias());
            aliases.add(entry);
        }
        root.add("aliases", aliases);
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
        if (json.length() > MAX_CHARACTERS) throw new IllegalArgumentException("Clipboard data too large");
        return json;
    }

    public static List<UploadAliasRecords.Entry> parse(String text) {
        if (text == null || text.isBlank() || text.length() > MAX_CHARACTERS)
            throw new IllegalArgumentException("Invalid clipboard length");
        try {
            var root = JsonParser.parseString(text).getAsJsonObject();
            if (!FORMAT.equals(string(root, "format")) || !root.has("version")
                    || !root.get("version").isJsonPrimitive()
                    || !root.getAsJsonPrimitive("version").isNumber()
                    || root.get("version").getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) != 0)
                throw new IllegalArgumentException("Unsupported alias format");
            var aliases = root.getAsJsonArray("aliases");
            if (aliases == null || aliases.size() > MAX_ENTRIES)
                throw new IllegalArgumentException("Invalid alias count");
            var result = new ArrayList<UploadAliasRecords.Entry>();
            var seen = new HashSet<String>();
            for (var value : aliases) {
                var entry = value.getAsJsonObject();
                String source = UploadAliasRecords.normalize(string(entry, "source"));
                String alias = string(entry, "alias").strip();
                if (!UploadAliasRecords.valid(source, alias) || !seen.add(source))
                    throw new IllegalArgumentException("Invalid or duplicate alias source");
                result.add(new UploadAliasRecords.Entry(source, alias, 1));
            }
            return List.copyOf(result);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid upload alias clipboard data", error);
        }
    }

    private static String string(JsonObject object, String key) {
        var value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected text: " + key);
        return value.getAsString();
    }
}
