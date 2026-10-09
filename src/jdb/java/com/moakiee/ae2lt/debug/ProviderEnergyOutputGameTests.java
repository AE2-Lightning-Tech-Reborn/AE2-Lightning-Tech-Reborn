package com.moakiee.ae2lt.debug;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import appeng.capabilities.Capabilities;
import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.glodblock.github.appflux.common.me.inventory.FEGenericStackInvStorage;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.logic.AllowedOutputFilter;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real AppFlux capabilities and tickers; the dropper is a bidirectional FE machine. */
@GameTestHolder("ae2lt_provider_energy")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "ae2lt", bus = EventBusSubscriber.Bus.FORGE)
public final class ProviderEnergyOutputGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    // Existing energy-routing fixtures attach different capabilities to barrels.
    // Use droppers here so both fixture families can run in the global profile.
    private static final Map<DropperBlockEntity, Machine> MACHINES = new WeakHashMap<>();

    @SubscribeEvent
    public static void capabilities(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof DropperBlockEntity dropper)) return;
        var machine = machine(dropper);
        var energy = LazyOptional.of(() -> machine.energy);
        var fluid = LazyOptional.of(() -> machine.fluid);
        event.addCapability(new ResourceLocation("ae2lt", "test_provider_energy"), new ICapabilityProvider() {
            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
                if (capability == ForgeCapabilities.ENERGY) return energy.cast();
                if (capability == ForgeCapabilities.FLUID_HANDLER) return fluid.cast();
                return LazyOptional.empty();
            }
        });
        event.addListener(energy::invalidate);
        event.addListener(fluid::invalidate);
    }

    @GameTest(batch = "ae2lt_provider_energy", templateNamespace = "ae2lt_provider_energy", template = "empty", timeoutTicks = 180)
    public static void adjacentAutoReturnLeavesFeAndReturnsItemsAndFluids(GameTestHelper helper) {
        checkAutoReturn(helper, false);
    }

    @GameTest(batch = "ae2lt_provider_energy", templateNamespace = "ae2lt_provider_energy", template = "empty", timeoutTicks = 180)
    public static void wirelessAutoReturnLeavesFeAndReturnsItemsAndFluids(GameTestHelper helper) {
        checkAutoReturn(helper, true);
    }

    private static void checkAutoReturn(GameTestHelper helper, boolean wireless) {
        var fixture = fixture(helper, wireless, false);
        helper.runAfterDelay(40, () -> {
            ready(helper, fixture);
            fixture.machine.energy.setStored(25_000);
            fixture.machine.fluid.fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE);
            fixture.dropper.setItem(0, new ItemStack(Items.STONE, 32));
            fixture.provider.setReturnMode(ReturnMode.AUTO);
        });
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(fixture.machine.energy.getEnergyStored() == 25_000
                    && fixture.machine.energy.extractions == 0, "AUTO extracted the machine's FE");
            helper.assertTrue(stored(fixture.provider, AppFluxBridge.FE_KEY) == 0, "FE entered the network");
            helper.assertTrue(fixture.dropper.isEmpty() && stored(fixture.provider, AEItemKey.of(Items.STONE)) == 32,
                    "item return changed");
            helper.assertTrue(fixture.machine.fluid.isEmpty()
                    && stored(fixture.provider, AEFluidKey.of(Fluids.WATER)) == 1_000, "fluid return changed");
            helper.succeed();
        });
    }

    @GameTest(batch = "ae2lt_provider_energy", templateNamespace = "ae2lt_provider_energy", template = "empty", timeoutTicks = 180)
    public static void adjacentInductionSuppliesWithoutRecyclingAndStopsWhenRemoved(GameTestHelper helper) {
        checkInduction(helper, false);
    }

    @GameTest(batch = "ae2lt_provider_energy", templateNamespace = "ae2lt_provider_energy", template = "empty", timeoutTicks = 180)
    public static void wirelessInductionSuppliesWithoutRecyclingAndStopsWhenRemoved(GameTestHelper helper) {
        checkInduction(helper, true);
    }

    private static void checkInduction(GameTestHelper helper, boolean wireless) {
        var fixture = fixture(helper, wireless, false);
        helper.runAfterDelay(40, () -> {
            ready(helper, fixture);
            fixture.machine.energy.setStored(25_000);
            helper.assertTrue(fixture.provider.getMainNode().getGrid().getStorageService().getInventory()
                    .insert(AppFluxBridge.FE_KEY, 100_000, Actionable.MODULATE, IActionSource.empty()) == 100_000,
                    "flux cell rejected the source energy");
            fixture.provider.setReturnMode(ReturnMode.AUTO);
            induction(fixture.provider, true);
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(fixture.machine.energy.getEnergyStored() == 100_000, "induction did not power machine");
            helper.assertTrue(fixture.machine.energy.extractions == 0, "supplied FE was pulled back");
            helper.assertTrue(stored(fixture.provider, AppFluxBridge.FE_KEY) == 25_000,
                    "FE transfer must conserve network plus machine energy");
            induction(fixture.provider, false);
            fixture.machine.energy.setStored(0);
        });
        helper.runAfterDelay(150, () -> {
            helper.assertTrue(fixture.machine.energy.getEnergyStored() == 0, "power continued after removing card");
            helper.assertTrue(stored(fixture.provider, AppFluxBridge.FE_KEY) == 25_000, "network FE changed without card");
            helper.succeed();
        });
    }

    @GameTest(batch = "ae2lt_provider_energy", templateNamespace = "ae2lt_provider_energy", template = "empty", timeoutTicks = 100)
    public static void passiveAndEjectInputsRejectFeInEveryMode(GameTestHelper helper) {
        checkPassive(helper, false);
    }

    @GameTest(batch = "ae2lt_provider_energy", templateNamespace = "ae2lt_provider_energy", template = "empty", timeoutTicks = 100)
    public static void extendedPassiveAndEjectInputsRejectFeInEveryMode(GameTestHelper helper) {
        checkPassive(helper, true);
    }

    private static void checkPassive(GameTestHelper helper, boolean extended) {
        var fixture = fixture(helper, true, extended);
        helper.runAfterDelay(40, () -> {
            ready(helper, fixture);
            var provider = fixture.provider;
            // Even explicitly listing FE as a pattern result must not authorize reception.
            provider.getExposedPatternInventory().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                    new GenericStack[] {new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)},
                    new GenericStack[] {new GenericStack(AppFluxBridge.FE_KEY, 1_000)}));
            var strict = new AllowedOutputFilter();
            strict.allowStrict(AppFluxBridge.FE_KEY);
            strict.allowIdOnly(AppFluxBridge.FE_KEY);
            helper.assertTrue(strict.isEmpty() && !strict.matches(AppFluxBridge.FE_KEY),
                    "FE-only output filter must stay empty");
            for (boolean card : new boolean[] { false, true }) {
                induction(provider, card);
                for (boolean filtered : new boolean[] { false, true }) {
                    provider.setFilteredImport(filtered);
                    for (var mode : ReturnMode.values()) {
                        provider.setReturnMode(mode);
                        assertRejects(helper, provider.getBlockPos(), Direction.SOUTH);
                        if (mode == ReturnMode.EJECT) {
                            assertRejects(helper, fixture.dropper.getBlockPos().north(), Direction.SOUTH);
                        }
                    }
                }
            }
            helper.succeed();
        });
    }

    private static void assertRejects(GameTestHelper helper, BlockPos pos, Direction face) {
        var input = helper.getLevel().getBlockEntity(pos).getCapability(Capabilities.GENERIC_INTERNAL_INV, face).orElse(null);
        helper.assertTrue(input != null, "passive return capability is missing at " + pos);
        helper.assertTrue(!input.isAllowed(AppFluxBridge.FE_KEY), "passive input advertises FE acceptance");
        for (var action : Actionable.values()) {
            helper.assertTrue(input.insert(0, AppFluxBridge.FE_KEY, 1_000, action) == 0, "generic input accepted FE");
        }
        var adapter = new FEGenericStackInvStorage(input);
        helper.assertTrue(adapter.receiveEnergy(1_000, true) == 0 && adapter.receiveEnergy(1_000, false) == 0,
                "Applied Flux FE adapter accepted energy");
        var energy = helper.getLevel().getBlockEntity(pos).getCapability(ForgeCapabilities.ENERGY, face).orElse(null);
        helper.assertTrue(energy == null || (energy.receiveEnergy(1_000, true) == 0
                && energy.receiveEnergy(1_000, false) == 0), "block energy capability accepted FE");
    }

    @GameTest(batch = "ae2lt_provider_energy", templateNamespace = "ae2lt_provider_energy", template = "empty", timeoutTicks = 160)
    public static void existingFeReturnBufferStillDrainsIntoNetwork(GameTestHelper helper) {
        var fixture = fixture(helper, false, false);
        helper.runAfterDelay(40, () -> {
            ready(helper, fixture);
            // Represents a saved pre-fix return buffer: do not delete already-owned FE.
            var returns = fixture.provider.getLogic().getReturnInv();
            returns.setStack(0, new GenericStack(AppFluxBridge.FE_KEY, 7_000));
            var saved = returns.writeToTag();
            returns.clear();
            returns.readFromTag(saved);
            helper.assertTrue(returns.getAmount(0) == 7_000
                    && AppFluxBridge.FE_KEY.equals(returns.getKey(0)), "saved FE did not survive restoration");
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(stored(fixture.provider, AppFluxBridge.FE_KEY) == 7_000,
                    "existing FE was stranded or discarded");
            helper.succeed();
        });
    }

    private static Fixture fixture(GameTestHelper helper, boolean wireless, boolean extended) {
        helper.setBlock(POS.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(POS, (extended ? ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER
                : ModBlocks.OVERLOADED_PATTERN_PROVIDER).get().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.SOUTH));
        helper.setBlock(POS.west(), AEBlocks.DRIVE.block());
        var drive = (DriveBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS.west()));
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        drive.getInternalInventory().setItemDirect(1, AEItems.FLUID_CELL_1K.stack());
        var cell = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("appflux:fe_1k_cell")));
        helper.assertTrue(!cell.isEmpty(), "Applied Flux dependency missing");
        drive.getInternalInventory().setItemDirect(2, cell);
        var provider = (OverloadedPatternProviderBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
        var machinePos = wireless ? POS.offset(4, 0, 0) : POS.south();
        helper.setBlock(machinePos, Blocks.DROPPER);
        var dropper = (DropperBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(machinePos));
        if (wireless) {
            provider.setProviderMode(ProviderMode.WIRELESS);
            helper.assertTrue(provider.addOrUpdateConnection(helper.getLevel().dimension(), dropper.getBlockPos(),
                    Direction.NORTH), "wireless target must bind");
        }
        return new Fixture(provider, dropper, machine(dropper));
    }

    private static void ready(GameTestHelper helper, Fixture fixture) {
        helper.assertTrue(fixture.provider.getMainNode().isActive(), "provider must be active");
    }

    private static void induction(OverloadedPatternProviderBlockEntity provider, boolean installed) {
        var upgrades = ((IUpgradeableObject) provider.getLogic()).getUpgrades();
        upgrades.setItemDirect(0, installed ? new ItemStack(AppFluxBridge.getInductionCard()) : ItemStack.EMPTY);
    }

    private static long stored(OverloadedPatternProviderBlockEntity provider, AEKey key) {
        return provider.getMainNode().getGrid().getStorageService().getInventory()
                .extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    private static Machine machine(DropperBlockEntity dropper) {
        return MACHINES.computeIfAbsent(dropper, ignored -> new Machine());
    }

    private static final class Machine {
        final TrackedEnergy energy = new TrackedEnergy();
        final FluidTank fluid = new FluidTank(4_000);
    }

    private static final class TrackedEnergy extends EnergyStorage {
        int extractions;

        TrackedEnergy() { super(100_000); }

        void setStored(int amount) { energy = amount; }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            extractions++;
            return super.extractEnergy(amount, simulate);
        }
    }

    private record Fixture(OverloadedPatternProviderBlockEntity provider, DropperBlockEntity dropper, Machine machine) {}
}
