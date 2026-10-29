package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class UpstreamVisualResourceTest {
    @Test
    void miningFactoryGuiArtworkMatchesItsLayout() throws Exception {
        Path layout = Path.of("src/main/resources/assets/ae2/screens/mining_factory.json");
        var document = JsonParser.parseString(Files.readString(layout)).getAsJsonObject();
        assertEquals("ae2lt:textures/guis/mining_factory.png",
                document.getAsJsonObject("background").get("texture").getAsString());
        assertTrue(document.getAsJsonObject("images").has("miningOverlay"));
        var image = ImageIO.read(Path.of("src/main/resources/assets/ae2lt/textures/guis/mining_factory.png").toFile());
        assertTrue(image.getWidth() >= 227);
        assertTrue(image.getHeight() >= 183);
    }

    @Test
    void rainbowPigmeeShaderModelAndDropArePresent() throws Exception {
        Path assetRoot = Path.of("src/main/resources/assets/ae2lt");
        for (String resource : new String[] {
                "shaders/core/rainbow_pigmee.json",
                "shaders/core/rainbow_pigmee.vsh",
                "shaders/core/rainbow_pigmee.fsh",
                "blockstates/rainbow_pigmee_fumo.json",
                "models/block/rainbow_pigmee_fumo.json",
                "models/item/rainbow_pigmee_fumo.json"}) {
            assertTrue(Files.isRegularFile(assetRoot.resolve(resource)), resource);
        }
        var loot = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/ae2lt/loot_tables/blocks/rainbow_pigmee_fumo.json")));
        assertTrue(loot.toString().contains("ae2lt:rainbow_pigmee_fumo"));
    }
}
