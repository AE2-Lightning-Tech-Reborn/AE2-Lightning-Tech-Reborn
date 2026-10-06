package com.moakiee.ae2lt.blockentity;

import java.util.ArrayList;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Settings;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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
            var items = owner.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
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
            var saved = owner.saveWithoutMetadata();
            owner.clearImportBuffer();
            owner.loadTag(saved);
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
            var items = owner.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
            check(items.insertItem(0, new ItemStack(Items.STONE, 64), false).getCount() == 57, "wrong remainder");
            check(input.insert(1, STONE, 1, Actionable.MODULATE) == 0, "slot index bypassed shared limit");
            var memorySettings = new net.minecraft.nbt.CompoundTag();
            owner.exportSettings(appeng.util.SettingsFrom.MEMORY_CARD, memorySettings, null);
            check(!memorySettings.contains("ae2ltPassiveInput"), "memory card copied owned resources");
            var restored = dismantleAndReplace(helper, owner);
            check(buffer(restored).amount(STONE) == capacity, "dismantling truncated pending items");
            helper.succeed();
        });
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
            var fluid = owner.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).orElse(null);
            check(fluid != null, "missing fluid capability");
            check(fluid.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.SIMULATE) == 1000,
                    "fluid simulation rejected");
            check(buffer(owner).isEmpty(), "fluid simulation mutated state");
            check(fluid.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000,
                    "fluid admission rejected");
            var water = AEFluidKey.of(Fluids.WATER);
            var saved = owner.saveWithoutMetadata();
            owner.clearImportBuffer();
            owner.loadTag(saved);
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

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void proxyViewsRefreshWithinTheSameTick(GameTestHelper helper) {
        var owner = fixture(helper, true, true);
        helper.runAfterDelay(40, () -> {
            var config = owner.getInterfaceLogic().getConfig();
            var proxy = ((OverloadedInterfaceLogic) owner.getInterfaceLogic()).getProxiedStorage();
            config.setStack(0, new appeng.api.stacks.GenericStack(STONE, 64));
            check(proxy.proxyInsert(STONE, 48, Actionable.MODULATE) == 48, "could not seed stock");
            checkProxyView(proxy, STONE, 48);
            check(proxy.proxyExtract(STONE, 8, Actionable.SIMULATE) == 8, "simulation rejected");
            checkProxyView(proxy, STONE, 48);
            check(proxy.proxyExtract(STONE, 8, Actionable.MODULATE) == 8, "extraction rejected");
            checkProxyView(proxy, STONE, 40);
            check(proxy.proxyInsert(STONE, 8, Actionable.MODULATE) == 8, "insertion rejected");
            checkProxyView(proxy, STONE, 48);
            config.setStack(0, new appeng.api.stacks.GenericStack(STONE, 16));
            checkProxyView(proxy, STONE, 16);
            owner.setSlotUnlimited(0, true);
            checkProxyView(proxy, STONE, 48);
            owner.setSlotUnlimited(0, false);
            checkProxyView(proxy, STONE, 16);
            config.setStack(0, null);
            checkProxyView(proxy, STONE, 0);
            check(stored(owner, STONE) == 48, "view changes mutated stock");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void guardedNetworkWritesRefreshProxyViews(GameTestHelper helper) {
        var owner = fixture(helper, true, true);
        helper.runAfterDelay(40, () -> {
            var proxy = ((OverloadedInterfaceLogic) owner.getInterfaceLogic()).getProxiedStorage();
            owner.getInterfaceLogic().getConfig().setStack(0, new appeng.api.stacks.GenericStack(STONE, 64));
            checkProxyView(proxy, STONE, 0);
            var network = owner.getMainNode().getGrid().getStorageService().getInventory();
            proxy.runWithNetworkGuard(() -> {
                check(proxy.proxyInsert(STONE, 8, Actionable.MODULATE) == 0, "guard allowed network reentry");
                check(network.insert(STONE, 32, Actionable.MODULATE, IActionSource.ofMachine(owner)) == 32,
                        "guarded insertion rejected");
            });
            checkProxyView(proxy, STONE, 32);
            try {
                proxy.runWithNetworkGuard(() -> {
                    check(network.extract(STONE, 8, Actionable.MODULATE, IActionSource.ofMachine(owner)) == 8,
                            "guarded extraction rejected");
                    throw new IllegalStateException("test interrupted after mutation");
                });
                throw new AssertionError("guard swallowed failure");
            } catch (IllegalStateException expected) {
                check("test interrupted after mutation".equals(expected.getMessage()), "unexpected guarded failure");
            }
            check(!proxy.isNetworkOperationInProgress(), "guard remained active after failure");
            checkProxyView(proxy, STONE, 24);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void duplicateConfigurationsShareStockAndRefreshDisplayKeys(GameTestHelper helper) {
        var owner = fixture(helper, true, true);
        helper.runAfterDelay(40, () -> {
            var config = owner.getInterfaceLogic().getConfig();
            var proxy = ((OverloadedInterfaceLogic) owner.getInterfaceLogic()).getProxiedStorage();
            config.setStack(0, new GenericStack(STONE, 16));
            config.setStack(1, new GenericStack(STONE, 32));
            check(proxy.proxyInsert(STONE, 80, Actionable.MODULATE) == 80, "could not seed shared stock");
            var snapshot = advertised(proxy);
            check(snapshot.get(STONE) == 48, "duplicate slots did not aggregate their caps");
            check(proxy.getStack(0).amount() == 16 && proxy.getStack(1).amount() == 32, "per-slot display cap changed");
            var accumulated = advertised(proxy);
            proxy.getAvailableStacks(accumulated);
            check(accumulated.get(STONE) == 96, "cache overwrote caller-owned counter");
            owner.setSlotUnlimited(0, true);
            owner.setSlotUnlimited(1, true);
            check(advertised(proxy).get(STONE) == 80, "unlimited caps overflowed or duplicated physical stock");
            config.setStack(0, null);
            config.setStack(1, new GenericStack(STONE, 8));
            check(advertised(proxy).get(STONE) == 8, "old cap survived refresh");
            check(proxy.getStack(1).amount() == 8, "display retained old amount");
            var gold = AEItemKey.of(Items.GOLD_INGOT);
            check(proxy.proxyInsert(gold, 8, Actionable.MODULATE) == 8, "could not seed replacement key");
            config.setStack(1, new GenericStack(gold, 8));
            var replacement = proxy.getStack(1);
            check(replacement.what().equals(gold) && replacement.amount() == 8, "display retained old key at equal amount");
            var refreshed = advertised(proxy);
            check(refreshed.get(STONE) == 0 && refreshed.get(gold) == 8, "old key survived refreshed exposure");
            config.setStack(1, null);
            check(!advertised(proxy).iterator().hasNext(), "empty view retained zero or stale entries");
            check(proxy.getStack(1) == null, "removed configuration retained display stack");
            check(snapshot.get(STONE) == 48, "refresh mutated previous caller snapshot");
            check(stored(owner, STONE) == 80 && stored(owner, gold) == 8, "view refresh changed physical stock");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void overlappingFuzzyViewsRefreshAcrossTicksAndUpgradeRemoval(GameTestHelper helper) {
        var owner = fixture(helper, true, true);
        var named = new ItemStack(Items.STONE);
        named.setHoverName(Component.literal("Configured variant"));
        var configuredVariant = AEItemKey.of(named);
        var other = new ItemStack(Items.STONE);
        other.setHoverName(Component.literal("Unconfigured variant"));
        var otherVariant = AEItemKey.of(other);
        var config = owner.getInterfaceLogic().getConfig();
        var proxy = ((OverloadedInterfaceLogic) owner.getInterfaceLogic()).getProxiedStorage();
        helper.runAfterDelay(40, () -> {
            config.setStack(0, new GenericStack(STONE, 16));
            config.setStack(1, new GenericStack(configuredVariant, 32));
            check(proxy.proxyInsert(STONE, 40, Actionable.MODULATE) == 40, "could not seed exact stock");
            check(proxy.proxyInsert(configuredVariant, 48, Actionable.MODULATE) == 48, "could not seed configured variant");
            check(proxy.proxyInsert(otherVariant, 64, Actionable.MODULATE) == 64, "could not seed unconfigured variant");
            var exact = advertised(proxy);
            check(exact.get(STONE) == 16 && exact.get(configuredVariant) == 32 && exact.get(otherVariant) == 0,
                    "exact view leaked fuzzy variants");
        });
        helper.runAfterDelay(42, () -> {
            var logic = owner.getInterfaceLogic();
            logic.getConfigManager().putSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
            check(logic.getUpgrades().insertItem(0, AEItems.FUZZY_CARD.stack(), false).isEmpty(), "fuzzy upgrade rejected");
            var fuzzy = advertised(proxy);
            check(fuzzy.get(STONE) == 40 && fuzzy.get(configuredVariant) == 48 && fuzzy.get(otherVariant) == 48,
                    "overlapping fuzzy configurations duplicated stock or lost shared caps");
            config.setStack(0, new GenericStack(STONE, 8));
            var reduced = advertised(proxy);
            check(reduced.get(STONE) == 40 && reduced.get(configuredVariant) == 40 && reduced.get(otherVariant) == 40,
                    "fuzzy refresh retained old caps or amounts");
            config.setStack(1, null);
            var single = advertised(proxy);
            check(single.get(STONE) == 8 && single.get(configuredVariant) == 8 && single.get(otherVariant) == 8,
                    "removed fuzzy configuration retained its cap");
        });
        helper.runAfterDelay(44, () -> {
            var nextTick = advertised(proxy);
            check(nextTick.get(STONE) == 8 && nextTick.get(configuredVariant) == 8 && nextTick.get(otherVariant) == 8,
                    "cross-tick refresh accumulated prior scratch values");
            owner.getInterfaceLogic().getUpgrades().setItemDirect(0, ItemStack.EMPTY);
            var exact = advertised(proxy);
            check(exact.get(STONE) == 8 && exact.get(configuredVariant) == 0 && exact.get(otherVariant) == 0,
                    "upgrade removal retained fuzzy exposure");
            config.setStack(0, null);
            check(!advertised(proxy).iterator().hasNext(), "cleared fuzzy view retained stale entries");
            check(stored(owner, STONE) == 40 && stored(owner, configuredVariant) == 48 && stored(owner, otherVariant) == 64,
                    "fuzzy view changed physical stock");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fuzzyModeChangesRefreshAdvertisedStockWithinTheSameTick(GameTestHelper helper) {
        var owner = fixture(helper, true, true);
        var undamaged = AEItemKey.of(Items.IRON_PICKAXE);
        var damagedStack = new ItemStack(Items.IRON_PICKAXE);
        damagedStack.setDamageValue(1);
        var damaged = AEItemKey.of(damagedStack);
        var logic = owner.getInterfaceLogic();
        var config = logic.getConfig();
        var proxy = ((OverloadedInterfaceLogic) logic).getProxiedStorage();
        helper.runAfterDelay(40, () -> {
            config.setStack(0, new GenericStack(undamaged, 8));
            check(proxy.proxyInsert(undamaged, 4, Actionable.MODULATE) == 4, "could not seed undamaged tools");
            check(proxy.proxyInsert(damaged, 12, Actionable.MODULATE) == 12, "could not seed damaged tools");
        });
        helper.runAfterDelay(42, () -> {
            var settings = logic.getConfigManager();
            settings.putSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
            check(logic.getUpgrades().insertItem(0, AEItems.FUZZY_CARD.stack(), false).isEmpty(), "fuzzy upgrade rejected");
            var broad = advertised(proxy);
            check(broad.get(undamaged) == 4 && broad.get(damaged) == 8, "broad fuzzy mode omitted damaged stock");
            var savedMode = new net.minecraft.nbt.CompoundTag();
            settings.writeToNBT(savedMode);
            long tick = helper.getLevel().getGameTime();
            settings.putSetting(Settings.FUZZY_MODE, FuzzyMode.PERCENT_99);
            check(proxy.extract(damaged, 1, Actionable.SIMULATE, IActionSource.empty()) == 0,
                    "strict fuzzy mode allowed damaged extraction");
            var strict = advertised(proxy);
            check(strict.get(undamaged) == 4 && strict.get(damaged) == 0,
                    "same-tick fuzzy mode change retained damaged stock");
            checkProxyView(proxy, undamaged, 4);
            settings.readFromNBT(savedMode);
            var restored = advertised(proxy);
            check(restored.get(undamaged) == 4 && restored.get(damaged) == 8,
                    "same-tick NBT setting restoration retained strict stock");
            check(proxy.extract(damaged, 1, Actionable.SIMULATE, IActionSource.empty()) == 1,
                    "restored fuzzy mode rejected damaged extraction");
            settings.putSetting(Settings.FUZZY_MODE, FuzzyMode.PERCENT_99);
            check(advertised(proxy).get(damaged) == 0, "repeated fuzzy mode change reused stale stock");
            check(broad.get(damaged) == 8 && !proxy.isNetworkOperationInProgress(),
                    "mode refresh mutated the caller snapshot or retained its guard");
            check(helper.getLevel().getGameTime() == tick, "test did not exercise same-tick changes");
            check(stored(owner, undamaged) == 4 && stored(owner, damaged) == 12,
                    "fuzzy mode changes mutated physical tool stock");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void failedProxyInsertionRefreshesViewsAndAllowsRecovery(GameTestHelper helper) {
        checkFailedProxyTransfer(helper, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void failedProxyExtractionRefreshesViewsAndAllowsRecovery(GameTestHelper helper) {
        checkFailedProxyTransfer(helper, false);
    }

    private static void checkFailedProxyTransfer(GameTestHelper helper, boolean inserting) {
        var owner = fixture(helper, true, false);
        helper.runAfterDelay(40, () -> {
            var proxy = ((OverloadedInterfaceLogic) owner.getInterfaceLogic()).getProxiedStorage();
            var service = owner.getMainNode().getGrid().getStorageService();
            long[] amount = {32};
            boolean[] interrupt = {false};
            var storage = new MEStorage() {
                @Override
                public long insert(AEKey key, long requested, Actionable mode, IActionSource source) {
                    if (!STONE.equals(key)) return 0;
                    if (mode == Actionable.MODULATE) {
                        check(proxy.proxyExtract(STONE, 1, Actionable.MODULATE) == 0, "insertion allowed proxy reentry");
                        amount[0] += requested;
                        service.invalidateCache();
                        if (interrupt[0]) throw new IllegalStateException("storage interrupted after mutation");
                    }
                    return requested;
                }

                @Override
                public long extract(AEKey key, long requested, Actionable mode, IActionSource source) {
                    if (!STONE.equals(key)) return 0;
                    long extracted = Math.min(amount[0], requested);
                    if (mode == Actionable.MODULATE) {
                        check(proxy.proxyInsert(STONE, 1, Actionable.MODULATE) == 0, "extraction allowed proxy reentry");
                        amount[0] -= extracted;
                        service.invalidateCache();
                        if (interrupt[0]) throw new IllegalStateException("storage interrupted after mutation");
                    }
                    return extracted;
                }

                @Override
                public void getAvailableStacks(KeyCounter out) {
                    if (amount[0] > 0) out.add(STONE, amount[0]);
                }

                @Override
                public Component getDescription() {
                    return Component.literal("Interrupted storage fixture");
                }
            };
            IStorageProvider provider = mounts -> mounts.mount(storage);
            service.addGlobalStorageProvider(provider);
            try {
                owner.getInterfaceLogic().getConfig().setStack(0, new GenericStack(STONE, 64));
                checkProxyView(proxy, STONE, 32);
                var snapshot = advertised(proxy);
                check(proxy.proxyInsert(STONE, 8, Actionable.SIMULATE) == 8, "insertion simulation rejected");
                check(proxy.proxyExtract(STONE, 8, Actionable.SIMULATE) == 8, "extraction simulation rejected");
                checkProxyView(proxy, STONE, 32);
                interrupt[0] = true;
                try {
                    if (inserting) proxy.proxyInsert(STONE, 8, Actionable.MODULATE);
                    else proxy.proxyExtract(STONE, 8, Actionable.MODULATE);
                    throw new AssertionError("proxy swallowed storage failure");
                } catch (IllegalStateException expected) {
                    check("storage interrupted after mutation".equals(expected.getMessage()), "unexpected storage failure");
                } finally {
                    interrupt[0] = false;
                }
                long expected = inserting ? 40 : 24;
                check(amount[0] == expected && stored(owner, STONE) == expected, "failed transfer changed ownership twice");
                check(!proxy.isNetworkOperationInProgress(), "proxy remained guarded after storage failure");
                checkProxyView(proxy, STONE, expected);
                check(snapshot.get(STONE) == 32, "failed transfer mutated prior caller snapshot");
                check(proxy.proxyInsert(STONE, 4, Actionable.MODULATE) == 4, "insertion did not recover");
                checkProxyView(proxy, STONE, expected + 4);
                check(proxy.proxyExtract(STONE, 4, Actionable.MODULATE) == 4, "extraction did not recover");
                checkProxyView(proxy, STONE, expected);
                check(amount[0] == expected && stored(owner, STONE) == expected, "recovery lost or duplicated stock");
                helper.succeed();
            } finally {
                service.removeGlobalStorageProvider(provider);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void reentrantSimulationDoesNotPoisonOtherDisplaySlots(GameTestHelper helper) {
        checkReentrantDisplay(helper, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void inventoryRefreshRejectsReentryAcrossDisplayAndTransferPaths(GameTestHelper helper) {
        checkReentrantDisplay(helper, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void normalAutomaticExportRefreshesProxyViews(GameTestHelper helper) {
        checkAutomaticExportViews(helper, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void wirelessAutomaticExportRefreshesProxyViews(GameTestHelper helper) {
        checkAutomaticExportViews(helper, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void directTransfersRefreshWarmNetworkSnapshots(GameTestHelper helper) {
        var owner = fixture(helper, true, true);
        helper.runAfterDelay(40, () -> {
            var proxy = ((OverloadedInterfaceLogic) owner.getInterfaceLogic()).getProxiedStorage();
            var service = owner.getMainNode().getGrid().getStorageService();
            owner.getInterfaceLogic().getConfig().setStack(0, new GenericStack(STONE, 64));
            check(proxy.proxyInsert(STONE, 32, Actionable.MODULATE) == 32, "could not seed warm network snapshot");
            proxy.runWithNetworkGuard(() -> {
                service.invalidateCache();
                check(service.getCachedInventory().get(STONE) == 32, "network snapshot did not contain seeded stock");
            });
            checkProxyView(proxy, STONE, 32);
            check(proxy.proxyExtract(STONE, 8, Actionable.SIMULATE) == 8, "warm snapshot simulation rejected");
            check(service.getCachedInventory().get(STONE) == 32, "simulation changed the network snapshot");
            check(proxy.proxyExtract(STONE, 8, Actionable.MODULATE) == 8, "warm snapshot extraction rejected");
            checkProxyView(proxy, STONE, 24);
            check(service.getCachedInventory().get(STONE) == 24, "extraction retained stale network snapshot");
            check(proxy.proxyInsert(STONE, 8, Actionable.MODULATE) == 8, "warm snapshot insertion rejected");
            checkProxyView(proxy, STONE, 32);
            check(service.getCachedInventory().get(STONE) == 32 && stored(owner, STONE) == 32,
                    "insertion retained stale network snapshot or changed ownership");
            helper.succeed();
        });
    }

    private static void checkAutomaticExportViews(GameTestHelper helper, boolean wireless) {
        var owner = fixture(helper, true, true);
        var targetPos = POS.north();
        helper.setBlock(targetPos, net.minecraft.world.level.block.Blocks.BARREL);
        var target = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(helper.absolutePos(targetPos));
        owner.setIOSpeedMode(OverloadedInterfaceBlockEntity.IOSpeedMode.FAST);
        if (wireless) {
            owner.setInterfaceMode(OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS);
            check(owner.addOrUpdateConnection(new OverloadedInterfaceBlockEntity.WirelessConnection(
                    helper.getLevel().dimension(), helper.absolutePos(targetPos), Direction.SOUTH)),
                    "wireless export target was rejected");
        } else {
            owner.setEnergyOutputDir(Direction.NORTH);
        }
        Runnable exportAndVerify = () -> {
            var proxy = ((OverloadedInterfaceLogic) owner.getInterfaceLogic()).getProxiedStorage();
            check(stored(owner, STONE) == 32 && target.isEmpty(), "automatic export ran before the view probe");
            var snapshot = advertised(proxy);
            checkProxyView(proxy, STONE, 32);
            owner.tickGridItemIo();
            check(target.getItem(0).is(Items.STONE) && target.getItem(0).getCount() == 32,
                    "automatic export did not reach its target");
            check(stored(owner, STONE) == 0, "automatic export left physical stock behind");
            checkProxyView(proxy, STONE, 0);
            check(snapshot.get(STONE) == 32 && !proxy.isNetworkOperationInProgress(),
                    "automatic export changed a prior snapshot or leaked its guard");
            helper.succeed();
        };
        helper.runAfterDelay(40, () -> {
            var proxy = ((OverloadedInterfaceLogic) owner.getInterfaceLogic()).getProxiedStorage();
            owner.getInterfaceLogic().getConfig().setStack(0, new GenericStack(STONE, 64));
            check(proxy.proxyInsert(STONE, 32, Actionable.MODULATE) == 32, "could not seed automatic export");
            owner.setExportMode(OverloadedInterfaceBlockEntity.ExportMode.AUTO);
            if (wireless) {
                owner.tickGridItemIo();
                owner.getMainNode().ifPresent((grid, node) -> grid.getTickManager().sleepDevice(node));
            } else {
                exportAndVerify.run();
            }
        });
        if (wireless) helper.runAfterDelay(41, exportAndVerify);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void automaticExportGuardsCallbacksAndRecoversViewsAfterStorageFailure(GameTestHelper helper) {
        var owner = fixture(helper, true, false);
        var targetPos = POS.north();
        helper.setBlock(targetPos, net.minecraft.world.level.block.Blocks.BARREL);
        var target = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(helper.absolutePos(targetPos));
        owner.setIOSpeedMode(OverloadedInterfaceBlockEntity.IOSpeedMode.FAST);
        owner.setEnergyOutputDir(Direction.NORTH);
        helper.runAfterDelay(40, () -> {
            var proxy = ((OverloadedInterfaceLogic) owner.getInterfaceLogic()).getProxiedStorage();
            var service = owner.getMainNode().getGrid().getStorageService();
            long[] amount = {32};
            boolean[] interrupt = {true};
            int[] callbacks = {0};
            var storage = new MEStorage() {
                @Override
                public long extract(AEKey key, long requested, Actionable mode, IActionSource source) {
                    if (!STONE.equals(key)) return 0;
                    long extracted = Math.min(amount[0], requested);
                    if (mode == Actionable.MODULATE && extracted > 0) {
                        callbacks[0]++;
                        check(proxy.isNetworkOperationInProgress(), "automatic export callback ran outside its guard");
                        check(proxy.proxyExtract(STONE, 1, Actionable.MODULATE) == 0,
                                "automatic export callback allowed recursive extraction");
                        check(owner.getExposedGenericInv().insert(0, STONE, 1, Actionable.MODULATE) == 0,
                                "automatic export callback accepted recursive input");
                        owner.tickGridItemIo();
                        check(target.isEmpty(), "recursive automatic export reached the target");
                        amount[0] -= interrupt[0] ? Math.min(8, extracted) : extracted;
                        service.invalidateCache();
                        if (interrupt[0]) throw new IllegalStateException("automatic storage interrupted after mutation");
                    }
                    return extracted;
                }

                @Override
                public void getAvailableStacks(KeyCounter out) {
                    if (amount[0] > 0) out.add(STONE, amount[0]);
                }

                @Override
                public Component getDescription() {
                    return Component.literal("Automatic export failure fixture");
                }
            };
            IStorageProvider provider = mounts -> mounts.mount(storage);
            service.addGlobalStorageProvider(provider);
            try {
                owner.getInterfaceLogic().getConfig().setStack(0, new GenericStack(STONE, 64));
                checkProxyView(proxy, STONE, 32);
                var snapshot = advertised(proxy);
                var originalInventory = proxy.getNetworkInventory(service);
                check(originalInventory == proxy.getNetworkInventory(service), "network wrapper was not reused");
                owner.setExportMode(OverloadedInterfaceBlockEntity.ExportMode.AUTO);
                try {
                    owner.tickGridItemIo();
                    throw new AssertionError("automatic export swallowed storage failure");
                } catch (IllegalStateException expected) {
                    check("automatic storage interrupted after mutation".equals(expected.getMessage()),
                            "unexpected automatic export failure: " + expected.getMessage());
                }
                check(amount[0] == 24 && target.isEmpty() && buffer(owner).isEmpty(),
                        "interrupted export was replayed or buffered without a known result");
                checkProxyView(proxy, STONE, 24);
                check(snapshot.get(STONE) == 32 && callbacks[0] == 1 && !proxy.isNetworkOperationInProgress(),
                        "automatic export failure changed a snapshot or leaked its guard");
                interrupt[0] = false;
                owner.setExportMode(OverloadedInterfaceBlockEntity.ExportMode.OFF);
                owner.setExportMode(OverloadedInterfaceBlockEntity.ExportMode.AUTO);
                owner.tickGridItemIo();
                checkProxyView(proxy, STONE, 0);
                check(target.getItem(0).getCount() == 24 && callbacks[0] == 2,
                        "automatic export did not recover after failure");
                helper.succeed();
            } finally {
                service.removeGlobalStorageProvider(provider);
            }
        });
    }

    private static void checkReentrantDisplay(GameTestHelper helper, boolean reportedStock) {
        var owner = fixture(helper, true, false);
        helper.runAfterDelay(40, () -> {
            var proxy = ((OverloadedInterfaceLogic) owner.getInterfaceLogic()).getProxiedStorage();
            var service = owner.getMainNode().getGrid().getStorageService();
            boolean[] probe = {false};
            int[] callbacks = {0};
            boolean[] unguardedCallback = {false};
            var storage = new MEStorage() {
                private void checkReentry() {
                    if (!probe[0]) return;
                    callbacks[0]++;
                    unguardedCallback[0] |= !proxy.isNetworkOperationInProgress();
                    check(proxy.proxyInsert(STONE, 1, Actionable.SIMULATE) == 0, "inventory callback allowed insertion reentry");
                    check(proxy.proxyExtract(STONE, 1, Actionable.SIMULATE) == 0, "inventory callback allowed extraction reentry");
                    check(proxy.getAmount(1) == 0 && proxy.getStack(1) == null, "inventory callback exposed recursive stock");
                    var nested = advertised(proxy);
                    check(!nested.iterator().hasNext(), "inventory callback recursively advertised stock");
                }

                @Override
                public long extract(AEKey key, long requested, Actionable mode, IActionSource source) {
                    if (!STONE.equals(key)) return 0;
                    if (!reportedStock && mode == Actionable.SIMULATE) checkReentry();
                    return mode == Actionable.SIMULATE ? Math.min(32, requested) : 0;
                }

                @Override
                public void getAvailableStacks(KeyCounter out) {
                    if (reportedStock) {
                        checkReentry();
                        out.add(STONE, 32);
                    }
                }

                @Override
                public Component getDescription() {
                    return Component.literal("Reentrant inventory fixture");
                }
            };
            IStorageProvider provider = mounts -> mounts.mount(storage);
            service.addGlobalStorageProvider(provider);
            try {
                var config = owner.getInterfaceLogic().getConfig();
                config.setStack(0, new GenericStack(STONE, 16));
                config.setStack(1, new GenericStack(STONE, 64));
                service.invalidateCache();
                probe[0] = true;
                check(proxy.getAmount(0) == 16, "outer display query lost stock");
                check(proxy.getAmount(1) == 32, "recursive zero poisoned another display slot");
                var second = proxy.getStack(1);
                check(second != null && second.what().equals(STONE) && second.amount() == 32,
                        "recursive display query overwrote another slot");
                check(advertised(proxy).get(STONE) == 32, "reentrant refresh lost or duplicated advertised stock");
                check(callbacks[0] > 0 && !proxy.isNetworkOperationInProgress(), "callback was not exercised or guard leaked");
                check(!unguardedCallback[0], "inventory callback ran outside the network guard");
                probe[0] = false;
                check(stored(owner, STONE) == 32, "reentrant view changed physical stock");
                config.setStack(0, null);
                config.setStack(1, null);
                check(!advertised(proxy).iterator().hasNext(), "view did not recover after configuration removal");
                helper.succeed();
            } finally {
                probe[0] = false;
                service.removeGlobalStorageProvider(provider);
            }
        });
    }

    private static KeyCounter advertised(OverloadedInterfaceLogic.ProxiedStorageInv proxy) {
        var available = new KeyCounter();
        proxy.getAvailableStacks(available);
        return available;
    }

    private static void checkProxyView(OverloadedInterfaceLogic.ProxiedStorageInv proxy, AEKey key, long amount) {
        check(proxy.getAmount(0) == amount, "stale display amount: expected " + amount + ", got " + proxy.getAmount(0));
        var display = proxy.getStack(0);
        check(amount == 0 ? display == null : display != null && display.what().equals(key) && display.amount() == amount,
                "stale display stack");
        var available = new appeng.api.stacks.KeyCounter();
        proxy.getAvailableStacks(available);
        check(available.get(key) == amount, "stale advertised stock: expected " + amount + ", got " + available.get(key));
    }

    private static OverloadedInterfaceBlockEntity dismantleAndReplace(GameTestHelper helper,
                                                                      OverloadedInterfaceBlockEntity owner) {
        var drops = net.minecraft.world.level.block.Block.getDrops(owner.getBlockState(), helper.getLevel(),
                owner.getBlockPos(), owner, null, ItemStack.EMPTY);
        var item = drops.stream().filter(s -> s.is(ModBlocks.OVERLOADED_INTERFACE.get().asItem()))
                .findFirst().orElseThrow(() -> new IllegalStateException("missing interface drop"));
        var settings = item.getTag();
        var blockEntitySettings = item.getTagElement("BlockEntityTag");
        check((settings != null && settings.contains("ae2ltPassiveInput", net.minecraft.nbt.Tag.TAG_LIST))
                || (blockEntitySettings != null && blockEntitySettings.contains("ae2ltPassiveInput", net.minecraft.nbt.Tag.TAG_LIST)),
                "missing owned input NBT");
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
