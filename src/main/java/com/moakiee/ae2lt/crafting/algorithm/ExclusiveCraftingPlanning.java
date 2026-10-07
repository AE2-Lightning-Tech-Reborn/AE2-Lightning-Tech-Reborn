package com.moakiee.ae2lt.crafting.algorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import net.minecraft.resources.ResourceLocation;

import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.thunderbolt.ae2.crafting.CapturedPlanningChoice;
import com.moakiee.thunderbolt.api.crafting.CraftingAlgorithmProvider;
import com.moakiee.thunderbolt.api.crafting.CraftingPlanningEngines;
import com.moakiee.thunderbolt.api.crafting.PlanningChoice;
import com.moakiee.thunderbolt.api.crafting.PlanningRequest;
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
    private static final List<ResourceLocation> BASE_SELECTABLE = List.of(
            ThunderboltV2PlanningEngine.ID, CraftingPlanningEngines.VANILLA_ID);
    private static final List<ResourceLocation> CP_SAT_SELECTABLE = List.of(
            ThunderboltV2PlanningEngine.ID, CpSatPlanningEngine.ID, CraftingPlanningEngines.VANILLA_ID);
    private static final List<PlanningChoice> V2_CANDIDATES = List.of(
            PlanningChoice.engine(ThunderboltV2PlanningEngine.ID), PlanningChoice.VANILLA);
    private static final List<PlanningChoice> CP_SAT_CANDIDATES = List.of(
            PlanningChoice.engine(CpSatPlanningEngine.ID), PlanningChoice.VANILLA);
    private static final List<PlanningChoice> VANILLA_CANDIDATES = List.of(PlanningChoice.VANILLA);

    private ExclusiveCraftingPlanning() {
    }

    public static List<ResourceLocation> ownedAlgorithms() {
        return OWNED_ALGORITHMS;
    }

    public static List<ResourceLocation> selectable() {
        return CraftingPlanningEngines.isKnown(CpSatPlanningEngine.ID) ? CP_SAT_SELECTABLE : BASE_SELECTABLE;
    }

    public static ResourceLocation normalize(@Nullable ResourceLocation selected) {
        if (ThunderboltV2PlanningEngine.ID.equals(selected)
                || CraftingPlanningEngines.VANILLA_ID.equals(selected)
                || (CpSatPlanningEngine.ID.equals(selected)
                        && CraftingPlanningEngines.isKnown(CpSatPlanningEngine.ID))) {
            return selected;
        }
        return ThunderboltV2PlanningEngine.ID;
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
        int index = selected == null ? 0 : Math.max(0, options.indexOf(selected));
        return options.get(Math.floorMod(index + 1, options.size()));
    }

    public static List<PlanningChoice> candidatesForSelected(@Nullable ResourceLocation selected) {
        var exclusive = normalize(selected);
        if (CraftingPlanningEngines.VANILLA_ID.equals(exclusive)) {
            return VANILLA_CANDIDATES;
        }
        return CpSatPlanningEngine.ID.equals(exclusive) ? CP_SAT_CANDIDATES : V2_CANDIDATES;
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
        return exclusiveAlgorithmFromSources(grid.getNodes(), ExclusiveCraftingPlanning::lockSource);
    }

    @Nullable
    static ResourceLocation exclusiveAlgorithmFromLockSources(
            Iterable<ExclusiveCraftingLockSource> sources) {
        return exclusiveAlgorithmFromSources(sources, Function.identity());
    }

    @Nullable
    private static <T> ResourceLocation exclusiveAlgorithmFromSources(
            Iterable<T> sources, Function<T, ExclusiveCraftingLockSource> resolver) {
        ExclusiveCraftingLockSource best = null;
        int bestCpu = Integer.MIN_VALUE;
        int bestAlgo = Integer.MIN_VALUE;
        for (var source : sources) {
            var lock = resolver.apply(source);
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

    public static ExclusivePlanningDecision resolve(@Nullable IGrid grid) {
        return new ExclusivePlanningDecision(exclusiveAlgorithm(grid));
    }

    public static List<PlanningChoice> candidatesForConfigure(ExclusivePlanningDecision decision) {
        return decision == null ? List.of() : decision.candidates();
    }

    public static boolean locksExclusiveAlgorithm(@Nullable IGrid grid) {
        return exclusiveAlgorithm(grid) != null;
    }

    public static boolean locksExclusiveEngine(@Nullable IGrid grid) {
        var exclusive = exclusiveAlgorithm(grid);
        return exclusive != null && !CraftingPlanningEngines.VANILLA_ID.equals(exclusive);
    }

    public static boolean acceptsNodeLessV2Request(@Nullable IGrid grid, @Nullable PlanningRequest request) {
        if (grid == null || request == null || request.requester() == null
                || request.requestedAmount() <= 0 || request.output() == null
                || request.requester().getGridNode() != null
                || request.craftingService() != grid.getCraftingService()) {
            return false;
        }
        return ThunderboltV2PlanningEngine.ID.equals(exclusiveAlgorithm(grid));
    }

    public record ExclusivePlanningDecision(@Nullable ResourceLocation algorithm) {
        public boolean locked() {
            return algorithm != null;
        }

        public boolean engineLocked() {
            return locked() && !CraftingPlanningEngines.VANILLA_ID.equals(algorithm);
        }

        public List<PlanningChoice> candidates() {
            return locked() ? candidatesForSelected(algorithm) : List.of();
        }
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
