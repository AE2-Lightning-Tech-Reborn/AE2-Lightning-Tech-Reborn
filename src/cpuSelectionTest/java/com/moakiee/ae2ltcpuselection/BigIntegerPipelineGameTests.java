package com.moakiee.ae2ltcpuselection;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.me.storage.*;

import com.moakiee.ae2lt.block.*;
import com.moakiee.ae2lt.blockentity.*;
import com.moakiee.ae2lt.crafting.big.*;
import com.moakiee.ae2lt.logic.craft.*;
import com.moakiee.ae2lt.logic.tianshu.*;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.thunderbolt.api.storage.BigMEStorage;
import com.moakiee.thunderbolt.core.crafting.big.*;
import com.moakiee.thunderbolt.core.crafting.planner.*;
import com.moakiee.thunderbolt.core.storage.big.*;
import com.moakiee.thunderbolt.core.storage.cell.*;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.*;

import java.math.BigInteger;
import java.util.*;

public final class BigIntegerPipelineGameTests {
    private static final BigInteger N = BigInteger.TEN.pow(100);
    private static final IActionSource SOURCE = IActionSource.empty();

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(net.minecraft.network.chat.Component.literal(message), 0);
    }

    private static IndexedStorageCellInventory cell(GameTestHelper h) {
        var item = ModItems.INFINITE_STORAGE_CELL.get();
        return new IndexedStorageCellInventory(
                new ItemStack(item), item, h.getLevel().registryAccess(), null);
    }

    public static void multipleInfiniteCellsKeepLegacyProjectionPositive(GameTestHelper h) {
        var first = cell(h);
        var second = cell(h);
        var key = AEItemKey.of(Items.IRON_INGOT);
        first.insertBig(key, N, Actionable.MODULATE, SOURCE);
        second.insertBig(key, N, Actionable.MODULATE, SOURCE);
        var network = new NetworkStorage();
        network.mount(0, new DriveWatcher(first, () -> {}));
        network.mount(0, new DriveWatcher(second, () -> {}));
        var visible = new KeyCounter();
        network.getAvailableStacks(visible);
        require(visible.get(key) == Long.MAX_VALUE, "combined long display overflowed");
        require(
                BigStorageOps.snapshot(network, SOURCE).get(key).equals(N.multiply(BigInteger.TWO)),
                "combined exact stock changed");
        h.succeed();
    }

    public static void liveMixinsShareExactAndLegacyInventory(GameTestHelper h) {
        var cell = cell(h);
        var drive = new DriveWatcher(cell, () -> {});
        var net = new NetworkStorage();
        net.mount(0, drive);
        require(net instanceof BigMEStorage, "network exact mixin missing");
        require(drive instanceof BigMEStorage, "drive exact mixin missing");
        var key = AEItemKey.of(Items.IRON_INGOT);
        require(
                BigStorageOps.insert(net, key, N, Actionable.SIMULATE, SOURCE).equals(N),
                "simulation rejected");
        require(BigStorageOps.snapshot(net, SOURCE).isEmpty(), "simulation mutated cell");
        require(
                BigStorageOps.insert(net, key, N, Actionable.MODULATE, SOURCE).equals(N),
                "exact insertion truncated");
        require(
                net.extract(key, 17, Actionable.MODULATE, SOURCE) == 17,
                "legacy extraction failed");
        require(
                BigStorageOps.snapshot(net, SOURCE)
                        .get(key)
                        .equals(N.subtract(BigInteger.valueOf(17))),
                "dual accounting");
        drive.setExtractFiltering(true, true);
        drive.setAllowExtraction(false);
        require(
                BigStorageOps.extract(net, key, N, Actionable.MODULATE, SOURCE).signum() == 0,
                "drive extraction policy bypassed");
        require(BigStorageOps.snapshot(net, SOURCE).isEmpty(), "filtered stock leaked into plan");
        drive.setAllowExtraction(true);
        drive.setAllowInsertion(false);
        require(
                BigStorageOps.insert(net, key, N, Actionable.MODULATE, SOURCE).signum() == 0,
                "drive insertion policy bypassed");
        var saved = cell.storage().persist(null, h.getLevel().registryAccess());
        var restored = new IndexedStorage();
        restored.load(saved, h.getLevel().registryAccess());
        require(
                restored.getAmountExact(key).equals(N.subtract(BigInteger.valueOf(17))),
                "NBT truncated stock");
        require(
                BigStorageOps.extract(net, key, N, Actionable.MODULATE, SOURCE)
                        .equals(N.subtract(BigInteger.valueOf(17))),
                "exact extraction truncated");
        h.succeed();
    }

    public static void nativeStoragePriorityIsPreserved(GameTestHelper h) {
        var high = cell(h);
        var low = cell(h);
        var net = new NetworkStorage();
        net.mount(10, new DriveWatcher(high, () -> {}));
        net.mount(-10, new DriveWatcher(low, () -> {}));
        var key = AEItemKey.of(Items.GOLD_INGOT);
        BigStorageOps.insert(net, key, N, Actionable.MODULATE, SOURCE);
        require(high.storage().getAmountExact(key).equals(N), "insertion priority reversed");
        low.insertBig(key, BigInteger.TEN, Actionable.MODULATE, SOURCE);
        BigStorageOps.extract(net, key, BigInteger.ONE, Actionable.MODULATE, SOURCE);
        require(
                low.storage().getAmountExact(key).equals(BigInteger.valueOf(9)),
                "extraction priority reversed");
        h.succeed();
    }

    private static BigMatrixRecipe planks(GameTestHelper h) {
        var holder =
                com.moakiee.ae2lt.recipe.compat.LegacyRecipeAccess.manager(h.getLevel())
                        .byKey(net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.RECIPE,
                                Identifier.parse("minecraft:oak_planks")))
                        .orElseThrow();
        var recipe = new RecipeHolder<CraftingRecipe>(holder.id(), (CraftingRecipe) holder.value());
        var input = new ItemStack[9];
        Arrays.fill(input, ItemStack.EMPTY);
        input[0] = new ItemStack(Items.OAK_LOG);
        var encoded =
                PatternDetailsHelper.encodeCraftingPattern(
                        recipe, input, new ItemStack(Items.OAK_PLANKS, 4), false, false);
        var decoded = PatternDetailsHelper.decodePattern(encoded, h.getLevel());
        return Objects.requireNonNull(
                BigMatrixRecipe.capture(
                        decoded, List.of(Map.of(AEItemKey.of(Items.OAK_LOG), 1L)), h.getLevel()));
    }

    public static void actualRecipePlanRestartCommitAndRefundKeepEveryUnit(GameTestHelper h) {
        var recipe = planks(h);
        var raw = AEItemKey.of(Items.OAK_LOG);
        var out = AEItemKey.of(Items.OAK_PLANKS);
        var graph =
                CraftGraph.<AEKey>builder().stockExact(raw, N).pattern(recipe.pattern()).build();
        var plan =
                BigCraftingPlanner.plan(
                        graph, out, N.multiply(BigInteger.valueOf(4)), Map.of(raw, N));
        require(plan.executable(), "real recipe not executable");
        var cell = cell(h);
        cell.insertBig(raw, N, Actionable.MODULATE, SOURCE);
        var job = new BigCraftingJob(out, N.multiply(BigInteger.valueOf(4)), plan.program());
        for (var e : job.program.required().entrySet())
            job.escrow.put(
                    e.getKey(),
                    cell.extractBig(e.getKey(), e.getValue(), Actionable.MODULATE, SOURCE));
        job.acceptedStep = job.current().steps().size();
        job.readyAt = 100;
        var restored =
                BigCraftingJob.load(
                        job.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        require(
                restored.readyAt == 100 && restored.escrow.equals(job.escrow),
                "pending restart lost ownership");
        while (!restored.returning) restored.commitBlock();
        for (var e : restored.escrow.entrySet())
            cell.insertBig(e.getKey(), e.getValue(), Actionable.MODULATE, SOURCE);
        require(
                cell.storage().getAmountExact(out).equals(N.multiply(BigInteger.valueOf(4))),
                "output amount changed");
        require(cell.storage().getAmountExact(raw).signum() == 0, "raw duplicated");
        var cancelled =
                BigCraftingJob.load(
                        job.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        cancelled.cancel();
        require(
                cancelled.escrow.get(raw).equals(N) && !cancelled.escrow.containsKey(out),
                "cancellation materialized pending output");
        h.succeed();
    }

    public static void formedMultidimensionalMachinesRunAnExactOrder(GameTestHelper h) {
        var level = h.getLevel();
        var direction = Direction.EAST;
        var cpuPos = h.absolutePos(new BlockPos(4, 10, 4));
        var matrixPos = h.absolutePos(new BlockPos(22, 12, 4));
        for (int x = 0; x < 7; x++)
            for (int y = 0; y < 7; y++)
                for (int z = 0; z < 7; z++) {
                    var local = new BlockPos(x, y, z);
                    var state =
                            switch (TianshuMultiblockTemplate.roleAt(local)) {
                                case CASING ->
                                        ModBlocks.TIANSHU_SUPERCOMPUTER_CASING
                                                .get()
                                                .defaultBlockState();
                                case COOLING ->
                                        ModBlocks.PHASE_CHANGE_COOLING_UNIT
                                                .get()
                                                .defaultBlockState();
                                case GLASS ->
                                        ModBlocks.TIANSHU_SUPERCOMPUTER_GLASS
                                                .get()
                                                .defaultBlockState();
                                case CONTROLLER ->
                                        ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER
                                                .get()
                                                .defaultBlockState()
                                                .setValue(
                                                        TianshuSupercomputerControllerBlock.FACING,
                                                        direction);
                                case PORT_CANDIDATE ->
                                        (local.equals(TianshuMultiblockTemplate.LOWER_PORT)
                                                        ? ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get()
                                                        : ModBlocks.PHASE_CHANGE_COOLING_UNIT.get())
                                                .defaultBlockState();
                                case CORE_RESERVED ->
                                        (local.equals(new BlockPos(3, 3, 3))
                                                        ? ModBlocks
                                                                .MULTIDIMENSIONAL_SUPERCOMPUTING_UNIT
                                                                .get()
                                                        : ModBlocks.TIANSHU_BLANK_UNIT.get())
                                                .defaultBlockState();
                                case IGNORED -> Blocks.AIR.defaultBlockState();
                            };
                    place(h, TianshuMultiblockScanner.worldPos(cpuPos, local, direction), state);
                }
        BlockPos patternPos = null;
        var portLocal = new BlockPos(6, 5, 3);
        for (var entry : MatrixMultiblockTemplate.entries()) {
            var local = entry.localPos();
            var world = MatrixMultiblockScanner.worldPos(matrixPos, local, direction);
            var state =
                    switch (entry.role()) {
                        case CASING ->
                                ModBlocks.MATTER_WARPING_MATRIX_CASING.get().defaultBlockState();
                        case CONSTRAINT_FRAME ->
                                ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME
                                        .get()
                                        .defaultBlockState();
                        case GLASS ->
                                ModBlocks.MATTER_WARPING_MATRIX_GLASS.get().defaultBlockState();
                        case CONTROLLER ->
                                ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER
                                        .get()
                                        .defaultBlockState()
                                        .setValue(MatrixControllerBlock.FACING, direction);
                        case PORT_CANDIDATE ->
                                (local.equals(portLocal)
                                                ? ModBlocks.MATTER_WARPING_MATRIX_PORT.get()
                                                : ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME
                                                        .get())
                                        .defaultBlockState();
                        case PATTERN_BAY -> {
                            if (patternPos == null) {
                                patternPos = world;
                                yield ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T1
                                        .get()
                                        .defaultBlockState();
                            }
                            yield Blocks.AIR.defaultBlockState();
                        }
                        case CRAFTING_BAY ->
                                (local.equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)
                                                ? ModBlocks
                                                        .MATTER_WARPING_MATRIX_MULTIDIMENSIONAL_MAIN_CORE
                                                        .get()
                                                : ModBlocks.TIANSHU_BLANK_UNIT.get())
                                        .defaultBlockState();
                        case EMPTY -> Blocks.AIR.defaultBlockState();
                    };
            place(h, world, state);
        }
        var drivePos = h.absolutePos(new BlockPos(14, 10, 4));
        var powerPos = drivePos.above();
        place(h, drivePos, AEBlocks.DRIVE.block().defaultBlockState());
        place(h, powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var drive = (DriveBlockEntity) level.getBlockEntity(drivePos);
        drive.getInternalInventory()
                .setItemDirect(0, new ItemStack(ModItems.INFINITE_STORAGE_CELL.get()));
        var cpu = (TianshuSupercomputerControllerBlockEntity) level.getBlockEntity(cpuPos);
        var matrix = (MatrixControllerBlockEntity) level.getBlockEntity(matrixPos);
        var patterns = (MatrixPatternStorageBlockEntity) level.getBlockEntity(patternPos);
        var recipe = planks(h);
        patterns.getInventory().setStackInSlot(0, recipe.definition().toStack());
        // The empty vanilla template does not keep the out-of-template multiblock chunks ticking.
        for (var pos : List.of(cpuPos, matrixPos, patternPos, drivePos,
                MatrixMultiblockScanner.worldPos(matrixPos, portLocal, direction))) {
            level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        }
        h.runAfterDelay(
                10,
                () -> {
                    cpu.scanNow();
                    matrix.scanAndForm(
                            net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(
                                    level));
                    require(cpu.isFormed(), "CPU did not form: " + cpu.issueText());
                    require(
                            matrix.isBigCraftingAvailable(),
                            "matrix did not form as multidimensional");
                    var cpuPort =
                            (TianshuSupercomputerPortBlockEntity)
                                    level.getBlockEntity(cpu.getPortPos());
                    var matrixPort =
                            (MatrixPortBlockEntity)
                                    level.getBlockEntity(
                                            MatrixMultiblockScanner.worldPos(
                                                    matrixPos, portLocal, direction));
                    connectWhenReady(h, drive, cpuPort, matrixPort, () -> {
                        h.runAfterDelay(
                            30,
                            () -> {
                                require(
                                        cpu.bigCrafting().available(),
                                        "CPU exact service inactive");
                                var inventory = cpu.getGrid().getStorageService().getInventory();
                                var raw = AEItemKey.of(Items.OAK_LOG);
                                var output = AEItemKey.of(Items.OAK_PLANKS);
                                require(
                                        BigStorageOps.insert(
                                                        inventory,
                                                        raw,
                                                        N,
                                                        Actionable.MODULATE,
                                                        SOURCE)
                                                .equals(N),
                                        "network cell did not accept exact stock");
                                require(
                                        matrix.affordableOperations(Long.MAX_VALUE)
                                                == Long.MAX_VALUE,
                                        "multidimensional long dispatch still energy limited");
                                var future =
                                        cpu.bigCrafting()
                                                .preview(
                                                        output,
                                                        N.multiply(BigInteger.valueOf(4)),
                                                        SOURCE);
                                var phase = new int[1];
                                var startedAt = new long[1];
                                var removedCell = new ItemStack[] {ItemStack.EMPTY};
                                h.succeedWhen(
                                        () -> {
                                            h.assertTrue(future.isDone(), "waiting for planner");
                                            var plan = future.join();
                                            require(
                                                    plan.executable(),
                                                    "captured machine graph failed exact planning");
                                            var service = cpu.bigCrafting();
                                            if (phase[0] == 0) {
                                                var adapted =
                                                        new BigCraftingPlan(
                                                                output,
                                                                N.multiply(BigInteger.valueOf(4)),
                                                                plan,
                                                                "");
                                                var submitted =
                                                        cpu.getGrid()
                                                                .getCraftingService()
                                                                .submitJob(
                                                                        adapted,
                                                                        null,
                                                                        cpuPort
                                                                                .getTimeWheelCraftingCpuPool(),
                                                                        true,
                                                                        SOURCE);
                                                require(
                                                        submitted.successful(),
                                                        "native CPU submit rejected exact order");
                                                require(
                                                        !BigStorageOps.snapshot(inventory, SOURCE)
                                                                .containsKey(raw),
                                                        "reserved raw still in network");
                                                service.load(
                                                        service.save(level.registryAccess()),
                                                        level.registryAccess());
                                                patterns.getInventory()
                                                        .setStackInSlot(0, ItemStack.EMPTY);
                                                startedAt[0] = level.getGameTime();
                                                phase[0] = 1;
                                            }
                                            if (phase[0] == 1) {
                                                h.assertTrue(
                                                        level.getGameTime() - startedAt[0] >= 10,
                                                        "waiting with recipe removed");
                                                require(
                                                        service.job() != null
                                                                && service.job().blockIndex == 0
                                                                && service.job().readyAt < 0,
                                                        "missing recipe still dispatched");
                                                require(
                                                        service.job().escrow.get(raw).equals(N),
                                                        "waiting lost escrow");
                                                patterns.getInventory()
                                                        .setStackInSlot(
                                                                0, recipe.definition().toStack());
                                                phase[0] = 2;
                                            }
                                            if (phase[0] == 2) {
                                                h.assertTrue(
                                                        service.job() != null
                                                                && service.job().readyAt >= 0,
                                                        "waiting for matrix acceptance");
                                                service.load(
                                                        service.save(level.registryAccess()),
                                                        level.registryAccess());
                                                removedCell[0] =
                                                        drive.getInternalInventory()
                                                                .extractItem(0, 1, false);
                                                startedAt[0] = level.getGameTime();
                                                phase[0] = 3;
                                            }
                                            if (phase[0] == 3) {
                                                h.assertTrue(
                                                        level.getGameTime() - startedAt[0] >= 15,
                                                        "waiting without output storage");
                                                require(
                                                        service.job() != null
                                                                && service.job().returning,
                                                        "missing drive lost completed job");
                                                require(
                                                        service.job()
                                                                .escrow
                                                                .get(output)
                                                                .equals(
                                                                        N.multiply(
                                                                                BigInteger.valueOf(
                                                                                        4))),
                                                        "undelivered output truncated");
                                                service.load(
                                                        service.save(level.registryAccess()),
                                                        level.registryAccess());
                                                drive.getInternalInventory()
                                                        .setItemDirect(0, removedCell[0]);
                                                phase[0] = 4;
                                            }
                                            if (phase[0] == 4) {
                                                h.assertTrue(
                                                        !service.busy(),
                                                        "waiting for output return");
                                                require(
                                                        BigStorageOps.snapshot(inventory, SOURCE)
                                                                .get(output)
                                                                .equals(
                                                                        N.multiply(
                                                                                BigInteger.valueOf(
                                                                                        4))),
                                                        "live pipeline lost output");
                                                require(
                                                        BigStorageOps.insert(
                                                                        inventory,
                                                                        raw,
                                                                        N,
                                                                        Actionable.MODULATE,
                                                                        SOURCE)
                                                                .equals(N),
                                                        "second stock insertion failed");
                                                require(
                                                        service.submit(
                                                                output,
                                                                N.multiply(BigInteger.valueOf(4)),
                                                                plan,
                                                                SOURCE),
                                                        "second order rejected");
                                                service.cancel();
                                                phase[0] = 5;
                                            }
                                            h.assertTrue(
                                                    !service.busy(),
                                                    "waiting for cancellation refund");
                                            var finalStock =
                                                    BigStorageOps.snapshot(inventory, SOURCE);
                                            require(
                                                    finalStock.get(raw).equals(N),
                                                    "cancel did not refund all raw material");
                                            require(
                                                    finalStock
                                                            .get(output)
                                                            .equals(
                                                                    N.multiply(
                                                                            BigInteger.valueOf(4))),
                                                    "cancel fabricated more output");
                                        });
                            });
                    });
                });
    }

    private static void connectWhenReady(GameTestHelper h, DriveBlockEntity drive,
            TianshuSupercomputerPortBlockEntity cpuPort, MatrixPortBlockEntity matrixPort,
            Runnable connected) {
        var node = drive.getMainNode().getNode();
        var cpuNode = cpuPort.getMainNode().getNode();
        var matrixNode = matrixPort.getMainNode().getNode();
        if (node == null || cpuNode == null || matrixNode == null) {
            require(h.getTick() < 160, "formed machine grid nodes did not become active: drive="
                    + (node != null) + " cpu=" + (cpuNode != null) + " matrix=" + (matrixNode != null));
            h.runAfterDelay(1, () -> connectWhenReady(h, drive, cpuPort, matrixPort, connected));
            return;
        }
        GridHelper.createConnection(node, cpuNode);
        GridHelper.createConnection(node, matrixNode);
        connected.run();
    }

    private static void place(GameTestHelper h, BlockPos pos, BlockState state) {
        h.getLevel().getChunkAt(pos);
        h.getLevel().setBlock(pos, state, 3);
    }
}
