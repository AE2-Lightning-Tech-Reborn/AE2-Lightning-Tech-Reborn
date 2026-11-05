package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CraftingMaterialReadinessTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), null);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void alternativesAndNewComponentVariantsCanSatisfyOneSlotWithoutNativeMatching() {
        var oak = AEItemKey.of(Items.OAK_PLANKS);
        var birch = AEItemKey.of(Items.BIRCH_PLANKS);
        var pattern = pattern(input(2, new GenericStack(oak, 1), new GenericStack(birch, 1)));
        var inventory = inventory();
        inventory.insert(oak, 1, Actionable.MODULATE);
        assertEquals(Set.of(oak, birch), inventory.readiness.missing(pattern));
        var named = new ItemStack(Items.BIRCH_PLANKS);
        named.setHoverName(Component.literal("new physical variant"));
        inventory.insert(AEItemKey.of(named), 1, Actionable.MODULATE);
        assertTrue(inventory.readiness.missing(pattern).isEmpty());
        assertEquals(1, inventory.list.get(oak), "probe must not consume anything");
    }

    @Test
    void repeatedAnchorsDoNotMultiplyStockAndFixedSlotsShareTheSameStock() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var inventory = inventory();
        inventory.insert(iron, 1, Actionable.MODULATE);
        var duplicate = pattern(input(2, new GenericStack(iron, 1), new GenericStack(iron, 1)));
        assertEquals(Set.of(iron), inventory.readiness.missing(duplicate));
        var twoSlots = pattern(input(1, new GenericStack(iron, 1)), input(1, new GenericStack(iron, 1)));
        assertEquals(Set.of(iron), inventory.readiness.missing(twoSlots));
        inventory.insert(iron, 1, Actionable.MODULATE);
        assertTrue(inventory.readiness.missing(twoSlots).isEmpty());
    }

    @Test
    void fluidsUseTheirPhysicalUnitSize() {
        var water = AEFluidKey.of(Fluids.WATER);
        var pattern = pattern(input(2, new GenericStack(water, 1000)));
        var inventory = inventory();
        inventory.insert(water, 1999, Actionable.MODULATE);
        assertEquals(Set.of(water), inventory.readiness.missing(pattern));
        inventory.insert(water, 1, Actionable.MODULATE);
        assertTrue(inventory.readiness.missing(pattern).isEmpty());
    }

    @Test
    void optimisticReadinessDoesNotClaimNativeValidityAndUnknownInputsNeedRefresh() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var pattern = pattern(input(1, new GenericStack(iron, 1)));
        assertFalse(CraftingMaterialReadiness.stableInputs(pattern));
        var inventory = inventory();
        assertEquals(Set.of(iron), inventory.readiness.missing(pattern));
        inventory.insert(iron, 1, Actionable.MODULATE);
        assertTrue(inventory.readiness.missing(pattern).isEmpty(),
                "full native validity must be checked by the original dispatch path");
    }

    @Test
    void largeQuantitiesCannotWrapIntoAFalseShortage() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var inventory = inventory();
        inventory.insert(iron, Long.MAX_VALUE, Actionable.MODULATE);
        assertTrue(inventory.readiness.missing(
                pattern(input(Long.MAX_VALUE, new GenericStack(iron, 1)))).isEmpty());
    }

    @Test
    void alternativeSlotsCannotEachClaimTheSamePhysicalStock() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var choices = new GenericStack[] {new GenericStack(iron, 1), new GenericStack(gold, 1)};
        var pattern = pattern(input(2, choices), input(2, choices));
        var inventory = inventory();
        inventory.insert(iron, 1, Actionable.MODULATE);
        inventory.insert(gold, 1, Actionable.MODULATE);

        assertEquals(Set.of(iron, gold), inventory.readiness.missing(pattern));
        assertEquals(1, inventory.list.get(iron));
        assertEquals(1, inventory.list.get(gold));
        inventory.insert(gold, 2, Actionable.MODULATE);
        assertTrue(inventory.readiness.missing(pattern).isEmpty(), "new stock must release the shortage");
    }

    @Test
    void surplusInAnOverlappingThirdSlotCannotHideASharedSubsetShortage() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var diamond = AEItemKey.of(Items.DIAMOND);
        var scarce = new GenericStack[] {new GenericStack(iron, 1), new GenericStack(gold, 1)};
        var pattern = pattern(input(1, scarce), input(1, scarce),
                input(1, new GenericStack(gold, 1), new GenericStack(diamond, 1)));
        var inventory = inventory();
        inventory.insert(iron, 1, Actionable.MODULATE);
        inventory.insert(diamond, 100, Actionable.MODULATE);

        assertFalse(inventory.readiness.missing(pattern).isEmpty());
        inventory.insert(gold, 1, Actionable.MODULATE);
        assertTrue(inventory.readiness.missing(pattern).isEmpty());
    }

    @Test
    void sharedDemandAndSupplyMayBothExceedLongWithoutWrapping() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var choices = new GenericStack[] {new GenericStack(iron, 1), new GenericStack(gold, 1)};
        var pattern = pattern(input(Long.MAX_VALUE, choices), input(Long.MAX_VALUE, choices));
        var inventory = inventory();
        inventory.insert(iron, Long.MAX_VALUE - 1, Actionable.MODULATE);
        inventory.insert(gold, Long.MAX_VALUE - 1, Actionable.MODULATE);

        assertTrue(inventory.readiness.missing(
                pattern(input(Long.MAX_VALUE - 1, choices), input(Long.MAX_VALUE - 1, choices))).isEmpty());
        assertFalse(inventory.readiness.missing(pattern).isEmpty());
        inventory.insert(iron, 1, Actionable.MODULATE);
        inventory.insert(gold, 1, Actionable.MODULATE);
        assertTrue(inventory.readiness.missing(pattern).isEmpty());
    }

    @Test
    void saturatedVariantFamiliesRemainAvailableForNativeAllocation() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var named = new ItemStack(Items.IRON_INGOT);
        named.setHoverName(Component.literal("another physical stock"));
        var inventory = inventory();
        inventory.insert(iron, Long.MAX_VALUE, Actionable.MODULATE);
        inventory.insert(AEItemKey.of(named), Long.MAX_VALUE, Actionable.MODULATE);
        var choices = new GenericStack[] {new GenericStack(iron, 1), new GenericStack(gold, 1)};

        assertTrue(inventory.readiness.missing(
                pattern(input(Long.MAX_VALUE, choices), input(Long.MAX_VALUE, choices))).isEmpty(),
                "a saturated family projection is not evidence of a physical shortage");
    }

    @Test
    void oversizedCustomPatternsKeepTheOrdinaryAllocatorAvailable() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var inventory = inventory();
        inventory.insert(iron, 1, Actionable.MODULATE);
        var inputs = new IPatternDetails.IInput[SharedInputAvailability.MAX_INPUTS + 1];
        java.util.Arrays.fill(inputs, input(1, new GenericStack(iron, 1), new GenericStack(gold, 1)));

        assertTrue(inventory.readiness.missing(pattern(inputs)).isEmpty(),
                "joint preflight must stay bounded for arbitrarily large addon input definitions");
    }

    @Test
    void alternativeAssignmentAgreesWithIndependentSmallInventoryOracle() {
        var random = new Random(0x45434f47544cL);
        var keys = new AEKey[] {AEItemKey.of(Items.IRON_INGOT), AEItemKey.of(Items.GOLD_INGOT),
                AEItemKey.of(Items.DIAMOND)};
        for (int scenario = 0; scenario < 1000; scenario++) {
            var inventory = inventory();
            var stock = new long[keys.length];
            for (int k = 0; k < keys.length; k++) {
                stock[k] = random.nextInt(5);
                inventory.insert(keys[k], stock[k], Actionable.MODULATE);
            }
            int slots = 2 + random.nextInt(3);
            var counts = new int[slots];
            var units = new int[slots][keys.length];
            var inputs = new IPatternDetails.IInput[slots];
            boolean uniformUnits = scenario % 2 == 0;
            for (int slot = 0; slot < slots; slot++) {
                counts[slot] = 1 + random.nextInt(2);
                var choices = new java.util.ArrayList<GenericStack>();
                int requiredChoice = random.nextInt(keys.length);
                for (int k = 0; k < keys.length; k++) {
                    if (k == requiredChoice || random.nextBoolean()) {
                        units[slot][k] = uniformUnits ? 1 : 1 + random.nextInt(3);
                        choices.add(new GenericStack(keys[k], units[slot][k]));
                    }
                }
                inputs[slot] = input(counts[slot], choices.toArray(GenericStack[]::new));
            }
            boolean possible = assign(stock.clone(), units, counts.clone(), 0);
            boolean ready = inventory.readiness.missing(pattern(inputs)).isEmpty();
            if (uniformUnits) assertEquals(possible, ready, "unit-sized allocation scenario " + scenario);
            else if (possible) assertTrue(ready, "optimistic probe must not park feasible scenario " + scenario);
            for (int k = 0; k < keys.length; k++) assertEquals(stock[k], inventory.list.get(keys[k]));
        }
    }

    private static boolean assign(long[] stock, int[][] units, int[] remaining, int slot) {
        if (slot == remaining.length) return true;
        if (remaining[slot] == 0) return assign(stock, units, remaining, slot + 1);
        for (int k = 0; k < stock.length; k++) {
            int amount = units[slot][k];
            if (amount == 0 || stock[k] < amount) continue;
            stock[k] -= amount;
            remaining[slot]--;
            boolean possible = assign(stock, units, remaining, slot);
            remaining[slot]++;
            stock[k] += amount;
            if (possible) return true;
        }
        return false;
    }

    @Test
    void waitingTasksShareOneFamilyCountUntilThatFamilyChanges() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var inventory = inventory();
        var tasks = new IPatternDetails[1000];
        for (int i = 0; i < tasks.length; i++) {
            tasks[i] = pattern(input(2, new GenericStack(iron, 1)));
            assertEquals(Set.of(iron), inventory.readiness.missing(tasks[i]));
        }
        assertEquals(1, inventory.fuzzyQueries, "one physical family lookup shared by 1000 tasks");
        inventory.insert(gold, 10, Actionable.MODULATE);
        for (var task : tasks) assertEquals(Set.of(iron), inventory.readiness.missing(task));
        assertEquals(1, inventory.fuzzyQueries, "unrelated material must not invalidate cached shortage");

        var named = new ItemStack(Items.IRON_INGOT);
        named.setHoverName(Component.literal("previously unseen variant"));
        inventory.insert(AEItemKey.of(named), 2, Actionable.MODULATE);
        for (var task : tasks) assertTrue(inventory.readiness.missing(task).isEmpty());
        assertEquals(2, inventory.fuzzyQueries, "new components invalidate only their primary family");
        inventory.clear();
        for (var task : tasks) assertEquals(Set.of(iron), inventory.readiness.missing(task));
        assertEquals(3, inventory.fuzzyQueries, "inventory removal must invalidate optimistic stock too");
    }

    private static CountingInventory inventory() {
        var readiness = new CraftingMaterialReadiness[1];
        var inventory = new CountingInventory(key -> readiness[0].onInventoryChange(key));
        readiness[0] = inventory.readiness = new CraftingMaterialReadiness(inventory);
        return inventory;
    }

    private static final class CountingInventory extends ListCraftingInventory {
        private CraftingMaterialReadiness readiness;
        private int fuzzyQueries;

        private CountingInventory(java.util.function.Consumer<AEKey> listener) {
            super(listener::accept);
        }

        @Override public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
            fuzzyQueries++;
            return super.findFuzzyTemplates(key);
        }

        @Override public long extract(AEKey key, long amount, Actionable mode) {
            throw new AssertionError("readiness must not enter native extraction");
        }
    }

    private static IPatternDetails.IInput input(long multiplier, GenericStack... choices) {
        return new IPatternDetails.IInput() {
            public GenericStack[] getPossibleInputs() { return choices; }
            public long getMultiplier() { return multiplier; }
            public boolean isValid(AEKey key, Level level) {
                throw new AssertionError("shortage proof must not invoke dynamic matching");
            }
            public AEKey getRemainingKey(AEKey key) {
                throw new AssertionError("shortage proof must not compute containers");
            }
        };
    }

    private static IPatternDetails pattern(IPatternDetails.IInput... inputs) {
        return new IPatternDetails() {
            public IInput[] getInputs() { return inputs; }
            public GenericStack[] getOutputs() { return new GenericStack[0]; }
            public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
        };
    }
}
