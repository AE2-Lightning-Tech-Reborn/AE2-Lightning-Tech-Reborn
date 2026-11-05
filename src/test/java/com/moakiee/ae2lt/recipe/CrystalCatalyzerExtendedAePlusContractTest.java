package com.moakiee.ae2lt.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CrystalCatalyzerExtendedAePlusContractTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void lattraRecipesRequireTheirOptionalItemsAndExpectedOutputs() throws IOException {
        assertRecipe(
                "data/ae2lt/recipes/crystal_catalyzer/lattra_crystal_block.json",
                "extendedae_plus:lattra_crystal");
        assertRecipe(
                "data/ae2lt/recipes/crystal_catalyzer/dust/lattra_crystal_block.json",
                "extendedae_plus:lattra_dust");
    }

    private static void assertRecipe(String relativePath, String outputId) throws IOException {
        JsonObject recipe = JsonParser.parseString(
                Files.readString(RESOURCES.resolve(relativePath))).getAsJsonObject();
        var conditions = recipe.getAsJsonArray("conditions");
        assertNotNull(conditions, relativePath);
        assertEquals(3, conditions.size(), relativePath);
        JsonObject condition = conditions.get(0).getAsJsonObject();
        assertEquals("forge:mod_loaded", condition.get("type").getAsString(), relativePath);
        assertEquals("extendedae_plus", condition.get("modid").getAsString(), relativePath);
        assertItemExists(conditions.get(1).getAsJsonObject(),
                "extendedae_plus:lattra_crystal_block", relativePath);
        assertItemExists(conditions.get(2).getAsJsonObject(), outputId, relativePath);
        assertEquals("ae2lt:crystal_catalyzer", recipe.get("type").getAsString(), relativePath);
        assertEquals("extendedae_plus:lattra_crystal_block",
                recipe.getAsJsonObject("catalyst").get("item").getAsString(), relativePath);
        assertEquals(outputId, recipe.getAsJsonObject("output").get("id").getAsString(), relativePath);
        assertFalse(recipe.get("output").getAsJsonObject().has("tag"), relativePath);
    }

    private static void assertItemExists(JsonObject condition, String itemId, String relativePath) {
        assertEquals("forge:item_exists", condition.get("type").getAsString(), relativePath);
        assertEquals(itemId, condition.get("item").getAsString(), relativePath);
    }
}
