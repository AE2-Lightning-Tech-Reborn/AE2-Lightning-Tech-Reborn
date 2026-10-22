package com.moakiee.ae2lt.debug;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;

import mekanism.api.RelativeSide;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.tile.interfaces.ISideConfiguration;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.registry.ModBlocks;

/** Exercises the actual sided Mekanism capabilities through both provider return paths. */
@GameTestHolder("ae2lt_provider_mekanism")
@PrefixGameTestTemplate(false)
public final class ProviderMekanismReturnGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 420)
    public static void adjacentUnfilteredReturnPreservesInfuserAuxiliarySlots(GameTestHelper helper) {
        check(helper, "mekanism:basic_infusing_factory", false, false);
    }

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 420)
    public static void adjacentFilteredReturnPreservesMatchingAuxiliaryItems(GameTestHelper helper) {
        check(helper, "mekanism:basic_infusing_factory", false, true);
    }

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 420)
    public static void wirelessUnfilteredReturnPreservesInfuserAuxiliarySlots(GameTestHelper helper) {
        check(helper, "mekanism:basic_infusing_factory", true, false);
    }

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 420)
    public static void wirelessFilteredReturnPreservesMatchingAuxiliaryItems(GameTestHelper helper) {
        check(helper, "mekanism:basic_infusing_factory", true, true);
    }

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 420)
    public static void adjacentReturnPreservesInfiniteReactionFactoryEnergySlot(GameTestHelper helper) {
        checkReactionFactory(helper, false, false);
    }

    @GameTest(templateNamespace = "ae2lt_io", template = "wireless_io_empty", timeoutTicks = 420)
    public static void wirelessReturnPreservesInfiniteReactionFactoryEnergySlot(GameTestHelper helper) {
        checkReactionFactory(helper, true, true);
    }

    private static void checkReactionFactory(GameTestHelper helper, boolean wireless, boolean filtered) {
        // MoreMachine is optional in the standard dev runtime. Add its jar through
        // ae2ltJdbProbeMods to exercise the exact factory from the reported screen.
        String id = "mekanism_extras:infinite_pressurised_reacting_factory";
        if (!BuiltInRegistries.BLOCK.containsKey(new ResourceLocation(id))) {
            org.slf4j.LoggerFactory.getLogger("ae2lt").info("Skipping optional return regression: {} is absent", id);
            helper.succeed();
            return;
        }
        check(helper, id, wireless, filtered);
    }

    private static void check(GameTestHelper helper, String blockId, boolean wireless, boolean filtered) {
        org.slf4j.LoggerFactory.getLogger("ae2lt").info(
                "Running provider return regression: machine={}, wireless={}, filtered={}", blockId, wireless, filtered);
        helper.setBlock(POS.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(POS, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.SOUTH));
        helper.setBlock(POS.west(), AEBlocks.DRIVE.block());
        var drive = (DriveBlockEntity) helper.getBlockEntity(POS.west());
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        var provider = (OverloadedPatternProviderBlockEntity) helper.getBlockEntity(POS);
        provider.setFilteredImport(filtered);
        provider.getExposedPatternInventory().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.STONE), 1)}));
        provider.setReturnMode(ReturnMode.AUTO);

        var id = new ResourceLocation(blockId);
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "test machine must be installed: " + id);
        var machinePos = wireless ? POS.offset(4, 0, 0) : POS.south();
        helper.setBlock(machinePos, BuiltInRegistries.BLOCK.get(id));
        var machine = (ISideConfiguration) helper.getBlockEntity(machinePos);
        var config = machine.getConfig().getConfig(TransmissionType.ITEM);
        helper.assertTrue(config != null, "machine must have an item config");
        var input = slot(config, DataType.INPUT);
        var output = slot(config, DataType.OUTPUT);
        var energy = slot(config, DataType.ENERGY);
        var protectedType = config.getSlotInfo(DataType.EXTRA) == null ? DataType.ENERGY : DataType.EXTRA;
        var auxiliary = slot(config, protectedType);
        var side = RelativeSide.fromDirections(machine.getDirection(), Direction.NORTH);
        setSide(machine, config, side, protectedType);
        if (wireless) {
            provider.setProviderMode(ProviderMode.WIRELESS);
            helper.assertTrue(provider.addOrUpdateConnection(helper.getLevel().dimension(),
                    helper.absolutePos(machinePos), Direction.NORTH), "wireless machine must bind");
        }

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(provider.getMainNode().isActive(), "provider must be active");
            put(input, 11);
            put(output, 23);
            put(energy, 9);
            put(auxiliary, 17);
            var raw = helper.getBlockEntity(machinePos).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElse(null);
            helper.assertTrue(raw != null && raw.extractItem(0, 64, true).getCount() == 17,
                    "auxiliary must be externally extractable to reproduce the original bug");
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(auxiliary.getCount() == 17 && stored(provider) == 0,
                    "AUTO must leave auxiliary material in its slot, even when it matches a pattern output");
            setSide(machine, config, side, DataType.ENERGY);
        });
        helper.runAfterDelay(160, () -> {
            helper.assertTrue(energy.getCount() == (energy == auxiliary ? 17 : 9) && stored(provider) == 0,
                    "changing side config must not make the energy slot an output");
            setSide(machine, config, side, DataType.INPUT_OUTPUT);
        });
        // An idle sweep backs off to 128 ticks. The side changes at tick 160;
        // allow a complete sweep plus return-buffer/network transfer before asserting.
        helper.runAfterDelay(320, () -> {
            helper.assertTrue(output.isEmpty() && stored(provider) == 23,
                    "real output must still return after switching to combined item I/O");
            helper.assertTrue(input.getCount() == 11 && auxiliary.getCount() == 17
                            && energy.getCount() == (energy == auxiliary ? 17 : 9),
                    "returning products must preserve input, auxiliary and energy contents");
            provider.setMachineAdaptationEnabled(false);
            setSide(machine, config, side, protectedType);
        });
        helper.runAfterDelay(390, () -> {
            helper.assertTrue(auxiliary.isEmpty() && stored(provider) == 40,
                    "disabling adaptation restores generic AUTO extraction of the auxiliary slot");
            helper.succeed();
        });
    }

    private static IInventorySlot slot(ConfigInfo config, DataType type) {
        return ((InventorySlotInfo) config.getSlotInfo(type)).getSlots().getFirst();
    }

    private static void put(IInventorySlot slot, int count) {
        // Invalid/leftover materials are externally extractable in Mekanism;
        // use the native unchecked setter to reproduce that state deliberately.
        try {
            var setter = BasicInventorySlot.class.getDeclaredMethod("setStackUnchecked", ItemStack.class);
            setter.setAccessible(true);
            setter.invoke(slot, new ItemStack(Items.STONE, count));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot seed the actual Mekanism slot", failure);
        }
    }

    private static void setSide(ISideConfiguration machine, ConfigInfo config, RelativeSide side, DataType type) {
        config.setDataType(type, side);
        machine.getConfig().sideChanged(TransmissionType.ITEM, side);
    }

    private static long stored(OverloadedPatternProviderBlockEntity provider) {
        return provider.getMainNode().getGrid().getStorageService().getInventory()
                .extract(AEItemKey.of(Items.STONE), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }
}
