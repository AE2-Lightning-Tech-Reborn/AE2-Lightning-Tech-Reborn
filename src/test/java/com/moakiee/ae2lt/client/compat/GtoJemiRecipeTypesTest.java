package com.moakiee.ae2lt.client.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GtoJemiRecipeTypesTest {
    @BeforeAll
    static void bootstrap() {
        com.moakiee.ae2lt.testing.MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void vanillaFurnaceUsesTheRegisteredNativeEmiSmeltingCategory() {
        var furnace = new ResourceLocation("minecraft", "furnace");
        var smelting = new ResourceLocation("minecraft", "smelting");
        assertEquals(smelting, GtoJemiRecipeTypes.resolve(furnace, Set.of(smelting)::contains));
        assertEquals(furnace, GtoJemiRecipeTypes.resolve(furnace, Set.of(furnace, smelting)::contains));
        assertEquals(furnace, GtoJemiRecipeTypes.resolve(furnace, Set.<ResourceLocation>of()::contains));
    }

    @Test
    void addonIdentityDoesNotChangeWhenNoNativeCategoryExists() {
        var addon = new ResourceLocation("ae2lt", "multiblock_structure");
        assertEquals(addon, GtoJemiRecipeTypes.resolve(addon, Set.<ResourceLocation>of()::contains));
        assertEquals(addon, GtoJemiRecipeTypes.resolve(addon, Set.of(addon)::contains));
    }
}
