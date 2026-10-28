package com.moakiee.ae2lt.machine.miningfactory;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MiningFactoryInventoryTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void outputsFillMatchingStacksBeforeEmptySlots() {
        var inventory = new MiningFactoryInventory(() -> {});
        inventory.setItemDirect(MiningFactoryInventory.OUTPUT + 1, new ItemStack(Items.DIAMOND, 4000));
        assertTrue(inventory.insertOutput(new ItemStack(Items.DIAMOND, 200)).isEmpty());
        assertEquals(4096, inventory.getStackInSlot(MiningFactoryInventory.OUTPUT + 1).getCount());
        assertEquals(104, inventory.getStackInSlot(MiningFactoryInventory.OUTPUT).getCount());
    }

    @Test
    void fullOutputsReturnExactRemainderAndRejectAutomationInsertion() {
        var inventory = new MiningFactoryInventory(() -> {});
        int capacity = MiningFactoryInventory.CAPACITY * MiningFactoryInventory.OUTPUT_COUNT;
        assertEquals(7, inventory.insertOutput(new ItemStack(Items.DIAMOND, capacity + 7)).getCount());
        assertFalse(inventory.hasOutputRoom());
        assertEquals(1, inventory.insertItem(MiningFactoryInventory.OUTPUT,
                new ItemStack(Items.DIAMOND), false).getCount());
        assertEquals(64, inventory.extractItem(MiningFactoryInventory.OUTPUT, 64, true).getCount());
        assertFalse(inventory.hasOutputRoom());
        inventory.extractItem(MiningFactoryInventory.OUTPUT, 64, false);
        assertTrue(inventory.hasOutputRoom());
        assertTrue(inventory.insertOutput(new ItemStack(Items.DIAMOND, 64)).isEmpty());
        assertFalse(inventory.hasOutputRoom());
    }

    @Test
    void outputCountsSurviveSaveLoadAboveVanillaLimit() {
        var inventory = new MiningFactoryInventory(() -> {});
        inventory.insertOutput(new ItemStack(Items.DIAMOND, 8193));
        var tag = new CompoundTag();
        inventory.saveToTag(tag, "Inventory");
        var restored = new MiningFactoryInventory(() -> {});
        restored.loadFromTag(tag, "Inventory");
        assertEquals(4096, restored.getStackInSlot(MiningFactoryInventory.OUTPUT).getCount());
        assertEquals(4096, restored.getStackInSlot(MiningFactoryInventory.OUTPUT + 1).getCount());
        assertEquals(1, restored.getStackInSlot(MiningFactoryInventory.OUTPUT + 2).getCount());
    }

    @Test
    void onlyReconstructibleBlocksAreAcceptedAsInputs() {
        assertEquals(Blocks.STONE.defaultBlockState(), MiningLoot.stateOf(new ItemStack(Items.STONE)));
        assertNull(MiningLoot.stateOf(new ItemStack(Items.CHEST)));
        assertNull(MiningLoot.stateOf(new ItemStack(Items.DIAMOND)));
        var tagged = new ItemStack(Items.STONE);
        tagged.getOrCreateTagElement("BlockEntityTag").putString("LootTable", "minecraft:chests/simple_dungeon");
        assertNull(MiningLoot.stateOf(tagged));
    }

    @Test
    void validBlockStatePropertiesArePreservedAndInvalidValuesIgnored() {
        var input = new ItemStack(Items.OAK_LOG);
        input.getOrCreateTagElement("BlockStateTag").putString("axis", "x");
        assertEquals("x", MiningLoot.stateOf(input).getValue(
                net.minecraft.world.level.block.RotatedPillarBlock.AXIS).getName());
        input.getOrCreateTagElement("BlockStateTag").putString("axis", "invalid");
        assertEquals(Blocks.OAK_LOG.defaultBlockState(), MiningLoot.stateOf(input));
    }

    @Test
    void outputRangeNeverIncludesToolOrMatrix() {
        assertFalse(MiningFactoryInventory.isOutputSlot(MiningFactoryInventory.INPUT));
        assertFalse(MiningFactoryInventory.isOutputSlot(MiningFactoryInventory.TOOL));
        assertFalse(MiningFactoryInventory.isOutputSlot(MiningFactoryInventory.MATRIX));
        for (int slot = MiningFactoryInventory.OUTPUT;
                slot < MiningFactoryInventory.OUTPUT + MiningFactoryInventory.OUTPUT_COUNT; slot++) {
            assertTrue(MiningFactoryInventory.isOutputSlot(slot));
            assertEquals(4096, new MiningFactoryInventory(() -> {}).getSlotLimit(slot));
        }
    }
}
