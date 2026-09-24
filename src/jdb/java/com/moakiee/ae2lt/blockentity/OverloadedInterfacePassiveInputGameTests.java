package com.moakiee.ae2lt.blockentity;

import java.util.ArrayList;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.logic.BufferedInterfaceInput;
import com.moakiee.ae2lt.logic.OverloadedInterfaceLogic;
import com.moakiee.ae2lt.logic.energy.PowerCostUtil;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Uses real capabilities, powered AE grids, cells and block-entity persistence. */
@GameTestHolder("ae2lt_interface_input")
@PrefixGameTestTemplate(false)
public final class OverloadedInterfacePassiveInputGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final AEItemKey STONE = AEItemKey.of(Items.STONE);

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void normalPassiveInputFlushesWithAutomaticIoDisabled(GameTestHelper helper) {
        checkPassiveIo(helper, false);
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void wirelessPassiveInputFlushesWithNoRemoteConnections(GameTestHelper helper) {
        checkPassiveIo(helper, true);
    }

    private static void checkPassiveIo(GameTestHelper helper, boolean wireless) {
        var owner = fixture(helper, true, true);
        owner.setInterfaceMode(wireless ? OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS
                : OverloadedInterfaceBlockEntity.InterfaceMode.NORMAL);
        helper.runAfterDelay(40, () -> {
            var items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, owner.getBlockPos(), Direction.UP);
            check(items != null && owner.getMainNode().isActive(), "inactive fixture or missing item capability");
            for (int i = 0; i < 1000; i++) {
                check(items.insertItem(0, new ItemStack(Items.STONE), true).isEmpty(), "simulate rejected");
            }
            check(buffer(owner).isEmpty() && stored(owner, STONE) == 0, "simulation mutated ownership");
            for (int i = 0; i < 1000; i++) {
                check(items.insertItem(0, new ItemStack(Items.STONE), false).isEmpty(), "actual insertion rejected");
            }
            check(buffer(owner).amount(STONE) == 1000 && stored(owner, STONE) == 0,
                    "passive input went straight to the network");
            var advertised = new appeng.api.stacks.KeyCounter();
            ((OverloadedInterfaceLogic) owner.getInterfaceLogic()).getProxiedStorage().getAvailableStacks(advertised);
            check(advertised.get(STONE) == 0, "pending input was advertised as network stock");
            check(owner.hasGridItemIoWork(), "OFF modes stranded buffered input");
        });
        helper.runAfterDelay(50, () -> {
            check(buffer(owner).isEmpty() && stored(owner, STONE) == 1000, "scheduled flush lost/duplicated items");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void rejectingNetworkRetainsInputThroughSaveReloadAndRecovery(GameTestHelper helper) {
        var owner = fixture(helper, true, false);
        helper.runAfterDelay(40, () -> {
            var input = owner.getExposedGenericInv();
            check(input.insert(0, STONE, 64, Actionable.MODULATE) == 64, "empty network must allow local buffering");
        });
        helper.runAfterDelay(55, () -> {
            check(buffer(owner).amount(STONE) == 64 && stored(owner, STONE) == 0, "rejection lost input");
            var saved = owner.saveWithoutMetadata(helper.getLevel().registryAccess());
            owner.clearImportBuffer();
            owner.loadTag(saved, helper.getLevel().registryAccess());
            check(buffer(owner).amount(STONE) == 64, "save/reload changed input ownership");
            drive(helper).getInternalInventory().insertItem(0, AEItems.ITEM_CELL_1K.stack(), false);
        });
        helper.runAfterDelay(75, () -> {
            check(buffer(owner).isEmpty() && stored(owner, STONE) == 64, "recovered storage failed to drain");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void finiteBufferBackpressureFilterAndRemovalConserveItems(GameTestHelper helper) {
        var owner = fixture(helper, true, false);
        helper.runAfterDelay(40, () -> {
            var filter = new ItemStack(ModItems.OVERLOADED_FILTER_COMPONENT.get());
            ((ICellWorkbenchItem) filter.getItem()).getConfigInventory(filter).setStack(0,
                    new appeng.api.stacks.GenericStack(STONE, 1));
            owner.getFilterInv().setItemDirect(0, filter);
            owner.rebuildFilter();
            var input = owner.getExposedGenericInv();
            check(input.insert(0, AEItemKey.of(Items.DIRT), 1, Actionable.MODULATE) == 0, "filter bypass");
            long capacity = BufferedInterfaceInput.capacity(STONE.getType());
            check(input.insert(0, STONE, Long.MAX_VALUE, Actionable.SIMULATE) == capacity, "simulation capacity");
            check(buffer(owner).isEmpty(), "capacity probe reserved space");
            check(input.insert(0, STONE, capacity - 7, Actionable.MODULATE) == capacity - 7, "capacity fill");
            var items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, owner.getBlockPos(), Direction.UP);
            check(items.insertItem(0, new ItemStack(Items.STONE, 64), false).getCount() == 57, "wrong remainder");
            check(input.insert(1, STONE, 1, Actionable.MODULATE) == 0, "slot index bypassed shared limit");
            check(owner.exportSettings(appeng.util.SettingsFrom.MEMORY_CARD, null)
                    .get(com.moakiee.ae2lt.registry.ModDataComponents.INTERFACE_INPUT_BUFFER.get()) == null,
                    "memory card copied owned resources");
            var restored = dismantleAndReplace(helper, owner);
            check(buffer(restored).amount(STONE) == capacity, "dismantling truncated pending items");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void partiallyFullCellRetainsRemainderAcrossReloadAndCapacityRecovery(GameTestHelper helper) {
        var owner = fixture(helper, true, true);
        var capacity = new long[1];
        helper.startSequence().thenIdle(40).thenExecute(() -> {
            check(owner.getMainNode().isActive(), "partial-storage fixture inactive");
            var network = owner.getMainNode().getGrid().getStorageService().getInventory();
            capacity[0] = network.insert(STONE, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
            check(capacity[0] > 64 && capacity[0] < Long.MAX_VALUE, "expected finite item cell capacity");
            check(network.insert(STONE, capacity[0] - 11, Actionable.MODULATE, IActionSource.empty())
                    == capacity[0] - 11, "failed to prefill item cell");
            check(owner.getExposedGenericInv().insert(0, STONE, 64, Actionable.MODULATE) == 64,
                    "local buffer must accept beyond the remaining network capacity");
            check(buffer(owner).amount(STONE) == 64 && stored(owner, STONE) == capacity[0] - 11,
                    "admission bypassed the buffer");
        }).thenExecuteAfter(10, () -> {
            check(stored(owner, STONE) == capacity[0] && buffer(owner).amount(STONE) == 53,
                    "partial flush must retain all 53 rejected items");
            var saved = owner.saveWithoutMetadata(helper.getLevel().registryAccess());
            owner.clearImportBuffer();
            owner.loadTag(saved, helper.getLevel().registryAccess());
            check(buffer(owner).amount(STONE) == 53, "partial remainder changed on save/reload");
            var network = owner.getMainNode().getGrid().getStorageService().getInventory();
            check(network.extract(STONE, 32, Actionable.MODULATE, IActionSource.empty()) == 32,
                    "failed to release cell capacity");
        }).thenExecuteAfter(10, () -> {
            check(stored(owner, STONE) == capacity[0] && buffer(owner).amount(STONE) == 21,
                    "only the newly available 32 items of capacity may be filled");
            var network = owner.getMainNode().getGrid().getStorageService().getInventory();
            check(network.extract(STONE, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty()) == capacity[0],
                    "network ownership changed during recovery");
        }).thenExecuteAfter(10, () -> {
            check(buffer(owner).isEmpty() && stored(owner, STONE) == 21,
                    "last 21 owned items must flush exactly once after capacity returns");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void energyChargedOnceAndInactiveGridRejectsInput(GameTestHelper helper) {
        var owner = fixture(helper, false, true);
        helper.runAfterDelay(50, () -> {
            check(owner.getMainNode().isActive(), "finite powered grid did not activate; power="
                    + owner.getMainNode().getGrid().getEnergyService().getStoredPower());
            var energy = owner.getMainNode().getGrid().getEnergyService();
            double before = energy.getStoredPower();
            var input = owner.getExposedGenericInv();
            check(input.insert(0, STONE, 64, Actionable.SIMULATE) == 64, "powered simulation rejected");
            check(Math.abs(before - energy.getStoredPower()) < 0.001, "simulation charged energy");
            check(input.insert(0, STONE, 64, Actionable.MODULATE) == 64, "powered insertion rejected");
            check(Math.abs(before - energy.getStoredPower() - PowerCostUtil.cost(STONE, 64)) < 0.001,
                    "buffer admission charged wrong energy");
            long now = helper.getLevel().getGameTime();
            int phase = Math.floorMod(owner.getBlockPos().hashCode(), 5);
            long flushAt = now + Math.floorMod(phase - Math.floorMod(now, 5), 5);
            double afterAdmission = energy.getStoredPower();
            ((OverloadedInterfaceLogic) owner.getInterfaceLogic()).getProxiedStorage().runWithNetworkGuard(() ->
                    buffer(owner).flush(owner.getMainNode().getGrid().getStorageService().getInventory(),
                            IActionSource.ofMachine(owner), flushAt, phase, owner::saveChanges));
            check(Math.abs(afterAdmission - energy.getStoredPower()) < 0.001, "flush charged energy twice");
            check(stored(owner, STONE) == 64 && buffer(owner).isEmpty(), "finite-energy flush failed");
            energy.extractAEPower(Double.MAX_VALUE, Actionable.MODULATE, PowerMultiplier.ONE);
            check(input.insert(0, STONE, 1, Actionable.MODULATE) == 0, "unpowered input accepted");
        });
        helper.runAfterDelay(65, () -> {
            check(!owner.getMainNode().isActive(), "drained grid remained active");
            check(owner.getExposedGenericInv().insert(0, STONE, 1, Actionable.SIMULATE) == 0,
                    "inactive simulation accepted");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void fluidsPersistAndNetworkReentryIsRejectedWhileGuiRemainsImmediate(GameTestHelper helper) {
        var owner = fixture(helper, true, true);
        helper.runAfterDelay(40, () -> {
            var fluid = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, owner.getBlockPos(), Direction.UP);
            check(fluid != null, "missing fluid capability");
            check(fluid.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.SIMULATE) == 1000,
                    "fluid simulation rejected");
            check(buffer(owner).isEmpty(), "fluid simulation mutated state");
            check(fluid.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000,
                    "fluid admission rejected");
            var water = AEFluidKey.of(Fluids.WATER);
            var saved = owner.saveWithoutMetadata(helper.getLevel().registryAccess());
            owner.clearImportBuffer();
            owner.loadTag(saved, helper.getLevel().registryAccess());
            check(buffer(owner).amount(water) == 1000, "fluid reload lost input");
            var proxy = ((OverloadedInterfaceLogic) owner.getInterfaceLogic()).getProxiedStorage();
            proxy.runWithNetworkGuard(() -> {
                check(owner.getExposedGenericInv().insert(0, STONE, 64, Actionable.MODULATE) == 0,
                        "network reentry bypassed input guard");
                check(owner.getExposedGenericInv().insert(0, STONE, 64, Actionable.SIMULATE) == 0,
                        "network reentry simulation bypassed guard");
            });
            check(proxy.proxyInsert(STONE, 32, Actionable.MODULATE) == 32, "GUI proxy failed");
            check(buffer(owner).amount(STONE) == 0 && stored(owner, STONE) == 32, "GUI was unexpectedly buffered");
            var restored = dismantleAndReplace(helper, owner);
            check(buffer(restored).amount(water) == 1000, "dismantling lost fluid");
            drive(helper).getInternalInventory().insertItem(1, AEItems.FLUID_CELL_1K.stack(), false);
        });
        // Replacing the junction splits and rebuilds the AE grid. Measure recovery
        // from a powered node with mounted storage, not an assumed boot duration.
        helper.startSequence().thenIdle(45).thenWaitUntil(() -> {
            var restored = (OverloadedInterfaceBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
            helper.assertTrue(restored.getMainNode().isActive(), "waiting for rebuilt AE grid");
            var network = restored.getMainNode().getGrid().getStorageService().getInventory();
            helper.assertTrue(network.insert(AEFluidKey.of(Fluids.WATER), 1000, Actionable.SIMULATE, IActionSource.empty()) == 1000,
                    "waiting for fluid cell mount");
        }).thenExecuteAfter(10, () -> {
            var restored = (OverloadedInterfaceBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
            check(buffer(restored).isEmpty() && stored(restored, AEFluidKey.of(Fluids.WATER)) == 1000,
                    "fluid did not flush after grid recovery: pending="
                            + buffer(restored).amount(AEFluidKey.of(Fluids.WATER))
                            + ", stored=" + stored(restored, AEFluidKey.of(Fluids.WATER))
                            + ", active=" + restored.getMainNode().isActive());
        }).thenSucceed();
    }

    private static OverloadedInterfaceBlockEntity dismantleAndReplace(GameTestHelper helper,
                                                                      OverloadedInterfaceBlockEntity owner) {
        var drops = net.minecraft.world.level.block.Block.getDrops(owner.getBlockState(), helper.getLevel(),
                owner.getBlockPos(), owner, null, ItemStack.EMPTY);
        var item = drops.stream().filter(s -> s.is(ModBlocks.OVERLOADED_INTERFACE.get().asItem()))
                .findFirst().orElseThrow(() -> new IllegalStateException("missing interface drop"));
        check(item.has(com.moakiee.ae2lt.registry.ModDataComponents.INTERFACE_INPUT_BUFFER.get()), "missing owned input component");
        // These are the additional drops used by both normal break and wrench dismantling.
        var additional = new ArrayList<ItemStack>();
        owner.addAdditionalDrops(helper.getLevel(), owner.getBlockPos(), additional);
        check(additional.stream().noneMatch(s -> s.is(Items.STONE)), "duplicated portable input as loose items");
        owner.clearContent();
        check(buffer(owner).isEmpty(), "clearContent retained portable ownership");
        helper.setBlock(POS, net.minecraft.world.level.block.Blocks.AIR);
        helper.setBlock(POS, ModBlocks.OVERLOADED_INTERFACE.get());
        var restored = (OverloadedInterfaceBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
        ModBlocks.OVERLOADED_INTERFACE.get().setPlacedBy(helper.getLevel(), restored.getBlockPos(),
                restored.getBlockState(), null, item);
        return restored;
    }

    private static OverloadedInterfaceBlockEntity fixture(GameTestHelper helper, boolean creative, boolean withCell) {
        helper.setBlock(POS.east(), creative ? AEBlocks.CREATIVE_ENERGY_CELL.block() : AEBlocks.ENERGY_CELL.block());
        if (!creative) {
            var cell = (appeng.blockentity.networking.EnergyCellBlockEntity) helper.getLevel()
                    .getBlockEntity(helper.absolutePos(POS.east()));
            cell.injectAEPower(150000, Actionable.MODULATE);
        }
        helper.setBlock(POS, ModBlocks.OVERLOADED_INTERFACE.get());
        helper.setBlock(POS.west(), AEBlocks.DRIVE.block());
        if (withCell) drive(helper).getInternalInventory().insertItem(0, AEItems.ITEM_CELL_1K.stack(), false);
        var owner = (OverloadedInterfaceBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
        owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.OFF);
        owner.setExportMode(OverloadedInterfaceBlockEntity.ExportMode.OFF);
        return owner;
    }

    private static DriveBlockEntity drive(GameTestHelper helper) {
        return (DriveBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS.west()));
    }

    private static long stored(OverloadedInterfaceBlockEntity owner, AEKey key) {
        return owner.getMainNode().getGrid().getStorageService().getInventory()
                .extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    private static BufferedInterfaceInput buffer(OverloadedInterfaceBlockEntity owner) {
        try {
            var field = OverloadedInterfaceBlockEntity.class.getDeclaredField("passiveInput");
            field.setAccessible(true);
            return (BufferedInterfaceInput) field.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
