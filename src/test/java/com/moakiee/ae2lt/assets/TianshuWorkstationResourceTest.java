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
    void retainedTerminalStyleIncludesResolveAgainstInstalledForgeAssets() throws Exception {
        var visited = new HashSet<String>();
        for (var path : new String[]{"ae2lt_craft_confirm.json", "ae2lt_crafting_report.json", "tianshu_terminal_settings.json",
                "terminals/tianshu_crafting_terminal.json", "wireless_tianshu_crafting_terminal.json"}) {
            checkStyle(Path.of(path), visited);
        }
    }

    @Test
    void reportStyleHostsNativeAddonWidgetWithoutCoveringItsOwnBookmarkButton() throws Exception {
        var legacy = json("assets/ae2/screens/ae2lt_craft_confirm.json");
        var report = json("assets/ae2/screens/ae2lt_crafting_report.json");
        var widgets = report.getAsJsonObject("widgets");
        // GTLCore adds its native bookmark during the superclass constructor. The report
        // provides the visible bookmark (with exact-amount handling), so host that duplicate
        // outside the report rather than allowing a missing-widget crash or overlapping buttons.
        var nativeBookmark = widgets.remove("favoriteMissing").getAsJsonObject();
        assertTrue(nativeBookmark.get("left").getAsInt() + nativeBookmark.get("width").getAsInt() <= 0);
        assertTrue(widgets.getAsJsonObject("bookmarkMissing").get("left").getAsInt() >= 0);
        assertEquals(legacy, report, "Keep the retained resource and active report layout aligned");
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
    void retainedWorkstationRecipesKeepTheirRegisteredOutputsAndWirelessIntegration() throws Exception {
        for (var item : new String[]{"tianshu_crafting_terminal", "wireless_tianshu_crafting_terminal",
                "overload_alloy_anvil"}) {
            var recipe = json("data/ae2lt/recipes/" + item + ".json");
            assertEquals("ae2lt:" + item, recipe.getAsJsonObject("result").get("item").getAsString());
            assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
        }
        for (var terminal : new String[]{"access", "crafting", "pattern", "tianshu_pattern"}) {
            var recipe = json("data/ae2lt/recipes/wireless_universal_terminal/combine_tianshu_crafting_with_"
                    + terminal + ".json");
            assertEquals("ae2wtlib:combine", recipe.get("type").getAsString());
            assertEquals("tianshu_crafting", recipe.get("terminalBName").getAsString());
            assertEquals("ae2lt:wireless_tianshu_crafting_terminal",
                    recipe.getAsJsonObject("terminalB").get("item").getAsString());
        }
        var upgrade = json("data/ae2lt/recipes/wireless_universal_terminal/upgrade_tianshu_crafting_workstation.json");
        assertEquals("ae2wtlib:upgrade", upgrade.get("type").getAsString());
        assertEquals("tianshu_crafting", upgrade.get("terminalName").getAsString());
        assertEquals("ae2lt:wireless_tianshu_crafting_terminal",
                upgrade.getAsJsonObject("terminal").get("item").getAsString());
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
    void retainedWorkstationClassesAndMixinRegistrationsRemainPresent() throws Exception {
        for (var name : new String[]{"menu/TianshuCraftingTermMenu", "menu/OverloadAlloyAnvilMenu",
                "block/OverloadAlloyAnvilBlock", "part/TianshuCraftingTerminalPart"}) {
            assertNotNull(getClass().getClassLoader().getResource("com/moakiee/ae2lt/" + name + ".class"), name);
        }
        var mainMixins = json("ae2lt.mixins.json").getAsJsonArray("mixins");
        for (var name : new String[]{"OverloadAlloyAnvilMenuMixin", "OverloadAlloyAnvilFallingMixin",
                "TianshuStonecutterMenuMixin"}) {
            assertEquals(1, java.util.stream.StreamSupport.stream(mainMixins.spliterator(), false)
                    .filter(entry -> name.equals(entry.getAsString())).count(), name);
        }
        var wtlib = json("ae2lt.ae2wtlib.mixins.json");
        for (var name : new String[]{"TianshuMagnetMenuMixin", "TianshuMagnetHandlerMixin"}) {
            assertEquals(1, java.util.stream.StreamSupport.stream(wtlib.getAsJsonArray("mixins").spliterator(), false)
                    .filter(entry -> name.equals(entry.getAsString())).count(), name);
        }
        assertEquals(1, java.util.stream.StreamSupport.stream(wtlib.getAsJsonArray("client").spliterator(), false)
                .filter(entry -> "client.TianshuMagnetScreenMixin".equals(entry.getAsString())).count());
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
