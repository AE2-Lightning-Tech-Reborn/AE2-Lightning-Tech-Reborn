package com.moakiee.ae2lt.client.ctm;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

/** Reads the original AE2LT model fields for migration to a 26.1 blockstate model. */
public final class ConnectedTextureLoader {
    private ConnectedTextureLoader() {
    }

    public static ConnectedTextureGeometry read(JsonObject json) {
        var textures = GsonHelper.getAsJsonObject(json, "textures");
        return new ConnectedTextureGeometry(
                Identifier.parse(GsonHelper.getAsString(json, "connection", "ae2lt:same_block")),
                Identifier.parse(GsonHelper.getAsString(textures, "base")),
                Identifier.parse(GsonHelper.getAsString(textures, "ctm")),
                textures.has("overlay") ? Identifier.parse(GsonHelper.getAsString(textures, "overlay")) : null,
                GsonHelper.getAsString(json, "render_type", "minecraft:translucent"),
                GsonHelper.getAsBoolean(json, "ambientocclusion", true),
                GsonHelper.getAsBoolean(json, "gui3d", true),
                GsonHelper.getAsBoolean(json, "uses_block_light", true));
    }
}
