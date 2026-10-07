package com.moakiee.ae2lt.machine.largeoverload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Fixed first-generation LT factory, not a configurable structure framework. */
public final class LargeFactoryStructure {
    public static final int WIDTH = 9;
    public static final int HEIGHT = 7;
    public static final int DEPTH = 9;
    public static final BlockPos CONTROLLER = new BlockPos(4, 3, 0);
    public static final BlockPos CORE = new BlockPos(4, 3, 4);
    private static final int MAX_DIAGNOSTICS = 16;
    private static final List<Cell> CELLS = createCells();

    private LargeFactoryStructure() {
    }

    public enum Role { CONTROLLER, FRAME, CASING, CORE, HATCH, AIR }
    public enum Status { VALID, INVALID, INCOMPLETE }
    public enum Problem {
        WRONG_BLOCK, CHUNK_UNLOADED, MISSING_PROCESSING_HATCH, TOO_MANY_PROCESS_CORE_HATCHES,
        FIRMAMENT_OUTSIDE_STARSHIP, FIRMAMENT_NOT_EMPTY
    }
    /** Location and inventory/job readiness must come from the real central block entity. */
    public enum FirmamentReadiness { READY, OUTSIDE_STARSHIP, INVENTORY_OR_JOB_PRESENT }

    public record Cell(BlockPos localPosition, Role role) {
        public Cell {
            localPosition = localPosition.immutable();
            Objects.requireNonNull(role, "role");
        }
    }

    public record Member(BlockPos position, Role role, LargeFactoryComponent component) {
        public Member {
            position = position.immutable();
        }
    }

    public record Diagnostic(Problem problem, BlockPos position, Role expected, LargeFactoryComponent actual) {
        public Diagnostic {
            position = position.immutable();
        }
    }

    public record Formation(
            BlockPos controller, Direction facing, LargeFactoryComponent core,
            List<Member> members, List<Member> hatches, int patternSlots) {
        public Formation {
            controller = controller.immutable();
            members = List.copyOf(members);
            hatches = List.copyOf(hatches);
        }

        public boolean firmament() {
            return core == LargeFactoryComponent.FIRMAMENT_CORE;
        }
    }

    public record ScanResult(Status status, Formation formation, List<Diagnostic> diagnostics) {
        public ScanResult {
            Objects.requireNonNull(status, "status");
            diagnostics = List.copyOf(diagnostics);
            if ((status == Status.VALID) != (formation != null)) {
                throw new IllegalArgumentException("Only a valid scan can provide a factory formation");
            }
        }
    }

    /** Shared by the scanner and future preview/building UI; air remains an explicit requirement. */
    public static List<Cell> cells() {
        return CELLS;
    }

    public static Role roleAt(BlockPos local) {
        int x = local.getX(), y = local.getY(), z = local.getZ();
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            throw new IllegalArgumentException("Outside the fixed factory: " + local);
        }
        if (local.equals(CONTROLLER)) return Role.CONTROLLER;
        if (local.equals(CORE)) return Role.CORE;
        int boundaries = (x == 0 || x == WIDTH - 1 ? 1 : 0)
                + (y == 0 || y == HEIGHT - 1 ? 1 : 0) + (z == 0 || z == DEPTH - 1 ? 1 : 0);
        if (boundaries >= 2) return Role.FRAME;
        if (z == DEPTH - 1 && x >= 3 && x <= 5 && y >= 2 && y <= 4) return Role.HATCH;
        return boundaries == 1 ? Role.CASING : Role.AIR;
    }

    /** The front faces outwards. Local +Z extends behind the controller. */
    public static BlockPos worldPosition(BlockPos controller, BlockPos local, Direction facing) {
        int x = local.getX() - CONTROLLER.getX();
        int y = local.getY() - CONTROLLER.getY();
        int z = local.getZ() - CONTROLLER.getZ();
        return switch (facing) {
            case NORTH -> controller.offset(x, y, z);
            case EAST -> controller.offset(-z, y, x);
            case SOUTH -> controller.offset(-x, y, -z);
            case WEST -> controller.offset(z, y, -x);
            default -> throw new IllegalArgumentException("Factory facing must be horizontal");
        };
    }

    /**
     * Pure read-only scan. loaded must only test availability and resolve must never load chunks.
     * Binding, inventory transfer and taking over a firmament core happen after successful scanning.
     */
    public static ScanResult scan(BlockPos controller, Direction facing, Predicate<BlockPos> loaded,
            Function<BlockPos, LargeFactoryComponent> resolve,
            Function<BlockPos, FirmamentReadiness> firmamentReadiness) {
        Objects.requireNonNull(loaded, "loaded");
        Objects.requireNonNull(resolve, "resolve");
        Objects.requireNonNull(firmamentReadiness, "firmamentReadiness");
        List<Diagnostic> issues = new ArrayList<>();
        // Enumerate the whole footprint, including negative chunk coordinates.
        BlockPos cornerA = worldPosition(controller, BlockPos.ZERO, facing);
        BlockPos cornerB = worldPosition(controller, new BlockPos(WIDTH - 1, HEIGHT - 1, DEPTH - 1), facing);
        for (int chunkX = Math.min(cornerA.getX(), cornerB.getX()) >> 4;
                chunkX <= Math.max(cornerA.getX(), cornerB.getX()) >> 4; chunkX++) {
            for (int chunkZ = Math.min(cornerA.getZ(), cornerB.getZ()) >> 4;
                    chunkZ <= Math.max(cornerA.getZ(), cornerB.getZ()) >> 4; chunkZ++) {
                BlockPos probe = new BlockPos(chunkX << 4, controller.getY(), chunkZ << 4);
                if (!loaded.test(probe)) {
                    issues.add(new Diagnostic(Problem.CHUNK_UNLOADED, probe, null, null));
                }
            }
        }
        if (!issues.isEmpty()) return new ScanResult(Status.INCOMPLETE, null, issues);

        List<Member> members = new ArrayList<>(323);
        List<Member> hatches = new ArrayList<>(9);
        LargeFactoryComponent core = null;
        int patternHatches = 0, crystalHatches = 0, processHatches = 0, patternSlots = 0;
        for (Cell cell : CELLS) {
            BlockPos position = worldPosition(controller, cell.localPosition(), facing);
            // Also handle a synchronous unload/change during the scan without reading that chunk.
            if (!loaded.test(position)) {
                return new ScanResult(Status.INCOMPLETE, null,
                        List.of(new Diagnostic(Problem.CHUNK_UNLOADED, position, cell.role(), null)));
            }
            LargeFactoryComponent component = Objects.requireNonNull(resolve.apply(position), "resolved component");
            boolean matches = switch (cell.role()) {
                case CONTROLLER -> component == LargeFactoryComponent.CONTROLLER;
                case FRAME -> component == LargeFactoryComponent.FRAME;
                case CASING -> component == LargeFactoryComponent.CASING;
                case CORE -> component.isCore();
                case HATCH -> component == LargeFactoryComponent.CASING || component.isHatch();
                case AIR -> component == LargeFactoryComponent.AIR;
            };
            if (!matches) {
                addIssue(issues, new Diagnostic(Problem.WRONG_BLOCK, position, cell.role(), component));
                continue;
            }
            if (cell.role() != Role.AIR) members.add(new Member(position, cell.role(), component));
            if (cell.role() == Role.CORE) core = component;
            if (component.isHatch()) {
                hatches.add(new Member(position, cell.role(), component));
                if (component.isPatternHatch()) {
                    patternHatches++;
                    patternSlots += component.patternSlots();
                }
                if (component == LargeFactoryComponent.CRYSTAL_HATCH) crystalHatches++;
                if (component == LargeFactoryComponent.PROCESS_CORE_HATCH) processHatches++;
            }
        }
        boolean firmament = core == LargeFactoryComponent.FIRMAMENT_CORE;
        if ((firmament && patternHatches == 0) || (!firmament && patternHatches + crystalHatches == 0)) {
            addIssue(issues, new Diagnostic(Problem.MISSING_PROCESSING_HATCH, controller, Role.HATCH, null));
        }
        if (processHatches > 1) {
            addIssue(issues, new Diagnostic(Problem.TOO_MANY_PROCESS_CORE_HATCHES, controller, Role.HATCH,
                    LargeFactoryComponent.PROCESS_CORE_HATCH));
        }
        if (firmament) {
            BlockPos center = worldPosition(controller, CORE, facing);
            FirmamentReadiness readiness = Objects.requireNonNull(firmamentReadiness.apply(center));
            if (readiness != FirmamentReadiness.READY) {
                addIssue(issues, new Diagnostic(readiness == FirmamentReadiness.OUTSIDE_STARSHIP
                        ? Problem.FIRMAMENT_OUTSIDE_STARSHIP : Problem.FIRMAMENT_NOT_EMPTY,
                        center, Role.CORE, core));
            }
        }
        if (!issues.isEmpty()) return new ScanResult(Status.INVALID, null, issues);
        return new ScanResult(Status.VALID,
                new Formation(controller, facing, core, members, hatches, patternSlots), List.of());
    }

    private static void addIssue(List<Diagnostic> issues, Diagnostic issue) {
        if (issues.size() < MAX_DIAGNOSTICS) issues.add(issue);
    }

    private static List<Cell> createCells() {
        List<Cell> cells = new ArrayList<>(WIDTH * HEIGHT * DEPTH);
        for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < DEPTH; z++) for (int x = 0; x < WIDTH; x++) {
            BlockPos local = new BlockPos(x, y, z);
            cells.add(new Cell(local, roleAt(local)));
        }
        return List.copyOf(cells);
    }
}
