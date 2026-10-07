package com.moakiee.ae2lt.machine.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.moakiee.ae2lt.machine.lightningassembly.LightningAssemblyChamberInventory;
import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;
import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;
import com.moakiee.ae2lt.machine.miningfactory.MiningFactoryInventory;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ManualInputTransferTest {
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    record Layout(LargeStackItemHandler inventory, int inputCount, int output, int outputCount, int protectedSlot) {}

    static Stream<Layout> layouts() {
        return Stream.of(
                new Layout(new LightningSimulationChamberInventory(null), 3, 4, 1, 3),
                new Layout(new LightningAssemblyChamberInventory(null), 9, 10, 1, 9),
                new Layout(new OverloadProcessingFactoryInventory(null), 9, 10, 1, 9),
                new Layout(new MiningFactoryInventory(null), 1, 2, 9, 11));
    }

    @ParameterizedTest @MethodSource("layouts")
    void transfersOnlyMaterialInputsAtActualSlotCapacity(Layout layout) {
        var inv = layout.inventory();
        int amount = inv.getSlotLimit(0);
        inv.setItemDirect(0, new ItemStack(Items.STONE, amount));
        inv.setItemDirect(layout.protectedSlot(), new ItemStack(Items.DIAMOND));
        if (inv instanceof MiningFactoryInventory) inv.setItemDirect(1, new ItemStack(Items.DIAMOND_PICKAXE));
        AtomicInteger aborts = new AtomicInteger();
        var result = new ManualInputTransfer().execute(0, inv, 0, layout.inputCount(), layout.output(),
                layout.outputCount(), budget -> 0, aborts::incrementAndGet);
        assertEquals(amount, result.moved());
        assertEquals(amount, inv.getStackInSlot(layout.output()).getCount());
        assertTrue(inv.getStackInSlot(0).isEmpty());
        assertTrue(inv.getStackInSlot(layout.protectedSlot()).is(Items.DIAMOND));
        if (inv instanceof MiningFactoryInventory) assertTrue(inv.getStackInSlot(1).is(Items.DIAMOND_PICKAXE));
        assertEquals(1, aborts.get());
        assertEquals(0, result.remainingSlots());
        assertFalse(inv.isItemValid(layout.output(), new ItemStack(Items.STONE)));
    }

    @Test void partialMovePreservesComponentsAndNotifiesOnceAfterCancellation() {
        AtomicInteger notices = new AtomicInteger();
        AtomicInteger aborts = new AtomicInteger();
        var inv = new LightningSimulationChamberInventory(() -> {
            notices.incrementAndGet();
            if (notices.get() == 3) assertEquals(1, aborts.get());
        });
        ItemStack named = new ItemStack(Items.IRON_INGOT, 200);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Named input"));
        inv.setItemDirect(0, named);
        inv.setItemDirect(4, named.copyWithCount(8100));
        var result = new ManualInputTransfer().execute(0, inv, 0, 3, 4, 1, budget -> 0, aborts::incrementAndGet);
        assertEquals(92, result.moved());
        assertEquals(108, inv.getStackInSlot(0).getCount());
        assertEquals(8192, inv.getStackInSlot(4).getCount());
        assertTrue(ItemStack.isSameItemSameComponents(named, inv.getStackInSlot(4)));
        assertEquals(3, notices.get());
        assertEquals(1, result.remainingSlots());
    }

    @Test void incompatibleOutputDoesNotCancelOrNotify() {
        AtomicInteger notices = new AtomicInteger();
        var inv = new LightningSimulationChamberInventory(notices::incrementAndGet);
        var named = new ItemStack(Items.IRON_INGOT, 2);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Different"));
        inv.setItemDirect(0, named);
        inv.setItemDirect(4, new ItemStack(Items.IRON_INGOT));
        var result = new ManualInputTransfer().execute(0, inv, 0, 3, 4, 1, budget -> 0, () -> fail("No input moved"));
        assertEquals(0, result.moved());
        assertEquals(2, notices.get());
        assertEquals(2, inv.getStackInSlot(0).getCount());
    }

    @Test void matchingOutputIsFilledBeforeEmptyOutput() {
        var inv = new MiningFactoryInventory(null);
        inv.setItemDirect(0, new ItemStack(Items.STONE, 100));
        inv.setItemDirect(5, new ItemStack(Items.STONE, 4050));
        var result = new ManualInputTransfer().execute(0, inv, 0, 1, 2, 9, budget -> 0, () -> {});
        assertEquals(100, result.moved());
        assertEquals(4096, inv.getStackInSlot(5).getCount());
        assertEquals(54, inv.getStackInSlot(2).getCount());
    }

    @Test void differentInputsCanPassThroughSingleOutputInOneClick() {
        var inv = new LightningSimulationChamberInventory(null);
        inv.setItemDirect(0, new ItemStack(Items.IRON_INGOT, 500));
        inv.setItemDirect(1, new ItemStack(Items.GOLD_INGOT, 600));
        inv.setItemDirect(2, new ItemStack(Items.COPPER_INGOT, 700));
        AtomicInteger aborts = new AtomicInteger();
        var result = new ManualInputTransfer().execute(0, inv, 0, 3, 4, 1,
                accepting(inv, 4, Integer.MAX_VALUE), aborts::incrementAndGet);
        assertEquals(1800, result.moved());
        assertEquals(1800, result.exported());
        assertTrue(inv.isEmpty());
        assertEquals(1, aborts.get());
    }

    @Test void inputlessClickExportsExistingOutputWithoutCancelling() {
        var inv = new LightningSimulationChamberInventory(null);
        inv.setItemDirect(4, new ItemStack(Items.GOLD_INGOT, 900));
        var result = new ManualInputTransfer().execute(0, inv, 0, 3, 4, 1,
                accepting(inv, 4, Integer.MAX_VALUE), () -> fail("No input moved"));
        assertEquals(0, result.moved());
        assertEquals(900, result.exported());
        assertTrue(inv.isEmpty());
    }

    @Test void rejectingReceiverLeavesItemsInOutputAndRemainingInputsInPlace() {
        var inv = new LightningSimulationChamberInventory(null);
        inv.setItemDirect(0, new ItemStack(Items.IRON_INGOT, 100));
        inv.setItemDirect(1, new ItemStack(Items.GOLD_INGOT, 100));
        var result = new ManualInputTransfer().execute(0, inv, 0, 3, 4, 1, accepting(inv, 4, 0), () -> {});
        assertEquals(100, result.moved());
        assertEquals(0, result.exported());
        assertEquals(1, result.remainingSlots());
        assertEquals(100, inv.getStackInSlot(4).getCount());
        assertEquals(100, inv.getStackInSlot(1).getCount());
    }

    @Test void tinyReceiverCannotCauseUnboundedRetry() {
        var inv = new LightningSimulationChamberInventory(null);
        inv.setItemDirect(0, new ItemStack(Items.STONE, 1_000_000)); // Legacy oversized input.
        var result = new ManualInputTransfer().execute(0, inv, 0, 3, 4, 1, accepting(inv, 4, 1), () -> {});
        assertEquals(ManualInputTransfer.EXPORT_ATTEMPTS, result.exported());
        assertEquals(1_000_000, inv.getStackInSlot(0).getCount() + inv.getStackInSlot(4).getCount() + result.exported());
        assertTrue(inv.getStackInSlot(4).getCount() <= inv.getSlotLimit(4));
    }

    @Test void cooldownIsSharedAndRejectedClickDoesNoInventoryWork() {
        var controller = new ManualInputTransfer();
        var inv = new LightningSimulationChamberInventory(null);
        assertTrue(controller.execute(50, inv, 0, 3, 4, 1, budget -> 0, () -> {}).accepted());
        assertFalse(controller.execute(53, inv, 0, 3, 4, 1, budget -> { fail("Cooldown"); return 0; }, () -> {}).accepted());
        assertTrue(controller.execute(54, inv, 0, 3, 4, 1, budget -> 0, () -> {}).accepted());
    }

    @Test void reentrantRequestIsRejected() {
        var controller = new ManualInputTransfer();
        var inv = new LightningSimulationChamberInventory(null);
        controller.execute(0, inv, 0, 3, 4, 1, budget -> {
            assertFalse(controller.execute(100, inv, 0, 3, 4, 1, b -> 0, () -> {}).accepted());
            return 0;
        }, () -> {});
    }

    @Test void externalCallbackCannotDoubleExtractOrOccupyRemainderSpace() {
        var inv = new LightningSimulationChamberInventory(null);
        inv.setItemDirect(4, new ItemStack(Items.STONE, 8000));
        inv.setItemDirect(0, new ItemStack(Items.GOLD_INGOT));
        long sent = inv.exportOutput(4, offer -> {
            assertEquals(8000, offer.getCount());
            assertTrue(inv.extractItem(4, 8000, false).isEmpty());
            assertTrue(inv.extractItem(0, 1, false).isEmpty());
            assertEquals(10, inv.insertRecipeOutput(new ItemStack(Items.STONE, 10), false).getCount());
            assertThrows(IllegalStateException.class, () -> inv.setItemDirect(4, new ItemStack(Items.DIAMOND)));
            inv.getStackInSlot(0).shrink(1);
            return 123;
        });
        assertEquals(123, sent);
        assertEquals(7877, inv.getStackInSlot(4).getCount());
        assertEquals(1, inv.getStackInSlot(0).getCount());
    }

    @Test void unknownReceiverReceiptReleasesReservationWithoutReplay() {
        var inv = new LightningSimulationChamberInventory(null);
        inv.setItemDirect(4, new ItemStack(Items.STONE, 200));
        assertEquals(200, inv.exportOutput(4, stack -> { throw new IllegalArgumentException(); }));
        assertTrue(inv.getStackInSlot(4).isEmpty());
        inv.setItemDirect(4, new ItemStack(Items.DIRT, 3));
        assertEquals(3, inv.extractItem(4, 3, false).getCount());
    }

    private static ManualInputTransfer.Exporter accepting(LargeStackItemHandler inv, int slot, int limit) {
        return budget -> {
            if (inv.getStackInSlot(slot).isEmpty() || !budget.take()) return 0;
            return inv.exportOutput(slot, stack -> Math.min(limit, stack.getCount()));
        };
    }
}
