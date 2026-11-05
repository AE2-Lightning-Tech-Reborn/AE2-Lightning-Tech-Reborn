package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TianshuWorkstationResourceTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    private static JsonObject json(String path) throws Exception {
        return JsonParser.parseString(Files.readString(RESOURCES.resolve(path))).getAsJsonObject();
    }

    @Test
    void remainingTerminalStyleIncludesResolveAgainstInstalledForgeAssets() throws Exception {
        var visited = new HashSet<String>();
        for (var path : new String[]{"ae2lt_craft_confirm.json", "tianshu_terminal_settings.json"}) {
            checkStyle(Path.of(path), visited);
        }
    }

    private void checkStyle(Path path, HashSet<String> visited) throws Exception {
        String name = path.normalize().toString().replace('\\', '/');
        if (!visited.add(name)) return;
        String resource = "assets/ae2/screens/" + name;
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            var style = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (style.has("includes")) for (var include : style.getAsJsonArray("includes")) {
                var parent = path.getParent();
                checkStyle((parent == null ? Path.of("") : parent).resolve(include.getAsString()), visited);
            }
        }
    }

    @Test
    void removedWorkstationRecipesDoNotRemainInReleaseResources() {
        var paths = new String[]{"tianshu_crafting_terminal.json", "wireless_tianshu_crafting_terminal.json",
                "wireless_universal_terminal/combine_tianshu_crafting_with_access.json",
                "wireless_universal_terminal/combine_tianshu_crafting_with_crafting.json",
                "wireless_universal_terminal/combine_tianshu_crafting_with_pattern.json",
                "wireless_universal_terminal/combine_tianshu_crafting_with_tianshu_pattern.json",
                "wireless_universal_terminal/upgrade_tianshu_crafting_workstation.json"};
        for (var path : paths) {
            assertFalse(Files.exists(RESOURCES.resolve("data/ae2lt/recipes/" + path)), path);
        }
        assertFalse(Files.exists(RESOURCES.resolve("data/ae2lt/recipes/overload_alloy_anvil.json")));
    }

    @Test
    void wirelessSupplySwitchRetainsItsWidgetLayout() throws Exception {
        var style = json("assets/ae2/screens/tianshu_terminal_settings.json");
        assertTrue(style.getAsJsonObject("widgets").has("jeiWirelessSupply"));
        var supply = style.getAsJsonObject("widgets").getAsJsonObject("jeiWirelessSupply");
        assertTrue(supply.get("top").getAsInt() + supply.get("height").getAsInt()
                <= style.getAsJsonObject("generatedBackground").get("height").getAsInt());
    }

    @Test
    void removedFeatureClassesAndMixinRegistrationsAreAbsent() throws Exception {
        for (var name : new String[]{"menu/TianshuCraftingTermMenu", "menu/OverloadAlloyAnvilMenu",
                "block/OverloadAlloyAnvilBlock", "part/TianshuCraftingTerminalPart"}) {
            assertNull(getClass().getClassLoader().getResource("com/moakiee/ae2lt/" + name + ".class"), name);
        }
        for (var config : new String[]{"ae2lt.mixins.json", "ae2lt.ae2wtlib.mixins.json"}) {
            var contents = Files.readString(RESOURCES.resolve(config));
            assertFalse(contents.contains("OverloadAlloyAnvil"));
            assertFalse(contents.contains("TianshuMagnet"));
            assertFalse(contents.contains("TianshuStonecutter"));
            assertFalse(contents.contains("TianshuCraftingTerminalHandler"));
        }
    }

    @Test
    void confirmationTextureMatchesStyleAndContainsPixels() throws Exception {
        var style = json("assets/ae2/screens/ae2lt_craft_confirm.json");
        var background = style.getAsJsonObject("background");
        var bitmap = ImageIO.read(RESOURCES.resolve("assets/ae2lt/textures/guis/ae2lt_craftingreport.png").toFile());
        assertNotNull(bitmap);
        assertEquals(background.get("textureWidth").getAsInt(), bitmap.getWidth());
        assertEquals(background.get("textureHeight").getAsInt(), bitmap.getHeight());
        var pixels = new HashSet<Integer>();
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) pixels.add(bitmap.getRGB(x, y));
        assertTrue(pixels.size() > 4, "Report texture must not be blank");
    }

    @Test
    void finishedPanelsTagResolvesBothRegisteredFamilies() throws Exception {
        var tag = json("data/ae2lt/tags/items/pigmee_finished_building_panels.json");
        var values = tag.getAsJsonArray("values");
        assertEquals(2, values.size());
        for (var value : values) {
            var reference = value.getAsString();
            assertTrue(reference.startsWith("#ae2lt:"));
            assertFalse(reference.equals("#ae2lt:pigmee_finished_building_panels"));
            assertTrue(Files.isRegularFile(RESOURCES.resolve("data/ae2lt/tags/items/"
                    + reference.substring("#ae2lt:".length()) + ".json")));
        }
    }
}
