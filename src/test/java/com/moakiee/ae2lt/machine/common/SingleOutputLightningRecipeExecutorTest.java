package com.moakiee.ae2lt.machine.common;

import static org.junit.jupiter.api.Assertions.*;

import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;
import com.moakiee.ae2lt.me.key.LightningKey;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SingleOutputLightningRecipeExecutorTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void refundsThePartiallyExtractedCurrentSlotAndPreviousSlots() {
        var fixture = new Fixture();
        fixture.shortSlot = 1;
        assertFalse(fixture.complete());
        fixture.assertInputs(4, 4);
        assertEquals(100, fixture.power);
        assertTrue(fixture.inventory.getStackInSlot(4).isEmpty());
    }

    @Test
    void outputFilledDuringLightningExtractionDoesNotCreateAFreePartialOutput() {
        var fixture = new Fixture();
        fixture.onLightningExtract = () -> fixture.inventory.setItemDirect(4, new ItemStack(Items.DIAMOND, 8191));
        assertFalse(fixture.complete());
        fixture.assertInputs(4, 4);
        assertEquals(100, fixture.power);
        assertEquals(8191, fixture.inventory.getStackInSlot(4).getCount(),
                "Failed completion must not retain part of the crafted output alongside refunded inputs");
    }

    @Test
    void insufficientRealLightningRefundsInputsAndAnyExtractedLightning() {
        var fixture = new Fixture();
        fixture.power = 3;
        assertFalse(fixture.complete());
        fixture.assertInputs(4, 4);
        assertEquals(3, fixture.power);
        assertTrue(fixture.inventory.getStackInSlot(4).isEmpty());
    }

    @Test
    void successfulCompletionConsumesExactlyOneRecipe() {
        var fixture = new Fixture();
        assertTrue(fixture.complete());
        fixture.assertInputs(0, 0);
        assertEquals(90, fixture.power);
        assertEquals(2, fixture.inventory.getStackInSlot(4).getCount());
    }

    private static final class Fixture implements SingleOutputLightningRecipeExecutor.InventoryAdapter,
            SingleOutputLightningRecipeExecutor.LightningAdapter {
        final LightningSimulationChamberInventory inventory = new LightningSimulationChamberInventory(null);
        int shortSlot = -1;
        long power = 100;
        Runnable onLightningExtract = () -> { };

        Fixture() {
            inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
            inventory.setStackInSlot(1, new ItemStack(Items.REDSTONE, 4));
        }

        boolean complete() {
            return SingleOutputLightningRecipeExecutor.complete(0, 1, slot -> 4,
                    new ItemStack(Items.DIAMOND, 2),
                    () -> Optional.of(new SingleOutputLightningRecipeExecutor.LightningPlan(LightningKey.HIGH_VOLTAGE, 10)),
                    this, this);
        }

        void assertInputs(int iron, int redstone) {
            assertEquals(iron, inventory.getStackInSlot(0).getCount());
            assertEquals(redstone, inventory.getStackInSlot(1).getCount());
        }

        public boolean canAcceptOutput(ItemStack result) { return inventory.canAcceptRecipeOutput(result); }
        public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
        public ItemStack extractItem(int slot, int amount) {
            return inventory.extractItem(slot, slot == shortSlot ? amount - 1 : amount, false);
        }
        public ItemStack insertOutput(ItemStack stack) { return inventory.insertRecipeOutput(stack, false); }
        public void insertInput(int slot, ItemStack stack) { inventory.insertItem(slot, stack, false); }
        public long simulateExtract(LightningKey key, long amount) { return amount; }
        public long extract(LightningKey key, long amount) {
            long extracted = Math.min(amount, power);
            power -= extracted;
            onLightningExtract.run();
            return extracted;
        }
        public long insert(LightningKey key, long amount) { power += amount; return amount; }
    }
}
