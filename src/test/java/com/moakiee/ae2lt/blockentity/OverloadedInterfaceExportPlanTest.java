package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import com.moakiee.ae2lt.logic.energy.PowerCostUtil;

class OverloadedInterfaceExportPlanTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void indexedPlanReusesStatesAcrossReorderingAndDuplicates() {
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        var entries = List.of(new OverloadedInterfaceBlockEntity.ExportConfigEntry(stone, 64),
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(dirt, 64));
        var state = new OverloadedInterfaceBlockEntity.ConnectionState();
        var plan = state.exportPlan(stone.getType(), entries);
        plan.transfers()[0].untilTick = 91;
        assertSame(plan, state.exportPlan(stone.getType(), entries));

        var reordered = state.exportPlan(stone.getType(), List.of(
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(dirt, 32),
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(stone, 16),
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(stone, 8)));
        assertNotSame(plan, reordered);
        assertSame(reordered.transfers()[1], reordered.transfers()[2]);
        assertSame(plan.transfers()[0], reordered.transfers()[1]);
        assertEquals(91, reordered.transfers()[1].untilTick);
        state.resetWirelessIo(OverloadedInterfaceBlockEntity.IOSpeedMode.FAST);
        assertNotSame(reordered.transfers()[1], state.exportPlan(stone.getType(), entries).transfers()[0]);
    }

    @Test
    void capacityEvictionDoesNotSplitDuplicateKeyStates() {
        var state = new OverloadedInterfaceBlockEntity.ConnectionState();
        for (int index = 0; index < 127; index++) {
            var stack = new ItemStack(Items.STONE);
            stack.setHoverName(Component.literal("old-" + index));
            var key = AEItemKey.of(stack);
            state.exportPlan(key.getType(), List.of(new OverloadedInterfaceBlockEntity.ExportConfigEntry(key, 64)));
        }
        var shared = AEItemKey.of(Items.DIRT);
        var entries = List.of(new OverloadedInterfaceBlockEntity.ExportConfigEntry(shared, 64),
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(AEItemKey.of(Items.COBBLESTONE), 64),
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(shared, 32));
        var plan = state.exportPlan(shared.getType(), entries);
        assertSame(plan.transfers()[0], plan.transfers()[2]);
    }

    @Test
    void preferredSlotFallsBackWithoutChangingSimulationState() {
        var transfer = new OverloadedInterfaceBlockEntity.ExportTransferState();
        transfer.preferredSlot = 0;
        var handler = new ItemStackHandler(3);
        handler.setStackInSlot(0, new ItemStack(Items.DIRT, 64));
        var stone = AEItemKey.of(Items.STONE);
        assertEquals(64, OverloadedInterfaceBlockEntity.insertIntoItemHandler(transfer, handler, stone, 64, true));
        assertEquals(0, transfer.preferredSlot);
        assertTrue(handler.getStackInSlot(1).isEmpty());
        assertEquals(64, OverloadedInterfaceBlockEntity.insertIntoItemHandler(transfer, handler, stone, 64, false));
        assertEquals(1, transfer.preferredSlot);
        assertEquals(64, handler.getStackInSlot(1).getCount());
    }

    @Test
    void directExportUsesActualSupplyWithoutSuccessSimulation() {
        var stone = AEItemKey.of(Items.STONE);
        var supply = new Supply();
        supply.items = 23;
        var handler = new ItemStackHandler(1);
        double[] power = {100};
        var transfer = new OverloadedInterfaceBlockEntity.ExportTransferState();
        assertEquals(23, OverloadedInterfaceBlockEntity.exportBoundedItemKey(transfer,
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(stone, 64), handler, supply, IActionSource.empty(),
                new PowerCostUtil.EnergyAccess(), () -> grid(power), (key, amount) -> fail("unexpected overflow"),
                OverloadedInterfaceBlockEntity.IOSpeedMode.FAST, 10, -1));
        assertEquals(0, supply.items);
        assertEquals(23, handler.getStackInSlot(0).getCount());
        assertEquals(0, supply.simulations);
        assertEquals(1, supply.extractions);
        assertEquals(94, power[0]);
    }

    @Test
    void targetMutationBuffersOnlyUndeliveredExtraction() {
        var stone = AEItemKey.of(Items.STONE);
        var supply = new Supply();
        var handler = new ItemStackHandler(1);
        supply.afterExtract = () -> handler.setStackInSlot(0, new ItemStack(Items.DIRT, 64));
        long[] overflow = {0};
        double[] power = {100};
        assertEquals(0, OverloadedInterfaceBlockEntity.exportBoundedItemKey(
                new OverloadedInterfaceBlockEntity.ExportTransferState(),
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(stone, 64), handler, supply, IActionSource.empty(),
                new PowerCostUtil.EnergyAccess(), () -> grid(power), (key, amount) -> {
                    assertEquals(stone, key);
                    overflow[0] += amount;
                }, OverloadedInterfaceBlockEntity.IOSpeedMode.FAST, 10, -1));
        assertEquals(100, supply.items + overflow[0]);
        assertEquals(64, overflow[0]);
        assertEquals(100, power[0]);
    }

    @Test
    void offlineAndIdleReserveNeverExtractSupply() {
        var stone = AEItemKey.of(Items.STONE);
        var entry = new OverloadedInterfaceBlockEntity.ExportConfigEntry(stone, 64);
        var supply = new Supply();
        var handler = new ItemStackHandler(1);
        double[] power = {4};
        var access = new PowerCostUtil.EnergyAccess();
        for (IGrid candidate : new IGrid[] {null, grid(power)}) {
            assertEquals(0, OverloadedInterfaceBlockEntity.exportBoundedItemKey(
                    new OverloadedInterfaceBlockEntity.ExportTransferState(), entry, handler, supply,
                    IActionSource.empty(), access, () -> candidate, (key, amount) -> fail(),
                    OverloadedInterfaceBlockEntity.IOSpeedMode.FAST, 10, -1));
        }
        assertEquals(0, supply.extractions);
        power[0] = 12;
        assertEquals(32, OverloadedInterfaceBlockEntity.exportBoundedItemKey(
                new OverloadedInterfaceBlockEntity.ExportTransferState(), entry, handler, supply,
                IActionSource.empty(), access, () -> grid(power), (key, amount) -> fail(),
                OverloadedInterfaceBlockEntity.IOSpeedMode.FAST, 11, -1));
        assertEquals(4, power[0]);
        assertEquals(100, supply.items + handler.getStackInSlot(0).getCount());
    }

    @Test
    void barrelProjectionMatchesForgeSimulation() {
        var barrel = new BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
        var handler = new InvWrapper(barrel);
        var transfer = new OverloadedInterfaceBlockEntity.ExportTransferState();
        var plain = AEItemKey.of(Items.STONE);
        var namedStack = new ItemStack(Items.STONE);
        namedStack.setHoverName(Component.literal("variant"));
        var named = AEItemKey.of(namedStack);
        var random = new SplittableRandom(20261004L);
        for (int round = 0; round < 500; round++) {
            for (int slot = 0; slot < 27; slot++) {
                var key = slot % 3 == 0 ? named : slot % 3 == 1 ? plain : AEItemKey.of(Items.DIRT);
                barrel.setItem(slot, random.nextBoolean() ? key.toStack(random.nextInt(1, 65)) : ItemStack.EMPTY);
            }
            transfer.preferredSlot = random.nextInt(-1, 29);
            var key = round % 2 == 0 ? plain : named;
            int amount = random.nextInt(1, 2049);
            var remainder = key.toStack(amount);
            int preferred = transfer.preferredSlot >= 0 && transfer.preferredSlot < 27
                    ? transfer.preferredSlot : -1;
            if (preferred >= 0) remainder = handler.insertItem(preferred, remainder, true);
            for (int slot = 0; slot < 27 && !remainder.isEmpty(); slot++) {
                if (slot != preferred) remainder = handler.insertItem(slot, remainder, true);
            }
            assertEquals(amount - remainder.getCount(),
                    OverloadedInterfaceBlockEntity.insertIntoItemHandler(transfer, handler, key, amount, true));
        }
    }

    @Test
    void customBarrelWrapperStillUsesItsSimulationCallback() {
        var barrel = new BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
        var calls = new AtomicInteger();
        var handler = new InvWrapper(barrel) {
            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                calls.incrementAndGet();
                return stack;
            }
        };
        var transfer = new OverloadedInterfaceBlockEntity.ExportTransferState();
        assertEquals(0, OverloadedInterfaceBlockEntity.insertIntoItemHandler(
                transfer, handler, AEItemKey.of(Items.STONE), 64, true));
        assertEquals(27, calls.get());
        assertEquals(-1, transfer.preferredSlot);
        assertTrue(barrel.isEmpty());
    }

    private static final class Supply implements MEStorage {
        long items = 100;
        int simulations;
        int extractions;
        Runnable afterExtract = () -> {};

        @Override
        public Component getDescription() {
            return Component.literal("supply");
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.SIMULATE) {
                simulations++;
                return Math.min(items, amount);
            }
            extractions++;
            long extracted = Math.min(items, amount);
            items -= extracted;
            afterExtract.run();
            return extracted;
        }
    }

    private static IGrid grid(double[] power) {
        var energy = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
                new Class<?>[] {IEnergyService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getIdlePowerUsage")) return 4.0;
                    if (method.getName().equals("extractAEPower")) {
                        double extracted = Math.min(power[0], (Double) args[0]);
                        if (args[1] == Actionable.MODULATE) power[0] -= extracted;
                        return extracted;
                    }
                    throw new AssertionError(method);
                });
        return (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[] {IGrid.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getEnergyService") || method.getName().equals("getService")) {
                        return energy;
                    }
                    throw new AssertionError(method);
                });
    }
}
