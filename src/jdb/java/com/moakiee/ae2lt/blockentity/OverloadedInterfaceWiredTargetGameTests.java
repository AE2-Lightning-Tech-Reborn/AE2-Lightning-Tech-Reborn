package com.moakiee.ae2lt.blockentity;

import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.api.orientation.BlockOrientation;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real grids, inventory capabilities and the actual Applied Flux distributor. */
@GameTestHolder("ae2lt_interface_input")
@PrefixGameTestTemplate(false)
public final class OverloadedInterfaceWiredTargetGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final AEItemKey STONE = AEItemKey.of(Items.STONE);

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void normalAutoKeepsLoadedCellUntilExplicitlyDirected(GameTestHelper helper) {
        checkDrive(helper, false);
    }

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void fastAutoKeepsLoadedCellUntilExplicitlyDirected(GameTestHelper helper) {
        checkDrive(helper, true);
    }

    private static void checkDrive(GameTestHelper helper, boolean fast) {
        var owner = fixture(helper);
        var drive = drive(helper);
        var original = drive.getInternalInventory().getStackInSlot(0).copy();
        owner.setIOSpeedMode(fast ? OverloadedInterfaceBlockEntity.IOSpeedMode.FAST
                : OverloadedInterfaceBlockEntity.IOSpeedMode.NORMAL);
        helper.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() ->
                owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.AUTO))
                .thenExecuteAfter(40, () -> {
                    check(stored(owner, STONE) == 64 && owner.benchmarkBufferedImportAmount() == 0,
                            "automatic input removed the sole loaded drive cell");
                    check(!owner.allowsNormalInteraction(Direction.WEST), "drive not protected");
                    pointWithWrench(helper, owner, Direction.WEST);
                }).thenExecuteAfter(20, () -> {
                    check(drive.getInternalInventory().getStackInSlot(0).isEmpty()
                            && owner.benchmarkBufferedImportAmount() == 1,
                            "explicit direction did not allow intentional cell extraction");
                    var saved = owner.saveWithoutMetadata(helper.getLevel().registryAccess());
                    var restored = new OverloadedInterfaceBlockEntity(owner.getBlockPos(), owner.getBlockState());
                    restored.loadTag(saved, helper.getLevel().registryAccess());
                    check(restored.getTargetDirection() == Direction.WEST, "blockstate direction did not survive reload");
                    owner.clearImportBuffer();
                    pointWithWrench(helper, owner, null);
                    drive.getInternalInventory().setItemDirect(0, original);
                }).thenWaitUntil(() -> ready(owner)).thenExecuteAfter(40, () -> {
                    check(stored(owner, STONE) == 64 && owner.benchmarkBufferedImportAmount() == 0,
                            "returning to automatic input did not restore protection");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 280)
    public static void driveFrontAndLiveGridSplitUseCurrentNetwork(GameTestHelper helper) {
        var owner = fixture(helper);
        var drive = drive(helper);
        BlockOrientation.EAST_UP.setOn(drive);
        // Front inventory faces the interface; ME connection comes from the south.
        helper.setBlock(POS.west(2), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(POS.west().south(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(POS.south(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() -> {
            check(drive.getGridNode(Direction.EAST) == null, "fixture did not expose drive front");
            check(!owner.allowsNormalInteraction(Direction.WEST), "front bypassed same-grid protection");
            owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.AUTO);
        }).thenExecuteAfter(30, () -> {
            check(stored(owner, STONE) == 64 && owner.benchmarkBufferedImportAmount() == 0,
                    "drive front lost loaded cell");
            owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.OFF);
            helper.setBlock(POS.south(), Blocks.AIR);
        }).thenWaitUntil(() -> {
            check(owner.getMainNode().isActive() && drive.getMainNode().isActive(), "waiting for split grids");
            check(owner.getMainNode().getGrid() != drive.getMainNode().getGrid(), "grid did not split");
            check(owner.allowsNormalInteraction(Direction.WEST), "different-grid target stayed blocked");
        }).thenExecute(() -> helper.setBlock(POS.south(), AEBlocks.CREATIVE_ENERGY_CELL.block()))
                .thenWaitUntil(() -> {
                    ready(owner);
                    check(!owner.allowsNormalInteraction(Direction.WEST), "rejoined target stayed allowed");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void providerAndInterfaceAreSkippedAndWrenchMatchesAe2(GameTestHelper helper) {
        var owner = fixture(helper);
        helper.setBlock(POS.north(), AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(POS.south(), AEBlocks.INTERFACE.block());
        helper.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() -> {
            for (var side : List.of(Direction.WEST, Direction.NORTH, Direction.SOUTH)) {
                check(!owner.allowsNormalInteraction(side), "missing storage/provider/interface exclusion");
            }
            var nativePos = helper.absolutePos(POS.north());
            for (var starting : PushDirection.values()) {
                for (var clicked : Direction.values()) {
                    helper.getLevel().setBlockAndUpdate(owner.getBlockPos(), owner.getBlockState()
                            .setValue(PatternProviderBlock.PUSH_DIRECTION, starting));
                    helper.getLevel().setBlockAndUpdate(nativePos, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                            .setValue(PatternProviderBlock.PUSH_DIRECTION, starting));
                    wrench(helper, owner.getBlockPos(), clicked);
                    wrench(helper, nativePos, clicked);
                    var expected = helper.getLevel().getBlockState(nativePos).getValue(PatternProviderBlock.PUSH_DIRECTION);
                    check(owner.getBlockState().getValue(PatternProviderBlock.PUSH_DIRECTION) == expected,
                            "wrench differs from native AE2: " + starting + " / " + clicked);
                    var selected = expected.getDirection();
                    if (selected != null) {
                        for (var candidate : Direction.values()) {
                            check(owner.allowsNormalInteraction(candidate) == (candidate == selected),
                                    "wrench-selected face failed to bypass protection or leaked another face");
                        }
                    }
                }
            }
            pointWithWrench(helper, owner, null);
            check(owner.getTargetDirection() == null && !owner.allowsNormalInteraction(Direction.WEST),
                    "wrench failed to return to protected automatic scan");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void ordinaryContainerAndSameGridMachineStillTransfer(GameTestHelper helper) {
        var owner = fixture(helper);
        var factory = factory(helper, POS.north(), false);
        helper.setBlock(POS.south(), Blocks.BARREL);
        var barrel = (BarrelBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS.south()));
        barrel.setItem(0, new ItemStack(Items.STONE, 32));
        factory.getInventory().insertRecipeOutputs(List.of(new ItemStack(Items.STONE, 32)));
        prepareFluxCell(helper);
        helper.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() -> {
            check(factory.getMainNode().getGrid() == owner.getMainNode().getGrid(), "machine is not on same grid");
            owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.AUTO);
            enableEnergy(owner);
        }).thenExecuteAfter(40, () -> {
            check(barrel.isEmpty() && factory.getInventory().getStackInSlot(
                    OverloadProcessingFactoryInventory.SLOT_OUTPUT_0).isEmpty(), "normal targets did not drain");
            check(stored(owner, STONE) == 128, "automatic input did not conserve 128 stone");
            check(factory.getEnergyStorage().getStoredEnergyLong() > 0, "same-grid processing machine received no FE");
            owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.OFF);
            helper.setBlock(POS.south(), Blocks.AIR);
            owner.getInterfaceLogic().getConfig().setStack(0, new GenericStack(STONE, 32));
            owner.setExportMode(OverloadedInterfaceBlockEntity.ExportMode.AUTO);
        }).thenExecuteAfter(40, () -> {
            check(factory.getInventory().getStackInSlot(0).getCount() > 0, "same-grid processing input rejected export");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void serviceRegisteredProviderIsProtectedForItemsAndEnergy(GameTestHelper helper) {
        var owner = fixture(helper);
        // The block is a processing machine, but its node offers storage in this
        // fixture. This checks third-party providers without a drive class/ID list.
        var provider = factory(helper, POS.north(), true);
        var other = factory(helper, POS.south(), false);
        provider.getInventory().insertRecipeOutputs(List.of(new ItemStack(Items.STONE, 32)));
        prepareFluxCell(helper);
        helper.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() -> {
            owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.AUTO);
            enableEnergy(owner);
        }).thenExecuteAfter(40, () -> {
            check(!owner.allowsNormalInteraction(Direction.NORTH), "generic storage service was ignored");
            check(provider.getInventory().getStackInSlot(10).getCount() == 32
                    && provider.getEnergyStorage().getStoredEnergyLong() == 0, "automatic path touched provider");
            pointWithWrench(helper, owner, Direction.NORTH);
            other.getEnergyStorage().loadStoredEnergy(0);
            refillEnergy(owner);
        }).thenExecuteAfter(30, () -> {
            check(provider.getInventory().getStackInSlot(10).isEmpty() && stored(owner, STONE) == 96,
                    "explicit direction did not import provider output");
            check(provider.getEnergyStorage().getStoredEnergyLong() > 0, "explicit provider received no FE");
            check(other.getEnergyStorage().getStoredEnergyLong() == 0, "explicit mode leaked adjacent FE");
            owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.OFF);
            owner.getInterfaceLogic().getConfig().setStack(0, new GenericStack(STONE, 32));
            owner.setExportMode(OverloadedInterfaceBlockEntity.ExportMode.AUTO);
            pointWithWrench(helper, owner, null);
            provider.getEnergyStorage().loadStoredEnergy(0);
            refillEnergy(owner);
        }).thenExecuteAfter(30, () -> {
            check(provider.getInventory().getStackInSlot(0).isEmpty()
                    && provider.getEnergyStorage().getStoredEnergyLong() == 0,
                    "automatic export/FE protection did not resume");
            // Refill available stock consumed by the ordinary adjacent machine.
            owner.getMainNode().getGrid().getStorageService().getInventory()
                    .insert(STONE, 64, Actionable.MODULATE, IActionSource.empty());
            pointWithWrench(helper, owner, Direction.NORTH);
        }).thenExecuteAfter(30, () -> {
            check(provider.getInventory().getStackInSlot(0).getCount() > 0,
                    "explicit provider did not receive exported items");
            owner.setInterfaceMode(OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS);
            provider.getEnergyStorage().loadStoredEnergy(0);
            other.getEnergyStorage().loadStoredEnergy(0);
            refillEnergy(owner);
        }).thenExecuteAfter(30, () -> {
            check(provider.getEnergyStorage().getStoredEnergyLong() == 0
                    && other.getEnergyStorage().getStoredEnergyLong() == 0,
                    "wireless mode energized unbound neighbors");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void fluidTransfersUseTheSameAutomaticAndExplicitRules(GameTestHelper helper) {
        var owner = fixture(helper);
        var provider = factory(helper, POS.north(), true);
        var machine = factory(helper, POS.south(), false);
        var water = AEFluidKey.of(Fluids.WATER);
        drive(helper).getInternalInventory().setItemDirect(1, AEItems.FLUID_CELL_1K.stack());
        setOutputFluid(provider);
        setOutputFluid(machine);
        helper.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() ->
                owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.AUTO))
                .thenExecuteAfter(30, () -> {
                    check(machine.getOutputFluid().isEmpty() && provider.getOutputFluid().getAmount() == 1000
                            && stored(owner, water) == 1000, "automatic fluid input ignored target roles");
                    pointWithWrench(helper, owner, Direction.NORTH);
                }).thenExecuteAfter(30, () -> {
                    check(provider.getOutputFluid().isEmpty() && stored(owner, water) == 2000,
                            "explicit fluid input did not bypass provider protection");
                    owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.OFF);
                    pointWithWrench(helper, owner, null);
                    owner.getInterfaceLogic().getConfig().setStack(0, new GenericStack(water, 1000));
                    owner.setExportMode(OverloadedInterfaceBlockEntity.ExportMode.AUTO);
                }).thenExecuteAfter(30, () -> {
                    check(provider.getInputFluid().isEmpty() && machine.getInputFluid().getAmount() > 0,
                            "automatic fluid output ignored target roles");
                    owner.getMainNode().getGrid().getStorageService().getInventory()
                            .insert(water, 1000, Actionable.MODULATE, IActionSource.empty());
                    pointWithWrench(helper, owner, Direction.NORTH);
                }).thenExecuteAfter(30, () -> {
                    check(provider.getInputFluid().getAmount() > 0, "explicit fluid output was rejected");
                }).thenSucceed();
    }

    private static void setOutputFluid(OverloadProcessingFactoryBlockEntity factory) {
        try {
            var field = OverloadProcessingFactoryBlockEntity.class.getDeclaredField("outputTank");
            field.setAccessible(true);
            ((FluidTank) field.get(factory)).setFluid(new FluidStack(Fluids.WATER, 1000));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void wirelessBoundDriveRetainsIntentionalExtraction(GameTestHelper helper) {
        var owner = fixture(helper);
        helper.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() -> {
            owner.setInterfaceMode(OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS);
            check(owner.addOrUpdateConnection(new OverloadedInterfaceBlockEntity.WirelessConnection(
                    helper.getLevel().dimension(), drive(helper).getBlockPos(), Direction.EAST)), "failed to bind drive");
            owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.AUTO);
        }).thenExecuteAfter(40, () -> {
            check(drive(helper).getInternalInventory().getStackInSlot(0).isEmpty()
                    && owner.benchmarkBufferedImportAmount() == 1, "wired policy changed explicit wireless binding");
        }).thenSucceed();
    }

    private static OverloadedInterfaceBlockEntity fixture(GameTestHelper helper) {
        helper.setBlock(POS.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(POS, ModBlocks.OVERLOADED_INTERFACE.get());
        helper.setBlock(POS.west(), AEBlocks.DRIVE.block());
        var cell = AEItems.ITEM_CELL_1K.stack();
        var contents = StorageCells.getCellInventory(cell, null);
        check(contents != null && contents.insert(STONE, 64, Actionable.MODULATE, IActionSource.empty()) == 64,
                "fixture cell rejected stone");
        contents.persist();
        drive(helper).getInternalInventory().setItemDirect(0, cell);
        var owner = (OverloadedInterfaceBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
        owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.OFF);
        owner.setExportMode(OverloadedInterfaceBlockEntity.ExportMode.OFF);
        return owner;
    }

    private static OverloadProcessingFactoryBlockEntity factory(GameTestHelper helper, BlockPos pos, boolean provider) {
        helper.setBlock(pos, ModBlocks.OVERLOAD_PROCESSING_FACTORY.get());
        var factory = (OverloadProcessingFactoryBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        // Isolate adjacent FE delivery from the machine's own network recharge.
        // Keep the real grid node and item/fluid/energy capabilities intact.
        factory.getMainNode().addService(IGridTickable.class, new IGridTickable() {
            @Override
            public TickingRequest getTickingRequest(IGridNode node) {
                return new TickingRequest(1, 20, true);
            }

            @Override
            public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
                return TickRateModulation.SLEEP;
            }
        });
        if (provider) factory.getMainNode().addService(IStorageProvider.class, mounts -> {});
        return factory;
    }

    private static void prepareFluxCell(GameTestHelper helper) {
        var cell = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("appflux:fe_1k_cell")));
        check(!cell.isEmpty(), "Applied Flux test dependency missing");
        drive(helper).getInternalInventory().setItemDirect(1, cell);
    }

    private static void enableEnergy(OverloadedInterfaceBlockEntity owner) {
        refillEnergy(owner);
        var card = AppFluxBridge.getInductionCard();
        check(card != null, "induction card missing");
        owner.getInterfaceLogic().getUpgrades().setItemDirect(0, new ItemStack(card));
        owner.invalidateInductionCardCache();
    }

    private static void refillEnergy(OverloadedInterfaceBlockEntity owner) {
        check(owner.getMainNode().getGrid().getStorageService().getInventory().insert(AppFluxBridge.FE_KEY,
                100_000, Actionable.MODULATE, IActionSource.empty()) == 100_000, "FE cell did not accept test energy");
    }

    private static DriveBlockEntity drive(GameTestHelper helper) {
        return (DriveBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS.west()));
    }

    private static void ready(OverloadedInterfaceBlockEntity owner) {
        check(owner.getMainNode().isActive() && stored(owner, STONE) == 64, "waiting for loaded cell mount");
    }

    private static long stored(OverloadedInterfaceBlockEntity owner, AEKey key) {
        return owner.getMainNode().getGrid().getStorageService().getInventory()
                .extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    private static void pointWithWrench(GameTestHelper helper, OverloadedInterfaceBlockEntity owner,
                                        Direction direction) {
        if (owner.getTargetDirection() != null) wrench(helper, owner.getBlockPos(), owner.getTargetDirection());
        if (direction != null) wrench(helper, owner.getBlockPos(), direction.getOpposite());
        check(owner.getTargetDirection() == direction, "wrench did not select requested direction");
    }

    private static void wrench(GameTestHelper helper, BlockPos pos, Direction face) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var wrench = AEItems.CERTUS_QUARTZ_WRENCH.stack();
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        helper.getLevel().getBlockState(pos).useItemOn(wrench, helper.getLevel(), player,
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
