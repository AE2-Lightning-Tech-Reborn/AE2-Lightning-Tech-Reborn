package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.blockentity.TianshuSeedStorageBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanner;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockTemplate;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopMemberPattern;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.logic.tianshu.loop.TianshuSeedRefillService;
import com.moakiee.ae2lt.logic.tianshu.terminal.SeedRefillSync;
import com.moakiee.ae2lt.overload.runtime.pattern.SourcePatternSnapshot;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Exercises the same refill service as the button against a formed Tianshu and real ME cells. */


public final class TianshuSeedRefillGameTests {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void reconcileThroughRealControllerAndMeNetwork(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        var direction = Direction.NORTH;
        var build = TianshuJdbHarness.class.getDeclaredMethod("buildComplete", ServerLevel.class, BlockPos.class, Direction.class);
        build.setAccessible(true);
        build.invoke(null, level, pos, direction);
        var controller = (TianshuSupercomputerControllerBlockEntity) level.getBlockEntity(pos);
        controller.initializeIdentityFromItem(ItemStack.EMPTY);
        controller.scanNow();
        check(controller.isFormed(), "native Tianshu forms: " + controller.issueText());
        var portPos = TianshuMultiblockScanner.worldPos(pos, TianshuMultiblockTemplate.LOWER_PORT, direction);
        var port = (TianshuSupercomputerPortBlockEntity) level.getBlockEntity(portPos);
        var cooling = TianshuJdbHarness.class.getDeclaredMethod("coolingPosition", BlockPos.class, Direction.class, int.class);
        cooling.setAccessible(true);
        var seedPos = (BlockPos) cooling.invoke(null, pos, direction, 1);
        var seedDrive = (TianshuSeedStorageBlockEntity) level.getBlockEntity(seedPos);
        seedDrive.getCellInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        var networkPos = pos.offset(12, 0, 0);
        level.setBlockAndUpdate(networkPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(networkPos.east(), AEBlocks.DRIVE.block().defaultBlockState());
        var drive = (DriveBlockEntity) level.getBlockEntity(networkPos.east());
        var networkCell = AEItems.ITEM_CELL_1K.stack();
        drive.getInternalInventory().setItemDirect(0, networkCell);
        helper.runAfterDelay(2, () -> GridHelper.createConnection(port.getMainNode().getNode(), drive.getMainNode().getNode()));

        helper.runAfterDelay(50, () -> {
            var network = port.getGrid().getStorageService().getInventory();
            var source = port.getActionSource();
            var seed = AEItemKey.of(Items.DIAMOND);
            var obsolete = AEItemKey.of(Items.EMERALD);
            var raw = AEItemKey.of(Items.REDSTONE);
            var output = AEItemKey.of(Items.PAPER);
            var encoded = PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(seed, 1), new GenericStack(raw, 1)),
                    List.of(new GenericStack(seed, 1), new GenericStack(output, 1)));
            var payload = new ClosedLoopPatternPayload(
                    List.of(new ClosedLoopMemberPattern(SourcePatternSnapshot.fromItemStack(encoded, level.registryAccess()), 1)),
                    List.of(new GenericStack(seed, 1)), List.of(new GenericStack(raw, 1)),
                    List.of(new GenericStack(output, 1)), 4, 2, true);
            var repository = port.getClosedLoopPatternRepository();
            repository.add(payload);
            port.closedLoopPatternsChanged();
            check(controller.insertReusableSeed(seed, 32, Actionable.MODULATE) == 32, "seed stock inserted");
            check(controller.insertReusableSeed(obsolete, 16, Actionable.MODULATE) == 16, "obsolete stock inserted");
            var trimmed = TianshuSeedRefillService.refillAll(port);
            check(trimmed.complete(), "trim completes");
            check(controller.reusableSeedAmount(seed) == 8 && controller.reusableSeedAmount(obsolete) == 0,
                    "only configured eight seed sets remain");
            check(network.extract(seed, 99, Actionable.SIMULATE, source) == 24
                    && network.extract(obsolete, 99, Actionable.SIMULATE, source) == 16, "exact excess reaches real ME storage");

            repository.clear();
            port.closedLoopPatternsChanged();
            var deleted = TianshuSeedRefillService.refillAll(port);
            check(deleted.complete() && controller.reusableSeedSnapshot().isEmpty(), "deleting final pattern returns all seeds");
            check(network.extract(seed, 99, Actionable.SIMULATE, source) == 32, "all original seeds conserved");

            // With no destination cell the return must leave every seed in its physical cell.
            controller.insertReusableSeed(obsolete, 5, Actionable.MODULATE);
            var savedNetworkCell = drive.getInternalInventory().getStackInSlot(0);
            drive.getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
            var blocked = TianshuSeedRefillService.refillAll(port);
            check(!blocked.complete() && controller.reusableSeedAmount(obsolete) == 5,
                    "ME rejection leaves all seeds in physical storage");
            var sync = SeedRefillSync.of(blocked);
            check(sync.state() == SeedRefillSync.STATE_RETURN_BLOCKED, "return failure has dedicated status");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            try {
                sync.writeToPacket(buffer);
                var decoded = new SeedRefillSync(buffer);
                check(decoded.equals(sync) && buffer.readableBytes() == 0, "status packet round trip preserves return key and count");
            } finally { buffer.release(); }
            drive.getInternalInventory().setItemDirect(0, savedNetworkCell);
            var retried = TianshuSeedRefillService.refillAll(port);
            check(retried.complete() && controller.reusableSeedSnapshot().isEmpty(), "retry after restoring destination succeeds");
            check(network.extract(obsolete, 99, Actionable.SIMULATE, source) == 21, "rejected and retried seeds are conserved");

            repository.add(payload);
            port.closedLoopPatternsChanged();
            var filled = TianshuSeedRefillService.refillAll(port);
            check(filled.complete() && controller.reusableSeedAmount(seed) == 8, "ordinary refill still works after cleanup");
            check(TianshuSeedRefillService.refillAll(port).moved().isEmpty(), "repeated click does not duplicate seeds");
            helper.succeed();
        });
    }
}
