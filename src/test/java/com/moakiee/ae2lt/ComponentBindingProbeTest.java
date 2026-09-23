package com.moakiee.ae2lt;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.moakiee.ae2lt.test.MinecraftComponentsTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ComponentBindingProbeTest extends MinecraftComponentsTestBase {
    @Test
    void vanillaItemCanBeConstructedAfterTestBootstrap() {
        assertEquals(64, new ItemStack(Items.STONE).getMaxStackSize());
    }
}
