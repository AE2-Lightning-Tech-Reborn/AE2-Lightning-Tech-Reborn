package com.moakiee.ae2lt.integration.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.me.storage.ExternalStorageFacade;

import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.common.capabilities.proxy.ProxyItemHandler;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;

class MekanismOutputItemHandlerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void onlyProductsAreVisibleEvenWhenEverySlotContainsTheSameItem() {
        var fixture = new Fixture();
        var storage = ExternalStorageFacade.of(fixture.handler);
        var stone = AEItemKey.of(Items.STONE);

        assertEquals(23, storage.getAvailableStacks().get(stone));
        assertEquals(23, storage.extract(stone, 100, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(11, fixture.input.getCount());
        assertEquals(17, fixture.extra.getCount());
        assertEquals(9, fixture.energy.getCount());
        assertTrue(fixture.output.isEmpty());
    }

    @Test
    void extractableAuxiliaryAndEnergySlotsRemainProtected() {
        var fixture = new Fixture();
        var raw = new ProxyItemHandler(fixture.inventory, Direction.NORTH, null);
        assertEquals(17, raw.extractItem(1, 64, true).getCount());
        assertEquals(9, raw.extractItem(2, 64, true).getCount());

        assertTrue(fixture.handler.extractItem(1, 64, false).isEmpty());
        assertTrue(fixture.handler.extractItem(2, 64, false).isEmpty());
        assertEquals(17, fixture.extra.getCount());
        assertEquals(9, fixture.energy.getCount());
    }

    @Test
    void mixedInputOutputGroupDoesNotMakeAnInputAProduct() {
        var fixture = new Fixture();
        assertTrue(fixture.handler.getStackInSlot(0).isEmpty());
        assertTrue(fixture.handler.extractItem(0, 64, false).isEmpty());
        assertEquals(23, fixture.handler.extractItem(3, 64, true).getCount());
        assertEquals(23, fixture.output.getCount());
    }

    @Test
    void bothSeparateOutputGroupsAreReturned() {
        var fixture = new Fixture();
        fixture.config = new ConfigInfo();
        fixture.config.addSlotInfo(DataType.OUTPUT_1, new InventorySlotInfo(false, true, fixture.input));
        fixture.config.addSlotInfo(DataType.OUTPUT_2, new InventorySlotInfo(false, true, fixture.output));
        var storage = ExternalStorageFacade.of(fixture.handler);

        assertEquals(34, storage.extract(AEItemKey.of(Items.STONE), 100,
                Actionable.MODULATE, IActionSource.empty()));
        assertEquals(17, fixture.extra.getCount());
    }

    @Test
    void changingExposedSlotsCannotReuseAnOldOutputIndex() {
        var fixture = new Fixture();
        fixture.inventory.slots = List.of(fixture.output);
        assertEquals(23, fixture.handler.getStackInSlot(0).getCount());
        fixture.inventory.slots = List.of(fixture.extra);
        assertTrue(fixture.handler.getStackInSlot(0).isEmpty());
        assertTrue(fixture.handler.extractItem(0, 64, false).isEmpty());
        fixture.inventory.slots = List.of(fixture.output);
        assertEquals(23, fixture.handler.extractItem(0, 64, false).getCount());
        assertEquals(17, fixture.extra.getCount());
    }

    @Test
    void replacingAnOutputGroupRevokesItsOldSlots() {
        var fixture = new Fixture();
        fixture.config.addSlotInfo(DataType.OUTPUT, new InventorySlotInfo(false, true, fixture.extra));
        assertTrue(fixture.handler.getStackInSlot(3).isEmpty());
        assertTrue(fixture.handler.extractItem(3, 64, false).isEmpty());
        assertEquals(17, fixture.handler.extractItem(1, 64, true).getCount());
        assertEquals(23, fixture.output.getCount());
    }

    @Test
    void outputClassificationAndExternalExtractionPermissionsAreCheckedLive() {
        var fixture = new Fixture();
        fixture.config = new ConfigInfo();
        assertTrue(fixture.handler.extractItem(3, 64, false).isEmpty());

        var lockedOutput = BasicInventorySlot.at(stack -> false, stack -> true, null, 0, 0);
        lockedOutput.setStack(new ItemStack(Items.STONE, 5));
        fixture.config.addSlotInfo(DataType.OUTPUT, new InventorySlotInfo(false, true, lockedOutput));
        fixture.inventory.slots = List.of(lockedOutput);
        assertEquals(5, fixture.handler.getStackInSlot(0).getCount());
        assertTrue(fixture.handler.extractItem(0, 64, false).isEmpty());
        assertEquals(5, lockedOutput.getCount());
    }

    @Test
    void compensationCannotInsertIntoInputsOrAuxiliarySlots() {
        var fixture = new Fixture();
        var remainder = new ItemStack(Items.STONE, 4);
        for (int slot = 0; slot < 3; slot++) {
            assertEquals(4, fixture.handler.insertItem(slot, remainder, false).getCount());
            assertFalse(fixture.handler.isItemValid(slot, remainder));
        }
        assertEquals(11, fixture.input.getCount());
        assertEquals(17, fixture.extra.getCount());
        assertEquals(9, fixture.energy.getCount());
    }

    private static final class Fixture {
        private final IInventorySlot input = slot(11);
        private final IInventorySlot extra = slot(17);
        private final IInventorySlot energy = slot(9);
        private final IInventorySlot output = slot(23);
        private final Inventory inventory = new Inventory(List.of(input, extra, energy, output));
        private ConfigInfo config = new ConfigInfo();
        private final MekanismOutputItemHandler handler;

        private Fixture() {
            config.addSlotInfo(DataType.INPUT, new InventorySlotInfo(true, true, input));
            config.addSlotInfo(DataType.EXTRA, new InventorySlotInfo(true, true, extra));
            config.addSlotInfo(DataType.ENERGY, new InventorySlotInfo(true, true, energy));
            config.addSlotInfo(DataType.OUTPUT, new InventorySlotInfo(false, true, output));
            config.addSlotInfo(DataType.INPUT_OUTPUT, new InventorySlotInfo(true, true, input, output));
            handler = new MekanismOutputItemHandler(
                    new ProxyItemHandler(inventory, Direction.NORTH, null), inventory, () -> config, Direction.NORTH);
        }

        private static IInventorySlot slot(int count) {
            var slot = BasicInventorySlot.at(null, 0, 0);
            slot.setStack(new ItemStack(Items.STONE, count));
            return slot;
        }
    }

    private static final class Inventory implements IMekanismInventory {
        private List<IInventorySlot> slots;

        private Inventory(List<IInventorySlot> slots) {
            this.slots = slots;
        }

        @Override
        public List<IInventorySlot> getInventorySlots(Direction side) {
            return slots;
        }

        @Override
        public void onContentsChanged() {}
    }
}
