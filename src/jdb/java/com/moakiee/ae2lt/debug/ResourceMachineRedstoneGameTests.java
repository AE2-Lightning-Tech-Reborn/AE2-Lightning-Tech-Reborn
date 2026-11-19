package com.moakiee.ae2lt.debug;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.RelativeSide;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageCells;
import appeng.api.util.AEColor;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.menu.SlotSemantics;
import appeng.parts.automation.StorageLevelEmitterPart;
import appeng.util.SettingsFrom;
import com.mojang.authlib.GameProfile;
import com.moakiee.ae2lt.block.TeslaCoilBlock;
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.blockentity.TeslaCoilBlockEntity;
import com.moakiee.ae2lt.machine.common.RedstoneControlledMachine;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilInventory;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilMode;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;
import com.moakiee.ae2lt.menu.RedstoneMachineMenu;
import com.moakiee.ae2lt.menu.TeslaCoilMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_redstone")
@PrefixGameTestTemplate(false)
public final class ResourceMachineRedstoneGameTests {
    private static final BlockPos POS = new BlockPos(2, 3, 2);
    private static final int OUTPUT = CrystalCatalyzerInventory.SLOT_OUTPUT;

    private static void check(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    private static AENetworkedBlockEntity machine(GameTestHelper h, boolean tesla) {
        h.setBlock(POS, tesla ? ModBlocks.TESLA_COIL.get() : ModBlocks.CRYSTAL_CATALYZER.get());
        if (tesla) h.setBlock(POS.above(), ModBlocks.TESLA_COIL.get().defaultBlockState()
                .setValue(TeslaCoilBlock.HALF, DoubleBlockHalf.UPPER));
        return h.getBlockEntity(POS);
    }

    private static AENetworkedBlockEntity network(GameTestHelper h, boolean tesla, long hv) {
        var machine = machine(h, tesla);
        h.setBlock(POS.below(), AEBlocks.CONTROLLER.block());
        h.setBlock(POS.below(2), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(POS.below().east(), AEBlocks.DRIVE.block());
        var cell = new ItemStack(ModItems.LIGHTNING_STORAGE_COMPONENT_I.get());
        var storage = StorageCells.getCellInventory(cell, null);
        check(storage != null, "missing lightning cell");
        check(storage.insert(LightningKey.HIGH_VOLTAGE, hv, Actionable.MODULATE,
                IActionSource.ofMachine(machine)) == hv, "could not seed HV");
        storage.persist();
        DriveBlockEntity drive = h.getBlockEntity(POS.below().east());
        drive.getInternalInventory().setItemDirect(0, cell);
        return machine;
    }

    private static void install(RedstoneControlledMachine host, RedstoneMode mode) {
        check(host.getUpgrades().addItems(AEItems.REDSTONE_CARD.stack()).isEmpty(), "card rejected");
        host.getRedstoneControl().setMode(mode);
    }

    @GameTest(templateNamespace = "ae2lt_redstone", template = "empty")
    public static void catalyzerUpgradeMenuAndSave(GameTestHelper h) { menuAndSave(h, false); }

    @GameTest(templateNamespace = "ae2lt_redstone", template = "empty")
    public static void teslaUpgradeMenuAndSave(GameTestHelper h) { menuAndSave(h, true); }

    private static void menuAndSave(GameTestHelper h, boolean tesla) {
        var host = machine(h, tesla);
        var controlled = (RedstoneControlledMachine) host;
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "RedstoneQA"));
        player.getInventory().clearContent();
        player.setPos(host.getBlockPos().getX() + 0.5, host.getBlockPos().getY(), host.getBlockPos().getZ() + 0.5);
        RedstoneMachineMenu<?> menu = tesla
                ? new TeslaCoilMenu(1, player.getInventory(), (TeslaCoilBlockEntity) host)
                : new CrystalCatalyzerMenu(1, player.getInventory(), (CrystalCatalyzerBlockEntity) host);
        player.containerMenu = menu;
        var upgrades = menu.getSlots(SlotSemantics.UPGRADE);
        check(upgrades.size() == 1 && !upgrades.getFirst().mayPlace(AEItems.SPEED_CARD.stack()),
                "expected exactly one redstone-only slot");
        player.getInventory().setItem(0, AEItems.REDSTONE_CARD.stack(2));
        var source = menu.getSlots(SlotSemantics.PLAYER_HOTBAR).getFirst();
        menu.clicked(source.index, 0, ClickType.QUICK_MOVE, player);
        check(controlled.getRedstoneControl().hasCard() && player.getInventory().getItem(0).getCount() == 1,
                "shift-click did not install exactly one card");
        controlled.getRedstoneControl().setMode(RedstoneMode.HIGH_SIGNAL);
        menu.broadcastChanges();
        check(menu.redstoneCardInstalled && menu.redstonePaused && menu.redstoneMode == RedstoneMode.HIGH_SIGNAL,
                "menu redstone state was not synchronized");
        controlled.getRedstoneControl().setMode(RedstoneMode.SIGNAL_PULSE);
        check(controlled.getRedstoneControl().getMode() == RedstoneMode.HIGH_SIGNAL, "pulse mode accepted");
        var tag = new CompoundTag();
        host.saveAdditional(tag, h.getLevel().registryAccess());
        var copy = tesla ? new TeslaCoilBlockEntity(host.getBlockPos(), host.getBlockState())
                : new CrystalCatalyzerBlockEntity(host.getBlockPos(), host.getBlockState());
        copy.loadTag(tag, h.getLevel().registryAccess());
        var restored = (RedstoneControlledMachine) copy;
        check(restored.getRedstoneControl().hasCard()
                && restored.getRedstoneControl().getMode() == RedstoneMode.HIGH_SIGNAL, "save lost card/mode");
        restored.getRedstoneControl().setMode(RedstoneMode.LOW_SIGNAL);
        copy.importSettings(SettingsFrom.MEMORY_CARD, host.exportSettings(SettingsFrom.MEMORY_CARD, null), null);
        check(restored.getRedstoneControl().getMode() == RedstoneMode.HIGH_SIGNAL, "memory card lost mode");
        var drops = new ArrayList<ItemStack>();
        host.addAdditionalDrops(h.getLevel(), host.getBlockPos(), drops);
        check(drops.stream().filter(s -> s.is(AEItems.REDSTONE_CARD.asItem())).mapToInt(ItemStack::getCount).sum() == 1,
                "breaking machine lost or duplicated the upgrade");
        menu.clicked(upgrades.getFirst().index, 0, ClickType.QUICK_MOVE, player);
        check(!controlled.getRedstoneControl().hasCard() && controlled.getRedstoneControl().isEnabled()
                && player.getInventory().getItem(0).getCount() == 2, "card removal failed to restore normal operation");
        copy.loadTag(new CompoundTag(), h.getLevel().registryAccess());
        check(!restored.getRedstoneControl().hasCard()
                && restored.getRedstoneControl().getMode() == RedstoneMode.IGNORE, "legacy save retained stale controls");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_redstone", template = "empty", timeoutTicks = 220)
    public static void crystalPauseResumeAndExport(GameTestHelper h) { catalyzer(h, false); }

    @GameTest(templateNamespace = "ae2lt_redstone", template = "empty", timeoutTicks = 220)
    public static void dustPauseResumeAndExport(GameTestHelper h) { catalyzer(h, true); }

    private static void catalyzer(GameTestHelper h, boolean dust) {
        var host = (CrystalCatalyzerBlockEntity) network(h, false, 10);
        if (dust) host.cycleMode();
        install(host, RedstoneMode.HIGH_SIGNAL);
        host.getInventory().setItemDirect(CrystalCatalyzerInventory.SLOT_CATALYST, AEBlocks.QUARTZ_BLOCK.stack());
        host.getTank().setFluid(new FluidStack(Fluids.WATER, 1000));
        host.getEnergyStorage().receiveEnergy(1_000_000, false);
        h.runAfterDelay(30, () -> {
            check(!host.hasLockedRecipe() && host.getEnergyStorage().getEnergyStored() == 1_000_000,
                    "disabled catalyzer started/consumed FE");
            h.setBlock(POS.west(), Blocks.REDSTONE_BLOCK);
            host.getLogic().tickingRequest(host.getMainNode().getNode(), 1);
            check(host.hasLockedRecipe() && host.getConsumedEnergy() > 0, "powered catalyzer failed to start");
            h.setBlock(POS.west(), Blocks.AIR);
            var tag = new CompoundTag();
            host.saveAdditional(tag, h.getLevel().registryAccess());
            host.clearContent();
            host.loadTag(tag, h.getLevel().registryAccess());
        });
        h.runAfterDelay(31, () -> {
            long paid = host.getConsumedEnergy();
            long stored = host.getEnergyStorage().getStoredEnergyLong();
            h.runAfterDelay(30, () -> {
                check(paid > 0 && host.getConsumedEnergy() == paid
                        && host.getEnergyStorage().getStoredEnergyLong() == stored
                        && host.getFluid().getAmount() == 1000 && host.getAvailableHighVoltage() == 10
                        && host.getInventory().getStackInSlot(OUTPUT).isEmpty(), "pause lost progress or spent resources");
                // Existing output must still leave while disabled.
                h.setBlock(POS.east(), Blocks.CHEST);
                host.getInventory().setItemDirect(OUTPUT, AEItems.CERTUS_QUARTZ_CRYSTAL.stack());
                host.updateOutputSides(EnumSet.allOf(RelativeSide.class));
                host.setAutoExportEnabled(true);
                host.getLogic().tickingRequest(host.getMainNode().getNode(), 1);
                ChestBlockEntity chest = h.getBlockEntity(POS.east());
                check(host.getInventory().getStackInSlot(OUTPUT).isEmpty() && !chest.isEmpty(), "paused export blocked");
                host.setAutoExportEnabled(false);
                h.setBlock(POS.west(), Blocks.REDSTONE_BLOCK);
            });
        });
        h.runAfterDelay(170, () -> {
            check(!host.getInventory().getStackInSlot(OUTPUT).isEmpty()
                    && host.getFluid().isEmpty() && host.getAvailableHighVoltage() == 9,
                    "resumed cycle did not finish exactly once");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt_redstone", template = "empty", timeoutTicks = 180)
    public static void teslaHvPauseAndUpperHalfWake(GameTestHelper h) { tesla(h, false); }

    @GameTest(templateNamespace = "ae2lt_redstone", template = "empty", timeoutTicks = 180)
    public static void teslaEhvPauseAndUpperHalfWake(GameTestHelper h) { tesla(h, true); }

    private static void tesla(GameTestHelper h, boolean ehv) {
        long input = ehv ? TeslaCoilMode.EXTREME_HIGH_VOLTAGE.requiredHighVoltage() : 0;
        var host = (TeslaCoilBlockEntity) network(h, true, input);
        if (ehv) {
            host.cycleMode();
            host.getInventory().setItemDirect(TeslaCoilInventory.SLOT_MATRIX, new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()));
        } else {
            host.getInventory().setItemDirect(TeslaCoilInventory.SLOT_DUST,
                    new ItemStack(ModItems.OVERLOAD_CRYSTAL_DUST.get(), TeslaCoilMode.HIGH_VOLTAGE.requiredDust()));
        }
        host.getEnergyStorage().receiveEnergy(TeslaCoilBlockEntity.ENERGY_CAPACITY, false);
        install(host, RedstoneMode.HIGH_SIGNAL);
        long[] paid = {0};
        long[] energy = {0};
        h.runAfterDelay(30, () -> {
            check(!host.hasLockedMode() && host.getConsumedEnergy() == 0, "disabled coil started");
            h.setBlock(POS.above().west(), Blocks.REDSTONE_BLOCK);
            host.getLogic().tickingRequest(host.getMainNode().getNode(), 1);
            check(host.hasLockedMode() && host.getConsumedEnergy() > 0, "upper half signal did not start coil");
            h.setBlock(POS.above().west(), Blocks.AIR);
            paid[0] = host.getConsumedEnergy();
            energy[0] = host.getEnergyStorage().getStoredEnergyLong();
            var tag = new CompoundTag();
            host.saveAdditional(tag, h.getLevel().registryAccess());
            host.clearContent();
            host.loadTag(tag, h.getLevel().registryAccess());
        });
        h.runAfterDelay(70, () -> {
            check(host.getConsumedEnergy() == paid[0] && host.getEnergyStorage().getStoredEnergyLong() == energy[0]
                    && host.getAvailableHighVoltage() == input && host.getAvailableExtremeHighVoltage() == 0,
                    "paused coil advanced/lost progress or consumed lightning");
            h.setBlock(POS.above().west(), Blocks.REDSTONE_BLOCK);
        });
        h.runAfterDelay(120, () -> {
            check(!host.hasLockedMode() && (ehv ? host.getAvailableExtremeHighVoltage() : host.getAvailableHighVoltage()) == 1,
                    "sleeping coil failed to wake/complete");
            check(!ehv || host.getAvailableHighVoltage() == 0, "EHV input debit was incorrect");
            h.succeed();
        });
    }

    private static StorageLevelEmitterPart emitter(GameTestHelper h, long threshold) {
        var cable = AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT);
        PartHelper.setPart(h.getLevel(), h.absolutePos(POS.below().north()), null, null, cable);
        PartHelper.setPart(h.getLevel(), h.absolutePos(POS.north()), null, null, cable);
        var emitter = PartHelper.setPart(h.getLevel(), h.absolutePos(POS.north()), Direction.SOUTH, null,
                AEParts.LEVEL_EMITTER.asItem());
        check(emitter != null, "could not place level emitter");
        emitter.getConfig().setStack(0, new GenericStack(LightningKey.HIGH_VOLTAGE, 1));
        emitter.setReportingValue(threshold);
        emitter.getConfigManager().putSetting(Settings.REDSTONE_EMITTER, RedstoneMode.HIGH_SIGNAL);
        return emitter;
    }

    @GameTest(templateNamespace = "ae2lt_redstone", template = "empty", timeoutTicks = 220)
    public static void levelEmitterStopsTeslaAndRestartsAfterStockFalls(GameTestHelper h) {
        var host = (TeslaCoilBlockEntity) network(h, true, 5);
        var emitter = emitter(h, 3);
        install(host, RedstoneMode.LOW_SIGNAL);
        host.getEnergyStorage().receiveEnergy(TeslaCoilBlockEntity.ENERGY_CAPACITY, false);
        host.getInventory().setItemDirect(TeslaCoilInventory.SLOT_DUST,
                new ItemStack(ModItems.OVERLOAD_CRYSTAL_DUST.get(), TeslaCoilMode.HIGH_VOLTAGE.requiredDust()));
        h.runAfterDelay(50, () -> {
            check(emitter.isProvidingWeakPower() > 0 && !host.hasLockedMode() && host.getAvailableHighVoltage() == 5,
                    "emitter did not stop production above limit");
            host.getMainNode().getGrid().getStorageService().getInventory().extract(LightningKey.HIGH_VOLTAGE, 5,
                    Actionable.MODULATE, IActionSource.ofMachine(host));
        });
        h.runAfterDelay(110, () -> {
            check(host.getAvailableHighVoltage() == 1, "emitter failed to restart coil after stock fell");
            host.getMainNode().getGrid().getStorageService().getInventory().insert(LightningKey.HIGH_VOLTAGE, 5,
                    Actionable.MODULATE, IActionSource.ofMachine(host));
        });
        h.runAfterDelay(140, () -> host.getInventory().setItemDirect(TeslaCoilInventory.SLOT_DUST,
                new ItemStack(ModItems.OVERLOAD_CRYSTAL_DUST.get(), TeslaCoilMode.HIGH_VOLTAGE.requiredDust())));
        h.runAfterDelay(190, () -> {
            check(host.getAvailableHighVoltage() == 6 && host.getConsumedEnergy() == 0,
                    "replenished input bypassed emitter stop");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt_redstone", template = "empty", timeoutTicks = 220)
    public static void levelEmitterProtectsCatalyzerInputReserve(GameTestHelper h) {
        var host = (CrystalCatalyzerBlockEntity) network(h, false, 0);
        var emitter = emitter(h, 3);
        install(host, RedstoneMode.HIGH_SIGNAL);
        host.getInventory().setItemDirect(CrystalCatalyzerInventory.SLOT_CATALYST, AEBlocks.QUARTZ_BLOCK.stack());
        host.getTank().setFluid(new FluidStack(Fluids.WATER, 16000));
        host.getEnergyStorage().receiveEnergy(1_000_000, false);
        h.runAfterDelay(40, () -> {
            check(!host.hasLockedRecipe() && emitter.isProvidingWeakPower() == 0, "low reserve did not block start");
            host.getMainNode().getGrid().getStorageService().getInventory().insert(LightningKey.HIGH_VOLTAGE, 6,
                    Actionable.MODULATE, IActionSource.ofMachine(host));
        });
        h.runAfterDelay(180, () -> {
            long remaining = host.getAvailableHighVoltage();
            check(remaining == 2 && emitter.isProvidingWeakPower() == 0
                    && !host.getRedstoneControl().isEnabled(), "input reserve did not stop consumption: " + remaining);
            int fluid = host.getFluid().getAmount();
            long energy = host.getEnergyStorage().getStoredEnergyLong();
            h.runAfterDelay(25, () -> {
                check(host.getAvailableHighVoltage() == remaining && host.getFluid().getAmount() == fluid
                        && host.getEnergyStorage().getStoredEnergyLong() == energy, "reserve continued draining while paused");
                h.succeed();
            });
        });
    }
}
