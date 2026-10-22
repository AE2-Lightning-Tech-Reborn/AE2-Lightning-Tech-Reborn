package com.moakiee.ae2lt.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

/** Handles ExtendedAE's unconditional loot entry for its optional AppliedE item. */
public final class ExtendedAeLootCompat {
    public static final ResourceLocation TABLE = new ResourceLocation("expatternprovider", "blocks/ex_emc_interface");
    public static final ResourceLocation ITEM = new ResourceLocation("expatternprovider", "ex_emc_interface");

    private ExtendedAeLootCompat() {
    }

    public static JsonElement sanitize(ResourceLocation table, JsonElement json, boolean itemPresent) {
        if (itemPresent || !TABLE.equals(table)) return json;
        var copy = json.deepCopy();
        return replaceMissingEntry(copy) ? copy : json;
    }

    private static boolean replaceMissingEntry(JsonElement element) {
        boolean changed = false;
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) changed |= replaceMissingEntry(child);
        } else if (element instanceof JsonObject object) {
            if (isString(object.get("type"), "minecraft:item") && isString(object.get("name"), ITEM.toString())) {
                object.addProperty("type", "minecraft:empty");
                object.remove("name");
                changed = true;
            }
            for (var entry : object.entrySet()) changed |= replaceMissingEntry(entry.getValue());
        }
        return changed;
    }

    private static boolean isString(JsonElement element, String value) {
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString() && value.equals(element.getAsString());
    }
}
