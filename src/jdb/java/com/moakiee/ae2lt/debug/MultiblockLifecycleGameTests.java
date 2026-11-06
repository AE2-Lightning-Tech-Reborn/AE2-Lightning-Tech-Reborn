package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.block.MatrixControllerBlock;
import com.moakiee.ae2lt.block.MultiblockStateProperties;
import com.moakiee.ae2lt.block.TianshuSupercomputerControllerBlock;
import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanner;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockTemplate;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanner;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockTemplate;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.logic.persistence.MultiblockRenderCleanupSavedData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_multiblock_lifecycle")
@PrefixGameTestTemplate(false)
public final class MultiblockLifecycleGameTests {
    @GameTest(template = "migration_test", templateNamespace = "ae2lt_migration", timeoutTicks = 1200)
    public static void matrixControllerRemovalClearsReloadedMembers(GameTestHelper h) {
        exercise(h, true, false);
    }

    @GameTest(template = "migration_test", templateNamespace = "ae2lt_migration", timeoutTicks = 1200)
    public static void tianshuControllerRemovalClearsReloadedMembers(GameTestHelper h) {
        exercise(h, false, false);
    }

    @GameTest(template = "migration_test", templateNamespace = "ae2lt_migration", timeoutTicks = 1200)
    public static void matrixIntactStructureReformsAfterChunkReload(GameTestHelper h) {
        exercise(h, true, true);
    }

    @GameTest(template = "migration_test", templateNamespace = "ae2lt_migration", timeoutTicks = 1200)
    public static void tianshuIntactStructureReformsAfterChunkReload(GameTestHelper h) {
        exercise(h, false, true);
    }

    @GameTest(template = "migration_test", templateNamespace = "ae2lt_migration", timeoutTicks = 1200)
    public static void westMatrixRemovalClearsPreviouslySkippedMembers(GameTestHelper h) {
        exercise(h, true, false, true);
    }

    @GameTest(template = "migration_test", templateNamespace = "ae2lt_migration", timeoutTicks = 1200)
    public static void westTianshuRemovalClearsPreviouslySkippedMembers(GameTestHelper h) {
        exercise(h, false, false, true);
    }

    @GameTest(template = "migration_test", templateNamespace = "ae2lt_migration", timeoutTicks = 1200)
    public static void westMatrixIntactStructureReformsAfterChunkReload(GameTestHelper h) {
        exercise(h, true, true, true);
    }

    @GameTest(template = "migration_test", templateNamespace = "ae2lt_migration", timeoutTicks = 1200)
    public static void westTianshuIntactStructureReformsAfterChunkReload(GameTestHelper h) {
        exercise(h, false, true, true);
    }

    @GameTest(template = "migration_test", templateNamespace = "ae2lt_migration", timeoutTicks = 200)
    public static void matrixReformationCancelsOutdatedCleanup(GameTestHelper h) {
        reformationCancelsCleanup(h, true);
    }

    @GameTest(template = "migration_test", templateNamespace = "ae2lt_migration", timeoutTicks = 200)
    public static void tianshuReformationCancelsOutdatedCleanup(GameTestHelper h) {
        reformationCancelsCleanup(h, false);
    }

    private static void reformationCancelsCleanup(GameTestHelper h, boolean matrix) {
        var level = h.getLevel();
        var controller = new BlockPos(matrix ? 4098 : 8194, 100, 8);
        var chunk = new ChunkPos(controller);
        level.setChunkForced(chunk.x, chunk.z, true);
        var members = build(level, controller, Direction.EAST, matrix);
        h.runAfterDelay(10, () -> {
            form(level, controller, matrix);
            require(formed(level, controller, matrix), "valid cancellation fixture did not form");
            var cleanup = MultiblockRenderCleanupSavedData.get(level);
            for (var pos : members) cleanup.defer(pos);
            form(level, controller, matrix);
            h.runAfterDelay(4, () -> {
                require(formed(level, controller, matrix), "queued cleanup invalidated a reformed controller");
                for (var pos : members) {
                    var state = level.getBlockState(pos);
                    if (state.hasProperty(MultiblockStateProperties.FORMED))
                        require(state.getValue(MultiblockStateProperties.FORMED), "outdated cleanup cleared reformed member " + pos);
                }
                var unrelated = controller.above(20);
                level.setBlock(unrelated, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                cleanup.defer(unrelated);
                h.runAfterDelay(4, () -> {
                    require(level.getBlockState(unrelated).is(Blocks.STONE), "cleanup changed an unrelated replacement block");
                    level.setChunkForced(chunk.x, chunk.z, false);
                    System.out.println("MULTIBLOCK_REFORMATION_PASS matrix=" + matrix);
                    h.succeed();
                });
            });
        });
    }

    private static void exercise(GameTestHelper h, boolean matrix, boolean intact) {
        exercise(h, matrix, intact, false);
    }

    private static void exercise(GameTestHelper h, boolean matrix, boolean intact, boolean west) {
        // Far from GameTest/spawn tickets; the two structures never share chunks.
        int originX = (matrix ? 1024 : 2048) + (intact ? 256 : 0) + (west ? 128 : 0);
        // Keep normal six-neighbour block updates inside the controller chunk.
        var controller = new BlockPos(originX + (west ? 18 : 13), 100, 8);
        Direction facing = west ? Direction.WEST : Direction.EAST;
        var level = h.getLevel();
        var controllerChunk = new ChunkPos(controller);
        var remoteChunk = new ChunkPos(controllerChunk.x + (west ? -1 : 1), controllerChunk.z);
        level.setChunkForced(controllerChunk.x, controllerChunk.z, true);
        level.setChunkForced(remoteChunk.x, remoteChunk.z, true);
        level.getChunk(remoteChunk.x, remoteChunk.z);
        var members = build(level, controller, facing, matrix);
        BlockPos remoteMember = members.stream().filter(pos -> new ChunkPos(pos).equals(remoteChunk))
                .filter(pos -> level.getBlockState(pos).hasProperty(MultiblockStateProperties.FORMED))
                .findFirst().orElseThrow();
        h.runAfterDelay(10, () -> {
            form(level, controller, matrix);
            require(formed(level, controller, matrix), "valid fixture did not form");
            require(level.getBlockState(remoteMember).getValue(MultiblockStateProperties.FORMED),
                    "remote member was not formed before unload");
            level.setChunkForced(controllerChunk.x, controllerChunk.z, false);
            level.setChunkForced(remoteChunk.x, remoteChunk.z, false);
            System.out.println("MULTIBLOCK_UNLOAD_REQUEST matrix=" + matrix + " intact=" + intact + " facing=" + facing);
            // Await native distance-manager and chunk unload processing, with no manual BE removal.
            h.startSequence().thenWaitUntil(() -> {
                h.assertTrue(level.getChunkSource().getChunkNow(remoteChunk.x, remoteChunk.z) == null,
                        "waiting for real remote chunk unload");
                h.assertTrue(level.getChunkSource().getChunkNow(controllerChunk.x, controllerChunk.z) == null,
                        "waiting for real controller chunk unload");
            }).thenExecute(() -> {
                System.out.println("MULTIBLOCK_ACTUALLY_UNLOADED matrix=" + matrix + " intact=" + intact + " facing=" + facing);
                // A level-33 region ticket materializes only this chunk at FULL, without loading its neighbour.
                level.getChunk(controllerChunk.x, controllerChunk.z);
                require(!level.isLoaded(remoteMember), "reloading controller unexpectedly loaded remote member chunk");
                if (!intact) level.setBlockAndUpdate(controller, Blocks.AIR.defaultBlockState());
                // Vanilla neighbour-shape updates may load adjacent chunks during block removal.
                // The invariant is eventual cleanup of every previously unloaded formed member.
                System.out.println("MULTIBLOCK_AFTER_REMOVAL matrix=" + matrix + " intact=" + intact
                        + " facing=" + facing + " remoteLoaded=" + level.isLoaded(remoteMember));
                level.setChunkForced(controllerChunk.x, controllerChunk.z, true);
                level.setChunkForced(remoteChunk.x, remoteChunk.z, true);
                level.getChunk(remoteChunk.x, remoteChunk.z);
            }).thenIdle(40).thenExecute(() -> {
                if (intact) {
                    require(formed(level, controller, matrix), "intact structure did not reform after chunk reload");
                    require(level.getBlockState(remoteMember).getValue(MultiblockStateProperties.FORMED),
                            "intact remote member lost its formed state");
                } else {
                    int stale = 0;
                    for (var pos : members) {
                        var state = level.getBlockState(pos);
                        if (state.hasProperty(MultiblockStateProperties.FORMED)
                                && state.getValue(MultiblockStateProperties.FORMED)) stale++;
                    }
                    require(stale == 0, "controller removed while members unloaded left " + stale + " formed blocks");
                }
                level.setChunkForced(controllerChunk.x, controllerChunk.z, false);
                level.setChunkForced(remoteChunk.x, remoteChunk.z, false);
                System.out.println("MULTIBLOCK_LIFECYCLE_PASS matrix=" + matrix + " intact=" + intact + " facing=" + facing);
            }).thenSucceed();
        });
    }

    private static boolean formed(ServerLevel level, BlockPos pos, boolean matrix) {
        if (matrix) return level.getBlockEntity(pos) instanceof MatrixControllerBlockEntity be && be.isFormed();
        return level.getBlockEntity(pos) instanceof TianshuSupercomputerControllerBlockEntity be && be.isFormed();
    }

    private static void form(ServerLevel level, BlockPos pos, boolean matrix) {
        if (matrix) ((MatrixControllerBlockEntity) level.getBlockEntity(pos))
                .scanAndForm(FakePlayerFactory.getMinecraft(level));
        else ((TianshuSupercomputerControllerBlockEntity) level.getBlockEntity(pos)).scanNow();
    }

    private static List<BlockPos> build(ServerLevel level, BlockPos controller, Direction facing, boolean matrix) {
        var members = new ArrayList<BlockPos>();
        boolean pattern = false;
        int height = matrix ? 11 : 7;
        for (int x = 0; x < 7; x++) for (int y = 0; y < height; y++) for (int z = 0; z < 7; z++) {
            var local = new BlockPos(x, y, z);
            Block block;
            if (matrix) {
                block = switch (MatrixMultiblockTemplate.roleAt(local)) {
                    case CASING -> ModBlocks.MATTER_WARPING_MATRIX_CASING.get();
                    case CONSTRAINT_FRAME -> ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME.get();
                    case GLASS -> ModBlocks.MATTER_WARPING_MATRIX_GLASS.get();
                    case CONTROLLER -> ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.get();
                    case PORT_CANDIDATE -> local.equals(new BlockPos(6, 5, 3))
                            ? ModBlocks.MATTER_WARPING_MATRIX_PORT.get() : ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME.get();
                    case PATTERN_BAY -> {
                        if (pattern) yield Blocks.AIR;
                        pattern = true;
                        yield ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T1.get();
                    }
                    case CRAFTING_BAY -> local.equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)
                            ? ModBlocks.MATTER_WARPING_MATRIX_MULTIDIMENSIONAL_MAIN_CORE.get() : ModBlocks.TIANSHU_BLANK_UNIT.get();
                    case EMPTY -> Blocks.AIR;
                };
            } else {
                block = switch (TianshuMultiblockTemplate.roleAt(local)) {
                    case CASING -> ModBlocks.TIANSHU_SUPERCOMPUTER_CASING.get();
                    case COOLING -> ModBlocks.PHASE_CHANGE_COOLING_UNIT.get();
                    case GLASS -> ModBlocks.TIANSHU_SUPERCOMPUTER_GLASS.get();
                    case CONTROLLER -> ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get();
                    case PORT_CANDIDATE -> local.equals(TianshuMultiblockTemplate.UPPER_PORT)
                            ? ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get() : ModBlocks.PHASE_CHANGE_COOLING_UNIT.get();
                    case CORE_RESERVED -> local.equals(new BlockPos(3, 3, 3))
                            ? ModBlocks.BASELINE_SUPERCOMPUTING_UNIT.get() : ModBlocks.TIANSHU_BLANK_UNIT.get();
                    case IGNORED -> Blocks.AIR;
                };
            }
            var state = block.defaultBlockState();
            if (block instanceof MatrixControllerBlock) state = state.setValue(MatrixControllerBlock.FACING, facing);
            if (block instanceof TianshuSupercomputerControllerBlock)
                state = state.setValue(TianshuSupercomputerControllerBlock.FACING, facing);
            var pos = matrix ? MatrixMultiblockScanner.worldPos(controller, local, facing)
                    : TianshuMultiblockScanner.worldPos(controller, local, facing);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            if (!state.isAir()) members.add(pos);
        }
        return members;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
