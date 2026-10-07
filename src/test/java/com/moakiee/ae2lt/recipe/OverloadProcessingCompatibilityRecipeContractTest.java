package com.moakiee.ae2lt.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class OverloadProcessingCompatibilityRecipeContractTest {
    private static final Path RECIPE_ROOT = Path.of(
            "src/main/resources/data/ae2lt/recipe/overload_processing");

    @Test
    void advancedAeRecipesKeepUpstreamConventionTags() throws Exception {
        assertTag("aae_quantum_processor.json", 1, "c:storage_blocks/redstone");
    }

    @Test
    void extendedAeRecipesKeepUpstreamConventionTags() throws Exception {
        assertTag("eae_concurrent_processor.json", 0, "c:storage_blocks/entro");
        assertTag("eae_concurrent_processor.json", 1, "c:storage_blocks/redstone");
    }

    private static void assertTag(String filename, int inputIndex, String expectedTag) throws Exception {
        JsonObject input = recipe(filename).getAsJsonArray("inputs")
                .get(inputIndex)
                .getAsJsonObject();
        JsonObject ingredient = input.getAsJsonObject("ingredient");

        assertEquals(expectedTag, ingredient.get("tag").getAsString(), filename);
        assertFalse(ingredient.has("item"), filename);
    }

    private static JsonObject recipe(String filename) throws Exception {
        return JsonParser.parseString(Files.readString(RECIPE_ROOT.resolve(filename))).getAsJsonObject();
    }
}
