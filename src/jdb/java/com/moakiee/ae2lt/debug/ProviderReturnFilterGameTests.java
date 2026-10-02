package com.moakiee.ae2lt.debug;

import java.util.List;

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
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercise the real AE2 grid ticker, import toggle and machine capabilities. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class ProviderReturnFilterGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    @GameTest(template = "wireless_io_empty", timeoutTicks = 220)
    public static void adjacentUnfilteredReturnWorksWithoutPatterns(GameTestHelper helper) {
        checkUnfiltered(helper, false);
    }

    @GameTest(template = "wireless_io_empty", timeoutTicks = 220)
    public static void wirelessUnfilteredReturnWorksWithoutPatterns(GameTestHelper helper) {
        checkUnfiltered(helper, true);
    }

    private static void checkUnfiltered(GameTestHelper helper, boolean wireless) {
        var fixture = fixture(helper, wireless);
        fixture.provider.setReturnMode(ReturnMode.AUTO);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(fixture.provider.getMainNode().isActive(), "provider must be active");
            fill(fixture.inventory);
        });
        helper.runAfterDelay(180, () -> {
            assertDrained(helper, fixture);
            helper.succeed();
        });
    }

    @GameTest(template = "wireless_io_empty", timeoutTicks = 180)
    public static void disablingFilterWakesAnEmptyPatternProvider(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        fixture.provider.setFilteredImport(true);
        fixture.provider.setReturnMode(ReturnMode.AUTO);
        helper.runAfterDelay(40, () -> fill(fixture.inventory));
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(stored(fixture.provider, Items.STONE) == 0
                    && fixture.inventory.getItem(0).getCount() == 32,
                    "enabled filter without patterns must leave inventory untouched");
            fixture.provider.setFilteredImport(false);
        });
        helper.runAfterDelay(140, () -> {
            assertDrained(helper, fixture);
            helper.succeed();
        });
    }

    @GameTest(template = "wireless_io_empty", timeoutTicks = 230)
    public static void enabledFilterPreservesByproductsUntilDisabled(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        fixture.provider.setFilteredImport(true);
        fixture.provider.setReturnMode(ReturnMode.AUTO);
        helper.runAfterDelay(40, () -> {
            var pattern = PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
                    List.of(new GenericStack(AEItemKey.of(Items.STONE), 1)));
            fixture.provider.getExposedPatternInventory().setItemDirect(0, pattern);
            fill(fixture.inventory);
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(stored(fixture.provider, Items.STONE) == 32,
                    "listed output must return to ME storage");
            helper.assertTrue(stored(fixture.provider, Items.DIRT) == 0
                    && fixture.inventory.getItem(1).getCount() == 17,
                    "filtered return must preserve unlisted byproduct");
            fixture.provider.setFilteredImport(false);
        });
        helper.runAfterDelay(190, () -> {
            assertDrained(helper, fixture);
            helper.succeed();
        });
    }

    private static Fixture fixture(GameTestHelper helper, boolean wireless) {
        helper.setBlock(POS.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(POS, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.SOUTH));
        helper.setBlock(POS.west(), AEBlocks.DRIVE.block());
        var drive = (DriveBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS.west()));
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        var provider = (OverloadedPatternProviderBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(POS));
        var machinePos = wireless ? POS.offset(4, 0, 0) : POS.south();
        helper.setBlock(machinePos, Blocks.BARREL);
        if (wireless) {
            provider.setProviderMode(ProviderMode.WIRELESS);
            helper.assertTrue(provider.addOrUpdateConnection(helper.getLevel().dimension(),
                    helper.absolutePos(machinePos), Direction.NORTH), "wireless target must bind");
        }
        return new Fixture(provider, (Container) helper.getLevel().getBlockEntity(helper.absolutePos(machinePos)));
    }

    private static void fill(Container inventory) {
        inventory.setItem(0, new ItemStack(Items.STONE, 32));
        inventory.setItem(1, new ItemStack(Items.DIRT, 17));
        inventory.setChanged();
    }

    private static void assertDrained(GameTestHelper helper, Fixture fixture) {
        helper.assertTrue(fixture.inventory.isEmpty(), "machine inventory must be empty");
        long stone = stored(fixture.provider, Items.STONE);
        long dirt = stored(fixture.provider, Items.DIRT);
        var drive = (DriveBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS.west()));
        helper.assertTrue(stone == 32 && dirt == 17, "both exact quantities must reach ME storage: stone="
                + stone + ", dirt=" + dirt + ", cell=" + drive.getInternalInventory().getStackInSlot(0));
    }

    private static long stored(OverloadedPatternProviderBlockEntity provider, Item item) {
        return provider.getMainNode().getGrid().getStorageService().getInventory()
                .extract(AEItemKey.of(item), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    private record Fixture(OverloadedPatternProviderBlockEntity provider, Container inventory) {}
}
