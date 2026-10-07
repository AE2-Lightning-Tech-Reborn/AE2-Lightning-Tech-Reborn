package com.moakiee.ae2lt.machine.largeoverload;

import static org.junit.jupiter.api.Assertions.*;
import static com.moakiee.ae2lt.machine.largeoverload.LargeFactoryComponent.*;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class LargeFactoryStructureTest {
    private static final BlockPos CONTROLLER_POS = new BlockPos(-1, 64, -1);

    @Test
    void fixedLayoutHasExactShellAirAndRearPortCounts() {
        Map<LargeFactoryStructure.Role, Integer> counts = new EnumMap<>(LargeFactoryStructure.Role.class);
        LargeFactoryStructure.cells().forEach(cell -> counts.merge(cell.role(), 1, Integer::sum));
        assertEquals(567, LargeFactoryStructure.cells().size());
        assertEquals(Map.of(
                LargeFactoryStructure.Role.CONTROLLER, 1, LargeFactoryStructure.Role.CORE, 1,
                LargeFactoryStructure.Role.FRAME, 84, LargeFactoryStructure.Role.CASING, 228,
                LargeFactoryStructure.Role.HATCH, 9, LargeFactoryStructure.Role.AIR, 244), counts);
        for (var cell : LargeFactoryStructure.cells()) {
            if (cell.role() == LargeFactoryStructure.Role.HATCH) {
                assertEquals(8, cell.localPosition().getZ());
                assertTrue(cell.localPosition().getX() >= 3 && cell.localPosition().getX() <= 5);
                assertTrue(cell.localPosition().getY() >= 2 && cell.localPosition().getY() <= 4);
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> LargeFactoryStructure.cells().clear());
        assertThrows(IllegalArgumentException.class, () -> LargeFactoryStructure.roleAt(new BlockPos(9, 0, 0)));
    }

    @Test
    void coreIsFourBlocksBehindEveryFacingAndAllCellsStayUnique() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertEquals(CONTROLLER_POS.relative(facing.getOpposite(), 4),
                    LargeFactoryStructure.worldPosition(CONTROLLER_POS, LargeFactoryStructure.CORE, facing));
            assertEquals(CONTROLLER_POS, LargeFactoryStructure.worldPosition(
                    CONTROLLER_POS, LargeFactoryStructure.CONTROLLER, facing));
            var positions = new HashSet<BlockPos>();
            for (var cell : LargeFactoryStructure.cells()) {
                assertTrue(positions.add(LargeFactoryStructure.worldPosition(
                        CONTROLLER_POS, cell.localPosition(), facing)));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> LargeFactoryStructure.worldPosition(
                CONTROLLER_POS, LargeFactoryStructure.CORE, Direction.UP));
    }

    @Test
    void formsOrdinaryFactoryWithOnePatternHatchAndNoEnergyOrProcessHatch() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var blocks = layout(facing, CORE_T1);
            put(blocks, facing, new BlockPos(3, 2, 8), PATTERN_HATCH);
            var result = scan(facing, blocks);
            assertEquals(LargeFactoryStructure.Status.VALID, result.status(), result.diagnostics().toString());
            assertEquals(323, result.formation().members().size());
            assertEquals(1, result.formation().hatches().size());
            assertEquals(36, result.formation().patternSlots());
            assertFalse(result.formation().firmament());
        }
    }

    @Test
    void multipleHatchesCountAllPagesWithoutAddingAnotherControllerOrCore() {
        var blocks = layout(Direction.NORTH, CORE_T4);
        rearHatches(blocks, Direction.NORTH, EXPANDED_PATTERN_HATCH);
        assertEquals(1296, scan(Direction.NORTH, blocks).formation().patternSlots());
        put(blocks, Direction.NORTH, new BlockPos(3, 2, 8), PROCESS_CORE_HATCH);
        put(blocks, Direction.NORTH, new BlockPos(4, 2, 8), ENERGY_HATCH);
        var result = scan(Direction.NORTH, blocks);
        assertEquals(1008, result.formation().patternSlots());
        assertEquals(9, result.formation().hatches().size());
    }

    @Test
    void rejectsHatchesInSideWallsFramesAndAirEvenWhenRearHatchExists() {
        for (BlockPos illegal : new BlockPos[]{new BlockPos(0, 3, 4), new BlockPos(0, 0, 0),
                new BlockPos(4, 3, 5), new BlockPos(2, 3, 8)}) {
            var blocks = layout(Direction.WEST, CORE_T2);
            put(blocks, Direction.WEST, new BlockPos(3, 2, 8), PATTERN_HATCH);
            put(blocks, Direction.WEST, illegal, EXPANDED_PATTERN_HATCH);
            var result = scan(Direction.WEST, blocks);
            assertEquals(LargeFactoryStructure.Status.INVALID, result.status());
            assertTrue(result.diagnostics().stream().anyMatch(d -> d.problem()
                    == LargeFactoryStructure.Problem.WRONG_BLOCK
                    && d.position().equals(LargeFactoryStructure.worldPosition(CONTROLLER_POS, illegal, Direction.WEST))));
        }
    }

    @Test
    void ordinaryRequiresProcessingHatchAndAllowsOnlyOneProcessCoreHatch() {
        var blocks = layout(Direction.NORTH, CORE_T1);
        assertEquals(LargeFactoryStructure.Status.INVALID, scan(Direction.NORTH, blocks).status());
        put(blocks, Direction.NORTH, new BlockPos(3, 2, 8), CRYSTAL_HATCH);
        assertEquals(LargeFactoryStructure.Status.VALID, scan(Direction.NORTH, blocks).status());
        put(blocks, Direction.NORTH, new BlockPos(4, 2, 8), PROCESS_CORE_HATCH);
        put(blocks, Direction.NORTH, new BlockPos(5, 2, 8), PROCESS_CORE_HATCH);
        assertTrue(scan(Direction.NORTH, blocks).diagnostics().stream().anyMatch(d ->
                d.problem() == LargeFactoryStructure.Problem.TOO_MANY_PROCESS_CORE_HATCHES));
    }

    @Test
    void firmamentNeedsRealPatternHatchAndReadyStarshipCore() {
        var blocks = layout(Direction.EAST, FIRMAMENT_CORE);
        put(blocks, Direction.EAST, new BlockPos(3, 2, 8), CRYSTAL_HATCH);
        assertEquals(LargeFactoryStructure.Status.INVALID, scan(Direction.EAST, blocks).status());
        put(blocks, Direction.EAST, new BlockPos(4, 2, 8), PATTERN_HATCH);
        assertTrue(scan(Direction.EAST, blocks).formation().firmament());
        for (var readiness : new LargeFactoryStructure.FirmamentReadiness[]{
                LargeFactoryStructure.FirmamentReadiness.OUTSIDE_STARSHIP,
                LargeFactoryStructure.FirmamentReadiness.INVENTORY_OR_JOB_PRESENT}) {
            var result = LargeFactoryStructure.scan(CONTROLLER_POS, Direction.EAST, p -> true,
                    blocks::get, p -> readiness);
            assertEquals(LargeFactoryStructure.Status.INVALID, result.status());
            assertNull(result.formation());
        }
    }

    @Test
    void missingRequiredChunkReturnsIncompleteBeforeAnyWorldReads() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var blocks = layout(facing, CORE_T1);
            put(blocks, facing, new BlockPos(3, 2, 8), PATTERN_HATCH);
            var chunks = new HashSet<Long>();
            blocks.keySet().forEach(p -> chunks.add(chunk(p)));
            assertTrue(chunks.size() > 1);
            for (long unloaded : chunks) {
                var reads = new AtomicInteger();
                var result = LargeFactoryStructure.scan(CONTROLLER_POS, facing, p -> chunk(p) != unloaded,
                        p -> { reads.incrementAndGet(); return blocks.get(p); },
                        p -> { fail("Do not read the core while a chunk is missing"); return null; });
                assertEquals(LargeFactoryStructure.Status.INCOMPLETE, result.status());
                assertEquals(0, reads.get());
            }
        }
    }

    @Test
    void chunkDisappearingDuringReadNeverProducesAValidSnapshot() {
        var blocks = layout(Direction.NORTH, CORE_T1);
        put(blocks, Direction.NORTH, new BlockPos(3, 2, 8), PATTERN_HATCH);
        var reads = new AtomicInteger();
        var result = LargeFactoryStructure.scan(CONTROLLER_POS, Direction.NORTH,
                p -> reads.get() < 10,
                p -> { reads.incrementAndGet(); return blocks.get(p); },
                p -> LargeFactoryStructure.FirmamentReadiness.READY);
        assertEquals(LargeFactoryStructure.Status.INCOMPLETE, result.status());
        assertEquals(10, reads.get());
    }

    private static long chunk(BlockPos p) {
        return ((long) (p.getX() >> 4) << 32) ^ ((p.getZ() >> 4) & 0xffffffffL);
    }

    private static LargeFactoryStructure.ScanResult scan(Direction facing, Map<BlockPos, LargeFactoryComponent> blocks) {
        return LargeFactoryStructure.scan(CONTROLLER_POS, facing, p -> true, blocks::get,
                p -> LargeFactoryStructure.FirmamentReadiness.READY);
    }

    private static Map<BlockPos, LargeFactoryComponent> layout(Direction facing, LargeFactoryComponent core) {
        Map<BlockPos, LargeFactoryComponent> blocks = new HashMap<>();
        for (var cell : LargeFactoryStructure.cells()) {
            var component = switch (cell.role()) {
                case AIR -> AIR;
                case CONTROLLER -> CONTROLLER;
                case CORE -> core;
                case FRAME -> FRAME;
                case CASING, HATCH -> CASING;
            };
            put(blocks, facing, cell.localPosition(), component);
        }
        return blocks;
    }

    private static void rearHatches(Map<BlockPos, LargeFactoryComponent> blocks, Direction facing,
            LargeFactoryComponent component) {
        for (int x = 3; x <= 5; x++) for (int y = 2; y <= 4; y++) {
            put(blocks, facing, new BlockPos(x, y, 8), component);
        }
    }

    private static void put(Map<BlockPos, LargeFactoryComponent> blocks, Direction facing,
            BlockPos local, LargeFactoryComponent component) {
        blocks.put(LargeFactoryStructure.worldPosition(CONTROLLER_POS, local, facing), component);
    }
}
