package com.moakiee.ae2lt.machine.firmament;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class FirmamentConversionRecipeBookContractTest {
    @Test
    void customMachineRecipesStayOutOfTheVanillaRecipeBook() throws Exception {
        Path sources = Path.of("src/main/java/com/moakiee/ae2lt");
        List<Path> recipes;
        try (var files = Files.walk(sources)) {
            recipes = files
                    .filter(path -> path.toString().endsWith("Recipe.java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains("implements Recipe<")
                                    && !source.contains("extends CustomRecipe");
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
        }

        assertFalse(recipes.isEmpty());
        List<String> missing = new ArrayList<>();
        for (Path recipe : recipes) {
            String source = Files.readString(recipe);
            if (!source.contains("public boolean isSpecial()") || !source.contains("return true;")) {
                missing.add(recipe.toString());
            }
        }
        assertTrue(missing.isEmpty(), () -> "machine recipes missing isSpecial(): " + missing);
    }
}
