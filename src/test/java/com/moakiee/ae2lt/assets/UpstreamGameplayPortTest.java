package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.moakiee.ae2lt.lightning.RainbowPigmeeTransformation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class UpstreamGameplayPortTest {
    @Test
    void rainbowTriggerRequiresExactCustomName() {
        assertTrue(RainbowPigmeeTransformation.matchesName(Component.literal("jeb_")));
        assertFalse(RainbowPigmeeTransformation.matchesName(null));
        assertFalse(RainbowPigmeeTransformation.matchesName(Component.literal("Jeb_")));
        assertFalse(RainbowPigmeeTransformation.matchesName(Component.literal("jeb_ ")));
    }

    @Test
    void allDyesUseDistinctFixedCatalystLayouts() throws Exception {
        var layouts = new HashSet<String>();
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        for (String color : colors) {
            var recipe = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/data/ae2lt/recipes/rainbow_dye/" + color + ".json"))).getAsJsonObject();
            assertEquals("ae2lt:rainbow_pigmee_dye", recipe.get("type").getAsString());
            var pattern = recipe.getAsJsonArray("pattern");
            assertEquals(3, pattern.size());
            assertEquals('P', pattern.get(1).getAsString().charAt(1));
            String layout = pattern.toString();
            assertTrue(layouts.add(layout), color);
            assertEquals(4, layout.chars().filter(character -> character == 'B').count());
            assertEquals("ae2lt:rainbow_pigmee_fumo",
                    recipe.getAsJsonObject("key").getAsJsonObject("P").get("item").getAsString());
            assertEquals("ae2lt:dye_base",
                    recipe.getAsJsonObject("key").getAsJsonObject("B").get("item").getAsString());
            assertEquals("minecraft:" + color + "_dye",
                    recipe.getAsJsonObject("result").get("item").getAsString());
            assertEquals(4, recipe.getAsJsonObject("result").get("count").getAsInt());
        }
    }

    @Test
    void pigmeeBuildingFamiliesHaveCompleteAssetsAndDrops() throws Exception {
        var resources = Path.of("src/main/resources");
        var colors = new String[] {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        var pickaxe = JsonParser.parseString(Files.readString(resources.resolve(
                "data/minecraft/tags/blocks/mineable/pickaxe.json"))).getAsJsonObject().getAsJsonArray("values");
        var names = new java.util.ArrayList<String>();
        names.add("pigmee_building_block");
        names.add("pigmee_building_slab");
        for (var color : colors) {
            for (var shape : new String[] {"building_panel", "framed_building_panel",
                    "building_slab", "framed_building_slab"}) {
                names.add(color + "_pigmee_" + shape);
            }
        }
        assertEquals(66, names.size());
        for (var name : names) {
            for (var location : new String[] {"assets/ae2lt/blockstates/", "assets/ae2lt/models/item/",
                    "data/ae2lt/loot_tables/blocks/"}) {
                assertTrue(Files.isRegularFile(resources.resolve(location + name + ".json")), name + " " + location);
            }
            var blockModel = name.endsWith("building_slab") ? name + "_bottom" : name;
            assertTrue(Files.isRegularFile(resources.resolve("assets/ae2lt/models/block/" + blockModel + ".json")),
                    name + " block model");
            assertTrue(pickaxe.toString().contains("ae2lt:" + name), name + " mineable/pickaxe");
            for (var language : new String[] {"en_us", "zh_cn"}) {
                var translations = JsonParser.parseString(Files.readString(resources.resolve(
                        "assets/ae2lt/lang/" + language + ".json"))).getAsJsonObject();
                assertTrue(translations.has("block.ae2lt." + name), language + " " + name);
            }
        }
    }

    @Test
    void machineResourcesAndTranslationsAreComplete() throws Exception {
        for (String machine : new String[] {"mining_factory", "overloaded_io_port"}) {
            assertTrue(Files.exists(Path.of("src/main/resources/assets/ae2/screens/" + machine + ".json")));
            for (String prefix : new String[] {"assets/ae2lt/blockstates/", "assets/ae2lt/models/item/",
                    "data/ae2lt/loot_tables/blocks/"}) {
                assertTrue(Files.exists(Path.of("src/main/resources/" + prefix + machine + ".json")));
            }
            for (String language : new String[] {"en_us", "zh_cn"}) {
                var translations = JsonParser.parseString(Files.readString(Path.of(
                        "src/main/resources/assets/ae2lt/lang/" + language + ".json"))).getAsJsonObject();
                assertTrue(translations.has("block.ae2lt." + machine));
                assertTrue(translations.has("item.ae2lt.dye_base"));
            }
        }
    }
}
