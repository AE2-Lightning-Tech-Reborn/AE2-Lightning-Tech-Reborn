package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

final class ArmorModuleTextureContractTest {

    private static final List<String> MODULES = List.of(
            "module_night_vision",
            "module_water_breathing",
            "module_reach_extension",
            "module_matrix_shield",
            "module_phase_shield",
            "module_reflect",
            "module_undying",
            "module_dash",
            "module_creative_flight",
            "module_saturation",
            "module_dig_affinity",
            "module_phase_flight",
            "module_purification");

    @Test
    void armorModulesUseDedicatedItemTextures() throws Exception {
        for (String module : MODULES) {
            Path modelPath = Path.of("src/main/resources/assets/ae2lt/models/item", module + ".json");
            String model = Files.readString(modelPath);

            assertTrue(
                    model.contains("\"layer0\": \"ae2lt:item/" + module + "\""),
                    module + " should point to its dedicated item texture");
        }
    }

    @Test
    void armorModuleTextureFilesExist() {
        for (String module : MODULES) {
            Path texturePath = Path.of("src/main/resources/assets/ae2lt/textures/item", module + ".png");

            assertTrue(Files.isRegularFile(texturePath), module + " texture should exist");
        }
    }

    @Test
    void celestweaveArmorLayersHaveWornTextures() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/CelestweaveArmorMaterials.java"))
                .replaceAll("\\s+", "");

        assertTrue(
                source.contains("newArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID,\"celestweave\"))"),
                "Celestweave should declare its ae2lt:celestweave worn armor layer");
        for (String texture : List.of(
                "models/armor/celestweave_layer_1",
                "models/armor/celestweave_layer_1_glow",
                "models/armor/celestweave_layer_2",
                "models/armor/celestweave_layer_2_glow",
                "entity/celestweave_phase_wing",
                "entity/celestweave_phase_wing_glow")) {
            Path texturePath = Path.of("src/main/resources/assets/ae2lt/textures", texture + ".png");

            assertTrue(Files.isRegularFile(texturePath), texture + " texture should exist");
        }
    }
}
