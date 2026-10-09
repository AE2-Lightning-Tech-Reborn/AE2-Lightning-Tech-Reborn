package com.moakiee.ae2lt.api.lightning;

import static org.junit.jupiter.api.Assertions.*;

import com.moakiee.ae2lt.api.lightning.collector.CollectorCrystalApi;
import com.moakiee.ae2lt.api.lightning.collector.CollectorCrystalBehavior;
import com.moakiee.ae2lt.api.lightning.collector.CollectorCrystalBehavior.OutputRange;
import com.moakiee.ae2lt.machine.lightningcollector.LightningCollectorInventory;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CollectorCrystalRegistrationTest {
    @BeforeAll
    static void bootstrap() {
        if (net.minecraftforge.fml.loading.LoadingModList.get() == null) {
            net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), null);
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void ordinaryAddonCrystalsCoexistAndEnterTheCollectorSlot() {
        CollectorCrystalBehavior first = (crystal, tier, base) -> new OutputRange(4, 4);
        CollectorCrystalBehavior second = (crystal, tier, base) -> new OutputRange(7, 9);
        var firstId = BuiltInRegistries.ITEM.getKey(Items.PAPER);
        var secondId = BuiltInRegistries.ITEM.getKey(Items.GLASS);
        CollectorCrystalApi.register(firstId, first);
        CollectorCrystalApi.register(secondId, second);
        var firstCrystal = new ItemStack(Items.PAPER);
        var secondCrystal = new ItemStack(Items.GLASS);
        assertSame(first, CollectorCrystalApi.find(firstCrystal));
        assertSame(second, CollectorCrystalApi.find(secondCrystal));
        assertNull(CollectorCrystalApi.find(ItemStack.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> CollectorCrystalApi.register(firstId, second));
        var inventory = new LightningCollectorInventory(null);
        assertEquals(1, inventory.getSlotLimit(LightningCollectorInventory.SLOT_CRYSTAL));
        assertTrue(inventory.isItemValid(LightningCollectorInventory.SLOT_CRYSTAL, firstCrystal));
        assertTrue(inventory.isItemValid(LightningCollectorInventory.SLOT_CRYSTAL, secondCrystal));
        assertEquals(new OutputRange(4, 4), first.preview(firstCrystal, LightningTier.HIGH_VOLTAGE,
                new OutputRange(1, 2)));
    }

    @Test
    void defaultCaptureCallbackKeepsTheInstalledStack() {
        CollectorCrystalBehavior behavior = (crystal, tier, base) -> base;
        var crystal = new ItemStack(Items.PAPER);
        assertSame(crystal, behavior.onCaptured(null, crystal));
    }
}
