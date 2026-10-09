package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CelestweaveArmorRenderSourceContractTest {
    @Test
    void realAndProjectedArmorUseCustomModelAndProjectionKeepsSilentEquipSound() throws Exception {
        String baseArmor = read(
                "src/main/java/com/moakiee/ae2lt/celestweave/BaseCelestweaveArmorItem.java");
        String projection = read(
                "src/main/java/com/moakiee/ae2lt/item/PhaseLockProjectionItem.java");
        String extensions = read(
                "src/main/java/com/moakiee/ae2lt/client/CelestweaveArmorRenderExtensions.java");

        assertTrue(baseArmor.contains("CelestweaveArmorRenderExtensions.INSTANCE"));
        assertTrue(projection.contains("CelestweaveArmorRenderExtensions.INSTANCE"));
        assertTrue(projection.contains("return SoundEvents.EMPTY"));
        assertFalse(projection.contains("equipmentSlot == EquipmentSlot.HEAD"));
        assertTrue(extensions.contains("CelestweaveArmorModel.forSlot(equipmentSlot)"));
        assertTrue(extensions.contains("return original"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
