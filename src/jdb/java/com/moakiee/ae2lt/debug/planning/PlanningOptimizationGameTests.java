package com.moakiee.ae2lt.debug.planning;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import com.moakiee.ae2lt.crafting.algorithm.ExclusiveCraftingLockSource;
import com.moakiee.thunderbolt.api.crafting.CraftingAlgorithmSelection;
import com.moakiee.thunderbolt.api.crafting.CraftingPlanningEngines;
import com.moakiee.thunderbolt.api.crafting.PlanningChoice;
import com.moakiee.thunderbolt.core.crafting.algorithm.CraftingPlanningService;
import com.moakiee.thunderbolt.core.crafting.planner.ThunderboltV2PlanningEngine;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Development-only checks of the transformed resolver and real AE2 inventory notifications. */
@GameTestHolder("ae2lt_planning")
@PrefixGameTestTemplate(false)
public final class PlanningOptimizationGameTests {
    private PlanningOptimizationGameTests() { }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void exclusiveV2SurvivesWithoutATianshuCpu(GameTestHelper helper) {
        verifyExclusiveService(helper, ThunderboltV2PlanningEngine.ID,
                List.of(PlanningChoice.engine(ThunderboltV2PlanningEngine.ID), PlanningChoice.VANILLA));
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void exclusiveVanillaSurvivesWithoutEnumeratingCpus(GameTestHelper helper) {
        verifyExclusiveService(helper, CraftingPlanningEngines.VANILLA_ID, List.of(PlanningChoice.VANILLA));
    }

    private static void verifyExclusiveService(GameTestHelper helper, ResourceLocation algorithm,
            List<PlanningChoice> expected) {
        var lock = new ExclusiveCraftingLockSource() {
            public boolean ae2lt$isExclusiveLockActive() { return true; }
            public ResourceLocation ae2lt$getExclusiveAlgorithm() { return algorithm; }
            public int ae2lt$getLockCpuPriority() { return 0; }
            public int ae2lt$getLockProviderPriority() { return 0; }
            public void ae2lt$cycleExclusiveAlgorithm() { }
            public void ae2lt$setExclusiveSelection(CraftingAlgorithmSelection selection) { }
        };
        var node = proxy(IGridNode.class, (instance, method, args) ->
                method.getName().equals("getOwner") ? lock : null);
        var grid = proxy(IGrid.class, (instance, method, args) -> switch (method.getName()) {
            case "getMachines" -> Set.of();
            case "getNodes" -> List.of(node);
            default -> throw new AssertionError("Exclusive planning enumerated CPU availability: " + method.getName());
        });
        helper.assertTrue(new CraftingPlanningService(grid).resolve().equals(expected),
                "The transformed resolver must keep the same exclusive choice as ordinary requests");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void sharedAlternativesWaitForStockWithoutConsumingIt(GameTestHelper helper) throws Exception {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var choices = new GenericStack[] {new GenericStack(iron, 1), new GenericStack(gold, 1)};
        var pattern = pattern(input(2, choices), input(2, choices));
        var readiness = new Readiness();
        readiness.inventory.insert(iron, 1, Actionable.MODULATE);
        readiness.inventory.insert(gold, 1, Actionable.MODULATE);
        helper.assertTrue(readiness.missing(pattern).equals(Set.of(iron, gold)), "Shared stock must not count twice");
        helper.assertTrue(readiness.inventory.list.get(iron) == 1 && readiness.inventory.list.get(gold) == 1,
                "A shortage proof must not extract stock");
        readiness.inventory.insert(gold, 2, Actionable.MODULATE);
        helper.assertTrue(readiness.missing(pattern).isEmpty(), "Real inventory notifications must refresh the proof");
        helper.assertTrue(readiness.inventory.list.get(iron) == 1 && readiness.inventory.list.get(gold) == 3,
                "A successful preflight must remain read-only");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void feasibleAlternativeReassignmentRemainsAvailable(GameTestHelper helper) throws Exception {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var readiness = new Readiness();
        readiness.inventory.insert(iron, 1, Actionable.MODULATE);
        readiness.inventory.insert(gold, 1, Actionable.MODULATE);
        var pattern = pattern(input(1, new GenericStack(iron, 1), new GenericStack(gold, 1)),
                input(1, new GenericStack(iron, 1)));
        helper.assertTrue(readiness.missing(pattern).isEmpty(), "The flexible slot must leave iron for the fixed slot");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void jointDemandAboveLongDoesNotWrap(GameTestHelper helper) throws Exception {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var choices = new GenericStack[] {new GenericStack(iron, 1), new GenericStack(gold, 1)};
        var readiness = new Readiness();
        readiness.inventory.insert(iron, Long.MAX_VALUE - 1, Actionable.MODULATE);
        readiness.inventory.insert(gold, Long.MAX_VALUE - 1, Actionable.MODULATE);
        helper.assertTrue(readiness.missing(pattern(input(Long.MAX_VALUE - 1, choices),
                input(Long.MAX_VALUE - 1, choices))).isEmpty(), "Feasible aggregate quantities may exceed long");
        helper.assertTrue(!readiness.missing(pattern(input(Long.MAX_VALUE, choices),
                input(Long.MAX_VALUE, choices))).isEmpty(), "Two missing units must remain visible above long");
        helper.succeed();
    }

    private static final class Readiness {
        final ListCraftingInventory inventory;
        private final Method changed;
        private final Method missing;
        private Object delegate;

        Readiness() throws Exception {
            var type = Class.forName("com.moakiee.ae2lt.crafting.timewheel.CraftingMaterialReadiness");
            changed = type.getDeclaredMethod("onInventoryChange", AEKey.class);
            changed.setAccessible(true);
            missing = type.getDeclaredMethod("missing", IPatternDetails.class);
            missing.setAccessible(true);
            inventory = new ListCraftingInventory(key -> {
                if (delegate == null) return;
                try { changed.invoke(delegate, key); }
                catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            });
            var constructor = type.getDeclaredConstructor(ListCraftingInventory.class);
            constructor.setAccessible(true);
            delegate = constructor.newInstance(inventory);
        }

        @SuppressWarnings("unchecked")
        Set<AEKey> missing(IPatternDetails pattern) throws Exception {
            return (Set<AEKey>) missing.invoke(delegate, pattern);
        }
    }

    private static IPatternDetails.IInput input(long multiplier, GenericStack... choices) {
        return new IPatternDetails.IInput() {
            public GenericStack[] getPossibleInputs() { return choices; }
            public long getMultiplier() { return multiplier; }
            public boolean isValid(AEKey key, Level level) { throw new AssertionError("Preflight entered native matching"); }
            public AEKey getRemainingKey(AEKey key) { throw new AssertionError("Preflight computed containers"); }
        };
    }

    private static IPatternDetails pattern(IPatternDetails.IInput... inputs) {
        return new IPatternDetails() {
            public IInput[] getInputs() { return inputs; }
            public GenericStack[] getOutputs() { return new GenericStack[0]; }
            public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
        };
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
