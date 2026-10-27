package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.logic.BarrelInventoryNotificationBatch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Runs against transformed vanilla block entities, including their real comparator updates. */
@GameTestHolder("ae2lt_io")
@PrefixGameTestTemplate(false)
public final class BarrelInventoryNotificationGameTests {
    private record Target(Container inventory, BlockPos comparator) {}

    @GameTest(template = "wireless_io_empty", timeoutTicks = 60)
    public static void nestedBarrelTransfersNotifyAfterOuterClose(GameTestHelper h) {
        var first = target(h, new BlockPos(2, 2, 2), Blocks.BARREL);
        var second = target(h, new BlockPos(2, 2, 6), Blocks.BARREL);
        h.runAfterDelay(10, () -> {
            assertOutput(h, first, 0);
            assertOutput(h, second, 0);
            assertScheduled(h, first, false);
            assertScheduled(h, second, false);
            var chunk = h.getLevel().getChunkAt(h.absolutePos(new BlockPos(2, 2, 2)));
            chunk.setUnsaved(false);
            try (var outer = BarrelInventoryNotificationBatch.open()) {
                first.inventory.setItem(0, new ItemStack(Items.STONE, 64));
                h.assertTrue(chunk.isUnsaved(),
                        "first write must mark the chunk dirty before batch close");
                try (var inner = BarrelInventoryNotificationBatch.open()) {
                    second.inventory.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    first.inventory.setItem(1, new ItemStack(Items.DIRT, 64));
                }
                assertScheduled(h, first, false);
                assertScheduled(h, second, false);
            }
            assertScheduled(h, first, true);
            assertScheduled(h, second, true);
            h.runAfterDelay(4, () -> {
                assertOutput(h, first, 2);
                assertOutput(h, second, 1);
                assertScheduled(h, first, false);
                try (var batch = BarrelInventoryNotificationBatch.open()) {
                    first.inventory.setItem(0, ItemStack.EMPTY);
                    first.inventory.setItem(1, ItemStack.EMPTY);
                    assertScheduled(h, first, false);
                }
                assertScheduled(h, first, true);
                h.runAfterDelay(4, () -> {
                    assertOutput(h, first, 0);
                    assertOutput(h, second, 1);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = "wireless_io_empty", timeoutTicks = 60)
    public static void unbatchedBarrelsAndOtherContainersNotifyImmediately(GameTestHelper h) {
        var barrel = target(h, new BlockPos(2, 2, 2), Blocks.BARREL);
        var chest = target(h, new BlockPos(2, 2, 6), Blocks.CHEST);
        h.runAfterDelay(10, () -> {
            assertScheduled(h, barrel, false);
            assertScheduled(h, chest, false);
            barrel.inventory.setItem(0, new ItemStack(Items.STONE, 64));
            assertScheduled(h, barrel, true);
            try (var batch = BarrelInventoryNotificationBatch.open()) {
                chest.inventory.setItem(0, new ItemStack(Items.STONE, 64));
                assertScheduled(h, chest, true);
            }
            h.runAfterDelay(4, () -> {
                assertOutput(h, barrel, 1);
                assertOutput(h, chest, 1);
                h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", timeoutTicks = 60)
    public static void exceptionalTransferClosesBatchAndRestoresImmediateUpdates(GameTestHelper h) {
        var barrel = target(h, new BlockPos(2, 2, 2), Blocks.BARREL);
        h.runAfterDelay(10, () -> {
            var expected = new IllegalStateException("fixture transfer failure");
            try (var batch = BarrelInventoryNotificationBatch.open()) {
                barrel.inventory.setItem(0, new ItemStack(Items.STONE, 64));
                assertScheduled(h, barrel, false);
                throw expected;
            } catch (IllegalStateException failure) {
                if (failure != expected) throw failure;
            }
            assertScheduled(h, barrel, true);
            h.runAfterDelay(4, () -> {
                assertOutput(h, barrel, 1);
                assertScheduled(h, barrel, false);
                barrel.inventory.setItem(0, ItemStack.EMPTY);
                assertScheduled(h, barrel, true);
                h.runAfterDelay(4, () -> {
                    assertOutput(h, barrel, 0);
                    h.succeed();
                });
            });
        });
    }

    private static Target target(GameTestHelper h, BlockPos pos, Block inventory) {
        var comparator = pos.east();
        h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(comparator.below(), Blocks.STONE);
        h.setBlock(pos, inventory);
        h.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        return new Target((Container) h.getBlockEntity(pos), comparator);
    }

    private static void assertScheduled(GameTestHelper h, Target target, boolean expected) {
        boolean scheduled = h.getLevel().getBlockTicks().hasScheduledTick(h.absolutePos(target.comparator), Blocks.COMPARATOR);
        h.assertTrue(scheduled == expected, "comparator notification scheduled=" + scheduled + ", expected=" + expected);
    }

    private static void assertOutput(GameTestHelper h, Target target, int expected) {
        var comparator = (ComparatorBlockEntity) h.getBlockEntity(target.comparator);
        h.assertTrue(comparator.getOutputSignal() == expected,
                "comparator output=" + comparator.getOutputSignal() + ", expected=" + expected);
    }
}
