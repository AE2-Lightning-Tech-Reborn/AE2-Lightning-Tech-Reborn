package com.moakiee.ae2lt.compat;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtendedAeLootCompatTest {
    private static final String LOOT = """
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[
              {"type":"minecraft:item","name":"expatternprovider:ex_emc_interface",
               "conditions":[{"condition":"minecraft:survives_explosion"}]},
              {"type":"minecraft:item","name":"minecraft:diamond"}]}]}
            """;

    @Test
    void missingOptionalItemReplacesOnlyThatEntryAndPreservesOriginal() {
        var original = JsonParser.parseString(LOOT);
        var sanitized = ExtendedAeLootCompat.sanitize(ExtendedAeLootCompat.TABLE, original, false);
        var entries = sanitized.getAsJsonObject().getAsJsonArray("pools").get(0)
                .getAsJsonObject().getAsJsonArray("entries");
        assertEquals("minecraft:empty", entries.get(0).getAsJsonObject().get("type").getAsString());
        assertFalse(entries.get(0).getAsJsonObject().has("name"));
        assertEquals(1, entries.get(0).getAsJsonObject().getAsJsonArray("conditions").size());
        assertEquals("minecraft:diamond", entries.get(1).getAsJsonObject().get("name").getAsString());
        assertEquals(JsonParser.parseString(LOOT), original);
    }

    @Test
    void installedItemAndOtherTablesAreUntouched() {
        var json = JsonParser.parseString(LOOT);
        assertSame(json, ExtendedAeLootCompat.sanitize(ExtendedAeLootCompat.TABLE, json, true));
        assertSame(json, ExtendedAeLootCompat.sanitize(new ResourceLocation("other:table"), json, false));
        var replacement = JsonParser.parseString("{\"pools\":[]}");
        assertSame(replacement, ExtendedAeLootCompat.sanitize(ExtendedAeLootCompat.TABLE, replacement, false));
    }
}
