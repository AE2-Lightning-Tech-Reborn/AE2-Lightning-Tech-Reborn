package com.moakiee.ae2lt.machine.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.moakiee.ae2lt.machine.lightningassembly.LightningAssemblyChamberInventory;
import com.moakiee.ae2lt.machine.lightningchamber.LargeStackItemHandler;
import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.EarlyLoadingException;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ManualInputTransferTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), new EarlyLoadingException("test bootstrap", null, List.of()));
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    record Layout(LargeStackItemHandler inventory, int inputCount, int output, int protectedSlot) {}

    static Stream<Layout> layouts() {
        return Stream.of(
                new Layout(new LightningSimulationChamberInventory(null), 3, 4, 3),
                new Layout(new LightningAssemblyChamberInventory(null), 9, 10, 9),
                new Layout(new OverloadProcessingFactoryInventory(null), 9, 10, 9));
    }

    @ParameterizedTest
    @MethodSource("layouts")
    void transfersOnlyMaterialInputs(Layout layout) {
        var inventory = layout.inventory();
        int amount = inventory.getSlotLimit(0);
        inventory.setItemDirect(0, new ItemStack(Items.STONE, amount));
        inventory.setItemDirect(layout.protectedSlot(), new ItemStack(Items.DIAMOND));
        AtomicInteger cancellations = new AtomicInteger();
        var result = new ManualInputTransfer().execute(0, inventory, 0, layout.inputCount(),
                layout.output(), 1, budget -> 0, cancellations::incrementAndGet);
        assertEquals(amount, result.moved());
        assertEquals(amount, inventory.getStackInSlot(layout.output()).getCount());
        assertTrue(inventory.getStackInSlot(0).isEmpty());
        assertTrue(inventory.getStackInSlot(layout.protectedSlot()).is(Items.DIAMOND));
        assertEquals(1, cancellations.get());
        assertEquals(0, result.remainingSlots());
    }

    @Test
    void partialMovePreservesTagsAndBatchesNotification() {
        AtomicInteger notices = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        var inventory = new LightningSimulationChamberInventory(notices::incrementAndGet);
        ItemStack named = new ItemStack(Items.IRON_INGOT, 200);
        named.setHoverName(Component.literal("Named input"));
        inventory.setItemDirect(0, named);
        inventory.setItemDirect(4, named.copyWithCount(8100));
        var result = new ManualInputTransfer().execute(0, inventory, 0, 3, 4, 1,
                budget -> 0, cancellations::incrementAndGet);
        assertEquals(92, result.moved());
        assertEquals(108, inventory.getStackInSlot(0).getCount());
        assertEquals(8192, inventory.getStackInSlot(4).getCount());
        assertTrue(ItemStack.isSameItemSameTags(named, inventory.getStackInSlot(4)));
        assertEquals(3, notices.get());
        assertEquals(1, cancellations.get());
    }

    @Test
    void rejectedOutputDoesNotCancelProcessing() {
        var inventory = new LightningSimulationChamberInventory(null);
        ItemStack named = new ItemStack(Items.IRON_INGOT, 2);
        named.setHoverName(Component.literal("Different"));
        inventory.setItemDirect(0, named);
        inventory.setItemDirect(4, new ItemStack(Items.IRON_INGOT));
        var result = new ManualInputTransfer().execute(0, inventory, 0, 3, 4, 1,
                budget -> 0, () -> fail("No input moved"));
        assertEquals(0, result.moved());
        assertEquals(2, inventory.getStackInSlot(0).getCount());
    }

    @Test
    void oneClickCanExportDifferentInputsWithBoundedAttempts() {
        var inventory = new LightningSimulationChamberInventory(null);
        inventory.setItemDirect(0, new ItemStack(Items.IRON_INGOT, 500));
        inventory.setItemDirect(1, new ItemStack(Items.GOLD_INGOT, 600));
        inventory.setItemDirect(2, new ItemStack(Items.COPPER_INGOT, 700));
        AtomicInteger cancellations = new AtomicInteger();
        var result = new ManualInputTransfer().execute(0, inventory, 0, 3, 4, 1,
                budget -> budget.take() ? inventory.exportOutput(4, stack -> stack.getCount()) : 0,
                cancellations::incrementAndGet);
        assertEquals(1800, result.moved());
        assertEquals(1800, result.exported());
        assertTrue(inventory.isEmpty());
        assertEquals(1, cancellations.get());
    }

    @Test
    void cooldownAndReentrancyRejectExtraClicks() {
        var transfer = new ManualInputTransfer();
        var inventory = new LightningSimulationChamberInventory(null);
        assertTrue(transfer.execute(50, inventory, 0, 3, 4, 1, budget -> {
            assertFalse(transfer.execute(100, inventory, 0, 3, 4, 1, ignored -> 0, () -> {}).accepted());
            return 0;
        }, () -> {}).accepted());
        assertFalse(transfer.execute(53, inventory, 0, 3, 4, 1,
                budget -> { fail("Cooldown"); return 0; }, () -> {}).accepted());
        assertTrue(transfer.execute(54, inventory, 0, 3, 4, 1, budget -> 0, () -> {}).accepted());
    }

    @Test
    void foreignStorageCannotDoubleExtractOrMutateReservedOutput() {
        var inventory = new LightningSimulationChamberInventory(null);
        inventory.setItemDirect(4, new ItemStack(Items.STONE, 8000));
        inventory.setItemDirect(0, new ItemStack(Items.GOLD_INGOT));
        long sent = inventory.exportOutput(4, offer -> {
            assertEquals(8000, offer.getCount());
            assertTrue(inventory.extractItem(4, 8000, false).isEmpty());
            assertTrue(inventory.extractItem(0, 1, false).isEmpty());
            assertThrows(IllegalStateException.class,
                    () -> inventory.setItemDirect(4, new ItemStack(Items.DIAMOND)));
            inventory.getStackInSlot(0).shrink(1);
            return 123;
        });
        assertEquals(123, sent);
        assertEquals(7877, inventory.getStackInSlot(4).getCount());
        assertEquals(1, inventory.getStackInSlot(0).getCount());
    }
}
