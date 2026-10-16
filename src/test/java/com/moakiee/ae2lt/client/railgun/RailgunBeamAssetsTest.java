package com.moakiee.ae2lt.client.railgun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class RailgunBeamAssetsTest {
    @Test
    void moduleUsesItsDedicatedReadableTexture() throws Exception {
        Path assets = Path.of("src/main/resources/assets/ae2lt");
        var model = JsonParser.parseString(Files.readString(
                assets.resolve("models/item/railgun_module_ehv_beam.json"))).getAsJsonObject();
        var textures = model.getAsJsonObject("textures");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("ae2lt:item/railgun_module_ehv_beam", textures.get("layer0").getAsString());
        assertFalse(textures.has("layer1"));

        var image = ImageIO.read(assets.resolve("textures/item/railgun_module_ehv_beam.png").toFile());
        assertNotNull(image);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
    }

    @Test
    void moduleRecipeUsesSixteenEntangledLattices() throws Exception {
        var recipe = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/ae2lt/recipes/lightning_assembly/railgun_module_ehv_beam.json")))
                .getAsJsonObject();
        var lattice = recipe.getAsJsonArray("inputs").get(1).getAsJsonObject();
        assertEquals("ae2lt:entangled_topological_lattice",
                lattice.getAsJsonObject("ingredient").get("item").getAsString());
        assertEquals(16, lattice.get("count").getAsInt());
    }
}
