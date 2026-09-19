package com.moakiee.ae2lt.crafting.algorithm;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import net.minecraft.resources.ResourceLocation;

import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.thunderbolt.ae2.crafting.CapturedPlanningChoice;
import com.moakiee.thunderbolt.api.crafting.CraftingAlgorithmProvider;
import com.moakiee.thunderbolt.api.crafting.CraftingPlanningEngines;
import com.moakiee.thunderbolt.api.crafting.PlanningChoice;
import com.moakiee.thunderbolt.core.crafting.planner.CpSatPlanningEngine;
import com.moakiee.thunderbolt.core.crafting.planner.ThunderboltV2PlanningEngine;

/**
 * Tianshu and formed Transfinite controllers lock one planning algorithm for
 * the grid. Thunderbolt's resolver still appends vanilla as a fallback;
 * exclusive engine picks keep that sentinel only long enough to satisfy
 * {@code thunderbolt$configurePlanning}, then strip it.
 */
public final class ExclusiveCraftingPlanning {
    private static final List<ResourceLocation> OWNED_ALGORITHMS = List.of(
            ThunderboltV2PlanningEngine.ID,
            CpSatPlanningEngine.ID);

    private ExclusiveCraftingPlanning() {
    }

    public static List<ResourceLocation> ownedAlgorithms() {
        return OWNED_ALGORITHMS;
    }

    public static List<ResourceLocation> selectable() {
        var options = new ArrayList<ResourceLocation>(3);
        options.add(ThunderboltV2PlanningEngine.ID);
        if (CraftingPlanningEngines.isKnown(CpSatPlanningEngine.ID)) {
            options.add(CpSatPlanningEngine.ID);
        }
        options.add(CraftingPlanningEngines.VANILLA_ID);
        return List.copyOf(options);
    }

    public static ResourceLocation normalize(@Nullable ResourceLocation selected) {
        var options = selectable();
        if (selected != null && options.contains(selected)) {
            return selected;
        }
        return options.get(0);
    }

    public static int displayIndex(@Nullable ResourceLocation selected) {
        var exclusive = normalize(selected);
        if (CraftingPlanningEngines.VANILLA_ID.equals(exclusive)) {
            return 2;
        }
        if (CpSatPlanningEngine.ID.equals(exclusive)) {
            return 1;
        }
        return 0;
    }

    public static ResourceLocation algorithmAtDisplayIndex(int index) {
        return switch (Math.max(0, Math.min(index, 2))) {
            case 1 -> CpSatPlanningEngine.ID;
            case 2 -> CraftingPlanningEngines.VANILLA_ID;
            default -> ThunderboltV2PlanningEngine.ID;
        };
    }

    public static String translationKey(@Nullable ResourceLocation selected) {
        var exclusive = normalize(selected);
        if (CraftingPlanningEngines.VANILLA_ID.equals(exclusive)) {
            return "ae2lt.tianshu.gui.algorithm.vanilla";
        }
        if (CpSatPlanningEngine.ID.equals(exclusive)) {
            return "ae2lt.tianshu.gui.algorithm.cp_sat";
        }
        return "ae2lt.tianshu.gui.algorithm.v2";
    }

    public static ResourceLocation cycle(@Nullable ResourceLocation selected) {
        var options = selectable();
        int index = options.indexOf(normalize(selected));
        return options.get(Math.floorMod(index + 1, options.size()));
    }

    public static List<PlanningChoice> candidatesForSelected(@Nullable ResourceLocation selected) {
        var exclusive = normalize(selected);
        if (CraftingPlanningEngines.VANILLA_ID.equals(exclusive)) {
            return List.of(PlanningChoice.VANILLA);
        }
        return List.of(PlanningChoice.engine(exclusive), PlanningChoice.VANILLA);
    }

    @Nullable
    public static ResourceLocation exclusiveAlgorithm(@Nullable IGrid grid) {
        if (grid == null) {
            return null;
        }
        var tianshu = exclusiveTianshuAlgorithm(grid);
        if (tianshu != null) {
            return tianshu;
        }
        return exclusiveLockSourceAlgorithm(grid);
    }

    @Nullable
    private static ResourceLocation exclusiveTianshuAlgorithm(IGrid grid) {
        TianshuSupercomputerPortBlockEntity best = null;
        int bestCpu = Integer.MIN_VALUE;
        int bestAlgo = Integer.MIN_VALUE;
        for (var port : grid.getMachines(TianshuSupercomputerPortBlockEntity.class)) {
            if (!port.isLinkActive()) {
                continue;
            }
            var controller = port.getController();
            if (controller == null) {
                continue;
            }
            int cpu = controller.getCpuPriority();
            int algo = port.getPriority();
            if (best == null || cpu > bestCpu || (cpu == bestCpu && algo > bestAlgo)) {
                best = port;
                bestCpu = cpu;
                bestAlgo = algo;
            }
        }
        return best == null ? null : normalize(best.getSelectedAlgorithm());
    }

    @Nullable
    private static ResourceLocation exclusiveLockSourceAlgorithm(IGrid grid) {
        var sources = new ArrayList<ExclusiveCraftingLockSource>();
        for (var node : grid.getNodes()) {
            var lock = lockSource(node);
            if (lock != null) {
                sources.add(lock);
            }
        }
        return exclusiveAlgorithmFromLockSources(sources);
    }

    @Nullable
    static ResourceLocation exclusiveAlgorithmFromLockSources(
            Iterable<ExclusiveCraftingLockSource> sources) {
        ExclusiveCraftingLockSource best = null;
        int bestCpu = Integer.MIN_VALUE;
        int bestAlgo = Integer.MIN_VALUE;
        for (var lock : sources) {
            if (lock == null || !lock.ae2lt$isExclusiveLockActive()) {
                continue;
            }
            int cpu = lock.ae2lt$getLockCpuPriority();
            int algo = lock.ae2lt$getLockProviderPriority();
            if (best == null || cpu > bestCpu || (cpu == bestCpu && algo > bestAlgo)) {
                best = lock;
                bestCpu = cpu;
                bestAlgo = algo;
            }
        }
        return best == null ? null : normalize(best.ae2lt$getExclusiveAlgorithm());
    }

    @Nullable
    private static ExclusiveCraftingLockSource lockSource(IGridNode node) {
        if (node.getService(CraftingAlgorithmProvider.class) instanceof ExclusiveCraftingLockSource lock) {
            return lock;
        }
        if (node.getOwner() instanceof ExclusiveCraftingLockSource lock) {
            return lock;
        }
        return null;
    }

    public static List<PlanningChoice> candidatesForConfigure(
            @Nullable IGrid grid, List<PlanningChoice> resolved) {
        var exclusive = exclusiveAlgorithm(grid);
        if (exclusive == null) {
            return resolved;
        }
        return candidatesForSelected(exclusive);
    }

    public static boolean locksExclusiveAlgorithm(@Nullable IGrid grid) {
        return exclusiveAlgorithm(grid) != null;
    }

    public static boolean locksExclusiveEngine(@Nullable IGrid grid) {
        var exclusive = exclusiveAlgorithm(grid);
        return exclusive != null && !CraftingPlanningEngines.VANILLA_ID.equals(exclusive);
    }

    public static List<CapturedPlanningChoice> stripVanilla(
            @Nullable List<CapturedPlanningChoice> captured) {
        if (captured == null || captured.isEmpty()) {
            return List.of();
        }
        var kept = new ArrayList<CapturedPlanningChoice>(captured.size());
        for (var candidate : captured) {
            if (candidate != null && candidate.choice().kind() != PlanningChoice.Kind.VANILLA) {
                kept.add(candidate);
            }
        }
        return List.copyOf(kept);
    }
}
